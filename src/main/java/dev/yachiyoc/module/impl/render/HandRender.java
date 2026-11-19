package dev.yachiyoc.module.impl.render;

import dev.yachiyoc.YachiyoC;
import dev.yachiyoc.module.Module;
import dev.yachiyoc.module.ModuleCategory;
import dev.yachiyoc.module.setting.BoolSetting;
import dev.yachiyoc.module.setting.DoubleSetting;
import dev.yachiyoc.render.Render;
import dev.yachiyoc.render.Render3DCallbacks;
import dev.yachiyoc.render.balloon.BalloonPhysics;
import dev.yachiyoc.render.v1_21_11.BalloonWrist;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public final class HandRender extends Module {
    private static final String ID = "hand_render";
    private static final int BURST_TICKS = 12;

    private final BoolSetting balloon = add(new BoolSetting(ID, "balloon", true));
    private final DoubleSetting size = add(new DoubleSetting(ID, "balloon_size", 0.48, 0.20, 1.60, 0.01));
    private final DoubleSetting ropeLength = add(new DoubleSetting(ID, "rope_length", 1.08, 0.20, 2.50, 0.01));
    private final BoolSetting canPop = add(new BoolSetting(ID, "can_pop", true));
    private final DoubleSetting respawnDelay = add(new DoubleSetting(ID, "respawn_delay", 5, 0, 90, 0.1));

    private ClientWorld world;
    private ClientPlayerEntity player;
    private BalloonPhysics physics;
    private Burst burst;
    private int ticks;
    private int poppedAt = -1;

    public HandRender() {
        super(ID, ModuleCategory.RENDER);
        group("balloon", balloon, size, ropeLength, canPop, respawnDelay);
        for (var setting : List.of(size, canPop, respawnDelay)) {
            setting.description(() -> Text.translatable(setting.translationKey() + ".description"));
        }
        BalloonWrist.register();
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        Render3DCallbacks.register(this::render);
    }

    public static HandRender active() {
        if (YachiyoC.modules() != null && YachiyoC.modules().byId(ID) instanceof HandRender render
                && render.enabled() && render.balloon.get()) return render;
        return null;
    }

    public boolean tetherVisible() {
        return physics != null;
    }

    @Override
    public void onDisable() {
        clear();
    }

    private void clear() {
        world = null;
        player = null;
        physics = null;
        burst = null;
        ticks = 0;
        poppedAt = -1;
        BalloonWrist.clear();
    }

    private void tick(MinecraftClient client) {
        if (!enabled() || !balloon.get() || client.world == null || client.player == null
                || !client.player.isAlive() || client.player.isSpectator()) {
            if (world != null) clear();
            return;
        }
        if (world != client.world || player != client.player) {
            clear();
            world = client.world;
            player = client.player;
        }
        ticks++;
        if (burst != null && ticks - burst.started() > BURST_TICKS) burst = null;
        if (poppedAt >= 0 && ticks - poppedAt < Math.round(respawnDelay.get() * 20)) return;
        poppedAt = -1;

        Vec3d forward = Vec3d.fromPolar(0, player.getBodyYaw());
        Vec3d wrist = restWrist(player, forward);
        float wristFollow = client.options.getPerspective().isFirstPerson() ? 0.45F : 0.8F;
        wrist = wrist.lerp(BalloonWrist.displayPosition(client, wrist), wristFollow);
        double radius = size.get() / 2;
        double length = ropeLength.get();
        if (physics == null || physics.anchor().squaredDistanceTo(wrist) > 5.0) {
            physics = new BalloonPhysics(wrist, forward, radius, length);
        }
        physics.tick(wrist, forward, radius, length, obstacles(physics.bounds(wrist, radius, length)));
    }

    private static Vec3d restWrist(ClientPlayerEntity player, Vec3d forward) {
        double side = player.getMainArm() == Arm.RIGHT ? 0.34 : -0.34;
        double swing = Math.sin(Math.PI * player.getHandSwingProgress(1));
        Vec3d sideOffset = new Vec3d(-forward.z * side, 0, forward.x * side);
        return player.getEyePos().add(sideOffset).add(forward.multiply(0.27 + 0.22 * swing)).add(0, -0.56, 0);
    }

    private List<Box> obstacles(Box bounds) {
        List<Box> boxes = new ArrayList<>();
        for (var shape : world.getBlockCollisions(player, bounds)) boxes.addAll(shape.getBoundingBoxes());
        for (var entity : world.getOtherEntities(player, bounds, entity -> entity.isAlive() && entity.isPushable())) {
            boxes.add(entity.getBoundingBox());
        }
        boxes.add(player.getBoundingBox().contract(0.035));
        return boxes;
    }

    private void render() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!enabled() || !balloon.get() || world == null || client.world != world || client.player != player
                || !player.isAlive() || player.isSpectator()) return;
        var render = Render.api();
        float delta = render.tickDelta();
        if (physics != null) {
            var frame = physics.frame(delta);
            Vec3d wrist = BalloonWrist.displayPosition(client, frame.rope()[0]);
            render.balloon(frame, wrist, light(frame.center()));
        }
        if (burst != null) {
            float age = (ticks - burst.started() + delta) / 20.0F;
            render.balloonBurst(burst.center(), burst.radius(), age, light(burst.center()));
        }
    }

    private float light(Vec3d position) {
        return 0.39F + world.getLightLevel(BlockPos.ofFloored(position)) * (0.61F / 15);
    }

    /** Consumes only a visible hit on the local balloon before the ordinary attack target. */
    public boolean attack(MinecraftClient client) {
        if (physics == null || world != client.world || player != client.player || client.getCameraEntity() != player
                || client.attackCooldown > 0 || !player.isAlive() || player.isSpectator()) return false;
        boolean firstPerson = client.options.getPerspective().isFirstPerson();
        float delta = client.getRenderTickCounter().getTickProgress(true);
        Vec3d start = firstPerson ? client.gameRenderer.getCamera().getCameraPos() : player.getCameraPosVec(delta);
        Vec3d direction = player.getRotationVec(delta);
        double reach = player.getEntityInteractionRange();
        HitResult target = client.crosshairTarget;
        if (target != null && target.getType() != HitResult.Type.MISS) reach = Math.min(reach, start.distanceTo(target.getPos()));
        var frame = physics.frame(delta);
        if (!Double.isFinite(frame.hit(start, direction, reach))) return false;

        // The two-argument vanilla swing updates the local view without the ClientPlayerEntity packet override.
        player.swingHand(Hand.MAIN_HAND, false);
        if (!canPop.get()) {
            physics.swat(direction);
        } else {
            burst = new Burst(frame.center(), frame.radius(), ticks);
            physics = null;
            poppedAt = ticks;
            Vec3d center = frame.center();
            client.getSoundManager().play(new PositionedSoundInstance(SoundEvents.BLOCK_BUBBLE_COLUMN_BUBBLE_POP,
                    SoundCategory.PLAYERS, 0.95F, 1.28F, player.getRandom(), center.x, center.y, center.z));
        }
        return true;
    }

    private record Burst(Vec3d center, double radius, int started) { }
}
