package com.blib.neoforge.internal.client.service.impl;

import net.neoforged.fml.ModList;

import com.blib.internal.client.service.BLibClientSodiumCompatService;

public final class BLibNeoForgeClientSodiumCompatServiceImpl implements BLibClientSodiumCompatService {

    @Override
    public boolean isSodiumActive() {
        var modList = ModList.get();

        if (modList == null) {
            return false;
        }

        return modList.isLoaded("sodium") || modList.isLoaded("embeddium") || modList.isLoaded("rubidium");
    }
}
