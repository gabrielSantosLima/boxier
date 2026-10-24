package com.lima.boxier;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@EventBusSubscriber(modid = Boxier.MODID)
public final class BrowserCommands {
    private BrowserCommands() {
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("boxierbrowser")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("open")
                        .then(Commands.argument("url", StringArgumentType.greedyString())
                                .executes(context -> open(context, StringArgumentType.getString(context, "url")))))
                .then(Commands.literal("close")
                        .executes(context -> close(context)))
                .then(Commands.literal("openat")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(context -> openAt(context)))));
    }

    private static int open(CommandContext<CommandSourceStack> context, String url) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal("Starting browser..."), false);

        BrowserSessionManager.openTest(url, 1280, 1280).whenComplete((session, throwable) -> {
            source.getServer().executeIfPossible(() -> {
                if (throwable != null) {
                    source.sendFailure(Component.literal("Browser failed: " + throwable.getMessage()));
                    return;
                }
                source.sendSuccess(() -> Component.literal("Browser opened with " + session.getFrameCount()
                        + " frames; title=" + session.getTitle()), false);
            });
        });
        return 1;
    }

    private static int openAt(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
            if (player.level() instanceof ServerLevel level) {
                BrowserSessionManager.requestControl(level, pos, player);
                context.getSource().sendSuccess(() -> Component.literal("Requested browser control"), false);
                return 1;
            }
        } catch (Exception exception) {
            context.getSource().sendFailure(Component.literal("Failed: " + exception.getMessage()));
        }
        return 0;
    }

    private static int close(CommandContext<CommandSourceStack> context) {
        BrowserSessionManager.closeTest();
        context.getSource().sendSuccess(() -> Component.literal("Browser closed"), false);
        return 1;
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        String url = System.getProperty("boxier.browser.testUrl");
        if (url == null || url.isBlank()) {
            return;
        }
        Boxier.LOGGER.info("Opening test browser for {}", url);
        BrowserSessionManager.openTest(url, 1280, 1280).whenComplete((session, throwable) -> {
            if (throwable != null) {
                Boxier.LOGGER.error("Test browser failed", throwable);
            } else {
                Boxier.LOGGER.info("Test browser opened: frames={} title={}", session.getFrameCount(), session.getTitle());
            }
        });
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        BrowserSessionManager.shutdown();
        BrowserRuntime.shutdown();
    }
}
