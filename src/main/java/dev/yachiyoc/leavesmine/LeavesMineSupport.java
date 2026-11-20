/*
 * Mining helpers ported from LeavesHack and its Meteor BlockUtils dependency.
 * See META-INF/licenses/leaveshack.txt.
 */
package dev.yachiyoc.leavesmine;

import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.RaycastContext;

final class LeavesMineSupport {
    private static final double MIN_EYE_HEIGHT = 0.4;
    private static final double MAX_EYE_HEIGHT = 1.62;
    private static final double MOVEMENT_THRESHOLD = 0.0002;

    private LeavesMineSupport() {}

    static boolean canBreak(MinecraftClient mc, BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        if (!mc.player.isCreative() && state.getHardness(mc.world, pos) < 0) return false;
        return state.getOutlineShape(mc.world, pos) != VoxelShapes.empty();
    }

    static Direction getClickSide(MinecraftClient mc, BlockPos pos) {
        Direction side = null;
        double range = 100;
        for (Direction direction : Direction.values()) {
            if (!canSee(mc, pos, direction)) continue;
            double distance = faceDistance(mc, pos, direction);
            if (distance > range) continue;
            side = direction;
            range = distance;
        }
        if (side != null) return side;
        side = Direction.UP;
        for (Direction direction : Direction.values()) {
            if (!isGrimDirection(mc, pos, direction)) continue;
            double distance = faceDistance(mc, pos, direction);
            if (distance > range) continue;
            side = direction;
            range = distance;
        }
        return side;
    }

    private static float faceDistance(MinecraftClient mc, BlockPos pos, Direction side) {
        return MathHelper.sqrt((float) mc.player.getEyePos().squaredDistanceTo(pos.offset(side).toCenterPos()));
    }

    private static boolean canSee(MinecraftClient mc, BlockPos pos, Direction side) {
        var vector = side.getVector();
        var test = pos.toCenterPos().add(vector.getX() * 0.5, vector.getY() * 0.5, vector.getZ() * 0.5);
        HitResult result = mc.world.raycast(new RaycastContext(mc.player.getEyePos(), test,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
        return result == null || result.getType() == HitResult.Type.MISS;
    }

    private static boolean isGrimDirection(MinecraftClient mc, BlockPos pos, Direction side) {
        var shape = mc.world.getBlockState(pos).getCollisionShape(mc.world, pos)
                .offset(pos.getX(), pos.getY(), pos.getZ());
        Box combined = new Box(pos);
        for (Box box : shape.getBoundingBoxes()) {
            combined = new Box(
                    Math.max(box.minX, combined.minX), Math.max(box.minY, combined.minY),
                    Math.max(box.minZ, combined.minZ), Math.min(box.maxX, combined.maxX),
                    Math.min(box.maxY, combined.maxY), Math.min(box.maxZ, combined.maxZ));
        }
        var player = mc.player;
        Box eye = new Box(player.getX(), player.getY() + MIN_EYE_HEIGHT, player.getZ(),
                player.getX(), player.getY() + MAX_EYE_HEIGHT, player.getZ()).expand(MOVEMENT_THRESHOLD);
        double margin = VoxelShapes.MIN_SIZE;
        if (combined.maxX - margin > eye.minX && combined.minX + margin < eye.maxX
                && combined.maxY - margin > eye.minY && combined.minY + margin < eye.maxY
                && combined.maxZ - margin > eye.minZ && combined.minZ + margin < eye.maxZ) {
            return true;
        }
        return !switch (side) {
            case NORTH -> eye.minZ > combined.minZ;
            case SOUTH -> eye.maxZ < combined.maxZ;
            case EAST -> eye.maxX < combined.maxX;
            case WEST -> eye.minX > combined.minX;
            case UP -> eye.maxY < combined.maxY;
            case DOWN -> eye.minY > combined.minY;
        };
    }

    static boolean hasCrystal(MinecraftClient mc, BlockPos pos) {
        Box box = new Box(pos);
        for (var entity : mc.world.getEntities()) {
            if (entity instanceof EndCrystalEntity crystal && crystal.isAlive()
                    && crystal.getBoundingBox().intersects(box)) {
                return true;
            }
        }
        return false;
    }

    static int getEnchantmentLevel(ItemStack stack, RegistryKey<Enchantment> key) {
        if (stack.isEmpty()) return 0;
        var entries = stack.getItem() == Items.ENCHANTED_BOOK
                ? stack.getOrDefault(DataComponentTypes.STORED_ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT)
                        .getEnchantmentEntries()
                : stack.getEnchantments().getEnchantmentEntries();
        Object2IntMap<RegistryEntry<Enchantment>> enchantments = new Object2IntArrayMap<>();
        for (var entry : entries) {
            enchantments.put(entry.getKey(), entry.getIntValue());
        }
        for (var entry : Object2IntMaps.fastIterable(enchantments)) {
            if (entry.getKey().matchesKey(key)) return entry.getIntValue();
        }
        return 0;
    }
}
