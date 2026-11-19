package dev.yachiyoc.render.balloon;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/** A buoyant ellipsoid on a segmented, inextensible tether. Distances are in blocks. */
public final class BalloonPhysics {
    private static final int LINKS = 12;
    private static final int SUBSTEPS = 4;
    private static final int ITERATIONS = 6;
    private static final double STEP = 1.0 / (20 * SUBSTEPS);
    private static final double ROPE_RADIUS = 0.007;
    private static final double BALLOON_INVERSE_MASS = 0.065;
    private static final double CORE = 0.46;
    private static final double HEIGHT_RATIO = 1.17;
    private static final double KNOT_OFFSET = 1.03;

    private final Vec3d[] rope = new Vec3d[LINKS + 1];
    private final Vec3d[] lastRope = new Vec3d[LINKS + 1];
    private Vec3d center;
    private Vec3d velocity = Vec3d.ZERO;
    private Vec3d anchor;
    private double radius;
    private double length;
    private double time;
    private Frame previous;
    private Frame current;
    private List<Box> obstacles = List.of();

    public BalloonPhysics(Vec3d anchor, Vec3d forward, double radius, double length) {
        this.anchor = anchor;
        this.radius = radius;
        this.length = length;
        Vec3d knot = anchor.add(forward.multiply(0.58).add(0, 0.81, 0).normalize().multiply(length * 0.98));
        center = knot.add(0, radius * HEIGHT_RATIO * KNOT_OFFSET, 0);
        for (int i = 0; i <= LINKS; i++) rope[i] = lastRope[i] = anchor.lerp(knot, (double) i / LINKS);
        current = previous = snapshot();
    }

    public Box bounds(Vec3d target, double targetRadius, double targetLength) {
        double margin = Math.max(radius, targetRadius) * 2.5 + 0.15;
        Vec3d reach = target.add(0, Math.max(length, targetLength), 0);
        return new Box(anchor, target).union(new Box(center, reach)).expand(margin + Math.max(length, targetLength) * 0.5);
    }

    public void tick(Vec3d target, Vec3d forward, double newRadius, double newLength, List<Box> obstacles) {
        previous = current;
        this.obstacles = obstacles;
        double heightChange = (newRadius - radius) * HEIGHT_RATIO * KNOT_OFFSET;
        center = center.add(0, heightChange, 0);
        radius = newRadius;
        length = newLength;
        double linkLength = length / LINKS;
        for (int step = 1; step <= SUBSTEPS; step++) {
            time += STEP;
            Vec3d pin = anchor.lerp(target, (double) step / SUBSTEPS);
            Vec3d oldCenter = center;
            Vec3d side = new Vec3d(-forward.z, 0, forward.x);
            double crosswind = (Math.sin(time * 1.72) * 0.24 + Math.sin(time * 2.81 + 0.8) * 0.075) * length;
            double downwind = Math.sin(time * 2.12 + 0.6) * 0.14 * length;
            Vec3d desired = pin.add(forward.multiply(length * 0.86 + downwind)).add(side.multiply(crosswind));
            Vec3d lateral = new Vec3d(desired.x - center.x, 0, desired.z - center.z);
            double lift = 9.4 + 2.5 * Math.sin(time * 1.89 + 0.3);
            double slack = pin.y + length * 0.65 - knot().y;
            velocity = velocity.multiply(0.977).add(lateral.multiply(22 * STEP))
                    .add(0, (lift + Math.max(0, slack) * 17) * STEP, 0);
            center = center.add(velocity.multiply(STEP));
            for (int i = 1; i < LINKS; i++) {
                Vec3d point = rope[i];
                rope[i] = point.add(point.subtract(lastRope[i]).multiply(0.93)).add(0, -6 * STEP * STEP, 0);
                lastRope[i] = point;
            }
            for (int iteration = 0; iteration < ITERATIONS; iteration++) {
                rope[0] = pin;
                rope[LINKS] = knot();
                if ((iteration & 1) == 0) {
                    for (int i = 0; i < LINKS; i++) constrainLink(i, linkLength);
                } else {
                    for (int i = LINKS - 1; i >= 0; i--) constrainLink(i, linkLength);
                }
                // Limit long-range stretch before resolving contacts, including a wedged balloon.
                Vec3d offset = knot().subtract(pin);
                if (offset.lengthSquared() > length * length) center = center.add(offset.normalize().multiply(length).subtract(offset));
                for (Box box : obstacles) {
                    Contact contact = contact(center, box, radius, radius * HEIGHT_RATIO, 1.0);
                    if (contact != null) {
                        double separation = contact.distance();
                        double correction = Math.max(CORE - separation, (0.91 - separation) * 0.40);
                        if (correction > 0) center = center.add(toWorld(contact.normal()).multiply(correction));
                    }
                }
                for (int i = 1; i < LINKS; i++) {
                    for (Box box : obstacles) {
                        Contact contact = contact(rope[i], box, ROPE_RADIUS, ROPE_RADIUS, 1.0);
                        if (contact != null) rope[i] = rope[i].add(contact.normal().multiply((1 - contact.distance()) * ROPE_RADIUS));
                    }
                }
            }
            Vec3d solved = center.subtract(oldCenter).multiply(1 / STEP);
            double speed = solved.length();
            velocity = speed > 12 ? solved.multiply(12 / speed) : solved;
        }
        anchor = rope[0] = target;
        rope[LINKS] = knot();
        current = snapshot();
    }

    private void constrainLink(int index, double linkLength) {
        int next = index + 1;
        Vec3d delta = rope[next].subtract(rope[index]);
        double distance = delta.length();
        if (distance < 1.0E-7) return;
        double firstMass = index == 0 ? 0 : 1;
        double secondMass = next == LINKS ? BALLOON_INVERSE_MASS : 1;
        Vec3d correction = delta.multiply((distance - linkLength) / (distance * (firstMass + secondMass)));
        if (index != 0) rope[index] = rope[index].add(correction.multiply(firstMass));
        rope[next] = rope[next].subtract(correction.multiply(secondMass));
        if (next == LINKS) center = center.subtract(correction.multiply(secondMass));
    }

    private Vec3d knot() {
        return center.add(0, -radius * HEIGHT_RATIO * KNOT_OFFSET, 0);
    }

    private Vec3d toWorld(Vec3d unit) {
        return unit.multiply(radius, radius * HEIGHT_RATIO, radius);
    }

    public Vec3d anchor() { return anchor; }

    public void swat(Vec3d direction) {
        velocity = velocity.add(direction.multiply(2.7)).add(0, 0.65, 0);
    }

    public Frame frame(float tickDelta) {
        if (tickDelta >= 1 || previous == current) return current;
        Vec3d c = previous.center().lerp(current.center(), tickDelta);
        Vec3d[] points = new Vec3d[rope.length];
        for (int i = 0; i < points.length; i++) points[i] = previous.rope()[i].lerp(current.rope()[i], tickDelta);
        double r = previous.radius() + (radius - previous.radius()) * tickDelta;
        return new Frame(c, r, points, contacts(c, r));
    }

    private Frame snapshot() {
        return new Frame(center, radius, rope.clone(), contacts(center, radius));
    }

    private List<Contact> contacts(Vec3d position, double radius) {
        List<Contact> result = new ArrayList<>();
        for (Box box : obstacles) {
            Contact contact = contact(position, box, radius, radius * HEIGHT_RATIO, 1.0);
            if (contact != null) result.add(new Contact(contact.normal(), Math.max(0.035, contact.distance() - 0.007)));
        }
        return result;
    }

    /** The normalized closest point gives a separating plane even at the corner of a voxel. */
    private static Contact contact(Vec3d position, Box box, double r, double yRadius, double max) {
        double x = (position.x - Math.clamp(position.x, box.minX, box.maxX)) / r;
        double y = (position.y - Math.clamp(position.y, box.minY, box.maxY)) / yRadius;
        double z = (position.z - Math.clamp(position.z, box.minZ, box.maxZ)) / r;
        double squared = x * x + y * y + z * z;
        if (squared >= max * max) return null;
        if (squared > 1.0E-12) {
            double distance = Math.sqrt(squared);
            return new Contact(new Vec3d(x / distance, y / distance, z / distance), distance);
        }
        double minX = (position.x - box.minX) / r;
        double maxX = (box.maxX - position.x) / r;
        double minY = (position.y - box.minY) / yRadius;
        double maxY = (box.maxY - position.y) / yRadius;
        double minZ = (position.z - box.minZ) / r;
        double maxZ = (box.maxZ - position.z) / r;
        double nearest = Math.min(Math.min(Math.min(minX, maxX), Math.min(minY, maxY)), Math.min(minZ, maxZ));
        if (nearest == minX) return new Contact(new Vec3d(-1, 0, 0), -nearest);
        if (nearest == maxX) return new Contact(new Vec3d(1, 0, 0), -nearest);
        if (nearest == minY) return new Contact(new Vec3d(0, -1, 0), -nearest);
        if (nearest == maxY) return new Contact(new Vec3d(0, 1, 0), -nearest);
        return new Contact(new Vec3d(0, 0, nearest == minZ ? -1 : 1), -nearest);
    }

    public record Contact(Vec3d normal, double distance) { }

    public record Frame(Vec3d center, double radius, Vec3d[] rope, List<Contact> contacts) {
        public double heightRadius() { return radius * HEIGHT_RATIO; }

        /** Distance along a unit world-space ray to the deformed, convex body. */
        public double hit(Vec3d start, Vec3d direction, double reach) {
            Vec3d scale = new Vec3d(1 / radius, 1 / heightRadius(), 1 / radius);
            Vec3d origin = start.subtract(center).multiply(scale);
            Vec3d ray = direction.multiply(scale);
            double a = ray.lengthSquared();
            double b = origin.dotProduct(ray);
            double c = origin.lengthSquared() - 1;
            double determinant = b * b - a * c;
            if (determinant < 0 || a == 0) return Double.POSITIVE_INFINITY;
            double span = Math.sqrt(determinant);
            double near = Math.max(0, (-b - span) / a);
            double far = Math.min(reach, (-b + span) / a);
            for (Contact contact : contacts) {
                double slope = ray.dotProduct(contact.normal());
                double intercept = -contact.distance() - origin.dotProduct(contact.normal());
                if (slope > 1.0E-8) near = Math.max(near, intercept / slope);
                else if (slope < -1.0E-8) far = Math.min(far, intercept / slope);
                else if (intercept > 0) return Double.POSITIVE_INFINITY;
            }
            return near <= far ? near : Double.POSITIVE_INFINITY;
        }
    }
}
