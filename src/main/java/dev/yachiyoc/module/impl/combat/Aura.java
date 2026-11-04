package dev.yachiyoc.module.impl.combat;

import dev.yachiyoc.combat.AttackScheduler;
import dev.yachiyoc.combat.TargetSelector;
import dev.yachiyoc.module.Module;
import dev.yachiyoc.module.ModuleCategory;
import dev.yachiyoc.module.setting.BoolSetting;
import dev.yachiyoc.module.setting.DoubleSetting;
import dev.yachiyoc.module.setting.EnumSetting;
import dev.yachiyoc.module.setting.RegistryListSetting;
import dev.yachiyoc.platform.RegistryKind;
import dev.yachiyoc.rotation.RotationChoice;
import dev.yachiyoc.rotation.RotationManager;
import dev.yachiyoc.rotation.RotationMode;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Set;

/** Module entrypoint only; target and attack logic lives in combat/. */
public final class Aura extends Module {
    private static final List<String> DEFAULT_TARGETS = List.of(
            "minecraft:player",
            "minecraft:zombie",
            "minecraft:skeleton",
            "minecraft:creeper",
            "minecraft:spider",
            "minecraft:enderman",
            "minecraft:witch",
            "minecraft:slime",
            "minecraft:blaze",
            "minecraft:pillager",
            "minecraft:vindicator",
            "minecraft:ravager");

    private final DoubleSetting range =
            add(new DoubleSetting("aura", "range", 3.0, 1.0, 6.0, 0.1));
    private final RegistryListSetting targets = add(new RegistryListSetting(
            "aura", "targets", RegistryKind.ENTITY_TARGET, DEFAULT_TARGETS));
    private final BoolSetting throughWalls =
            add(new BoolSetting("aura", "through_walls", false));
    private final EnumSetting<RotationChoice> rotation = add(new EnumSetting<>(
            "aura", "rotation", RotationChoice.class, RotationChoice.FOLLOW));

    public Aura() {
        super("aura", ModuleCategory.COMBAT, true);

        targets.description(() -> net.minecraft.text.Text.translatable(
                "setting.yachiyoc.aura.targets.description"));
        rotation.description(() -> net.minecraft.text.Text.translatable(
                "setting.yachiyoc.aura.rotation.description"));
        RotationManager.initialize();
        ClientTickEvents.START_CLIENT_TICK.register(this::tick);
    }

    @Override
    public void onDisable() {
        RotationManager.clearAim();
    }

    private void tick(MinecraftClient client) {
        if (!enabled() || client.player == null || client.world == null
                || client.currentScreen != null || client.interactionManager == null) {
            return;
        }
        ClientPlayerEntity player = client.player;
        Entity target = TargetSelector.findBest(client, player,
                new TargetSelector.Filter(range.get(), Set.copyOf(targets.selected()),
                        throughWalls.get(), false));
        if (target == null) {
            RotationManager.clearAim();
            return;
        }

        Vec3d eye = player.getEyePos();
        Vec3d point = target.getBoundingBox().getCenter();
        float[] angles = RotationManager.anglesTo(point.x - eye.x, point.y - eye.y, point.z - eye.z);
        if (!AttackScheduler.canAttack(player)) {
            return;
        }
        RotationMode mode = RotationManager.resolve(rotation.get());
        RotationManager.run(mode, angles[0], angles[1],
                () -> AttackScheduler.attack(client, player, target));
    }
}
