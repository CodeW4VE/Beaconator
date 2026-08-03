package xyz.w4ve.beaconator.client;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import xyz.w4ve.beaconator.config.BeaconatorConfig;

/**
 * The mod's own translations, with a toggle in the screen rather than following the game.
 *
 * <p>Deliberate spanglish in the Spanish strings: a beacon is a beacon, easy place is easy
 * place, a litematic is a litematic. Those are the words the people who build perimeters
 * actually use, and translating them would only make the screen harder to read.
 */
public final class Lang {
	public enum Mode {
		/** Follow whatever language Minecraft is set to. */
		AUTO,
		ENGLISH,
		SPANISH
	}

	private static final Map<String, String> EN = new HashMap<>();
	private static final Map<String, String> ES = new HashMap<>();

	private Lang() {
	}

	private static void put(String key, String english, String spanish) {
		EN.put(key, english);
		ES.put(key, spanish);
	}

	static {
		// tabs
		put("tab.map", "Map", "Mapa");
		put("tab.plan", "Plan", "Plan");
		put("tab.grid", "Grid", "Retícula");
		put("tab.blocks", "Blocks", "Bloques");
		put("tab.materials", "Materials", "Materiales");
		put("tab.keys", "Keys", "Teclas");
		put("tab.display", "Display", "Pantalla");
		put("tab.shared", "Server", "Servidor");
		put("tab.water_map", "Currents", "Corrientes");
		put("tab.water_setup", "Setup", "Ajustes");
		put("tab.water_cost", "Cost", "Coste");
		put("section.to_water", "Water", "Agua");
		put("section.to_beacons", "Beacons", "Beacons");
		put("section.water_tip", "The water lines that carry what you throw in at a beacon to the digsort",
				"Las corrientes que llevan lo que tirás en un beacon hasta el digsort");
		put("section.beacons_tip", "Back to the perimeter itself", "Volver al perímetro");

		// water: the one time explanation. Everything else in the mod assumes you want beacons;
		// this part assumes you are building a digsort, and that is worth saying out loud once.
		put("water.intro_title", "Water lines: what this is for",
				"Corrientes de agua: para qué es esto");
		put("water.intro_what", "This part of the mod is aimed at the technical crowd. What we do "
				+ "is run water streams from the beacons to a digsort in the middle, so you throw "
				+ "your shulkers in at any beacon and they end up in the sorter on their own.",
				"Esta parte del mod es para la gente técnica. Lo que hacemos es llevar corrientes "
				+ "de agua de los beacons a un digsort en el medio, así tirás las shulkers en "
				+ "cualquier beacon y terminan solas en el sorter.");
		put("water.intro_who", "If that is not what you are building, and you are never making a "
				+ "digsort, this section is useless to you. No harm done: everything else in the "
				+ "mod works exactly the same without it.",
				"Si no es lo que estás construyendo, y nunca vas a hacer un digsort, esta sección "
				+ "no te sirve de nada. Tranquilo: todo lo demás del mod funciona exactamente "
				+ "igual sin ella.");
		put("water.intro_ok", "Got it", "Entendido");
		put("water.intro_again", "Read that again", "Leerlo otra vez");

		// water: the tab itself
		put("water.generate", "Generate", "Generar");
		put("water.regenerate", "Regenerate", "Regenerar");
		put("water.confirm_generate", "That replaces the %d runs you drew. Press it again to go ahead",
				"Eso reemplaza las %d líneas que dibujaste. Apretalo otra vez para seguir");
		put("water.generated", "%d runs, %s blocks of channel", "%d líneas, %s bloques de canal");
		put("water.draw_on", "Draw: on", "Dibujar: sí");
		put("water.draw_off", "Draw: off", "Dibujar: no");
		put("water.clear", "Clear all", "Borrar todo");
		put("water.confirm_clear", "Press again to throw away all %d runs",
				"Apretá otra vez para tirar las %d líneas");
		put("water.cleared", "Water lines cleared", "Corrientes borradas");
		put("water.drain_here", "Drain: look at it", "Desagüe: mirálo");
		put("water.drain_set", "Draining at %d, %d", "Desagüe en %d, %d");
		put("water.drain_none", "Draining at the middle of the grid (nothing picked yet)",
				"Desagüe en el medio de la retícula (todavía sin elegir)");
		put("water.drain_look", "Look at the block the water leaves by, then press it again",
				"Mirá el bloque por donde sale el agua y apretá otra vez");
		put("water.layout_fishbone", "Shape: fishbone", "Forma: espina");
		put("water.layout_tree", "Shape: tree", "Forma: árbol");
		put("water.ice", "Ice: %s", "Hielo: %s");
		put("water.empty", "No water lines yet. Generate one, or draw it yourself.",
				"Todavía no hay corrientes. Generá una, o dibujala vos.");
		put("water.hint_draw", "Left drag draws, right click erases up to the nearest crossing, middle drag pans",
				"Arrastrar izq dibuja, click der borra hasta el cruce más cercano, la rueda mueve");
		put("water.hint_look", "Drag to pan, right click erases up to the nearest crossing, Draw adds",
				"Arrastrá para mover, click der borra hasta el cruce más cercano, Dibujar agrega");
		put("water.drew", "Run of %d blocks", "Línea de %d bloques");
		put("water.erased", "%d blocks of channel erased", "%d bloques de canal borrados");
		put("water.nothing_there", "No run under there", "No hay ninguna línea ahí");
		put("water.stats", "%s channel  ·  %s %s (%d shulkers)  ·  longest trip %s",
				"%s de canal  ·  %s %s (%d shulkers)  ·  viaje más largo %s");
		put("water.stats2", "%d nodes served, %d without a line  ·  %d buckets, %d plates, %d junctions",
				"%d nodos servidos, %d sin línea  ·  %d cubos, %d placas, %d cruces");
		put("water.blocked", "%d runs go straight through a pyramid base",
				"%d líneas pasan por encima de una base de pirámide");
		put("water.disconnected", "%d nodes have a line that does not reach the middle",
				"%d nodos tienen línea pero no llega al medio");
		put("water.ideal", "Longest trip is %d blocks over the best a straight channel could do",
				"El viaje más largo se pasa %d bloques del mejor posible");
		put("water.ideal_perfect", "Every trip is as short as a channel can make it",
				"Cada viaje es lo más corto que un canal puede dar");
		put("water.materials", "Water lines", "Corrientes de agua");
		put("water.orphans", "%d nodes have no line to the middle yet",
				"%d nodos todavía no tienen línea al medio");
		put("water.progress", "%s flowing  ·  %s iced  ·  %s dug  ·  %s still solid",
				"%s con agua  ·  %s con hielo  ·  %s picado  ·  %s sin picar");
		put("water.progress_note", "%s blocks are too far away to read, so they are not counted",
				"%s bloques están demasiado lejos para leerlos, así que no se cuentan");
		put("water.fittings", "Sources and plates", "Fuentes y placas");
		put("water.fittings_note", "Source and plate positions are a proposal until a stretch is built",
				"Las posiciones de fuentes y placas son una propuesta hasta que se cave un tramo");
		put("water.fed", "This beacon already has water on it", "Este beacon ya tiene agua encima");
		put("water.all_served", "All %d nodes have a line to the middle",
				"Los %d nodos tienen línea al medio");
		put("water.colour", "Channel", "Canal");
		put("water.colour_bad", "Bad run", "Línea mala");
		put("water.colour_drain", "Drain", "Desagüe");
		put("water.source_every", "Source every", "Fuente cada");
		put("water.channel_y", "Channel Y", "Y del canal");
		put("water.row_step", "Every nth row", "Una fila de cada");
		put("water.drain_tip", "Aim at the block the water leaves the sorter by, then press this",
				"Apuntá al bloque por donde el agua sale del sorter y apretá esto");
		put("water.layout_tip", "Fishbone: one spine per row into one trunk, every item takes the "
				+ "shortest trip there is. Tree: shorter to build, much slower to ride",
				"Espina: una espina por fila a un tronco, cada ítem hace el viaje más corto que hay. "
				+ "Árbol: menos canal, pero mucho más lento");
		put("water.ice_tip", "What goes under the water. Packed ice is nine ice each, blue ice is "
				+ "eighty one, and neither changes how fast an item goes",
				"Lo que va bajo el agua. El packed ice son nueve hielos cada uno, el blue ice "
				+ "ochenta y uno, y ninguno cambia lo rápido que va un ítem");
		put("water.layer", "Ice floor on y=%d, water on y=%d", "Piso de hielo en y=%d, agua en y=%d");
		put("water.layer_bottom", "which is the deepest layer there is, where the bases already are",
				"que es la capa más honda que hay, donde ya están las bases");
		put("water.layer_high", "the pyramid bases are on y=%d, so mind what the channel crosses",
				"las bases de las pirámides están en y=%d, ojo con lo que cruza el canal");
		put("water.setup_hint", "Excluded nodes get no channel: they are outside the perimeter",
				"Los nodos excluidos no llevan canal: están fuera del perímetro");
		put("water.cost_mine", "%s %s to mine, %d shulkers of it",
				"%s %s por picar, %d shulkers de eso");
		put("water.cost_channel", "%s blocks of channel, %s blocks to dig out",
				"%s bloques de canal, %s bloques por excavar");
		put("water.cost_trip", "Longest trip %s blocks, average %s, %d corners at worst",
				"Viaje más largo %s bloques, promedio %s, %d esquinas en el peor caso");
		put("water.cost_nodes", "%d nodes served, %d without a line, %d excluded and left out",
				"%d nodos servidos, %d sin línea, %d excluidos y fuera");
		put("water.cost_flow", "%d water buckets, %d pressure plates, %d junctions",
				"%d cubos de agua, %d placas de presión, %d cruces");
		put("water.see_cost", "Water lines: %s channel, %d shulkers of ice. Water > Cost has the rest",
				"Corrientes: %s de canal, %d shulkers de hielo. El resto en Agua > Coste");
		put("water.show", "Show in world", "Ver en el mundo");

		// shared
		put("done", "Done", "Listo");
		put("cancel", "Cancel", "Cancelar");
		put("no_plan", "No plan open. Go to the Plan tab.", "No hay plan abierto. Andá a la pestaña Plan.");
		put("nodes", "nodes", "nodos");
		put("blocks", "blocks", "bloques");
		put("wrong_dimension", "This plan belongs to %s", "Este plan es de %s");

		// master switch
		put("master.on", "Beaconator is on again, same setup as before",
				"Beaconator encendido otra vez, con los mismos ajustes");
		put("master.off", "Beaconator is off: nothing drawn, nothing scanned. /bea on to bring it back",
				"Beaconator apagado: no dibuja ni escanea nada. /bea on para traerlo de vuelta");
		put("master.button_on", "Mod: on", "Mod: encendido");
		put("master.button_off", "Mod: OFF", "Mod: APAGADO");
		put("master.tooltip", "Turns off every overlay, the HUD, assisted placement and scanning, "
				+ "without touching your settings or your plan",
				"Apaga todos los dibujos, el HUD, la colocación asistida y el escaneo, "
				+ "sin tocar tus ajustes ni tu plan");

		// map
		put("map.fit", "Fit", "Encajar");
		put("map.centre", "Centre on me", "Centrar en mí");
		put("map.coverage_on", "Coverage on", "Cobertura sí");
		put("map.coverage_off", "Coverage off", "Cobertura no");
		put("map.outlines_on", "Outlines on", "Contornos sí");
		put("map.outlines_off", "Outlines off", "Contornos no");
		put("map.hint", "Drag to pan, scroll to zoom, left click drops a node, right click marks it excluded",
				"Arrastrá para mover, rueda para zoom, click izquierdo elimina un nodo, click derecho lo excluye");
		put("map.stats", "%d chunks drawn, %d tiles in memory", "%d chunks dibujados, %d tiles en memoria");
		put("map.node_at", "Node %s at %d, %d", "Nodo %s en %d, %d");
		put("map.undo", "Undo", "Deshacer");
		put("map.redraw", "Redraw", "Redibujar");
		put("map.moved", "Centre at %d, %d, %d", "Centro en %d, %d, %d");
		put("map.hint_move", "Arrows nudge the grid (shift 5, ctrl 16), page up and down move it in Y",
				"Las flechas mueven la retícula (shift 5, ctrl 16), av pág y re pág la mueven en Y");
		put("map.redrawn", "Redrew %d loaded chunks", "Redibujados %d chunks cargados");
		put("map.undo_node", "node", "nodo");
		put("map.undo_area", "area", "área");
		put("map.undone", "Undid %s", "Deshecho %s");
		put("map.nothing_to_undo", "Nothing to undo", "Nada que deshacer");
		put("map.selected", "%d nodes set to %s", "%d nodos puestos en %s");
		put("map.hint_area", "Shift + drag marks a rectangle, control + drag clears it. Ctrl+Z undoes.",
				"Shift + arrastrar marca un rectángulo, control + arrastrar lo limpia. Ctrl+Z deshace.");
		put("map.move_on", "Move: on", "Mover: sí");
		put("map.move_off", "Move: off", "Mover: no");
		put("map.hint_moving", "Drag a node to move it on its own, ctrl for no snapping. Arrows nudge it.",
				"Arrastrá un nodo para moverlo solo, ctrl para que no se pegue. Las flechas lo ajustan.");
		put("map.node_moved", "Node %s moved %d, %d off the grid",
				"Nodo %s movido %d, %d fuera de la retícula");
		put("map.node_home", "Node %s back on the grid", "Nodo %s de vuelta en la retícula");
		put("map.undo_move", "move", "movimiento");
		put("map.no_move_target", "Click a node first to pick which one the arrows move",
				"Hacé click en un nodo primero para elegir cuál mueven las flechas");

		// plan
		put("plan.name", "Name", "Nombre");
		put("plan.new", "New plan here", "Plan nuevo aquí");
		put("plan.centre", "Centre  (x, y, z)", "Centro  (x, y, z)");
		put("plan.apply_centre", "Move centre", "Mover el centro");
		put("plan.here", "Centre on me", "Centrar en mí");
		put("plan.detect", "Detect from world", "Detectar del mundo");
		put("plan.save", "Save plan now", "Guardar el plan ya");
		put("plan.open", "Open plan", "Abrir plan");
		put("plan.delete", "Delete plan", "Borrar plan");
		put("plan.import", "Import .litematic", "Importar .litematic");
		put("plan.export", "Export .litematic", "Exportar .litematic");
		put("plan.pick_open", "Open a plan", "Abrir un plan");
		put("plan.pick_delete", "Delete a plan", "Borrar un plan");
		put("plan.pick_import", "Import a schematic", "Importar un litematic");
		put("plan.empty_list", "Nothing here yet", "Todavía no hay nada");
		put("plan.saved", "Saved", "Guardado");
		put("plan.autosaves", "The plan saves itself when you close this screen",
				"El plan se guarda solo al cerrar esta pantalla");
		put("share.title", "Plans shared on this server", "Planes compartidos en este server");
		put("share.empty", "Nobody has shared a plan here yet", "Todavía nadie compartió un plan acá");
		put("share.push", "Share mine", "Compartir el mío");
		put("share.go", "Server plans", "Planes del server");
		put("share.remove", "Remove", "Quitar");
		put("share.refresh", "Refresh", "Recargar");
		put("share.opened", "Opened the shared plan \"%s\"", "Abriste el plan compartido \"%s\"");
		put("share.opening", "Asking for \"%s\"...", "Pidiendo \"%s\"...");
		put("share.pushed", "\"%s\" is on the server now", "\"%s\" ya está en el server");
		put("share.removed", "Took \"%s\" off the server", "Sacaste \"%s\" del server");
		put("share.on", "You are on \"%s\": what you mark, everyone sees", "Estás en \"%s\": lo que marques lo ven todos");
		put("share.off", "You are on a local plan, only you see it", "Estás en un plan local, sólo lo ves vos");
		put("share.hint", "Sharing your plan puts a copy on the server under its own name", "Compartir tu plan sube una copia al server con su nombre");
		put("plan.save_failed", "Could not save, check the log", "No se pudo guardar, mirá el log");
		put("plan.created", "Plan \"%s\" centred where you are standing",
				"Plan \"%s\" centrado donde estás parado");
		put("plan.centre_moved", "Centre moved", "Centro movido");
		put("plan.bad_numbers", "Those are not three numbers", "Eso no son tres números");
		put("plan.opened", "Opened \"%s\"", "Abriste \"%s\"");
		put("plan.open_failed", "Could not open \"%s\"", "No se pudo abrir \"%s\"");
		put("plan.deleted", "Deleted \"%s\"", "Borraste \"%s\"");
		put("plan.delete_failed", "Could not delete it", "No se pudo borrar");
		put("plan.detected", "Found %d beacons: %d x %d nodes, spacing %d",
				"Encontré %d beacons: %d x %d nodos, separación %d");
		put("plan.imported", "Imported %d nodes", "Importados %d nodos");
		put("plan.imported_loose", "Imported, but schematics carry no coordinates: it landed at your feet",
				"Importado, pero los litematics no llevan coordenadas: quedó a tus pies");
		put("plan.import_failed", "Could not read it: %s", "No se pudo leer: %s");
		put("plan.exporting", "Writing %s.litematic, this takes a moment...",
				"Escribiendo %s.litematic, esto tarda un poco...");
		put("plan.exported", "Wrote schematics/%s.litematic", "Escribí schematics/%s.litematic");
		put("plan.export_failed", "Could not write it: %s", "No se pudo escribir: %s");

		// grid
		put("grid.ring", "Ring", "Anillo");
		put("grid.spacing", "Spacing", "Separación");
		put("grid.sides", "Sides", "Lados");
		put("grid.west", "West", "Oeste");
		put("grid.east", "East", "Este");
		put("grid.north", "North", "Norte");
		put("grid.south", "South", "Sur");
		put("grid.beacons_one", "%d beacon per node", "%d beacon por nodo");
		put("grid.beacons_many", "%d beacons per node", "%d beacons por nodo");
		put("grid.level", "Pyramid level %d (%d blocks)", "Pirámide nivel %d (%d bloques)");
		put("grid.axis", "Rotation %s", "Rotación %s");
		put("grid.auto_spacing", "Spacing follows level", "Separación según el nivel");
		put("grid.summary", "%d x %d = %d nodes, %s blocks", "%d x %d = %d nodos, %s bloques");
		put("grid.per_pyramid", "%d blocks per pyramid, base %d x %d",
				"%d bloques por pirámide, base %d x %d");
		put("grid.fits", "Coverage lines up exactly, no overlap and no holes",
				"La cobertura encaja exacta, sin solape ni huecos");
		put("grid.overlap", "Coverage overlaps by %d blocks, no holes",
				"La cobertura solapa %d bloques, sin huecos");
		put("grid.gap", "Warning: %d blocks between nodes get no effect at all",
				"Ojo: %d bloques entre nodos se quedan sin efecto");

		// blocks
		put("blocks.pyramid", "Pyramid block", "Bloque de la pirámide");
		put("blocks.marker", "Marker block for excluded nodes", "Bloque marcador de los nodos excluidos");
		put("blocks.from_hand", "From hand", "El de la mano");
		put("blocks.place_marker", "Place marker block", "Poner bloque marcador");
		put("blocks.inner_cap", "Cap the rest too", "Rematar también los demás");
		put("blocks.inner_cap_is", "Nodes inside get %s on top", "Los nodos de dentro llevan %s encima");
		put("blocks.hold_first", "Hold a block first", "Agarrá un bloque primero");
		put("blocks.unknown", "Unknown block: %s", "Bloque desconocido: %s");
		put("blocks.using", "Using %s", "Usando %s");
		put("blocks.explain1", "Excluded nodes are built like the rest and topped with the marker,",
				"Los nodos excluidos se construyen igual y llevan el marcador encima,");
		put("blocks.explain2", "so they read as outside the perimeter from a distance.",
				"para que se vean como fuera del perímetro desde lejos.");

		// materials
		put("materials.scan", "Scan the world", "Escanear el mundo");
		put("materials.keep_scanning", "Keep scanning", "Escanear siempre");
		put("materials.block", "Block", "Bloque");
		put("materials.needed", "Needed", "Hace falta");
		put("materials.missing", "Missing", "Falta");
		put("materials.scanned", "Read %d of %d nodes, %d finished", "Leí %d de %d nodos, %d terminados");
		put("materials.total", "%s blocks in total, %s still to place",
				"%s bloques en total, faltan %s por poner");
		put("materials.unknown", "%s of those sit in nodes too far away to check",
				"%s de esos están en nodos demasiado lejos para comprobar");
		put("materials.states", "%d nodes finished, %d pending, %d excluded, %d removed",
				"%d nodos terminados, %d pendientes, %d excluidos, %d eliminados");

		// display
		put("display.coverage", "Coverage", "Cobertura");
		put("display.outlines", "Outlines", "Contornos");
		put("display.style_slab", "Slab at beacon height", "Losa en el beacon");
		put("display.style_floor", "Slab at the bottom", "Losa en el fondo");
		put("display.style_full", "Full volume", "Volumen entero");
		put("display.see_through", "See through terrain", "Ver a través");
		put("display.opacity", "Opacity %", "Opacidad %");
		put("display.distance", "Distance", "Distancia");
		put("display.beacon_boxes", "Beacon boxes", "Cajas de beacons");
		put("display.beams", "Beams", "Haces de luz");
		put("display.beam_pixels", "Beam thickness", "Grosor del haz");
		put("display.pyramid_outline", "Pyramid outline", "Contorno pirámide");
		put("display.hud", "HUD", "HUD");
		put("display.progress", "Shade by progress", "Color por progreso");
		put("display.shade_excluded", "Shade excluded too", "Color en excluidos");
		put("display.easy_place", "Easy place", "Easy place");
		put("display.strict", "Block stray blocks", "Nada fuera del plan");
		put("display.layers", "Layers: %s", "Capas: %s");
		put("display.layer_up", "Layer up", "Capa arriba");
		put("display.layer_down", "Layer down", "Capa abajo");
		put("colour.pending", "To do", "Por hacer");
		put("colour.partial", "Half built", "A medias");
		put("colour.placed", "Done", "Hecho");
		put("colour.excluded", "Excluded", "Excluido");
		put("colour.excluded_done", "Excluded, done", "Excluido, hecho");
		put("colour.removed", "Dropped", "Eliminado");
		put("colour.beam", "Beam", "Haz");
		put("colour.white", "white", "blanco");
		put("colour.yellow", "yellow", "amarillo");
		put("colour.orange", "orange", "naranja");
		put("colour.red", "red", "rojo");
		put("colour.pink", "pink", "rosa");
		put("colour.purple", "purple", "morado");
		put("colour.blue", "blue", "azul");
		put("colour.cyan", "cyan", "cian");
		put("colour.green", "green", "verde");
		put("colour.lime", "lime", "lima");
		put("colour.grey", "grey", "gris");
		put("colour.dark_grey", "dark grey", "gris oscuro");
		put("display.language", "Language", "Idioma");
		put("display.follow_litematica", "Follow Litematica", "Seguir Litematica");

		// layers
		put("layers.all", "all layers", "todas las capas");
		put("layers.single", "layer y=%d", "capa y=%d");
		put("layers.range", "layers y=%d..%d", "capas y=%d..%d");

		// keys
		put("keys.press", "Press a key...", "Apretá una tecla...");
		put("keys.reset", "Reset", "Restablecer");
		put("keys.clear", "Clear", "Quitar");
		put("keys.hint", "Click a binding, then press the key you want. Escape clears it.",
				"Hacé click en una tecla y apretá la que quieras. Escape la quita.");
		put("keys.hint2", "Shift + the edit mode key opens this screen.",
				"Shift + la tecla de modo edición abre esta pantalla.");
		put("keys.bound", "Bound to %s", "Asignada a %s");
		put("keys.cleared", "Binding cleared", "Tecla quitada");
		put("keys.conflict", "%s is already used by: %s",
				"%s ya la usa: %s");

		// hud
		put("hud.edit", "edit", "edición");
		put("hud.shared", "shared", "compartido");
		put("plan.no_server", "This server does not have Beaconator", "Este server no tiene Beaconator");
		put("hud.grid", "%d x %d nodes (ring %d)", "%d x %d nodos (anillo %d)");
		put("hud.grid_custom", "%d x %d nodes (custom sides)", "%d x %d nodos (lados a mano)");
		put("hud.setup", "%d beacon(s) per node, level %d, spacing %d",
				"%d beacon(s) por nodo, nivel %d, separación %d");
		put("hud.auto", " (auto)", " (auto)");
		put("hud.states", "%d pending, %d placed, %d excluded, %d removed",
				"%d pendientes, %d colocados, %d excluidos, %d eliminados");
		put("hud.node", "Node %s at %d, %d, %d", "Nodo %s en %d, %d, %d");
		put("hud.easy_place", "Easy place: %s", "Easy place: %s");
		put("hud.nothing_here", "nothing here", "nada aquí");
		put("hud.missing", "Missing %s blocks", "Faltan %s bloques");
		put("hud.nothing_missing", "Nothing missing in what has been scanned",
				"No falta nada de lo escaneado");

		// node states
		put("state.pending", "PENDING", "PENDIENTE");
		put("state.placed", "PLACED", "COLOCADO");
		put("state.excluded", "EXCLUDED", "EXCLUIDO");
		put("state.removed", "REMOVED", "ELIMINADO");
	}

	public static String t(String key) {
		Map<String, String> table = table();
		String value = table.get(key);

		if (value == null) {
			value = EN.get(key);
		}

		return value == null ? key : value;
	}

	public static String t(String key, Object... args) {
		try {
			return String.format(t(key), args);
		} catch (RuntimeException e) {
			return t(key);
		}
	}

	public static Component c(String key) {
		return Component.literal(t(key));
	}

	public static Component c(String key, Object... args) {
		return Component.literal(t(key, args));
	}

	private static Map<String, String> table() {
		return switch (BeaconatorConfig.get().language) {
			case ENGLISH -> EN;
			case SPANISH -> ES;
			case AUTO -> isSpanishGame() ? ES : EN;
		};
	}

	private static boolean isSpanishGame() {
		Minecraft minecraft = Minecraft.getInstance();

		if (minecraft.getLanguageManager() == null) {
			return false;
		}

		return minecraft.getLanguageManager().getSelected().startsWith("es_");
	}

	/** Name of a node state in the current language. */
	public static String state(xyz.w4ve.beaconator.model.NodeStatus status) {
		return switch (status) {
			case PENDING -> t("state.pending");
			case PLACED -> t("state.placed");
			case EXCLUDED -> t("state.excluded");
			case REMOVED -> t("state.removed");
		};
	}
}
