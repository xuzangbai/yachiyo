package dev.yachiyoc.hud;

import dev.yachiyoc.module.setting.BoolSetting;
import dev.yachiyoc.module.setting.DoubleSetting;
import dev.yachiyoc.module.setting.RegistryListSetting;
import dev.yachiyoc.module.setting.SoundSetting;
import dev.yachiyoc.platform.Chat;
import dev.yachiyoc.platform.RegistryKind;
import dev.yachiyoc.platform.SoundPlayer;
import dev.yachiyoc.ui.render.Render2D;
import dev.yachiyoc.ui.render.Text2D;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lists dropped items / entities around the player, with an optional watched
 * item alert (chat + custom ogg sound). The layout is intentionally simple and
 * fully configurable: range, item/entity toggles, line limit, scale, background.
 */
public final class DropDisplayHud extends HudElement {
    private final DoubleSetting range =
            add(new DoubleSetting("hud.drop_display", "range", 48.0, 8.0, 128.0, 1.0));
    private final BoolSetting showItems =
            add(new BoolSetting("hud.drop_display", "show_items", true));
    private final BoolSetting showEntities =
            add(new BoolSetting("hud.drop_display", "show_entities", true));
    private final BoolSetting showPlayers =
            add(new BoolSetting("hud.drop_display", "show_players", false));
    private final DoubleSetting maxLines =
            add(new DoubleSetting("hud.drop_display", "max_lines", 10.0, 1.0, 20.0, 1.0));
    private final BoolSetting background =
            add(new BoolSetting("hud.drop_display", "background", true));
    private final DoubleSetting scale =
            add(new DoubleSetting("hud.drop_display", "scale", 1.0, 0.5, 2.0, 0.1));
    private final RegistryListSetting watchedItems =
            add(new RegistryListSetting("hud.drop_display", "watched_items", RegistryKind.ITEM,
                    List.of("minecraft:diamond", "minecraft:netherite_ingot", "minecraft:elytra")));
    private final BoolSetting notifyChat =
            add(new BoolSetting("hud.drop_display", "notify_chat", true));
    private final BoolSetting notifySound =
            add(new BoolSetting("hud.drop_display", "notify_sound", true));
    private final SoundSetting sound =
            add(new SoundSetting("hud.drop_display", "sound", "ding0.4.ogg"));

    private final List<Entry> entries = new ArrayList<>();
    private final Map<String, Long> lastNotify = new HashMap<>();

    public DropDisplayHud() {
        super("drop_display");
    }

    @Override
    public void layout() {
        entries.clear();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) {
            width = 180.0F;
            height = 24.0F;
            return;
        }

        double maxDistance = range.get();
        for (Entity entity : client.world.getEntities()) {
            if (entity == client.player || entity.isRemoved()) {
                continue;
            }
            double distance = Math.sqrt(entity.squaredDistanceTo(client.player));
            if (distance > maxDistance) {
                continue;
            }
            if (entity instanceof ItemEntity item) {
                if (!showItems.get() || item.getStack().isEmpty()) {
                    continue;
                }
                String id = Registries.ITEM.getId(item.getStack().getItem()).toString();
                entries.add(new Entry(item.getStack().getName().getString(), item.getStack(),
                        distance, watchedItems.contains(id)));
            } else if (showEntities.get()) {
                if (entity instanceof PlayerEntity && !showPlayers.get()) {
                    continue;
                }
                entries.add(new Entry(entity.getName().getString(), ItemStack.EMPTY, distance, false));
            }
        }

        entries.sort(Comparator.comparing((Entry entry) -> !entry.watched())
                .thenComparingDouble(Entry::distance));
        notifyWatched();
        int limit = Math.max(1, (int) maxLines.get().doubleValue());
        if (entries.size() > limit) {
            entries.subList(limit, entries.size()).clear();
        }

        float s = scale.get().floatValue();
        float textWidth = 80.0F;
        for (Entry entry : entries) {
            String label = entry.label() + (entry.stack().getCount() > 1 ? " x" + entry.stack().getCount() : "");
            textWidth = Math.max(textWidth, Text2D.width(Text.literal(label)));
        }
        width = Math.max(170.0F, textWidth + 58.0F) * s;
        height = Math.max(24.0F, entries.size() * 18.0F + 8.0F) * s;
    }

    private void notifyWatched() {
        if (!notifyChat.get() && !notifySound.get()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Entry entry : entries) {
            if (entry.stack().isEmpty() || !entry.watched()) {
                continue;
            }
            String id = Registries.ITEM.getId(entry.stack().getItem()).toString();
            long last = lastNotify.getOrDefault(id, 0L);
            if (now - last < 4000L) {
                continue;
            }
            lastNotify.put(id, now);
            if (notifyChat.get()) {
                Chat.info(Text.translatable("hud.yachiyoc.drop_display.found",
                        entry.label(), entry.stack().getCount(), Math.round(entry.distance())));
            }
            if (notifySound.get()) {
                SoundPlayer.play(sound.get());
            }
        }
    }

    @Override
    public void render(DrawContext context) {
        float s = scale.get().floatValue();
        if (background.get()) {
            Render2D.roundedRect(context, x, y, width, height, 6.0F * s, 0xCC10141C);
        }

        if (entries.isEmpty()) {
            Text2D.drawScaled(context, Text.translatable("hud.yachiyoc.drop_display.empty"),
                    x + 8.0F * s, y + 7.0F * s, s, 0xFF8A909C, false);
            return;
        }

        for (int index = 0; index < entries.size(); index++) {
            Entry entry = entries.get(index);
            float rowY = y + (4.0F + index * 18.0F) * s;
            float textX = x + 8.0F * s;

            if (!entry.stack().isEmpty()) {
                context.getMatrices().pushMatrix();
                context.getMatrices().translate(x + 6.0F * s, rowY);
                context.getMatrices().scale(s, s);
                context.drawItem(entry.stack(), 0, 0);
                context.drawStackOverlay(MinecraftClient.getInstance().textRenderer, entry.stack(), 0, 0);
                context.getMatrices().popMatrix();
                textX = x + 26.0F * s;
            } else {
                Render2D.roundedRect(context, x + 8.0F * s, rowY + 5.0F * s,
                        4.0F * s, 4.0F * s, 1.0F * s, 0xFFA7ADBA);
                textX = x + 18.0F * s;
            }

            String label = entry.label() + (entry.stack().getCount() > 1 ? " x" + entry.stack().getCount() : "");
            Text shown = Text2D.trim(Text.literal(label), (int) ((width / s) - 48.0F));
            Text2D.drawScaled(context, shown, textX, rowY + 4.0F * s, s,
                    entry.watched() ? 0xFFFF5A5A : 0xFFF3F5F8, false);
            Text distance = Text.literal(Math.round(entry.distance()) + "m");
            float distanceWidth = Text2D.width(distance) * s * 0.9F;
            Text2D.drawScaled(context, distance,
                    x + width - 8.0F * s - distanceWidth, rowY + 4.0F * s, s * 0.9F,
                    entry.watched() ? 0xFFFF8A8A : 0xFF8A909C, false);
        }
    }

    private record Entry(String label, ItemStack stack, double distance, boolean watched) {
    }
}
