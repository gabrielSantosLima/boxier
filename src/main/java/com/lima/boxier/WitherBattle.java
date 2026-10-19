package com.lima.boxier;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.WeatherData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.world.clock.WorldClocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = Boxier.MODID)
public final class WitherBattle {
    public static final String EPIC_TAG = "boxier_epic_wither";

    private static final long NIGHT_TIME = 18000L;
    private static final long NIGHT_START = 13000L;
    private static final long NIGHT_END = 23000L;
    private static final int NIGHT_TRANSITION_TICKS = 200;
    private static final int STORM_TICKS = 300;
    private static final int LIGHTNING_INTERVAL = 15;
    private static final double LIGHTNING_MIN_DISTANCE = 8.0;
    private static final double LIGHTNING_MAX_DISTANCE = 32.0;
    private static final float CHARGE_TICKS = 220.0F;
    private static final int LIGHT_COLOR = 0xFF00FF;

    private static final int MAX_TORNADOS = 2;
    private static final int TORNADO_SPAWN_INTERVAL = 200;
    private static final int BATTLE_SYNC_INTERVAL = 20;

    private static final Set<ResourceKey<Level>> RAGING = ConcurrentHashMap.newKeySet();
    private static final Map<ResourceKey<Level>, NightTransition> TRANSITIONS = new ConcurrentHashMap<>();
    private static final List<Tornado> TORNADOS = new ArrayList<>();

    private WitherBattle() {
    }

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(BattleStatePayload.TYPE, BattleStatePayload.STREAM_CODEC);
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk()) {
            return;
        }
        if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof WitherBoss wither) {
            start(level, wither);
        }
    }

    @SubscribeEvent
    public static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof WitherBoss wither
                && wither.getPersistentData().getBooleanOr(EPIC_TAG, false)
                && event.getSource().is(DamageTypeTags.IS_LIGHTNING)) {
            event.setCanceled(true);
        }
    }

    public static void start(ServerLevel level, WitherBoss wither) {
        if (wither.getPersistentData().getBooleanOr(EPIC_TAG, false)) {
            return;
        }

        wither.getPersistentData().putBoolean(EPIC_TAG, true);
        wither.setPersistenceRequired();
        wither.makeInvulnerable();
        RAGING.add(level.dimension());
        beginNightTransition(level);
        PacketDistributor.sendToPlayersInDimension(level, new BattleStatePayload(true));

        level.playSound(null, wither.getX(), wither.getY(), wither.getZ(),
                SoundEvents.ENDER_DRAGON_DEATH, SoundSource.HOSTILE, 6.0F, 0.5F);
        level.playSound(null, wither.getX(), wither.getY(), wither.getZ(),
                SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 5.0F, 0.6F);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        for (ServerLevel level : server.getAllLevels()) {
            if (!RAGING.contains(level.dimension())) {
                continue;
            }

            List<WitherBoss> alive = new ArrayList<>();
            level.getEntities(EntityTypeTest.forClass(WitherBoss.class), wither -> wither.getPersistentData().getBooleanOr(EPIC_TAG, false), alive);

            if (alive.isEmpty()) {
                end(level);
                continue;
            }

            applyStorm(level);
            advanceNightTransition(level);

            long gameTime = level.getGameTime();
            if (gameTime % BATTLE_SYNC_INTERVAL == 0L) {
                PacketDistributor.sendToPlayersInDimension(level, new BattleStatePayload(true));
            }

            for (WitherBoss wither : alive) {
                tickWither(level, wither, gameTime);
            }
            tickTornados(level, alive.get(0), gameTime);
        }
    }

    private static void beginNightTransition(ServerLevel level) {
        var clock = level.registryAccess().get(WorldClocks.OVERWORLD);
        if (clock.isEmpty() || !(level.clockManager() instanceof ServerClockManager manager)) {
            return;
        }

        long current = level.getOverworldClockTime();
        long dayTime = Math.floorMod(current, 24000L);

        if (dayTime >= NIGHT_START && dayTime <= NIGHT_END) {
            manager.setPaused(clock.get(), true);
            return;
        }

        long target = current - dayTime + NIGHT_TIME;
        if (target <= current) {
            target += 24000L;
        }
        TRANSITIONS.put(level.dimension(), new NightTransition(current, target));
        manager.setPaused(clock.get(), false);
    }

    private static void advanceNightTransition(ServerLevel level) {
        NightTransition transition = TRANSITIONS.get(level.dimension());
        if (transition == null) {
            return;
        }

        var clock = level.registryAccess().get(WorldClocks.OVERWORLD);
        if (clock.isEmpty() || !(level.clockManager() instanceof ServerClockManager manager)) {
            TRANSITIONS.remove(level.dimension());
            return;
        }

        if (transition.ticks > 0) {
            float progress = 1.0F - transition.ticks / (float) NIGHT_TRANSITION_TICKS;
            long total = transition.start + (long) ((transition.target - transition.start) * progress);
            manager.setTotalTicks(clock.get(), total);
            transition.ticks--;
        } else {
            manager.setTotalTicks(clock.get(), transition.target);
            manager.setPaused(clock.get(), true);
            TRANSITIONS.remove(level.dimension());
        }
    }

    private static void applyStorm(ServerLevel level) {
        WeatherData weather = level.getWeatherData();
        weather.setClearWeatherTime(0);
        weather.setRaining(true);
        weather.setThundering(true);
        weather.setRainTime(STORM_TICKS);
        weather.setThunderTime(STORM_TICKS);
    }

    private static void tickWither(ServerLevel level, WitherBoss wither, long gameTime) {
        strikeLightning(level, wither, gameTime);

        int invulnerableTicks = wither.getInvulnerableTicks();
        if (invulnerableTicks > 0) {
            tickCharge(level, wither, invulnerableTicks);
        }
    }

    private static void strikeLightning(ServerLevel level, WitherBoss wither, long gameTime) {
        if (gameTime % LIGHTNING_INTERVAL != 0L) {
            return;
        }
        RandomSource random = level.getRandom();
        double angle = random.nextDouble() * Math.PI * 2.0;
        double distance = LIGHTNING_MIN_DISTANCE + random.nextDouble() * (LIGHTNING_MAX_DISTANCE - LIGHTNING_MIN_DISTANCE);
        BlockPos pos = wither.blockPosition().offset(Mth.floor(Math.cos(angle) * distance), 0, Mth.floor(Math.sin(angle) * distance));
        EntityTypes.LIGHTNING_BOLT.spawn(level, pos, EntitySpawnReason.EVENT);
    }

    private static void tickCharge(ServerLevel level, WitherBoss wither, int invulnerableTicks) {
        float charge = 1.0F - Mth.clamp(invulnerableTicks / CHARGE_TICKS, 0.0F, 1.0F);
        int count = 2 + Mth.floor(charge * 40.0F);
        double x = wither.getX();
        double y = wither.getY() + 1.5;
        double z = wither.getZ();

        level.sendParticles(new DustParticleOptions(LIGHT_COLOR, 1.0F + charge * 2.0F), x, y, z, count, 0.7, 1.2, 0.7, 0.03);
        level.sendParticles(ParticleTypes.END_ROD, x, y, z, count / 2, 0.9, 1.4, 0.9, 0.06);

        if (invulnerableTicks % 20 == 0) {
            level.playSound(null, x, y, z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 4.0F, 0.5F + charge * 0.5F);
        }
        if (invulnerableTicks % 40 == 0) {
            level.playSound(null, x, y, z, SoundEvents.WITHER_AMBIENT, SoundSource.HOSTILE, 3.0F, 1.0F);
        }
    }

    private static void tickTornados(ServerLevel level, WitherBoss boss, long gameTime) {
        int count = 0;
        Iterator<Tornado> iterator = TORNADOS.iterator();
        while (iterator.hasNext()) {
            Tornado tornado = iterator.next();
            if (tornado.level != level) {
                continue;
            }
            tornado.tick(boss, gameTime);
            if (tornado.dead) {
                iterator.remove();
            } else {
                count++;
            }
        }

        RandomSource random = level.getRandom();
        if (count < MAX_TORNADOS && gameTime % TORNADO_SPAWN_INTERVAL == 0L && random.nextFloat() < 0.5F) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = 10.0 + random.nextDouble() * 20.0;
            double x = boss.getX() + Math.cos(angle) * distance;
            double z = boss.getZ() + Math.sin(angle) * distance;
            TORNADOS.add(new Tornado(level, x, z, random));
        }
    }

    private static void end(ServerLevel level) {
        RAGING.remove(level.dimension());
        TRANSITIONS.remove(level.dimension());
        TORNADOS.removeIf(tornado -> tornado.level == level);
        PacketDistributor.sendToPlayersInDimension(level, new BattleStatePayload(false));

        WeatherData weather = level.getWeatherData();
        weather.setRaining(false);
        weather.setThundering(false);
        weather.setRainTime(0);
        weather.setThunderTime(0);

        level.registryAccess().get(WorldClocks.OVERWORLD).ifPresent(clock -> {
            if (level.clockManager() instanceof ServerClockManager manager) {
                manager.setPaused(clock, false);
            }
        });
    }

    private static final class NightTransition {
        private final long start;
        private final long target;
        private int ticks = NIGHT_TRANSITION_TICKS;

        private NightTransition(long start, long target) {
            this.start = start;
            this.target = target;
        }
    }

    private static final class Tornado {
        private static final double HEIGHT = 30.0;
        private static final double BASE_RADIUS = 1.5;
        private static final double TOP_RADIUS = 11.0;
        private static final double SPEED = 0.4;
        private static final double REGION = 100.0;
        private static final int ARMS = 4;
        private static final int LEVELS = 10;

        private final ServerLevel level;
        private final RandomSource random;
        private final int lifetime;
        private final double spin;

        private double x;
        private double z;
        private double targetX;
        private double targetZ;
        private int age;
        private boolean dead;

        private Tornado(ServerLevel level, double x, double z, RandomSource random) {
            this.level = level;
            this.random = random;
            this.x = x;
            this.z = z;
            this.lifetime = 600 + random.nextInt(601);
            this.spin = random.nextDouble() * Math.PI * 2.0;
            pickTarget(x, z);
        }

        private void pickTarget(double centerX, double centerZ) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = 20.0 + random.nextDouble() * (REGION - 20.0);
            this.targetX = centerX + Math.cos(angle) * distance;
            this.targetZ = centerZ + Math.sin(angle) * distance;
        }

        private void tick(WitherBoss boss, long gameTime) {
            if (boss.isRemoved() || boss.isDeadOrDying() || ++this.age > this.lifetime) {
                this.dead = true;
                return;
            }

            double bossX = boss.getX();
            double bossZ = boss.getZ();

            double dx = this.targetX - this.x;
            double dz = this.targetZ - this.z;
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance < 3.0) {
                pickTarget(bossX, bossZ);
            } else {
                this.x += dx / distance * SPEED;
                this.z += dz / distance * SPEED;
            }

            double bossDx = this.x - bossX;
            double bossDz = this.z - bossZ;
            if (bossDx * bossDx + bossDz * bossDz > REGION * REGION) {
                pickTarget(bossX, bossZ);
            }

            int baseY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(this.x), Mth.floor(this.z));
            spawnParticles(gameTime, baseY);
            applyForces(baseY);
        }

        private void spawnParticles(long gameTime, int baseY) {
            double spinAngle = this.spin + gameTime * 0.35;
            for (int arm = 0; arm < ARMS; arm++) {
                double armOffset = arm * (Math.PI * 2.0 / ARMS);
                for (int layer = 0; layer < LEVELS; layer++) {
                    double t = layer / (double) (LEVELS - 1);
                    double radius = (BASE_RADIUS + t * TOP_RADIUS) * (1.0 + 0.08 * Math.sin(gameTime * 0.3 + t * 6.0));
                    double angle = spinAngle * (1.0 - t * 0.5) + armOffset + t * 4.0;
                    double px = this.x + Math.cos(angle) * radius;
                    double pz = this.z + Math.sin(angle) * radius;
                    double py = baseY + t * HEIGHT;
                    level.sendParticles(ParticleTypes.SNOWFLAKE, px, py, pz, 1, 0.0, 0.0, 0.0, 0.0);
                }
            }
            level.sendParticles(ParticleTypes.WHITE_ASH, this.x, baseY + 0.5, this.z, 8, TOP_RADIUS * 0.5, 0.5, TOP_RADIUS * 0.5, 0.03);
            level.sendParticles(ParticleTypes.CLOUD, this.x, baseY + HEIGHT, this.z, 4, TOP_RADIUS * 0.6, 1.0, TOP_RADIUS * 0.6, 0.01);
        }

        private void applyForces(int baseY) {
            double capture = TOP_RADIUS;
            AABB region = new AABB(this.x - capture, baseY - 2.0, this.z - capture, this.x + capture, baseY + HEIGHT, this.z + capture);

            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, region)) {
                if (entity instanceof WitherBoss) {
                    continue;
                }

                double dx = entity.getX() - this.x;
                double dz = entity.getZ() - this.z;
                double distanceSq = dx * dx + dz * dz;
                if (distanceSq > capture * capture) {
                    continue;
                }

                double distance = Math.sqrt(distanceSq);
                double nx = dx / Math.max(distance, 0.001);
                double nz = dz / Math.max(distance, 0.001);
                double strength = 1.0 - distance / capture;

                double inwardX = -nx * (0.04 + 0.10 * strength);
                double inwardZ = -nz * (0.04 + 0.10 * strength);
                double swirlX = -nz * (0.20 + 0.20 * strength);
                double swirlZ = nx * (0.20 + 0.20 * strength);

                Vec3 velocity = entity.getDeltaMovement();
                double vy = Math.min(velocity.y * 0.92 + 0.10 + 0.22 * strength, 1.4);
                entity.setDeltaMovement(velocity.x * 0.72 + inwardX + swirlX, vy, velocity.z * 0.72 + inwardZ + swirlZ);
                entity.needsSync = true;
                entity.fallDistance = 0.0;
            }
        }
    }
}
