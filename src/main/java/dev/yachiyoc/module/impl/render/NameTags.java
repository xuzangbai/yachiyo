package dev.yachiyoc.module.impl.render;

import dev.yachiyoc.module.Module;
import dev.yachiyoc.module.ModuleCategory;
import dev.yachiyoc.module.setting.BoolSetting;
import dev.yachiyoc.module.setting.DoubleSetting;
import dev.yachiyoc.platform.Debug;
import dev.yachiyoc.platform.EffectParticles;
import dev.yachiyoc.render.Render;
import dev.yachiyoc.ui.render.UiTextures;
import dev.yachiyoc.render.Render3DCallbacks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
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
    /** 文字行高（方块）：0.025 是原版名牌的字号，乘以 10 行距。 */
    private static final float LINE = 0.025F * 10.0F;
    /** 药水最多显示几个，避免一排标签糊满屏幕。 */
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
    /** 装备图标大小（方块）。 */
    private final DoubleSetting equipScale =
            add(new DoubleSetting("nametags", "equip_scale", 0.5, 0.2, 1.5, 0.05));
    /** 装备总开关（默认关）。 */
    private final BoolSetting equipment =
            add(new BoolSetting("nametags", "equipment", false));
    /** 图标上方显示装备名字。 */
    private final BoolSetting equipName =
            add(new BoolSetting("nametags", "equip_name", true));
    /** 图标下方显示附魔。 */
    private final BoolSetting equipEnchant =
            add(new BoolSetting("nametags", "equip_enchant", true));
    /** 神龟图标大小（单位：行高，1 行高 ≈ 0.25 格）。 */
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

    public NameTags() {
        super("nametags", ModuleCategory.RENDER, true);
        healthBar.visibleWhen(health::get);
    }

    @Override
    public void onEnable() {
        Render3DCallbacks.register(renderTask);
    }

    @Override
    public void onDisable() {
        Render3DCallbacks.unregister(renderTask);
    }

    private void renderNameTags() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity me = client.player;
        if (me == null || client.world == null || !Render.api().inWorld()) {
            return;
        }
        Vec3d cameraPos = Render.api().cameraPos();
        double maxRange = range.get();
        List<? extends PlayerEntity> players = client.world.getPlayers().stream()
                .filter(player -> player != me || self.get())
                .filter(player -> invisible.get() || !player.isInvisible())
                .filter(player -> throughWalls.get() || me.canSee(player))
                .filter(player -> player.squaredDistanceTo(cameraPos) <= maxRange * maxRange)
                .sorted(Comparator.comparingDouble(player -> -player.squaredDistanceTo(cameraPos)))
                .toList();

        if (Debug.enabled("nametags")) {
            StringBuilder summary = new StringBuilder();
            for (PlayerEntity player : players) {
                summary.append(' ').append(player.getName().getString())
                        .append('=').append(player.getStatusEffects().size());
            }
            Debug.logThrottled("nametags", 2000L, "labels=%d%s", players.size(), summary.toString());
        }

        for (PlayerEntity player : players) {
            draw(client, me, player);
        }
    }

    private void draw(MinecraftClient client, ClientPlayerEntity me, PlayerEntity player) {
        float zoom = scale.get().floatValue();
        // 用 tickDelta 插值：直接拿 getX() 是每 tick 跳一次，走起来会一抽一抽
        float td = Render.api().tickDelta();
        Vec3d lerped = player.getLerpedPos(td);
        Vec3d anchor = new Vec3d(lerped.x,
                lerped.y + player.getHeight() + 0.42D + height.get(), lerped.z);

        List<Text> lines = new ArrayList<>(3);
        lines.add(Text.literal(player.getName().getString()));
        String info = infoLine(client, player);
        if (!info.isEmpty()) {
            lines.add(Text.literal(info));
        }
        String effects = potions.get() ? effectsLine(player) : "";
        if (effects.isEmpty() && potions.get()) {
            // 效果包没有 → 读元数据；元数据也没有 → 用身上冒的粒子兜底
            effects = metadataEffects(player);
            if (effects.isEmpty()) {
                effects = particleEffects(player, lerped);
            }
        }
        if (!effects.isEmpty()) {
            lines.add(Text.literal(effects));
        }

        if (Debug.enabled("nametags")) {
            Vec3d screen = Render.api().project(anchor);
            Debug.logThrottled("nametags", 2000L, "label %s anchor=(%.1f, %.1f, %.1f) screen=%s lines=%d",
                    player.getName().getString(), anchor.x, anchor.y, anchor.z,
                    screen == null ? "behind" : String.format(Locale.ROOT, "%.3f, %.3f",
                            screen.x, screen.y),
                    lines.size());
        }
        boolean bar = healthBar.get();
        float lineHeight = LINE * zoom;
        float widest = 0.0F;
        for (Text line : lines) {
            widest = Math.max(widest, Render.api().textWidth(line, zoom));
        }
        float contentWidth = widest + 0.03F;
        float contentHeight = lineHeight * lines.size() + (bar ? lineHeight * 0.45F : 0.0F) + 0.012F;

        if (equipment.get()) {
            drawEquipment(player, anchor, lineHeight, zoom);
        }
        if (background.get()) {
            Vec3d boardCenter = anchor.add(0.0D,
                    -(contentHeight / 2.0F - lineHeight / 2.0F), 0.0D);
            Render.api().billboardRect(boardCenter, contentWidth, contentHeight, 0x66000000);
        }

        // 神龟药水：紧贴标签上方挂一个图标（每 ICON_ROLL_MS 重摇一次）
        // 敌人拿不到效果包，所以除了真实数据，也要认"推断出来的抗性提升"
        if (potions.get() && (isTurtleMaster(player) || isInferredTurtle(effects))) {
            float size = lineHeight * turtleSize.get().floatValue();
            Render.api().billboardImage(turtleIcon(player),
                    anchor.add(0.0D, lineHeight * 0.55D + size * 0.5D, 0.0D), size, size);
        }

        for (int i = 0; i < lines.size(); i++) {
            int color = i == 0 ? 0xFFFFFFFF : (i == 1 ? 0xFFD6D6D6 : 0xFFB9C7FF);
            Render.api().text(lines.get(i), anchor.add(0.0D, -lineHeight * i, 0.0D), zoom,
                    color, true, throughWalls.get());
        }

        if (bar) {
            float maxHealth = Math.max(1.0F, player.getMaxHealth());
            float health = Math.max(0.0F, player.getHealth());
            // 伤害吸收是额外的黄心，不算在 getHealth() 里：总长度按「血量 + 吸收」算，
            // 这样 20 血 + 8 吸收就是 28 点，红段 71%、黄段 29%
            float absorption = Math.max(0.0F, player.getAbsorptionAmount());
            float total = maxHealth + absorption;
            float healthRatio = Math.min(1.0F, health / total);
            float absorbRatio = Math.min(1.0F - healthRatio, absorption / total);

            float barWidth = contentWidth * 0.94F;
            float barHeight = lineHeight * 0.28F;
            double barY = anchor.y - lineHeight * lines.size() - barHeight * 0.5D;
            // 三层共面会深度打架：底板在最里，红、黄依次往相机方向挪 0.02 / 0.04 格（5mm 不够，深度精度会抖）
            Vec3d barPos = new Vec3d(anchor.x, barY, anchor.z);
            float barLeft = -barWidth / 2.0F;
            Render.api().billboardRect(barPos, barWidth, barHeight, 0x99000000);
            if (healthRatio > 0.01F) {
                float center = barLeft + barWidth * healthRatio / 2.0F;
                Render.api().billboardRect(
                        Render.api().billboardOffset(barPos, center, 0.0F, 0.02F),
                        barWidth * healthRatio, barHeight, healthColor(healthRatio));
            }
            if (absorbRatio > 0.01F) {
                // 黄段左端 = 红段右端，不能重叠
                float center = barLeft + barWidth * healthRatio + barWidth * absorbRatio / 2.0F;
                Render.api().billboardRect(
                        Render.api().billboardOffset(barPos, center, 0.0F, 0.04F),
                        barWidth * absorbRatio, barHeight, 0xFFFFD24A);
            }
        }
    }

    /**
     * 「神龟药水」在原版里不是一个独立效果，而是 **抗性提升 + 缓慢** 同时存在
     * （Turtle Master = Resistance III + Slowness IV），所以按这两者判定。
     */
    /**
     * 装备显示：一排物品图标（上方是名字、下方是附魔），照着 Alien / Hachimi 的布局来。
     * 图标用 {@code RenderApi.item()} 画真实 3D 模型，文字用 billboard 贴在图标上下。
     */
    private void drawEquipment(PlayerEntity player, Vec3d anchor, float lineHeight, float zoom) {
        List<ItemStack> items = new ArrayList<>(6);
        for (EquipmentSlot slot : EQUIPMENT_ORDER) {
            ItemStack stack = player.getEquippedStack(slot);
            if (!stack.isEmpty()) {
                items.add(stack);
            }
        }
        if (items.isEmpty()) {
            return;
        }
        float icon = equipScale.get().floatValue();
        float gapX = equipGapX.get().floatValue();
        float gapY = equipGapY.get().floatValue();
        float spacing = icon * gapX;
        float textScale = zoom * 0.8F;
        float rowY = (float) anchor.y + lineHeight * 0.5F * gapY + icon * 0.75F
                + lineHeight * 0.9F * gapY;
        Vec3d row = new Vec3d(anchor.x, rowY, anchor.z);

        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            float offset = (i - (items.size() - 1) / 2.0F) * spacing;
            Vec3d pos = Render.api().billboardOffset(row, offset, 0.0F);
            Render.api().item(stack, pos, icon);

            // 名字在图标**下方**
            if (equipName.get()) {
                Text name = stack.get(DataComponentTypes.CUSTOM_NAME);
                if (name == null) {
                    name = stack.getName();
                }
                Render.api().text(name,
                        Render.api().billboardOffset(pos, 0.0F, -(icon * 0.55F + lineHeight * 0.7F) * gapY),
                        textScale, 0xFFFFFFFF, true, throughWalls.get());
            }

            // 附魔在图标**上方**，逐行往上堆
            if (!equipEnchant.get()) {
                continue;
            }
            ItemEnchantmentsComponent enchantments = EnchantmentHelper.getEnchantments(stack);
            float enchantY = (icon * 0.55F + lineHeight * 0.6F) * gapY;
            for (RegistryEntry<Enchantment> entry : enchantments.getEnchantments()) {
                int level = enchantments.getLevel(entry);
                String label = entry.value().description().getString() + (level > 1 ? roman(level) : "");
                Render.api().text(Text.literal(label),
                        Render.api().billboardOffset(pos, 0.0F, enchantY),
                        textScale, 0xFFB9C7FF, true, throughWalls.get());
                enchantY += lineHeight * 0.85F * gapY;
            }
        }
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
    private String infoLine(MinecraftClient client, PlayerEntity player) {
        StringBuilder builder = new StringBuilder();
        if (health.get()) {
            builder.append(String.format(Locale.ROOT, "%.1f", player.getHealth()));
            float absorption = player.getAbsorptionAmount();
            if (absorption > 0.0F) {
                builder.append('+').append(String.format(Locale.ROOT, "%.1f", absorption));
            }
        }
        if (distance.get()) {
            if (!builder.isEmpty()) {
                builder.append("  ");
            }
            builder.append(String.format(Locale.ROOT, "%.1fm", player.distanceTo(client.player)));
        }
        if (ping.get()) {
            int latency = latency(client, player);
            if (latency > 0) {
                if (!builder.isEmpty()) {
                    builder.append("  ");
                }
                builder.append(latency).append("ms");
            }
        }
        return builder.toString();
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
        int red = Math.min(255, (int) (255 * (1.0F - ratio)));
        int green = Math.min(255, (int) (200 * ratio) + 40);
        return 0xFF000000 | (red << 16) | (green << 8) | 0x40;
    }
}
