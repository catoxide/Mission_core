package com.catoxide.missioncore;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.*;
import java.util.function.Supplier;

public class NetworkHandler {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            ResourceLocation.parse("missioncore:main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );


    public static void register() {
        int id = 0;

        INSTANCE.registerMessage(id++, UpdateMissionsPacket.class,
                UpdateMissionsPacket::encode,
                UpdateMissionsPacket::decode,
                UpdateMissionsPacket::handle);

        // 注册 SyncSharedMissionsPacket
        INSTANCE.registerMessage(id++, SyncSharedMissionsPacket.class,
                SyncSharedMissionsPacket::encode,
                SyncSharedMissionsPacket::decode,
                SyncSharedMissionsPacket::handle);

        // 注册 SyncPlayerMissionsPacket
        INSTANCE.registerMessage(id++, SyncPlayerMissionsPacket.class,
                SyncPlayerMissionsPacket::encode,
                SyncPlayerMissionsPacket::decode,
                SyncPlayerMissionsPacket::handle);
    }

    public static class UpdateMissionsPacket {
        private final List<Mission> missions;

        public UpdateMissionsPacket(List<Mission> missions) {
            this.missions = missions;
        }

        public void encode(FriendlyByteBuf buffer) {
            buffer.writeInt(missions.size());
            for (Mission mission : missions) {
                CompoundTag missionTag = mission.serializeNBT();
                buffer.writeNbt(missionTag);
            }
        }

        public static UpdateMissionsPacket decode(FriendlyByteBuf buffer) {
            int missionCount = buffer.readInt();
            List<Mission> missions = new ArrayList<>();
            for (int i = 0; i < missionCount; i++) {
                CompoundTag missionTag = buffer.readNbt();
                if (missionTag != null) {
                    missions.add(Mission.deserializeNBT(missionTag));
                }
            }
            return new UpdateMissionsPacket(missions);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.getCapability(ModCapabilities.PLAYER_MISSIONS).ifPresent(cap -> {
                        MissionCore.LOGGER.info("客户端收到{}个任务", this.missions.size());

                        if (mc.screen == null) {
                            mc.gui.setOverlayMessage(
                                    Component.literal("任务已更新"),
                                    false
                            );
                        }
                    });
                }
            });
            ctx.get().setPacketHandled(true);
        }

        public List<Mission> getMissions() {
            return missions;
        }
    }

    public static class SyncSharedMissionsPacket {
        private final Map<String, WorldSharedMissionData.SharedMission> missionProgress;

        public SyncSharedMissionsPacket(Map<String, WorldSharedMissionData.SharedMission> missionProgress) {
            this.missionProgress = missionProgress;
        }

        public void encode(FriendlyByteBuf buffer) {
            buffer.writeInt(missionProgress.size());
            missionProgress.forEach((id, mission) -> {
                buffer.writeUtf(id);
                buffer.writeInt(mission.progress);
                buffer.writeInt(mission.target);
                buffer.writeBoolean(mission.completed);
            });
        }

        public static SyncSharedMissionsPacket decode(FriendlyByteBuf buffer) {
            int size = buffer.readInt();
            Map<String, WorldSharedMissionData.SharedMission> progressMap = new HashMap<>();
            for (int i = 0; i < size; i++) {
                String id = buffer.readUtf();
                int progress = buffer.readInt();
                int target = buffer.readInt();
                boolean completed = buffer.readBoolean();

                WorldSharedMissionData.SharedMission mission = new WorldSharedMissionData.SharedMission(id, target);
                mission.progress = progress;
                mission.completed = completed;
                progressMap.put(id, mission);
            }
            return new SyncSharedMissionsPacket(progressMap);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                // 客户端处理：更新所有共享任务进度
                ClientMissionData.updateAllMissionProgress(this.missionProgress);
                MissionCore.LOGGER.debug("客户端收到共享任务进度更新: {}", this.missionProgress.size());
            });
            ctx.get().setPacketHandled(true);
        }
    }
    public static class SyncPlayerMissionsPacket {
        private final Set<String> activatedMissions;

        public SyncPlayerMissionsPacket(Set<String> activatedMissions) {
            this.activatedMissions = activatedMissions;
        }

        public void encode(FriendlyByteBuf buffer) {
            buffer.writeInt(activatedMissions.size());
            for (String missionId : activatedMissions) {
                buffer.writeUtf(missionId);
            }
        }

        public static SyncPlayerMissionsPacket decode(FriendlyByteBuf buffer) {
            int size = buffer.readInt();
            Set<String> missions = new HashSet<>();
            for (int i = 0; i < size; i++) {
                missions.add(buffer.readUtf());
            }
            return new SyncPlayerMissionsPacket(missions);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                // 客户端处理：更新玩家激活的任务
                ClientMissionData.setActivatedMissions(this.activatedMissions);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public class ClientMissionData {
        private static final Map<String, WorldSharedMissionData.SharedMission> missionProgress = new HashMap<>();
        private static final Set<String> activatedMissions = new HashSet<>();

        public static void updateProgress(Map<String, Integer> progressMap) {
            missionProgress.clear();
            progressMap.forEach((missionId, progressValue) -> {
                // 这里需要从其他地方获取目标值，暂时设为0
                WorldSharedMissionData.SharedMission mission = new WorldSharedMissionData.SharedMission(missionId, 0);
                mission.progress = progressValue;
                missionProgress.put(missionId, mission);
            });
        }
        public static void updateAllMissionProgress(Map<String, WorldSharedMissionData.SharedMission> progressMap) {
            missionProgress.clear();
            missionProgress.putAll(progressMap);
        }

        public static void updateMissionProgress(String instanceId, WorldSharedMissionData.SharedMission mission) {
            missionProgress.put(instanceId, mission);
        }

        public static WorldSharedMissionData.SharedMission getMissionProgress(String missionId) {
            return missionProgress.get(missionId);
        }

        public static void setActivatedMissions(Set<String> missions) {
            activatedMissions.clear();
            activatedMissions.addAll(missions);
        }

        public static Set<String> getActivatedMissions() {
            return new HashSet<>(activatedMissions);
        }
    }

}