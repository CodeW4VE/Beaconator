# Plan: water lines from the perimeter to the middle

Read this first, it has the decisions already made and the ones still open.

## What we are building

A perimeter has a digsort in the middle and a hundred beacons around it. You throw shulkers in at
a beacon, and they should end up in the sorter. That means a network of underground water
streams, and Beaconator already knows where every node is, so it should work out the network,
draw it, count it and export it.

## What the user already decided

These are settled, do not reopen them:

- **Everything runs on the last layer before bedrock.** Not a chosen Y: the bottom one.
- **This is free real estate**, because the beacons already go as deep as they can. On the current
  plan the beacons sit at y = -55 and their pyramid goes down four layers to y = -59, which *is*
  the bottom layer. The network lives exactly where we are already digging.
- **Items enter at the beacon column.** The water starts at the beacon and runs down the side of
  the pyramid, carrying the item with it, and lands at the foot of the base where the channel
  passes. No structure on the surface, no stub of its own.
- **Packed ice under the water**, water flowing one way, **pressure plates to split the currents**.
- **What is being optimised is the trip, not the bill.** Given a choice between the network that
  costs least and the one that gets items to the middle soonest, it is the second one. This is the
  ruling that decides between layouts, and it is why the budget prints trip length, average trip
  and corners next to the materials.
- **You point at the block it drains into.** The digsort ends in a stream of water somewhere, and
  that block is picked by looking at it, the way a beacon is picked, not by typing coordinates.
  There is no sensible default here: the middle of a grid is usually inside the centre node's own
  pyramid.
- **The ice is a block like any other.** Packed ice is what we are building with, but it is picked
  from a list the same way the pyramid block is, so blue ice or plain ice cost the plan out
  without touching code.
- **Two maps, one page each.** One for the beacons and one for the currents. Whichever you are on
  is the one that lights up and the other goes dim, so a hundred nodes do not drown out the lines.
  On the currents map you draw and erase runs one block wide, and you can see them under the
  ground, because the point is knowing where to dig.
- **Drawing replaces the calculation, it does not sit on top of it.** If a generated run is wrong
  you delete it and draw yours; what is on the map is what gets built. The consequence to design
  around: regenerating throws away what was drawn, so regenerate has to be a button you press on
  purpose and not something that happens quietly when a node changes.
- **It lives inside Beaconator**, not in a separate mod, but on **its own page** of the screen.
  Think of it as turning the page: a second thing this tool does, on the same grid.

## What already exists that we get for free

| We have | Where | Why it matters |
| --- | --- | --- |
| Every node's exact position | `PerimeterPlan.nodes()` | the network's endpoints, already on a perfect grid |
| Which nodes are actually built | `PerimeterPlan.buildNodes()` | what the network has to reach, which is far fewer than the grid |
| The centre | `plan.centerX/centerZ` | the destination |
| The exact shape of every base | `PyramidCalculator.groupOffsets` | what the channel has to go around |
| Material counting | `MaterialTally` + the Materials tab | counts the network the same way it counts pyramids |
| Litematic export | `io/LitematicIO` | how the thing gets built, with Litematica |
| Rendering boxes and lines | `render/PerimeterRenderer` | drawing the network is the same job as drawing the grid |
| A map that draws the grid | `client/map/MapView` | the network on the map is a second layer on it |

What we do **not** have, despite an earlier note here saying we did: **terrain heights**. `MapTile`
reads a chunk's heights to shade the pixel and throws them away, so there is no heightmap to ask.
It would not help anyway: at y = -59 what matters is caves, lava and ancient cities, not the
surface, and the client only sees loaded chunks. Obstacle detection has to be a scan in the shape
of `WorldScanner`, which already reports anything unloaded as unchecked rather than as clear.

## The design questions, answered

The model is written (`model/water/`, pure and tested, no Minecraft), so most of these now have
numbers behind them instead of guesses.

1. **Network shape: fishbone, and it is not close.** With trips as the thing being optimised, the
   fishbone is not merely good, it is **optimal**: every item travels exactly its own Manhattan
   distance to the middle, turning one corner, and no channel without diagonals can do better. A
   test asserts that node by node. The greedy tree buys a 1.5% shorter network (10,160 blocks
   against 10,319) by making the worst trip **two and a half times longer** (2,317 against 908,
   with ten corners instead of one). Exactly the trade we are not making. `WaterLayout.TREE` stays
   in for the day someone plans a ring shaped perimeter, where it does win on length.
2. **Does every node get a line? Yes, and it is affordable.** The 289 node estimate in the first
   draft of this plan was the whole grid; a real perimeter has most of it removed. Big Culo has
   **100 live nodes**, and the full network is **10,319 blocks of channel, 92,871 ice to mine, 54
   shulkers**. That is a weekend of a frozen ocean, not a fantasy. Cutting to every second row
   halves the ice and strands 50 nodes, which is a bad trade at this price.
3. **Y of the network: the bottom layer, going around the bases.** There is no layer underneath:
   bedrock fills y = -64 and reaches up to -60 at random, so -59 is the deepest guaranteed
   diggable layer and the pyramid bases already sit on it. The channel therefore runs on that same
   layer, one block clear of every base. This costs nothing, because the water coming down the
   pyramid lands exactly there. `Corridors` models the lanes this leaves between the bases, and a
   test asserts that no run ever crosses a base.
4. **Rivers, ravines, caves, ancient cities: flag them.** Not built yet. It wants a scan along the
   planned channel over loaded chunks, reporting coordinates rather than routing around them.
   Same principle as a hand drawn run through a pyramid: say what is wrong, do not refuse.
5. **Water and plates: counted, not yet placed.** The budget already includes 1,306 buckets and a
   flow stop per source and per junction. Placing them is the last phase on purpose.

## Where this stands

**Done: the model and the budget.**

- `model/water/WaterSpec` the knobs, `WaterLayout` the shape, `WaterSegment` a run,
  `Corridors` the lanes between the bases, `WaterNetwork` the network, `WaterBudget` the cost.
- Pure `model/` code, so it ports to any Minecraft version for free like the rest of it.
- `WaterNetworkTest` covers the rule that matters (no run through a base), connectivity, partial
  coverage and the ice arithmetic.
- `RealPlanBudgetTest` prices a saved plan from the command line:

  ```
  ./gradlew test -Dbeaconator.plan=config/beaconator/<world>/<name>.json
  ```

Big Culo, 100 live nodes at 101 spacing, level 4:

| shape | channel | ice to mine | shulkers | buckets | longest | average | corners | ideal |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| fishbone, every row | 10,319 | 92,871 | 54 | 1,306 | **908** | 498 | 1 | 908 |
| fishbone, every 2nd row | 5,509 | 49,581 | 29 | 698 | 797 | 478 | 1 | 797 |
| fishbone, every 3rd row | 3,875 | 34,875 | 21 | 491 | 819 | 470 | 1 | 819 |
| tree, every row | 10,160 | 91,440 | 53 | 1,487 | 2,317 | 1,177 | 10 | 903 |
| fishbone, blue ice | 10,319 | 835,839 | 484 | 1,306 | 908 | 498 | 1 | 908 |

The last column is the shortest trip physics allows, so a layout that matches it is wasting no
time at all: the fishbone does, on every row. Ice is what has to be **mined**, not what is placed:
packed ice is nine blocks of ice each, blue ice is eighty one. Blue ice buys nothing in that table
because distance is the same, so it is only worth arguing about if the ice under the water turns
out to change how fast an item actually moves, which is a thing to settle in game.

**Next, in order.**

1. **The network becomes a document.** Right now `WaterNetwork` is computed on the spot from the
   plan. Drawing on it means it has to be a thing that is kept and saved next to the plan: the
   runs, the drain, the spec. Generate fills it in, the map edits it, the file remembers it.
2. **The drawing, and drawing on it.** The network in the world and on the currents map, with the
   beacons dimmed, runs one block wide, erase and redraw, visible through the ground. A run drawn
   through a pyramid base is drawn in red rather than refused: the mod says what it would break
   and the call is yours.
3. **The count.** The budget in the Materials tab, next to the pyramids.
4. **The blocks.** Ice, floor and the drop from each beacon, exportable as a litematic.
5. **Water and plates.** Last on purpose: it is the only part whose correctness the compiler
   cannot check.

## What worries me

- **I cannot test the flow.** Water is decided by the game. A channel that is right on paper can
  stop an item at a junction, and the only way to find out is to build it. Everything above is
  designed so this only bites at the very end.
- **Junctions.** There is no room to step down towards the middle at the bottom layer, so the
  current is made by sources and stopped by plates, and every place two runs meet head on is a
  place items can pile up. The budget counts them; nothing has proved one works yet.
- **Feature creep in the screen.** The screen already has eight tabs. This needs to be its own
  page with its own tabs, not four more buttons crammed into Display. `docs/UX.md` applies: text
  that fits, greyed out when unavailable, saves itself.

## Naming

The user called it "Beaconator plus". Inside the mod it is the same jar and the same plan file;
what changes is a second page. Suggested: a **Water** tab that opens its own set of tabs, the same
way the Map tab owns the whole screen when you are on it.
