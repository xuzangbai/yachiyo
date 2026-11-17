package dev.yachiyoc.render.esp;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Exact exposed rectangles of voxel shapes. No unit-cube approximation or sampled clipping. */
final class SonarGeometry {
    private static final double EPSILON = 1.0E-7;

    private SonarGeometry() { }

    record Face(int axis, int sign, double plane, double minU, double minV, double maxU, double maxV) {
        double area() { return (maxU - minU) * (maxV - minV); }

        Vec3d point(double u, double v, double lift) {
            double p = plane + sign * lift;
            return switch (axis) {
                case 0 -> new Vec3d(p, v, u);
                case 1 -> new Vec3d(u, p, v);
                default -> new Vec3d(u, v, p);
            };
        }

        double coordinate(Vec3d p) { return axis == 0 ? p.x : axis == 1 ? p.y : p.z; }
        double u(Vec3d p) { return axis == 0 ? p.z : p.x; }
        double v(Vec3d p) { return axis == 1 ? p.z : p.y; }

        boolean facesCamera(Vec3d camera) { return (coordinate(camera) - plane) * sign > EPSILON; }

        double minimumDistanceSquared(Vec3d p) {
            double du = Math.max(minU - u(p), Math.max(0, u(p) - maxU));
            double dv = Math.max(minV - v(p), Math.max(0, v(p) - maxV));
            double dn = coordinate(p) - plane;
            return du * du + dv * dv + dn * dn;
        }

        double maximumDistanceSquared(Vec3d p) {
            double du = Math.max(Math.abs(minU - u(p)), Math.abs(maxU - u(p)));
            double dv = Math.max(Math.abs(minV - v(p)), Math.abs(maxV - v(p)));
            double dn = coordinate(p) - plane;
            return du * du + dv * dv + dn * dn;
        }
    }

    static List<Face> faces(Box box) {
        return List.of(
                new Face(0, -1, box.minX, box.minZ, box.minY, box.maxZ, box.maxY),
                new Face(0, 1, box.maxX, box.minZ, box.minY, box.maxZ, box.maxY),
                new Face(1, -1, box.minY, box.minX, box.minZ, box.maxX, box.maxZ),
                new Face(1, 1, box.maxY, box.minX, box.minZ, box.maxX, box.maxZ),
                new Face(2, -1, box.minZ, box.minX, box.minY, box.maxX, box.maxY),
                new Face(2, 1, box.maxZ, box.minX, box.minY, box.maxX, box.maxY));
    }

    static List<Face> exposedFaces(Box box, List<Box> occluders) {
        List<Face> exposed = new ArrayList<>();
        for (Face face : faces(box)) {
            List<Face> pieces = List.of(face);
            for (Box other : occluders) {
                double low = minimum(other, face.axis), high = maximum(other, face.axis);
                boolean covers = face.sign > 0 ? low <= face.plane + EPSILON && high > face.plane + EPSILON
                        : high >= face.plane - EPSILON && low < face.plane - EPSILON;
                if (!covers) continue;
                int uAxis = face.axis == 0 ? 2 : 0, vAxis = face.axis == 1 ? 2 : 1;
                List<Face> next = new ArrayList<>();
                for (Face piece : pieces) {
                    subtract(piece, minimum(other, uAxis), minimum(other, vAxis),
                            maximum(other, uAxis), maximum(other, vAxis), next);
                }
                pieces = next;
                if (pieces.isEmpty()) break;
            }
            exposed.addAll(pieces);
        }
        return exposed;
    }

    private static double minimum(Box b, int axis) { return axis == 0 ? b.minX : axis == 1 ? b.minY : b.minZ; }
    private static double maximum(Box b, int axis) { return axis == 0 ? b.maxX : axis == 1 ? b.maxY : b.maxZ; }

    static void subtract(Face face, double minU, double minV, double maxU, double maxV, List<Face> result) {
        double u0 = Math.max(face.minU, minU), v0 = Math.max(face.minV, minV);
        double u1 = Math.min(face.maxU, maxU), v1 = Math.min(face.maxV, maxV);
        if (u1 - u0 <= EPSILON || v1 - v0 <= EPSILON) { result.add(face); return; }
        add(result, face, face.minU, face.minV, u0, face.maxV);
        add(result, face, u1, face.minV, face.maxU, face.maxV);
        add(result, face, u0, face.minV, u1, v0);
        add(result, face, u0, v1, u1, face.maxV);
    }

    private static void add(List<Face> faces, Face base, double u0, double v0, double u1, double v1) {
        if (u1 - u0 > EPSILON && v1 - v0 > EPSILON) faces.add(new Face(base.axis, base.sign, base.plane, u0, v0, u1, v1));
    }

    /** Merge touching strips only; holes, normal directions and fractional heights stay intact. */
    static List<Face> merge(List<Face> faces) {
        return mergeAxis(mergeAxis(faces, true), false);
    }

    private static List<Face> mergeAxis(List<Face> input, boolean horizontal) {
        List<Face> sorted = new ArrayList<>(input);
        Comparator<Face> comparator = Comparator.comparingInt(Face::axis).thenComparingInt(Face::sign)
                .thenComparingDouble(Face::plane);
        comparator = horizontal ? comparator.thenComparingDouble(Face::minV).thenComparingDouble(Face::maxV)
                .thenComparingDouble(Face::minU) : comparator.thenComparingDouble(Face::minU)
                .thenComparingDouble(Face::maxU).thenComparingDouble(Face::minV);
        sorted.sort(comparator);
        List<Face> result = new ArrayList<>();
        for (Face face : sorted) {
            if (!result.isEmpty()) {
                Face last = result.getLast();
                boolean samePlane = last.axis == face.axis && last.sign == face.sign && close(last.plane, face.plane);
                boolean touching = horizontal ? close(last.minV, face.minV) && close(last.maxV, face.maxV) && close(last.maxU, face.minU)
                        : close(last.minU, face.minU) && close(last.maxU, face.maxU) && close(last.maxV, face.minV);
                if (samePlane && touching) {
                    result.set(result.size() - 1, new Face(last.axis, last.sign, last.plane,
                            last.minU, last.minV, horizontal ? face.maxU : last.maxU, horizontal ? last.maxV : face.maxV));
                    continue;
                }
            }
            result.add(face);
        }
        return result;
    }

    private static boolean close(double a, double b) { return Math.abs(a - b) < EPSILON; }
}
