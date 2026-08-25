package com.blib.neoforge.internal.service.impl;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

import com.blib.api.common.mod.v1.model.DistributionEnvironmentType;
import com.blib.api.common.mod.v1.model.ReleaseEnvironmentType;
import com.blib.api.common.mod.v1.model.Version;
import com.blib.api.common.mod.v1.model.loader.ModLoaderType;
import com.blib.internal.service.BLibModLoaderService;

@ApiStatus.Internal
public class BLibNeoForgeModLoaderServiceImpl implements BLibModLoaderService {

    @Override
    public Path getGameDirectory() {
        return FMLLoader.getGamePath();
    }

    @Override
    public ModLoaderType getModLoaderType() {
        return ModLoaderType.NEOFORGE;
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public DistributionEnvironmentType getDistributionEnvironmentType() {
        return switch (FMLLoader.getDist()) {
            case CLIENT -> DistributionEnvironmentType.CLIENT;
            case DEDICATED_SERVER -> DistributionEnvironmentType.DEDICATED_SERVER;
        };
    }

    @Override
    public ReleaseEnvironmentType getReleaseEnvironmentType() {
        return !FMLLoader.isProduction()
            ? ReleaseEnvironmentType.DEVELOPMENT
            : ReleaseEnvironmentType.PRODUCTION;
    }

    @Override
    public @Nullable Version getModVersion(String modId) {
        // ModList.get() is null until NeoForge has populated the mod list, which happens after Minecraft.<init>
        // begins. Anything queried during early boot (e.g. the MainTarget MRT mixin touching BLib.LOGGER, which
        // triggers BLib.<clinit>) needs to fail soft here rather than NPE. Callers handle a null Version by
        // treating it as "unknown" — see BLibMod#version which retries on subsequent calls.
        var modList = ModList.get();

        if (modList == null) {
            return null;
        }

        return modList
            .getModContainerById(modId)
            .map(mod -> mod.getModInfo().getVersion().toString())
            .map(BLibNeoForgeModLoaderServiceImpl::parseOrNull)
            .orElse(null);
    }

    /**
     * ⚠⚠ NEVER LET A FOREIGN VERSION STRING THROW. This method's contract is "null means unknown", and every caller is
     * written against that; a mod versioning itself in a way {@link Version} cannot read must degrade to unknown, not
     * take the game down during mod construction. BLib 0.3.5-fork crashed on startup for everyone running Iris and
     * Sodium together for exactly this reason.
     */
    private static @Nullable Version parseOrNull(String raw) {
        try {
            return Version.parse(raw);
        } catch (RuntimeException exception) {
            return null;
        }
    }

}
