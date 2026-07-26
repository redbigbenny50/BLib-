package com.blib.api.common.dismemberment.v1;

import net.minecraft.world.phys.Vec3;

/**
 * A posed, oriented model cube expressed in world space.  Collision adapters create these from their real model
 * hierarchy; the generic resolver only performs the ray test and never guesses anatomy from an entity bounding box.
 */
public record LimbCollisionBox(Vec3 center, Vec3 axisX, Vec3 axisY, Vec3 axisZ, Vec3 halfExtents) {

    public Vec3 toLocal(Vec3 point) {
        var offset = point.subtract(center);
        return new Vec3(offset.dot(axisX), offset.dot(axisY), offset.dot(axisZ));
    }

    public Vec3 toWorld(Vec3 point) {
        return center.add(axisX.scale(point.x)).add(axisY.scale(point.y)).add(axisZ.scale(point.z));
    }
}
