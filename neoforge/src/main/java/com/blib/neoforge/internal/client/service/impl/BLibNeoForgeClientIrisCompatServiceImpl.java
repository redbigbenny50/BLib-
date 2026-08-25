package com.blib.neoforge.internal.client.service.impl;

import net.neoforged.fml.ModList;

import com.blib.internal.client.service.BLibClientIrisCompatService;

public final class BLibNeoForgeClientIrisCompatServiceImpl implements BLibClientIrisCompatService {

    /** The mod list is null until FML has built it, which is later than the main render target is first created. */
    @Override
    public boolean isDetectionReady() {
        return ModList.get() != null;
    }

    @Override
    public boolean isShaderModActive() {
        var modList = ModList.get();

        if (modList == null) {
            return false;
        }

        return modList.isLoaded("oculus") || modList.isLoaded("iris");
    }
}
