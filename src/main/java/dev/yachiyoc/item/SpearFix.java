package dev.yachiyoc.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.KineticWeaponComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtInt;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.text.Text;

import java.util.Map;

/**
 * 1.21.11 长矛（spear）识别工具。
 *
 * <p>背景：1.21.11 才加入长矛，老版本服务端没有这个物品。Via 把长矛向下翻译时会退化成同材质的剑，
 * 并把原始物品 id 写进 {@code custom_data}，所以客户端看到的是"剑"，模型、持矛动作、蓄力行为全都不对。
 * 这里负责把这种"其实是长矛的剑"认出来，并给出对应的真长矛物品 / 真 {@code kinetic_weapon} 组件。
 *
 * <p>移植自 NoDecodeKick（MIT License, Copyright (c) 2026 matl114）。
 */
public final class SpearFix {
    /** 1.21.11 里第一把长矛的物品 id，id 大于等于它的原始物品就是长矛。 */
    private static final int FIRST_SPEAR_ITEM_ID = 1296;

    /** ViaFabricPlus 保存的原始物品 id（新版格式）。 */
    private static final String VIA_HASH_TAG = "VV|original_hashes";

    /** ViaFabricPlus 保存的原始物品 id（1.21.11 -> 1.21.9 协议专用）。 */
    private static final String VIA_PROTOCOL_TAG = "VB|Protocol1_21_11To1_21_9|id";

    /** Via 下拉时会退化成同材质的剑，这里按材质找回真长矛。 */
    private static final Map<Item, Item> SWORD_TO_SPEAR = Map.of(
            Items.WOODEN_SWORD, Items.WOODEN_SPEAR,
            Items.STONE_SWORD, Items.STONE_SPEAR,
            Items.COPPER_SWORD, Items.COPPER_SPEAR,
            Items.IRON_SWORD, Items.IRON_SPEAR,
            Items.GOLDEN_SWORD, Items.GOLDEN_SPEAR,
            Items.DIAMOND_SWORD, Items.DIAMOND_SPEAR,
            Items.NETHERITE_SWORD, Items.NETHERITE_SPEAR);

    private SpearFix() {
    }

    /** 这个物品栈在服务端眼里是不是长矛（原生长矛，或者被 Via 降级成剑的长矛）。 */
    public static boolean isSpear(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (stack.contains(DataComponentTypes.KINETIC_WEAPON)) {
            return true;
        }
        if (!stack.getItem().getRegistryEntry().isIn(ItemTags.SWORDS)) {
            return false;
        }
        Integer itemId = viaItemId(stack);
        if (itemId != null && itemId >= FIRST_SPEAR_ITEM_ID) {
            return true;
        }
        Text name = stack.getCustomName();
        if (name == null) {
            return false;
        }
        // 旧版 Via 不会写 custom_data，只能靠翻译后的物品名兜底。
        String text = name.getString();
        return text.contains("1.21.11") && text.contains("Spear");
    }

    /** 剑 -> 同材质长矛；不是可识别材质时返回 null。 */
    public static Item realSpearItem(ItemStack stack) {
        return stack == null ? null : SWORD_TO_SPEAR.get(stack.getItem());
    }

    /**
     * 真长矛的 {@code kinetic_weapon} 组件：原生长矛直接取自己的，被降级的剑取同材质长矛的默认值。
     */
    public static KineticWeaponComponent realKineticWeapon(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        Item spear = SWORD_TO_SPEAR.get(stack.getItem());
        if (spear != null && isSpear(stack)) {
            return spear.getComponents().get(DataComponentTypes.KINETIC_WEAPON);
        }
        return stack.get(DataComponentTypes.KINETIC_WEAPON);
    }

    /** 读出 ViaFabricPlus 记录的原始物品 id，读不到返回 null。 */
    public static Integer viaItemId(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (data == null || data.isEmpty()) {
            return null;
        }
        NbtCompound nbt = data.copyNbt();
        if (nbt == null) {
            return null;
        }
        NbtElement hashes = nbt.get(VIA_HASH_TAG);
        if (hashes instanceof NbtCompound compound) {
            NbtElement id = compound.get("id");
            if (id instanceof NbtInt value) {
                return value.intValue();
            }
        }
        NbtElement protocolId = nbt.get(VIA_PROTOCOL_TAG);
        if (protocolId instanceof NbtInt value) {
            return value.intValue();
        }
        return null;
    }
}
