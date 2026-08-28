# Changelog

## 2.3.1

**Your keys stay where you put them.** Opening the screen used to be a special case inside the edit
mode key rather than a binding of its own, so when it became one it needed a repair for anyone
already playing: `options.txt` remembers an unbound binding as firmly as a bound one, and that
saved "unknown" beats any new default. The repair worked. It also ran on every single launch, and
"unbound" is exactly what a player who cleared that key on purpose has, so clearing it, closing the
game and finding it back on `B` was the whole experience of trying to get rid of it. Vanilla paints
both `B` bindings red as a conflict, which is a good reason to want one gone.

- **It runs once now**, and writes that down in `beaconator.json` whether or not the key needed
  moving. A one time repair is a repair; the same repair every launch is the mod overruling its own
  user.

## 2.3.0

**Everything flows to the drain.** The drain is the block you point at and press the button on,
and it is the only place in the network that means anything: every current exists to get an item
there. The sources and plates did not know that. They were worked out by following each run from
whichever end it happened to be stored at, and a run is stored in the order it was drawn or laid,
so half of them pointed the wrong way. That is where the sources pushing away from the middle came
from, and the plates dropped into the middle of a straight stretch where nothing was meeting
anything.

- **They are worked out on the tree of blocks rooted at the drain now**, walked from every dead end
  down to the middle, so downstream means downstream everywhere and a plate only lands where two
  currents genuinely meet. There is a test that checks every single fitting of a real perimeter
  gets closer to the drain, which is the check that was missing.
- **Nothing is placed on the drain itself.** It is the end of the line: a source there has nowhere
  to push but backwards.
- **Water you already poured counts.** Before drawing a fitting the mod asks the world which way
  the water in that block is already going, and if it is already heading for the drain there is
  nothing to propose: the channel works as built. Same for a plate that is already down. A finished
  stretch stops being drawn, the way a dug stretch already did.

**Two rules the channel now keeps everywhere.** Both come from building one rather than drawing
one, and both are checked against a whole perimeter in the tests.

- **No two sources ever touch.** Water next to water is one pool with no current in it, which is
  the one way to build this that moves nothing at all. There is always a plate between one source
  and the next.
- **No block of channel is left empty.** Every single one ends up with water in it or a plate on
  it. Water reaches seven blocks past its source whatever the spacing says, so anything beyond that
  gets a plate rather than a bare block of ice, which is the hardest thing in a finished network to
  find once drops start going missing. Big Culo: 8,514 blocks of channel, 7,417 wet, 1,097 plated,
  nothing empty. Widening the source spacing now trades buckets for plates instead of thinning the
  channel out.
- Where the two rules disagree, the border wins: a gap on a chunk border is filled with water, never
  with a plate.
- **No more plates in threes.** Two branches ending a block apart asked for a plate each and got
  them. Past the first, they were cutting a current that had already been cut.

**Chunk borders.** Flowing water pushes an item every tick; bare ice only keeps the speed it
already had, and a chunk that unloads and comes back does not give it back. Out at the far end of
a perimeter, unloaded is the normal state, so the channel now treats a border as the one part of
the network that is not a matter of taste.

- **A source on the last block before every border**, with the spacing restarting there, so the
  item crosses under power instead of coasting. Big Culo: 532 crossings, thirteen buckets more than
  the old spacing, because the count restarts rather than adding to it.
- **A plate never lands on either side of a border.** One that would has walked back upstream to
  the first free block. Same job, one block earlier.
- **A corner on a border gets a red post and a cross on the map**, and is left where it is: moving
  it means moving the run and every trip through it. Big Culo has three.
- **The Cost tab counts the borders**, in green when they are all under moving water.

**The plates go where the bill was already charging for them:** one block upstream of every source,
where two currents meet, plus one at the end of each run. The bill said eleven hundred and the
world drew twenty nine; now they are the same plates. Sources are all placed before any plate is,
so a plate on a block two runs share cannot take the block the other run's source needed.

**Ghost blocks, and the plates are part of the schematic now.** Walk up to a plate and the mark
turns into an actual pressure plate, the block with its own model and its own texture, sitting in
the square it goes in. The game draws it, the same way a schematic mod does. Step back and it is a
post again, because a plate seen from four hundred blocks away is nothing at all.

- **It disappears when you place it.** A ghost is a thing that is missing, so the moment the plate
  is in the world the mod stops drawing one there, and a finished stretch of channel looks
  finished. Same for a source once the water is in. That is the whole reason to draw them.
- **Assisted placement handles plates.** They are part of what the plan wants now, so easy place
  puts one in your hand and lets you place it where the network asked for one, and with strict
  placement on it stops you putting one anywhere else. Only while the plan has a channel: a
  perimeter with no water lines has no opinion about your plates. Water is still yours to pour, a
  bucket not being a block placement.
- A source stays a translucent box, block sized and at the height water sits at. Fluids do not go
  through the block renderer at all, so there is no block to ask the game for.
- Not on 26.x. The single block call the rest of this rides on does not exist there any more. The
  marks stay marks on that jar; everything else, the schematic included, is identical.

**Following Litematica no longer talks over it.** Mirroring their easy place toggle wrote a line of
our own on the action bar, which is where they had just written theirs, so pressing their key
showed their message and then ours on top of it. It syncs quietly now. The state is on the HUD.

## 2.2.0

**The channel colours are the channel's own.** Turning the water lines down, and turning them any
colour you like, both work now.

- **Every state of a stretch has its own colour.** Dug-but-unfloored and iced used to borrow the
  node colours from the Display tab, so the one button that said it coloured the channel moved the
  cyan and left the yellow and the green exactly as they were. Five colours, each labelled with the
  state it paints: **Not dug**, **Dug, no ice**, **Iced**, **Bad run**, **Drain**.
- **An opacity setting for the water lines.** It existed in the config file and was never on screen.
  The outline of a stretch follows it too instead of staying pinned at full strength, which is what
  made turning it down look like it did nothing.
- **A Look tab** on the Water page, between Setup and Cost, for how the channel is drawn: show it or
  not, opacity, the five colours, and the sources-and-plates proposal. Setup keeps what the network
  is made of. The colours were squeezed into Setup's right hand column, which is why there was room
  for one of them and not three.

**Minecraft 26.1.2, and what 2.1.0 was supposed to be.** 2.1.0 was tagged and never published: its
release failed on the 26.x builds and nothing was uploaded, so this is the first release since
2.0.0 and it carries both.

- **A jar for 26.1.2**, which is the first Minecraft that ships unobfuscated. Everything about
  building for it is different: there are no mappings to remap against any more, the build script
  uses the plugin that does not remap, and the game wants Java 25. **Treat it as a beta.** It
  compiles clean against the real 26.1.2 API, and unlike the 1.21 jars nobody has had it in a world
  yet.
- **26.2 is not here yet.** It replaced the path a mod's own geometry takes to the screen, not just
  the names on it, and that is a rewrite rather than a port. It is next.
- Every jar now declares the one version it was compiled for again. Since 2.1.0 they all claimed
  `>=1.21 <26.3`, which let Fabric load a 1.21 jar on a version it was never built against.

## 2.1.0

**Minecraft 26.1 and 26.2.** The mod now ships for the new version series. The jump from 1.21.11
to 26.x is the largest since 1.21.5 and the port starts from the 1.21.11 rendering layer;
compile-and-fix iteration is the way through.

- Ships for Minecraft **26.1.2** and **26.2**, each compiled against its own Fabric API.
- The variant files that draw in the world start from the 1.21.11 base; changes in the rendering
  API between 1.21.11 and 26.x will surface as compiler errors and get fixed one by one.
- Fabric Loom is pinned to a stable release (`1.17.17`) instead of a snapshot.
- The `fabric.mod.json` version range now covers `>=1.21 <26.3` so the jar loads on the new
  versions without manual editing.

## 2.0.0

**The water lines.** A perimeter is only half the build: the other half is the channels that carry
what you throw in at a beacon to the digsort in the middle. The mod now plans them, and it has its
own page for it.

- **A Water page**, not a ninth tab. Nine tabs did not fit in one row, and the water lines are not
  a ninth setting of the same thing anyway: they are the perimeter seen from underneath. Press
  **Water >** at the end of the tab row and the whole screen turns the page, to **Currents** (the
  map you draw on), **Setup** (drain, shape, ice, how far a source carries, colours) and **Cost**
  (the whole bill). **< Beacons** brings you back.
- **Its own map.** Same view as the beacons map, so turning the page keeps you
  looking at the same piece of the world, but the nodes step back and the channels come forward:
  a run one block wide has no chance against a hundred coverage boxes at full strength. The
  beacons themselves are drawn bigger rather than dimmer, and every pyramid **base** is outlined,
  because those are what the channel has to fit between.
- **The first time you open it, it says who this is for.** This part of the mod assumes you are
  building a digsort, and anybody who is not deserves to be told so once rather than left to work
  it out from a screen full of jargon. Once, and then never again; there is a button on the tab to
  read it back.
- **Excluded nodes get no channel.** They are outside the perimeter: nobody throws shulkers in at
  one, so running ice out to them was digging for nothing. They are still drawn and still avoided,
  because their pyramid sits on the channel's own layer. On a real perimeter with nineteen of them
  that is 1,700 blocks of channel and nine shulkers of ice saved.
- **Generate**, and you get the whole network worked out from the plan: one spine per row, one
  trunk to the drain, every run a block clear of every pyramid base. The shape is a fishbone
  because what is being optimised is the trip and not the bill: every item travels its own exact
  Manhattan distance to the middle around a single corner, which nothing without diagonals beats.
  The tree shape is a button away for the perimeters where it wins.
- **Draw your own.** Turn Draw on and drag: runs are one block wide and snap to the axis, right
  click erases one whether Draw is on or not. What is on the map is what gets built, so a run you
  draw is not an override sitting on top of a calculation. That is also why **Generate throws away
  what you drew**, and asks first when there is anything to lose.
- **You point at the drain.** The middle of a grid is not a place: it is usually inside the centre
  node's own pyramid, and the sorter is wherever it actually got built. Aim at the block the water
  leaves by, open the screen, press the button.
- **The count, in the Materials tab**: channel, ice, buckets, pressure plates, junctions, and the
  shulkers of ice you actually have to mine, which is nine times the ice you place for packed and
  eighty one for blue. On a real hundred node perimeter that is the difference between a weekend
  and a fantasy, and it is better to know before the first block goes in.
- **It tells you what is wrong rather than refusing.** A run dug through a pyramid base is drawn in
  red, a node the channel never reaches gets a cross, and a network that does not join up says how
  many nodes are stranded. Your call, every time.
- The channels are drawn in the world at the layer they will be dug on, so you can stand in the
  hole and see where the next one goes. Toggle and colour on the Display tab.
- The water lines are saved with the plan and travel with it: opening a shared plan brings its
  channels, and editing them on a shared plan sends them up when you close the screen.

- **The channel reads itself back out of the world.** Every block of it is scanned as you get near,
  the same way the pyramids are, and drawn by what it is waiting for: cyan is still solid rock,
  yellow is dug out with no ice under it, green is floored and waiting for water, and **a stretch
  with water in it stops being drawn at all**. So the map empties as the thing gets built, and the
  line that is left is the work that is left. Blocks too far away to read are never counted as
  missing.
- **Erasing takes out the stretch between crossings**, not the whole eight hundred block spine.
- **A node whose beacon already has water on it gets a ring** on the currents map: that is the drop
  into the channel working, and it is the one part of it that can be checked from a distance.
- **Sources and plates can be shown** (Setup, off by default): a source at the head of each run and
  every few blocks after it, a stop where each run flows into another. Marked as a proposal
  everywhere it appears, because nothing about those positions has been checked against a channel
  that runs. Dig one stretch, see what the water really does, then trust them.
- **The channel's Y is yours to set**, not fixed at the bottom layer.

Not in this release, on purpose: the network does not export to a litematic yet, and the water
sources and pressure plates are counted rather than placed. Those are the parts a compiler cannot
check, and they wait until the shape has been dug in a real world.

## 1.3.0

- **A node can be moved on its own.** Some node always lands where a beacon cannot go, and until
  now the only answer was to nudge the whole grid and move the other two hundred with it. Turn
  **Move** on in the map, or hold alt, and drag that one node: everything else stays put. It snaps
  back onto its own row or column within four blocks, so a nudged node normally stays lined up and
  the water channel that serves its row stays straight; hold control to place it anywhere, or use
  the arrow keys for the last block or two. The map draws a line back to the cell it came from.
  Moved nodes travel to the rest of the team on a shared plan like any other change.
- A node moved off its row is left out of the water network rather than dragging that row's
  channel across the perimeter to reach it. It still gets its beacons and its pyramid.

## 1.2.0

- **The scan stopped unbuilding the plan.** A scan that could not find the beacons of a node used
  to downgrade it from placed back to pending, rewriting the plan as chunks arrived and losing the
  green for good. A node that reads as empty is far more often a plan pointed at the wrong spot
  than a dismantled perimeter, so the progress colour says so instead and the plan is left alone.
  Unloaded chunks no longer overwrite the last real reading either, and hovering a node shows what
  the scan actually saw.
- **A master switch**, for when you want the mod installed and quiet.
- **Marking one node no longer greys out every other one.** A click wiped the whole scan cache, so
  every finished node lost its progress colour at once; only the node you touched is stale now.
- The water network of a perimeter is modelled and costed underneath: the channels that carry what
  you throw in at a beacon to a sorter, on the one layer left free between the pyramid bases.
  Nothing surfaces it in the screen yet.

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
