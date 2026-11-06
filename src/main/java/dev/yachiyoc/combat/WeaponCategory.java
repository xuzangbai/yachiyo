package dev.yachiyoc.combat;

import dev.yachiyoc.item.SpearFix;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MaceItem;
import net.minecraft.item.TridentItem;
import net.minecraft.registry.tag.ItemTags;

import java.util.function.Predicate;

/**
 * 自动切换时“选哪一类武器”。
 *
 * <p>用种类而不是具体物品，这样同种类里带哪把就用哪把（下界合金剑 / 钻石剑 / 模组剑都算“剑”）。
 * 同类里具体挑哪一把由 {@link WeaponSwap} 按 {@code weapon} 组件的攻击力比较。
 */
public enum WeaponCategory {
    /**
     * 剑。注意排除“Via 降级成剑的长矛”（{@link SpearFix#isSpear}）：那种物品真实身份是长矛，
     * 不该被当成剑切过来。
     */
    SWORD(stack -> stack.isIn(ItemTags.SWORDS) && !SpearFix.isSpear(stack)),
    AXE(stack -> stack.isIn(ItemTags.AXES)),
    /** 长矛：原生标签，或者 Via 降级成剑的长矛。 */
    SPEAR(stack -> stack.isIn(ItemTags.SPEARS) || SpearFix.isSpear(stack)),
    MACE(stack -> stack.getItem() instanceof MaceItem),
    TRIDENT(stack -> stack.getItem() instanceof TridentItem),
    /** 任意能打的东西：只要有武器组件或命中剑/斧/长矛标签。 */
    ANY(stack -> stack.get(DataComponentTypes.WEAPON) != null
            || stack.get(DataComponentTypes.KINETIC_WEAPON) != null
            || stack.get(DataComponentTypes.PIERCING_WEAPON) != null
            || stack.isIn(ItemTags.SWORDS)
            || stack.isIn(ItemTags.AXES)
            || stack.isIn(ItemTags.SPEARS));

    private final Predicate<ItemStack> matcher;

    WeaponCategory(Predicate<ItemStack> matcher) {
        this.matcher = matcher;
    }

    public boolean matches(ItemStack stack) {
        return !stack.isEmpty() && matcher.test(stack);
    }
}
