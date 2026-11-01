package com.blib.api.common.socket.v1;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * A named attachment point that follows an animated bone: a bone plus an offset and rotation in that bone's space, or a
 * Blockbench locator. Use it to find where a muzzle, hand or mount point is in the world
 * ({@link AzBoneSockets#resolve}) or to render something there ({@code AzAttachmentLayer}).
 * <p>
 * Offsets are in Blockbench pixels from the bone's pivot and rotations in Blockbench degrees, the same conventions as
 * hitbox offsets and keyframes.
 * </p>
 * <p>
 * On the server (e.g. to spawn a projectile at a muzzle) a socket resolves through {@link GeoServerPoses}: BLib poses
 * the mob's own geo and animation files there, the same evaluator as its server-side limb hitboxes. Nothing is synced.
 * </p>
 */
public final class AzBoneSocket {

    private final String name;

    @Nullable
    private final String bone;

    @Nullable
    private final String locator;

    private final float offsetX;

    private final float offsetY;

    private final float offsetZ;

    private final float rotationX;

    private final float rotationY;

    private final float rotationZ;

    /**
     * Creates a bone or locator socket.
     *
     * @param name      the socket's name
     * @param bone      the bone it follows, or {@code null} for a locator socket, which finds its bone at runtime
     * @param locator   the Blockbench locator it follows, or {@code null}
     * @param offsetX   the X offset in Blockbench pixels
     * @param offsetY   the Y offset in Blockbench pixels
     * @param offsetZ   the Z offset in Blockbench pixels
     * @param rotationX the X rotation in Blockbench degrees
     * @param rotationY the Y rotation in Blockbench degrees
     * @param rotationZ the Z rotation in Blockbench degrees
     * @throws NullPointerException     if {@code name} is null
     * @throws IllegalArgumentException if both {@code bone} and {@code locator} are set, or neither is set
     */
    public AzBoneSocket(
        String name,
        @Nullable String bone,
        @Nullable String locator,
        float offsetX,
        float offsetY,
        float offsetZ,
        float rotationX,
        float rotationY,
        float rotationZ
    ) {
        this.name = Objects.requireNonNull(name, "name");
        if ((bone == null) == (locator == null)) {
            throw new IllegalArgumentException("A socket follows either a bone or a locator");
        }
        this.bone = bone;
        this.locator = locator;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
        this.rotationX = rotationX;
        this.rotationY = rotationY;
        this.rotationZ = rotationZ;
    }

    public String name() {
        return name;
    }

    @Nullable
    public String bone() {
        return bone;
    }

    @Nullable
    public String locator() {
        return locator;
    }

    public float offsetX() {
        return offsetX;
    }

    public float offsetY() {
        return offsetY;
    }

    public float offsetZ() {
        return offsetZ;
    }

    public float rotationX() {
        return rotationX;
    }

    public float rotationY() {
        return rotationY;
    }

    public float rotationZ() {
        return rotationZ;
    }

    /** A socket on a bone; configure it with the returned builder. */
    public static Builder bone(String name, String bone) {
        return new Builder(name, Objects.requireNonNull(bone, "bone"), null);
    }

    /**
     * A socket at a Blockbench locator. The locator's position and rotation are used, plus any extra offset and
     * rotation you set; the socket is named after the locator.
     */
    public static Builder locator(String locator) {
        return new Builder(locator, null, Objects.requireNonNull(locator, "locator"));
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof AzBoneSocket))
            return false;
        AzBoneSocket other = (AzBoneSocket) obj;
        return Objects.equals(name, other.name)
            && Objects.equals(bone, other.bone)
            && Objects.equals(locator, other.locator)
            && Float.compare(offsetX, other.offsetX) == 0
            && Float.compare(offsetY, other.offsetY) == 0
            && Float.compare(offsetZ, other.offsetZ) == 0
            && Float.compare(rotationX, other.rotationX) == 0
            && Float.compare(rotationY, other.rotationY) == 0
            && Float.compare(rotationZ, other.rotationZ) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            name,
            bone,
            locator,
            offsetX,
            offsetY,
            offsetZ,
            rotationX,
            rotationY,
            rotationZ
        );
    }

    @Override
    public String toString() {
        return "AzBoneSocket[name=" + name + ", bone=" + bone + ", locator=" + locator
            + ", offsetX=" + offsetX + ", offsetY=" + offsetY + ", offsetZ=" + offsetZ
            + ", rotationX=" + rotationX + ", rotationY=" + rotationY + ", rotationZ=" + rotationZ + "]";
    }

    public static final class Builder {

        private final String name;

        private final String bone;

        private final String locator;

        private float offsetX, offsetY, offsetZ;

        private float rotationX, rotationY, rotationZ;

        private Builder(String name, String bone, String locator) {
            this.name = Objects.requireNonNull(name, "name");
            this.bone = bone;
            this.locator = locator;
        }

        public Builder offset(float x, float y, float z) {
            this.offsetX = x;
            this.offsetY = y;
            this.offsetZ = z;
            return this;
        }

        public Builder rotation(float x, float y, float z) {
            this.rotationX = x;
            this.rotationY = y;
            this.rotationZ = z;
            return this;
        }

        public AzBoneSocket build() {
            return new AzBoneSocket(
                name,
                bone,
                locator,
                offsetX,
                offsetY,
                offsetZ,
                rotationX,
                rotationY,
                rotationZ
            );
        }
    }
}
