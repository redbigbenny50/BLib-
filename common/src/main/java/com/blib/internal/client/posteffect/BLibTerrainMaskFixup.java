package com.blib.internal.client.posteffect;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.ApiStatus;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;

import com.blib.mod.BLib;

/**
 * Repairs the auxiliary attachments for Sodium-rendered terrain.
 * <p>
 * Sodium draws terrain with its own shaders, which write only a colour. Every auxiliary attachment therefore keeps
 * whatever was already in it for terrain fragments — and an unpatched fragment shader that declares a single
 * {@code out vec4 fragColor} while several draw buffers are bound leaves those attachments as spec-undefined writes,
 * which on some drivers is structured garbage rather than zero. That is the same hazard the
 * {@code BLibEntityShaderPatcher.Category.PASSTHROUGH} path exists to prevent for sky shaders, and this pass is its
 * equivalent for terrain: it stamps the terrain category into the mask and deterministic zero into the rest.
 * <h2>Why zeroing the rest matters as much as the category</h2> Measurement settled this. With the mask alone repaired,
 * a read-back at the centre of the screen returned the terrain category both immediately after this pass <em>and</em>
 * again at post-effect time — the classification was correct end to end — while the view still rendered terrain as
 * though it were hot. The thermal post computes {@code terrainHeat = blockLight + 1.50 * emission}, taking
 * {@code blockLight} from {@code entityDrawData.g} and {@code emission} from {@code entitySpecular.a}. Those are
 * attachments 4 and 5, equally unwritten by Sodium. A pixel classified perfectly can still render as a heat source if
 * the numbers feeding its heat are garbage.
 * <p>
 * This also accounts for the horizon-shaped boundary that made the bug look like a screen-space artifact: the sky pass
 * writes the auxiliary attachments across the region it covers, terrain never overwrites them, and the discontinuity
 * left behind sits exactly at the horizon and travels with the camera.
 * <h2>Ordering</h2> The pass runs after the terrain layers and before entities and particles, so an unconditional write
 * is safe — entities and particles draw afterwards and write their own categories and lighting over the top.
 * <p>
 * ⚠ Translucent terrain (water, glass, ice) is drawn <em>later</em> than this pass and is equally unwritten by Sodium,
 * so those surfaces are not repaired here.
 * <h2>Two implementations of the geometry test</h2> <b>SAMPLE (default).</b> Binds a scratch framebuffer carrying the
 * six auxiliary textures, samples the main target's depth texture explicitly, and discards where depth is at the far
 * plane. No depth test, no depth func, no {@code glDrawBuffers} remap of the main target, and no reliance on
 * multiple-render-target write semantics.
 * <p>
 * This is not the feedback loop the original implementation avoided. That hazard is sampling an attachment <em>of the
 * bound framebuffer</em>: here the attachments are written but never sampled, and the depth texture is sampled but is
 * not attached to the scratch target.
 * <p>
 * <b>DEPTH_TEST (rollback).</b> The original mechanism, unchanged: a fullscreen quad at {@code z = 1.0} drawn with
 * {@code GL_GREATER} into attachment 1 of the main MRT, mask only. Kept verbatim so the previous behaviour is one JVM
 * argument away rather than a jar swap.
 * <h2>Switches</h2>
 * <ul>
 * <li>{@code -Dblib.terrainMaskFixup=false} — disable the pass entirely.</li>
 * <li>{@code -Dblib.terrainMaskFixup.mode=depthtest} — roll back to the original mechanism.</li>
 * <li>{@code -Dblib.terrainMaskFixup.auxZero=false} — write only the mask, leaving the heat attachments alone. The A/B
 * for the fix above.</li>
 * <li>{@code -Dblib.terrainMaskFixup.lateRepair=true} — additionally re-apply the category just before the post effect,
 * blended with {@code GL_MAX}. Off by default: measurement showed nothing clobbers the mask, so this is retained only
 * as a diagnostic.</li>
 * <li>{@code -Dblib.terrainMaskFixup.debugReadback=true} — log the mask byte and the draw-data bytes at the centre of
 * the screen about once a second.</li>
 * </ul>
 * <p>
 * The GLSL lives here as strings rather than in {@code assets/}: these programs are compiled directly and never
 * registered with vanilla's shader system, so they stay invisible to mods that walk and re-parse the vanilla shader
 * registry.
 */
@ApiStatus.Internal
public final class BLibTerrainMaskFixup {

    private static final int GL_COLOR_ATTACHMENT1 = 36065;

    private static final int GL_FUNC_ADD = 32774;

    private static final int GL_MAX = 32776;

    private static final int GL_RG = 33319;

    private static final String MODE_PROPERTY = "blib.terrainMaskFixup.mode";

    private static final String MODE_DEPTH_TEST = "depthtest";

    private static final String AUX_ZERO_PROPERTY = "blib.terrainMaskFixup.auxZero";

    private static final String LATE_REPAIR_PROPERTY = "blib.terrainMaskFixup.lateRepair";

    private static final String DEBUG_READBACK_PROPERTY = "blib.terrainMaskFixup.debugReadback";

    /** Must match {@code BLibEntityShaderPatcher.Category.TERRAIN}. */
    private static final float TERRAIN_MASK_VALUE = 0.5F;

    private static final long READBACK_INTERVAL_IN_MILLIS = 1000L;

    /** Scratch-FBO attachment order. The main target's numbering is one higher — mask is attachment 1 there. */
    private static final int SCRATCH_MASK = 0;

    private static final int SCRATCH_DRAW_DATA = 3;

    private static final String DEPTH_TEST_VERTEX_SOURCE = """
        #version 150

        void main() {
            // Fullscreen triangle from gl_VertexID alone — no vertex buffer, no attributes, no VAO contents.
            vec2 corner = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);

            // z = 1.0 is the far plane: with GL_GREATER this fragment survives only where something nearer was drawn.
            gl_Position = vec4(corner * 2.0 - 1.0, 1.0, 1.0);
        }
        """;

    private static final String DEPTH_TEST_FRAGMENT_SOURCE = """
        #version 150

        out vec2 blibEntityMask;

        void main() {
            // R = category (terrain). G = the background-entity lane, which terrain never participates in.
            blibEntityMask = vec2(%s, 0.0);
        }
        """.formatted(TERRAIN_MASK_VALUE);

    /** Same fullscreen triangle, but depth is irrelevant here — the test is done in the fragment shader. */
    private static final String SAMPLE_VERTEX_SOURCE = """
        #version 150

        void main() {
            vec2 corner = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);

            gl_Position = vec4(corner * 2.0 - 1.0, 0.0, 1.0);
        }
        """;

    /**
     * Writes the terrain category plus deterministic zero for every other channel. Outputs are bound to locations by
     * name before linking, so this stays valid GLSL 150 rather than needing explicit layout qualifiers.
     * <p>
     * {@code texelFetch} with {@code gl_FragCoord.xy} is an exact 1:1 texel mapping because the viewport is set to the
     * attachments' own dimensions — no filtering, no half-texel offset, and no varying to interpolate.
     */
    private static final String SAMPLE_FRAGMENT_SOURCE = """
        #version 150

        uniform sampler2D BlibDepth;

        out vec2 blibEntityMask;
        out vec4 blibEntityLightmap;
        out vec4 blibEntityNormal;
        out vec4 blibEntityDrawData;
        out vec4 blibEntitySpecular;
        out vec4 blibEntityMaterialId;

        void main() {
            float depth = texelFetch(BlibDepth, ivec2(gl_FragCoord.xy), 0).r;

            // Untouched sky is still at the far plane. Everything nearer had geometry drawn into it.
            if (depth >= 1.0) {
                discard;
            }

            // R = category (terrain). G = the background-entity lane, which terrain never participates in.
            blibEntityMask = vec2(%s, 0.0);

            // Deterministic zero, exactly as the PASSTHROUGH patch does for sky shaders. Terrain under Sodium has no
            // real lighting data to offer, and zero reads as "cold" rather than as whatever the driver left behind.
            blibEntityLightmap = vec4(0.0, 0.0, 0.0, 1.0);
            blibEntityNormal = vec4(0.0, 0.0, 0.0, 1.0);
            blibEntityDrawData = vec4(0.0, 0.0, 0.0, 1.0);
            blibEntitySpecular = vec4(0.0, 0.0, 0.0, 1.0);
            blibEntityMaterialId = vec4(0.0, 0.0, 0.0, 1.0);
        }
        """.formatted(TERRAIN_MASK_VALUE);

    private static final String[] SAMPLE_OUTPUT_NAMES = {
        "blibEntityMask",
        "blibEntityLightmap",
        "blibEntityNormal",
        "blibEntityDrawData",
        "blibEntitySpecular",
        "blibEntityMaterialId"
    };

    private static int depthTestProgramId = -1;

    private static int sampleProgramId = -1;

    private static int sampleDepthUniformLocation = -1;

    private static int vertexArrayId = -1;

    private static int scratchFrameBufferId = -1;

    private static int scratchAttachedTextureId = -1;

    private static ByteBuffer readbackBuffer;

    private static long lastReadbackAtMillis;

    private static boolean lateReadbackPending;

    private static boolean failed;

    private BLibTerrainMaskFixup() {
        throw new UnsupportedOperationException();
    }

    /**
     * Stamps the terrain category and deterministic zero over every pixel that has geometry. Safe to call
     * unconditionally — it returns immediately unless Sodium is present and the MRT attachments exist.
     */
    public static void run() {
        if (!isEnabled()) {
            return;
        }

        if (isDepthTestMode()) {
            runDepthTest();
        } else {
            runDepthSample(false);
        }
    }

    /**
     * Re-applies the category immediately before the post effect samples the mask, blended with {@code GL_MAX} so it
     * can only raise a category and never overwrite an entity, a held item or the sky.
     * <p>
     * Off unless explicitly enabled: an early/late read-back pair showed the mask already correct at consumption time,
     * so this repairs nothing in practice. Kept because it costs one flag and it is the instrument that would catch a
     * clobber if a future Sodium introduces one.
     */
    public static void runLateRepair() {
        if (!isEnabled() || isDepthTestMode()) {
            return;
        }

        if (!"true".equalsIgnoreCase(System.getProperty(LATE_REPAIR_PROPERTY))) {
            return;
        }

        runDepthSample(true);
    }

    public static void destroy() {
        if (depthTestProgramId != -1) {
            GL20.glDeleteProgram(depthTestProgramId);
            depthTestProgramId = -1;
        }

        if (sampleProgramId != -1) {
            GL20.glDeleteProgram(sampleProgramId);
            sampleProgramId = -1;
            sampleDepthUniformLocation = -1;
        }

        if (vertexArrayId != -1) {
            GL30.glDeleteVertexArrays(vertexArrayId);
            vertexArrayId = -1;
        }

        destroyScratchFrameBuffer();
    }

    private static boolean isEnabled() {
        return !failed && BLibSodiumCompat.isTerrainMaskFixupEnabled() && BLibMainTargetMRT.isAttached();
    }

    private static boolean isDepthTestMode() {
        return MODE_DEPTH_TEST.equalsIgnoreCase(System.getProperty(MODE_PROPERTY));
    }

    private static boolean isAuxZeroEnabled() {
        return !"false".equalsIgnoreCase(System.getProperty(AUX_ZERO_PROPERTY));
    }

    // ---------------------------------------------------------------------------------------------------------
    // Depth-sampling path (default) — shared by the main pass and the optional late repair
    // ---------------------------------------------------------------------------------------------------------

    /**
     * @param lateRepair when true only the mask is written, blended with {@code GL_MAX}, and the read-back samples
     *                   before the draw so it reports what the post effect would otherwise have consumed.
     */
    private static void runDepthSample(boolean lateRepair) {
        var mainTarget = Minecraft.getInstance().getMainRenderTarget();

        if (mainTarget == null) {
            return;
        }

        var depthTextureId = mainTarget.getDepthTextureId();

        if (depthTextureId <= 0) {
            return;
        }

        if (!ensureSampleProgram() || !ensureScratchFrameBuffer()) {
            return;
        }

        // Every raw GL call below is paired with a restore of the exact previous value, which is what keeps
        // GlStateManager's cache truthful; state GlStateManager tracks is changed through GlStateManager itself.
        var previousFrameBuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        var previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        var previousVertexArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        var previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        var previousBlendEquation = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);
        var depthTestWasEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        var blendWasEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        var cullWasEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        var scissorWasEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);

        var previousViewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, previousViewport);

        GlStateManager._activeTexture(GL13.GL_TEXTURE0);

        var previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);

        try {
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, scratchFrameBufferId);
            GlStateManager._viewport(0, 0, BLibMainTargetMRT.width(), BLibMainTargetMRT.height());

            // Outputs whose draw buffer is GL_NONE are discarded, so one program serves every combination.
            GL30.glDrawBuffers(selectDrawBuffers(lateRepair));

            if (lateRepair) {
                debugReadback("late");
            }

            // The scratch target carries no depth attachment, so the test would be meaningless as well as unwanted.
            GlStateManager._disableDepthTest();
            GlStateManager._disableCull();

            if (scissorWasEnabled) {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            }

            if (lateRepair) {
                // GL_MAX ignores the blend factors, but they are set anyway so no stale factor state is implied.
                GlStateManager._enableBlend();
                GlStateManager._blendFunc(GL11.GL_ONE, GL11.GL_ONE);
                GlStateManager._blendEquation(GL_MAX);
            } else {
                GlStateManager._disableBlend();
            }

            GlStateManager._glUseProgram(sampleProgramId);
            GlStateManager._bindTexture(depthTextureId);
            GL20.glUniform1i(sampleDepthUniformLocation, 0);

            GL30.glBindVertexArray(vertexArrayId);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);

            if (!lateRepair) {
                debugReadback("early");
            }
        } finally {
            if (lateRepair) {
                GlStateManager._blendEquation(previousBlendEquation == 0 ? GL_FUNC_ADD : previousBlendEquation);
                RenderSystem.defaultBlendFunc();
            }

            if (blendWasEnabled) {
                GlStateManager._enableBlend();
            } else {
                GlStateManager._disableBlend();
            }

            GlStateManager._bindTexture(previousTexture);
            GlStateManager._activeTexture(previousActiveTexture);

            GL30.glBindVertexArray(previousVertexArray);
            GlStateManager._glUseProgram(previousProgram);

            GlStateManager._viewport(previousViewport[0], previousViewport[1], previousViewport[2], previousViewport[3]);
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFrameBuffer);

            if (depthTestWasEnabled) {
                GlStateManager._enableDepthTest();
            } else {
                GlStateManager._disableDepthTest();
            }

            if (cullWasEnabled) {
                GlStateManager._enableCull();
            } else {
                GlStateManager._disableCull();
            }

            if (scissorWasEnabled) {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
            }
        }
    }

    /** Mask only for the late repair and when aux zeroing is switched off; all six otherwise. */
    private static int[] selectDrawBuffers(boolean lateRepair) {
        if (lateRepair || !isAuxZeroEnabled()) {
            return new int[] {
                GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_NONE,
                GL11.GL_NONE,
                GL11.GL_NONE,
                GL11.GL_NONE,
                GL11.GL_NONE
            };
        }

        return new int[] {
            GL30.GL_COLOR_ATTACHMENT0,
            GL30.GL_COLOR_ATTACHMENT1,
            GL30.GL_COLOR_ATTACHMENT2,
            GL30.GL_COLOR_ATTACHMENT3,
            GL30.GL_COLOR_ATTACHMENT4,
            GL30.GL_COLOR_ATTACHMENT5
        };
    }

    /**
     * Reads the mask and the draw-data at the centre of the screen. Called with the scratch framebuffer bound, so the
     * attachments are addressable directly and nothing else needs rebinding.
     */
    private static void debugReadback(String label) {
        if (!"true".equalsIgnoreCase(System.getProperty(DEBUG_READBACK_PROPERTY))) {
            return;
        }

        var now = System.currentTimeMillis();

        if ("early".equals(label)) {
            if (now - lastReadbackAtMillis < READBACK_INTERVAL_IN_MILLIS) {
                return;
            }

            lastReadbackAtMillis = now;
            lateReadbackPending = true;
        } else {
            // The late sample rides the window the early one opened, so the two lines always arrive as a pair.
            if (!lateReadbackPending) {
                return;
            }

            lateReadbackPending = false;
        }

        if (readbackBuffer == null) {
            readbackBuffer = BufferUtils.createByteBuffer(4);
        }

        var x = BLibMainTargetMRT.width() / 2;
        var y = BLibMainTargetMRT.height() / 2;

        readbackBuffer.clear();
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0 + SCRATCH_MASK);
        GL11.glReadPixels(x, y, 1, 1, GL_RG, GL11.GL_UNSIGNED_BYTE, readbackBuffer);

        var category = readbackBuffer.get(0) & 0xFF;
        var backgroundLane = readbackBuffer.get(1) & 0xFF;

        readbackBuffer.clear();
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0 + SCRATCH_DRAW_DATA);
        GL11.glReadPixels(x, y, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, readbackBuffer);

        var drawR = readbackBuffer.get(0) & 0xFF;
        var drawG = readbackBuffer.get(1) & 0xFF;
        var drawB = readbackBuffer.get(2) & 0xFF;
        var drawA = readbackBuffer.get(3) & 0xFF;

        BLib.LOGGER.info(
            "[BLib] Mask at screen centre ({}): category={} (128 = terrain, 0 = sky), backgroundLane={}"
                + " | drawData r={} g={} b={} a={} (g drives terrain heat; 0 = cold)",
            label,
            category,
            backgroundLane,
            drawR,
            drawG,
            drawB,
            drawA
        );
    }

    private static boolean ensureSampleProgram() {
        if (sampleProgramId != -1) {
            return true;
        }

        var programId = link(SAMPLE_VERTEX_SOURCE, SAMPLE_FRAGMENT_SOURCE, SAMPLE_OUTPUT_NAMES);

        if (programId == -1) {
            return false;
        }

        sampleProgramId = programId;
        sampleDepthUniformLocation = GL20.glGetUniformLocation(sampleProgramId, "BlibDepth");

        if (sampleDepthUniformLocation == -1) {
            GL20.glDeleteProgram(sampleProgramId);
            sampleProgramId = -1;

            return giveUp("depth sampler uniform was optimised out");
        }

        return ensureVertexArray();
    }

    /**
     * The scratch framebuffer carries the six auxiliary textures, in the same order as the main target but shifted down
     * one so the mask sits at attachment 0. Rebuilt whenever the mask texture is reallocated — which happens on every
     * window resize, since {@code BLibMainTargetMRT.attach} generates fresh texture ids.
     */
    private static boolean ensureScratchFrameBuffer() {
        var maskTextureId = BLibMainTargetMRT.entityMaskTextureId();

        if (maskTextureId <= 0) {
            return false;
        }

        if (scratchFrameBufferId != -1 && scratchAttachedTextureId == maskTextureId) {
            return true;
        }

        destroyScratchFrameBuffer();

        var previousFrameBuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);

        scratchFrameBufferId = GL30.glGenFramebuffers();

        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, scratchFrameBufferId);

        attachScratchTexture(0, maskTextureId);
        attachScratchTexture(1, BLibMainTargetMRT.entityLightmapTextureId());
        attachScratchTexture(2, BLibMainTargetMRT.entityNormalTextureId());
        attachScratchTexture(3, BLibMainTargetMRT.entityDrawDataTextureId());
        attachScratchTexture(4, BLibMainTargetMRT.entitySpecularTextureId());
        attachScratchTexture(5, BLibMainTargetMRT.entityMaterialIdTextureId());

        GL30.glDrawBuffers(selectDrawBuffers(false));

        var status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);

        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFrameBuffer);

        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            destroyScratchFrameBuffer();

            return giveUp("scratch framebuffer incomplete: 0x" + Integer.toHexString(status));
        }

        scratchAttachedTextureId = maskTextureId;

        return true;
    }

    private static void attachScratchTexture(int index, int textureId) {
        if (textureId <= 0) {
            return;
        }

        GL30.glFramebufferTexture2D(
            GL30.GL_FRAMEBUFFER,
            GL30.GL_COLOR_ATTACHMENT0 + index,
            GL11.GL_TEXTURE_2D,
            textureId,
            0
        );
    }

    private static void destroyScratchFrameBuffer() {
        if (scratchFrameBufferId != -1) {
            GL30.glDeleteFramebuffers(scratchFrameBufferId);
            scratchFrameBufferId = -1;
        }

        scratchAttachedTextureId = -1;
    }

    // ---------------------------------------------------------------------------------------------------------
    // Depth-test path (rollback — the original mechanism, unchanged)
    // ---------------------------------------------------------------------------------------------------------

    private static void runDepthTest() {
        if (!ensureDepthTestProgram()) {
            return;
        }

        // Save every piece of state we touch. A leaked GL flag here surfaces as someone else's renderer breaking.
        var previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        var previousVertexArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        var depthTestWasEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        var previousDepthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        var blendWasEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        var cullWasEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);

        try {
            // Only attachment 1 is written; index 0 is NONE so nothing can touch the colour buffer.
            GL30.glDrawBuffers(new int[] { GL11.GL_NONE, GL_COLOR_ATTACHMENT1 });

            RenderSystem.depthMask(false);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(GL11.GL_GREATER);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_CULL_FACE);

            GL20.glUseProgram(depthTestProgramId);
            GL30.glBindVertexArray(vertexArrayId);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        } finally {
            GL30.glBindVertexArray(previousVertexArray);
            GL20.glUseProgram(previousProgram);

            GL11.glDepthFunc(previousDepthFunc);

            if (!depthTestWasEnabled) {
                GL11.glDisable(GL11.GL_DEPTH_TEST);
            }

            if (blendWasEnabled) {
                GL11.glEnable(GL11.GL_BLEND);
            }

            if (cullWasEnabled) {
                GL11.glEnable(GL11.GL_CULL_FACE);
            }

            RenderSystem.depthMask(true);

            // Puts all seven attachments back so the rest of the frame keeps writing the full MRT set.
            BLibMainTargetMRT.restoreDrawBuffers();
        }
    }

    private static boolean ensureDepthTestProgram() {
        if (depthTestProgramId != -1) {
            return true;
        }

        var programId = link(DEPTH_TEST_VERTEX_SOURCE, DEPTH_TEST_FRAGMENT_SOURCE, null);

        if (programId == -1) {
            return false;
        }

        depthTestProgramId = programId;

        return ensureVertexArray();
    }

    // ---------------------------------------------------------------------------------------------------------
    // Shared plumbing
    // ---------------------------------------------------------------------------------------------------------

    private static boolean ensureVertexArray() {
        if (vertexArrayId == -1) {
            vertexArrayId = GL30.glGenVertexArrays();
        }

        return true;
    }

    /**
     * Compiles, binds fragment outputs to locations and links. {@code outputNames} binds each name to its index; pass
     * null for the depth-test program, whose single output goes to location 1 to match the main target's layout.
     */
    private static int link(String vertexSource, String fragmentSource, String[] outputNames) {
        var vertexShaderId = compile(GL20.GL_VERTEX_SHADER, vertexSource);
        var fragmentShaderId = compile(GL20.GL_FRAGMENT_SHADER, fragmentSource);

        if (vertexShaderId == -1 || fragmentShaderId == -1) {
            if (vertexShaderId != -1) {
                GL20.glDeleteShader(vertexShaderId);
            }

            if (fragmentShaderId != -1) {
                GL20.glDeleteShader(fragmentShaderId);
            }

            giveUp("shader compilation failed");

            return -1;
        }

        var programId = GL20.glCreateProgram();

        GL20.glAttachShader(programId, vertexShaderId);
        GL20.glAttachShader(programId, fragmentShaderId);

        if (outputNames == null) {
            GL30.glBindFragDataLocation(programId, 1, "blibEntityMask");
        } else {
            for (var i = 0; i < outputNames.length; i++) {
                GL30.glBindFragDataLocation(programId, i, outputNames[i]);
            }
        }

        GL20.glLinkProgram(programId);

        var linked = GL20.glGetProgrami(programId, GL20.GL_LINK_STATUS) != 0;
        var log = GL20.glGetProgramInfoLog(programId);

        GL20.glDetachShader(programId, vertexShaderId);
        GL20.glDetachShader(programId, fragmentShaderId);
        GL20.glDeleteShader(vertexShaderId);
        GL20.glDeleteShader(fragmentShaderId);

        if (!linked) {
            GL20.glDeleteProgram(programId);

            giveUp("program link failed: " + log);

            return -1;
        }

        return programId;
    }

    private static int compile(int type, String source) {
        var shaderId = GL20.glCreateShader(type);

        GL20.glShaderSource(shaderId, source);
        GL20.glCompileShader(shaderId);

        if (GL20.glGetShaderi(shaderId, GL20.GL_COMPILE_STATUS) == 0) {
            BLib.LOGGER.error("[BLib] Terrain mask fixup shader failed to compile: {}", GL20.glGetShaderInfoLog(shaderId));
            GL20.glDeleteShader(shaderId);

            return -1;
        }

        return shaderId;
    }

    /** Disables the pass permanently for this session rather than retrying — and failing — every frame. */
    private static boolean giveUp(String reason) {
        failed = true;

        BLib.LOGGER.error("[BLib] Terrain mask fixup disabled for this session: {}", reason);

        return false;
    }
}
