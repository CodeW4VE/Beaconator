package xyz.w4ve.beaconator.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import xyz.w4ve.beaconator.BeaconatorClient;

/** The {@code schematics} folder, the same one Litematica reads from. */
public final class SchematicFiles {
	private static final String EXTENSION = ".litematic";

	private SchematicFiles() {
	}

	public static Path dir() {
		return FabricLoader.getInstance().getGameDir().resolve("schematics");
	}

	public static List<String> list() {
		List<String> names = new ArrayList<>();
		Path dir = dir();

		if (!Files.isDirectory(dir)) {
			return names;
		}

		try (Stream<Path> files = Files.list(dir)) {
			files.filter(path -> path.getFileName().toString().endsWith(EXTENSION))
					.forEach(path -> names.add(path.getFileName().toString()));
		} catch (IOException e) {
			BeaconatorClient.LOGGER.warn("Could not list {}", dir, e);
		}

		names.sort(String::compareToIgnoreCase);
		return names;
	}

	/** Resolves a name against the schematics folder, adding the extension when it is missing. */
	public static Path resolve(String name) {
		String file = name.endsWith(EXTENSION) ? name : name + EXTENSION;
		return dir().resolve(file);
	}

	public static String stripExtension(String name) {
		return name.endsWith(EXTENSION) ? name.substring(0, name.length() - EXTENSION.length()) : name;
	}
}
