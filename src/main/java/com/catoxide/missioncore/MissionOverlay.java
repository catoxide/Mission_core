package com.catoxide.missioncore;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.Set;

public class MissionOverlay implements IGuiOverlay {
    private boolean debugRendered = false;
    private long lastLogTime = 0;
    private boolean hasLoggedNoMissions = false;

    @Override
    public void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick,
                       int screenWidth, int screenHeight) {
        Minecraft minecraft = Minecraft.getInstance();

        // 条件检查：如果HUD被隐藏，则返回
        if (minecraft.options.hideGui) {
            return;
        }
        if (minecraft.screen != null) {
            // 只记录一次调试信息，避免日志刷屏
            if (!debugRendered) {
                MissionCore.LOGGER.debug("HUD隐藏: 打开界面 {}", minecraft.screen.getClass().getSimpleName());
                debugRendered = true;
            }
            return;
        }
        if (minecraft.player == null) {
            return;
        }
        if (!HudConfig.enabled) {
            return;
        }

        // 重置调试标志
        debugRendered = false;

        Player player = minecraft.player;
        int x = HudConfig.xOffset;
        final int[] y = {HudConfig.yOffset};
        int spacing = 12;

        // 获取玩家激活的任务实例 - 直接从客户端数据获取
        Set<String> activatedMissionInstances = NetworkHandler.ClientMissionData.getActivatedMissions();

        // 添加调试信息 - 减少日志频率
        long currentTime = System.currentTimeMillis();
        if (activatedMissionInstances.isEmpty()) {
            if (!hasLoggedNoMissions || currentTime - lastLogTime > 5000) {
                MissionCore.LOGGER.debug("没有激活的任务");
                lastLogTime = currentTime;
                hasLoggedNoMissions = true;
            }
            // 显示提示信息
            guiGraphics.drawString(
                    minecraft.font,
                    "没有激活的任务",
                    x,
                    y[0],
                    0xFFFFFF,
                    false
            );
            return;
        }

        hasLoggedNoMissions = false;

        if (currentTime - lastLogTime > 5000) {
            MissionCore.LOGGER.debug("渲染{}个激活的任务", activatedMissionInstances.size());
            lastLogTime = currentTime;
        }

        for (String instanceId : activatedMissionInstances) {
            Mission mission = MissionInstanceManager.getMission(instanceId);
            if (mission == null) continue;

            MissionDefinition def = MissionRegistry.getMission(mission.getDefinitionId());
            if (def == null) continue;

            // 获取任务进度
            String progressText = "进度未知";
            int color = 0xFFFFFF; // 白色

            if (mission.isShared()) {
                // 对于共享任务，尝试获取进度
                // 替换 MissionOverlay.java 中的进度显示部分
                WorldSharedMissionData.SharedMission sharedMission = NetworkHandler.ClientMissionData.getMissionProgress(instanceId);
                if (sharedMission != null) {
                    progressText = sharedMission.progress + "/" + sharedMission.target;
                    if (sharedMission.completed) {
                        progressText += " §a已完成";
                        color = 0x00FF00; // 绿色
                    }
                } else {
                    // 尝试从任务定义获取目标值（作为备选方案）
                    MissionDefinition defg = MissionRegistry.getMission(mission.getDefinitionId());
                    if (defg != null) {
                        progressText = "0/" + def.getTarget(); // 默认进度为0
                    }
                }
            }

            // 格式化显示
            String text = def.getTitle() + ": " + progressText;
            guiGraphics.drawString(
                    minecraft.font,
                    Component.literal(text), // 使用Component以支持颜色代码
                    x,
                    y[0],
                    color,
                    false
            );
            y[0] += spacing;
        }
    }
}