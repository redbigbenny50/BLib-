package com.blib.api.common.dismemberment.v1.hitbox;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.blib.api.common.dismemberment.v1.DismembermentManager;

/**
 * Server-side evaluator for Bedrock geo models and their animation files, producing oriented limb hurtboxes in world
 * space.
 * <p>
 * The renderer remains responsible for drawing the model, but firearm targeting must not read renderer state: a
 * dedicated server has no renderer, and a client pose can be late or spoofed. This profile evaluates the authored bone
 * hierarchy and cubes from the same asset files the client draws.
 * </p>
 * <h2>Hitbox groups made in Blockbench</h2>
 * <p>
 * Any group (bone) whose name starts with {@code hitbox_} is a hitbox group. Its cubes are never drawn in game. The
 * rest of the name says which limb it belongs to, using {@link LimbNameMatcher}: {@code hitbox_head} feeds the limb
 * {@code avp_alien:drone_head}, {@code hitbox_left_arm} feeds {@code avp_alien:drone_left_arm}, and so on. Put the
 * group inside the bone it should move with and shape its cubes freely (move, resize, rotate, inflate).
 * </p>
 * <ul>
 * <li>A limb with one or more matching hitbox groups uses ONLY those cubes.</li>
 * <li>Group names must be unique in Blockbench, so number extra groups for the same limb: {@code hitbox_head},
 * {@code hitbox_head_2}, {@code hitbox_head_3}. Groups inside a hitbox group are part of it automatically.</li>
 * <li>A limb with no hitbox group keeps the old behaviour: every visible cube of its bone (and child bones).</li>
 * <li>Hitbox groups never count as visible cubes for any other limb.</li>
 * <li>A hitbox group's cubes get no automatic padding; their size is exactly what was drawn, unless the
 * {@code blib_hitboxes} data file sets {@code padding} for that limb.</li>
 * </ul>
 * <h2>Data-file tuning</h2>
 * <p>
 * Damage multipliers, sever damage, padding and tags can be overridden per limb from
 * {@code data/<namespace>/blib_hitboxes/<model>.json} (see {@link LimbHitboxSettings}). The model id used for that
 * lookup is the {@code id} this profile was loaded with.
 * </p>
 */
public final class GeoHitboxProfile {

    private static final Logger LOGGER = LoggerFactory.getLogger(GeoHitboxProfile.class);

    /** The prefix that marks a Blockbench group as a hitbox group. */
    public static final String HITBOX_GROUP_PREFIX = "hitbox_";

    private static final Map<ResourceLocation, GeoHitboxProfile> LOADED = new ConcurrentHashMap<>();

    private final ResourceLocation id;

    private final Map<String, Bone> bones;

    private final Map<String, Animation> animations;

    private final Map<String, LimbRule> limbRoots;

    /** For each limb root (keyed like {@link #limbRoots}), the hitbox group bones that replace its visible cubes. */
    private final Map<String, List<String>> hitboxGroupsByRoot;

    /** Limb ids that are shaped by hitbox groups, for the client picker and the overlay. */
    private final Set<ResourceLocation> limbsWithHitboxGroups;

    private GeoHitboxProfile(
        ResourceLocation id,
        Map<String, Bone> bones,
        Map<String, Animation> animations,
        Map<String, LimbRule> limbRoots
    ) {
        this.id = id;
        this.bones = bones;
        this.animations = animations;
        this.limbRoots = limbRoots;
        this.hitboxGroupsByRoot = matchHitboxGroups(id, bones, limbRoots);
        var shaped = new HashSet<ResourceLocation>();
        for (var entry : hitboxGroupsByRoot.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                shaped.add(limbRoots.get(entry.getKey()).limbId());
            }
        }
        this.limbsWithHitboxGroups = Set.copyOf(shaped);
    }

    /**
     * Loads a profile from resources visible to {@code anchor}'s class loader, the way the mod that owns the assets
     * would read them. Pass a class from the mod that ships the model.
     *
     * @param id            the model id; also the {@code blib_hitboxes} data file id (e.g. {@code avp_alien:drone})
     * @param anchor        any class from the mod that ships the asset files
     * @param geometryPath  the classpath path of the geo file, e.g. {@code /assets/avp_alien/geo/entity/drone.geo.json}
     * @param animationPath the classpath path of the animation file, or {@code null} for a model with no animations
     * @param limbRoots     the bone each limb starts at, and its default rule
     */
    public static GeoHitboxProfile load(
        ResourceLocation id,
        Class<?> anchor,
        String geometryPath,
        String animationPath,
        Map<String, LimbRule> limbRoots
    ) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(anchor, "anchor");
        try (
            var geometryInput = anchor.getResourceAsStream(geometryPath);
            var animationInput = animationPath == null ? null : anchor.getResourceAsStream(animationPath)
        ) {
            if (geometryInput == null || animationPath != null && animationInput == null) {
                throw new IllegalStateException(
                    "Missing hitbox assets for " + id + " (" + geometryPath + ", " + animationPath + ")"
                );
            }
            return load(id, geometryInput, animationInput, limbRoots);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Could not load animated hitbox profile " + id, exception);
        }
    }

    /** Loads a profile from already-open streams. The caller closes them. {@code animationInput} may be null. */
    public static GeoHitboxProfile load(
        ResourceLocation id,
        InputStream geometryInput,
        InputStream animationInput,
        Map<String, LimbRule> limbRoots
    ) {
        var geometry = JsonParser.parseReader(new InputStreamReader(geometryInput, StandardCharsets.UTF_8)).getAsJsonObject();
        // Oct 31 - a model with no animation file still gives its rest pose (bone sockets on any model).
        var animations = animationInput == null
            ? Map.<String, Animation>of()
            : readAnimations(JsonParser.parseReader(new InputStreamReader(animationInput, StandardCharsets.UTF_8)).getAsJsonObject());
        var profile = new GeoHitboxProfile(id, readBones(geometry), animations, Map.copyOf(limbRoots));
        LOADED.put(id, profile);
        return profile;
    }

    /** A profile loaded earlier under this id, or {@code null}. */
    public static GeoHitboxProfile get(ResourceLocation id) {
        return LOADED.get(id);
    }

    public ResourceLocation id() {
        return id;
    }

    /** True when this limb is shaped by {@code hitbox_} groups rather than by its visible cubes. */
    public boolean usesHitboxGroups(ResourceLocation limbId) {
        return limbsWithHitboxGroups.contains(limbId);
    }

    /** The limb a {@code hitbox_} bone name belongs to in this profile, or {@code null}. */
    public ResourceLocation limbForHitboxGroup(String boneName) {
        for (var entry : hitboxGroupsByRoot.entrySet()) {
            if (entry.getValue().contains(boneName)) {
                return limbRoots.get(entry.getKey()).limbId();
            }
        }
        return null;
    }

    public List<LimbHitboxVolume> volumes(LivingEntity entity, String animationName, double animationTimeSeconds) {
        return volumes(entity, animationName, animationTimeSeconds, 0.0F, 0.0F, 0.0F);
    }

    /**
     * Oct 31 - the same, for a model the renderer also turns as a whole (swimming pitch, a leap, wall crawling):
     * hitboxes follow the turned model instead of standing upright.
     *
     * @param pitchDegrees nose-down turn of the whole model, like {@code Entity#getXRot}
     * @param rollDegrees  turn about the body's forward axis, positive rolls towards its right side
     * @param pivotY       height above the entity's feet the model turns around, in blocks
     */
    public List<LimbHitboxVolume> volumes(
        LivingEntity entity,
        String animationName,
        double animationTimeSeconds,
        float pitchDegrees,
        float rollDegrees,
        float pivotY
    ) {
        var animation = animations.get(animationName);
        var matrices = seededMatrices(pitchDegrees, rollDegrees, pivotY);
        for (var bone : bones.values()) {
            matrixFor(bone, animation, animationTimeSeconds, matrices);
        }

        var result = new ArrayList<LimbHitboxVolume>();
        for (var entry : limbRoots.entrySet()) {
            var rule = entry.getValue();
            // A detached limb must disappear from the authoritative firearm target set immediately. This also skips
            // the rest of the bone subtree, which is especially important for multi-segment tails.
            if (DismembermentManager.isDetached(entity, rule.limbId())) {
                continue;
            }
            var settings = LimbHitboxSettingsRegistry.find(id, rule.limbId()).orElse(LimbHitboxSettings.NONE);
            var groups = hitboxGroupsByRoot.getOrDefault(entry.getKey(), List.of());
            if (!groups.isEmpty()) {
                var resolved = resolve(rule, settings, 0.0D);
                for (var groupName : groups) {
                    addGroupVolumes(bones.get(groupName), resolved, entity, matrices, result);
                }
                continue;
            }
            var root = bones.get(entry.getKey());
            if (root != null) {
                addVolumes(root, resolve(rule, settings, rule.padding()), rule.includeDescendants(), entity, matrices, result);
            }
        }
        return result;
    }

    /** A point on a posed bone, in world space, with the bone's own axes. */
    public record BonePoint(
        Vec3 position,
        Vec3 forward,
        Vec3 up,
        Vec3 right
    ) {}

    /** Whether this model has a bone with this name. */
    public boolean hasBone(String boneName) {
        return bones.containsKey(boneName);
    }

    /** The bone that owns this Blockbench locator, or {@code null}. */
    public @Nullable String boneForLocator(String locatorName) {
        for (var bone : bones.values()) {
            if (bone.locators().containsKey(locatorName)) {
                return bone.name();
            }
        }
        return null;
    }

    /**
     * Oct 31 - where a bone (or one of its Blockbench locators) is on the server, posed by the given animation, for
     * bone sockets: a projectile from an animated barrel, a held item's grip. Computes only the bone's own parent
     * chain.
     *
     * @param entity        the entity whose position, body yaw and scale place the model
     * @param boneName      the bone, or {@code null} to find the bone that owns {@code locatorName}
     * @param locatorName   a Blockbench locator on that bone, or {@code null} for the bone's pivot
     * @param offsetPixels  an extra offset in the bone's own space, in Blockbench pixels
     * @param animationName the animation that is playing, or {@code null} for the rest pose
     * @param animationTime seconds into that animation
     * @return the point, or {@code null} if the bone or locator does not exist
     */
    public @Nullable BonePoint bonePoint(
        Entity entity,
        @Nullable String boneName,
        @Nullable String locatorName,
        Vec3 offsetPixels,
        @Nullable String animationName,
        double animationTime
    ) {
        return bonePoint(entity, boneName, locatorName, offsetPixels, animationName, animationTime, 0.0F, 0.0F, 0.0F);
    }

    /** {@link #bonePoint}, for a model turned as a whole; see the rotating {@link #volumes} overload. */
    public @Nullable BonePoint bonePoint(
        Entity entity,
        @Nullable String boneName,
        @Nullable String locatorName,
        Vec3 offsetPixels,
        @Nullable String animationName,
        double animationTime,
        float pitchDegrees,
        float rollDegrees,
        float pivotY
    ) {
        if (boneName == null && locatorName != null) {
            boneName = boneForLocator(locatorName);
        }
        var bone = boneName == null ? null : bones.get(boneName);
        if (bone == null) {
            return null;
        }
        var anchor = bone.pivot();
        if (locatorName != null) {
            var locator = bone.locators().get(locatorName);
            if (locator == null) {
                return null;
            }
            anchor = locator;
        }
        var animation = animationName == null ? null : animations.get(animationName);
        var transform = matrixFor(bone, animation, animationTime, seededMatrices(pitchDegrees, rollDegrees, pivotY));
        var local = transform.transformPosition(modelVector(anchor.add(offsetPixels)));
        var yaw = Math.toRadians(entity instanceof LivingEntity living ? living.yBodyRot : entity.getYRot());
        // Same placement as toWorldVolume, which matches AzEntityModelRenderer's 180 - body yaw turn.
        var worldX = new Vec3(-Math.cos(yaw), 0.0D, -Math.sin(yaw));
        var worldY = new Vec3(0.0D, 1.0D, 0.0D);
        var worldZ = new Vec3(Math.sin(yaw), 0.0D, -Math.cos(yaw));
        var scale = entity instanceof LivingEntity living ? living.getScale() : 1.0F;
        var position = entity.position()
            .add(worldX.scale(local.x * scale))
            .add(worldY.scale(local.y * scale))
            .add(worldZ.scale(local.z * scale));
        // Geo -Z is the model's front, so the bone's forward is its local -Z.
        var forward = mapAxis(transform.transformDirection(new Vector3f(0, 0, -1)), worldX, worldY, worldZ);
        var up = mapAxis(transform.transformDirection(new Vector3f(0, 1, 0)), worldX, worldY, worldZ);
        // worldX is the entity's right at any yaw (yaw 0 faces +Z, right is -X), so local +X is right.
        var right = mapAxis(transform.transformDirection(new Vector3f(1, 0, 0)), worldX, worldY, worldZ);
        return new BonePoint(position, forward, up, right);
    }

    public double animationLengthSeconds(String animationName) {
        var animation = animations.get(animationName);
        return animation == null ? 0.0D : animation.lengthSeconds();
    }

    public boolean hasAnimation(String animationName) {
        return animations.containsKey(animationName);
    }

    /** Returns the first authored animation available in this model, falling back to its neutral pose. */
    public String firstAnimation(String... candidates) {
        for (var candidate : candidates) {
            if (animations.containsKey(candidate)) {
                return candidate;
            }
        }
        return "";
    }

    private static Resolved resolve(LimbRule rule, LimbHitboxSettings settings, double defaultPadding) {
        var tags = new HashSet<>(rule.tags());
        settings.tags().ifPresent(tags::addAll);
        return new Resolved(
            rule.limbId(),
            settings.damageMultiplier().orElse(rule.healthDamageMultiplier()),
            settings.limbDamageMultiplier().orElse(rule.limbDamageMultiplier()),
            settings.severDamage().orElse(rule.limbDamageThreshold()),
            settings.padding().orElse(defaultPadding),
            Set.copyOf(tags)
        );
    }

    private static Map<String, List<String>> matchHitboxGroups(
        ResourceLocation id,
        Map<String, Bone> bones,
        Map<String, LimbRule> limbRoots
    ) {
        var result = new HashMap<String, List<String>>();
        var used = new HashSet<String>();
        for (var entry : limbRoots.entrySet()) {
            var path = entry.getValue().limbId().getPath();
            var best = new ArrayList<String>();
            var bestLength = -1;
            for (var boneName : bones.keySet()) {
                if (!boneName.startsWith(HITBOX_GROUP_PREFIX)) {
                    continue;
                }
                var name = groupLimbName(boneName);
                if (!LimbNameMatcher.matches(path, name)) {
                    continue;
                }
                if (name.length() > bestLength) {
                    best.clear();
                    bestLength = name.length();
                }
                if (name.length() == bestLength) {
                    best.add(boneName);
                }
            }
            best.sort(Comparator.naturalOrder());
            used.addAll(best);
            result.put(entry.getKey(), List.copyOf(best));
        }
        for (var boneName : bones.keySet()) {
            if (isHitboxRoot(bones, boneName) && !used.contains(boneName)) {
                LOGGER.warn(
                    "[BLib] Hitbox group '{}' in model {} matches no limb and is ignored. Name it hitbox_<limb>, for example hitbox_head.",
                    boneName,
                    id
                );
            }
        }
        return Map.copyOf(result);
    }

    /**
     * The limb name a hitbox group stands for. Blockbench needs every group name to be unique, so a limb that needs
     * groups on several bones (a head and its jaw, say) can number them: {@code hitbox_head}, {@code hitbox_head_2},
     * {@code hitbox_head3} all mean {@code head}. No limb name ends in a digit, so the number is never part of it.
     */
    static String groupLimbName(String boneName) {
        return boneName.substring(HITBOX_GROUP_PREFIX.length()).replaceFirst("_?\\d+$", "");
    }

    /** A {@code hitbox_} bone that is not itself inside another hitbox group. */
    private static boolean isHitboxRoot(Map<String, Bone> bones, String boneName) {
        if (!boneName.startsWith(HITBOX_GROUP_PREFIX)) {
            return false;
        }
        var parent = bones.get(boneName).parent();
        while (parent != null) {
            if (parent.startsWith(HITBOX_GROUP_PREFIX)) {
                return false;
            }
            var parentBone = bones.get(parent);
            parent = parentBone == null ? null : parentBone.parent();
        }
        return true;
    }

    /** Holds the whole-model turn in a matrix map; no bone name can contain a NUL character. */
    private static final String MODEL_ROOT_KEY = "\u0000model";

    private static final Matrix4f IDENTITY = new Matrix4f();

    /**
     * A matrix map whose parent-less bones start from the whole-model turn. In this space +X is the entity's right, +Y
     * up and -Z its front (see toWorldVolume), so nose-down pitch is a negative turn about X and a roll to the right a
     * negative turn about Z.
     */
    private static Map<String, Matrix4f> seededMatrices(float pitchDegrees, float rollDegrees, float pivotY) {
        var matrices = new HashMap<String, Matrix4f>();
        if (pitchDegrees != 0.0F || rollDegrees != 0.0F) {
            matrices.put(
                MODEL_ROOT_KEY,
                new Matrix4f()
                    .translate(0.0F, pivotY, 0.0F)
                    .rotateX((float) Math.toRadians(-pitchDegrees))
                    .rotateZ((float) Math.toRadians(-rollDegrees))
                    .translate(0.0F, -pivotY, 0.0F)
            );
        }
        return matrices;
    }

    private Matrix4f matrixFor(Bone bone, Animation animation, double animationTimeSeconds, Map<String, Matrix4f> matrices) {
        var cached = matrices.get(bone.name());
        if (cached != null) {
            return cached;
        }
        var parentBone = bone.parent() == null ? null : bones.get(bone.parent());
        var parent = parentBone == null
            ? matrices.getOrDefault(MODEL_ROOT_KEY, IDENTITY)
            : matrixFor(parentBone, animation, animationTimeSeconds, matrices);
        var transform = new Matrix4f(parent);
        var pose = animation == null ? BonePose.DEFAULT : animation.poseFor(bone.name(), animationTimeSeconds);
        var pivot = modelVector(bone.pivot());
        var position = modelVector(pose.position());
        var rotation = bone.rotation().add(pose.rotation());

        transform
            .translate(position)
            .translate(pivot)
            .rotateZ((float) Math.toRadians(rotation.z()))
            .rotateY((float) Math.toRadians(-rotation.y()))
            .rotateX((float) Math.toRadians(-rotation.x()))
            .scale((float) pose.scale().x, (float) pose.scale().y, (float) pose.scale().z)
            .translate(-pivot.x, -pivot.y, -pivot.z);
        matrices.put(bone.name(), transform);
        return transform;
    }

    /** Visible cubes of a limb: never descends into hitbox groups, which belong to their own limb. */
    private void addVolumes(
        Bone bone,
        Resolved rule,
        boolean includeDescendants,
        LivingEntity entity,
        Map<String, Matrix4f> matrices,
        List<LimbHitboxVolume> output
    ) {
        if (bone == null || bone.name().startsWith(HITBOX_GROUP_PREFIX)) {
            return;
        }
        addCubes(bone, rule, entity, matrices, output);
        if (includeDescendants) {
            for (var childName : bone.children()) {
                addVolumes(bones.get(childName), rule, true, entity, matrices, output);
            }
        }
    }

    /** Every cube of a hitbox group, including groups nested inside it. */
    private void addGroupVolumes(
        Bone bone,
        Resolved rule,
        LivingEntity entity,
        Map<String, Matrix4f> matrices,
        List<LimbHitboxVolume> output
    ) {
        if (bone == null) {
            return;
        }
        addCubes(bone, rule, entity, matrices, output);
        for (var childName : bone.children()) {
            addGroupVolumes(bones.get(childName), rule, entity, matrices, output);
        }
    }

    private void addCubes(Bone bone, Resolved rule, LivingEntity entity, Map<String, Matrix4f> matrices, List<LimbHitboxVolume> output) {
        var boneTransform = matrices.get(bone.name());
        for (var cube : bone.cubes()) {
            // Bedrock geometry permits flat visual planes (one dimension is zero). They have no physical volume and
            // therefore cannot be a firearm hurtbox.
            if (cube.size().x <= 0.0D || cube.size().y <= 0.0D || cube.size().z <= 0.0D) {
                continue;
            }
            var transform = new Matrix4f(boneTransform);
            var pivot = modelVector(cube.pivot());
            transform
                .translate(pivot)
                .rotateZ((float) Math.toRadians(cube.rotation().z()))
                .rotateY((float) Math.toRadians(-cube.rotation().y()))
                .rotateX((float) Math.toRadians(-cube.rotation().x()))
                .translate(-pivot.x, -pivot.y, -pivot.z);
            var volume = toWorldVolume(entity, transform, cube, rule);
            if (volume != null) {
                output.add(volume);
            }
        }
    }

    private LimbHitboxVolume toWorldVolume(LivingEntity entity, Matrix4f transform, Cube cube, Resolved rule) {
        var inflate = cube.inflate() / 16.0F;
        var origin = cubeOrigin(cube.origin(), cube.size());
        var size = new Vector3f(
            (float) cube.size().x / 16.0F + inflate * 2.0F,
            (float) cube.size().y / 16.0F + inflate * 2.0F,
            (float) cube.size().z / 16.0F + inflate * 2.0F
        );
        if (size.x <= 0.0F || size.y <= 0.0F || size.z <= 0.0F) {
            return null;
        }
        origin.sub(inflate, inflate, inflate);
        var localCenter = transform.transformPosition(new Vector3f(origin).add(new Vector3f(size).mul(0.5F)));
        var localX = transform.transformDirection(new Vector3f(1, 0, 0)).normalize();
        var localY = transform.transformDirection(new Vector3f(0, 1, 0)).normalize();
        var localZ = transform.transformDirection(new Vector3f(0, 0, 1)).normalize();
        // Match AzEntityModelRenderer.applyRotations: it turns the model by 180 - body yaw. In particular, the
        // authored local Z axis maps to (sin(yaw), 0, -cos(yaw)); reversing it mirrors a tail to the mob's front.
        var yaw = Math.toRadians(entity.yBodyRot);
        var worldX = new Vec3(-Math.cos(yaw), 0.0D, -Math.sin(yaw));
        var worldY = new Vec3(0.0D, 1.0D, 0.0D);
        var worldZ = new Vec3(Math.sin(yaw), 0.0D, -Math.cos(yaw));
        var scale = entity.getScale();

        return new LimbHitboxVolume(
            rule.limbId(),
            entity.position()
                .add(worldX.scale(localCenter.x * scale))
                .add(worldY.scale(localCenter.y * scale))
                .add(worldZ.scale(localCenter.z * scale)),
            mapAxis(localX, worldX, worldY, worldZ),
            mapAxis(localY, worldX, worldY, worldZ),
            mapAxis(localZ, worldX, worldY, worldZ),
            new Vec3(size.x * scale * 0.5D, size.y * scale * 0.5D, size.z * scale * 0.5D).add(
                rule.padding() * scale,
                rule.padding() * scale,
                rule.padding() * scale
            ),
            rule.healthDamageMultiplier(),
            rule.limbDamageMultiplier(),
            rule.limbDamageThreshold(),
            rule.tags()
        );
    }

    private static Vec3 mapAxis(Vector3f axis, Vec3 worldX, Vec3 worldY, Vec3 worldZ) {
        return worldX.scale(axis.x).add(worldY.scale(axis.y)).add(worldZ.scale(axis.z)).normalize();
    }

    private static Vector3f modelVector(Vec3 vector) {
        return new Vector3f((float) -vector.x / 16.0F, (float) vector.y / 16.0F, (float) vector.z / 16.0F);
    }

    private static Vector3f cubeOrigin(Vec3 origin, Vec3 size) {
        return new Vector3f(
            (float) -(origin.x + size.x) / 16.0F,
            (float) origin.y / 16.0F,
            (float) origin.z / 16.0F
        );
    }

    private static Map<String, Bone> readBones(JsonObject root) {
        var result = new LinkedHashMap<String, Bone>();
        var entries = root.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones");
        for (var entry : entries) {
            var object = entry.getAsJsonObject();
            var name = object.get("name").getAsString();
            result.put(
                name,
                new Bone(
                    name,
                    stringOrNull(object, "parent"),
                    vectorOrZero(object.get("pivot")),
                    vectorOrZero(object.get("rotation")),
                    readCubes(object),
                    new ArrayList<>(),
                    readLocators(object)
                )
            );
        }
        for (var bone : result.values()) {
            if (bone.parent() != null && result.containsKey(bone.parent())) {
                result.get(bone.parent()).children().add(bone.name());
            }
        }
        return Map.copyOf(result);
    }

    /** Blockbench locators: a bare {@code [x, y, z]} or {@code {"offset": [...], "rotation": [...]}}, model space. */
    private static Map<String, Vec3> readLocators(JsonObject bone) {
        if (!bone.has("locators") || !bone.get("locators").isJsonObject()) {
            return Map.of();
        }
        var result = new LinkedHashMap<String, Vec3>();
        for (var entry : bone.getAsJsonObject("locators").entrySet()) {
            var value = entry.getValue();
            var position = value.isJsonObject() ? value.getAsJsonObject().get("offset") : value;
            if (position != null && position.isJsonArray() && position.getAsJsonArray().size() >= 3) {
                result.put(entry.getKey(), vector(position));
            }
        }
        return Map.copyOf(result);
    }

    private static List<Cube> readCubes(JsonObject bone) {
        if (!bone.has("cubes")) {
            return List.of();
        }
        var result = new ArrayList<Cube>();
        for (var entry : bone.getAsJsonArray("cubes")) {
            var object = entry.getAsJsonObject();
            result.add(
                new Cube(
                    vector(object.get("origin")),
                    vector(object.get("size")),
                    object.has("pivot") ? vector(object.get("pivot")) : Vec3.ZERO,
                    vectorOrZero(object.get("rotation")),
                    object.has("inflate") ? object.get("inflate").getAsFloat() : 0.0F
                )
            );
        }
        return List.copyOf(result);
    }

    private static Map<String, Animation> readAnimations(JsonObject root) {
        var result = new HashMap<String, Animation>();
        if (!root.has("animations")) {
            return Map.of();
        }
        for (var entry : root.getAsJsonObject("animations").entrySet()) {
            var animation = entry.getValue().getAsJsonObject();
            var length = animation.has("animation_length") ? animation.get("animation_length").getAsDouble() : 0.0D;
            // Same rule as before the move into BLib: a boolean true (or the string "true") loops; anything else holds.
            var loop = animation.has("loop") && animation.get("loop").isJsonPrimitive() && animation.get("loop").getAsBoolean();
            var poses = new HashMap<String, AnimatedBone>();
            var bones = animation.has("bones") ? animation.getAsJsonObject("bones") : new JsonObject();
            for (var boneEntry : bones.entrySet()) {
                var bone = boneEntry.getValue().getAsJsonObject();
                poses.put(
                    boneEntry.getKey(),
                    new AnimatedBone(
                        Channel.read(bone.get("rotation")),
                        Channel.read(bone.get("position")),
                        Channel.read(bone.get("scale"))
                    )
                );
            }
            result.put(entry.getKey(), new Animation(length, loop, Map.copyOf(poses)));
        }
        return Map.copyOf(result);
    }

    private static String stringOrNull(JsonObject object, String name) {
        return object.has(name) ? object.get(name).getAsString() : null;
    }

    private static Vec3 vectorOrZero(JsonElement value) {
        return value == null ? Vec3.ZERO : vector(value);
    }

    private static Vec3 vector(JsonElement value) {
        var array = value.getAsJsonArray();
        return new Vec3(array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble());
    }

    /**
     * The default hitbox rule for one limb, set in code by the mod that registers it. The {@code blib_hitboxes} data
     * file can override any of the numbers and add tags.
     *
     * @param limbId                 the limb this bone subtree belongs to
     * @param healthDamageMultiplier multiplies normal health damage on a hit (2.0 is a headshot)
     * @param limbDamageMultiplier   how much of a hit counts towards severing
     * @param limbDamageThreshold    damage the limb absorbs before detaching; 0 means never
     * @param padding                extra size around every visible cube, in blocks (not applied to hitbox groups)
     * @param includeDescendants     whether child bones belong to the limb too
     * @param tags                   free labels, see {@link LimbHitboxVolume#tags()}
     */
    public record LimbRule(
        ResourceLocation limbId,
        float healthDamageMultiplier,
        float limbDamageMultiplier,
        float limbDamageThreshold,
        double padding,
        boolean includeDescendants,
        Set<String> tags
    ) {

        public LimbRule {
            Objects.requireNonNull(limbId, "limbId");
            tags = tags == null ? Set.of() : Set.copyOf(tags);
        }

        public LimbRule(
            ResourceLocation limbId,
            float healthDamageMultiplier,
            float limbDamageMultiplier,
            float limbDamageThreshold,
            double padding,
            boolean includeDescendants
        ) {
            this(limbId, healthDamageMultiplier, limbDamageMultiplier, limbDamageThreshold, padding, includeDescendants, Set.of());
        }

        public LimbRule(
            ResourceLocation limbId,
            float healthDamageMultiplier,
            float limbDamageMultiplier,
            float limbDamageThreshold,
            double padding
        ) {
            this(limbId, healthDamageMultiplier, limbDamageMultiplier, limbDamageThreshold, padding, true, Set.of());
        }
    }

    private record Resolved(
        ResourceLocation limbId,
        float healthDamageMultiplier,
        float limbDamageMultiplier,
        float limbDamageThreshold,
        double padding,
        Set<String> tags
    ) {}

    private record Bone(
        String name,
        String parent,
        Vec3 pivot,
        Vec3 rotation,
        List<Cube> cubes,
        List<String> children,
        Map<String, Vec3> locators
    ) {}

    private record Cube(
        Vec3 origin,
        Vec3 size,
        Vec3 pivot,
        Vec3 rotation,
        float inflate
    ) {}

    private record BonePose(
        Vec3 rotation,
        Vec3 position,
        Vec3 scale
    ) {

        private static final BonePose DEFAULT = new BonePose(Vec3.ZERO, Vec3.ZERO, new Vec3(1.0D, 1.0D, 1.0D));
    }

    private record Animation(
        double lengthSeconds,
        boolean loop,
        Map<String, AnimatedBone> bones
    ) {

        private BonePose poseFor(String boneName, double timeSeconds) {
            var bone = bones.get(boneName);
            if (bone == null) {
                return BonePose.DEFAULT;
            }
            var time = loop && lengthSeconds > 0.0D
                ? timeSeconds % lengthSeconds
                : Mth.clamp(timeSeconds, 0.0D, lengthSeconds);
            return new BonePose(
                bone.rotation().at(time, Vec3.ZERO),
                bone.position().at(time, Vec3.ZERO),
                bone.scale().at(time, new Vec3(1, 1, 1))
            );
        }
    }

    private record AnimatedBone(
        Channel rotation,
        Channel position,
        Channel scale
    ) {}

    private record Channel(List<Keyframe> frames) {

        private static final Channel EMPTY = new Channel(List.of());

        private static Channel read(JsonElement value) {
            if (value == null) {
                return EMPTY;
            }
            var frames = new ArrayList<Keyframe>();
            if (value.isJsonArray() || value.isJsonPrimitive()) {
                frames.add(new Keyframe(0.0D, readValue(value)));
            } else {
                var object = value.getAsJsonObject();
                if (object.has("vector")) {
                    frames.add(new Keyframe(0.0D, readValue(object.get("vector"))));
                } else {
                    for (var entry : object.entrySet()) {
                        try {
                            var key = Double.parseDouble(entry.getKey());
                            var frame = entry.getValue();
                            if (frame.isJsonObject()) {
                                var frameObject = frame.getAsJsonObject();
                                frame = frameObject.has("post") ? frameObject.get("post") : frameObject.get("pre");
                                if (frame == null && frameObject.has("vector")) {
                                    frame = frameObject.get("vector");
                                }
                                if (frame != null && frame.isJsonObject()) {
                                    frame = frame.getAsJsonObject().get("vector");
                                }
                            }
                            if (frame != null) {
                                frames.add(new Keyframe(key, readValue(frame)));
                            }
                        } catch (NumberFormatException ignored) {
                            // Metadata such as easing belongs to the keyframe, not this channel's timeline.
                        }
                    }
                }
            }
            frames.sort(Comparator.comparingDouble(Keyframe::time));
            return frames.isEmpty() ? EMPTY : new Channel(List.copyOf(frames));
        }

        private static AnimatedVector readValue(JsonElement value) {
            if (value.isJsonPrimitive()) {
                var expression = value.getAsString();
                return new AnimatedVector(expression, expression, expression);
            }
            var array = value.getAsJsonArray();
            return new AnimatedVector(array.get(0).getAsString(), array.get(1).getAsString(), array.get(2).getAsString());
        }

        private Vec3 at(double time, Vec3 fallback) {
            if (frames.isEmpty()) {
                return fallback;
            }
            var before = frames.getFirst();
            var after = frames.getLast();
            for (var frame : frames) {
                if (frame.time() >= time) {
                    after = frame;
                    break;
                }
                before = frame;
            }
            if (before == after || after.time() <= before.time()) {
                return before.value().evaluate(time);
            }
            var fraction = (time - before.time()) / (after.time() - before.time());
            return before.value().evaluate(time).lerp(after.value().evaluate(time), fraction);
        }

    }

    private record Keyframe(
        double time,
        AnimatedVector value
    ) {}

    private record AnimatedVector(
        String x,
        String y,
        String z
    ) {

        private Vec3 evaluate(double animationTime) {
            return new Vec3(
                MathExpression.evaluate(x, animationTime),
                MathExpression.evaluate(y, animationTime),
                MathExpression.evaluate(z, animationTime)
            );
        }

    }

    /**
     * The numeric Molang subset used by limb poses: arithmetic, parentheses, unary minus, {@code query.anim_time}, and
     * the math functions {@code sin cos clamp abs min max sqrt floor ceil round pow lerp} (trig in degrees, as Molang
     * defines). Anything else evaluates to 0, exactly as before, so an unknown expression can never throw on the server
     * tick.
     */
    static final class MathExpression {

        private final String input;

        private int cursor;

        private MathExpression(String input, double animationTime) {
            this.input = input.replace("Math.", "")
                .replace("math.", "")
                .replace("query.anim_time", "(" + animationTime + ")")
                .replace("q.anim_time", "(" + animationTime + ")")
                .replace(" ", "");
        }

        static double evaluate(String expression, double animationTime) {
            try {
                var parser = new MathExpression(expression, animationTime);
                var value = parser.expression();
                return parser.cursor == parser.input.length() && Double.isFinite(value) ? value : 0.0D;
            } catch (RuntimeException ignored) {
                return 0.0D;
            }
        }

        private double expression() {
            var value = term();
            while (cursor < input.length() && (input.charAt(cursor) == '+' || input.charAt(cursor) == '-')) {
                value = input.charAt(cursor++) == '+' ? value + term() : value - term();
            }
            return value;
        }

        private double term() {
            var value = factor();
            while (cursor < input.length() && (input.charAt(cursor) == '*' || input.charAt(cursor) == '/')) {
                value = input.charAt(cursor++) == '*' ? value * factor() : value / factor();
            }
            return value;
        }

        private double factor() {
            if (cursor < input.length() && input.charAt(cursor) == '-') {
                cursor++;
                return -factor();
            }
            if (cursor < input.length() && input.charAt(cursor) == '+') {
                cursor++;
                return factor();
            }
            if (cursor < input.length() && input.charAt(cursor) == '(') {
                cursor++;
                var value = expression();
                require(')');
                return value;
            }
            var start = cursor;
            while (cursor < input.length() && (Character.isLetter(input.charAt(cursor)) || input.charAt(cursor) == '_')) {
                cursor++;
            }
            if (start != cursor) {
                var function = input.substring(start, cursor).toLowerCase(java.util.Locale.ROOT);
                require('(');
                var arguments = new ArrayList<Double>();
                arguments.add(expression());
                while (cursor < input.length() && input.charAt(cursor) == ',') {
                    cursor++;
                    arguments.add(expression());
                }
                require(')');
                return call(function, arguments);
            }
            while (cursor < input.length() && (Character.isDigit(input.charAt(cursor)) || input.charAt(cursor) == '.')) {
                cursor++;
            }
            // Scientific notation can appear once query.anim_time has been substituted (e.g. 1.0E-4).
            if (cursor < input.length() && (input.charAt(cursor) == 'E' || input.charAt(cursor) == 'e')) {
                cursor++;
                if (cursor < input.length() && (input.charAt(cursor) == '-' || input.charAt(cursor) == '+')) {
                    cursor++;
                }
                while (cursor < input.length() && Character.isDigit(input.charAt(cursor))) {
                    cursor++;
                }
            }
            return Double.parseDouble(input.substring(start, cursor));
        }

        private static double call(String function, List<Double> arguments) {
            var a = arguments.get(0);
            return switch (function) {
                // Molang's trig functions take degrees. Java's take radians.
                case "sin" -> Math.sin(Math.toRadians(a));
                case "cos" -> Math.cos(Math.toRadians(a));
                case "abs" -> Math.abs(a);
                case "sqrt" -> Math.sqrt(a);
                case "floor" -> Math.floor(a);
                case "ceil" -> Math.ceil(a);
                case "round" -> Math.round(a);
                case "min" -> arguments.size() >= 2 ? Math.min(a, arguments.get(1)) : a;
                case "max" -> arguments.size() >= 2 ? Math.max(a, arguments.get(1)) : a;
                case "pow" -> arguments.size() >= 2 ? Math.pow(a, arguments.get(1)) : a;
                case "clamp" -> arguments.size() >= 3 ? Mth.clamp(a, arguments.get(1), arguments.get(2)) : a;
                case "lerp" -> arguments.size() >= 3 ? a + (arguments.get(1) - a) * arguments.get(2) : a;
                default -> 0.0D;
            };
        }

        private void require(char expected) {
            if (cursor >= input.length() || input.charAt(cursor++) != expected) {
                throw new IllegalArgumentException("Expected " + expected);
            }
        }
    }
}
