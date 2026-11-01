package com.blib.api.common.dismemberment.v1.hitbox;

/**
 * The one rule that links a short name ({@code head}, {@code left_arm}) to a limb id path ({@code drone_head},
 * {@code drone_left_arm}). Used for {@code hitbox_<name>} Blockbench groups and for {@code blib_hitboxes} data entries,
 * so both behave identically.
 */
public final class LimbNameMatcher {

    /** True when {@code name} is the whole limb path or its last {@code _}-separated part(s). */
    public static boolean matches(String limbPath, String name) {
        if (name.isEmpty()) {
            return false;
        }
        return limbPath.equals(name) || limbPath.endsWith("_" + name);
    }

    private LimbNameMatcher() {}
}
