package com.catoxide.missioncore.trigger;

import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class BlockStateChangeTrigger implements MissionTrigger {
    private Block requiredBlock;
    private String targetProperty;
    private String targetValue;

    // 用于跟踪上次处理的事件，防止重复触发
    private final Map<BlockPos, Long> lastProcessedTimes = new HashMap<>();
    private static final long COOLDOWN_MS = 100; // 100毫秒冷却时间

    @Override
    public MissionTrigger createNewInstance() {
        return new BlockStateChangeTrigger();
    }

    @Override
    public void configure(JsonObject config) {
        // 获取要监听的方块
        String blockId = config.get("block").getAsString();
        requiredBlock = ForgeRegistries.BLOCKS.getValue(ResourceLocation.parse(blockId));

        // 获取要监听的属性（可选）
        if (config.has("property")) {
            targetProperty = config.get("property").getAsString();
        }

        // 获取期望的属性值（可选）
        if (config.has("value")) {
            targetValue = config.get("value").getAsString();
        }

        // 添加调试日志
        if (requiredBlock == null) {
            System.err.println("BlockStateChangeTrigger: 无法找到方块: " + blockId);
        } else {
            System.out.println("BlockStateChangeTrigger: 配置方块 " + ForgeRegistries.BLOCKS.getKey(requiredBlock) +
                    (targetProperty != null ? " 属性 " + targetProperty : "") +
                    (targetValue != null ? " 值 " + targetValue : "") + " 成功");
        }
    }

    @Override
    public boolean shouldTrigger(Player player, Event event) {
        if (!(event instanceof BlockEvent.NeighborNotifyEvent neighborEvent)) return false;
        if (!(neighborEvent.getLevel() instanceof Level level)) return false;

        BlockPos pos = neighborEvent.getPos();
        BlockState state = level.getBlockState(pos);

        // 检查是否为指定的方块
        if (state.getBlock() != requiredBlock) return false;

        // 防止重复触发 - 添加冷却时间
        long currentTime = System.currentTimeMillis();
        Long lastTime = lastProcessedTimes.get(pos);
        if (lastTime != null && currentTime - lastTime < COOLDOWN_MS) {
            return false;
        }
        lastProcessedTimes.put(pos, currentTime);

        // 如果没有指定属性，只要方块变化就触发
        if (targetProperty == null) {
            System.out.println("BlockStateChangeTrigger: 方块 " + ForgeRegistries.BLOCKS.getKey(requiredBlock) +
                    " 状态变化，位置 " + pos);
            return true;
        }

        // 检查指定的属性
        Optional<Property<?>> property = state.getProperties().stream()
                .filter(p -> p.getName().equals(targetProperty))
                .findFirst();

        if (!property.isPresent()) {
            System.out.println("BlockStateChangeTrigger: 方块 " + ForgeRegistries.BLOCKS.getKey(requiredBlock) +
                    " 没有属性 " + targetProperty);
            return false;
        }

        // 获取属性当前值
        String currentValue = state.getValue(property.get()).toString();

        // 如果没有指定期望值，只要属性存在就触发
        if (targetValue == null) {
            System.out.println("BlockStateChangeTrigger: 方块 " + ForgeRegistries.BLOCKS.getKey(requiredBlock) +
                    " 属性 " + targetProperty + " 变化，当前值: " + currentValue);
            return true;
        }

        // 检查属性值是否匹配
        boolean matches = currentValue.equals(targetValue);
        if (matches) {
            System.out.println("BlockStateChangeTrigger: 方块 " + ForgeRegistries.BLOCKS.getKey(requiredBlock) +
                    " 属性 " + targetProperty + " 达到目标值: " + targetValue);
        }

        return matches;
    }
}