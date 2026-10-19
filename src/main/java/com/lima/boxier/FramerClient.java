package com.lima.boxier;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = Boxier.MODID, value = Dist.CLIENT)
public final class FramerClient {
    private FramerClient() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().getBlockState(event.getPos()).getBlock() instanceof FramerBlock
                && event.getLevel().getBlockEntity(event.getPos()) instanceof FramerBlockEntity framer) {
            Minecraft.getInstance().gui.setScreen(new FramerScreen(event.getPos(), framer.getUrl(), framer.getImageWidth(), framer.getImageHeight()));
        }
    }
}
