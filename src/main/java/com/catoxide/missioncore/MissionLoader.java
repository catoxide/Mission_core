package com.catoxide.missioncore;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

public class MissionLoader extends SimplePreparableReloadListener<Map<ResourceLocation, JsonObject>> {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().create();

    // 使用新的 ResourceLocation 构造方式
    private static final ResourceLocation MISSION_FILE = ResourceLocation.parse("missioncore:missions/global_missions.json");

    @Override
    protected Map<ResourceLocation, JsonObject> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, JsonObject> missions = new HashMap<>();
        ResourceLocation[] paths = {
                MISSION_FILE,
                ResourceLocation.parse("missioncore:global_missions.json")
        };

        for (ResourceLocation path : paths) {
            try {
                Optional<Resource> resourceOpt = manager.getResource(path);
                if (resourceOpt.isPresent()) {
                    try (InputStream stream = resourceOpt.get().open()) {
                        JsonElement jsonElement = GSON.fromJson(new InputStreamReader(stream), JsonElement.class);
                        JsonObject wrapper = new JsonObject();

                        if (jsonElement.isJsonArray()) {
                            wrapper.add("missions", jsonElement.getAsJsonArray());
                        } else {
                            wrapper = jsonElement.getAsJsonObject();
                        }

                        missions.put(path, wrapper);
                        LOGGER.info("成功加载任务文件: {}", path);
                        return missions;
                    }
                }
            } catch (Exception e) {
                LOGGER.error("加载任务文件失败: {}", path, e);
            }
        }

        // 所有路径都失败时尝试类路径
        tryLoadFromClasspath(missions);
        return missions;
    }

    // 修改类路径加载方法
    private void tryLoadFromClasspath(Map<ResourceLocation, JsonObject> missions) {
        String classpathPath = "/assets/missioncore/missions/global_missions.json";
        try (InputStream stream = getClass().getResourceAsStream(classpathPath)) {
            if (stream != null) {
                JsonElement jsonElement = GSON.fromJson(new InputStreamReader(stream), JsonElement.class);
                JsonObject wrapper = new JsonObject();

                if (jsonElement.isJsonArray()) {
                    wrapper.add("missions", jsonElement.getAsJsonArray());
                } else {
                    wrapper = jsonElement.getAsJsonObject();
                }

                missions.put(MISSION_FILE, wrapper);
                LOGGER.info("成功从类路径加载任务文件");
            }
        } catch (Exception e) {
            LOGGER.error("类路径加载失败", e);
        }
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonObject> missions, ResourceManager manager, ProfilerFiller profiler) {
        MissionRegistry.clear();

        for (Map.Entry<ResourceLocation, JsonObject> entry : missions.entrySet()) {
            try {
                JsonObject json = entry.getValue();

                // 检查是否是包装的数组
                if (json.has("missions") && json.get("missions").isJsonArray()) {
                    JsonArray missionsArray = json.getAsJsonArray("missions");
                    for (var element : missionsArray) {
                        if (element.isJsonObject()) {
                            registerMission(element.getAsJsonObject());
                        }
                    }
                }
                // 处理单个任务对象
                else if (json.has("id")) {
                    registerMission(json);
                }
            } catch (Exception e) {
                LOGGER.error("Invalid mission format in {}", entry.getKey(), e);
            }
        }
        LOGGER.info("Loaded {} missions", MissionRegistry.getMissionCount());
    }

    private void registerMission(JsonObject json) {
        try {
            boolean isShared = json.has("shared") && json.get("shared").getAsBoolean();
            MissionDefinition def = new MissionDefinition(
                    json.get("id").getAsString(),
                    json.get("title").getAsString(),
                    json.get("target").getAsInt(),
                    json.get("trigger").getAsJsonObject(), // 确保有 trigger 字段
                    isShared
            );

            MissionRegistry.registerMission(def);
            LOGGER.info("注册任务: {}", def.getId());

            // 验证触发器配置
            if (def.getTrigger() == null) {
                LOGGER.warn("任务 {} 的触发器配置无效或类型未知", def.getId());
            } else {
                LOGGER.info("任务 {} 触发器配置成功", def.getId());
            }
        } catch (Exception e) {
            LOGGER.error("创建任务定义失败", e);
        }
    }
}