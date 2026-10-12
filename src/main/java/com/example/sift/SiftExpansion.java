package com.example.sift;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.AxeItem;
import net.minecraft.item.BlockItem;
import net.minecraft.item.HoeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.ShovelItem;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.item.ToolMaterials;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Rarity;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Main sculk expansion, designed for Yarn 1.21.1+build.3. */
public final class SiftExpansion {
    private SiftExpansion() {}
    private static Identifier id(String path) { return SiftMod.id(path); }
    private static final LinkedHashMap<String, Block> BUILDING = new LinkedHashMap<>();
    private static final LinkedHashMap<String, Item> ALL_ITEMS = new LinkedHashMap<>();
    private static final Identifier RESONANCE_ID = id("full_sculkite_set_resonance");
    // A sword attack has (base + 3) = 10 damage; the suit adds another 10 to its attack attribute.
    private static final double BONUS_DAMAGE = 10.0;

    private static Block block(String name, Block source, float strength, boolean light, boolean tool) {
        AbstractBlock.Settings s = AbstractBlock.Settings.copy(source).strength(strength, 10.0f);
        if (light) s = s.luminance(state -> 12);
        if (tool) s = s.requiresTool();
        Block result = Registry.register(Registries.BLOCK, id(name), new Block(s));
        BUILDING.put(name, result);
        item(name, new BlockItem(result, new Item.Settings()));
        return result;
    }

    private static <T extends Item> T item(String name, T i) {
        Registry.register(Registries.ITEM, id(name), i);
        ALL_ITEMS.put(name, i);
        return i;
    }

    // Rocks / architecture
    public static final Block SCULKSTONE = block("sculkstone", Blocks.DEEPSLATE, 3.7f, false, true);
    public static final Block POLISHED_SCULKSTONE = block("polished_sculkstone", Blocks.POLISHED_DEEPSLATE, 3.8f, false, true);
    public static final Block SCULKSTONE_BRICKS = block("sculkstone_bricks", Blocks.DEEPSLATE_BRICKS, 4.0f, false, true);
    public static final Block CRACKED_SCULKSTONE_BRICKS = block("cracked_sculkstone_bricks", Blocks.CRACKED_DEEPSLATE_BRICKS, 3.8f, false, true);
    public static final Block SCULKSTONE_TILES = block("sculkstone_tiles", Blocks.DEEPSLATE_TILES, 4.0f, false, true);
    public static final Block CHISELED_SCULKSTONE = block("chiseled_sculkstone", Blocks.CHISELED_DEEPSLATE, 4.0f, false, true);
    public static final Block SCULK_PILLAR = block("sculk_pillar", Blocks.POLISHED_BASALT, 4.1f, false, true);
    public static final Block SHADOW_BASALT = block("shadow_basalt", Blocks.BASALT, 2.5f, false, true);
    public static final Block SCULK_SOIL = block("sculk_soil", Blocks.DIRT, 0.6f, false, false);
    public static final Block SCULK_MOSS = block("sculk_moss", Blocks.MOSS_BLOCK, 0.2f, false, false);
    public static final Block ABYSSAL_SHALE = block("abyssal_shale", Blocks.TUFF, 2.8f, false, true);
    public static final Block GLOWING_SCULK = block("glowing_sculk", Blocks.SCULK, 0.5f, true, false);
    public static final Block SIFT_LAMP = block("sift_lamp", Blocks.SEA_LANTERN, 0.9f, true, false);
    public static final Block ECHO_GLASS = block("echo_glass", Blocks.GLASS, 0.3f, true, false);
    // Mineral deposits
    public static final Block SCULKITE_ORE = block("sculkite_ore", Blocks.DEEPSLATE_DIAMOND_ORE, 5.2f, true, true);
    public static final Block DEEP_SCULKITE_ORE = block("deep_sculkite_ore", Blocks.DEEPSLATE_DIAMOND_ORE, 6.0f, true, true);
    public static final Block ECHO_ORE = block("echo_ore", Blocks.DEEPSLATE_EMERALD_ORE, 4.6f, true, true);
    public static final Block SCULKITE_BLOCK = block("sculkite_block", Blocks.NETHERITE_BLOCK, 7.0f, true, true);

    // Crafting materials
    public static final Item RAW_SCULKITE = item("raw_sculkite", new Item(new Item.Settings().rarity(Rarity.UNCOMMON)));
    public static final Item SCULKITE_INGOT = item("sculkite_ingot", new Item(new Item.Settings().rarity(Rarity.RARE)));
    public static final Item ECHO_GEM = item("echo_gem", new Item(new Item.Settings().rarity(Rarity.RARE)));
    public static final Item ABYSS_CORE = item("abyss_core", new Item(new Item.Settings().rarity(Rarity.EPIC).maxCount(16)));
    public static final Item SCULK_DUST = item("sculk_dust", new Item(new Item.Settings()));
    public static final Item SCULK_HEART = item("sculk_heart", new Item(new Item.Settings().rarity(Rarity.RARE)));

    // 1.21.1 ToolMaterial is an interface, NOT the record used in later Minecraft versions.
    public static final ToolMaterial SCULKITE_MATERIAL = new ToolMaterial() {
        @Override public int getDurability() { return 2548; }   // netherite = 2031
        @Override public float getMiningSpeedMultiplier() { return 11.0f; } // netherite = 9
        @Override public float getAttackDamage() { return 6.0f; } // sword damage: 1 (player) + 6 (material) + 3 = 10
        @Override public int getEnchantability() { return 38; }
        @Override public net.minecraft.registry.tag.TagKey<Block> getInverseTag() {
            return ToolMaterials.NETHERITE.getInverseTag();
        }
        @Override public Ingredient getRepairIngredient() {
            return Ingredient.ofItems(SCULKITE_INGOT);
        }
    };

    public static final Item SCULKITE_SWORD = item("sculkite_sword",
            new SwordItem(SCULKITE_MATERIAL, new Item.Settings().rarity(Rarity.EPIC)
                    .attributeModifiers(SwordItem.createAttributeModifiers(SCULKITE_MATERIAL, 3, -2.4f))));
    public static final Item ECHO_BLADE = item("echo_blade",
            new SwordItem(SCULKITE_MATERIAL, new Item.Settings().rarity(Rarity.RARE)
                    .attributeModifiers(SwordItem.createAttributeModifiers(SCULKITE_MATERIAL, 2, -2.0f))));
    public static final Item SCULKITE_PICKAXE = item("sculkite_pickaxe", new PickaxeItem(SCULKITE_MATERIAL,
            new Item.Settings().attributeModifiers(PickaxeItem.createAttributeModifiers(SCULKITE_MATERIAL, 1.0f, -2.8f))));
    public static final Item SCULKITE_AXE = item("sculkite_axe", new AxeItem(SCULKITE_MATERIAL,
            new Item.Settings().attributeModifiers(AxeItem.createAttributeModifiers(SCULKITE_MATERIAL, 5.0f, -3.0f))));
    public static final Item SCULKITE_SHOVEL = item("sculkite_shovel", new ShovelItem(SCULKITE_MATERIAL,
            new Item.Settings().attributeModifiers(ShovelItem.createAttributeModifiers(SCULKITE_MATERIAL, 1.5f, -3.0f))));
    public static final Item SCULKITE_HOE = item("sculkite_hoe", new HoeItem(SCULKITE_MATERIAL,
            new Item.Settings().attributeModifiers(HoeItem.createAttributeModifiers(SCULKITE_MATERIAL, 0.0f, 0f))));

    // Armor material is registry-backed in 1.21.1.
    public static final RegistryEntry<ArmorMaterial> SCULKITE_ARMOR_MATERIAL = Registry.registerReference(
            Registries.ARMOR_MATERIAL, id("sculkite"), new ArmorMaterial(
                    Map.of(ArmorItem.Type.HELMET, 4, ArmorItem.Type.CHESTPLATE, 9,
                            ArmorItem.Type.LEGGINGS, 7, ArmorItem.Type.BOOTS, 4,
                            ArmorItem.Type.BODY, 12),
                    38, SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE,
                    () -> Ingredient.ofItems(SCULKITE_INGOT),
                    List.of(new ArmorMaterial.Layer(id("sculkite"))), 5.0f, 0.25f));

    public static final Item SCULKITE_HELMET = item("sculkite_helmet",
            new ArmorItem(SCULKITE_ARMOR_MATERIAL, ArmorItem.Type.HELMET,
                    new Item.Settings().maxDamage(ArmorItem.Type.HELMET.getMaxDamage(45)).rarity(Rarity.EPIC)));
    public static final Item SCULKITE_CHESTPLATE = item("sculkite_chestplate",
            new ArmorItem(SCULKITE_ARMOR_MATERIAL, ArmorItem.Type.CHESTPLATE,
                    new Item.Settings().maxDamage(ArmorItem.Type.CHESTPLATE.getMaxDamage(45)).rarity(Rarity.EPIC)));
    public static final Item SCULKITE_LEGGINGS = item("sculkite_leggings",
            new ArmorItem(SCULKITE_ARMOR_MATERIAL, ArmorItem.Type.LEGGINGS,
                    new Item.Settings().maxDamage(ArmorItem.Type.LEGGINGS.getMaxDamage(45)).rarity(Rarity.EPIC)));
    public static final Item SCULKITE_BOOTS = item("sculkite_boots",
            new ArmorItem(SCULKITE_ARMOR_MATERIAL, ArmorItem.Type.BOOTS,
                    new Item.Settings().maxDamage(ArmorItem.Type.BOOTS.getMaxDamage(45)).rarity(Rarity.EPIC)));

    public static void init() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.BUILDING_BLOCKS).register(entries -> {
            for (Block b : BUILDING.values()) entries.add(b);
        });
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(entries -> {
            entries.add(RAW_SCULKITE); entries.add(SCULKITE_INGOT); entries.add(ECHO_GEM);
            entries.add(ABYSS_CORE); entries.add(SCULK_DUST); entries.add(SCULK_HEART);
        });
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> {
            entries.add(SCULKITE_SWORD); entries.add(ECHO_BLADE);
            entries.add(SCULKITE_HELMET); entries.add(SCULKITE_CHESTPLATE);
            entries.add(SCULKITE_LEGGINGS); entries.add(SCULKITE_BOOTS);
            entries.add(SCULKITE_AXE);
        });
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> {
            entries.add(SCULKITE_PICKAXE); entries.add(SCULKITE_AXE);
            entries.add(SCULKITE_SHOVEL); entries.add(SCULKITE_HOE);
        });
    }

    public static boolean fullArmor(ServerPlayerEntity p) {
        return p.getEquippedStack(EquipmentSlot.HEAD).isOf(SCULKITE_HELMET)
                && p.getEquippedStack(EquipmentSlot.CHEST).isOf(SCULKITE_CHESTPLATE)
                && p.getEquippedStack(EquipmentSlot.LEGS).isOf(SCULKITE_LEGGINGS)
                && p.getEquippedStack(EquipmentSlot.FEET).isOf(SCULKITE_BOOTS);
    }

    public static void tick(ServerPlayerEntity p, int ticks, boolean inSift) {
        // Always sync on every tick so removing armor / swapping swords instantly removes x2 damage.
        EntityAttributeInstance attack = p.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        if (attack != null) {
            boolean active = fullArmor(p) && p.getMainHandStack().isOf(SCULKITE_SWORD);
            if (active && !attack.hasModifier(RESONANCE_ID)) {
                attack.addTemporaryModifier(new EntityAttributeModifier(RESONANCE_ID,
                        BONUS_DAMAGE, EntityAttributeModifier.Operation.ADD_VALUE));
                p.sendMessage(net.minecraft.text.Text.literal("§bСкалковый резонанс: меч ×2!"), true);
            } else if (!active && attack.hasModifier(RESONANCE_ID)) {
                attack.removeModifier(RESONANCE_ID);
            }
        }
        if (ticks % 80 != 0) return;
        if (inSift && fullArmor(p)) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 100, 0, true, false));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 260, 0, true, false));
        }
        if (inSift && p.getMainHandStack().isOf(ECHO_BLADE)) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 100, 0, true, false));
        }
    }
}
