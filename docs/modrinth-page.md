<!-- The Modrinth page body. Edit here, then PATCH it to the project. -->

# Beaconator

**Plan and build beacon perimeters.** Put the centre down, and Beaconator works out where every
other beacon goes, draws how much ground each one actually covers, tracks what you have already
built, and tells the rest of your team about it.

Built for spawn proofing large areas, by people who were doing it with a schematic mod and a
spreadsheet.

![A perimeter from the air, with a beam over every beacon](https://raw.githubusercontent.com/CodeW4VE/Beaconator/main/docs/img/01-beams.jpg)

---

## Why not just use a schematic mod

Schematic mods are good at schematics. A perimeter is not really a schematic problem:

- They highlight **every** block of the schematic, air and dirt included. That is noise you have
  to look past on a build made of two hundred identical pyramids.
- Nothing shows you the area a beacon **actually covers**, so you find the holes in your
  perimeter by walking into one and getting shot.
- Telling apart what is built from what is not means comparing against the schematic by eye,
  node by node, for hundreds of nodes.
- The plan lives on one person's computer. Everyone else is guessing.

Beaconator only does perimeters, so it can do them properly.

## What you get

### Beams that go to the sky

Every beacon position gets a beam, exactly like a real beacon, so you can see where to dig from
the far side of the site:

| Colour | Meaning |
| --- | --- |
| 🔴 Red | Nothing built here yet |
| 🟡 Yellow | Started but not finished, **including a pyramid that is up and one beacon short** |
| 🟢 Green | Done, checked against the world |
| ⚪ Grey | Deliberately outside the perimeter |

That yellow is the one that earns its keep. A node missing its second beacon looks finished from
any distance, and you find out when the perimeter does not spawn proof.

![Red beams over what is left, green over what is done](https://raw.githubusercontent.com/CodeW4VE/Beaconator/main/docs/img/04-beams-close.jpg)

The beams thicken with distance so they never thin out to a single pixel, because a perimeter is
over a thousand blocks across and that is exactly where you are looking at them from. Press a key
to hide them when they are in the way.

### A map to plan on

The mod draws its own map, one pixel per block, from the chunks you load as you play, with the
whole grid on top. That is how a perimeter actually gets planned: looking down at the shape, not
squinting at boxes from the ground.

Click nodes to drop them or mark them excluded. Shift + drag does whole rectangles. Ctrl + Z
undoes. Arrow keys nudge the grid a block at a time, because being three blocks off is the
difference between a beacon that fits and one that does not.

Tiles are cached on disk, so flying over the site once is enough.

![The map tab with the whole grid on it](https://raw.githubusercontent.com/CodeW4VE/Beaconator/main/docs/img/02-map.jpg)

### Coverage as vanilla computes it

Each node is drawn as the volume the game actually applies effects in: `2r + 1` wide, `r` down,
and unbounded upwards. Not a cube, because it is not a cube. Set the spacing to `2r + 1` and the
coverage lines up exactly, with no overlap and no holes, and the screen tells you when it does
not. Strips of ground that get no effect are painted red.

### The cheapest pyramid, worked out for you

Beacons on one shared pyramid make layer `k` into `(2k + a) x (2k + b)`, where `a x b` is the
rectangle the beacons sit in. The shape matters more than people expect:

| Beacons | In a row | Best shape | Blocks saved per node |
| --- | --- | --- | --- |
| 4 | 236 | **216** (2x2) | 20 |
| 5 | 260 | **244** (2x3) | 16 |
| 6 | 284 | **244** (2x3) | 40 |

Across three hundred nodes, four beacons in a square instead of a row is six thousand blocks you
do not have to farm. Beaconator costs every rectangle that fits your beacons and uses the
cheapest one.

A curiosity that falls out of the maths: five and six beacons need the **same pyramid**, since
both live in a 2x3. There are only five primary effects, so the sixth is a spare that costs one
beacon and no blocks at all.

### One plan for the whole team

Put the same jar on your Fabric server and a **Server** tab appears. Share your plan, everyone
else opens it from a list, and from then on every node anyone places, excludes or drops shows up
live for the rest. Anyone with the mod can mark nodes: the people digging a perimeter are a team,
and asking an admin to tick off every finished node would be worse than not sharing it at all.

The plan is stored in the world folder, so a copy of the world carries the perimeter with it.
A vanilla client on a server that has the mod is never sent anything.

### Everything else

- **Live scan.** Nodes go green on their own as you build them. You tell the mod nothing.
- **Material list** of what the plan needs and what is still missing, in the HUD and in full.
- **Assisted placement** that picks the right block out of your hotbar, and refuses to let you
  put plan blocks where the plan wants none. It follows Litematica's easy place toggle when
  Litematica is installed, instead of fighting it for the same click.
- **Mixed pyramids count.** Built out of whatever iron, gold and diamond you had lying around?
  The scan accepts any beacon base block, so a working frankenstein pyramid reads as done.
- **Layer filter** to work one course at a time, and it stays where you put it between sessions.
- **Litematica import and export**, so you can pick up a perimeter you already built and hand the
  schematic to people who do not run this.
- **English and Spanish**, switched in the screen.
- **Every key rebindable, with modifiers.** `G`, `Shift + G` and `Ctrl + Shift + G` are three
  different bindings, and the screen tells you when a key is already taken by something else.

![The plan tab](https://raw.githubusercontent.com/CodeW4VE/Beaconator/main/docs/img/03-plan.jpg)

## Installing

Drop the jar in `mods/`. Needs Fabric Loader and Fabric API.

Optional: put **the same jar** on the Fabric server for the shared plans. Nothing else changes if
you do not.

## Getting started

1. **Shift + B** opens the screen. `B` toggles edit mode.
2. **Plan → New plan here**, standing roughly in the middle. Already half built? **Detect from
   world** reads the beacons that are already there, with their real coordinates.
3. **Map tab** to shape it: click the nodes you do not want.
4. **Grid tab** for beacons per node, level and spacing. Leave *Spacing follows level* on.
5. Go dig. Follow the red beams; they turn green behind you.

## Source

[github.com/CodeW4VE/Beaconator](https://github.com/CodeW4VE/Beaconator), MIT.

The grid maths is plain Java with no Minecraft in it, covered by 56 tests that run without the
game, including one that checks no other beacon arrangement beats the one the mod picks.
