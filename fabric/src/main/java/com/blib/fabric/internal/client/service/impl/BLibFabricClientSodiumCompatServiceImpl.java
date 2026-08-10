package com.blib.fabric.internal.client.service.impl;

import net.fabricmc.loader.api.FabricLoader;

import com.blib.internal.client.service.BLibClientSodiumCompatService;

public final class BLibFabricClientSodiumCompatServiceImpl implements BLibClientSodiumCompatService {

    @Override
    public boolean isSodiumActive() {
        var loader = FabricLoader.getInstance();
        return loader.isModLoaded("sodium") || loader.isModLoaded("embeddium") || loader.isModLoaded("rubidium");
    }
}
