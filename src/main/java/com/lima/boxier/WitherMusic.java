package com.lima.boxier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.SelectMusicEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(modid = Boxier.MODID, value = Dist.CLIENT)
public final class WitherMusic {
    // The server resends the active state every second; give a little slack before stopping.
    private static final int SYNC_TIMEOUT = 60;

    private static WitherMusicInstance instance;
    private static int activeTicks;

    private WitherMusic() {
    }

    @SubscribeEvent
    public static void registerClientPayloads(RegisterClientPayloadHandlersEvent event) {
        event.register(BattleStatePayload.TYPE, WitherMusic::onBattleState);
    }

    public static void onBattleState(BattleStatePayload payload, IPayloadContext context) {
        activeTicks = payload.active() ? SYNC_TIMEOUT : 0;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (activeTicks <= 0) {
            stop();
            return;
        }

        activeTicks--;
        if (instance == null) {
            instance = new WitherMusicInstance();
            Minecraft.getInstance().getSoundManager().play(instance);
        }
    }

    // Cancels the situational (ambient) music while the battle track is playing
    @SubscribeEvent
    public static void onSelectMusic(SelectMusicEvent event) {
        if (activeTicks > 0) {
            event.overrideMusic(null);
        }
    }

    private static void stop() {
        if (instance != null) {
            instance.stop();
            Minecraft.getInstance().getSoundManager().stop(instance);
            instance = null;
        }
    }

    private static final class WitherMusicInstance extends AbstractSoundInstance implements TickableSoundInstance {
        private boolean stopped;

        private WitherMusicInstance() {
            super(Boxier.WITHER_BATTLE.get(), SoundSource.MUSIC, SoundInstance.createUnseededRandom());
            this.looping = true;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.volume = 1.0F;
        }

        @Override
        public boolean isStopped() {
            return this.stopped;
        }

        @Override
        public void tick() {
        }

        private void stop() {
            this.stopped = true;
        }
    }
}
