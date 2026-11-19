package dev.yachiyoc.render.v1_21_11;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.yachiyoc.render.balloon.BalloonPhysics;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

final class BalloonMesh {
    private static final int LATITUDES = 24;
    private static final int LONGITUDES = 36;
    private static final int TUBE_SIDES = 7;
    private static final double THREAD = 0.0045;
    private static final double TETHER = 0.0037;
    private static final double TAU = Math.PI * 2;
    private static final Vec3d[] DIRECTIONS = createDirections();
    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("yachiyoc", "pipeline/balloon"))
                    .withVertexShader(Identifier.of("yachiyoc", "core/balloon"))
                    .withFragmentShader(Identifier.of("yachiyoc", "core/balloon"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR_NORMAL, VertexFormat.DrawMode.TRIANGLES)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(true).withCull(false).build());
    static final RenderLayer LAYER = RenderLayer.of("yachiyoc:balloon", RenderSetup.builder(PIPELINE).build());

    private BalloonMesh() { }

    static void draw(MatrixStack.Entry matrix, VertexConsumerProvider consumers, Vec3d camera,
                     BalloonPhysics.Frame frame, Vec3d wrist, float light) {
        VertexConsumer buffer = consumers.getBuffer(LAYER);
        Vec3d center = frame.center().subtract(camera);
        int latex = tint(0xFFAF78E4, light);
        int cord = tint(0x44DBD3E4, light);
        SurfacePoint[] surface = new SurfacePoint[DIRECTIONS.length];
        for (int i = 0; i < surface.length; i++) surface[i] = surfacePoint(frame, center, DIRECTIONS[i]);
        for (int latitude = 0; latitude < LATITUDES; latitude++) {
            for (int longitude = 0; longitude < LONGITUDES; longitude++) {
                int a = latitude * (LONGITUDES + 1) + longitude;
                int b = a + LONGITUDES + 1;
                if (latitude != 0) triangle(buffer, matrix, surface[a], surface[a + 1], surface[b + 1], latex);
                if (latitude != LATITUDES - 1) triangle(buffer, matrix, surface[a], surface[b + 1], surface[b], latex);
            }
        }

        Vec3d[] rope = frame.rope();
        Vec3d correction = wrist.subtract(rope[0]);
        Vec3d[] string = new Vec3d[rope.length];
        string[0] = wrist.subtract(camera);
        for (int i = 1; i < string.length; i++) {
            double transfer = Math.max(0, 1 - i / 4.0);
            string[i] = rope[i].add(correction.multiply(transfer * transfer)).subtract(camera);
        }
        drawTether(buffer, matrix, string, cord);
        Vec3d start = string[string.length - 1];
        double size = frame.radius();
        Vec3d neck = center.add(0, -frame.heightRadius() * 0.945, 0);
        Vec3d tie = start.add(0, size * 0.019, 0);
        tube(buffer, matrix, neck, tie, size * 0.104, size * 0.043, latex);
        tube(buffer, matrix, tie, start.add(0, -size * 0.045, 0), size * 0.043, size * 0.105, latex);
        tube(buffer, matrix, tie.add(-0.017, 0.004, 0), tie.add(0.017, 0.004, 0), THREAD, THREAD, cord);
    }

    private static void drawTether(VertexConsumer buffer, MatrixStack.Entry matrix, Vec3d[] string, int color) {
        Vec3d previous = string[0];
        for (int i = 1; i < string.length - 1; i++) {
            Vec3d bend = string[i];
            Vec3d enter = bend.lerp(string[i - 1], 0.23);
            Vec3d exit = bend.lerp(string[i + 1], 0.23);
            tube(buffer, matrix, previous, enter, TETHER, TETHER, color);
            Vec3d middle = enter.lerp(bend, 0.50).lerp(bend.lerp(exit, 0.50), 0.50);
            tube(buffer, matrix, enter, middle, TETHER, TETHER, color);
            tube(buffer, matrix, middle, exit, TETHER, TETHER, color);
            previous = exit;
        }
        tube(buffer, matrix, previous, string[string.length - 1], TETHER, TETHER, color);
    }

    static void drawWrist(MatrixStack.Entry matrix, VertexConsumer buffer, Vec3d center, double across,
                          Vec3d face, double depth, boolean wrap, float light) {
        int cord = tint(0x58F1DCF9, light);
        if (wrap) {
            Vec3d last = center.add(across, 0, 0);
            for (int i = 1; i <= 32; i++) {
                double angle = TAU * i / 32;
                Vec3d next = center.add(squareCircle(Math.cos(angle)) * across, 0,
                        squareCircle(Math.sin(angle)) * BalloonWrist.WRAP_DEPTH);
                tube(buffer, matrix, last, next, THREAD, THREAD, cord);
                last = next;
            }
        }
        Vec3d knot = center.add(face.multiply(depth));
        Vec3d acrossBow = new Vec3d(face.z, 0, -face.x);
        double scale = wrap ? 1 : 0.66;
        double size = THREAD * 1.22;
        for (int side : new int[]{-1, 1}) {
            double x = side * scale;
            Vec3d bend = bowPoint(knot, acrossBow, face, 0.101 * x, -0.035 * scale, depth * 0.18);
            curve(buffer, matrix, knot, bowPoint(knot, acrossBow, face, 0.052 * x, -0.060 * scale, 0),
                    bowPoint(knot, acrossBow, face, 0.123 * x, -0.078 * scale, depth * 0.14), bend, size, size, cord);
            curve(buffer, matrix, bend, bowPoint(knot, acrossBow, face, 0.107 * x, 0.014 * scale, depth * 0.16),
                    bowPoint(knot, acrossBow, face, 0.040 * x, 0.026 * scale, depth * 0.04), knot, size, size, cord);
            curve(buffer, matrix, knot, bowPoint(knot, acrossBow, face, 0.020 * x, 0.039 * scale, depth * 0.08),
                    bowPoint(knot, acrossBow, face, 0.092 * x, 0.066 * scale, depth * 0.14),
                    bowPoint(knot, acrossBow, face, 0.071 * x, (side == 1 ? 0.108 : 0.080) * scale, depth * 0.12),
                    size, THREAD * 0.62, cord);
        }
        tube(buffer, matrix, knot.add(acrossBow.multiply(-0.011 * scale)).add(0, -0.007, 0),
                knot.add(acrossBow.multiply(0.011 * scale)).add(0, 0.007, 0),
                size * 1.6, size * 1.6, cord);
    }

    private static Vec3d bowPoint(Vec3d knot, Vec3d across, Vec3d face, double x, double y, double lift) {
        return knot.add(across.multiply(x)).add(0, y, 0).add(face.multiply(lift));
    }

    private static double squareCircle(double component) {
        return Math.copySign(Math.pow(Math.abs(component), 0.25), component);
    }

    private static void curve(VertexConsumer buffer, MatrixStack.Entry matrix, Vec3d a, Vec3d b, Vec3d c,
                              Vec3d d, double startRadius, double endRadius, int color) {
        Vec3d previous = a;
        double lastRadius = startRadius;
        for (int i = 1; i <= 9; i++) {
            double t = i / 9.0;
            double s = 1 - t;
            Vec3d next = a.multiply(s * s * s).add(b.multiply(3 * s * s * t))
                    .add(c.multiply(3 * s * t * t)).add(d.multiply(t * t * t));
            double radius = startRadius + (endRadius - startRadius) * t;
            tube(buffer, matrix, previous, next, lastRadius, radius, color);
            lastRadius = radius;
            previous = next;
        }
    }

    private static SurfacePoint surfacePoint(BalloonPhysics.Frame frame, Vec3d center, Vec3d direction) {
        double radius = 1;
        Vec3d normal = direction;
        double strongest = 0;
        for (var contact : frame.contacts()) {
            double approach = -direction.dotProduct(contact.normal());
            if (approach <= 0) continue;
            radius = Math.min(radius, contact.distance() / approach);
            double lip = Math.clamp((approach - contact.distance() + 0.075) / 0.14, 0, 1);
            double blend = lip * lip * (3 - 2 * lip);
            if (blend > strongest) {
                strongest = blend;
                normal = direction.lerp(contact.normal().negate(), blend);
            }
        }
        Vec3d p = direction.multiply(radius * frame.radius(), radius * frame.heightRadius(), radius * frame.radius());
        normal = normal.multiply(1, frame.radius() / frame.heightRadius(), 1).normalize();
        return new SurfacePoint(center.add(p), normal);
    }

    static void drawBurst(MatrixStack.Entry matrix, VertexConsumerProvider consumers, Vec3d camera,
                          Vec3d center, double radius, float age, float light) {
        if (age >= 0.60F) return;
        VertexConsumer buffer = consumers.getBuffer(LAYER);
        Vec3d origin = center.subtract(camera);
        for (int i = 0; i < 22; i++) {
            double y = 1 - (i + 0.5) * (2.0 / 22);
            double angle = i * 2.399963229728653;
            double horizontal = Math.sqrt(1 - y * y);
            Vec3d direction = new Vec3d(horizontal * Math.cos(angle), y, horizontal * Math.sin(angle));
            double travel = radius * 0.81 + (1.25 + (i % 5) * 0.23) * age * (1 - 0.38 * age);
            Vec3d p = origin.add(direction.multiply(travel)).add(0, -2.7 * age * age, 0);
            double spin = angle + age * (11 + i % 7);
            Vec3d axis = new Vec3d(Math.cos(spin), 0.55 * Math.sin(spin * 1.27), Math.sin(spin)).normalize();
            Vec3d side = axis.crossProduct(direction).normalize();
            double strip = (0.05 + radius * 0.24) * Math.max(0, 1 - age / 0.60);
            Vec3d a = p.add(axis.multiply(strip));
            Vec3d b = p.add(side.multiply(strip * 0.32));
            Vec3d c = p.subtract(axis.multiply(strip * 0.70)).add(direction.multiply(strip * 0.28));
            Vec3d d = p.subtract(side.multiply(strip * 0.23));
            int color = tint(i % 4 == 0 ? 0xFFD9B3FF : 0xFFA16CD6, light);
            flatTriangle(buffer, matrix, a, b, d, color);
            flatTriangle(buffer, matrix, b, c, d, color);
        }
    }

    private static Vec3d[] createDirections() {
        Vec3d[] directions = new Vec3d[(LATITUDES + 1) * (LONGITUDES + 1)];
        for (int latitude = 0; latitude <= LATITUDES; latitude++) {
            double polar = latitude * Math.PI / LATITUDES;
            for (int longitude = 0; longitude <= LONGITUDES; longitude++) {
                double azimuth = longitude * TAU / LONGITUDES;
                directions[latitude * (LONGITUDES + 1) + longitude] = new Vec3d(
                        Math.sin(polar) * Math.cos(azimuth), Math.cos(polar), Math.sin(polar) * Math.sin(azimuth));
            }
        }
        return directions;
    }

    private static int tint(int color, float light) {
        int red = Math.round(((color >>> 16) & 255) * light);
        int green = Math.round(((color >>> 8) & 255) * light);
        int blue = Math.round((color & 255) * light);
        return (color & 0xFF000000) | (red << 16) | (green << 8) | blue;
    }

    private static void tube(VertexConsumer buffer, MatrixStack.Entry matrix, Vec3d start, Vec3d end,
                             double startRadius, double endRadius, int color) {
        Vec3d axis = end.subtract(start).normalize();
        if (axis == Vec3d.ZERO) return;
        Vec3d u = axis.crossProduct(Math.abs(axis.y) > 0.90 ? new Vec3d(1, 0, 0) : new Vec3d(0, 1, 0)).normalize();
        Vec3d v = axis.crossProduct(u);
        Vec3d previous = u;
        for (int i = 1; i <= TUBE_SIDES; i++) {
            double angle = i * TAU / TUBE_SIDES;
            Vec3d n = u.multiply(Math.cos(angle)).add(v.multiply(Math.sin(angle)));
            SurfacePoint a = new SurfacePoint(start.add(previous.multiply(startRadius)), previous);
            SurfacePoint b = new SurfacePoint(start.add(n.multiply(startRadius)), n);
            SurfacePoint c = new SurfacePoint(end.add(n.multiply(endRadius)), n);
            SurfacePoint d = new SurfacePoint(end.add(previous.multiply(endRadius)), previous);
            triangle(buffer, matrix, a, b, c, color);
            triangle(buffer, matrix, a, c, d, color);
            previous = n;
        }
    }

    private static void flatTriangle(VertexConsumer buffer, MatrixStack.Entry matrix, Vec3d a, Vec3d b, Vec3d c, int color) {
        Vec3d normal = b.subtract(a).crossProduct(c.subtract(a)).normalize();
        triangle(buffer, matrix, new SurfacePoint(a, normal), new SurfacePoint(b, normal), new SurfacePoint(c, normal), color);
    }

    private static void triangle(VertexConsumer buffer, MatrixStack.Entry matrix,
                                 SurfacePoint a, SurfacePoint b, SurfacePoint c, int color) {
        vertex(buffer, matrix, a, color);
        vertex(buffer, matrix, b, color);
        vertex(buffer, matrix, c, color);
    }

    private static void vertex(VertexConsumer buffer, MatrixStack.Entry matrix, SurfacePoint point, int color) {
        Vec3d p = point.position();
        Vec3d n = point.normal();
        buffer.vertex(matrix, (float) p.x, (float) p.y, (float) p.z).color(color)
                .normal(matrix, (float) n.x, (float) n.y, (float) n.z);
    }

    private record SurfacePoint(Vec3d position, Vec3d normal) { }
}
