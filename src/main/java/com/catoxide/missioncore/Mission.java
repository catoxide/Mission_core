package com.catoxide.missioncore;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

public class Mission {
    private final String instanceId; // 新增：任务实例唯一ID
    private final String definitionId; // 修改：改为引用任务定义ID
    private String title;
    private boolean isShared;

    public Mission(String instanceId, String definitionId, String title, boolean isShared) {
        this.instanceId = instanceId;
        this.definitionId = definitionId;
        this.title = title;
        this.isShared = isShared;
    }

    // 添加getter方法
    public String getInstanceId() { return instanceId; }
    public String getDefinitionId() { return definitionId; }
    public String getTitle() { return title; }
    public boolean isShared() { return isShared; } // 添加缺失的方法

    // 修改序列化方法
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("instanceId", instanceId);
        tag.putString("definitionId", definitionId);
        tag.putString("title", title);
        tag.putBoolean("isShared", isShared);
        return tag;
    }

    public static Mission deserializeNBT(CompoundTag tag) {
        return new Mission(
                tag.getString("instanceId"),
                tag.getString("definitionId"),
                tag.getString("title"),
                tag.getBoolean("isShared")
        );
    }
}