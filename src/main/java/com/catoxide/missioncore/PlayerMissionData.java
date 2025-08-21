package com.catoxide.missioncore;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class PlayerMissionData implements ICapabilitySerializable<CompoundTag> {
    private final Set<String> activatedMissionInstances = new HashSet<>();
    private final LazyOptional<PlayerMissionData> holder = LazyOptional.of(() -> this);
    private final Player player;

    public PlayerMissionData(Player player) {
        this.player = player;
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        return ModCapabilities.PLAYER_MISSIONS.orEmpty(cap, holder);
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();

        // 序列化激活的任务实例
        ListTag instancesList = new ListTag();
        for (String instanceId : activatedMissionInstances) {
            instancesList.add(StringTag.valueOf(instanceId));
        }
        nbt.put("activatedMissionInstances", instancesList);

        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        activatedMissionInstances.clear();

        // 加载实例ID
        if (nbt.contains("activatedMissionInstances", Tag.TAG_LIST)) {
            ListTag list = nbt.getList("activatedMissionInstances", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                activatedMissionInstances.add(list.getString(i));
            }
        }
    }

    public void activateMissionInstance(String instanceId) {
        activatedMissionInstances.add(instanceId);
        setDirty();
        syncToClient(); // 新增：同步到客户端
    }

    public void deactivateMissionInstance(String instanceId) {
        activatedMissionInstances.remove(instanceId);
        setDirty();
        syncToClient(); // 新增：同步到客户端
    }

    public boolean hasMissionInstanceActivated(String instanceId) {
        return activatedMissionInstances.contains(instanceId);
    }

    public Set<String> getActivatedMissionInstances() {
        return Collections.unmodifiableSet(activatedMissionInstances);
    }

    public Mission getMission(String instanceId) {
        return MissionInstanceManager.getMission(instanceId);
    }
    private void syncToClient() {
        if (player instanceof ServerPlayer) {
            MissionCore.LOGGER.info("同步 {} 个激活任务给玩家 {}",
                    activatedMissionInstances.size(), player.getName().getString());

            NetworkHandler.INSTANCE.send(
                    PacketDistributor.PLAYER.with(() -> (ServerPlayer) player),
                    new NetworkHandler.SyncPlayerMissionsPacket(activatedMissionInstances)
            );
        }
    }

    // 标记数据已更改
    public void setDirty() {
        // 通知能力系统数据已更改
        holder.invalidate();
    }

    // 附加能力到玩家实体
    public static void attach(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            Player player = (Player) event.getObject();
            PlayerMissionData data = new PlayerMissionData(player); // 传入player参数
            event.addCapability(
                    ResourceLocation.parse("missioncore:player_missions"),
                    new ICapabilityProvider() {
                        @Nonnull
                        @Override
                        public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
                            return ModCapabilities.PLAYER_MISSIONS.orEmpty(cap, LazyOptional.of(() -> data));
                        }
                    }
            );
        }
    }

    // 玩家克隆时复制数据
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.isWasDeath()) return;

        Player original = event.getOriginal();
        Player newPlayer = event.getEntity();

        original.getCapability(ModCapabilities.PLAYER_MISSIONS).ifPresent(oldData -> {
            newPlayer.getCapability(ModCapabilities.PLAYER_MISSIONS).ifPresent(newData -> {
                newData.activatedMissionInstances.clear();
                newData.activatedMissionInstances.addAll(oldData.activatedMissionInstances);

                if (newPlayer instanceof ServerPlayer) {
                    newData.setDirty();
                }
            });
        });
    }

    // 玩家保存数据时触发
    public static void onPlayerSave(PlayerEvent.SaveToFile event) {
        if (event.getEntity() instanceof ServerPlayer) {
            ServerPlayer player = (ServerPlayer) event.getEntity();
            player.getCapability(ModCapabilities.PLAYER_MISSIONS).ifPresent(data -> {
                data.setDirty();
            });
        }
    }
}