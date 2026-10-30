package dev.yachiyoc.hud;

import dev.yachiyoc.module.setting.BoolSetting;
import dev.yachiyoc.module.setting.ColorSetting;
import dev.yachiyoc.module.setting.DoubleSetting;
import dev.yachiyoc.ui.render.Render2D;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * 9x3 inventory viewer. Mirrors the "inventory HUD" idea from Meteor/Lotus:
 * the main inventory (rows 1-3) is rendered as an item grid; counts are drawn
 * by vanilla's stack overlay.
 */
public final class ItemDisplayHud extends HudElement {
    private static final int SLOT = 18;
    private static final int PAD = 4;

    private final BoolSetting background =
            add(new BoolSetting("hud.item_display", "background", false));
    private final DoubleSetting scale =
            add(new DoubleSetting("hud.item_display", "scale", 1.0, 0.5, 2.5, 0.1));
    private final ColorSetting backgroundColor =
            add(new ColorSetting("hud.item_display", "background_color", 0xCC10141C));

    private final ItemStack[] preview = {
            new ItemStack(Items.OBSIDIAN, 64),
            new ItemStack(Items.END_CRYSTAL, 64),
            new ItemStack(Items.GOLDEN_APPLE, 64)
    };

    public ItemDisplayHud() {
        super("item_display");
    }

    @Override
    public void layout() {
        float s = scale.get().floatValue();
        width = (9 * SLOT + PAD * 2) * s;
        height = (3 * SLOT + PAD * 2) * s;
    }

    @Override
    public void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        float s = scale.get().floatValue();

        if (background.get()) {
            Render2D.roundedRect(context, x, y, width, height, 6.0F * s, backgroundColor.argb());
        }

        for (int index = 0; index < 27; index++) {
            ItemStack stack;
            if (client.player == null) {
                stack = index < preview.length ? preview[index] : ItemStack.EMPTY;
            } else {
                stack = client.player.getInventory().getStack(9 + index);
            }
            if (stack.isEmpty()) {
                continue;
            }

            int column = index % 9;
            int row = index / 9;
            float itemX = x + (PAD + column * SLOT) * s;
            float itemY = y + (PAD + row * SLOT) * s;

            context.getMatrices().pushMatrix();
            context.getMatrices().translate(itemX, itemY);
            context.getMatrices().scale(s, s);
            context.drawItem(stack, 0, 0);
            context.drawStackOverlay(client.textRenderer, stack, 0, 0);
            context.getMatrices().popMatrix();
        }
    }
}
