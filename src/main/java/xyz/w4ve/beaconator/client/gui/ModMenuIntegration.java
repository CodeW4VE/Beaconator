package xyz.w4ve.beaconator.client.gui;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Opens the Beaconator screen from Mod Menu.
 *
 * <p>Mod Menu is compile only, so this entrypoint is simply ignored when it is not installed.
 */
public class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return BeaconatorScreen::new;
	}
}
