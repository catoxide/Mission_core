package com.catoxide.missioncore;

import com.catoxide.missioncore.trigger.MissionTrigger;
import com.google.gson.JsonObject;

public class MissionDefinition {
    private final String id;
    private final String title;
    private final int target;
    private final JsonObject triggerConfig;
    private final boolean isShared;
    private MissionTrigger trigger; // 预配置的触发器

    public MissionDefinition(String id, String title, int target, JsonObject triggerConfig, boolean isShared) {
        this.id = id;
        this.title = title;
        this.target = target;
        this.triggerConfig = triggerConfig;
        this.isShared = isShared;

        // 预配置触发器 - 为每个任务创建新的触发器实例
        if (triggerConfig.has("type")) {
            String triggerType = triggerConfig.get("type").getAsString();
            MissionTrigger prototype = MissionRegistry.getTriggerPrototype(triggerType);
            if (prototype != null) {
                try {
                    // 创建新的触发器实例
                    this.trigger = prototype.createNewInstance();
                    this.trigger.configure(triggerConfig);
                    MissionCore.LOGGER.info("任务 {} 触发器配置成功: {}", id, triggerType);
                } catch (Exception e) {
                    MissionCore.LOGGER.error("预配置触发器失败: {}", triggerType, e);
                    this.trigger = null;
                }
            } else {
                MissionCore.LOGGER.error("未知的触发器类型: {}", triggerType);
            }
        } else {
            MissionCore.LOGGER.error("任务 {} 缺少触发器类型配置", id);
        }
    }

    // Getters
    public String getId() { return id; }
    public String getTitle() { return title; }
    public int getTarget() { return target; }
    public JsonObject getTriggerConfig() { return triggerConfig; }
    public boolean isShared() {return isShared;}
    public MissionTrigger getTrigger() { return trigger; }
}