package haage.allbut1.mixin;

import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("allbut1");
    
    @Shadow
    protected abstract Slot getSlotAt(double x, double y);
    
    @Shadow
    protected ScreenHandler handler;
    
    @Inject(method = "mouseClicked", at = @At("RETURN"))
    private void afterMouseClicked(Click click, boolean bl, CallbackInfoReturnable<Boolean> cir) {
        // Only handle left clicks
        if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return;
        }
        
        // Check if Ctrl is held
        long windowHandle = MinecraftClient.getInstance().getWindow().getHandle();
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
        
        // Find the clicked slot
        Slot clickedSlot = getSlotAt(click.x(), click.y());
        if (clickedSlot == null) {
            return;
        }
        
        // Get the cursor stack (what the player is holding)
        ClientPlayerInteractionManager im = MinecraftClient.getInstance().interactionManager;
        if (im == null) return;
        
        var player = MinecraftClient.getInstance().player;
        if (player == null) return;
        
        var cursorStack = handler.getCursorStack();
        if (cursorStack.isEmpty()) {
            LOGGER.info("AllBut1: Cursor is empty after click, ignoring");
            return; // Nothing was picked up
        }
        
        int cursorCount = cursorStack.getCount();
        int stackSize = clickedSlot.getStack().getCount();
        
        LOGGER.info("AllBut1: After vanilla click - slot has {}, cursor has {}", 
            stackSize, cursorCount);
        
        // Vanilla picked everything up (slot is now empty), put one back
        if (stackSize == 0 && cursorCount > 1) {
            LOGGER.info("AllBut1: Putting one item back in slot {}", clickedSlot.id);
            
            im.clickSlot(
                    handler.syncId,
                    clickedSlot.id,
                    GLFW.GLFW_MOUSE_BUTTON_RIGHT,
                    SlotActionType.PICKUP,
                    player
            );
        } else {
            LOGGER.info("AllBut1: Conditions not met - stackSize={}, cursorCount={}", stackSize, cursorCount);
        }
    }
}
