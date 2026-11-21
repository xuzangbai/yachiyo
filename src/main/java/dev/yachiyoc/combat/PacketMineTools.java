package dev.yachiyoc.combat;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public final class PacketMineTools {
    private PacketMineTools() {}

    public static boolean canBreak(MinecraftClient mc, BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        return !state.isAir() && state.getHardness(mc.world, pos) >= 0
                && !state.getOutlineShape(mc.world, pos).isEmpty();
    }

    public static BlockHitResult reachableHit(MinecraftClient mc, BlockPos pos) {
        Vec3d eye = mc.player.getEyePos();
        double reach = mc.player.getBlockInteractionRange();
        double closest = reach * reach;
        BlockHitResult best = null;
        for (var box : mc.world.getBlockState(pos).getOutlineShape(mc.world, pos).getBoundingBoxes()) {
            Vec3d center = box.getCenter().add(pos.getX(), pos.getY(), pos.getZ());
            for (Direction face : Direction.values()) {
                double halfWidth = switch (face.getAxis()) {
                    case X -> box.getLengthX() / 2;
                    case Y -> box.getLengthY() / 2;
                    case Z -> box.getLengthZ() / 2;
                };
                double offset = Math.max(0, halfWidth - 0.001);
                Vec3d point = center.add(face.getOffsetX() * offset, face.getOffsetY() * offset, face.getOffsetZ() * offset);
                BlockHitResult hit = raycast(mc, eye, point);
                double distance = eye.squaredDistanceTo(hit.getPos());
                if (hit.getType() == HitResult.Type.BLOCK && pos.equals(hit.getBlockPos()) && distance <= closest) {
                    best = hit;
                    closest = distance;
                }
            }
        }
        return best;
    }

    public static BlockHitResult raycast(MinecraftClient mc, Vec3d start, Vec3d end) {
        return mc.world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE, mc.player));
    }

    public static float breakingDelta(MinecraftClient mc, BlockPos pos, int slot) {
        BlockState state = mc.world.getBlockState(pos);
        var player = mc.player;
        float hardness = state.getHardness(mc.world, pos);
        if (hardness < 0) return 0;
        if (hardness == 0) return 1;
        ItemStack tool = player.getInventory().getStack(slot);
        ItemStack held = player.getMainHandStack();
        float speed = tool.getMiningSpeedMultiplier(state);
        if (speed > 1) {
            var efficiency = new EntityAttributeInstance(EntityAttributes.MINING_EFFICIENCY, ignored -> {});
            addToolModifiers(efficiency, EntityAttributes.MINING_EFFICIENCY, tool);
            speed += (float) efficiency.getValue();
        }
        if (StatusEffectUtil.hasHaste(player)) {
            speed *= 1 + (StatusEffectUtil.getHasteAmplifier(player) + 1) * 0.2f;
        }
        if (player.hasStatusEffect(StatusEffects.MINING_FATIGUE)) {
            speed *= switch (player.getStatusEffect(StatusEffects.MINING_FATIGUE).getAmplifier()) {
                case 0 -> 0.3f;
                case 1 -> 0.09f;
                case 2 -> 0.0027f;
                default -> 0.00081f;
            };
        }
        speed *= attribute(mc, EntityAttributes.BLOCK_BREAK_SPEED, held, tool);
        if (player.isSubmergedIn(FluidTags.WATER)) {
            speed *= attribute(mc, EntityAttributes.SUBMERGED_MINING_SPEED, held, tool);
        }
        if (!player.isOnGround()) speed /= 5;
        boolean canHarvest = !state.isToolRequired() || tool.isSuitableFor(state);
        return speed / hardness / (canHarvest ? 30f : 100f);
    }

    private static float attribute(MinecraftClient mc, RegistryEntry<EntityAttribute> type,
                                   ItemStack held, ItemStack tool) {
        var adjusted = new EntityAttributeInstance(type, ignored -> {});
        adjusted.setFrom(mc.player.getAttributeInstance(type));
        held.applyAttributeModifiers(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.equals(type)) adjusted.removeModifier(modifier.id());
        });
        addToolModifiers(adjusted, type, tool);
        return (float) adjusted.getValue();
    }

    private static void addToolModifiers(EntityAttributeInstance adjusted, RegistryEntry<EntityAttribute> type, ItemStack tool) {
        tool.applyAttributeModifiers(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.equals(type)) {
                adjusted.removeModifier(modifier.id());
                adjusted.addTemporaryModifier(modifier);
            }
        });
    }
}
