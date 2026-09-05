package com.example.lockedybridge;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.lwjgl.glfw.GLFW;

public class LockedYBridgeClient implements ClientModInitializer {
    private static final String MOD_NAME = "Locked Y Bridge";

    private KeyBinding toggleKey;
    private boolean enabled = false;
    private int lockedY = 0;
    private int placeCooldown = 0;

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.locked_y_bridge.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_C,
                "category.locked_y_bridge"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
    }

    private void onClientTick(MinecraftClient client) {
        while (toggleKey.wasPressed()) {
            toggle(client);
        }

        if (!enabled || client.player == null || client.world == null ||
                client.interactionManager == null) {
            return;
        }

        if (placeCooldown > 0) {
            placeCooldown--;
            return;
        }

        ItemStack held = client.player.getMainHandStack();
        if (!(held.getItem() instanceof BlockItem)) {
            return; // 手上不是方块：完全停止放置
        }

        // 前方最多预铺三格。所有目标的 Y 都固定为 lockedY。
        BlockPos base = client.player.getBlockPos();
        Direction forward = horizontalDirection(client.player);

        for (int distance = 1; distance <= 3; distance++) {
            BlockPos target = new BlockPos(
                    base.getX() + forward.getOffsetX() * distance,
                    lockedY,
                    base.getZ() + forward.getOffsetZ() * distance
            );

            if (tryPlace(client, target)) {
                placeCooldown = 1;
                break;
            }
        }
    }

    private void toggle(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            return;
        }

        enabled = !enabled;

        if (enabled) {
            // “脚下方块”的 Y：玩家脚所在方块坐标 - 1。
            lockedY = client.player.getBlockPos().getY() - 1;
            client.player.sendMessage(
                    net.minecraft.text.Text.literal("§a锁Y辅助：开启 §7| 锁定 Y = " + lockedY),
                    true
            );
        } else {
            client.player.sendMessage(
                    net.minecraft.text.Text.literal("§c锁Y辅助：关闭"),
                    true
            );
        }
    }

    private Direction horizontalDirection(ClientPlayerEntity player) {
        float yaw = player.getYaw();
        int dir = Math.round(yaw / 90.0f) & 3;
        return switch (dir) {
            case 0 -> Direction.SOUTH;
            case 1 -> Direction.WEST;
            case 2 -> Direction.NORTH;
            default -> Direction.EAST;
        };
    }

    private boolean tryPlace(MinecraftClient client, BlockPos target) {
        if (client.world == null || client.player == null || client.interactionManager == null) {
            return false;
        }

        BlockState targetState = client.world.getBlockState(target);
        if (!targetState.isAir()) {
            return false;
        }

        // 在目标位置下面的方块顶部放置，因此目标本身保持 lockedY。
        BlockPos support = target.down();
        BlockState supportState = client.world.getBlockState(support);

        if (!supportState.isSolidBlock(client.world, support)) {
            return false;
        }

        ItemStack held = client.player.getMainHandStack();
        if (!(held.getItem() instanceof BlockItem)) {
            return false;
        }

        Vec3Like hit = new Vec3Like(
                support.getX() + 0.5,
                support.getY() + 1.0,
                support.getZ() + 0.5
        );

        BlockHitResult hitResult = new BlockHitResult(
                new net.minecraft.util.math.Vec3d(hit.x, hit.y, hit.z),
                Direction.UP,
                support,
                false
        );

        var result = client.interactionManager.interactBlock(
                client.player,
                Hand.MAIN_HAND,
                hitResult
        );

        if (result.isAccepted()) {
            client.player.swingHand(Hand.MAIN_HAND);
            return true;
        }

        return false;
    }

    private record Vec3Like(double x, double y, double z) {}
}
