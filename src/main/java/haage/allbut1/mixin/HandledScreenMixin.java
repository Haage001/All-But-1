package haage.allbut1.mixin;

import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

import java.util.HashMap;
import java.util.Map;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("allbut1");

    @Shadow
    protected ScreenHandler handler;

    // Snapshot of slot contents before vanilla processes the click,
    // used to identify what item was moved and where it came from.
    @Unique
    private Item allbut1_preClickItem = null;
    @Unique
    private int allbut1_preClickCount = 0;
    @Unique
    private Map<Integer, Integer> allbut1_preClickSnapshot = null;

    /**
     * HEAD injection: capture slot state BEFORE vanilla processes the click.
     * This lets us know what item type was in the slot and what counts existed
     * on the destination side, so we can find where items landed after QUICK_MOVE.
     */
    @Inject(method = "onMouseClick(Lnet/minecraft/screen/slot/Slot;IILnet/minecraft/screen/slot/SlotActionType;)V", at = @At("HEAD"))
    private void beforeOnMouseClick(Slot slot, int slotId, int button, SlotActionType actionType, CallbackInfo ci) {
        if (slot == null) return;

        // Check if Ctrl is held - only snapshot if we're going to act
        long windowHandle = MinecraftClient.getInstance().getWindow().getHandle();
        boolean ctrlPressed = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS ||
                             GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
        boolean shiftPressed = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS ||
                              GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;

        if (ctrlPressed && shiftPressed && actionType == SlotActionType.QUICK_MOVE) {
            // Save the item type and count from the source slot
            ItemStack sourceStack = slot.getStack();
            allbut1_preClickItem = sourceStack.isEmpty() ? null : sourceStack.getItem();
            allbut1_preClickCount = sourceStack.getCount();

            // Snapshot counts of this item on the OTHER side (destination)
            boolean sourceIsPlayerInv = slot.inventory instanceof net.minecraft.entity.player.PlayerInventory;
            allbut1_preClickSnapshot = new HashMap<>();
            for (int i = 0; i < handler.slots.size(); i++) {
                Slot s = handler.slots.get(i);
                boolean slotIsPlayerInv = s.inventory instanceof net.minecraft.entity.player.PlayerInventory;
                if (slotIsPlayerInv != sourceIsPlayerInv) {
                    ItemStack stack = s.getStack();
                    if (!stack.isEmpty() && stack.getItem() == allbut1_preClickItem) {
                        allbut1_preClickSnapshot.put(i, stack.getCount());
                    } else if (stack.isEmpty()) {
                        allbut1_preClickSnapshot.put(i, 0);
                    }
                }
            }
        } else {
            allbut1_preClickItem = null;
            allbut1_preClickCount = 0;
            allbut1_preClickSnapshot = null;
        }
    }

    /**
     * RETURN injection: after vanilla processes the click, fix up the result.
     */
    @Inject(method = "onMouseClick(Lnet/minecraft/screen/slot/Slot;IILnet/minecraft/screen/slot/SlotActionType;)V", at = @At("RETURN"))
    private void afterOnMouseClick(Slot slot, int slotId, int button, SlotActionType actionType, CallbackInfo ci) {
        if (slot == null) return;

        // Check if our AllBut1 key (Ctrl) is held
        long windowHandle = MinecraftClient.getInstance().getWindow().getHandle();
        boolean ctrlPressed = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS ||
                             GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;

        if (!ctrlPressed) {
            return;
        }

        ClientPlayerInteractionManager im = MinecraftClient.getInstance().interactionManager;
        if (im == null) return;
        var player = MinecraftClient.getInstance().player;
        if (player == null) return;

        boolean shiftPressed = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS ||
                              GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;

        if (shiftPressed && actionType == SlotActionType.QUICK_MOVE) {
            allbut1_handleShiftClick(slot, slotId, im, player);
        } else if (!shiftPressed && actionType == SlotActionType.PICKUP) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                allbut1_handlePickup(slot, slotId, im, player);
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                allbut1_handleMacOSCtrlClick(slot, slotId, im, player);
            }
        }
    }

    /**
     * Handle Ctrl+Click (PICKUP): vanilla picked up the whole stack onto the cursor.
     * We right-click to place 1 back in the slot.
     */
    @Unique
    private void allbut1_handlePickup(Slot slot, int slotId, ClientPlayerInteractionManager im, net.minecraft.entity.player.PlayerEntity player) {
        var cursorStack = handler.getCursorStack();
        if (cursorStack.isEmpty()) {
            return;
        }

        int cursorCount = cursorStack.getCount();
        int slotCount = slot.getStack().getCount();

        if (slotCount == 0 && cursorCount > 1) {
            LOGGER.info("AllBut1: Ctrl+Click - putting 1 back in slot {}", slotId);
            im.clickSlot(handler.syncId, slotId, GLFW.GLFW_MOUSE_BUTTON_RIGHT, SlotActionType.PICKUP, player);
        }
    }

    /**
     * Handle Ctrl+Shift+Click (QUICK_MOVE): vanilla moved items to the other side.
     * Uses the pre-click snapshot to find exactly which slot(s) received items,
     * then picks up from the slot that received the most, places 1 back in the
     * original slot, and returns the rest.
     */
    @Unique
    private void allbut1_handleShiftClick(Slot slot, int slotId, ClientPlayerInteractionManager im, net.minecraft.entity.player.PlayerEntity player) {
        boolean sourceIsPlayerInv = slot.inventory instanceof net.minecraft.entity.player.PlayerInventory;
        ItemStack remaining = slot.getStack();

        if (remaining.isEmpty() && allbut1_preClickItem != null && allbut1_preClickSnapshot != null) {
            // Slot is empty - vanilla moved everything. Find where items landed by
            // comparing current counts to our pre-click snapshot.
            int bestSlot = -1;
            int bestIncrease = 0;

            for (Map.Entry<Integer, Integer> entry : allbut1_preClickSnapshot.entrySet()) {
                int idx = entry.getKey();
                int oldCount = entry.getValue();
                ItemStack current = handler.getSlot(idx).getStack();
                int newCount = (!current.isEmpty() && current.getItem() == allbut1_preClickItem) ? current.getCount() : 0;
                int increase = newCount - oldCount;

                if (increase > bestIncrease) {
                    bestIncrease = increase;
                    bestSlot = idx;
                }
            }

            if (bestSlot >= 0 && bestIncrease > 1) {
                LOGGER.info("AllBut1: Shift-click restore - slot {} gained {} items, placing 1 back in slot {}",
                    bestSlot, bestIncrease, slotId);

                // Pick up the stack from where it landed
                im.clickSlot(handler.syncId, bestSlot, GLFW.GLFW_MOUSE_BUTTON_LEFT, SlotActionType.PICKUP, player);
                // Right-click to place 1 in the original slot
                im.clickSlot(handler.syncId, slotId, GLFW.GLFW_MOUSE_BUTTON_RIGHT, SlotActionType.PICKUP, player);
                // Put the rest back where we got them
                im.clickSlot(handler.syncId, bestSlot, GLFW.GLFW_MOUSE_BUTTON_LEFT, SlotActionType.PICKUP, player);
            } else if (bestSlot >= 0 && bestIncrease == 1) {
                LOGGER.info("AllBut1: Shift-click moved only 1 item, nothing to split");
            } else {
                LOGGER.info("AllBut1: Could not find where transferred items landed");
            }
        } else if (remaining.getCount() > 1) {
            // Vanilla only moved some items. More than 1 still in the slot.
            LOGGER.info("AllBut1: Partial shift-click - {} remain in slot, reducing to 1", remaining.getCount());
            Item itemType = remaining.getItem();

            im.clickSlot(handler.syncId, slotId, GLFW.GLFW_MOUSE_BUTTON_LEFT, SlotActionType.PICKUP, player);
            im.clickSlot(handler.syncId, slotId, GLFW.GLFW_MOUSE_BUTTON_RIGHT, SlotActionType.PICKUP, player);

            int destSlot = allbut1_findCompatibleSlotOnOtherSide(itemType, slotId, sourceIsPlayerInv);
            if (destSlot >= 0) {
                im.clickSlot(handler.syncId, destSlot, GLFW.GLFW_MOUSE_BUTTON_LEFT, SlotActionType.PICKUP, player);
            } else {
                im.clickSlot(handler.syncId, slotId, GLFW.GLFW_MOUSE_BUTTON_LEFT, SlotActionType.PICKUP, player);
                LOGGER.info("AllBut1: No room on other side for extra items");
            }
        }
        // If exactly 1 remains, vanilla already did exactly what we wanted

        // Clean up snapshot
        allbut1_preClickItem = null;
        allbut1_preClickCount = 0;
        allbut1_preClickSnapshot = null;
    }

    /**
     * Handle macOS Ctrl+Click: On macOS, Ctrl+LeftClick is sent as a right-click.
     */
    @Unique
    private void allbut1_handleMacOSCtrlClick(Slot slot, int slotId, ClientPlayerInteractionManager im, net.minecraft.entity.player.PlayerEntity player) {
        var cursorStack = handler.getCursorStack();
        if (cursorStack.isEmpty()) {
            return;
        }

        int cursorCount = cursorStack.getCount();
        int slotCount = slot.getStack().getCount();

        if (cursorCount > 0 && slotCount > 0) {
            LOGGER.info("AllBut1: macOS Ctrl+Click - undoing half-pickup, cursor={}, slot={}", cursorCount, slotCount);
            im.clickSlot(handler.syncId, slotId, GLFW.GLFW_MOUSE_BUTTON_LEFT, SlotActionType.PICKUP, player);
            im.clickSlot(handler.syncId, slotId, GLFW.GLFW_MOUSE_BUTTON_LEFT, SlotActionType.PICKUP, player);
            im.clickSlot(handler.syncId, slotId, GLFW.GLFW_MOUSE_BUTTON_RIGHT, SlotActionType.PICKUP, player);
            LOGGER.info("AllBut1: macOS Ctrl+Click - all-but-1 complete");
        } else if (slotCount == 0 && cursorCount > 1) {
            LOGGER.info("AllBut1: macOS Ctrl+Click - putting 1 back in slot {}", slotId);
            im.clickSlot(handler.syncId, slotId, GLFW.GLFW_MOUSE_BUTTON_RIGHT, SlotActionType.PICKUP, player);
        }
    }

    /**
     * Find a compatible slot on the opposite side (existing stack with room, or empty slot).
     */
    @Unique
    private int allbut1_findCompatibleSlotOnOtherSide(Item item, int excludeSlotId, boolean sourceIsPlayerInv) {
        for (int i = 0; i < handler.slots.size(); i++) {
            if (i == excludeSlotId) continue;
            Slot s = handler.slots.get(i);
            boolean slotIsPlayerInv = s.inventory instanceof net.minecraft.entity.player.PlayerInventory;
            if (slotIsPlayerInv != sourceIsPlayerInv) {
                ItemStack stack = s.getStack();
                if (!stack.isEmpty() && stack.getItem() == item && stack.getCount() < stack.getMaxCount()) {
                    return i;
                }
            }
        }
        for (int i = 0; i < handler.slots.size(); i++) {
            if (i == excludeSlotId) continue;
            Slot s = handler.slots.get(i);
            boolean slotIsPlayerInv = s.inventory instanceof net.minecraft.entity.player.PlayerInventory;
            if (slotIsPlayerInv != sourceIsPlayerInv && s.getStack().isEmpty()) {
                return i;
            }
        }
        return -1;
    }
}
