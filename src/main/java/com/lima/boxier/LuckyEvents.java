package com.lima.boxier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

public final class LuckyEvents {
    private static final float REWARD_CHANCE = 0.50F;

    private static final EntityType<?>[] MONSTERS = {
            EntityTypes.ZOMBIE,
            EntityTypes.SKELETON,
            EntityTypes.CREEPER,
            EntityTypes.SPIDER
    };

    private static final EntityType<?>[] FRIENDLY_MOBS = {
            EntityTypes.IRON_GOLEM,
            EntityTypes.SNOW_GOLEM,
            EntityTypes.WOLF,
            EntityTypes.CAT,
            EntityTypes.VILLAGER
    };

    private static final Holder<MobEffect>[] GOOD_EFFECTS = new Holder[] {
            MobEffects.SPEED,
            MobEffects.STRENGTH,
            MobEffects.REGENERATION,
            MobEffects.HASTE,
            MobEffects.NIGHT_VISION,
            MobEffects.FIRE_RESISTANCE,
            MobEffects.ABSORPTION,
            MobEffects.JUMP_BOOST
    };

    private static final Holder<MobEffect>[] BAD_EFFECTS = new Holder[] {
            MobEffects.POISON,
            MobEffects.WITHER,
            MobEffects.BLINDNESS,
            MobEffects.WEAKNESS,
            MobEffects.NAUSEA
    };

    private static final Item[] LOOT = {
            Items.DIAMOND,
            Items.IRON_INGOT,
            Items.GOLD_INGOT,
            Items.EMERALD,
            Items.GOLDEN_APPLE,
            Items.COOKED_BEEF,
            Items.EXPERIENCE_BOTTLE,
            Items.ARROW
    };

    private static final Item[] GEAR = {
            Items.NETHERITE_SWORD,
            Items.DIAMOND_SWORD,
            Items.IRON_SWORD,
            Items.BOW,
            Items.DIAMOND_PICKAXE,
            Items.DIAMOND_AXE,
            Items.NETHERITE_HELMET,
            Items.DIAMOND_CHESTPLATE,
            Items.DIAMOND_LEGGINGS,
            Items.DIAMOND_BOOTS
    };

    private static final String[] MEMES = {
            "Rapaz, to passado",
            "E uma cilada, Bino",
            "Bora, Bill",
            "Calma, calabreso",
            "Que papinho hein",
            "Pega a visao",
            "Poe na conta do Chico Bento",
            "Quem quer dinheiro?",
            "Ce ta louco, mermao",
            "Ai e sacanagem",
            "Solta a batata",
            "Numero errado, otario",
            "Ta osso",
            "Vai dar nao",
            "Toma essa"
    };

    private static final List<LuckyEvent> EVENTS = List.of(
            new LuckyEvent("diamonds", LuckyCategory.REWARD, 10, LuckyEvents::diamonds),
            new LuckyEvent("random_loot", LuckyCategory.REWARD, 9, LuckyEvents::randomLoot),
            new LuckyEvent("experience", LuckyCategory.REWARD, 6, LuckyEvents::experience),
            new LuckyEvent("buff", LuckyCategory.REWARD, 9, LuckyEvents::buff),
            new LuckyEvent("friendly_mobs", LuckyCategory.REWARD, 6, LuckyEvents::friendlyMobs),
            new LuckyEvent("loot_chest", LuckyCategory.REWARD, 5, LuckyEvents::lootChest),
            new LuckyEvent("colored_sheep", LuckyCategory.REWARD, 8, LuckyEvents::coloredSheep),
            new LuckyEvent("enchanted_gear", LuckyCategory.REWARD, 5, LuckyEvents::enchantedGear),
            new LuckyEvent("meme_sign", LuckyCategory.REWARD, 8, LuckyEvents::memeSign),
            new LuckyEvent("fireworks", LuckyCategory.REWARD, 4, LuckyEvents::fireworks),
            new LuckyEvent("nested_lucky_blocks", LuckyCategory.REWARD, 3, LuckyEvents::nestedLuckyBlocks),
            new LuckyEvent("tree", LuckyCategory.REWARD, 4, LuckyEvents::tree),

            new LuckyEvent("monsters", LuckyCategory.TRAP, 10, LuckyEvents::monsters),
            new LuckyEvent("anvil", LuckyCategory.TRAP, 8, LuckyEvents::anvil),
            new LuckyEvent("tnt", LuckyCategory.TRAP, 7, LuckyEvents::tnt),
            new LuckyEvent("fire", LuckyCategory.TRAP, 5, LuckyEvents::fire),
            new LuckyEvent("lava", LuckyCategory.TRAP, 5, LuckyEvents::lava),
            new LuckyEvent("lightning", LuckyCategory.TRAP, 4, LuckyEvents::lightning),
            new LuckyEvent("cobwebs", LuckyCategory.TRAP, 4, LuckyEvents::cobwebs),
            new LuckyEvent("negative_buff", LuckyCategory.TRAP, 8, LuckyEvents::negativeBuff),
            new LuckyEvent("launch", LuckyCategory.TRAP, 4, LuckyEvents::launch),
            new LuckyEvent("silverfish", LuckyCategory.TRAP, 5, LuckyEvents::silverfish),
            new LuckyEvent("baby_zombie_netherite", LuckyCategory.TRAP, 3, LuckyEvents::babyZombieNetherite),
            new LuckyEvent("wardens", LuckyCategory.TRAP, 1, LuckyEvents::wardens),
            new LuckyEvent("giant", LuckyCategory.TRAP, 1, LuckyEvents::giant),
            new LuckyEvent("wither", LuckyCategory.TRAP, 1, LuckyEvents::wither),
            new LuckyEvent("lava_igloo", LuckyCategory.TRAP, 2, LuckyEvents::lavaIgloo)
    );

    private LuckyEvents() {
    }

    public static LuckyEvent roll(RandomSource random) {
        LuckyCategory category = random.nextFloat() < REWARD_CHANCE ? LuckyCategory.REWARD : LuckyCategory.TRAP;
        List<LuckyEvent> pool = new ArrayList<>();
        int total = 0;
        for (LuckyEvent event : EVENTS) {
            if (event.category() == category) {
                pool.add(event);
                total += event.weight();
            }
        }
        int pick = random.nextInt(total);
        for (LuckyEvent event : pool) {
            pick -= event.weight();
            if (pick < 0) {
                return event;
            }
        }
        return pool.get(pool.size() - 1);
    }

    private static void diamonds(ServerLevel level, ServerPlayer player, BlockPos pos, RandomSource random) {
        int amount = 16 + random.nextInt(49);
        while (amount > 0) {
            int stack = Math.min(amount, 64);
            LuckyEffects.drop(level, pos, new ItemStack(Items.DIAMOND, stack));
            amount -= stack;
        }
    }

    private static void randomLoot(ServerLevel level, ServerPlayer player, BlockPos pos, RandomSource random) {
        Item item = LOOT[random.nextInt(LOOT.length)];
        int amount;
        if (item == Items.ARROW) {
            amount = 8 + random.nextInt(25);
        } else if (item == Items.GOLDEN_APPLE) {
            amount = 1 + random.nextInt(3);
        } else {
            amount = 1 + random.nextInt(4);
        }
        LuckyEffects.drop(level, pos, new ItemStack(item, amount));
    }

    private static void experience(ServerLevel level, ServerPlayer player, BlockPos pos, RandomSource random) {
        player.giveExperienceLevels(5 + random.nextInt(16));
    }

    private static void buff(ServerLevel level, ServerPlayer player, BlockPos pos, RandomSource random) {
        Holder<MobEffect> effect = GOOD_EFFECTS[random.nextInt(GOOD_EFFECTS.length)];
        player.addEffect(new MobEffectInstance(effect, (10 + random.nextInt(31)) * 20, random.nextInt(2)));
    }

    private static void friendlyMobs(ServerLevel level, ServerPlayer player, BlockPos pos, RandomSource random) {
        EntityType<?> type = FRIENDLY_MOBS[random.nextInt(FRIENDLY_MOBS.length)];
        LuckyEffects.spawnMany(level, pos, random, type, 1 + random.nextInt(3), 3.0);
    }

    private static void lootChest(ServerLevel level, ServerPlayer player, BlockPos pos, RandomSource random) {
        BlockPos spot = LuckyEffects.findGroundSpot(level, player.blockPosition(), random, 2);
        if (spot == null) {
            return;
        }
        LuckyEffects.place(level, spot, Blocks.CHEST.defaultBlockState());
        if (level.getBlockEntity(spot) instanceof RandomizableContainerBlockEntity chest) {
            chest.setLootTable(BuiltInLootTables.SIMPLE_DUNGEON);
            chest.setLootTableSeed(random.nextLong());
        }
    }

    private static void coloredSheep(ServerLevel level, ServerPlayer player, BlockPos pos, RandomSource random) {
        int count = 5 + random.nextInt(6);
        for (int i = 0; i < count; i++) {
            Entity entity = LuckyEffects.spawnNear(level, pos, random, EntityTypes.SHEEP, 4.0);
            if (entity instanceof Sheep sheep) {
                sheep.setColor(DyeColor.byId(random.nextInt(16)));
            }
        }
    }

    private static void enchantedGear(ServerLevel level, ServerPlayer player, BlockPos pos, RandomSource random) {
        ItemStack stack = new ItemStack(GEAR[random.nextInt(GEAR.length)]);
        EnchantmentHelper.enchantItem(random, stack, 20 + random.nextInt(20), level.registryAccess(), Optional.empty());
        LuckyEffects.drop(level, pos, stack);
    }

    private static void memeSign(ServerLevel level, ServerPlayer player, BlockPos pos, RandomSource random) {
        BlockPos spot = LuckyEffects.findGroundSpot(level, player.blockPosition(), random, 2);
        if (spot == null) {
            return;
        }
        LuckyEffects.place(level, spot, Blocks.OAK_SIGN.defaultBlockState());
        if (level.getBlockEntity(spot) instanceof SignBlockEntity sign) {
            List<Component> lines = wrap(MEMES[random.nextInt(MEMES.length)], 15);
            SignText text = new SignText(lines, lines, DyeColor.BLACK, false);
            sign.setText(text, SignTextSlot.FRONT);
            sign.setText(text, SignTextSlot.BACK);
            sign.setChanged();
        }
    }

    private static void fireworks(ServerLevel level, ServerPlayer player, BlockPos pos, RandomSource random) {
        for (int i = 0; i < 3; i++) {
            double x = player.getX() + (random.nextDouble() - 0.5) * 2.0;
            double y = player.getY() + 1.0;
            double z = player.getZ() + (random.nextDouble() - 0.5) * 2.0;
            level.addFreshEntity(new FireworkRocketEntity(level, x, y, z, new ItemStack(Items.FIREWORK_ROCKET)));
        }
    }

    private static void nestedLuckyBlocks(ServerLevel level, ServerPlayer player, BlockPos pos, RandomSource random) {
        int count = 1 + random.nextInt(4);
        LuckyEffects.drop(level, pos, new ItemStack(Boxier.LUCKY_BLOCK_ITEM.get(), count));
    }

    private static void tree(ServerLevel level, ServerPlayer player, BlockPos pos, RandomSource random) {
        BlockPos base = LuckyEffects.findGroundSpot(level, player.blockPosition(), random, 3);
        if (base == null) {
            return;
        }
        for (int i = 0; i < 5; i++) {
            LuckyEffects.place(level, base.above(i), Blocks.OAK_LOG.defaultBlockState());
        }
        for (int dy = 3; dy <= 5; dy++) {
            int radius = dy == 5 ? 1 : 2;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dz == 0 && dy < 5) {
                        continue;
                    }
                    LuckyEffects.placeIfAir(level, base.above(dy).offset(dx, 0, dz), Blocks.OAK_LEAVES.defaultBlockState());
                }
            }
        }
    }

    private static void monsters(ServerLevel level, ServerPlayer player, BlockPos pos, RandomSource random) {
        int count = 3 + random.nextInt(4);
        for (int i = 0; i < count; i++) {
            EntityType<?> type = MONSTERS[random.nextInt(MONSTERS.length)];
            LuckyEffects.spawnNear(level, pos, random, type, 2.0);
        }
    }

    private static void anvil(ServerLevel level, ServerPlayer player, BlockPos pos, RandomSource random) {
        FallingBlockEntity anvil = FallingBlockEntity.fall(level, player.blockPosition().above(12), Blocks.ANVIL.defaultBlockState());
        anvil.setHurtsEntities(2.0F, 40);
    }

    private static void tnt(ServerLevel level, ServerPlayer player, BlockPos pos, RandomSource random) {
        double x = player.getX() + (random.nextDouble() - 0.5) * 2.0;
        double y = player.getY();
        double z = player.getZ() + (random.nextDouble() - 0.5) * 2.0;
        PrimedTnt tnt = new PrimedTnt(level, x, y, z, null);
        tnt.setFuse(30 + random.nextInt(30));
        level.addFreshEntity(tnt);
    }

    private static void fire(ServerLevel level, ServerPlayer player, BlockPos pos, RandomSource random) {
        for (int i = 0; i < 6; i++) {
            BlockPos spot = LuckyEffects.findGroundSpot(level, player.blockPosition(), random, 3);
            if (spot != null) {
                LuckyEffects.place(level, spot, Blocks.FIRE.defaultBlockState());
            }
        }
    }

    private static void lava(ServerLevel level, ServerPlayer player, BlockPos pos, RandomSource random) {
        for (int i = 0; i < 4; i++) {
            BlockPos spot = LuckyEffects.findGroundSpot(level, player.blockPosition(), random, 3);
            if (spot != null) {
                LuckyEffects.place(level, spot, Blocks.LAVA.defaultBlockState());
            }
        }
    }

    private static void lightning(ServerLevel level, ServerPlayer player, BlockPos pos, RandomSource random) {
        Entity entity = EntityTypes.LIGHTNING_BOLT.spawn(level, player.blockPosition(), net.minecraft.world.entity.EntitySpawnReason.EVENT);
        if (entity instanceof LightningBolt bolt) {
            bolt.setCause(player);
        }
    }

    private static void cobwebs(ServerLevel level, ServerPlayer player, BlockPos pos, RandomSource random) {
        for (int i = 0; i < 8; i++) {
            BlockPos spot = LuckyEffects.findGroundSpot(level, player.blockPosition(), random, 3);
            if (spot != null) {
                LuckyEffects.place(level, spot, Blocks.COBWEB.defaultBlockState());
            }
        }
    }

    private static void negativeBuff(ServerLevel level, ServerPlayer player, BlockPos pos, RandomSource random) {
        Holder<MobEffect> effect = BAD_EFFECTS[random.nextInt(BAD_EFFECTS.length)];
        player.addEffect(new MobEffectInstance(effect, (8 + random.nextInt(17)) * 20, random.nextInt(2)));
    }

    private static void launch(ServerLevel level, ServerPlayer player, BlockPos pos, RandomSource random) {
        player.push((random.nextDouble() - 0.5) * 0.8, 2.2, (random.nextDouble() - 0.5) * 0.8);
    }

    private static void silverfish(ServerLevel level, ServerPlayer player, BlockPos pos, RandomSource random) {
        LuckyEffects.spawnMany(level, pos, random, EntityTypes.SILVERFISH, 5 + random.nextInt(6), 2.0);
    }

    private static void babyZombieNetherite(ServerLevel level, ServerPlayer player, BlockPos pos, RandomSource random) {
        Entity entity = LuckyEffects.spawnNear(level, pos, random, EntityTypes.ZOMBIE, 2.0);
        if (!(entity instanceof Zombie zombie)) {
            return;
        }
        zombie.setBaby(true);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
        zombie.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
        zombie.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.NETHERITE_LEGGINGS));
        zombie.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.NETHERITE_BOOTS));
        zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
        zombie.setPersistenceRequired();
    }

    private static void wardens(ServerLevel level, ServerPlayer player, BlockPos pos, RandomSource random) {
        for (int i = 0; i < 5; i++) {
            Entity entity = LuckyEffects.spawnNear(level, pos, random, EntityTypes.WARDEN, 3.0);
            if (entity instanceof Warden warden) {
                warden.setHealth(1.0F);
                warden.setPersistenceRequired();
            }
        }
    }

    private static void giant(ServerLevel level, ServerPlayer player, BlockPos pos, RandomSource random) {
        Entity entity = LuckyEffects.spawnNear(level, pos, random, EntityTypes.GIANT, 3.0);
        if (entity instanceof Giant giant) {
            giant.setPersistenceRequired();
        }
    }

    private static void wither(ServerLevel level, ServerPlayer player, BlockPos pos, RandomSource random) {
        LuckyEffects.spawnNear(level, pos, random, EntityTypes.WITHER, 3.0);
    }

    private static void lavaIgloo(ServerLevel level, ServerPlayer player, BlockPos pos, RandomSource random) {
        BlockPos center = player.blockPosition();
        int radius = 2;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                boolean edge = Math.abs(dx) == radius || Math.abs(dz) == radius;
                BlockPos base = center.offset(dx, 0, dz);
                for (int dy = 0; dy <= 2; dy++) {
                    if (edge) {
                        LuckyEffects.place(level, base.above(dy), dy == 2 ? Blocks.ICE.defaultBlockState() : Blocks.SNOW_BLOCK.defaultBlockState());
                    } else {
                        LuckyEffects.place(level, base.above(dy), Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                LuckyEffects.place(level, center.offset(dx, 3, dz), Blocks.SNOW_BLOCK.defaultBlockState());
            }
        }
        int lavaRadius = radius + 1;
        for (int dx = -lavaRadius; dx <= lavaRadius; dx++) {
            for (int dz = -lavaRadius; dz <= lavaRadius; dz++) {
                if (Math.abs(dx) != lavaRadius && Math.abs(dz) != lavaRadius) {
                    continue;
                }
                BlockPos base = center.offset(dx, 0, dz);
                LuckyEffects.place(level, base.below(), Blocks.SNOW_BLOCK.defaultBlockState());
                LuckyEffects.place(level, base, Blocks.LAVA.defaultBlockState());
            }
        }
    }

    private static List<Component> wrap(String text, int maxLength) {
        List<Component> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split(" ")) {
            if (current.length() > 0 && current.length() + 1 + word.length() > maxLength) {
                lines.add(Component.literal(current.toString()));
                current.setLength(0);
            }
            if (current.length() > 0) {
                current.append(' ');
            }
            current.append(word);
        }
        if (current.length() > 0) {
            lines.add(Component.literal(current.toString()));
        }
        while (lines.size() < 4) {
            lines.add(Component.empty());
        }
        if (lines.size() > 4) {
            lines = new ArrayList<>(lines.subList(0, 4));
        }
        return lines;
    }
}
