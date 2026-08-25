package com.blib.fabric.internal.client.service.impl;

import net.fabricmc.loader.api.FabricLoader;

import com.blib.internal.client.service.BLibClientIrisCompatService;

public final class BLibFabricClientIrisCompatServiceImpl implements BLibClientIrisCompatService {

    /** FabricLoader's mod list is complete before any client class loads, so the answer is always authoritative. */
    @Override
    public boolean isDetectionReady() {
        return FabricLoader.getInstance() != null;
    }

    @Override
    public boolean isShaderModActive() {
        var loader = FabricLoader.getInstance();
        return loader.isModLoaded("iris") || loader.isModLoaded("oculus");
    }
}
