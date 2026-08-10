package com.blib.internal.client.posteffect;

import com.mojang.blaze3d.systems.RenderSystem;
import org.jetbrains.annotations.ApiStatus;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import com.blib.mod.BLib;

/**
 * Reclassifies Sodium-rendered terrain as terrain in the {@code entityMask} attachment.
 * <p>
 * Sodium draws terrain with its own shaders, which write only a colour. The auxiliary attachments therefore keep their
 * cleared {@code 0.0} for every terrain fragment — and {@code 0.0} is the value consumers read as SKY, so without this
 * the whole world is classified as sky rather than merely losing detail. This pass paints the terrain category
 * ({@code 0.5}) over exactly the pixels where geometry was drawn.
 * <p>
 * <b>Why the depth TEST and not a depth sampler.</b> The obvious implementation samples the depth texture and branches
 * on it. That texture is attached to the framebuffer we are drawing into, and sampling an attachment of the bound
 * framebuffer is a feedback loop with undefined results. Instead the quad is drawn at {@code z = 1.0} with
 * {@code GL_GREATER}: it passes wherever the stored depth is nearer than the far plane, i.e. wherever something was
 * drawn, and fails on untouched sky. No depth read, no feedback loop, and depth writes stay off so the buffer the rest
 * of the frame depends on is untouched.
 * <p>
 * <b>Why the ordering matters.</b> This runs after the terrain layers and before entities and particles. At that moment
 * the only things on screen are terrain (mask {@code 0.0} under Sodium) and sky (excluded by the depth test), so the
 * write can be unconditional without clobbering an entity's {@code 1.0} or a particle's {@code 0.25}. Entities and
 * particles drawn afterwards write their own categories over the top as usual.
 * <p>
 * The GLSL lives here as a string rather than in {@code assets/}: this program is compiled directly and never
 * registered with vanilla's shader system, so it stays invisible to mods that walk and re-parse the vanilla shader
 * registry.
 */
@ApiStatus.Internal
public final class BLibTerrainMaskFixup {

    private static final int GL_COLOR_ATTACHMENT1 = 36065;

    /** Must match {@code BLibEntityShaderPatcher.Category.TERRAIN}. */
    private static final float TERRAIN_MASK_VALUE = 0.5F;

    private static final String VERTEX_SOURCE = """
        #version 150

        void main() {
            // Fullscreen triangle from gl_VertexID alone — no vertex buffer, no attributes, no VAO contents.
            vec2 corner = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);

            // z = 1.0 is the far plane: with GL_GREATER this fragment survives only where something nearer was drawn.
            gl_Position = vec4(corner * 2.0 - 1.0, 1.0, 1.0);
        }
        """;

    private static final String FRAGMENT_SOURCE = """
        #version 150

        out vec2 blibEntityMask;

        void main() {
            // R = category (terrain). G = the background-entity lane, which terrain never participates in.
            blibEntityMask = vec2(%s, 0.0);
        }
        """.formatted(TERRAIN_MASK_VALUE);

    private static int programId = -1;

    private static int vertexArrayId = -1;

    private static boolean failed;

    private BLibTerrainMaskFixup() {
        throw new UnsupportedOperationException();
    }

    /**
     * Paints the terrain category over every pixel that has geometry. Safe to call unconditionally — it returns
     * immediately unless Sodium is present and the MRT attachments exist.
     */
    public static void run() {
        if (failed || !BLibSodiumCompat.isTerrainMaskFixupEnabled() || !BLibMainTargetMRT.isAttached()) {
            return;
        }

        if (!ensureProgram()) {
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

            GL20.glUseProgram(programId);
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

    public static void destroy() {
        if (programId != -1) {
            GL20.glDeleteProgram(programId);
            programId = -1;
        }

        if (vertexArrayId != -1) {
            GL30.glDeleteVertexArrays(vertexArrayId);
            vertexArrayId = -1;
        }
    }

    private static boolean ensureProgram() {
        if (programId != -1) {
            return true;
        }

        var vertexShaderId = compile(GL20.GL_VERTEX_SHADER, VERTEX_SOURCE);
        var fragmentShaderId = compile(GL20.GL_FRAGMENT_SHADER, FRAGMENT_SOURCE);

        if (vertexShaderId == -1 || fragmentShaderId == -1) {
            return giveUp("shader compilation failed");
        }

        programId = GL20.glCreateProgram();

        GL20.glAttachShader(programId, vertexShaderId);
        GL20.glAttachShader(programId, fragmentShaderId);
        GL30.glBindFragDataLocation(programId, 1, "blibEntityMask");
        GL20.glLinkProgram(programId);

        var linked = GL20.glGetProgrami(programId, GL20.GL_LINK_STATUS) != 0;
        var log = GL20.glGetProgramInfoLog(programId);

        GL20.glDetachShader(programId, vertexShaderId);
        GL20.glDetachShader(programId, fragmentShaderId);
        GL20.glDeleteShader(vertexShaderId);
        GL20.glDeleteShader(fragmentShaderId);

        if (!linked) {
            GL20.glDeleteProgram(programId);
            programId = -1;

            return giveUp("program link failed: " + log);
        }

        vertexArrayId = GL30.glGenVertexArrays();

        return true;
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
