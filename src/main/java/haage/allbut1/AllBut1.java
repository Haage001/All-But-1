package haage.allbut1;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AllBut1 implements ModInitializer {
	public static final String MOD_ID = "allbut1";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {

		LOGGER.info("Hello AllBut1");
	}
}