package com.catoxide.missioncore.trigger;

import com.google.gson.JsonObject;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.Event;

public interface MissionTrigger {
    boolean shouldTrigger(Player player, Event event);
    void configure(JsonObject config);

    // 添加创建新实例的方法
    MissionTrigger createNewInstance();
}