package dev.yachiyoc.platform;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.AmbientEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.WaterCreatureEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.SnowGolemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.vehicle.VehicleEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Cached registry views for the generic selector. Entity targets are limited
 * to living entities and projectiles, grouped into Meteor-style categories.
 */
public final class RegistryCatalog {
    public static final String CATEGORY_ALL = "all";
    public static final String CATEGORY_PLAYERS = "players";
    public static final String CATEGORY_MONSTERS = "monsters";
    public static final String CATEGORY_ANIMALS = "animals";
    public static final String CATEGORY_VILLAGERS = "villagers";
    public static final String CATEGORY_GOLEMS = "golems";
    public static final String CATEGORY_PROJECTILES = "projectiles";
    public static final String CATEGORY_VEHICLES = "vehicles";
    public static final String CATEGORY_WATER = "water";
    public static final String CATEGORY_AMBIENT = "ambient";
    public static final String CATEGORY_OTHER = "other";

    private static final List<String> TARGET_CATEGORIES = List.of(
            CATEGORY_ALL, CATEGORY_PLAYERS, CATEGORY_MONSTERS, CATEGORY_ANIMALS,
            CATEGORY_VILLAGERS, CATEGORY_GOLEMS, CATEGORY_PROJECTILES,
            CATEGORY_VEHICLES, CATEGORY_WATER, CATEGORY_AMBIENT, CATEGORY_OTHER);

    private static final Map<RegistryKind, List<Entry>> CACHE = new EnumMap<>(RegistryKind.class);

    private RegistryCatalog() {
    }

    public static List<Entry> entries(RegistryKind kind) {
        List<Entry> cached = CACHE.get(kind);
        if (cached != null && (kind != RegistryKind.ENTITY_TARGET || !cached.isEmpty())) {
            return cached;
        }
        List<Entry> built = build(kind);
        if (kind != RegistryKind.ENTITY_TARGET || !built.isEmpty()) {
            CACHE.put(kind, built);
        }
        return built;
    }

    public static List<String> categories(RegistryKind kind) {
        return kind == RegistryKind.ENTITY_TARGET ? TARGET_CATEGORIES : List.of(CATEGORY_ALL);
    }

    private static List<Entry> build(RegistryKind kind) {
        List<Entry> entries = new ArrayList<>();
        switch (kind) {
            case ITEM -> {
                for (Item item : Registries.ITEM) {
                    if (item == Items.AIR) {
                        continue;
                    }
                    ItemStack stack = new ItemStack(item);
                    entries.add(new Entry(Registries.ITEM.getId(item).toString(),
                            stack.getName().getString(), stack));
                }
            }
            case BLOCK -> {
                for (Block block : Registries.BLOCK) {
                    if (block == Blocks.AIR || block == Blocks.CAVE_AIR || block == Blocks.VOID_AIR) {
                        continue;
                    }
                    ItemStack stack = new ItemStack(block.asItem());
                    entries.add(new Entry(Registries.BLOCK.getId(block).toString(),
                            block.getName().getString(), stack));
                }
            }
            case ENTITY -> {
                for (EntityType<?> type : Registries.ENTITY_TYPE) {
                    entries.add(new Entry(Registries.ENTITY_TYPE.getId(type).toString(),
                            type.getName().getString(), ItemStack.EMPTY));
                }
            }
            case ENTITY_TARGET -> buildTargets(entries);
        }
        entries.sort(Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Entry::id));
        return List.copyOf(entries);
    }

    private static void buildTargets(List<Entry> entries) {
        World world = MinecraftClient.getInstance().world;
        if (world == null) {
            return;
        }
        for (EntityType<?> type : Registries.ENTITY_TYPE) {
            String id = Registries.ENTITY_TYPE.getId(type).toString();
            if ("minecraft:armor_stand".equals(id)) {
                continue;
            }
            if ("minecraft:player".equals(id)) {
                entries.add(new Entry(id, type.getName().getString(), ItemStack.EMPTY, CATEGORY_PLAYERS));
                continue;
            }
            Entity entity = type.create(world, SpawnReason.COMMAND);
            if (entity == null) {
                continue;
            }
            boolean living = entity instanceof LivingEntity;
            boolean projectile = entity instanceof ProjectileEntity;
            boolean vehicle = entity instanceof VehicleEntity;
            if (!living && !projectile && !vehicle) {
                continue;
            }
            entries.add(new Entry(id, type.getName().getString(), ItemStack.EMPTY, category(entity)));
        }
    }

    private static String category(Entity entity) {
        if (entity instanceof PlayerEntity) {
            return CATEGORY_PLAYERS;
        }
        if (entity instanceof ProjectileEntity) {
            return CATEGORY_PROJECTILES;
        }
        if (entity instanceof VehicleEntity) {
            return CATEGORY_VEHICLES;
        }
        if (entity instanceof MerchantEntity) {
            return CATEGORY_VILLAGERS;
        }
        if (entity instanceof IronGolemEntity || entity instanceof SnowGolemEntity) {
            return CATEGORY_GOLEMS;
        }
        if (entity instanceof AnimalEntity) {
            return CATEGORY_ANIMALS;
        }
        if (entity instanceof Monster) {
            return CATEGORY_MONSTERS;
        }
        if (entity instanceof WaterCreatureEntity) {
            return CATEGORY_WATER;
        }
        if (entity instanceof AmbientEntity) {
            return CATEGORY_AMBIENT;
        }
        return CATEGORY_OTHER;
    }

    public record Entry(String id, String name, ItemStack stack, String category) {
        public Entry(String id, String name, ItemStack stack) {
            this(id, name, stack, CATEGORY_ALL);
        }
    }
}
