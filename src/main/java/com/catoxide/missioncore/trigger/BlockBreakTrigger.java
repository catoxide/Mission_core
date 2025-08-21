package com.catoxide.missioncore.trigger;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.registries.ForgeRegistries;

public class BlockBreakTrigger implements MissionTrigger {
    private Block requiredBlock;

    @Override
    public MissionTrigger createNewInstance() {
        return new BlockBreakTrigger();
    }

    @Override
    public void configure(JsonObject config) {
        String blockId = config.get("block").getAsString();
        requiredBlock = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(blockId));

        // 添加调试日志
        if (requiredBlock == null) {
            System.err.println("BlockBreakTrigger: 无法找到方块: " + blockId);
        } else {
            System.out.println("BlockBreakTrigger: 配置方块 " + blockId + " 成功");
        }
    }

    @Override
    public boolean shouldTrigger(Player player, Event event) {
        if (!(event instanceof BlockEvent.BreakEvent breakEvent)) return false;

        // 添加调试日志
        boolean matches = breakEvent.getState().getBlock() == requiredBlock;
        if (matches) {
            System.out.println("BlockBreakTrigger: 玩家 " + player.getName().getString() +
                    " 破坏了方块 " + ForgeRegistries.BLOCKS.getKey(requiredBlock));
        }

        return matches;
    }
}