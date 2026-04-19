package haage.allbut1;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ClientModInitializer;

public class AllBut1Client implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("allbut1");

    @Override
    public void onInitializeClient() {
        LOGGER.info("AllBut1: Client initialized - using mixin to intercept clicks");
    }
}
