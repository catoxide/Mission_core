package com.catoxide.missioncore.trigger;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.registries.ForgeRegistries;

public class EntityKillTrigger implements MissionTrigger {
    private EntityType<?> requiredEntity;

    @Override
    public MissionTrigger createNewInstance() {
        return new EntityKillTrigger();
    }

    @Override
    public boolean shouldTrigger(Player player, Event event) {
        if (!(event instanceof LivingDeathEvent deathEvent)) return false;
        return deathEvent.getEntity().getType() == requiredEntity;
    }

    @Override
    public void configure(JsonObject config) {
        if (!config.has("entity")) {
            throw new IllegalArgumentException("EntityKillTrigger 需要 'entity' 配置");
        }

        String entityId = config.get("entity").getAsString();
        requiredEntity = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(entityId));

        if (requiredEntity == null) {
            throw new IllegalArgumentException("未知的实体ID: " + entityId);
        }
    }
}