# Plan: water lines from the perimeter to the middle

For the next session. Read this first, it has the decisions already made and the ones still open.

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
- **Items enter at the beacon column.** They drop the four blocks from the beacon down to the
  stream, so the entry point is the node itself, no extra structure on the surface.
- **Packed ice under the water**, water flowing one way, **pressure plates to split the currents**.
- **It lives inside Beaconator**, not in a separate mod, but on **its own page** of the screen.
  Think of it as turning the page: a second thing this tool does, on the same grid.

## What already exists that we get for free

Worth knowing before designing anything:

| We have | Where | Why it matters |
| --- | --- | --- |
| Every node's exact position | `PerimeterPlan.nodes()` | the network's endpoints, already on a perfect grid |
| The centre | `plan.centerX/centerZ` | the destination |
| Terrain heights | `client/map/MapTile` keeps a heightmap per chunk | tells us where a line would surface or hit a cave |
| Node states | `NodeStatus` | dropped nodes need no line; excluded ones might |
| Material counting | `PerimeterPlan.tally()` + the Materials tab | counts the network the same way it counts pyramids |
| Litematic export | `io/LitematicIO` | how the thing gets built, with Litematica |
| Rendering boxes and lines | `render/PerimeterRenderer` | drawing the network is the same job as drawing the grid |
| A map that draws the grid | `client/map/MapView` | the network on the map is a second layer on it |

Roughly: the hard parts of a planning tool are already written. This is a new thing to plan, not
a new tool.

## The design questions to settle first

Bring answers to these and the rest is typing.

1. **Network shape.** Fishbone (each row collects into its column, columns run to the centre) or
   spanning tree (every node joins the nearest line)? A fishbone on a regular grid is within a few
   percent of optimal, is trivial to reason about, and is far easier to build in order. **My
   recommendation: fishbone.**
2. **Does every node get a line?** With 289 nodes at 101 spacing the full network is around
   **27,000 blocks of channel**, and in packed ice that is **a quarter of a million ice blocks**.
   Perhaps only some rows get a line, or there are zone collectors and you walk the last stretch.
   **This is the question that decides whether the feature is useful or a fantasy.**
3. **Y of the network.** The bottom layer is stated, but the pyramid bases already occupy it at
   every node. Does the stream run *through* the pyramid footprint, around it, or one layer below
   the pyramid base? This one needs looking at in game.
4. **What happens at a river, a ravine, a cave, an ancient city.** Wall it off, route around it,
   or flag it and let a human decide? **My recommendation: flag it.** The mod knows where the
   line goes and can tell you it crosses something at those coordinates, and you deal with it.
5. **Do we place the water, or only the ice and the walls?** Water and pressure plates are where
   the "it compiled but the items stop at the corner" risk lives.

## What I would build, in order

**Phase 1, the plan and the drawing.** The network as data, drawn in the world and on the map like
the grid already is. No blocks, no export. This is what tells us whether the shape is right, and
it is a day.

**Phase 2, the count.** The material list for the network, alongside the pyramids. This is what
answers question 2 above, and it is where the tool earns its keep before a single block is placed.

**Phase 3, the blocks.** Ice, walls, floor, and the drop from each beacon down to the stream, as a
schematic. Exportable to litematic, buildable with Litematica.

**Phase 4, water and plates.** Last on purpose. It is the only part whose correctness the compiler
cannot check and I cannot verify without the game running it. Everything before this is useful
even if we never do it.

## What worries me

- **I cannot test the flow.** Water is decided by the game. A channel that is right on paper can
  stop an item at a junction, and the only way to find out is to build it. Phases 1 to 3 are
  designed so that this only bites at the very end.
- **The scale.** See question 2. The right answer might be "not the whole thing".
- **Feature creep in the screen.** The screen already has eight tabs. This needs to be its own
  page with its own tabs, not four more buttons crammed into Display. Whatever we do, `docs/UX.md`
  applies: text that fits, greyed out when unavailable, saves itself.

## Naming

The user called it "Beaconator plus". Inside the mod it is the same jar and the same plan file;
what changes is a second page. Suggested: a **Water** tab that opens its own set of tabs, the same
way the Map tab owns the whole screen when you are on it.
