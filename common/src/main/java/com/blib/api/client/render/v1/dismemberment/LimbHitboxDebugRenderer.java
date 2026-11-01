package com.blib.api.client.render.v1.dismemberment;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

import com.blib.api.common.dismemberment.v1.Dismemberable;
import com.blib.api.common.dismemberment.v1.LimbDefinitionRegistry;
import com.blib.api.common.dismemberment.v1.hitbox.LimbHitboxRegistry;
import com.blib.api.common.dismemberment.v1.hitbox.LimbHitboxVolume;

/**
 * The F3+B overlay for limb hitboxes. Drawn for every entity that has any, from any mod, right after vanilla's own
 * hitbox box.
 * <p>
 * Colours follow AzureLib's scheme so they read the same in both tools: <b>red</b> boxes multiply damage (above 1x),
 * <b>blue</b> boxes reduce it (below 1x), <b>green</b> boxes are 1x. Each limb gets a label with its name, multiplier,
 * sever progress and tags; the entity gets a header with its health, how many boxes it has, and any detached limbs.
 * F3+8 toggles the labels.
 * </p>
 * <p>
 * Only runs while F3+B is on. It has no gameplay effect and no cost otherwise.
 * </p>
 */
public final class LimbHitboxDebugRenderer {

    /** Supplies boxes taken from the rendered pose. Return {@code null} for entities it does not handle. */
    @FunctionalInterface
    public interface VolumeSource {

        List<LimbHitboxVolume> volumes(LivingEntity entity);
    }

    /** Adds mod-specific lines (for example "CRAWLING") under the entity's header. */
    @FunctionalInterface
    public interface DiagnosticLines {

        void append(LivingEntity entity, List<String> lines);
    }

    private static final List<VolumeSource> SOURCES = new CopyOnWriteArrayList<>();

    private static final List<DiagnosticLines> EXTRA_LINES = new CopyOnWriteArrayList<>();

    private static final int[][] EDGES = {
        { 0, 1 },
        { 0, 2 },
        { 0, 4 },
        { 1, 3 },
        { 1, 5 },
        { 2, 3 },
        { 2, 6 },
        { 3, 7 },
        { 4, 5 },
        { 4, 6 },
        { 5, 7 },
        { 6, 7 }
    };

    private static final double LABEL_RANGE = 24.0D;

    private static boolean labelsVisible = true;

    private static boolean toggleHeld;

    public static void registerVolumeSource(VolumeSource source) {
        SOURCES.add(source);
    }

    public static void registerDiagnosticLines(DiagnosticLines lines) {
        EXTRA_LINES.add(lines);
    }

    /** Called by BLib right after vanilla draws an entity's F3+B box. The pose stack sits on the entity. */
    public static void render(Entity entity, float partialTick, PoseStack poseStack, MultiBufferSource buffers) {
        if (!(entity instanceof LivingEntity living)) {
            return;
        }
        var minecraft = Minecraft.getInstance();
        tickToggle(minecraft);

        List<LimbHitboxVolume> volumes = null;
        var rendered = false;
        for (var source : SOURCES) {
            volumes = source.volumes(living);
            if (volumes != null) {
                rendered = true;
                break;
            }
        }
        if (volumes == null) {
            volumes = LimbHitboxRegistry.getHitboxes(living);
        }
        var hasDefinitions = living instanceof Dismemberable && !LimbDefinitionRegistry.getDefinitions(living).isEmpty();
        if (volumes.isEmpty() && !hasDefinitions) {
            return;
        }

        var origin = living.getPosition(partialTick);
        var lines = buffers.getBuffer(RenderType.lines());
        for (var volume : volumes) {
            drawBox(poseStack, lines, volume, origin, colorFor(volume.healthDamageMultiplier()));
        }

        if (!labelsVisible || !isLookedAt(minecraft, living, partialTick)) {
            return;
        }
        drawLimbLabels(poseStack, buffers, minecraft, living, volumes, origin);
        drawHeader(poseStack, buffers, minecraft, living, volumes.size(), rendered);
    }

    private static void drawBox(PoseStack poseStack, VertexConsumer consumer, LimbHitboxVolume volume, Vec3 origin, int color) {
        var x = volume.xAxis().scale(volume.halfExtents().x);
        var y = volume.yAxis().scale(volume.halfExtents().y);
        var z = volume.zAxis().scale(volume.halfExtents().z);
        var c = volume.center().subtract(origin);
        var corners = new Vec3[] {
            c.subtract(x).subtract(y).subtract(z),
            c.add(x).subtract(y).subtract(z),
            c.subtract(x).add(y).subtract(z),
            c.add(x).add(y).subtract(z),
            c.subtract(x).subtract(y).add(z),
            c.add(x).subtract(y).add(z),
            c.subtract(x).add(y).add(z),
            c.add(x).add(y).add(z)
        };
        var r = ((color >> 16) & 0xFF) / 255.0F;
        var g = ((color >> 8) & 0xFF) / 255.0F;
        var b = (color & 0xFF) / 255.0F;
        var pose = poseStack.last();
        for (var edge : EDGES) {
            var from = corners[edge[0]];
            var to = corners[edge[1]];
            var normal = to.subtract(from).normalize();
            consumer.addVertex(pose, (float) from.x, (float) from.y, (float) from.z)
                .setColor(r, g, b, 1.0F)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
            consumer.addVertex(pose, (float) to.x, (float) to.y, (float) to.z)
                .setColor(r, g, b, 1.0F)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
        }
    }

    /** Red above 1x, blue below 1x, green at 1x. */
    public static int colorFor(float multiplier) {
        if (multiplier > 1.001F) {
            return 0xFF3333;
        }
        if (multiplier < 0.999F) {
            return 0x4477FF;
        }
        return 0x33FF55;
    }

    private static void drawLimbLabels(
        PoseStack poseStack,
        MultiBufferSource buffers,
        Minecraft minecraft,
        LivingEntity living,
        List<LimbHitboxVolume> volumes,
        Vec3 origin
    ) {
        // One label per limb, on its biggest box, so a limb made of many cubes is not buried in text.
        var largest = new LinkedHashMap<ResourceLocation, LimbHitboxVolume>();
        for (var volume : volumes) {
            var current = largest.get(volume.limbId());
            if (current == null || size(volume) > size(current)) {
                largest.put(volume.limbId(), volume);
            }
        }
        var manager = living instanceof Dismemberable dismemberable ? dismemberable.getDismembermentManager() : null;
        var entityPath = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(living.getType()).getPath();
        for (var volume : largest.values()) {
            var text = new StringBuilder(shortName(entityPath, volume.limbId()))
                .append("  x")
                .append(String.format(Locale.ROOT, "%.2f", volume.healthDamageMultiplier()).replaceAll("0+$", "").replaceAll("\\.$", ""));
            if (volume.hasLimbDamagePool() && manager != null) {
                text.append(
                    String.format(Locale.ROOT, "  %.1f/%.1f", manager.getLimbDamage(volume.limbId()), volume.limbDamageThreshold())
                );
            }
            if (!volume.tags().isEmpty()) {
                text.append("  [").append(String.join(", ", volume.tags().stream().sorted().toList())).append(']');
            }
            drawText(
                poseStack,
                buffers,
                minecraft,
                volume.center().subtract(origin),
                List.of(text.toString()),
                0xFF000000 | colorFor(volume.healthDamageMultiplier()),
                0.0125F
            );
        }
    }

    private static void drawHeader(
        PoseStack poseStack,
        MultiBufferSource buffers,
        Minecraft minecraft,
        LivingEntity living,
        int boxes,
        boolean rendered
    ) {
        var lines = new ArrayList<String>();
        lines.add(String.format(Locale.ROOT, "%s  %.1f/%.1f", living.getName().getString(), living.getHealth(), living.getMaxHealth()));
        lines.add(boxes + " limb boxes (" + (rendered ? "rendered pose" : "server pose") + ")");
        if (living instanceof Dismemberable dismemberable) {
            var entityPath = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(living.getType()).getPath();
            for (var definition : LimbDefinitionRegistry.getDefinitions(living)) {
                if (dismemberable.getDismembermentManager().isDetached(definition.id())) {
                    lines.add(shortName(entityPath, definition.id()) + ": DETACHED");
                }
            }
        }
        for (var extra : EXTRA_LINES) {
            extra.append(living, lines);
        }
        drawText(poseStack, buffers, minecraft, new Vec3(0.0D, living.getBbHeight() + 0.45D, 0.0D), lines, 0xFFFFFFFF, 0.025F);
    }

    private static void drawText(
        PoseStack poseStack,
        MultiBufferSource buffers,
        Minecraft minecraft,
        Vec3 at,
        List<String> lines,
        int color,
        float scale
    ) {
        var font = minecraft.font;
        poseStack.pushPose();
        poseStack.translate(at.x, at.y, at.z);
        poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        // Same billboard orientation as EntityRenderer#renderNameTag.
        poseStack.scale(scale, -scale, scale);
        var y = 0.0F;
        for (var line : lines) {
            var lineColor = line.endsWith("DETACHED") ? 0xFFFF5555 : color;
            font.drawInBatch(
                Component.literal(line),
                -font.width(line) / 2.0F,
                y,
                lineColor,
                true,
                poseStack.last().pose(),
                buffers,
                Font.DisplayMode.NORMAL,
                0x55000000,
                0xF000F0
            );
            y += 10.0F;
        }
        poseStack.popPose();
    }

    /** {@code drone_left_arm} on a {@code drone} (or {@code aberrant_drone}) reads as {@code left_arm}. */
    static String shortName(String entityPath, ResourceLocation limbId) {
        var path = limbId.getPath();
        var candidate = entityPath;
        while (true) {
            if (path.startsWith(candidate + "_")) {
                return path.substring(candidate.length() + 1);
            }
            var cut = candidate.indexOf('_');
            if (cut < 0) {
                return path;
            }
            candidate = candidate.substring(cut + 1);
        }
    }

    private static double size(LimbHitboxVolume volume) {
        return volume.halfExtents().x * volume.halfExtents().y * volume.halfExtents().z;
    }

    private static boolean isLookedAt(Minecraft minecraft, LivingEntity living, float partialTick) {
        var player = minecraft.player;
        if (player == null) {
            return false;
        }
        var toEntity = living.getPosition(partialTick)
            .add(0.0D, living.getBbHeight() * 0.5D, 0.0D)
            .subtract(player.getEyePosition(partialTick));
        return toEntity.lengthSqr() <= LABEL_RANGE * LABEL_RANGE
            && player.getViewVector(partialTick).dot(toEntity.normalize()) >= 0.65D;
    }

    private static void tickToggle(Minecraft minecraft) {
        var window = minecraft.getWindow().getWindow();
        var held = InputConstants.isKeyDown(window, InputConstants.KEY_F3) && InputConstants.isKeyDown(window, InputConstants.KEY_8);
        if (held && !toggleHeld) {
            labelsVisible = !labelsVisible;
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(
                    Component.literal("[BLib] Limb hitbox labels " + (labelsVisible ? "on" : "off") + ". Press F3+8 to toggle."),
                    false
                );
            }
        }
        toggleHeld = held;
    }

    private LimbHitboxDebugRenderer() {}
}
