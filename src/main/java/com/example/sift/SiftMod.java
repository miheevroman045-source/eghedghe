package com.example.sift;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HuskEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootTable;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.Hand;
import net.minecraft.util.Rarity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Sift for Fabric 1.21.1.
 * Entirely server-side logic: no client-only classes or client install requirement.
 * Static dimension registry and worldgen are provided by bundled JSON datapack files.
 */
public final class SiftMod implements ModInitializer {
    public static final String MOD_ID = "sift";
    public static final RegistryKey<World> SIFT_WORLD =
            RegistryKey.of(RegistryKeys.WORLD, Identifier.of(MOD_ID, "sift"));

    private static final int RETURN_PORTAL_Y = 201;
    private static final int RETURN_PORTAL_X = 0;
    private static final int RETURN_PORTAL_Z = 0;
    private static final String RETURN_PREFIX = "sift_return_";
    private static final String HUNTER_TAG = "sift_stalker";
    private static final Map<UUID, Integer> PORTAL_COOLDOWN = new HashMap<>();
    private static final DustParticleEffect ECHO_DUST =
            new DustParticleEffect(new Vector3f(0.26f, 0.83f, 0.94f), 1.1f);

    public static final Block RIFT_STONE = Registry.register(Registries.BLOCK, id("rift_stone"),
            new Block(AbstractBlock.Settings.copy(Blocks.DEEPSLATE).strength(4.0f, 12.0f)));
    public static final Block PORTAL = Registry.register(Registries.BLOCK, id("portal"),
            new Block(AbstractBlock.Settings.create().noCollision()
                    .luminance(state -> 11).strength(50.0f, 1200.0f).dropsNothing()));
    public static final Block ECHO_CRYSTAL = Registry.register(Registries.BLOCK, id("echo_crystal"),
            new Block(AbstractBlock.Settings.copy(Blocks.AMETHYST_BLOCK)
                    .luminance(state -> 10).strength(2.0f, 6.0f)));
    public static final Item ECHO_KEY = Registry.register(Registries.ITEM, id("echo_key"),
            new Item(new Item.Settings().maxCount(16).rarity(Rarity.RARE)));

    public static Identifier id(String name) {
        return Identifier.of(MOD_ID, name);
    }

    private static void registerBlockItem(String name, Block block) {
        Registry.register(Registries.ITEM, id(name), new BlockItem(block, new Item.Settings()));
    }

    @Override
    public void onInitialize() {
        SiftExpansion.init();
        registerBlockItem("rift_stone", RIFT_STONE);
        registerBlockItem("echo_crystal", ECHO_CRYSTAL);
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS)
                .register(entries -> entries.add(ECHO_KEY));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.BUILDING_BLOCKS)
                .register(entries -> {
                    entries.add(RIFT_STONE);
                    entries.add(ECHO_CRYSTAL);
                });

        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (hand != Hand.MAIN_HAND || player.isSpectator()) return ActionResult.PASS;
            BlockPos clicked = hit.getBlockPos();
            if (world.getRegistryKey().equals(SIFT_WORLD)) {
                if (world.getBlockState(clicked).isOf(PORTAL)) return ActionResult.SUCCESS;
                return ActionResult.PASS;
            }
            if (!world.getRegistryKey().equals(World.OVERWORLD)) return ActionResult.PASS;
            if (!world.getBlockState(clicked).isOf(Blocks.REINFORCED_DEEPSLATE)) return ActionResult.PASS;
            ItemStack held = player.getStackInHand(hand);
            if (!(held.isOf(ECHO_KEY) || held.isOf(Items.ECHO_SHARD))) return ActionResult.PASS;
            if (world.isClient()) return ActionResult.SUCCESS;
            if (!(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;

            // Enforce the Ancient City reinforced-deepslate arch, not a random placed block.
            if (clicked.getY() > -10 || !nearAncientArch((ServerWorld) world, clicked)) {
                serverPlayer.sendMessage(Text.literal("§7Ключ работает только у портала Древнего города."), true);
                return ActionResult.SUCCESS;
            }
            if (!player.isCreative()) held.decrement(1);
            rememberReturn(serverPlayer);
            buildAncientGate((ServerWorld) world, clicked, hit.getSide(), serverPlayer);
            serverPlayer.sendMessage(Text.literal("§bПОРТАЛ СИФТА ПРОБУЖДЁН! §fВойди в светящийся проход."), false);
            return ActionResult.SUCCESS;
        });

        ServerTickEvents.END_SERVER_TICK.register(SiftMod::tick);

        CommandRegistrationCallback.EVENT.register((dispatcher, access, env) -> {
            dispatcher.register(CommandManager.literal("sift")
                .then(CommandManager.literal("help").executes(ctx -> {
                    ctx.getSource().sendFeedback(() -> Text.literal(
                            "§bСИФТ: найди арку из укреплённого глубинного сланца в Древнем городе. " +
                            "ПКМ эховым осколком или ключом — открой проход. Войди в портал; вернись через него."), false);
                    return 1;
                }))
                .then(CommandManager.literal("kit")
                    .requires(source -> source.hasPermissionLevel(2))
                    .executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                        player.giveItemStack(new ItemStack(ECHO_KEY));
                        player.giveItemStack(new ItemStack(ECHO_CRYSTAL, 8));
                        return 1;
                    }))
                .then(CommandManager.literal("gear")
                    .requires(source -> source.hasPermissionLevel(2))
                    .executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                        player.giveItemStack(new ItemStack(SiftExpansion.SCULKITE_SWORD));
                        player.giveItemStack(new ItemStack(SiftExpansion.ECHO_BLADE));
                        player.giveItemStack(new ItemStack(SiftExpansion.SCULKITE_PICKAXE));
                        player.giveItemStack(new ItemStack(SiftExpansion.SCULKITE_AXE));
                        player.giveItemStack(new ItemStack(SiftExpansion.SCULKITE_SHOVEL));
                        player.giveItemStack(new ItemStack(SiftExpansion.SCULKITE_HOE));
                        player.giveItemStack(new ItemStack(SiftExpansion.SCULKITE_HELMET));
                        player.giveItemStack(new ItemStack(SiftExpansion.SCULKITE_CHESTPLATE));
                        player.giveItemStack(new ItemStack(SiftExpansion.SCULKITE_LEGGINGS));
                        player.giveItemStack(new ItemStack(SiftExpansion.SCULKITE_BOOTS));
                        player.giveItemStack(new ItemStack(SiftExpansion.SCULKITE_INGOT, 32));
                        player.giveItemStack(new ItemStack(SiftExpansion.ECHO_GEM, 16));
                        player.sendMessage(Text.literal("§bВыдано скалкитовое снаряжение Сифта."), false);
                        return 1;
                    }))
                .then(CommandManager.literal("visit")
                    .requires(source -> source.hasPermissionLevel(2))
                    .executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                        if (player.getServerWorld().getRegistryKey().equals(SIFT_WORLD)) {
                            returnReturn(player);
                        } else {
                            rememberReturn(player);
                            enterSift(player);
                        }
                        return 1;
                    })));
        });
    }

    private static boolean nearAncientArch(ServerWorld world, BlockPos pos) {
        int reinforced = 0;
        for (int dx = -6; dx <= 6; dx++) {
            for (int dy = -5; dy <= 5; dy++) {
                for (int dz = -6; dz <= 6; dz++) {
                    if (world.getBlockState(pos.add(dx, dy, dz)).isOf(Blocks.REINFORCED_DEEPSLATE)) {
                        if (++reinforced >= 12) return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Attach the gateway to the Ancient City arch. The portal has its own reinforced
     * frame and can be entered physically. No special nether portal behaviour is used.
     */
    private static void buildAncientGate(ServerWorld world, BlockPos clicked, Direction normal, ServerPlayerEntity player) {
        if (!normal.getAxis().isHorizontal()) normal = player.getHorizontalFacing().getOpposite();
        Direction width = normal.rotateYClockwise();
        BlockPos bottomLeft = clicked.offset(normal, 2).offset(width, -1).down(1);
        buildGate(world, bottomLeft, width, Blocks.REINFORCED_DEEPSLATE);
        world.playSound(null, bottomLeft, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 1.5f, 0.55f);
        world.spawnParticles(ParticleTypes.PORTAL, bottomLeft.getX(), bottomLeft.getY() + 2.0, bottomLeft.getZ(),
                100, 2.0, 2.0, 1.0, 0.15);
    }

    private static void buildGate(ServerWorld world, BlockPos bottomLeft, Direction width, Block frame) {
        // Portal interior: 2 wide by 3 high, reinforced frame: 4 by 5.
        for (int x = -1; x <= 2; x++) {
            for (int y = -1; y <= 3; y++) {
                BlockPos target = bottomLeft.offset(width, x).up(y);
                if (y == -1 || y == 3 || x == -1 || x == 2) {
                    world.setBlockState(target, frame.getDefaultState(), Block.NOTIFY_ALL);
                } else {
                    world.setBlockState(target, PORTAL.getDefaultState(), Block.NOTIFY_ALL);
                }
            }
        }
    }

    private static void rememberReturn(ServerPlayerEntity player) {
        for (String s : Set.copyOf(player.getCommandTags())) {
            if (s.startsWith(RETURN_PREFIX)) player.removeCommandTag(s);
        }
        BlockPos p = player.getBlockPos();
        player.addCommandTag(RETURN_PREFIX + p.getX() + "_" + p.getY() + "_" + p.getZ());
    }

    private static BlockPos rememberedReturn(ServerPlayerEntity player) {
        for (String s : player.getCommandTags()) {
            if (!s.startsWith(RETURN_PREFIX)) continue;
            try {
                String[] a = s.substring(RETURN_PREFIX.length()).split("_");
                return new BlockPos(Integer.parseInt(a[0]), Integer.parseInt(a[1]), Integer.parseInt(a[2]));
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException ignored) {
                // Return to world spawn instead.
            }
        }
        return player.getServer().getOverworld().getSpawnPos();
    }

    private static void enterSift(ServerPlayerEntity player) {
        ServerWorld dimension = player.getServer().getWorld(SIFT_WORLD);
        if (dimension == null) {
            player.sendMessage(Text.literal("§cИзмерение sift:sift не загрузилось. Проверь сборку и datapack."), false);
            return;
        }
        createArrivalPlatform(dimension);
        setCooldown(player);
        // The player arrives OUTSIDE the portal, on a safe floating platform.
        player.teleport(dimension, 0.5, RETURN_PORTAL_Y, 5.5, Set.of(), player.getYaw(), player.getPitch());
        player.sendMessage(Text.literal("§5СИФТ. §7Держись ближе к свету. Кристаллы защищают от тьмы."), false);
        dimension.playSound(null, BlockPos.ofFloored(0, RETURN_PORTAL_Y, 5),
                SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 0.5f);
    }

    private static void returnReturn(ServerPlayerEntity player) {
        ServerWorld overworld = player.getServer().getOverworld();
        BlockPos p = rememberedReturn(player);
        setCooldown(player);
        player.teleport(overworld, p.getX() + 0.5, p.getY(), p.getZ() + 0.5,
                Set.of(), player.getYaw(), player.getPitch());
        player.sendMessage(Text.literal("§aТы вернулся из Сифта."), true);
    }

    private static void createArrivalPlatform(ServerWorld dimension) {
        final int y = RETURN_PORTAL_Y - 1;
        // Build only on first entry, do not overwrite player constructions later.
        if (dimension.getBlockState(new BlockPos(-8, y, -8)).isOf(RIFT_STONE)
                && dimension.getBlockState(new BlockPos(-1, RETURN_PORTAL_Y, 0)).isOf(PORTAL)) {
            return;
        }
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                BlockPos floor = new BlockPos(x, y, z);
                dimension.setBlockState(floor, RIFT_STONE.getDefaultState(), Block.NOTIFY_ALL);
                for (int air = 1; air <= 5; air++) {
                    BlockPos space = floor.up(air);
                    if (dimension.getBlockState(space).isSolidBlock(dimension, space)) {
                        dimension.setBlockState(space, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                    }
                }
            }
        }
        // Return portal facing the traveler.
        buildGate(dimension, new BlockPos(-1, RETURN_PORTAL_Y, RETURN_PORTAL_Z),
                Direction.EAST, RIFT_STONE);
        for (int i = -7; i <= 7; i += 7) {
            for (int j = -7; j <= 7; j += 7) {
                dimension.setBlockState(new BlockPos(i, RETURN_PORTAL_Y, j), ECHO_CRYSTAL.getDefaultState(), Block.NOTIFY_ALL);
                dimension.setBlockState(new BlockPos(i, RETURN_PORTAL_Y + 1, j), ECHO_CRYSTAL.getDefaultState(), Block.NOTIFY_ALL);
            }
        }
    }

    private static void setCooldown(ServerPlayerEntity player) {
        PORTAL_COOLDOWN.put(player.getUuid(), player.getServer().getTicks() + 110);
    }

    private static boolean touchesPortal(ServerPlayerEntity player) {
        BlockPos feet = player.getBlockPos();
        return player.getServerWorld().getBlockState(feet).isOf(PORTAL)
                || player.getServerWorld().getBlockState(feet.up()).isOf(PORTAL);
    }

    private static void tick(MinecraftServer server) {
        int ticks = server.getTicks();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.isSpectator() || !player.isAlive()) continue;
            ServerWorld world = player.getServerWorld();
            boolean isSift = world.getRegistryKey().equals(SIFT_WORLD);
            SiftExpansion.tick(player, ticks, isSift);
            UUID uuid = player.getUuid();

            if (ticks % 4 == 0 && ticks >= PORTAL_COOLDOWN.getOrDefault(uuid, 0) && touchesPortal(player)) {
                if (isSift) returnReturn(player);
                else if (world.getRegistryKey().equals(World.OVERWORLD)) {
                    // Return position is set during activation, not overwritten when entering.
                    enterSift(player);
                }
                continue;
            }
            if (!isSift) continue;

            // Biome ambience: sparse falling ash and echo fragments.
            if (ticks % 12 == 0) {
                world.spawnParticles(ParticleTypes.ASH, player.getX(), player.getY() + 2.2, player.getZ(),
                        4, 5.0, 1.4, 5.0, 0.01);
                world.spawnParticles(ECHO_DUST, player.getX(), player.getY() + 1.0, player.getZ(),
                        2, 2.0, 1.0, 2.0, 0.01);
            }

            if (ticks % 60 == 0) {
                boolean safe = player.getBlockPos().isWithinDistance(
                        new BlockPos(0, RETURN_PORTAL_Y, 0), 21);
                if (!safe) {
                    safe = hasNearbyCrystal(world, player.getBlockPos());
                }
                if (safe) {
                    player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 240, 0, true, false));
                    player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 80, 0, true, false));
                } else {
                    player.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 100, 0, true, false));
                    if (ticks % 180 == 0) {
                        player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 100, 0, true, true));
                    }
                }
            }
            if (ticks % 100 == 0) {
                generateRuinNear(world, player);
            }
            if (ticks % 240 == 0 && player.getBlockPos().getSquaredDistance(
                    new BlockPos(0, RETURN_PORTAL_Y, 0)) > 32 * 32) {
                summonHunter(world, player);
            }
        }
    }

    private static boolean hasNearbyCrystal(ServerWorld world, BlockPos origin) {
        for (int dx = -5; dx <= 5; dx += 2) {
            for (int dy = -3; dy <= 3; dy += 2) {
                for (int dz = -5; dz <= 5; dz += 2) {
                    if (world.getBlockState(origin.add(dx, dy, dz)).isOf(ECHO_CRYSTAL)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static void summonHunter(ServerWorld world, ServerPlayerEntity player) {
        if (world.getRandom().nextInt(4) != 0) return;
        int already = world.getEntitiesByClass(HuskEntity.class,
                player.getBoundingBox().expand(45),
                mob -> mob.isAlive() && mob.getCommandTags().contains(HUNTER_TAG)).size();
        if (already >= 3) return;
        int x = player.getBlockX() + world.getRandom().nextBetween(-16, 16);
        int z = player.getBlockZ() + world.getRandom().nextBetween(-16, 16);
        int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z) + 1;
        if (Math.abs(y - player.getY()) > 22 || y <= -50 || y >= 300) return;
        BlockPos pos = new BlockPos(x, y, z);
        if (!world.getBlockState(pos).isAir() || !world.getBlockState(pos.up()).isAir()) return;
        HuskEntity hunter = EntityType.HUSK.create(world);
        if (hunter == null) return;
        hunter.refreshPositionAndAngles(x + 0.5, y, z + 0.5, world.getRandom().nextFloat() * 360, 0);
        hunter.setCustomName(Text.literal("§3Страж Сифта"));
        hunter.setCustomNameVisible(true);
        hunter.addCommandTag(HUNTER_TAG);
        hunter.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 20 * 60, 0, true, false));
        hunter.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 20 * 60, 1, true, false));
        world.spawnEntity(hunter);
        world.spawnParticles(ParticleTypes.SOUL, x + 0.5, y + 0.5, z + 0.5, 25, 0.7, 1.0, 0.7, 0.02);
    }

    /**
     * Stable, deterministic ruins per exploration region. An underground marker
     * prevents regeneration after server restarts (no invasive chunk-mixin needed).
     */
    private static void generateRuinNear(ServerWorld world, ServerPlayerEntity player) {
        int cellX = Math.floorDiv(player.getBlockX(), 192);
        int cellZ = Math.floorDiv(player.getBlockZ(), 192);
        for (int rx = cellX - 1; rx <= cellX + 1; rx++) {
            for (int rz = cellZ - 1; rz <= cellZ + 1; rz++) {
                long hash = rx * 341873128712L ^ rz * 132897987541L ^ world.getSeed();
                if (Math.floorMod(hash, 4L) != 0L) continue;
                int x = rx * 192 + 96;
                int z = rz * 192 + 96;
                if (Math.abs(x - player.getBlockX()) > 96 || Math.abs(z - player.getBlockZ()) > 96) continue;
                BlockPos marker = new BlockPos(x, -61, z);
                if (world.getBlockState(marker).isOf(RIFT_STONE)) continue;
                int height = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos floor = new BlockPos(x, height - 1, z);
                if (!world.getBlockState(floor).isSolidBlock(world, floor)) continue;
                // Mark the region BEFORE building so disconnections cannot duplicate it.
                world.setBlockState(marker, RIFT_STONE.getDefaultState(), Block.NOTIFY_ALL);
                createRuin(world, new BlockPos(x, height, z));
            }
        }
    }

    private static void createRuin(ServerWorld world, BlockPos center) {
        // Open-air ruin: cracked circular plinth, ruined corner pillars, glowing altar, a chest.
        int y = center.getY();
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                if (dx * dx + dz * dz > 29) continue;
                BlockPos p = center.add(dx, -1, dz);
                world.setBlockState(p, (Math.abs(dx + dz) % 3 == 0
                                ? SiftExpansion.CRACKED_SCULKSTONE_BRICKS : SiftExpansion.SCULKSTONE_BRICKS).getDefaultState(),
                        Block.NOTIFY_ALL);
            }
        }
        for (int dx : new int[]{-4, 4}) {
            for (int dz : new int[]{-4, 4}) {
                int h = 2 + world.getRandom().nextInt(4);
                for (int i = 0; i < h; i++) {
                    world.setBlockState(center.add(dx, i, dz),
                            Blocks.POLISHED_DEEPSLATE.getDefaultState(), Block.NOTIFY_ALL);
                }
                world.setBlockState(center.add(dx, h, dz), ECHO_CRYSTAL.getDefaultState(), Block.NOTIFY_ALL);
            }
        }
        world.setBlockState(center, ECHO_CRYSTAL.getDefaultState(), Block.NOTIFY_ALL);
        BlockPos chestPos = center.add(2, 0, 0);
        world.setBlockState(chestPos, Blocks.CHEST.getDefaultState(), Block.NOTIFY_ALL);
        BlockEntity entity = world.getBlockEntity(chestPos);
        if (entity instanceof LootableContainerBlockEntity chest) {
            chest.setLootTable(RegistryKey.of(RegistryKeys.LOOT_TABLE, id("chests/sift_ruins")));
            chest.setLootTableSeed(world.getRandom().nextLong());
        }
        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                center.getX(), center.getY() + 1, center.getZ(), 24, 1.0, 0.6, 1.0, 0.015);
    }
}
