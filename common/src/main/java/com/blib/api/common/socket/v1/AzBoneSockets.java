package com.blib.api.common.socket.v1;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Vector3f;

/**
 * Turns {@link AzBoneSocket}s into world positions and orientations.
 * <p>
 * On the client a socket follows its bone's pose from the last rendered frame (bones start tracking their matrices the
 * first time a socket asks, so the very first call returns {@code false}). Off-screen entities are not posed, so their
 * sockets hold their last on-screen pose. On the server, and on the client before the bone has been posed, a socket
 * resolves through {@link GeoServerPoses}: the mob's own geo and animation files, posed on the server.
 * </p>
 */
public final class AzBoneSockets {

    private static final float DEG_TO_RAD = Mth.DEG_TO_RAD;

    private AzBoneSockets() {}

    /**
     * Resolves {@code socket} on {@code entity}.
     *
     * @param partialTick the frame's partial tick when called while rendering, otherwise 1
     * @return whether {@code dest} was filled; {@code false} if the socket can't be resolved yet
     */
    public static boolean resolve(Entity entity, AzBoneSocket socket, float partialTick, AzSocketTransform dest) {
        var client = clientResolver;

        if (entity.level().isClientSide && client != null && client.resolve(entity, socket, partialTick, dest)) {
            return true;
        }

        return resolveThroughServerPose(entity, socket, dest);
    }

    /** The socket's world position, or {@code null} if it can't be resolved yet. */
    public static @Nullable Vec3 position(Entity entity, AzBoneSocket socket, float partialTick) {
        AzSocketTransform transform = new AzSocketTransform();
        return resolve(entity, socket, partialTick, transform) ? transform.position() : null;
    }

    /** Resolves a socket from the bones the client renders. Set by BLib's client init; never set on a server. */
    @FunctionalInterface
    public interface ClientResolver {

        boolean resolve(Entity entity, AzBoneSocket socket, float partialTick, AzSocketTransform dest);
    }

    @Nullable
    private static volatile ClientResolver clientResolver;

    /** Called once by BLib's client init. */
    public static void setClientResolver(ClientResolver resolver) {
        clientResolver = resolver;
    }

    /**
     * Oct 31 - BLib's server path (Azure's needs its synced hitboxes, which BLib does not use): pose the mob's own geo
     * and animation files on the server ({@link GeoServerPoses}) and read the bone or locator from there.
     */
    private static boolean resolveThroughServerPose(Entity entity, AzBoneSocket socket, AzSocketTransform dest) {
        var entry = GeoServerPoses.find(entity);

        if (entry == null) {
            return false;
        }

        var pose = entry.pose(entity);
        var point = entry.profile()
            .bonePoint(
                entity,
                socket.bone(),
                socket.locator(),
                new Vec3(socket.offsetX(), socket.offsetY(), socket.offsetZ()),
                pose.animation(),
                pose.timeSeconds(),
                pose.pitchDegrees(),
                pose.rollDegrees(),
                pose.pivotY()
            );

        if (point == null) {
            return false;
        }

        Matrix3f basis = new Matrix3f(
            (float) point.right().x,
            (float) point.right().y,
            (float) point.right().z,
            (float) point.up().x,
            (float) point.up().y,
            (float) point.up().z,
            (float) -point.forward().x,
            (float) -point.forward().y,
            (float) -point.forward().z
        );
        basis.mul(socketRotation(socket.rotationX(), socket.rotationY(), socket.rotationZ(), new Matrix3f()));
        setFromBasis(dest, point.position().x, point.position().y, point.position().z, basis);
        return true;
    }

    /** A Blockbench rotation in degrees as a bone-space rotation matrix (AzureLib negates X and Y at load). */
    public static Matrix3f socketRotation(float x, float y, float z, Matrix3f dest) {
        return dest.rotationZYX(z * DEG_TO_RAD, -y * DEG_TO_RAD, -x * DEG_TO_RAD);
    }

    /** Fills {@code dest} from a position and a basis whose columns are the socket's axes, one model unit long. */
    public static void setFromBasis(AzSocketTransform dest, double x, double y, double z, Matrix3f basis) {
        Vector3f ax = basis.getColumn(0, new Vector3f());
        Vector3f ay = basis.getColumn(1, new Vector3f());
        Vector3f az = basis.getColumn(2, new Vector3f());
        dest.set(x, y, z, ax, ay, az);
    }
}
