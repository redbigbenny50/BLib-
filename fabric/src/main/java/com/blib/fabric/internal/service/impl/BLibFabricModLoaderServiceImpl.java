package com.blib.fabric.internal.service.impl;

import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

import com.blib.api.common.mod.v1.model.DistributionEnvironmentType;
import com.blib.api.common.mod.v1.model.ReleaseEnvironmentType;
import com.blib.api.common.mod.v1.model.Version;
import com.blib.api.common.mod.v1.model.loader.ModLoaderType;
import com.blib.internal.service.BLibModLoaderService;

@ApiStatus.Internal
public class BLibFabricModLoaderServiceImpl implements BLibModLoaderService {

    @Override
    public Path getGameDirectory() {
        return FabricLoader.getInstance().getGameDir();
    }

    @Override
    public ModLoaderType getModLoaderType() {
        return ModLoaderType.FABRIC;
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public @Nullable Version getModVersion(String modId) {
        var container = FabricLoader.getInstance().getModContainer(modId);

        return container
            .map(modContainer -> modContainer.getMetadata().getVersion().getFriendlyString())
            .map(BLibFabricModLoaderServiceImpl::parseOrNull)
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

    @Override
    public DistributionEnvironmentType getDistributionEnvironmentType() {
        return switch (FabricLoader.getInstance().getEnvironmentType()) {
            case CLIENT -> DistributionEnvironmentType.CLIENT;
            case SERVER -> DistributionEnvironmentType.DEDICATED_SERVER;
        };
    }

    @Override
    public ReleaseEnvironmentType getReleaseEnvironmentType() {
        return FabricLoader.getInstance().isDevelopmentEnvironment()
            ? ReleaseEnvironmentType.DEVELOPMENT
            : ReleaseEnvironmentType.PRODUCTION;
    }
}
