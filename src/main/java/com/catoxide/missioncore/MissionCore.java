package com.catoxide.missioncore;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
// 添加以下导入
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod("missioncore")
public class MissionCore {
    public static final String MODID = "missioncore";
    public static final Logger LOGGER = LogManager.getLogger();

    public MissionCore() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);
        modEventBus.addListener(this::registerOverlays);

        MinecraftForge.EVENT_BUS.register(this);

        // 注册任务事件监听器
        MissionEventListener missionEventListener = new MissionEventListener();
        MinecraftForge.EVENT_BUS.register(missionEventListener);

        MinecraftForge.EVENT_BUS.addGenericListener(Entity.class, PlayerMissionData::attach);
        MinecraftForge.EVENT_BUS.addListener(PlayerMissionData::onPlayerClone);
        MinecraftForge.EVENT_BUS.addListener(PlayerMissionData::onPlayerSave);
        MinecraftForge.EVENT_BUS.addListener(this::addReloadListeners);
    }

    // 新增覆盖层注册方法
    @OnlyIn(Dist.CLIENT)
    public void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("mission_overlay", new MissionOverlay());
        LOGGER.info("任务覆盖层已注册");
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer) {
            ServerPlayer player = (ServerPlayer) event.getEntity();
            MissionCore.LOGGER.info("玩家 {} 登录，同步任务数据", player.getName().getString());

            if (player.level() instanceof ServerLevel) {
                ServerLevel level = (ServerLevel) player.level();

                // 同步共享任务数据
                WorldSharedMissionData sharedData = WorldSharedMissionData.get(level);
                MissionCore.LOGGER.info("同步 {} 个共享任务给玩家 {}", sharedData.sharedMissions.size(), player.getName().getString());
                sharedData.syncToAllPlayers(level);
            }
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            if (event.getServer().getTickCount() % 100 == 0) {
                for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
                    // 使用正确的获取世界方法
                    WorldSharedMissionData sharedData = WorldSharedMissionData.get((ServerLevel) player.level());
                }
            }
        }
    }

    @SubscribeEvent
    public void addReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new MissionLoader());
        LOGGER.info("Registered mission loader");
    }

    @SubscribeEvent
    public void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            NetworkHandler.register();
            System.out.println("MissionCore 通用设置完成");
            validateResourceExistence();
        });
    }

    @SubscribeEvent
    public void clientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            System.out.println("MissionCore 客户端设置完成");
            // 确保配置类初始化（如果尚未存在）
            //HudConfig.setup();
        });
    }

    @SubscribeEvent
    public void registerCommands(RegisterCommandsEvent event) {
        MissionCommand.register(event.getDispatcher());
        System.out.println("注册任务命令");
    }

    private void validateResourceExistence() {
        String[] validPaths = {
                "assets/missioncore/missions/global_missions.json",
                "data/missioncore/missions/global_missions.json"
        };

        for (String path : validPaths) {
            if (getClass().getResource("/" + path) != null) {
                LOGGER.info("资源验证成功: {}", path);
                return;
            }
        }
        LOGGER.error("资源验证失败: 所有路径都未找到文件");
    }
    @SubscribeEvent
    public void onBlockStateChangeDebug(BlockEvent.NeighborNotifyEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);

        MissionCore.LOGGER.debug("方块状态变化事件: 位置={}, 方块={}, 状态={}",
                pos, ForgeRegistries.BLOCKS.getKey(state.getBlock()), state.toString());

        // 记录所有属性变化
        state.getProperties().forEach(property -> {
            MissionCore.LOGGER.debug("  属性 {} = {}", property.getName(), state.getValue(property));
        });
    }
}