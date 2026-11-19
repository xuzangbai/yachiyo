package dev.yachiyoc.render.v1_21_11;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.yachiyoc.mixin.GameRendererAccessor;
import dev.yachiyoc.module.impl.render.HandRender;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/** Captures the main wrist from the rendered arm and maps it into the world projection. */
public final class BalloonWrist {
    private static final long SAMPLE_LIFETIME = 300_000_000L;
    static final double WRAP_DEPTH = 0.145;
    private static final Vec3d BACK_OF_WRIST = new Vec3d(0, 0, 1);
    private static Vec3d firstPerson;
    private static long firstTime;
    private static Arm firstArm;
    private static Vec3d firstBowFace;
    private static Vec3d thirdPerson;
    private static long thirdTime;

    private BalloonWrist() { }

    public static void register() {
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
            if (renderer instanceof PlayerEntityRenderer<?> playerRenderer) helper.register(new WristFeature(playerRenderer));
        });
    }

    public static void clear() {
        firstPerson = thirdPerson = null;
        firstArm = null;
        firstBowFace = null;
    }

    public static void captureArm(MinecraftClient client, Arm arm, ModelPart part, MatrixStack matrices,
                                  OrderedRenderCommandQueue queue, int light) {
        HandRender hand = firstPersonArm(client, arm);
        if (hand == null) return;
        boolean slim = isSlim(client);
        matrices.push();
        try {
            part.applyTransform(matrices);
            Vec3d center = wristCenter(arm, slim).add(0, 0.048, 0);
            if (firstArm != arm || firstBowFace == null) {
                firstArm = arm;
                firstBowFace = visibleFace(matrices, center);
            }
            double across = slim ? 0.114 : 0.145;
            double depth = (firstBowFace.x == 0 ? WRAP_DEPTH : across) + 0.012;
            captureView(client, matrices, center.add(firstBowFace.multiply(depth)));
            if (hand.tetherVisible()) submitBow(matrices, queue, center, across, firstBowFace, depth, true, light);
        } finally {
            matrices.pop();
        }
    }

    public static void captureItem(MinecraftClient client, Arm arm, MatrixStack matrices,
                                   OrderedRenderCommandQueue queue, int light) {
        HandRender hand = firstPersonArm(client, arm);
        if (hand == null) return;
        float side = arm == Arm.RIGHT ? 1 : -1;
        Vec3d grip = new Vec3d(0.09F * side, -0.15F, 0.115F);
        Vec3d face = visibleFace(matrices, grip);
        captureView(client, matrices, grip.add(face.multiply(0.015)));
        if (hand.tetherVisible()) submitBow(matrices, queue, grip, 0, face, 0.015, false, light);
    }

    private static HandRender firstPersonArm(MinecraftClient client, Arm arm) {
        if (client.player != null && client.getCameraEntity() == client.player
                && client.options.getPerspective().isFirstPerson() && client.player.getMainArm() == arm) return HandRender.active();
        return null;
    }

    private static Vec3d wristCenter(Arm arm, boolean slim) {
        double x = slim ? 0.03125 : 0.0625;
        return new Vec3d(arm == Arm.RIGHT ? -x : x, 0.45, 0);
    }

    private static Vector3f transform(MatrixStack matrices, Vec3d position) {
        return matrices.peek().getPositionMatrix().transformPosition(position.toVector3f());
    }

    private static Vec3d visibleFace(MatrixStack matrices, Vec3d center) {
        var view = RenderSystem.getModelViewStack();
        var model = matrices.peek().getPositionMatrix();
        Vector3f towardEye = view.transformPosition(transform(matrices, center)).negate();
        Vector3f x = view.transformDirection(model.transformDirection(1, 0, 0, new Vector3f())).normalize();
        Vector3f z = view.transformDirection(model.transformDirection(0, 0, 1, new Vector3f())).normalize();
        double xFacing = towardEye.dot(x);
        double zFacing = towardEye.dot(z);
        return Math.abs(xFacing) > Math.abs(zFacing) ? new Vec3d(Math.copySign(1, xFacing), 0, 0)
                : new Vec3d(0, 0, Math.copySign(1, zFacing));
    }

    private static void submitBow(MatrixStack matrices, OrderedRenderCommandQueue queue, Vec3d center,
                                  double across, Vec3d face, double depth, boolean wrap, int packedLight) {
        int brightness = Math.max((packedLight >>> 4) & 15, (packedLight >>> 20) & 15);
        float light = 0.48F + brightness * (0.52F / 15);
        queue.submitCustom(matrices, BalloonMesh.LAYER,
                (entry, buffer) -> BalloonMesh.drawWrist(entry, buffer, center, across, face, depth, wrap, light));
    }

    private static void captureView(MinecraftClient client, MatrixStack matrices, Vec3d position) {
        Vector3f point = RenderSystem.getModelViewStack().transformPosition(transform(matrices, position));
        if (point.z >= -0.07F) return;
        var camera = client.gameRenderer.getCamera();
        var renderer = (GameRendererAccessor) client.gameRenderer;
        float delta = client.getRenderTickCounter().getTickProgress(true);
        double worldFov = renderer.yachiyoc$getFov(camera, delta, true);
        double handFov = renderer.yachiyoc$getFov(camera, delta, false);
        double ratio = Math.tan(Math.toRadians(worldFov / 2)) / Math.tan(Math.toRadians(handFov / 2));
        firstPerson = new Vec3d(point.x * ratio, point.y * ratio, point.z);
        firstTime = System.nanoTime();
    }

    public static Vec3d displayPosition(MinecraftClient client, Vec3d fallback) {
        Vec3d sample = null;
        long time = System.nanoTime();
        if (client.options.getPerspective().isFirstPerson()) {
            if (!client.options.hudHidden && firstPerson != null && time - firstTime < SAMPLE_LIFETIME) {
                var camera = client.gameRenderer.getCamera();
                Vector3f offset = camera.getRotation().transform(firstPerson.toVector3f());
                sample = camera.getCameraPos().add(offset.x, offset.y, offset.z);
            }
        } else if (thirdPerson != null && time - thirdTime < SAMPLE_LIFETIME) {
            sample = thirdPerson;
        }
        return sample != null && sample.squaredDistanceTo(fallback) < 2.25 ? sample : fallback;
    }

    private static final class WristFeature extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {
        private WristFeature(PlayerEntityRenderer<?> renderer) {
            super(renderer);
        }

        @Override
        public void render(MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
                           PlayerEntityRenderState state, float limbAngle, float limbDistance) {
            MinecraftClient client = MinecraftClient.getInstance();
            HandRender hand = HandRender.active();
            if (hand == null || client.player == null || state.id != client.player.getId()
                    || client.options.getPerspective().isFirstPerson()) return;
            var model = getContextModel();
            model.setAngles(state);
            Arm arm = client.player.getMainArm();
            boolean slim = isSlim(client);
            matrices.push();
            try {
                model.getArm(arm).applyTransform(matrices);
                Vec3d center = wristCenter(arm, slim);
                double depth = WRAP_DEPTH + 0.012;
                Vector3f point = transform(matrices, center.add(BACK_OF_WRIST.multiply(depth)));
                thirdPerson = client.gameRenderer.getCamera().getCameraPos().add(point.x, point.y, point.z);
                thirdTime = System.nanoTime();
                if (hand.tetherVisible()) submitBow(matrices, queue, center, slim ? 0.114 : 0.145,
                        BACK_OF_WRIST, depth, true, light);
            } finally {
                matrices.pop();
            }
        }
    }

    private static boolean isSlim(MinecraftClient client) {
        return client.player.getSkin().model() == net.minecraft.entity.player.PlayerSkinType.SLIM;
    }
}
