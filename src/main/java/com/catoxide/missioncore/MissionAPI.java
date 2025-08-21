package com.catoxide.missioncore;

import com.catoxide.missioncore.trigger.MissionTrigger;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class MissionAPI {
    public static void registerTrigger(String id, MissionTrigger trigger) {
        MissionRegistry.registerTriggerPrototype(id, trigger); // 修改这里
    }

    public static void registerMission(
            String id, String title, int target,
            String triggerType, JsonObject triggerConfig
    ) {
        // 添加 isShared 参数
        MissionDefinition def = new MissionDefinition(id, title, target, triggerConfig, true);
        MissionRegistry.registerMission(def);
    }

    public static MissionDefinition getMission(String id) {
        return MissionRegistry.getMission(id);
    }

    public static boolean assignMissionToPlayer(ServerPlayer player, String definitionId) {
        MissionDefinition mission = getMission(definitionId);
        if (mission == null) return false;

        player.getCapability(ModCapabilities.PLAYER_MISSIONS).ifPresent(data -> {
            // 创建任务实例
            String instanceId = MissionInstanceManager.createMissionInstance(definitionId, false);
            if (instanceId != null) {
                data.activateMissionInstance(instanceId);
            }
        });

        return true;
    }

    public static boolean updatePlayerMissionProgress(ServerPlayer player, String instanceId, int progress) {
        // 对于个人任务，需要实现进度更新逻辑
        // 这里暂时返回false，表示不支持
        return false;
    }

    public static List<Mission> getPlayerMissions(ServerPlayer player) {
        List<Mission> missions = new ArrayList<>();
        AtomicBoolean hasData = new AtomicBoolean(false);

        player.getCapability(ModCapabilities.PLAYER_MISSIONS).ifPresent(data -> {
            hasData.set(true);
            for (String instanceId : data.getActivatedMissionInstances()) {
                Mission mission = MissionInstanceManager.getMission(instanceId);
                if (mission != null) {
                    missions.add(mission);
                }
            }
        });

        // 如果没有数据，返回空列表
        if (!hasData.get()) {
            return new ArrayList<>();
        }

        return missions;
    }

    public static boolean isMissionCompleted(ServerPlayer player, String instanceId) {
        // 检查任务是否完成
        Mission mission = MissionInstanceManager.getMission(instanceId);
        if (mission == null) return false;

        if (mission.isShared()) {
            // 共享任务：从共享数据获取完成状态
            WorldSharedMissionData sharedData = WorldSharedMissionData.get((ServerLevel) player.level());
            WorldSharedMissionData.SharedMission sharedMission = sharedData.getSharedMission(instanceId);
            return sharedMission != null && sharedMission.completed;
        } else {
            // 个人任务：需要实现完成状态检查
            return false;
        }
    }

    public static void activateSharedMission(ServerLevel level, String definitionId, int target) {
        WorldSharedMissionData sharedData = WorldSharedMissionData.get(level);

        // 创建任务实例
        String instanceId = MissionInstanceManager.createMissionInstance(definitionId, true);
        if (instanceId != null) {
            sharedData.activateMission(instanceId, target);
        }
    }

    public static int getSharedMissionProgress(ServerLevel level, String instanceId) {
        WorldSharedMissionData sharedData = WorldSharedMissionData.get(level);
        WorldSharedMissionData.SharedMission mission = sharedData.getSharedMission(instanceId);
        return mission != null ? mission.progress : 0;
    }

    public static int getMissionTarget(ServerLevel level, String instanceId) {
        WorldSharedMissionData sharedData = WorldSharedMissionData.get(level);
        WorldSharedMissionData.SharedMission mission = sharedData.getSharedMission(instanceId);
        return mission != null ? mission.target : 0;
    }
}