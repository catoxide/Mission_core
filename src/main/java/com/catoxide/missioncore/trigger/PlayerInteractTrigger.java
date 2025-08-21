package com.catoxide.missioncore.trigger;

import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

public class PlayerInteractTrigger implements MissionTrigger {
    public enum InteractionType {
        LEFT_CLICK_BLOCK,
        RIGHT_CLICK_BLOCK,
        RIGHT_CLICK_ITEM,
        ANY_INTERACTION
    }

    public enum HandType {
        MAIN_HAND,
        OFF_HAND,
        EITHER_HAND
    }

    private Item requiredItem;
    private Block requiredBlock;
    private Map<String, String> requiredBlockState; // 新增：方块状态条件
    private TagKey<Block> requiredBlockTag; // 新增：方块标签
    private InteractionType interactionType;
    private HandType handType;
    private boolean requireItem;
    private boolean requireBlock;
    private boolean requireBlockState; // 新增：是否需要检查方块状态
    private boolean requireBlockTag;   // 新增：是否需要检查方块标签

    // 用于防止重复触发
    private final Map<String, Long> lastTriggerTimes = new HashMap<>();
    private static final long COOLDOWN_MS = 200; // 200毫秒冷却时间

    @Override
    public MissionTrigger createNewInstance() {
        return new PlayerInteractTrigger();
    }

    @Override
    public void configure(JsonObject config) {
        // 解析交互类型
        String interactTypeStr = config.has("interaction_type") ?
                config.get("interaction_type").getAsString() : "right_click_block";

        switch (interactTypeStr.toLowerCase()) {
            case "left_click_block":
                interactionType = InteractionType.LEFT_CLICK_BLOCK;
                break;
            case "right_click_block":
                interactionType = InteractionType.RIGHT_CLICK_BLOCK;
                break;
            case "right_click_item":
                interactionType = InteractionType.RIGHT_CLICK_ITEM;
                break;
            case "any_interaction":
            default:
                interactionType = InteractionType.ANY_INTERACTION;
                break;
        }

        // 解析手部类型
        String handTypeStr = config.has("hand_type") ?
                config.get("hand_type").getAsString() : "either_hand";

        switch (handTypeStr.toLowerCase()) {
            case "main_hand":
                handType = HandType.MAIN_HAND;
                break;
            case "off_hand":
                handType = HandType.OFF_HAND;
                break;
            case "either_hand":
            default:
                handType = HandType.EITHER_HAND;
                break;
        }

        // 解析所需物品
        requireItem = config.has("item");
        if (requireItem) {
            String itemId = config.get("item").getAsString();
            requiredItem = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(itemId));
            if (requiredItem == null) {
                System.err.println("PlayerInteractTrigger: 无法找到物品: " + itemId);
                requireItem = false;
            } else {
                System.out.println("PlayerInteractTrigger: 配置物品 " + ForgeRegistries.ITEMS.getKey(requiredItem) + " 成功");
            }
        }

        // 解析所需方块
        requireBlock = config.has("block");
        if (requireBlock) {
            String blockId = config.get("block").getAsString();
            requiredBlock = ForgeRegistries.BLOCKS.getValue(ResourceLocation.parse(blockId));
            if (requiredBlock == null) {
                System.err.println("PlayerInteractTrigger: 无法找到方块: " + blockId);
                requireBlock = false;
            } else {
                System.out.println("PlayerInteractTrigger: 配置方块 " + ForgeRegistries.BLOCKS.getKey(requiredBlock) + " 成功");
            }
        }

        // 新增：解析方块状态
        requireBlockState = config.has("block_state");
        if (requireBlockState) {
            requiredBlockState = new HashMap<>();
            JsonObject stateObj = config.getAsJsonObject("block_state");
            for (Map.Entry<String, com.google.gson.JsonElement> entry : stateObj.entrySet()) {
                requiredBlockState.put(entry.getKey(), entry.getValue().getAsString());
            }
            System.out.println("PlayerInteractTrigger: 配置方块状态 " + requiredBlockState + " 成功");
        }

        // 新增：解析方块标签
        requireBlockTag = config.has("block_tag");
        if (requireBlockTag) {
            String tagId = config.get("block_tag").getAsString();
            requiredBlockTag = BlockTags.create(ResourceLocation.parse(tagId));
            System.out.println("PlayerInteractTrigger: 配置方块标签 " + tagId + " 成功");
        }

        System.out.println("PlayerInteractTrigger: 配置完成 - 交互类型: " + interactionType +
                ", 手部类型: " + handType +
                (requireItem ? ", 物品: " + ForgeRegistries.ITEMS.getKey(requiredItem) : "") +
                (requireBlock ? ", 方块: " + ForgeRegistries.BLOCKS.getKey(requiredBlock) : "") +
                (requireBlockState ? ", 方块状态: " + requiredBlockState : "") +
                (requireBlockTag ? ", 方块标签: " + requiredBlockTag.location() : ""));
    }

    @Override
    public boolean shouldTrigger(Player player, Event event) {
        if (!(event instanceof PlayerInteractEvent)) return false;

        PlayerInteractEvent interactEvent = (PlayerInteractEvent) event;

        // 防止重复触发
        String triggerKey = player.getStringUUID() + "-" + System.currentTimeMillis() / COOLDOWN_MS;
        if (lastTriggerTimes.containsKey(triggerKey)) {
            return false;
        }
        lastTriggerTimes.put(triggerKey, System.currentTimeMillis());

        // 清理过期的触发记录
        cleanupOldTriggers();

        // 检查交互类型
        if (!checkInteractionType(interactEvent)) {
            return false;
        }

        // 检查手部类型
        if (!checkHandType(interactEvent.getHand())) {
            return false;
        }

        // 检查物品
        if (requireItem && !checkItem(player, interactEvent.getHand())) {
            return false;
        }

        // 检查方块
        if ((requireBlock || requireBlockState || requireBlockTag) && !checkBlock(interactEvent)) {
            return false;
        }

        // 所有条件都满足
        System.out.println("PlayerInteractTrigger: 玩家 " + player.getName().getString() +
                " 触发交互 - 类型: " + interactEvent.getClass().getSimpleName() +
                ", 手: " + interactEvent.getHand() +
                (requireItem ? ", 物品: " + getItemName(player.getItemInHand(interactEvent.getHand())) : "") +
                (requireBlock || requireBlockState || requireBlockTag ? ", 方块: " + getBlockName(interactEvent) : ""));

        return true;
    }

    private boolean checkInteractionType(PlayerInteractEvent event) {
        // 检查事件类型是否匹配配置
        switch (interactionType) {
            case LEFT_CLICK_BLOCK:
                // 只处理开始挖掘事件，忽略结束挖掘事件
                return event instanceof PlayerInteractEvent.LeftClickBlock &&
                        !((PlayerInteractEvent.LeftClickBlock) event).isCanceled();
            case RIGHT_CLICK_BLOCK:
                return event instanceof PlayerInteractEvent.RightClickBlock;
            case RIGHT_CLICK_ITEM:
                return event instanceof PlayerInteractEvent.RightClickItem;
            case ANY_INTERACTION:
                return true;
            default:
                return false;
        }
    }

    private boolean checkHandType(InteractionHand hand) {
        switch (handType) {
            case MAIN_HAND:
                return hand == InteractionHand.MAIN_HAND;
            case OFF_HAND:
                return hand == InteractionHand.OFF_HAND;
            case EITHER_HAND:
                return true;
            default:
                return false;
        }
    }

    private boolean checkItem(Player player, InteractionHand hand) {
        if (requiredItem == null) return false;

        ItemStack heldItem = player.getItemInHand(hand);
        return heldItem.getItem() == requiredItem;
    }

    private boolean checkBlock(PlayerInteractEvent event) {
        // 尝试获取方块位置和世界
        BlockPos pos = null;
        Level level = null;

        if (event.getClass().getSimpleName().contains("ClickBlock")) {
            try {
                java.lang.reflect.Method getPosMethod = event.getClass().getMethod("getPos");
                pos = (BlockPos) getPosMethod.invoke(event);
                java.lang.reflect.Method getLevelMethod = event.getClass().getMethod("getLevel");
                level = (Level) getLevelMethod.invoke(event);
            } catch (Exception e) {
                System.err.println("PlayerInteractTrigger: 无法获取方块位置信息: " + e.getMessage());
                return false;
            }
        }

        if (pos == null || level == null) return false;

        BlockState state = level.getBlockState(pos);

        // 1. 检查方块类型（原有逻辑）
        if (requireBlock && state.getBlock() != requiredBlock) {
            return false;
        }

        // 2. 检查方块标签（新增）
        if (requireBlockTag && !state.is(requiredBlockTag)) {
            return false;
        }

        // 3. 检查方块状态（新增）
        if (requireBlockState && !checkBlockState(state)) {
            return false;
        }

        return true;
    }

    private boolean checkBlockState(BlockState state) {
        for (Map.Entry<String, String> entry : requiredBlockState.entrySet()) {
            String propertyName = entry.getKey();
            String expectedValue = entry.getValue();

            Property<?> property = state.getBlock().getStateDefinition().getProperty(propertyName);
            if (property == null) {
                System.err.println("PlayerInteractTrigger: 方块 " + ForgeRegistries.BLOCKS.getKey(state.getBlock()) + " 没有属性: " + propertyName);
                return false;
            }

            Comparable<?> actualValue = state.getValue(property);
            if (!actualValue.toString().equals(expectedValue)) {
                return false;
            }
        }
        return true;
    }

    private void cleanupOldTriggers() {
        // 清理超过冷却时间两倍的旧记录
        long currentTime = System.currentTimeMillis();
        lastTriggerTimes.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > COOLDOWN_MS * 2
        );
    }

    private String getItemName(ItemStack itemStack) {
        return itemStack.isEmpty() ? "空手" : ForgeRegistries.ITEMS.getKey(itemStack.getItem()).toString();
    }

    private String getBlockName(PlayerInteractEvent event) {
        try {
            if (event.getClass().getSimpleName().contains("ClickBlock")) {
                java.lang.reflect.Method getPosMethod = event.getClass().getMethod("getPos");
                BlockPos pos = (BlockPos) getPosMethod.invoke(event);

                java.lang.reflect.Method getLevelMethod = event.getClass().getMethod("getLevel");
                Level level = (Level) getLevelMethod.invoke(event);

                if (pos != null && level != null) {
                    BlockState state = level.getBlockState(pos);
                    String blockName = ForgeRegistries.BLOCKS.getKey(state.getBlock()).toString();

                    // 添加状态信息
                    if (requireBlockState && !state.getProperties().isEmpty()) {
                        StringBuilder stateInfo = new StringBuilder("[");
                        for (Property<?> prop : state.getProperties()) {
                            if (stateInfo.length() > 1) stateInfo.append(", ");
                            stateInfo.append(prop.getName()).append("=").append(state.getValue(prop));
                        }
                        stateInfo.append("]");
                        blockName += stateInfo.toString();
                    }

                    return blockName;
                }
            }
        } catch (Exception e) {
            // 忽略错误
        }

        return "未知方块";
    }
}