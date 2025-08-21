package com.catoxide.missioncore;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

public class MissionCommand {
    // 自动补全提供器
    private static final SuggestionProvider<CommandSourceStack> MISSION_DEFINITION_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(
                    MissionRegistry.getAllMissions().stream()
                            .map(MissionDefinition::getId)
                            .collect(Collectors.toList()),
                    builder);

    private static final SuggestionProvider<CommandSourceStack> SHARED_MISSION_SUGGESTIONS =
            (context, builder) -> {
                if (context.getSource().getLevel() instanceof ServerLevel) {
                    ServerLevel level = (ServerLevel) context.getSource().getLevel();
                    WorldSharedMissionData sharedData = WorldSharedMissionData.get(level);
                    return SharedSuggestionProvider.suggest(sharedData.sharedMissions.keySet(), builder);
                }
                return builder.buildFuture();
            };

    private static final SuggestionProvider<CommandSourceStack> ACTIVATABLE_MISSION_SUGGESTIONS =
            (context, builder) -> {
                if (context.getSource().getLevel() instanceof ServerLevel) {
                    ServerLevel level = (ServerLevel) context.getSource().getLevel();
                    WorldSharedMissionData sharedData = WorldSharedMissionData.get(level);

                    // 只建议未完成的共享任务
                    Set<String> activatableMissions = sharedData.sharedMissions.entrySet().stream()
                            .filter(entry -> !entry.getValue().completed)
                            .map(entry -> {
                                Mission mission = MissionInstanceManager.getMission(entry.getKey());
                                if (mission != null) {
                                    MissionDefinition def = MissionRegistry.getMission(mission.getDefinitionId());
                                    if (def != null) {
                                        return def.getTitle() + " (" + entry.getKey() + ")";
                                    }
                                }
                                return entry.getKey();
                            })
                            .collect(Collectors.toSet());

                    return SharedSuggestionProvider.suggest(activatableMissions, builder);
                }
                return builder.buildFuture();
            };

    private static final SuggestionProvider<CommandSourceStack> PLAYER_MISSION_SUGGESTIONS =
            (context, builder) -> {
                try {
                    ServerPlayer player = EntityArgument.getPlayer(context, "target");
                    return player.getCapability(ModCapabilities.PLAYER_MISSIONS)
                            .map(data -> SharedSuggestionProvider.suggest(data.getActivatedMissionInstances(), builder))
                            .orElse(builder.buildFuture());
                } catch (Exception e) {
                    return builder.buildFuture();
                }
            };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("mission")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("list")
                        .executes(ctx -> listMissions(ctx.getSource()))
                )
                .then(Commands.literal("trigger")
                        .then(Commands.argument("event", StringArgumentType.string())
                                .executes(ctx -> triggerEvent(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "event")
                                ))
                        )
                )
                .then(Commands.literal("hud")
                        .then(Commands.literal("toggle")
                                .executes(ctx -> toggleHud(ctx.getSource()))
                        )
                        .then(Commands.literal("position")
                                .then(Commands.argument("x", IntegerArgumentType.integer(0, 1000))
                                        .then(Commands.argument("y", IntegerArgumentType.integer(0, 1000))
                                                .executes(ctx -> setHudPosition(
                                                        ctx.getSource(),
                                                        IntegerArgumentType.getInteger(ctx, "x"),
                                                        IntegerArgumentType.getInteger(ctx, "y")
                                                ))
                                        )
                                )
                        )
                        .then(Commands.literal("opacity")
                                .then(Commands.argument("value", IntegerArgumentType.integer(10, 100))
                                        .executes(ctx -> setHudOpacity(
                                                ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "value")
                                        ))
                                )
                        )
                )
                .then(Commands.literal("shared")
                        .then(Commands.literal("add")
                                .then(Commands.argument("definition_id", StringArgumentType.string())
                                        .suggests(MISSION_DEFINITION_SUGGESTIONS)
                                        .executes(ctx -> addSharedMission(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "definition_id")
                                        ))
                                )
                        )
                        .then(Commands.literal("remove")
                                .then(Commands.argument("instance_id", StringArgumentType.string())
                                        .suggests(SHARED_MISSION_SUGGESTIONS)
                                        .executes(ctx -> removeSharedMission(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "instance_id")
                                        ))
                                )
                        )
                        .then(Commands.literal("progress")
                                .then(Commands.argument("instance_id", StringArgumentType.string())
                                        .suggests(SHARED_MISSION_SUGGESTIONS)
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                .executes(ctx -> addSharedMissionProgress(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "instance_id"),
                                                        IntegerArgumentType.getInteger(ctx, "amount")
                                                ))
                                        )
                                )
                        )
                        .then(Commands.literal("list")
                                .executes(ctx -> listSharedMissions(ctx.getSource()))
                        )
                )
                .then(Commands.literal("player")
                        .then(Commands.argument("target", EntityArgument.player())
                                .then(Commands.literal("activate")
                                        .then(Commands.argument("instance_id", StringArgumentType.string())
                                                .suggests(SHARED_MISSION_SUGGESTIONS)
                                                .executes(ctx -> activatePlayerMission(
                                                        ctx.getSource(),
                                                        EntityArgument.getPlayer(ctx, "target"),
                                                        StringArgumentType.getString(ctx, "instance_id")
                                                ))
                                        )
                                )
                                .then(Commands.literal("deactivate")
                                        .then(Commands.argument("instance_id", StringArgumentType.string())
                                                .suggests(PLAYER_MISSION_SUGGESTIONS)
                                                .executes(ctx -> deactivatePlayerMission(
                                                        ctx.getSource(),
                                                        EntityArgument.getPlayer(ctx, "target"),
                                                        StringArgumentType.getString(ctx, "instance_id")
                                                ))
                                        )
                                )
                                .then(Commands.literal("list")
                                        .executes(ctx -> listPlayerMissions(
                                                ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "target")
                                        ))
                                )
                        )
                )
        );
    }

    // 添加共享任务（创建新实例）- 移除target参数
    private static int addSharedMission(CommandSourceStack source, String definitionId) {
        if (!(source.getLevel() instanceof ServerLevel)) {
            source.sendFailure(Component.literal("只能在服务器环境中执行此命令"));
            return 0;
        }

        ServerLevel level = (ServerLevel) source.getLevel();

        // 获取任务定义
        MissionDefinition def = MissionRegistry.getMission(definitionId);
        if (def == null) {
            source.sendFailure(Component.literal("未知的任务定义ID: " + definitionId));
            return 0;
        }

        // 创建新任务实例
        String instanceId = MissionInstanceManager.createMissionInstance(definitionId, true);
        if (instanceId == null) {
            source.sendFailure(Component.literal("创建任务实例失败"));
            return 0;
        }

        // 添加到共享任务数据，使用任务定义中的目标值
        WorldSharedMissionData sharedData = WorldSharedMissionData.get(level);
        sharedData.activateMission(instanceId, def.getTarget());

        Mission mission = MissionInstanceManager.getMission(instanceId);

        source.sendSuccess(() ->
                Component.literal("已添加共享任务实例: " + def.getTitle() +
                        " (实例ID: " + instanceId + ", 目标: " + def.getTarget() + ")"), true);
        return 1;
    }

    // 列出所有任务定义
    private static int listMissionDefinitions(CommandSourceStack source) {
        Collection<MissionDefinition> missions = MissionRegistry.getAllMissions();
        source.sendSuccess(() -> Component.literal("任务定义 (" + missions.size() + "):"), false);

        missions.forEach(mission -> {
            final String status = mission.getTitle() + " (" + mission.getId() + ", 目标: " + mission.getTarget() + ")";
            source.sendSuccess(() -> Component.literal(" - " + status), false);
        });

        return missions.size();
    }

    // 列出所有共享任务实例
    private static int listSharedMissions(CommandSourceStack source) {
        if (!(source.getLevel() instanceof ServerLevel)) {
            source.sendFailure(Component.literal("只能在服务器环境中执行此命令"));
            return 0;
        }

        ServerLevel level = (ServerLevel) source.getLevel();
        WorldSharedMissionData sharedData = WorldSharedMissionData.get(level);

        source.sendSuccess(() -> Component.literal("共享任务实例 (" + sharedData.sharedMissions.size() + "):"), false);

        if (sharedData.sharedMissions.isEmpty()) {
            source.sendSuccess(() -> Component.literal(" - 没有活动的共享任务"), false);
        } else {
            sharedData.sharedMissions.forEach((instanceId, mission) -> {
                Mission missionObj = MissionInstanceManager.getMission(instanceId);
                if (missionObj != null) {
                    MissionDefinition def = MissionRegistry.getMission(missionObj.getDefinitionId());
                    if (def != null) {
                        final String status = def.getTitle() + " (进度: " + mission.progress + "/" + mission.target +
                                ", 实例ID: " + instanceId + ")" + (mission.completed ? " §a已完成" : "");
                        source.sendSuccess(() -> Component.literal(" - " + status), false);
                    } else {
                        final String status = "未知任务定义 (进度: " + mission.progress + "/" + mission.target +
                                ", 实例ID: " + instanceId + ")" + (mission.completed ? " §a已完成" : "");
                        source.sendSuccess(() -> Component.literal(" - " + status), false);
                    }
                } else {
                    final String status = "未知任务实例 (进度: " + mission.progress + "/" + mission.target +
                            ", 实例ID: " + instanceId + ")" + (mission.completed ? " §a已完成" : "");
                    source.sendSuccess(() -> Component.literal(" - " + status), false);
                }
            });
        }

        return sharedData.sharedMissions.size();
    }

    // 列出玩家任务
    private static int listPlayerMissions(CommandSourceStack source, ServerPlayer player) {
        player.getCapability(ModCapabilities.PLAYER_MISSIONS).ifPresent(data -> {
            Set<String> activatedInstances = data.getActivatedMissionInstances();
            source.sendSuccess(() -> Component.literal("玩家 " + player.getScoreboardName() + " 的任务 (" +
                    activatedInstances.size() + "):"), false);

            if (activatedInstances.isEmpty()) {
                source.sendSuccess(() -> Component.literal(" - 没有激活的任务"), false);
            } else {
                for (String instanceId : activatedInstances) {
                    Mission mission = MissionInstanceManager.getMission(instanceId);
                    if (mission != null) {
                        MissionDefinition def = MissionRegistry.getMission(mission.getDefinitionId());
                        if (def != null) {
                            // 创建最终变量用于lambda表达式
                            final StringBuilder statusBuilder = new StringBuilder();
                            statusBuilder.append(def.getTitle()).append(" (实例ID: ").append(instanceId).append(")");

                            // 如果是共享任务，显示进度
                            if (mission.isShared()) {
                                WorldSharedMissionData sharedData = WorldSharedMissionData.get((ServerLevel) player.level());
                                WorldSharedMissionData.SharedMission sharedMission = sharedData.getSharedMission(instanceId);
                                if (sharedMission != null) {
                                    statusBuilder.append(" 进度: ").append(sharedMission.progress).append("/").append(sharedMission.target);
                                    if (sharedMission.completed) {
                                        statusBuilder.append(" §a已完成");
                                    }
                                }
                            }

                            final String status = statusBuilder.toString();
                            source.sendSuccess(() -> Component.literal(" - " + status), false);
                        } else {
                            final String status = "未知任务定义 (实例ID: " + instanceId + ")";
                            source.sendSuccess(() -> Component.literal(" - " + status), false);
                        }
                    } else {
                        final String status = "未知任务实例 (实例ID: " + instanceId + ")";
                        source.sendSuccess(() -> Component.literal(" - " + status), false);
                    }
                }
            }
        });

        return 1;
    }

    // 列出所有任务定义（旧方法）
    private static int listMissions(CommandSourceStack source) {
        return listMissionDefinitions(source);
    }

    private static int triggerEvent(CommandSourceStack source, String eventType) {
        if (!(source.getEntity() instanceof ServerPlayer)) {
            source.sendFailure(Component.literal("Command must be executed by a player"));
            return 0;
        }

        switch (eventType.toLowerCase()) {
            case "block_break":
                MissionCore.LOGGER.info("Triggering simulated block break event");
                source.sendSuccess(() -> Component.literal("Triggered block_break event"), true);
                break;

            case "entity_kill":
                MissionCore.LOGGER.info("Triggering simulated entity kill event");
                source.sendSuccess(() -> Component.literal("Triggered entity_kill event"), true);
                break;

            case "enter_area":
                MissionCore.LOGGER.info("Triggering simulated area enter event");
                source.sendSuccess(() -> Component.literal("Triggered enter_area event"), true);
                break;

            default:
                source.sendFailure(Component.literal("Unknown event type: " + eventType));
                return 0;
        }

        return 1;
    }

    private static int toggleHud(CommandSourceStack source) {
        HudConfig.enabled = !HudConfig.enabled;
        String status = HudConfig.enabled ? "§a启用" : "§c禁用";
        source.sendSuccess(() ->
                Component.literal("任务HUD已" + status), true);
        return 1;
    }

    private static int setHudPosition(CommandSourceStack source, int x, int y) {
        HudConfig.xOffset = x;
        HudConfig.yOffset = y;
        source.sendSuccess(() ->
                Component.literal("任务HUD位置已设置为: §e" + x + "§r, §e" + y), true);
        return 1;
    }

    private static int setHudOpacity(CommandSourceStack source, int opacity) {
        HudConfig.opacity = opacity;
        source.sendSuccess(() ->
                Component.literal("任务HUD透明度已设置为: §e" + opacity + "%"), true);
        return 1;
    }

    // 移除共享任务
    private static int removeSharedMission(CommandSourceStack source, String instanceId) {
        if (!(source.getLevel() instanceof ServerLevel)) {
            source.sendFailure(Component.literal("只能在服务器环境中执行此命令"));
            return 0;
        }

        ServerLevel level = (ServerLevel) source.getLevel();
        WorldSharedMissionData sharedData = WorldSharedMissionData.get(level);

        // 检查任务是否存在
        WorldSharedMissionData.SharedMission mission = sharedData.getSharedMission(instanceId);
        if (mission == null) {
            source.sendFailure(Component.literal("共享任务实例不存在: " + instanceId));
            return 0;
        }

        // 从共享数据中移除任务
        sharedData.sharedMissions.remove(instanceId);
        sharedData.setDirty();

        // 从实例管理器移除
        MissionInstanceManager.removeMission(instanceId);

        Mission missionObj = MissionInstanceManager.getMission(instanceId);
        if (missionObj != null) {
            MissionDefinition def = MissionRegistry.getMission(missionObj.getDefinitionId());
            if (def != null) {
                source.sendSuccess(() ->
                        Component.literal("已移除共享任务: " + def.getTitle()), true);
            } else {
                source.sendSuccess(() ->
                        Component.literal("已移除共享任务实例: " + instanceId), true);
            }
        } else {
            source.sendSuccess(() ->
                    Component.literal("已移除共享任务实例: " + instanceId), true);
        }

        return 1;
    }

    // 增加共享任务进度
    private static int addSharedMissionProgress(CommandSourceStack source, String instanceId, int amount) {
        if (!(source.getLevel() instanceof ServerLevel)) {
            source.sendFailure(Component.literal("只能在服务器环境中执行此命令"));
            return 0;
        }

        ServerLevel level = (ServerLevel) source.getLevel();
        WorldSharedMissionData sharedData = WorldSharedMissionData.get(level);

        // 检查任务是否存在
        WorldSharedMissionData.SharedMission mission = sharedData.getSharedMission(instanceId);
        if (mission == null) {
            source.sendFailure(Component.literal("共享任务实例不存在: " + instanceId));
            return 0;
        }

        // 更新进度
        sharedData.updateMissionProgress(level, instanceId, amount);

        Mission missionObj = MissionInstanceManager.getMission(instanceId);
        if (missionObj != null) {
            MissionDefinition def = MissionRegistry.getMission(missionObj.getDefinitionId());
            if (def != null) {
                source.sendSuccess(() ->
                        Component.literal("已为共享任务增加进度: " + def.getTitle() + " (+" + amount + ")"), true);
            } else {
                source.sendSuccess(() ->
                        Component.literal("已为共享任务增加进度: " + instanceId + " (+" + amount + ")"), true);
            }
        } else {
            source.sendSuccess(() ->
                    Component.literal("已为共享任务增加进度: " + instanceId + " (+" + amount + ")"), true);
        }
        return 1;
    }

    // 激活玩家任务 - 直接使用实例ID
    private static int activatePlayerMission(CommandSourceStack source, ServerPlayer player, String instanceId) {
        // 检查任务实例是否存在且是共享任务
        Mission mission = MissionInstanceManager.getMission(instanceId);
        if (mission == null || !mission.isShared()) {
            source.sendFailure(Component.literal("无效的任务实例或不是共享任务: " + instanceId));
            return 0;
        }

        // 检查共享任务是否存在
        if (!(player.level() instanceof ServerLevel)) {
            source.sendFailure(Component.literal("无法获取服务器世界"));
            return 0;
        }

        ServerLevel level = (ServerLevel) player.level();
        WorldSharedMissionData sharedData = WorldSharedMissionData.get(level);
        if (sharedData.getSharedMission(instanceId) == null) {
            source.sendFailure(Component.literal("共享任务不存在: " + instanceId));
            return 0;
        }

        player.getCapability(ModCapabilities.PLAYER_MISSIONS).ifPresent(data -> {
            if (!data.hasMissionInstanceActivated(instanceId)) {
                data.activateMissionInstance(instanceId);

                MissionDefinition def = MissionRegistry.getMission(mission.getDefinitionId());
                if (def != null) {
                    source.sendSuccess(() ->
                            Component.literal("已为玩家 " + player.getScoreboardName() + " 激活任务: " + def.getTitle()), true);
                } else {
                    source.sendSuccess(() ->
                            Component.literal("已为玩家 " + player.getScoreboardName() + " 激活任务: " + instanceId), true);
                }
            } else {
                source.sendFailure(Component.literal("玩家已激活此任务"));
            }
        });
        return 1;
    }

    // 取消激活玩家任务 - 直接使用实例ID
    private static int deactivatePlayerMission(CommandSourceStack source, ServerPlayer player, String instanceId) {
        player.getCapability(ModCapabilities.PLAYER_MISSIONS).ifPresent(data -> {
            if (data.hasMissionInstanceActivated(instanceId)) {
                data.deactivateMissionInstance(instanceId);

                Mission mission = MissionInstanceManager.getMission(instanceId);
                if (mission != null) {
                    MissionDefinition def = MissionRegistry.getMission(mission.getDefinitionId());
                    if (def != null) {
                        source.sendSuccess(() ->
                                Component.literal("已为玩家 " + player.getScoreboardName() + " 取消激活任务: " + def.getTitle()), true);
                    } else {
                        source.sendSuccess(() ->
                                Component.literal("已为玩家 " + player.getScoreboardName() + " 取消激活任务: " + instanceId), true);
                    }
                } else {
                    source.sendSuccess(() ->
                            Component.literal("已为玩家 " + player.getScoreboardName() + " 取消激活任务: " + instanceId), true);
                }
            } else {
                source.sendFailure(Component.literal("玩家未激活此任务"));
            }
        });
        return 1;
    }
}