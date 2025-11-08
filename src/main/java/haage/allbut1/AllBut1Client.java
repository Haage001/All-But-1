package haage.allbut1;

import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;

public class AllBut1Client implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("allbut1");

    @Override
    public void onInitializeClient() {
        // register our keybinding (default: Left Ctrl)
        KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.allbut1.leave_one",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_LEFT_CONTROL,
                new KeyBinding.Category(Identifier.of("category", "allbut1"))
        ));

        LOGGER.info("AllBut1: Mod initialized - using mixin to intercept clicks");
    }
}
