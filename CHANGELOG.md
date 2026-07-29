# Changelog

## 1.1.1

Adds **1.21.11**, and skips 1.21.9 and 1.21.10 on purpose.

Fabric API removed the world render events in 1.21.9, which is what an earlier version of
`docs/PORT-1.21.9-PLUS.md` called a wall that needed a mixin of our own. It did not: Fabric
redesigned the events and shipped them again in `0.137.0+1.21.10`, so the mod hooks a supported
API here as it always has. 1.21.9 never got them back on its own branch and its Fabric API stopped
in December, and 1.21.10 has the events but predates the renames of 1.21.11, so it would cost its
own substitution table and its own copies of four files. Neither is worth carrying.

The largest part of the port was a rename nobody had flagged: `ResourceLocation` is called
`Identifier` in 1.21.11, which took the four networking payload classes with it.

- **Line width survived.** `RenderSystem.lineWidth` is gone in 1.21.11, because a line's width
  stopped being a global switch and became an attribute of each of its vertices, read by vanilla's
  own line shader. The wireframe now goes through that shader, so the setting still does what it
  says, and it is honoured on hardware that used to ignore it and draw everything one pixel wide.
- The mouse and key handlers take event objects, the widgets draw their contents rather than
  themselves, and a cycling button is told its starting value when it is built. All of it is the
  same code underneath: `BeaconatorScreen` is still one file, not one per version.
- No change to anything on 1.21 through 1.21.8, which build and test exactly as before.

## 1.1.0

Ships for **1.21 through 1.21.8**, one jar per version, all from the same source.

Up to 1.21.4 vanilla had only renamed things, and `tools/multiversion.py` compiles a jar against
each version with a substitution table. 1.21.5 is where that stopped being enough: immediate mode
drawing is gone and blending, culling and depth are baked into pipeline objects declared up
front, so the drawing layer is written against that instead. 1.21.6 changed how a shader is fed
and needed it again. 1.21.7 and 1.21.8 came free.

The compiler does the checking, which matters because these builds are not play tested: a method
that does not exist fails the build rather than crashing someone's game. The 56 model tests run
on every version too. What no build can check is whether the rewritten drawing layer actually
draws, so 1.21.5 and up want a pair of eyes in game before they are trusted.

- **Excluded nodes change colour as you build them.** They get a pyramid, a beacon and a marker
  like everything else, but they were flat grey whatever their state, so there was no telling a
  finished one from one nobody had touched. On a site where half the nodes are excluded, that was
  half the perimeter you could not read. A finished one has its own colour, and the whole thing
  is a toggle if you would rather see the shape than the progress.
- **Colours are buttons, not sliders.** A palette has no more and no less to slide towards, and
  the bar drew half full whatever colour it was on. They wrap, they say the colour's name, and
  they show it.
- The stray `": "` in front of the beacons per node and pyramid level buttons is gone, and
  beacons per node goes to six, which the geometry already handled.
- The Display tab lays itself out from the height the screen actually has instead of a fixed 22
  pixels a row, so it stops running off the bottom of small screens.

## 1.0.0

First build.

### The screen

- Shift + B, `/bea`, or the Mod Menu entry. Every binding takes modifiers, so `G`, `Shift + G`
  and `Ctrl + Shift + G` are three different things.
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

### Sharing a plan with the server

- The same jar now runs on a Fabric server. Put it there and a **Server** tab appears listing
  the plans the server holds: press one to open it, Share mine to put yours up. From then on
  every node anyone places, excludes or drops shows up live for the rest, and the HUD says
  `[shared]` so you know your clicks are going out.
- Anyone with the mod can share a plan and mark nodes. Digging a perimeter is teamwork, and
  asking an admin to tick off each finished node would be worse than not sharing it at all.
- The plan lives in the world folder, so a copy of the world carries the perimeter with it.
- Client-side only, none of this runs and the mod behaves exactly as before. A vanilla client on
  a server that has the mod is never sent anything.

### Getting it right in the world

- **Yellow for half built.** A node that is started but not finished, including a pyramid that is
  up and one beacon short, no longer shades towards green and looks done from a distance.
- The beacons of a node take the **cheapest shape**, not a row: four go in a square for 216
  blocks against 236, five and six in a 2x3 for 244 against 260 and 284. Every rectangle that
  fits is costed and the best one wins.
- **Rotation in quarter turns** rather than an axis, so a node's beacons can hang off whichever
  side of it suits the perimeter.
- Optional **cap block on the nodes that stay in**, the way excluded ones get their marker.
- Colours are settings, on the Display tab, with dropped nodes finally separate from excluded
  ones.

### Grid

- Concentric rings from a single centre point: 1, 9, 25, 49 nodes and up, `(2n+1)^2`.
- Sides can be grown on their own, because real perimeters are not square.
- 1 to 6 beacons per node on one shared pyramid, layer sizes `(2k+a) x (2k+b)` for whatever
  rectangle the beacons need.
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
- Undo, on Ctrl + Z, a button on the map and a bindable key. Covers single clicks and whole
  rectangles, up to 64 steps back.

### Editing

- `B` toggles edit mode. Scroll grows the grid, shift scroll changes beacons per node, control
  scroll nudges the spacing.
- Left click drops a node, right click marks it as outside the perimeter with a real marker
  block on top. Both toggle back.
- Shift + drag on the map sets a rectangle of nodes at once.

### Building

- Live scan turns finished nodes green and half built ones yellow, on their own.
- Material list of what is needed and what is still missing, in total and per node.
- Assisted placement picks the right block from your hotbar and refuses to put plan blocks
  where the plan wants none.
- Layer filter to work one course at a time.

### Import and export

- **Detect from world** reads a perimeter that is already standing, straight from the loaded
  chunks, with real coordinates.
- Litematica import and export from the Plan tab. Our own files remember the world corner they
  came from; other people's land at your feet.
- Plans are saved per world under `config/beaconator/`.
