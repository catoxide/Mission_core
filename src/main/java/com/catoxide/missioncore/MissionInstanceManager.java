package com.catoxide.missioncore;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class MissionInstanceManager {
    private static final Map<String, Mission> missionInstances = new HashMap<>();

    // 创建新任务实例
    public static String createMissionInstance(String definitionId, boolean isShared) {
        MissionDefinition def = MissionRegistry.getMission(definitionId);
        if (def == null) {
            MissionCore.LOGGER.error("未知的任务定义ID: {}", definitionId);
            return null;
        }

        String instanceId = UUID.randomUUID().toString(); // 生成唯一ID
        Mission mission = new Mission(instanceId, definitionId, def.getTitle(), isShared);
        missionInstances.put(instanceId, mission);

        return instanceId;
    }

    // 获取任务实例
    public static Mission getMission(String instanceId) {
        return missionInstances.get(instanceId);
    }

    // 移除任务实例
    public static void removeMission(String instanceId) {
        missionInstances.remove(instanceId);
    }

    // 获取所有实例ID
    public static Set<String> getAllInstanceIds() {
        return missionInstances.keySet();
    }

    // 序列化所有任务实例
    public static CompoundTag serializeAll() {
        CompoundTag nbt = new CompoundTag();
        ListTag list = new ListTag();

        for (Mission mission : missionInstances.values()) {
            CompoundTag missionTag = mission.serializeNBT();
            list.add(missionTag);
        }

        nbt.put("missionInstances", list);
        return nbt;
    }

    // 反序列化所有任务实例
    public static void deserializeAll(CompoundTag nbt) {
        missionInstances.clear();
        if (nbt.contains("missionInstances", Tag.TAG_LIST)) {
            ListTag list = nbt.getList("missionInstances", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag missionTag = list.getCompound(i);
                Mission mission = Mission.deserializeNBT(missionTag);
                missionInstances.put(mission.getInstanceId(), mission);
            }
        }
    }
}