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
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
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
            add(new DoubleSetting("hud.drop_display", "range", 512.0, 8.0, 512.0, 1.0, true));
    private final BoolSetting showItems =
            add(new BoolSetting("hud.drop_display", "show_items", true));
    private final BoolSetting showEntities =
            add(new BoolSetting("hud.drop_display", "show_entities", true));
    private final BoolSetting showPlayers =
            add(new BoolSetting("hud.drop_display", "show_players", false));
    private final DoubleSetting maxLines =
            add(new DoubleSetting("hud.drop_display", "max_lines", 20.0, 1.0, 20.0, 1.0));
    private final BoolSetting background =
            add(new BoolSetting("hud.drop_display", "background", false));
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
    private long lastRangeCheck;
    private double cachedLoadedRange = 256.0;

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

        if (client.options != null) {
            range.setMax(loadedRange(client));
        }
        double maxDistance = range.get();
        Map<EntityType<?>, Group> groups = new HashMap<>();
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
                        distance, watchedItems.contains(id), item.getStack().getCount()));
            } else if (entity instanceof PlayerEntity) {
                if (showEntities.get() && showPlayers.get()) {
                    entries.add(new Entry(entity.getName().getString(), ItemStack.EMPTY,
                            distance, false, 1));
                }
            } else if (entity instanceof MobEntity mob) {
                if (!showEntities.get()) {
                    continue;
                }
                // Animals / monsters are merged by entity type; players stay individual.
                groups.computeIfAbsent(mob.getType(), type -> new Group())
                        .add(distance);
            } else if (entity instanceof LivingEntity) {
                if (showEntities.get()) {
                    entries.add(new Entry(entity.getName().getString(), ItemStack.EMPTY,
                            distance, false, 1));
                }
            } else if (showEntities.get()) {
                entries.add(new Entry(entity.getName().getString(), ItemStack.EMPTY,
                        distance, false, 1));
            }
        }
        for (Map.Entry<EntityType<?>, Group> group : groups.entrySet()) {
            entries.add(new Entry(group.getKey().getName().getString(), ItemStack.EMPTY,
                    group.getValue().distance, false, group.getValue().count));
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
            textWidth = Math.max(textWidth, Text2D.width(Text.literal(entryLabel(entry))));
        }
        width = Math.max(170.0F, textWidth + 58.0F) * s;
        height = Math.max(24.0F, entries.size() * 18.0F + 8.0F) * s;
    }

    /**
     * Actual loaded chunk radius around the player, not the client render
     * distance option. Server view distance is normally smaller, so using the
     * loaded chunks avoids showing "256m" when only 8-10 chunks are loaded.
     */
    private double loadedRange(MinecraftClient client) {
        if (client.options == null || client.world == null || client.player == null) {
            return cachedLoadedRange;
        }
        long now = System.currentTimeMillis();
        if (now - lastRangeCheck < 500L) {
            return cachedLoadedRange;
        }
        lastRangeCheck = now;

        int view = Math.max(2, client.options.getClampedViewDistance());
        int chunkX = client.player.getBlockX() >> 4;
        int chunkZ = client.player.getBlockZ() >> 4;
        int radius = 0;
        for (int r = 1; r <= view; r++) {
            if (client.world.isChunkLoaded(chunkX + r, chunkZ)
                    || client.world.isChunkLoaded(chunkX - r, chunkZ)
                    || client.world.isChunkLoaded(chunkX, chunkZ + r)
                    || client.world.isChunkLoaded(chunkX, chunkZ - r)) {
                radius = r;
            }
        }
        if (radius == 0) {
            radius = view;
        }
        cachedLoadedRange = Math.max(32.0, radius * 16.0);
        return cachedLoadedRange;
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
                        entry.label(), entry.count(), Math.round(entry.distance())));
            }
            if (notifySound.get()) {
                SoundPlayer.play(sound.get());
            }
        }
    }

    private static String entryLabel(Entry entry) {
        return entry.count() > 1 ? entry.label() + " x" + entry.count() : entry.label();
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

            Text shown = Text2D.trim(Text.literal(entryLabel(entry)),
                    (int) ((width / s) - 48.0F));
            Text2D.drawScaled(context, shown, textX, rowY + 4.0F * s, s,
                    entry.watched() ? 0xFFFF5A5A : 0xFFF3F5F8, false);
            Text distance = Text.literal(Math.round(entry.distance()) + "m");
            float distanceWidth = Text2D.width(distance) * s * 0.9F;
            Text2D.drawScaled(context, distance,
                    x + width - 8.0F * s - distanceWidth, rowY + 4.0F * s, s * 0.9F,
                    entry.watched() ? 0xFFFF8A8A : 0xFF8A909C, false);
        }
    }

    private static final class Group {
        private int count;
        private double distance = Double.MAX_VALUE;

        private void add(double entityDistance) {
            count++;
            distance = Math.min(distance, entityDistance);
        }
    }

    private record Entry(String label, ItemStack stack, double distance, boolean watched, int count) {
    }
}
