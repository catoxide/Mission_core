package com.catoxide.missioncore;

import com.catoxide.missioncore.trigger.MissionTrigger;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class MissionEventListener {
    @SubscribeEvent
    public void onEntityDeath(LivingDeathEvent event) {
        if (!(event.getEntity().getCommandSenderWorld() instanceof ServerLevel level)) return;
        if (!(event.getSource().getEntity() instanceof Player player)) return;

        WorldSharedMissionData sharedData = WorldSharedMissionData.get(level);

        // 获取玩家激活的任务实例
        player.getCapability(ModCapabilities.PLAYER_MISSIONS).ifPresent(playerData -> {
            for (String instanceId : playerData.getActivatedMissionInstances()) {
                Mission mission = MissionInstanceManager.getMission(instanceId);
                if (mission == null) continue;

                MissionDefinition def = MissionRegistry.getMission(mission.getDefinitionId());
                if (def == null) continue;

                // 使用预配置的触发器
                MissionTrigger trigger = def.getTrigger();
                if (trigger == null) {
                    continue;
                }

                if (trigger.shouldTrigger(player, event)) {
                    if (mission.isShared()) {
                        // 更新共享任务进度
                        sharedData.updateMissionProgress(level, instanceId, 1);
                        MissionCore.LOGGER.info("玩家 {} 完成共享任务进度: {}", player.getName().getString(), instanceId);
                    } else {
                        // TODO: 更新玩家个人任务进度
                        MissionCore.LOGGER.info("玩家 {} 完成个人任务进度: {}", player.getName().getString(), instanceId);
                    }
                }
            }
        });
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled()) return; // 检查事件是否被取消
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Player player = event.getPlayer();

        WorldSharedMissionData sharedData = WorldSharedMissionData.get(level);

        // 获取玩家激活的任务实例
        player.getCapability(ModCapabilities.PLAYER_MISSIONS).ifPresent(playerData -> {
            for (String instanceId : playerData.getActivatedMissionInstances()) {
                Mission mission = MissionInstanceManager.getMission(instanceId);
                if (mission == null) continue;

                MissionDefinition def = MissionRegistry.getMission(mission.getDefinitionId());
                if (def == null) continue;

                // 使用预配置的触发器
                MissionTrigger trigger = def.getTrigger();
                if (trigger == null) {
                    continue;
                }

                if (trigger.shouldTrigger(player, event)) {
                    if (mission.isShared()) {
                        // 更新共享任务进度
                        sharedData.updateMissionProgress(level, instanceId, 1);
                        MissionCore.LOGGER.info("玩家 {} 破坏方块，更新共享任务进度: {}", player.getName().getString(), instanceId);
                    } else {
                        // TODO: 更新玩家个人任务进度
                        MissionCore.LOGGER.info("玩家 {} 破坏方块，更新个人任务进度: {}", player.getName().getString(), instanceId);
                    }
                }
            }
        });
    }

    @SubscribeEvent
    public void onBlockStateChange(BlockEvent.NeighborNotifyEvent event) {
        if (event.isCanceled()) return; // 检查事件是否被取消
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        // 获取附近的玩家（简化处理，实际可能需要更精确的玩家检测）
        Player foundPlayer = null;
        for (Player p : level.players()) {
            if (p.blockPosition().closerThan(event.getPos(), 16)) { // 16格范围内的玩家
                foundPlayer = p;
                break;
            }
        }

        if (foundPlayer == null) return;

        final Player player = foundPlayer; // 创建 final 引用
        WorldSharedMissionData sharedData = WorldSharedMissionData.get(level);

        // 获取玩家激活的任务实例
        player.getCapability(ModCapabilities.PLAYER_MISSIONS).ifPresent(playerData -> {
            for (String instanceId : playerData.getActivatedMissionInstances()) {
                final String finalInstanceId = instanceId; // 创建 final 引用
                Mission mission = MissionInstanceManager.getMission(finalInstanceId);
                if (mission == null) continue;

                MissionDefinition def = MissionRegistry.getMission(mission.getDefinitionId());
                if (def == null) continue;

                // 使用预配置的触发器
                MissionTrigger trigger = def.getTrigger();
                if (trigger == null) {
                    continue;
                }

                if (trigger.shouldTrigger(player, event)) {
                    if (mission.isShared()) {
                        // 更新共享任务进度
                        sharedData.updateMissionProgress(level, finalInstanceId, 1);
                        MissionCore.LOGGER.info("玩家 {} 附近方块状态变化，更新共享任务进度: {}", player.getName().getString(), finalInstanceId);
                    } else {
                        // TODO: 更新玩家个人任务进度
                        MissionCore.LOGGER.info("玩家 {} 附近方块状态变化，更新个人任务进度: {}", player.getName().getString(), finalInstanceId);
                    }
                }
            }
        });
    }

    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent event) {
        // 不检查事件是否被取消，因为我们想要记录所有交互尝试
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;

        Player player = event.getEntity();
        WorldSharedMissionData sharedData = WorldSharedMissionData.get(level);

        // 获取玩家激活的任务实例
        player.getCapability(ModCapabilities.PLAYER_MISSIONS).ifPresent(playerData -> {
            for (String instanceId : playerData.getActivatedMissionInstances()) {
                Mission mission = MissionInstanceManager.getMission(instanceId);
                if (mission == null) continue;

                MissionDefinition def = MissionRegistry.getMission(mission.getDefinitionId());
                if (def == null) continue;

                // 使用预配置的触发器
                MissionTrigger trigger = def.getTrigger();
                if (trigger == null) {
                    continue;
                }

                // 检查触发器是否应该触发
                boolean shouldTrigger = trigger.shouldTrigger(player, event);

                if (shouldTrigger) {
                    if (mission.isShared()) {
                        // 更新共享任务进度
                        sharedData.updateMissionProgress(level, instanceId, 1);
                        MissionCore.LOGGER.info("玩家 {} 交互行为，更新共享任务进度: {}", player.getName().getString(), instanceId);
                    } else {
                        // TODO: 更新玩家个人任务进度
                        MissionCore.LOGGER.info("玩家 {} 交互行为，更新个人任务进度: {}", player.getName().getString(), instanceId);
                    }
                }
            }
        });
    }
}