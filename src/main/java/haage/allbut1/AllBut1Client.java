package haage.allbut1;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.lwjgl.glfw.GLFW;

import haage.allbut1.mixin.HandledScreenAccessor;

public class AllBut1Client implements ClientModInitializer {
    private static KeyBinding leaveOneKey;

    @Override
    public void onInitializeClient() {
        // register our keybinding (default: Left Ctrl)
        leaveOneKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.allbut1.leave_one",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_LEFT_CONTROL,
                "category.allbut1"
        ));

        ScreenEvents.BEFORE_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof HandledScreen<?> hs)) return;

            // 1) allowMouseClick returns boolean: true = pass to vanilla, false = consume
            ScreenMouseEvents.allowMouseClick(screen).register((s, mx, my, btn) -> {
                // only left button
                if (btn != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;

                // must have our leave-one key + Shift held
                InputUtil.Key bound = KeyBindingHelper.getBoundKeyOf(leaveOneKey);
                long window = MinecraftClient.getInstance().getWindow().getHandle();
                if (!InputUtil.isKeyPressed(window, bound.getCode()) || !s.hasShiftDown()) {
                    return true;   // let vanilla handle normal clicks or Ctrl-only
                }

                ClientPlayerInteractionManager im = MinecraftClient.getInstance().interactionManager;
                int syncId = hs.getScreenHandler().syncId;

                // find clicked slot
                for (Slot slot : hs.getScreenHandler().slots) {
                    int sx = ((HandledScreenAccessor) hs).getX() + slot.x;
                    int sy = ((HandledScreenAccessor) hs).getY() + slot.y;
                    if (mx >= sx && mx < sx + 16 && my >= sy && my < sy + 16) {
                        // a) Quick-move full stack
                        im.clickSlot(
                                syncId,
                                slot.id,
                                0,
                                SlotActionType.QUICK_MOVE,
                                MinecraftClient.getInstance().player
                        );
                        // b) Return exactly one
                        im.clickSlot(
                                syncId,
                                slot.id,
                                GLFW.GLFW_MOUSE_BUTTON_RIGHT,
                                SlotActionType.PICKUP,
                                MinecraftClient.getInstance().player
                        );
                        break;
                    }
                }

                // consume the click so vanilla shift-click doesn't also run
                return false;
            });

            // 2) AFTER mouse click: catch Ctrl+LeftClick *without* Shift to leave one
            ScreenMouseEvents.afterMouseClick(screen).register((s, mx, my, btn) -> {
                if (btn != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;
                // skip if shift—handled above
                InputUtil.Key bound = KeyBindingHelper.getBoundKeyOf(leaveOneKey);
                long window = MinecraftClient.getInstance().getWindow().getHandle();
                if (!InputUtil.isKeyPressed(window, bound.getCode()) || s.hasShiftDown()) {
                    return;
                }

                ClientPlayerInteractionManager im = MinecraftClient.getInstance().interactionManager;
                int syncId = hs.getScreenHandler().syncId;

                for (Slot slot : hs.getScreenHandler().slots) {
                    int sx = ((HandledScreenAccessor) hs).getX() + slot.x;
                    int sy = ((HandledScreenAccessor) hs).getY() + slot.y;
                    if (mx >= sx && mx < sx + 16 && my >= sy && my < sy + 16) {
                        // leave one behind
                        im.clickSlot(
                                syncId,
                                slot.id,
                                GLFW.GLFW_MOUSE_BUTTON_RIGHT,
                                SlotActionType.PICKUP,
                                MinecraftClient.getInstance().player
                        );
                        break;
                    }
                }
            });
        });
    }
}
