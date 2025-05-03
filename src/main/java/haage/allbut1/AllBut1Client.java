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
        // 1) Register our key binding (default: Left Control)
        leaveOneKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.allbut1.leave_one",            // translation key
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_LEFT_CONTROL,         // default key
                "category.allbut1"                  // translation key for category
        ));

        // 2) Hook into container screens
        ScreenEvents.BEFORE_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof HandledScreen<?> hs)) return;

            ScreenMouseEvents
                    .afterMouseClick(screen)
                    .register((s, mouseX, mouseY, button) -> {
                        // only left-click
                        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;

                        // —— CUSTOM KEY CHECK ——
                        // get the currently bound key for our action
                        InputUtil.Key bound = KeyBindingHelper.getBoundKeyOf(leaveOneKey);
                        long window = MinecraftClient.getInstance().getWindow().getHandle();
                        // if that key isn't physically down, bail out
                        if (!InputUtil.isKeyPressed(window, bound.getCode())) return;

                        // find the clicked slot
                        for (Slot slot : hs.getScreenHandler().slots) {
                            int sx = ((HandledScreenAccessor)hs).getX() + slot.x;
                            int sy = ((HandledScreenAccessor)hs).getY() + slot.y;
                            if (mouseX >= sx && mouseX < sx + 16
                                    && mouseY >= sy && mouseY < sy + 16) {

                                // server-synced right-click to drop exactly one back
                                int syncId = hs.getScreenHandler().syncId;
                                ClientPlayerInteractionManager im = MinecraftClient.getInstance().interactionManager;
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
