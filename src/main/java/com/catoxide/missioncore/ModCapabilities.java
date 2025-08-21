package com.catoxide.missioncore;

import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MissionCore.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModCapabilities {
    public static final Capability<PlayerMissionData> PLAYER_MISSIONS =
            CapabilityManager.get(new CapabilityToken<>() {});

    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.register(PlayerMissionData.class);
    }
}