package dev.yachiyoc.module.impl.render;

import dev.yachiyoc.module.Module;
import dev.yachiyoc.module.ModuleCategory;
import dev.yachiyoc.module.setting.BoolSetting;
import dev.yachiyoc.module.setting.DoubleSetting;
import dev.yachiyoc.platform.Debug;
import dev.yachiyoc.platform.EffectParticles;
import dev.yachiyoc.render.Render;
import dev.yachiyoc.render.RenderApi;
import dev.yachiyoc.render.NameTagLayout;
import dev.yachiyoc.ui.Theme;
import dev.yachiyoc.ui.render.UiTextures;
import dev.yachiyoc.render.Render3DCallbacks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 名字标签：在玩家头顶画名字、血量、距离、延迟、药水状态。
 *
 * <p>走 3D 层（{@link Render}），文字和底板都是正对相机的 billboard；
 * 原版名牌由 {@code EntityRendererMixin} 在本模块开启时取消，避免叠字。
 *
 * <p>药水：数据来自 {@code LivingEntity#getStatusEffects()}，能不能看到**取决于服务端是否下发**
 * （原版会给追踪范围内实体的效果包，但不少反作弊/隐藏插件会把别人的效果剥掉）。
 */
public final class NameTags extends Module {
    /** 装备显示顺序：头胸腿脚 → 主手 → 副手。 */
    private static final EquipmentSlot[] EQUIPMENT_ORDER = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
    };
    private static final int MAX_EFFECTS = 6;
    /** 神龟（turtle_master）用的随机图标，放在 textures/images/turtles/ 下。 */
    private static final String[] TURTLE_ICONS = {"pig_turtle", "ufo_turtle"};
    /** 图标多久换一次（毫秒）。 */
    private static final long ICON_ROLL_MS = 1500L;

    private final DoubleSetting range =
            add(new DoubleSetting("nametags", "range", 96.0, 8.0, 512.0, 8.0));
    private final DoubleSetting scale =
            add(new DoubleSetting("nametags", "scale", 1.0, 0.4, 2.5, 0.05));
    private final BoolSetting constantSize =
            add(new BoolSetting("nametags", "constant_size", true));
    private final DoubleSetting height =
            add(new DoubleSetting("nametags", "height", 0.0, -2.0, 3.0, 0.05));
    private final BoolSetting health =
            add(new BoolSetting("nametags", "health", true));
    private final BoolSetting healthBar =
            add(new BoolSetting("nametags", "health_bar", true));
    private final BoolSetting distance =
            add(new BoolSetting("nametags", "distance", true));
    private final BoolSetting ping =
            add(new BoolSetting("nametags", "ping", true));
    private final BoolSetting potions =
            add(new BoolSetting("nametags", "potions", true));
    /** 装备图标横向间距（倍数，1 = 刚好贴着）。 */
    private final DoubleSetting equipGapX =
            add(new DoubleSetting("nametags", "equip_gap_x", 1.25, 0.9, 3.0, 0.05));
    /** 装备文字纵向间距（倍数，1 = 默认行距）。 */
    private final DoubleSetting equipGapY =
            add(new DoubleSetting("nametags", "equip_gap_y", 1.0, 0.4, 3.0, 0.05));
    /** 装备图标相对大小；固定屏幕模式下 0.5 对应 20 个 GUI 像素。 */
    private final DoubleSetting equipScale =
            add(new DoubleSetting("nametags", "equip_scale", 0.5, 0.2, 1.5, 0.05));
    /** 装备总开关（默认关）。 */
    private final BoolSetting equipment =
            add(new BoolSetting("nametags", "equipment", false));
    /** 图标下方完整显示装备名字。 */
    private final BoolSetting equipName =
            add(new BoolSetting("nametags", "equip_name", true));
    /** 图标上方完整显示附魔。 */
    private final BoolSetting equipEnchant =
            add(new BoolSetting("nametags", "equip_enchant", true));
    /** 神龟图标大小（单位：行高，固定屏幕模式下 1 对应 10 个 GUI 像素）。 */
    private final DoubleSetting turtleSize =
            add(new DoubleSetting("nametags", "turtle_size", 7.0, 1.0, 40.0, 0.5));
    private final BoolSetting effectTime =
            add(new BoolSetting("nametags", "effect_time", true));
    private final BoolSetting turtleStrict =
            add(new BoolSetting("nametags", "turtle_strict", false));
    private final BoolSetting background =
            add(new BoolSetting("nametags", "background", true));
    private final BoolSetting throughWalls =
            add(new BoolSetting("nametags", "through_walls", true));
    private final BoolSetting self =
            add(new BoolSetting("nametags", "self", false));
    private final BoolSetting invisible =
            add(new BoolSetting("nametags", "invisible", true));

    private final Runnable renderTask = this::renderNameTags;
    /** UUID -> {当前图标下标, 下次重摇时间}。 */
    private final Map<UUID, long[]> iconRoll = new HashMap<>();
    private final Map<UUID, TagState> tags = new HashMap<>();
    private final List<PlayerEntity> visiblePlayers = new ArrayList<>();
    private World renderedWorld;
    private static final float NAME_FONT = 1.05F, INFO_FONT = 0.82F, EFFECT_FONT = 0.80F, GEAR_FONT = 0.74F;

    private record Chip(Text text, float width, int color) { }
    private record Gear(ItemStack stack, float width, List<Text> names, List<Text> enchantments) { }

    private static final class TagState {
        final NameTagLayout.Vitals vitals = new NameTagLayout.Vitals();
        long refreshed, lastSeen;
        Text name;
        float nameWidth, bodyWidth, chipWidth;
        List<Chip> chips = List.of();
        List<Text> effects = List.of();
        List<Gear> gear = List.of();
        boolean turtle;
    }

    public NameTags() {
        super("nametags", ModuleCategory.RENDER, true);
        group("layout", range, scale, constantSize, height, background, throughWalls, self, invisible);
        group("information", health, healthBar, distance, ping);
        group("equipment", equipment, equipName, equipEnchant, equipGapX, equipGapY, equipScale);
        group("effects", potions, effectTime, turtleSize, turtleStrict);
        healthBar.visibleWhen(health::get);
        constantSize.description(() -> Text.translatable("setting.yachiyoc.nametags.constant_size.description"));
        scale.description(() -> Text.translatable("setting.yachiyoc.nametags.scale.description"));
        // Persisted enabled state is restored without onEnable(), so the callback stays registered.
        Render3DCallbacks.register(renderTask);
    }

    @Override
    public void onEnable() {
        Render3DCallbacks.register(renderTask);
    }

    @Override
    public void onDisable() {
        tags.clear();
        visiblePlayers.clear();
        iconRoll.clear();
        particleCache.clear();
        renderedWorld = null;
    }

    private void renderNameTags() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!enabled() || client.player == null || client.world == null || !Render.api().inWorld()) return;
        if (renderedWorld != client.world) {
            tags.clear();
            iconRoll.clear();
            particleCache.clear();
            renderedWorld = client.world;
        }
        RenderApi api = Render.api();
        Vec3d camera = api.cameraPos();
        double squaredRange = range.get() * range.get();
        visiblePlayers.clear();
        for (PlayerEntity player : client.world.getPlayers()) {
            if (player.isRemoved() || (player == client.player && !self.get())
                    || (player.isInvisible() && !invisible.get())
                    || (!throughWalls.get() && !client.player.canSee(player))
                    || player.squaredDistanceTo(camera) > squaredRange) continue;
            visiblePlayers.add(player);
        }
        visiblePlayers.sort(Comparator.comparingDouble(player -> -player.squaredDistanceTo(camera)));
        long now = System.nanoTime();
        if (Debug.enabled("nametags")) Debug.logThrottled("nametags", 2000L,
                "labels=%d constant_size=%s", visiblePlayers.size(), constantSize.get());
        boolean previousDepth = api.throughWalls();
        try {
            api.throughWalls(throughWalls.get());
            for (PlayerEntity player : visiblePlayers) draw(client, player, now);
        } finally {
            api.throughWalls(previousDepth);
        }
        tags.entrySet().removeIf(entry -> now - entry.getValue().lastSeen > 2_000_000_000L);
        iconRoll.keySet().removeIf(id -> !tags.containsKey(id));
        particleCache.keySet().removeIf(id -> !tags.containsKey(id));
    }

    private void draw(MinecraftClient client, PlayerEntity player, long now) {
        RenderApi api = Render.api();
        Vec3d lerped = player.getLerpedPos(api.tickDelta());
        Vec3d anchor = lerped.add(0, player.getHeight() + 0.30 + height.get(), 0);
        float pixelSize = api.billboardPixelSize(anchor);
        if (pixelSize <= 0) return;
        float unit = (constantSize.get() ? pixelSize : 0.025F) * scale.get().floatValue();
        TagState state = tags.computeIfAbsent(player.getUuid(), ignored -> new TagState());
        state.lastSeen = now;
        state.vitals.update(player.getHealth(), player.getMaxHealth(), player.getAbsorptionAmount(), now);
        if (state.name == null || now - state.refreshed >= 120_000_000L) refresh(client, player, lerped, state, now);

        boolean bar = health.get() && healthBar.get();
        boolean absorption = bar && (player.getAbsorptionAmount() > 0 || state.vitals.absorption() > 0.005F);
        float textHeight = 13 + (state.chips.isEmpty() ? 0 : 16)
                + (state.effects.isEmpty() ? 0 : state.effects.size() * 10 + 7);
        float mainHeight = textHeight + 16 + (bar ? 12 + (absorption ? 7 : 0) : 0);
        float badgeSize = state.turtle ? 10 * turtleSize.get().floatValue() : 0;
        float badgeWidth = state.turtle ? badgeSize + 12 : 0;
        float width = state.bodyWidth + badgeWidth;
        float cardHeight = Math.max(mainHeight, state.turtle ? badgeSize + 16 : 0);
        float left = -width / 2, top = -cardHeight;
        float mainLeft = left + badgeWidth;
        float mainTop = top + (cardHeight - mainHeight) / 2;
        if (background.get()) {
            panel(api, anchor, left - 1, top + 1, width + 2, cardHeight + 2, unit, -0.15F, 0x35000000);
            panel(api, anchor, left, top, width, cardHeight, unit, 0, 0xD0191722);
            panel(api, anchor, mainLeft + 7, mainTop + 8, 2, 10, unit, 0.10F, Theme.ACCENT);
        }
        text(api, anchor, state.name, mainLeft + 14, mainTop + 8, NAME_FONT, unit, 0xFFF8F6FF);
        float rowY = mainTop + 24;
        float chipX = mainLeft + 8;
        for (Chip chip : state.chips) {
            if (background.get()) panel(api, anchor, chipX, rowY - 2, chip.width(), 13, unit, 0.12F,
                    0x20000000 | (chip.color() & 0xFFFFFF));
            text(api, anchor, chip.text(), chipX + 4, rowY, INFO_FONT, unit, chip.color());
            chipX += chip.width() + 4;
        }
        if (!state.chips.isEmpty()) rowY += 16;
        if (!state.effects.isEmpty()) {
            if (background.get()) panel(api, anchor, mainLeft + 8, rowY - 1,
                    state.bodyWidth - 16, state.effects.size() * 10 + 4, unit, 0.12F, 0x1CABA1E8);
            for (Text effect : state.effects) {
                text(api, anchor, effect, mainLeft + 12, rowY + 2, EFFECT_FONT, unit, 0xFFCFC4FF);
                rowY += 10;
            }
        }
        if (bar) drawHealth(api, anchor, mainLeft + 8, mainTop + mainHeight - (absorption ? 20 : 13),
                state.bodyWidth - 16, state, unit, absorption);
        if (state.turtle) {
            api.billboardImage(turtleIcon(player), at(api, anchor, left + 8 + badgeSize / 2,
                    top + cardHeight / 2, unit, 0.4F), badgeSize * unit, badgeSize * unit);
        }
        drawEquipment(api, anchor, state.gear, top - 7, unit);
    }

    private void refresh(MinecraftClient client, PlayerEntity player, Vec3d lerped, TagState state, long now) {
        state.refreshed = now;
        state.name = Text.literal(player.getName().getString()).formatted(Formatting.BOLD);
        state.nameWidth = pixels(state.name, NAME_FONT);
        List<Chip> chips = new ArrayList<>(3);
        if (health.get()) {
            String value = String.format(Locale.ROOT, "%.1f / %.0f", player.getHealth(), player.getMaxHealth());
            MutableText hp = Text.literal(value);
            if (player.getAbsorptionAmount() > 0) hp.append(Text.literal(String.format(Locale.ROOT,
                    " +%.1f", player.getAbsorptionAmount())).withColor(0xFFD24A));
            addChip(chips, hp, healthColor(player.getHealth() / Math.max(1, player.getMaxHealth())));
        }
        if (distance.get()) addChip(chips, Text.literal(String.format(Locale.ROOT, "%.1fm", player.distanceTo(client.player))), 0xFFC2C7DA);
        if (ping.get()) {
            int latency = latency(client, player);
            if (latency >= 0) addChip(chips, Text.literal(latency + "ms"),
                    latency < 100 ? 0xFF8EDDB8 : latency < 200 ? 0xFFFFCF7D : 0xFFFF919D);
        }
        state.chips = chips;
        state.chipWidth = 0;
        for (Chip chip : chips) state.chipWidth += chip.width() + 4;
        if (!chips.isEmpty()) state.chipWidth -= 4;
        state.bodyWidth = Math.max(112, Math.max(state.nameWidth + 26, state.chipWidth + 16));

        String effects = potions.get() ? effectsLine(player) : "";
        if (potions.get() && effects.isEmpty()) {
            effects = metadataEffects(player);
            if (effects.isEmpty()) effects = particleEffects(player, lerped);
        }
        if (!effects.isEmpty()) state.bodyWidth = Math.max(state.bodyWidth,
                Math.min(200, pixels(Text.literal(effects), EFFECT_FONT) + 24));
        List<Text> effectLines = new ArrayList<>();
        for (String line : NameTagLayout.wrap(effects, state.bodyWidth - 24,
                value -> pixels(Text.literal(value), EFFECT_FONT))) effectLines.add(Text.literal(line));
        state.effects = effectLines;
        state.turtle = potions.get() && (isTurtleMaster(player) || isInferredTurtle(effects));
        state.gear = equipment.get() ? equipment(player) : List.of();
    }

    private static void addChip(List<Chip> chips, Text text, int color) {
        chips.add(new Chip(text, pixels(text, INFO_FONT) + 8, color));
    }

    private List<Gear> equipment(PlayerEntity player) {
        List<Gear> result = new ArrayList<>(6);
        for (EquipmentSlot slot : EQUIPMENT_ORDER) {
            ItemStack equipped = player.getEquippedStack(slot);
            if (equipped.isEmpty()) continue;
            ItemStack stack = equipped.copy();
            Text name = stack.get(DataComponentTypes.CUSTOM_NAME);
            if (name == null) name = stack.getName();
            List<Text> enchantments = new ArrayList<>();
            if (equipEnchant.get()) {
                ItemEnchantmentsComponent component = EnchantmentHelper.getEnchantments(stack);
                for (RegistryEntry<Enchantment> entry : component.getEnchantments()) {
                    int level = component.getLevel(entry);
                    enchantments.add(entry.value().description().copy().append(level > 1 ? " " + roman(level) : ""));
                }
            }
            float widest = equipName.get() ? pixels(name, GEAR_FONT) : 0;
            for (Text enchantment : enchantments) widest = Math.max(widest, pixels(enchantment, GEAR_FONT));
            float column = Math.max(equipScale.get().floatValue() / 0.025F + 8, Math.clamp(widest + 10, 34, 66));
            List<Text> names = equipName.get() ? wrapStyled(name, column - 8) : List.of();
            List<Text> wrappedEnchantments = new ArrayList<>();
            for (Text enchantment : enchantments) wrappedEnchantments.addAll(wrapStyled(enchantment, column - 8));
            result.add(new Gear(stack, column, names, wrappedEnchantments));
        }
        return result;
    }

    /** Preserve all styled text while wrapping long custom item names and enchantments. */
    private static List<Text> wrapStyled(Text source, float width) {
        List<Text> lines = new ArrayList<>();
        MutableText[] line = {Text.empty()};
        source.visit((style, value) -> {
            for (int offset = 0; offset < value.length();) {
                int point = value.codePointAt(offset);
                offset += Character.charCount(point);
                if (point == '\n') { lines.add(line[0]); line[0] = Text.empty(); continue; }
                Text glyph = Text.literal(new String(Character.toChars(point))).setStyle(style);
                MutableText next = line[0].copy().append(glyph);
                if (!line[0].getString().isEmpty() && pixels(next, GEAR_FONT) > width) {
                    lines.add(line[0]);
                    line[0] = Text.empty();
                }
                line[0].append(glyph);
            }
            return Optional.empty();
        }, Style.EMPTY);
        if (!line[0].getString().isEmpty()) lines.add(line[0]);
        return lines;
    }

    private void drawEquipment(RenderApi api, Vec3d anchor, List<Gear> gear, float bottom, float unit) {
        if (gear.isEmpty()) return;
        float icon = equipScale.get().floatValue() / 0.025F;
        float vertical = equipGapY.get().floatValue();
        float lineStep = Math.max(8, 8 * vertical);
        int enchantLines = 0, nameLines = 0;
        float width = 0;
        float gap = Math.max(4, icon * (equipGapX.get().floatValue() - 1));
        for (Gear slot : gear) {
            enchantLines = Math.max(enchantLines, slot.enchantments().size());
            nameLines = Math.max(nameLines, slot.names().size());
            width += slot.width() + gap;
        }
        width -= gap;
        float above = enchantLines * lineStep;
        float below = nameLines * lineStep;
        float height = 12 + above + icon + below + (nameLines > 0 ? 4 * vertical : 0);
        float top = bottom - height;
        float left = -width / 2;
        for (Gear slot : gear) {
            if (background.get()) panel(api, anchor, left, top, slot.width(), height, unit, 0, 0xBF191722);
            float middle = left + slot.width() / 2;
            float iconY = top + 6 + above + icon / 2;
            api.item(slot.stack(), at(api, anchor, middle, iconY, unit, 0.4F), icon * unit);
            float y = top + 5;
            for (Text label : slot.enchantments()) {
                text(api, anchor, label, middle - pixels(label, GEAR_FONT) / 2, y, GEAR_FONT, unit, 0xFFCFC4FF);
                y += lineStep;
            }
            y = iconY + icon / 2 + 4 * vertical;
            for (Text label : slot.names()) {
                text(api, anchor, label, middle - pixels(label, GEAR_FONT) / 2, y, GEAR_FONT, unit, 0xFFE8E5F2);
                y += lineStep;
            }
            left += slot.width() + gap;
        }
    }

    private static void drawHealth(RenderApi api, Vec3d anchor, float x, float y, float width,
                                   TagState state, float unit, boolean absorption) {
        // Track, delayed damage and current HP use separate depths in the same camera-facing plane.
        panel(api, anchor, x, y, width, 6, unit, 0.10F, 0xCC0C0C13);
        if (state.vitals.trail() > state.vitals.fill() + 0.002F)
            panel(api, anchor, x, y, width * state.vitals.trail(), 6, unit, 0.20F, 0xDFF1B8A3);
        if (state.vitals.fill() > 0.002F)
            panel(api, anchor, x, y, width * state.vitals.fill(), 6, unit, 0.30F, healthColor(state.vitals.fill()));
        if (absorption) {
            panel(api, anchor, x, y + 9, width, 3, unit, 0.10F, 0x996A5425);
            if (state.vitals.absorption() > 0.002F)
                panel(api, anchor, x, y + 9, width * state.vitals.absorption(), 3, unit, 0.30F, 0xFFFFD24A);
        }
    }

    private static Vec3d at(RenderApi api, Vec3d anchor, float x, float y, float unit, float layer) {
        return api.billboardOffset(anchor, x * unit, -y * unit, layer * unit);
    }

    private static void panel(RenderApi api, Vec3d anchor, float x, float y, float width, float height,
                              float unit, float layer, int color) {
        api.billboardPanel(at(api, anchor, x + width / 2, y + height / 2, unit, layer), width * unit, height * unit, color);
    }

    private void text(RenderApi api, Vec3d anchor, Text text, float left, float top, float font, float unit, int color) {
        api.text(text, at(api, anchor, left + pixels(text, font) / 2, top, unit, 0.55F),
                font * unit / 0.025F, color, true, throughWalls.get());
    }

    private static float pixels(Text text, float font) {
        return Render.api().textWidth(text, font) / 0.025F;
    }

    /** 推断行里出现"抗性提升"也算神龟（敌人没有真实效果数据时走这条）。 */
    private boolean isInferredTurtle(String effects) {
        if (turtleStrict.get() || effects == null || effects.isEmpty()) {
            return false;
        }
        return effects.contains(StatusEffects.RESISTANCE.value().getName().getString());
    }
    private boolean isTurtleMaster(PlayerEntity player) {
        StatusEffectInstance resistance = player.getStatusEffect(StatusEffects.RESISTANCE);
        if (resistance == null) {
            return false;
        }
        if (!turtleStrict.get()) {
            // 宽松：只要身上有抗性提升就认（很多服的「神龟」只发抗性）
            return true;
        }
        // 严格：原版神龟 = 抗性 III + 缓慢 IV
        return resistance.getAmplifier() >= 2 || player.hasStatusEffect(StatusEffects.SLOWNESS);
    }
    /** 每个玩家一个图标，每 1.5 秒重摇；没有战绩就随机，看起来是"每次随机换" */
    private Identifier turtleIcon(PlayerEntity player) {
        long now = System.currentTimeMillis();
        long[] slot = iconRoll.computeIfAbsent(player.getUuid(), key -> new long[]{-1L, 0L});
        if (slot[1] <= now || slot[0] < 0 || slot[0] >= TURTLE_ICONS.length) {
            slot[0] = player.getRandom().nextInt(TURTLE_ICONS.length);
            slot[1] = now + ICON_ROLL_MS;
        }
        return UiTextures.turtleIcon(TURTLE_ICONS[(int) slot[0]]);
    }
    /**
     * 药水状态：有益的排前面，最多 {@link #MAX_EFFECTS} 个。
     * 名字直接用游戏内译名，等级用罗马数字（I 级不写）。
     */
    private String effectsLine(PlayerEntity player) {
        List<StatusEffectInstance> effects = new ArrayList<>(player.getStatusEffects());
        if (effects.isEmpty()) {
            return "";
        }
        effects.sort(Comparator
                .comparingInt((StatusEffectInstance effect) -> rank(effect))
                .thenComparingInt(StatusEffectInstance::getAmplifier).reversed());
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < Math.min(effects.size(), MAX_EFFECTS); i++) {
            StatusEffectInstance effect = effects.get(i);
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(effect.getEffectType().value().getName().getString());
            int level = effect.getAmplifier() + 1;
            if (level > 1) {
                builder.append(roman(level));
            }
            String time = effectTime.get() ? remaining(effect.getDuration()) : "";
            if (!time.isEmpty()) {
                builder.append(' ').append(time);
            }
        }
        if (effects.size() > MAX_EFFECTS) {
            builder.append(" +").append(effects.size() - MAX_EFFECTS);
        }
        return builder.toString();
    }

    /**    /**
     * 服务端把「这个玩家身上该冒哪些效果粒子」放在实体元数据里
     * （{@code LivingEntity.POTION_SWIRLS}，一个 {@code List<ParticleEffect>}），
     * 效果包被剥掉时它**照样会下发** —— 这就是 SLM 用的路子，比按位置猜粒子稳得多。
     *
     * <p>每个粒子带着该效果的颜色，按颜色精确查表就能还原效果列表，不需要模糊匹配。
     */
    /** 粒子兜底的记忆表：uuid -> (效果名 -> 到期时间)，看到就刷新、过期才删，避免乱跳。 */
    private final Map<UUID, Map<String, Long>> particleCache = new HashMap<>();
    private static final long PARTICLE_TTL_MS = 3000L;
    private static final Map<Integer, String> COLOR_MEMO = new HashMap<>();

    /** 兜底：按玩家附近冒出来的药水粒子颜色反推。 */
    private String particleEffects(PlayerEntity player, Vec3d pos) {
        long now = System.currentTimeMillis();
        Map<String, Long> cache = particleCache.computeIfAbsent(player.getUuid(), key -> new LinkedHashMap<>());
        for (int rgb : EffectParticles.colorsNear(pos, 2.2D)) {
            String name = COLOR_MEMO.computeIfAbsent(rgb, NameTags::nearestEffect);
            if (name != null) {
                cache.put(name, now + PARTICLE_TTL_MS);
            }
        }
        cache.entrySet().removeIf(entry -> entry.getValue() <= now);
        if (cache.isEmpty()) {
            particleCache.remove(player.getUuid());
            return "";
        }
        List<String> names = new ArrayList<>(cache.keySet());
        names.sort(String::compareTo);
        return "~" + String.join(" ", names.subList(0, Math.min(3, names.size())));
    }

    private static String nearestEffect(int rgb) {
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (Map.Entry<Integer, String> entry : colorTable().entrySet()) {
            int dr = ((rgb >> 16) & 0xFF) - ((entry.getKey() >> 16) & 0xFF);
            int dg = ((rgb >> 8) & 0xFF) - ((entry.getKey() >> 8) & 0xFF);
            int db = (rgb & 0xFF) - (entry.getKey() & 0xFF);
            int distance = Math.abs(dr) + Math.abs(dg) + Math.abs(db);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = entry.getValue();
            }
        }
        return bestDistance <= 90 ? best : null;
    }
    /** 诊断用：元数据里带的效果粒子数量。 */
    private static int swirlCount(PlayerEntity player) {
        List<net.minecraft.particle.ParticleEffect> swrils =
                player.getDataTracker().get(net.minecraft.entity.LivingEntity.POTION_SWIRLS);
        return swrils == null ? -1 : swrils.size();
    }
    private static Map<Integer, String> colorTable;

    private static String metadataEffects(PlayerEntity player) {
        List<net.minecraft.particle.ParticleEffect> swrils =
                player.getDataTracker().get(net.minecraft.entity.LivingEntity.POTION_SWIRLS);
        if (swrils == null || swrils.isEmpty()) {
            return "";
        }
        Map<Integer, String> table = colorTable();
        List<String> names = new ArrayList<>(2);
        for (net.minecraft.particle.ParticleEffect effect : swrils) {
            if (!(effect instanceof net.minecraft.particle.TintedParticleEffect tinted)) {
                continue;
            }
            int red = Math.round(tinted.getRed() * 255.0F);
            int green = Math.round(tinted.getGreen() * 255.0F);
            int blue = Math.round(tinted.getBlue() * 255.0F);
            String name = table.get((red << 16) | (green << 8) | blue);
            if (name != null && !names.contains(name)) {
                names.add(name);
            }
        }
        if (names.isEmpty()) {
            return "";
        }
        names.sort(String::compareTo);
        StringBuilder builder = new StringBuilder("~");
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) {
                builder.append(' ');
            }
            builder.append(names.get(i));
        }
        return builder.toString();
    }

    private static Map<Integer, String> colorTable() {
        if (colorTable != null) {
            return colorTable;
        }
        Map<Integer, String> map = new HashMap<>();
        for (var effect : Registries.STATUS_EFFECT) {
            map.putIfAbsent(effect.getColor() & 0xFFFFFF, effect.getName().getString());
        }
        colorTable = map;
        return map;
    }
    /**
     * 剩余时长：用原版 {@link net.minecraft.util.StringHelper#formatTicks} 的格式
     * （和药水提示框一致，会按 tick 速率换算，`/tick` 调速也准）。无限时长返回空串。
     */
    private static String remaining(int ticks) {
        if (ticks < 0 || ticks > 20 * 60 * 60) {
            return "";
        }
        MinecraftClient client = MinecraftClient.getInstance();
        float rate = client.world == null ? 20.0F : client.world.getTickManager().getTickRate();
        return net.minecraft.util.StringHelper.formatTicks(ticks, rate);
    }
    private static int rank(StatusEffectInstance effect) {
        StatusEffectCategory category = effect.getEffectType().value().getCategory();
        return switch (category) {
            case BENEFICIAL -> 2;
            case NEUTRAL -> 1;
            case HARMFUL -> 0;
        };
    }

    private static String roman(int value) {
        return switch (value) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> String.valueOf(value);
        };
    }

    private static int latency(MinecraftClient client, PlayerEntity player) {
        ClientPlayNetworkHandler handler = client.getNetworkHandler();
        if (handler == null) {
            return -1;
        }
        PlayerListEntry entry = handler.getPlayerListEntry(player.getUuid());
        return entry == null ? -1 : entry.getLatency();
    }

    /** 血量条颜色：满血偏绿，残血偏红。 */
    private static int healthColor(float ratio) {
        ratio = Math.clamp(ratio, 0, 1);
        int red = Math.min(255, (int) (255 * (1.0F - ratio)));
        int green = Math.min(255, (int) (200 * ratio) + 40);
        return 0xFF000000 | (red << 16) | (green << 8) | 0x40;
    }
}
