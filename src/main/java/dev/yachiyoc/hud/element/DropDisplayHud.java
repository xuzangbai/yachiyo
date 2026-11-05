package dev.yachiyoc.hud.element;

import dev.yachiyoc.hud.HudElement;
import dev.yachiyoc.module.setting.BoolSetting;
import dev.yachiyoc.module.setting.ColorSetting;
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
 * Lists every loaded dropped item / entity around the player, with an optional watched
 * item alert (chat + custom ogg sound). The layout is intentionally simple and
 * fully configurable: item/entity toggles, item merging, line limit, scale, background.
 */
public final class DropDisplayHud extends HudElement {
    private final BoolSetting showItems =
            add(new BoolSetting("hud.drop_display", "show_items", true));
    /** 只有“物品本身完全相同”（同物品 + 同组件/同自定义名）的掉落物才合并成一行；关掉就一实体一行。 */
    private final BoolSetting mergeItems =
            add(new BoolSetting("hud.drop_display", "merge_items", true));
    private final BoolSetting showEntities =
            add(new BoolSetting("hud.drop_display", "show_entities", true));
    /**
     * 非玩家实体的白名单，复用通用实体选择器（和 aura 的目标列表同一套 UI）。
     * （去掉 player：玩家由“显示玩家”管）。
     */
    private final RegistryListSetting entityTypes =
            add(new RegistryListSetting("hud.drop_display", "entity_types", RegistryKind.ENTITY_TARGET,
                    List.of("minecraft:zombified_piglin",
                            // 下界表：家畜 + 僵尸系 + 苦力怕/蜘蛛/史莱姆/村民
                            "minecraft:cow", "minecraft:sheep", "minecraft:pig", "minecraft:horse",
                            "minecraft:zombie", "minecraft:husk", "minecraft:drowned",
                            "minecraft:zombie_villager", "minecraft:creeper",
                            "minecraft:spider", "minecraft:cave_spider", "minecraft:slime",
                            "minecraft:villager", "minecraft:bogged")));
    private final BoolSetting showPlayers =
            add(new BoolSetting("hud.drop_display", "show_players", true));
    private final BoolSetting pinPlayers =
            add(new BoolSetting("hud.drop_display", "pin_players", true));
    private final DoubleSetting maxLines =
            add(new DoubleSetting("hud.drop_display", "max_lines", 20.0, 1.0, 64.0, 1.0));
    private final BoolSetting background =
            add(new BoolSetting("hud.drop_display", "background", false));
    private final ColorSetting backgroundColor =
            add(new ColorSetting("hud.drop_display", "background_color", 0xCC10141C));
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
        pinPlayers.visibleWhen(showPlayers::get);
        mergeItems.visibleWhen(showItems::get);
        entityTypes.visibleWhen(showEntities::get);
        entityTypes.description(() -> Text.translatable("setting.yachiyoc.hud.drop_display.entity_types.description"));
        backgroundColor.visibleWhen(background::get);
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

        // 全量列出已加载范围内的一切实体：不做距离过滤，由 max_lines 决定最终显示多少行。
        // 距离统一用三维距离（既然取消了半径过滤，高度轴就不再需要单独忽略）。
        // 掉落物：开了“物品合并”时，只有物品本身完全相同（同物品 + 同组件）的堆才合并成一行
        // （数量累加、距离取最近）；关掉则一个掉落物实体一行。生物 / 实体 / 玩家不受这个开关影响。
        // 非玩家实体还要过一遍“实体类型”白名单（同类型的判定结果每帧只算一次）。
        Map<EntityType<?>, Group> groups = new HashMap<>();
        List<ItemGroup> itemGroups = new ArrayList<>();
        Map<EntityType<?>, Boolean> typeAllowed = new HashMap<>();
        for (Entity entity : client.world.getEntities()) {
            if (entity == client.player || entity.isRemoved()) {
                continue;
            }
            double distance = Math.sqrt(entity.squaredDistanceTo(client.player));
            if (entity instanceof ItemEntity item) {
                if (!showItems.get() || item.getStack().isEmpty()) {
                    continue;
                }
                ItemGroup group = mergeItems.get() ? findItemGroup(itemGroups, item.getStack()) : null;
                if (group == null) {
                    group = new ItemGroup();
                    itemGroups.add(group);
                }
                group.add(item.getStack(), distance);
            } else if (entity instanceof PlayerEntity) {
                if (showEntities.get() && showPlayers.get()) {
                    entries.add(new Entry(entity.getName().getString(), ItemStack.EMPTY,
                            distance, false, 1, true, true));
                }
            } else if (showEntities.get() && typeAllowed.computeIfAbsent(entity.getType(),
                    type -> entityTypes.contains(Registries.ENTITY_TYPE.getId(type).toString()))) {
                if (entity instanceof MobEntity mob) {
                    // Animals / monsters are merged by entity type; players stay individual.
                    groups.computeIfAbsent(mob.getType(), type -> new Group())
                            .add(distance);
                } else {
                    entries.add(new Entry(entity.getName().getString(), ItemStack.EMPTY,
                            distance, false, 1, false, false));
                }
            }
        }
        for (ItemGroup value : itemGroups) {
            String id = Registries.ITEM.getId(value.sample.getItem()).toString();
            entries.add(new Entry(value.sample.getName().getString(), value.sample,
                    value.distance, watchedItems.contains(id), value.count, false, false));
        }
        for (Map.Entry<EntityType<?>, Group> group : groups.entrySet()) {
            entries.add(new Entry(group.getKey().getName().getString(), ItemStack.EMPTY,
                    group.getValue().distance, false, group.getValue().count, false, false));
        }

        entries.sort(Comparator.comparing((Entry entry) -> !entry.watched())
                .thenComparing((Entry entry) -> !(pinPlayers.get() && entry.player()))
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

    /** 找出“物品本身完全相同”的已有分组；没有则返回 null（调用方新建一个）。 */
    private static ItemGroup findItemGroup(List<ItemGroup> groups, ItemStack stack) {
        for (ItemGroup group : groups) {
            if (!group.sample.isEmpty() && ItemStack.areItemsAndComponentsEqual(group.sample, stack)) {
                return group;
            }
        }
        return null;
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
            Render2D.roundedRect(context, x, y, width, height, 6.0F * s, backgroundColor.get());
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
            boolean highlighted = entry.watched() || entry.red();
            Text2D.drawScaled(context, shown, textX, rowY + 4.0F * s, s,
                    highlighted ? 0xFFFF5A5A : 0xFFF3F5F8, false);
            Text distance = Text.literal(Math.round(entry.distance()) + "m");
            float distanceWidth = Text2D.width(distance) * s * 0.9F;
            Text2D.drawScaled(context, distance,
                    x + width - 8.0F * s - distanceWidth, rowY + 4.0F * s, s * 0.9F,
                    highlighted ? 0xFFFF8A8A : 0xFF8A909C, false);
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

    /** 同类掉落物合并：数量累加、距离取最近，代表栈取最近那一堆（保留自定义名字）。 */
    private static final class ItemGroup {
        private ItemStack sample = ItemStack.EMPTY;
        private int count;
        private double distance = Double.MAX_VALUE;

        private void add(ItemStack stack, double entityDistance) {
            count += stack.getCount();
            if (entityDistance < distance) {
                distance = entityDistance;
                sample = stack;
            }
        }
    }

    private record Entry(String label, ItemStack stack, double distance, boolean watched,
                         int count, boolean red, boolean player) {
    }
}
