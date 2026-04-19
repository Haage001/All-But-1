package haage.allbut1.mixin;

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
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;

@Mixin(AbstractContainerScreen.class)
public abstract class HandledScreenMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("allbut1");
    
    @Shadow
    protected Slot hoveredSlot;
    
    @Shadow
    protected AbstractContainerMenu menu;
    
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
        
        if (!ctrlPressed) {
            return; // Not our business
        }
        
        // Check if Shift is held (temporarily disabled)
        boolean shiftPressed = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS ||
                              GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        
        if (shiftPressed) {
            LOGGER.info("AllBut1: Shift is held, ignoring for now");
            return; // Temporarily disabled
        }
        
        // Use currently hovered slot as the clicked slot.
        Slot clickedSlot = hoveredSlot;
        if (clickedSlot == null) {
            return;
        }
        
        // Get the cursor stack (what the player is holding)
        MultiPlayerGameMode im = Minecraft.getInstance().gameMode;
        if (im == null) return;
        
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        
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
