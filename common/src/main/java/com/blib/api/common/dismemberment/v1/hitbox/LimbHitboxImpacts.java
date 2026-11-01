package com.blib.api.common.dismemberment.v1.hitbox;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Limb hitboxes for melee weapons and projectiles, not only guns (Oct 31).
 * <p>
 * Guns already aim at limbs themselves. This gives the same to every weapon on two data-pack lists, which anyone can
 * add to without code:
 * </p>
 * <ul>
 * <li>{@code data/blib/tags/item/limb_hitbox_weapons.json} - a melee hit with one of these items in the main hand finds
 * the limb along the attacker's line of sight.</li>
 * <li>{@code data/blib/tags/entity_type/limb_hitbox_projectiles.json} - a hit by one of these projectiles finds the
 * limb along its flight path.</li>
 * </ul>
 * <p>
 * The limb's damage multiplier applies to the hit (a head is x2 on a xeno), and the damage that actually LANDED - after
 * armour, shields and hit cooldowns - goes into that limb's sever pool, exactly like a bullet. Mobs without limb
 * hitboxes, and any weapon or projectile not on a list, are untouched.
 * </p>
 */
public final class LimbHitboxImpacts {

    /** Melee weapons that hit limbs. */
    public static final TagKey<Item> WEAPONS = TagKey.create(
        Registries.ITEM,
        ResourceLocation.fromNamespaceAndPath("blib", "limb_hitbox_weapons")
    );

    /** Projectiles that hit limbs. */
    public static final TagKey<EntityType<?>> PROJECTILES = TagKey.create(
        Registries.ENTITY_TYPE,
        ResourceLocation.fromNamespaceAndPath("blib", "limb_hitbox_projectiles")
    );

    /**
     * Adjusts a melee or projectile limb hit on top of the limb's own multiplier: plating, armour, special rules. A
     * result of 0 makes the hit bounce off completely (no damage, no flinch, no limb damage). Mods register these at
     * startup; they are multiplied together.
     */
    @FunctionalInterface
    public interface Modifier {

        float multiplier(LivingEntity target, LimbHitboxRegistry.Hit hit, DamageSource source);
    }

    private static final java.util.List<Modifier> MODIFIERS = new java.util.concurrent.CopyOnWriteArrayList<>();

    public static void registerModifier(Modifier modifier) {
        MODIFIERS.add(java.util.Objects.requireNonNull(modifier, "modifier"));
    }

    /**
     * A limb hit for one {@code hurt} call: the hit, the total multiplier, and the target's health before it. Worked
     * out once, by whichever of the two HEAD hooks runs first, and shared by both.
     */
    private record Pending(
        DamageSource source,
        LimbHitboxRegistry.Hit hit,
        float multiplier,
        float healthBefore
    ) {}

    /**
     * Keyed by target, not a stack: another mod may cancel {@code hurt} at its HEAD, which skips our RETURN hook, and a
     * stack would then pair the wrong entries. A stale entry here is simply replaced by the target's next hit.
     */
    // Weak keys: an entry left behind by a cancelled hit never keeps a dead or unloaded entity in memory. Entity does
    // not
    // override equals/hashCode, so this is identity-keyed like the hit itself.
    private static final ThreadLocal<Map<LivingEntity, Pending>> PENDING = ThreadLocal.withInitial(WeakHashMap::new);

    /**
     * Called at the start of {@code LivingEntity#hurt}: {@code true} when the hit lands on a limb whose modifiers make
     * it bounce off entirely, so {@code hurt} should return without doing anything.
     */
    public static boolean deflects(LivingEntity target, DamageSource source, float amount) {
        var pending = prepare(target, source, amount);
        return pending != null && pending.multiplier() <= 0.0F;
    }

    /**
     * Called at the start of {@code LivingEntity#hurt} on the server. Returns the damage to use: scaled by the limb's
     * multiplier (and any {@link Modifier}s) when the hit lands on a limb, unchanged otherwise.
     */
    public static float beforeHurt(LivingEntity target, DamageSource source, float amount) {
        var pending = prepare(target, source, amount);
        return pending == null ? amount : amount * pending.multiplier();
    }

    /** Works the hit out once per {@code hurt} call; the second HEAD hook gets the same answer. */
    private static @Nullable Pending prepare(LivingEntity target, DamageSource source, float amount) {
        var map = PENDING.get();
        var existing = map.get(target);

        if (existing != null && existing.source() == source) {
            return existing;
        }

        if (map.size() > 64) {
            map.clear();
        }

        map.remove(target);

        if (amount <= 0.0F || target.level().isClientSide) {
            return null;
        }

        var hit = find(target, source);

        if (hit == null) {
            return null;
        }

        var multiplier = hit.volume().healthDamageMultiplier();

        for (var modifier : MODIFIERS) {
            multiplier *= Math.max(0.0F, modifier.multiplier(target, hit, source));
        }

        var pending = new Pending(source, hit, multiplier, target.getHealth());
        map.put(target, pending);
        return pending;
    }

    /** Called when {@code LivingEntity#hurt} returns: records the damage that landed on the limb. */
    public static void afterHurt(LivingEntity target, boolean hurt) {
        var entry = PENDING.get().remove(target);

        if (entry == null || !hurt || entry.multiplier() <= 0.0F) {
            return;
        }

        var landed = entry.healthBefore() - target.getHealth();

        if (landed > 0.0F) {
            LimbHitboxDamage.apply(target, entry.hit(), landed);
        }
    }

    /** The limb this damage hits, or {@code null} if the weapon or projectile is not listed, or nothing was hit. */
    public static @Nullable LimbHitboxRegistry.Hit find(LivingEntity target, DamageSource source) {
        var direct = source.getDirectEntity();

        if (direct instanceof Projectile projectile) {
            if (!projectile.getType().is(PROJECTILES)) {
                return null;
            }

            // The impact is handled before the projectile moves this tick: its path runs from where it is along its
            // motion. A little behind and ahead, so a fast projectile that is already inside the target still counts.
            var motion = projectile.getDeltaMovement();
            var start = projectile.position().subtract(motion.scale(0.5D));
            var end = projectile.position().add(motion.scale(1.5D));
            return LimbHitboxRegistry.findNearest(target, start, end).orElse(null);
        }

        if (
            direct instanceof LivingEntity attacker
                && direct == source.getEntity()
                && isMelee(source)
                && attacker.getMainHandItem().is(WEAPONS)
        ) {
            // A player hits where they are looking. A mob's look control stares at its target's EYES, so its line of
            // sight would make every mob swing a head hit; mobs swing at the middle of the body instead.
            var eyes = attacker.getEyePosition();
            var centre = target.getBoundingBox().getCenter();
            var reach = eyes.distanceTo(centre) + target.getBbWidth() + 2.0D;
            var direction = attacker instanceof Player ? attacker.getViewVector(1.0F) : centre.subtract(eyes).normalize();
            Vec3 end = eyes.add(direction.scale(reach));
            return LimbHitboxRegistry.findNearest(target, eyes, end).orElse(null);
        }

        return null;
    }

    private static boolean isMelee(DamageSource source) {
        return source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypes.MOB_ATTACK) || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO);
    }

    private LimbHitboxImpacts() {}
}
