package haage.allbut1.mixin;

import org.spongepowered.asm.mixin.Mixin;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

// Empty accessor - keeping for compatibility, actual access is done in HandledScreenMixin
@Mixin(AbstractContainerScreen.class)
public interface HandledScreenAccessor {
}
