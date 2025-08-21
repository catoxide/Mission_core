package com.catoxide.missioncore;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.network.PacketDistributor;

import java.util.*;

public class WorldSharedMissionData extends SavedData {
    private static final String DATA_NAME = "missioncore_shared_missions";

    // 存储共享任务进度
    final Map<String, SharedMission> sharedMissions = new HashMap<>();

    // 玩家激活状态
    final Map<UUID, Set<String>> playerActivations = new HashMap<>();

    public static WorldSharedMissionData get(ServerLevel world) {
        return world.getDataStorage().computeIfAbsent(
                WorldSharedMissionData::load,
                WorldSharedMissionData::new,
                DATA_NAME
        );
    }

    // 内部类：共享任务
    public static class SharedMission {
        public final String instanceId;
        public int progress;
        public final int target;
        public boolean completed;

        public SharedMission(String instanceId, int target) {
            this.instanceId = instanceId;
            this.target = target;
        }

        public void updateProgress(int amount) {
            if (!completed) {
                progress = Math.min(progress + amount, target);
                completed = (progress >= target);
                MissionCore.LOGGER.info("任务 {} 进度更新: {}/{} (+{})", instanceId, progress, target, amount);
            }
        }
    }

    // 更新任务进度 - 确保只增加指定的数量
    public void updateMissionProgress(ServerLevel level, String instanceId, int amount) {
        SharedMission mission = sharedMissions.get(instanceId);
        if (mission != null && !mission.completed) {
            mission.updateProgress(amount);

            if (mission.completed) {
                distributeRewards(level, instanceId);
            }
            setDirty();

            // 发送同步数据包给所有玩家
            syncToAllPlayers(level);
        }
    }

    // 激活任务（全局）
    public void activateMission(String instanceId, int target) {
        sharedMissions.putIfAbsent(instanceId, new SharedMission(instanceId, target));
        setDirty();
    }


    // 同步任务进度给所有玩家
    public void syncToAllPlayers(ServerLevel level) {
        Map<String, SharedMission> progressMap = new HashMap<>();
        sharedMissions.forEach((id, mission) -> progressMap.put(id, mission));

        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            NetworkHandler.INSTANCE.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new NetworkHandler.SyncSharedMissionsPacket(progressMap)
            );
        }
    }

    // 序列化/反序列化
    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag missionsList = new ListTag();
        sharedMissions.forEach((instanceId, mission) -> {
            CompoundTag missionTag = new CompoundTag();
            missionTag.putString("instanceId", instanceId);
            missionTag.putInt("progress", mission.progress);
            missionTag.putInt("target", mission.target);
            missionTag.putBoolean("completed", mission.completed);
            missionsList.add(missionTag);
        });
        tag.put("sharedMissions", missionsList);
        // 保存激活状态
        CompoundTag activationsTag = new CompoundTag();
        playerActivations.forEach((playerId, missions) -> {
            ListTag list = new ListTag();
            missions.forEach(missionId -> list.add(StringTag.valueOf(missionId)));
            activationsTag.put(playerId.toString(), list);
        });
        tag.put("activations", activationsTag);

        return tag;
    }

    public static WorldSharedMissionData load(CompoundTag tag) {
        WorldSharedMissionData data = new WorldSharedMissionData();

        // 加载共享任务
        ListTag missionsList = tag.getList("sharedMissions", 10);
        for (int i = 0; i < missionsList.size(); i++) {
            CompoundTag missionTag = missionsList.getCompound(i);
            String instanceId = missionTag.getString("instanceId");
            int progress = missionTag.getInt("progress");
            int target = missionTag.getInt("target");
            boolean completed = missionTag.getBoolean("completed");

            SharedMission mission = new SharedMission(instanceId, target);
            mission.progress = progress;
            mission.completed = completed;
            data.sharedMissions.put(instanceId, mission);
        }

        // 加载激活状态
        CompoundTag activationsTag = tag.getCompound("activations");
        activationsTag.getAllKeys().forEach(playerIdStr -> {
            UUID playerId = UUID.fromString(playerIdStr);
            ListTag list = activationsTag.getList(playerIdStr, 8);
            Set<String> activatedMissions = new HashSet<>();
            for (int i = 0; i < list.size(); i++) {
                activatedMissions.add(list.getString(i));
            }
            data.playerActivations.put(playerId, activatedMissions);
        });

        return data;
    }

    private void distributeRewards(ServerLevel level, String missionId) {
        for (Map.Entry<UUID, Set<String>> entry : playerActivations.entrySet()) {
            if (entry.getValue().contains(missionId)) {
                // 通过服务器玩家列表获取玩家
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
                if (player != null) {
                    // 发放奖励
                    player.sendSystemMessage(Component.literal("任务完成奖励!"));
                    // 这里可以添加实际奖励逻辑，如给予物品、经验等
                }
            }
        }
    }

    // 添加获取任务方法
    public SharedMission getSharedMission(String missionId) {
        return sharedMissions.get(missionId);
    }
}