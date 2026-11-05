package dev.yachiyoc.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.yachiyoc.item.SpearFix;
import dev.yachiyoc.module.impl.misc.ViaFix;
import net.minecraft.client.render.entity.state.Lancing;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Via 长矛姿势修复：第三人称骑车/持矛（lancing）的手臂定位是按 {@code kinetic_weapon} 组件算的，
 * 被降级成剑的长矛拿不到组件就会摆成普通持剑姿势，这里把组件替换成真长矛的。
 *
 * <p>移植自 NoDecodeKick（MIT License, Copyright (c) 2026 matl114）。
 */
@Mixin(Lancing.class)
public abstract class LancingMixin {
    private static Object yachiyoc$armComponent(ItemStack stack, ComponentType<?> type,
                                                Operation<Object> original) {
        if (type == DataComponentTypes.KINETIC_WEAPON && ViaFix.fixSpearModel()) {
            Item spear = SpearFix.realSpearItem(stack);
            if (spear != null) {
                Object weapon = SpearFix.realKineticWeapon(stack);
                if (weapon != null) {
                    return weapon;
                }
            }
        }
        return original.call(stack, type);
    }

    @WrapOperation(
            method = "positionArmForSpear",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/item/ItemStack;get(Lnet/minecraft/component/ComponentType;)Ljava/lang/Object;"))
    private static Object yachiyoc$positionArmForSpear(ItemStack stack, ComponentType<?> type,
                                                       Operation<Object> original) {
        return yachiyoc$armComponent(stack, type, original);
    }

    @WrapOperation(
            method = "method_75392",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/item/ItemStack;get(Lnet/minecraft/component/ComponentType;)Ljava/lang/Object;"))
    private static Object yachiyoc$armPose(ItemStack stack, ComponentType<?> type,
                                           Operation<Object> original) {
        return yachiyoc$armComponent(stack, type, original);
    }

    @WrapOperation(
            method = "method_75395",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/item/ItemStack;get(Lnet/minecraft/component/ComponentType;)Ljava/lang/Object;"))
    private static Object yachiyoc$armPose2(ItemStack stack, ComponentType<?> type,
                                            Operation<Object> original) {
        return yachiyoc$armComponent(stack, type, original);
    }

    @WrapOperation(
            method = "method_75396",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/item/ItemStack;get(Lnet/minecraft/component/ComponentType;)Ljava/lang/Object;"))
    private static Object yachiyoc$armPose3(ItemStack stack, ComponentType<?> type,
                                            Operation<Object> original) {
        return yachiyoc$armComponent(stack, type, original);
    }
}
