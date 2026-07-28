# Beaconator

[![Build](https://github.com/CodeW4VE/Beaconator/actions/workflows/build.yml/badge.svg)](https://github.com/CodeW4VE/Beaconator/actions/workflows/build.yml)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Minecraft](https://img.shields.io/badge/minecraft-1.21-green.svg)](https://fabricmc.net/)

A Fabric mod for planning and building **beacon perimeters**. Client side on its own; put the
same jar on the server and the whole team shares one plan.

Drop one beacon in the middle, scroll to grow the grid, and Beaconator works out where every
other beacon goes, draws exactly how much ground each one covers, and keeps track of which ones
you have already built.

[Español](README_es.md)

## Why

Schematic mods do the job, but for this one task they get in the way:

- Every schematic block is highlighted, air and dirt included, which is noise you have to look past.
- Nothing shows the area a beacon actually covers, so you find the holes in your perimeter by walking into them.
- Telling apart what is built from what is not means comparing against the schematic by eye.

Beaconator only does perimeters, so it can do them properly.

## What it does

- **A map you can plan on.** The mod draws its own map, one pixel per block, from the chunks you load as you play, and puts the whole grid on top of it. You decide which nodes are in, which are out and which get dropped by clicking on them. That is how a perimeter actually gets planned: looking down at the shape, not squinting at boxes from the ground a hundred blocks away. Tiles are cached on disk, so what you have flown over stays drawn.
- **Grid from one point.** Place the centre, scroll to step through concentric rings: 1 node, 9, 25, 49. Sides can be grown on their own when the perimeter is not square.
- **Real coverage volumes.** Each node is drawn as the volume vanilla actually applies effects in. That is `2r + 1` blocks wide, `r` blocks down, and unbounded upwards, not a cube.
- **1 to 5 beacons per node** on one shared pyramid, so you can run every primary effect at once. The layer sizes and block counts are worked out for you.
- **Node states.** Left click drops a node from the plan, right click marks it as outside the perimeter, which puts a black stained glass on top so it reads as "not one of ours" from far away.
- **Live scan.** Nodes turn green on their own as you build them, and half built pyramids shade towards green as their blocks go in.
- **Material list** of what the plan needs and what is still missing.
- **Assisted placement** that picks the right block out of your hotbar and refuses to let you put plan blocks where the plan wants none.
- **Layer filter** to work one course at a time.
- **Litematica import and export**, so you can pick up a perimeter you already built and hand the schematic to people who do not run this mod. If Litematica is installed, easy place follows its toggle instead of fighting it.
- **A plan shared by the server.** Drop the jar on a Fabric server and one plan belongs to
  everyone: it arrives when you join, and every node anyone places, excludes or drops shows up
  live for the rest. Publishing it is an operator's call, marking nodes is anybody's. Without the
  mod on the server nothing changes, and a vanilla client is never sent anything.
- **English and Spanish**, switched from the screen rather than from the game language.
- **Every key rebindable** from the mod's own Keys tab, and the screen opens from Mod Menu too.

## Requirements

- Minecraft 1.21
- Fabric Loader 0.16 or newer
- Fabric API

Client side only. Nothing is sent to the server and nothing needs installing on it.

## Getting started

Press **shift + B** to open the screen, or bind a key of your own. Everything lives there: the
map, the grid settings, the blocks, the material list and the display options.

- **New plan here** on the Plan tab starts one centred where you stand.
- **Detect from world** reads a perimeter that is already built, straight out of the loaded
  chunks, with real coordinates. This is the way to pick up an existing one.

On the **Map** tab: drag to pan, scroll to zoom, left click drops a node, right click marks it
as outside the perimeter. The terrain fills itself in as you fly over it and is kept between
sessions.

Marking two hundred nodes one at a time is nobody's idea of fun, so drags do it in bulk:

| Input | What it does |
|-------|--------------|
| Shift + drag, left button | Everything in the rectangle is dropped from the plan |
| Shift + drag, right button | Everything in the rectangle is marked as outside the perimeter |
| Ctrl + drag | Everything in the rectangle goes back to pending |
| Ctrl + Z, or the Undo button | Puts back the last change, single node or whole rectangle |

In the world, with edit mode on (`B`):

| Input | What it does |
|-------|--------------|
| Scroll | Grow or shrink the grid |
| Shift + scroll | Beacons per node, 1 to 5 |
| Ctrl + scroll | Nudge the spacing |
| Left click a node | Drop it from the plan, or put it back |
| Right click a node | Mark it as outside the perimeter, or put it back |

The commands below do the same things and are still there if you prefer typing.

## Keys

Every binding is rebindable, either from the game's own controls screen or from the mod's
**Keys** tab. Only the first one has a key out of the box.

| Binding | Default |
|---------|---------|
| Edit mode, shift for the screen | `B` |
| Open the screen | unbound |
| Set the centre where you stand | unbound |
| Toggle render | unbound |
| Toggle easy place | unbound |
| Layer up · Layer down | unbound |
| Scan the world | unbound |

## Commands

Everything lives under `/bea` (or `/beaconator`).

| Command | What it does |
|---------|--------------|
| `/bea gui` | Open the screen |
| `/bea new <name>` | Start a plan centred where you stand |
| `/bea detect [name] [radius]` | Build the plan from beacons already standing in the world |
| `/bea move <dx> <dy> <dz>` · `center [x y z]` | Move the plan |
| `/bea open <name>` · `list` · `save` · `delete <name>` · `close` | Manage saved plans |
| `/bea info` | Everything about the current plan, materials included |
| `/bea ring <n>` | Set the grid to a concentric square |
| `/bea side <north\|south\|east\|west> <n>` | Grow or shrink one side |
| `/bea beacons <1-5>` | Beacons per node |
| `/bea level <1-4>` | Pyramid level, which sets the reach |
| `/bea spacing <n>` · `spacing auto` | Distance between nodes |
| `/bea axis <x\|z>` | Which way beacon rows run |
| `/bea block pyramid <id>` · `block marker <id>` | Blocks to build with |
| `/bea marker <on\|off>` | Whether excluded nodes get a real marker block |
| `/bea scan` | Check the whole plan against the world |
| `/bea materials [node]` | What is needed and what is missing |
| `/bea state <pending\|excluded\|removed>` | Set the node you are pointing at |
| `/bea fill <status> <fromI> <fromJ> <toI> <toJ>` | Set a rectangle of nodes at once |
| `/bea layer <all\|here\|y [toY]>` | Layer filter |
| `/bea easyplace <on\|off>` | Assisted placement |
| `/bea import <file>` · `export [file]` | Litematica schematics, from `schematics/` |
| `/bea render <on\|off>` · `style <slab\|floor\|full>` · `hud <on\|off>` | Display |

## The geometry, if you are curious

A beacon with `level` pyramid layers reaches `10 * level + 10` blocks, so a full level 4 pyramid
covers a square `101` blocks a side. Set the spacing to that and coverage lines up exactly with
no overlap and no holes. Anything wider leaves strips of ground with no effect, which Beaconator
paints red so you notice before you are standing in one.

Put `n` beacons in a row on one pyramid and layer `k` becomes `(2k + 1)` by `(2k + n)`. Five
level 4 beacons need a 9x13 base, 260 blocks, instead of five separate 9x9 pyramids.

The maths lives in `xyz.w4ve.beaconator.model`, has no Minecraft in it, and is covered by unit
tests against a real 208 node perimeter.

## Building

```
JAVA_HOME=/path/to/jdk-21 ./gradlew build
```

The jar lands in `build/libs/`.

## Licence

MIT. Behaviour was taken as inspiration from the schematic and info HUD mods everyone uses, but
no code was copied from any of them.
