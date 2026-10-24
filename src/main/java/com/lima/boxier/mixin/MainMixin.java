package com.lima.boxier.mixin;

import net.minecraft.client.main.Main;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Main.class)
public class MainMixin {
    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void boxier$allowAwt(CallbackInfo callback) {
        System.setProperty("java.awt.headless", "false");
    }
}
