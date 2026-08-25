package com.blib.api.common.mod.v1.model;

import org.jetbrains.annotations.Nullable;

public record Version(
    int major,
    int minor,
    int patch,
    @Nullable String suffix
) implements Comparable<Version> {

    public Version(int major, int minor, int patch) {
        this(major, minor, patch, null);
    }

    @Override
    public int compareTo(Version other) {
        if (major != other.major) {
            return Integer.compare(major, other.major);
        }

        if (minor != other.minor) {
            return Integer.compare(minor, other.minor);
        }

        if (patch != other.patch) {
            return Integer.compare(patch, other.patch);
        }

        // Release > pre-release
        if (suffix == null && other.suffix != null) {
            return 1;
        }

        if (suffix != null && other.suffix == null) {
            return -1;
        }

        if (suffix == null) {
            return 0;
        }

        // lexicographic comparison
        return suffix.compareTo(other.suffix);
    }

    /**
     * Parses {@code major.minor.patch}, with an optional {@code -suffix} and an optional {@code +build} tag.
     *
     * @throws IllegalArgumentException if the numeric part is not three dot-separated integers. ⚠⚠ CALLERS READING A
     *                                  VERSION OFF ANOTHER MOD MUST CATCH THIS. Mod authors are under no obligation to
     *                                  version their work the way we do, and one that does not must never be able to
     *                                  bring the game down — see the loader-service implementations, which fail soft to
     *                                  null.
     */
    public static Version parse(String input) {
        String versionPart;
        String suffix = null;

        // ⚠⚠ BUILD METADATA IS STRIPPED FIRST, AND LEAVING IT IN CRASHED THE GAME FOR EVERY IRIS + SODIUM USER ON
        // 0.3.5-fork. Both ship as "1.8.7+mc1.21.1"; the dots inside the build tag split into five parts instead of
        // three and this method threw during mod construction, taking BLib down with it. SemVer says everything from
        // the first '+' is metadata carrying no ordering meaning, so it is discarded rather than parsed.
        //
        // It is removed BEFORE the '-' split so a full "1.2.3-beta+build.5" yields the suffix "beta" and not
        // "beta+build.5", which would otherwise sort wrong against a plain "1.2.3-beta".
        var buildIndex = input.indexOf('+');

        if (buildIndex >= 0) {
            input = input.substring(0, buildIndex);
        }

        var suffixIndex = input.indexOf('-');

        if (suffixIndex >= 0) {
            versionPart = input.substring(0, suffixIndex);
            suffix = input.substring(suffixIndex + 1);
        } else {
            versionPart = input;
        }

        var parts = versionPart.split("\\.");

        if (parts.length != 3) {
            throw new IllegalArgumentException("Version must be in the format major.minor.patch");
        }

        var major = Integer.parseInt(parts[0]);
        var minor = Integer.parseInt(parts[1]);
        var patch = Integer.parseInt(parts[2]);

        return new Version(major, minor, patch, suffix);
    }
}
