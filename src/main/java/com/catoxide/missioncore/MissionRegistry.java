package com.catoxide.missioncore;

import com.catoxide.missioncore.trigger.BlockBreakTrigger;
import com.catoxide.missioncore.trigger.BlockStateChangeTrigger;
import com.catoxide.missioncore.trigger.EntityKillTrigger;
import com.catoxide.missioncore.trigger.PlayerInteractTrigger;
import com.catoxide.missioncore.trigger.MissionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.*;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class MissionRegistry {
    private static final Map<String, MissionDefinition> missions = new HashMap<>();
    private static final Map<String, MissionTrigger> triggerPrototypes = new HashMap<>();

    // 静态初始化块注册内置触发器原型
    static {
        registerTriggerPrototype("block_break", new BlockBreakTrigger());
        registerTriggerPrototype("entity_kill", new EntityKillTrigger());
        registerTriggerPrototype("block_state_change", new BlockStateChangeTrigger());
        registerTriggerPrototype("player_interact", new PlayerInteractTrigger());
        MissionCore.LOGGER.info("已注册 {} 个内置触发器原型", triggerPrototypes.size());
    }

    // 注册触发器原型的方法
    public static void registerTriggerPrototype(String name, MissionTrigger trigger) {
        triggerPrototypes.put(name, trigger);
        MissionCore.LOGGER.info("注册触发器原型: {}", name);
    }

    // 获取触发器原型的方法
    public static MissionTrigger getTriggerPrototype(String type) {
        return triggerPrototypes.get(type);
    }

    // 注册任务定义
    public static void registerMission(MissionDefinition definition) {
        missions.put(definition.getId(), definition);
        MissionCore.LOGGER.info("注册任务: {}", definition.getId());
    }

    public static int getMissionCount() {
        return missions.size();
    }

    public static void clear() {
        missions.clear();
        MissionCore.LOGGER.info("清空任务注册表");
    }

    public static Collection<MissionDefinition> getAllMissions() {
        return Collections.unmodifiableCollection(missions.values());
    }

    public static MissionDefinition getMission(String id) {
        return missions.get(id);
    }
}