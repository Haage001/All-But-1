package haage.allbut1.mixin;

import java.util.HashMap;
import java.util.Map;

import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

@Mixin(AbstractContainerScreen.class)
public abstract class HandledScreenMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("allbut1");

    private Slot allbut1$pendingShiftSlot;
    private boolean allbut1$pendingShiftSourceIsPlayer;
    private ItemStack allbut1$pendingShiftStack = ItemStack.EMPTY;
    private final Map<Integer, Integer> allbut1$pendingOppositeCounts = new HashMap<>();
    
    @Shadow
    protected Slot hoveredSlot;
    
    @Shadow
    protected AbstractContainerMenu menu;

    @Inject(method = "mouseClicked", at = @At("HEAD"))
    private void beforeMouseClicked(MouseButtonEvent click, boolean bl, CallbackInfoReturnable<Boolean> cir) {
        allbut1$pendingShiftSlot = null;
        allbut1$pendingShiftSourceIsPlayer = false;
        allbut1$pendingShiftStack = ItemStack.EMPTY;
        allbut1$pendingOppositeCounts.clear();

        if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return;
        }

        Slot clickedSlot = hoveredSlot;
        if (clickedSlot == null || !clickedSlot.hasItem()) {
            return;
        }

        long windowHandle = Minecraft.getInstance().getWindow().handle();
        boolean ctrlPressed = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS ||
            GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
        boolean shiftPressed = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS ||
            GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;

        // Fast all-but-1 only applies when assigned key (Ctrl) + Shift are held.
        if (!ctrlPressed || !shiftPressed || clickedSlot.getItem().getCount() <= 1) {
            return;
        }

        allbut1$pendingShiftSlot = clickedSlot;
        allbut1$pendingShiftSourceIsPlayer = clickedSlot.container instanceof Inventory;
        allbut1$pendingShiftStack = clickedSlot.getItem().copy();

        for (Slot slot : menu.slots) {
            boolean slotIsPlayerInventory = slot.container instanceof Inventory;
            if (slotIsPlayerInventory == allbut1$pendingShiftSourceIsPlayer) {
                continue;
            }

            int count = 0;
            if (slot.hasItem() && ItemStack.isSameItemSameComponents(slot.getItem(), allbut1$pendingShiftStack)) {
                count = slot.getItem().getCount();
            }
            allbut1$pendingOppositeCounts.put(slot.index, count);
        }
    }
    
    @Inject(method = "mouseClicked", at = @At("RETURN"))
    private void afterMouseClicked(MouseButtonEvent click, boolean bl, CallbackInfoReturnable<Boolean> cir) {
        // Only handle left clicks
        if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return;
        }
        
        // Check if Ctrl is held
        long windowHandle = Minecraft.getInstance().getWindow().handle();
        boolean ctrlPressed = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS ||
                             GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;

        boolean shiftPressed = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS ||
                              GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;

        MultiPlayerGameMode im = Minecraft.getInstance().gameMode;
        if (im == null) {
            return;
        }

        var player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        // Ctrl+Shift+LeftClick on a container slot: let vanilla quick-move run first, then return one item back.
        if (ctrlPressed && shiftPressed && allbut1$pendingShiftSlot != null && !allbut1$pendingShiftStack.isEmpty()) {
            if (allbut1$pendingShiftSlot.hasItem()) {
                return;
            }

            int oppositeSideSlotIndex = -1;
            for (Slot slot : menu.slots) {
                boolean slotIsPlayerInventory = slot.container instanceof Inventory;
                if (slotIsPlayerInventory == allbut1$pendingShiftSourceIsPlayer || !slot.hasItem()) {
                    continue;
                }

                if (!ItemStack.isSameItemSameComponents(slot.getItem(), allbut1$pendingShiftStack)) {
                    continue;
                }

                int beforeCount = allbut1$pendingOppositeCounts.getOrDefault(slot.index, 0);
                if (slot.getItem().getCount() > beforeCount) {
                    oppositeSideSlotIndex = slot.index;
                    break;
                }
            }

            if (oppositeSideSlotIndex != -1) {
                LOGGER.info("AllBut1: Shift-click transfer all-but-1 from slot {} via opposite slot {}",
                    allbut1$pendingShiftSlot.index, oppositeSideSlotIndex);

                im.handleContainerInput(
                    menu.containerId,
                    oppositeSideSlotIndex,
                    GLFW.GLFW_MOUSE_BUTTON_LEFT,
                    ContainerInput.PICKUP,
                    player
                );
                im.handleContainerInput(
                    menu.containerId,
                    allbut1$pendingShiftSlot.index,
                    GLFW.GLFW_MOUSE_BUTTON_RIGHT,
                    ContainerInput.PICKUP,
                    player
                );
                im.handleContainerInput(
                    menu.containerId,
                    oppositeSideSlotIndex,
                    GLFW.GLFW_MOUSE_BUTTON_LEFT,
                    ContainerInput.PICKUP,
                    player
                );
            } else {
                LOGGER.info("AllBut1: Shift-click post-fix skipped, no destination stack delta found");
            }

            return;
        }
        
        if (!ctrlPressed) {
            return; // Not our business
        }

        // Shift is handled above as its own quick-transfer mode.
        
        if (shiftPressed) {
            return;
        }
        
        // Use currently hovered slot as the clicked slot.
        Slot clickedSlot = hoveredSlot;
        if (clickedSlot == null) {
            return;
        }
        
        // Get the cursor stack (what the player is holding)
        var cursorStack = menu.getCarried();
        if (cursorStack.isEmpty()) {
            LOGGER.info("AllBut1: Cursor is empty after click, ignoring");
            return; // Nothing was picked up
        }
        
        int cursorCount = cursorStack.getCount();
        int stackSize = clickedSlot.getItem().getCount();
        
        LOGGER.info("AllBut1: After vanilla click - slot has {}, cursor has {}", 
            stackSize, cursorCount);
        
        // Vanilla picked everything up (slot is now empty), put one back
        if (stackSize == 0 && cursorCount > 1) {
            LOGGER.info("AllBut1: Putting one item back in slot {}", clickedSlot.index);
            
            im.handleContainerInput(
                menu.containerId,
                clickedSlot.index,
                    GLFW.GLFW_MOUSE_BUTTON_RIGHT,
                ContainerInput.PICKUP,
                    player
            );
        } else {
            LOGGER.info("AllBut1: Conditions not met - stackSize={}, cursorCount={}", stackSize, cursorCount);
        }
    }
}
