package dev.yachiyoc.module.impl.misc;

import dev.yachiyoc.module.Module;
import dev.yachiyoc.module.ModuleCategory;
import dev.yachiyoc.module.setting.BoolSetting;
import dev.yachiyoc.platform.Chat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * Via 修复：所有和 Via 协议翻译相关的客户端侧修正集中在这里。
 *
 * <p>包含两件事：
 * <ul>
 *   <li><b>长矛修复</b>：老版本服务端没有 1.21.11 的长矛，Via 会把长矛降级成同材质的剑，并把原始物品
 *       id 写进 {@code custom_data}。开启后按真长矛渲染（模型 / 第三人称持矛姿势），并恢复长矛的
 *       使用动作与蓄力时长。</li>
 *   <li><b>坏包防踢</b>：Via 翻译某些包（典型是带未知附魔 id 的 {@code system_chat}）会让客户端解码
 *       失败，{@code DecoderException} 会一路冒到 {@code ClientConnection#exceptionCaught} 把连接关掉。
 *       开启后只丢这一个包，不断线。</li>
 * </ul>
 *
 * <p>移植自 NoDecodeKick（MIT License, Copyright (c) 2026 matl114）。
 */
public final class ViaFix extends Module {
    private static final long NOTIFY_INTERVAL_MS = 1000L;

    private static ViaFix instance;
    private static long lastNotifyMs;

    /** 物品模型 + 第三人称持矛手臂姿势。 */
    public final BoolSetting spearModel = add(new BoolSetting("via_fix", "spear_model", true));

    /** 使用动作（UseAction）与最长使用时间，让长矛能正常蓄力。 */
    public final BoolSetting spearUseAction = add(new BoolSetting("via_fix", "spear_use_action", true));

    /** 包解码失败时只丢这一个包，不再断开连接。 */
    public final BoolSetting badPacket = add(new BoolSetting("via_fix", "bad_packet", true));

    /** 拦下坏包时在聊天栏提示一次。 */
    public final BoolSetting notify = add(new BoolSetting("via_fix", "notify", true));

    public ViaFix() {
        super("via_fix", ModuleCategory.MISC, true);
        instance = this;
        spearModel.description(() -> Text.translatable("setting.yachiyoc.via_fix.spear_model.description"));
        spearUseAction.description(() -> Text.translatable("setting.yachiyoc.via_fix.spear_use_action.description"));
        badPacket.description(() -> Text.translatable("setting.yachiyoc.via_fix.bad_packet.description"));
        notify.description(() -> Text.translatable("setting.yachiyoc.via_fix.notify.description"));
    }

    /** 是否修复长矛模型 / 持矛姿势。 */
    public static boolean fixSpearModel() {
        return instance != null && instance.enabled() && instance.spearModel.get();
    }

    /** 是否修复长矛使用动作 / 蓄力时长。 */
    public static boolean fixSpearUseAction() {
        return instance != null && instance.enabled() && instance.spearUseAction.get();
    }

    /** 是否拦掉解码失败导致的断线。 */
    public static boolean guardBadPackets() {
        return instance != null && instance.enabled() && instance.badPacket.get();
    }

    /** 询问式提示，走主线程，1 秒内只提示一次（一次断线风暴会有很多条）。 */
    public static void warnDroppedPacket(String reason) {
        if (instance == null || !instance.enabled() || !instance.badPacket.get() || !instance.notify.get()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastNotifyMs < NOTIFY_INTERVAL_MS) {
            return;
        }
        lastNotifyMs = now;
        MinecraftClient client = MinecraftClient.getInstance();
        Text message = Text.translatable("module.yachiyoc.via_fix.dropped",
                reason == null ? "?" : reason);
        client.execute(() -> Chat.info(message));
    }
}
