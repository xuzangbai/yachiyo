package dev.yachiyoc.platform;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Cached registry views for the generic selector. One selector screen is used
 * for items, blocks and entity types; callers only choose the kind.
 */
public final class RegistryCatalog {
    private static final Map<RegistryKind, List<Entry>> CACHE = new EnumMap<>(RegistryKind.class);

    private RegistryCatalog() {
    }

    public static List<Entry> entries(RegistryKind kind) {
        return CACHE.computeIfAbsent(kind, RegistryCatalog::build);
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
        }
        entries.sort(Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Entry::id));
        return List.copyOf(entries);
    }

    public record Entry(String id, String name, ItemStack stack) {
    }
}
