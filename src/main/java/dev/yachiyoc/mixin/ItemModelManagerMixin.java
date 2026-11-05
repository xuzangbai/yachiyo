package dev.yachiyoc.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.yachiyoc.item.SpearFix;
import dev.yachiyoc.module.impl.misc.ViaFix;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Via 长矛模型修复：把"被降级成剑的长矛"的 {@code item_model} 换回真长矛的模型。
 *
 * <p>移植自 NoDecodeKick（MIT License, Copyright (c) 2026 matl114）。
 */
@Mixin(ItemModelManager.class)
public abstract class ItemModelManagerMixin {
    @WrapOperation(
            method = "update",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/item/ItemStack;get(Lnet/minecraft/component/ComponentType;)Ljava/lang/Object;"))
    private Object yachiyoc$spearModel(ItemStack stack, ComponentType<?> type, Operation<Object> original) {
        Object value = original.call(stack, type);
        if (type != DataComponentTypes.ITEM_MODEL || !ViaFix.fixSpearModel()) {
            return value;
        }
        Item spear = SpearFix.realSpearItem(stack);
        if (spear == null || !SpearFix.isSpear(stack)) {
            return value;
        }
        Identifier model = spear.getComponents().get(DataComponentTypes.ITEM_MODEL);
        return model == null ? value : model;
    }
}
