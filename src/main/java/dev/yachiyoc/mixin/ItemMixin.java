package dev.yachiyoc.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.yachiyoc.item.SpearFix;
import dev.yachiyoc.module.impl.misc.ViaFix;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Via 长矛行为修复：被降级成剑的长矛要能像长矛一样"蓄力/突刺"。
 *
 * <p>{@code Item#getUseAction} 与 {@code Item#getMaxUseTime} 都是靠
 * {@code stack.contains(KINETIC_WEAPON)} 判断是不是长矛，这里把判断结果替换成
 * {@link SpearFix#realKineticWeapon}，两侧调用点共用同一套逻辑（非 kinetic 的调用原样放过）。
 *
 * <p>移植自 NoDecodeKick（MIT License, Copyright (c) 2026 matl114）。
 */
@Mixin(Item.class)
public abstract class ItemMixin {
    @WrapOperation(
            method = "getUseAction",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/item/ItemStack;contains(Lnet/minecraft/component/ComponentType;)Z",
                    ordinal = 1))
    private boolean yachiyoc$useAction(ItemStack stack, ComponentType<?> type, Operation<Boolean> original) {
        if (type == DataComponentTypes.KINETIC_WEAPON && ViaFix.fixSpearUseAction()) {
            return SpearFix.realKineticWeapon(stack) != null;
        }
        return original.call(stack, type);
    }

    @WrapOperation(
            method = "getMaxUseTime",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/item/ItemStack;contains(Lnet/minecraft/component/ComponentType;)Z",
                    ordinal = 1))
    private boolean yachiyoc$maxUseTime(ItemStack stack, ComponentType<?> type, Operation<Boolean> original) {
        if (type == DataComponentTypes.KINETIC_WEAPON && ViaFix.fixSpearUseAction()) {
            return SpearFix.realKineticWeapon(stack) != null;
        }
        return original.call(stack, type);
    }
}
