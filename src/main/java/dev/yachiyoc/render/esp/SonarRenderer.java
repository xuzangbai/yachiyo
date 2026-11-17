package dev.yachiyoc.render.esp;

import dev.yachiyoc.render.RenderApi;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Surface-only sonar: one GPU-evaluated sphere, accurate voxel faces, depth-tested terrain. */
final class SonarRenderer {
    private static final long REFRESH_NANOS = 150_000_000L;
    private static final double LIFT = 0.006;
    private final SonarPulse pulse = new SonarPulse();
    private World cachedWorld;
    private Vec3d cachedOrigin;
    private float cachedRadius;
    private long refreshed;
    private List<SonarGeometry.Face> terrain = List.of();

    void reset() {
        pulse.reset();
        cachedWorld = null;
        cachedOrigin = null;
        terrain = List.of();
    }

    void render(RenderApi api, World world, Box target, EspStyle style, int targetId, long now) {
        render(api, world, target, style, targetId, now, 1);
    }

    void render(RenderApi api, World world, Box target, EspStyle style, int targetId, long now, float visibility) {
        float maxRadius = Math.clamp(style.sonarRadius(), 0.3F, 6.0F);
        pulse.update(world, targetId, target.getCenter(), maxRadius, Math.max(0.4F, style.sonarPeriod()), now);
        Vec3d origin = pulse.origin();
        float opacity = pulse.opacity(now) * Math.clamp(visibility, 0, 1);
        int alpha = Math.round((style.sonarColor() >>> 24) * opacity);
        if (alpha < 2) return;
        if (world != cachedWorld || !origin.equals(cachedOrigin) || cachedRadius != maxRadius || now - refreshed >= REFRESH_NANOS) {
            terrain = world == null ? List.of() : collect(world, origin, maxRadius);
            cachedWorld = world;
            cachedOrigin = origin;
            cachedRadius = maxRadius;
            refreshed = now;
        }
        float spacing = Math.clamp(style.sonarSpacing(), 0.1F, 2.0F);
        float bandWidth = Math.clamp(spacing * style.thickness() * 0.1F, 0.025F, 0.6F);
        int color = (alpha << 24) | (style.sonarColor() & 0xFFFFFF);
        boolean targetThroughWalls = api.throughWalls();
        try {
            api.throughWalls(false);
            draw(api, terrain, origin, pulse.radius(now), bandWidth, spacing, style.sonarSpokes(), color);
            api.throughWalls(targetThroughWalls);
            draw(api, SonarGeometry.faces(target), origin, pulse.radius(now), bandWidth, spacing, style.sonarSpokes(), color);
        } finally {
            api.throughWalls(targetThroughWalls);
        }
    }

    private static void draw(RenderApi api, List<SonarGeometry.Face> faces, Vec3d origin,
                             float radius, float bandWidth, float spacing, int detail, int color) {
        Vec3d camera = api.cameraPos();
        double outer = radius + bandWidth * 2, inner = Math.max(0, radius - bandWidth * 7);
        for (SonarGeometry.Face face : faces) {
            if (!face.facesCamera(camera) || face.minimumDistanceSquared(origin) > outer * outer
                    || face.maximumDistanceSquared(origin) < inner * inner) continue;
            api.sonarQuad(face.point(face.minU(), face.minV(), LIFT), face.point(face.maxU(), face.minV(), LIFT),
                    face.point(face.maxU(), face.maxV(), LIFT), face.point(face.minU(), face.maxV(), LIFT),
                    origin, radius, bandWidth, spacing, detail, color);
        }
    }

    private static List<SonarGeometry.Face> collect(World world, Vec3d center, float radius) {
        Map<Long, List<Box>> shapes = new HashMap<>();
        List<SonarGeometry.Face> faces = new ArrayList<>();
        int bound = (int) Math.ceil(radius) + 1;
        BlockPos middle = BlockPos.ofFloored(center);
        for (int x = middle.getX() - bound; x <= middle.getX() + bound; x++) {
            for (int y = middle.getY() - bound; y <= middle.getY() + bound; y++) {
                for (int z = middle.getZ() - bound; z <= middle.getZ() + bound; z++) {
                    if (distanceToCellSquared(center, x, y, z) > radius * radius) continue;
                    BlockPos pos = new BlockPos(x, y, z);
                    List<Box> boxes = shape(world, pos, shapes);
                    if (boxes.isEmpty()) continue;
                    List<Box> occluders = new ArrayList<>(boxes);
                    for (Direction direction : Direction.values()) occluders.addAll(shape(world, pos.offset(direction), shapes));
                    for (Box box : boxes) {
                        for (SonarGeometry.Face face : SonarGeometry.exposedFaces(box, occluders)) {
                            if (face.minimumDistanceSquared(center) <= radius * radius) faces.add(face);
                        }
                    }
                }
            }
        }
        return SonarGeometry.merge(faces);
    }

    private static List<Box> shape(World world, BlockPos pos, Map<Long, List<Box>> cache) {
        long key = pos.asLong();
        List<Box> existing = cache.get(key);
        if (existing != null) return existing;
        List<Box> result = List.of();
        if (world.isPosLoaded(pos)) {
            BlockState state = world.getBlockState(pos);
            // Ignore fluids and non-solid plants; outline shapes preserve slabs, stairs, fences and doors.
            if (!state.isAir() && !state.getCollisionShape(world, pos).isEmpty()) {
                List<Box> local = state.getOutlineShape(world, pos).getBoundingBoxes();
                result = new ArrayList<>(local.size());
                for (Box box : local) result.add(box.offset(pos));
            }
        }
        cache.put(key, result);
        return result;
    }

    private static double distanceToCellSquared(Vec3d p, int x, int y, int z) {
        double dx = Math.max(x - p.x, Math.max(0, p.x - x - 1));
        double dy = Math.max(y - p.y, Math.max(0, p.y - y - 1));
        double dz = Math.max(z - p.z, Math.max(0, p.z - z - 1));
        return dx * dx + dy * dy + dz * dz;
    }
}
