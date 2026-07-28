# Changelog

## 1.0.0

First build.

### The screen

- Shift + B, a bindable key of its own, `/bea gui`, or the Mod Menu entry.
- Tabs for the map, the plan, the grid, the blocks, the material list, the keys and the display
  options. Every setting shows its current value, which is the one thing a command cannot do.
- **Map tab**: the mod's own map at one pixel per block, rasterised from the chunks you load as
  you play using the same colour table vanilla maps use, shaded by how the ground steps. The
  whole grid is drawn on top and nodes are edited by clicking them. Drag to pan, scroll to zoom.
  Terrain is stored as 512 block tiles on disk, so flying over the perimeter once is enough and
  changing the grid never throws the map away.
- **Blocks tab**: one button per beacon material, so picking emerald over iron is a click rather
  than typing an id.
- **Keys tab**: every binding of the mod, rebindable in place.
- English and Spanish, switched in the screen. Beacon stays beacon.
- When Litematica is installed, easy place follows its toggle instead of fighting it for the
  same click. Read by reflection, wrapped in try/catch, no code taken from it.

### Grid

- Concentric rings from a single centre point: 1, 9, 25, 49 nodes and up, `(2n+1)^2`.
- Sides can be grown on their own, because real perimeters are not square.
- 1 to 5 beacons per node on one shared pyramid, layer sizes `(2k+1) x (2k+n)`.
- Spacing follows the pyramid level by default (`2r + 1`, exact coverage), or is set by hand.
- Strips of ground with no coverage are painted red when the spacing is too wide.

### In the world

- **A beam on every beacon position**, red for missing, green for placed, grey for excluded. It
  runs to the sky like a real beacon beam and thickens with distance so it never thins out below
  a few pixels on screen: a perimeter is over a thousand blocks across, and a fixed size beam is
  a scratch from the far side of it. `G` turns the beams off.
- Coverage volumes drawn as vanilla computes them: `2r + 1` wide, `r` down, unbounded up.
- Rejoining a server reopens the plan you had open, instead of handing you an empty screen.
- Only what is on screen is drawn: nodes are tested against the view frustum, not just against
  distance, with a hard cap on top. A two thousand node grid no longer rebuilds two thousand
  boxes a frame for the ones behind you.
- Pointing at a node uses the block you are looking at before it falls back to the beacon plane,
  so it works from inside a trench and not only from the air.
- Slab, floor or full drawing styles, opacity, wireframe, see through, all configurable.
- No highlighting of schematic blocks. That is the noise this exists to avoid.
- Beacon positions and the pyramid footprint outlined per node.
- HUD with grid size, spacing, coverage fit, node states and what is missing.

### Editing in bulk

- Shift drag on the map marks a whole rectangle of nodes at once: left button drops them, right
  button marks them as outside the perimeter, control puts them back to pending. The rectangle
  and the nodes it catches are highlighted while you drag.
- Undo, on Ctrl + Z, a button on the map, a bindable key and `/bea undo`. Covers single clicks,
  rectangles and `/bea fill`, up to 64 steps back.

### Editing

- `B` toggles edit mode. Scroll grows the grid, shift scroll changes beacons per node, control
  scroll nudges the spacing.
- Left click drops a node, right click marks it as outside the perimeter with a real marker
  block on top. Both toggle back.
- `/bea fill` sets a rectangle of nodes at once.

### Building

- Live scan turns finished nodes green and shades half built ones towards green.
- Material list of what is needed and what is still missing, in total and per node.
- Assisted placement picks the right block from your hotbar and refuses to put plan blocks
  where the plan wants none.
- Layer filter to work one course at a time.

### Import and export

- `/bea detect` reads a perimeter that is already standing in the world, straight from the
  loaded chunks, with real coordinates.
- `/bea import` and `/bea export` for Litematica schematics. Our own files remember the world
  corner they came from; other people's land at your feet.
- Plans are saved per world under `config/beaconator/`.
