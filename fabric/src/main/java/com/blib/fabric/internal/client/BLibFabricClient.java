package com.blib.fabric.internal.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.jetbrains.annotations.ApiStatus;

import com.blib.fabric.internal.client.shader.BLibFabricShaders;
import com.blib.internal.client.BLibClient;
import com.blib.internal.client.territory.BLibClaimHud;

@ApiStatus.Internal
public class BLibFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        BLibClient.initialize();
        ClientTickEvents.END_CLIENT_TICK.register(client -> BLibClaimHud.tick());
        BLibFabricShaders.register();
    }
}
