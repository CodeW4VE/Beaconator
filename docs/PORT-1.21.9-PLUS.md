# Porting past 1.21.8: what actually happened

We ship **1.21 through 1.21.8 and 1.21.11**. This was the briefing for the day we sat down to go
further; it is now the record of that day, rewritten against what the compiler said rather than
what the jars looked like from outside.

Read [PORT-1.21.5-PLUS.md](PORT-1.21.5-PLUS.md) first. It predicted a day of work and a renderer
rewrite for 1.21.5, and got two hours and two files. This document made the opposite mistake, and
the lesson is the same one: **before believing a port estimate, compile and count.**

## What the first version of this document got wrong

It opened with a wall: Fabric API had deleted `WorldRenderEvents` in 1.21.9, so the mod had
nowhere to hook, and the port needed a mixin of our own into `LevelRenderer` — the first piece of
this whole port that a green build could not vouch for.

That was true of 1.21.9 and only 1.21.9. Fabric tracked it as
[issue #4902](https://github.com/FabricMC/fabric-api/issues/4902), redesigned the events in
[PR #4906](https://github.com/FabricMC/fabric-api/pull/4906), and shipped them in
**`fabric-api 0.137.0+1.21.10`**. No mixin was written. The ten minute check the plan opened with
was the whole of the wall.

Two facts settled the target:

- **1.21.9 never got them back.** The redesign landed on the 1.21.10 branch. 1.21.9's last Fabric
  API is `0.134.1`, from December. It is a version to skip, not to port to.
- **1.21.11 is the live one.** Its Fabric API is still moving, and its API is identical to
  1.21.10's apart from mappings — Fabric API is on Mojang names there, which is what we use.

So the order was inverted: straight to 1.21.11, skipping both 1.21.9 and 1.21.10.

## What the work turned out to be

70 errors, of which the largest group was a rename nobody had mentioned: **`ResourceLocation` is
called `Identifier`**. That alone accounted for the four custom payload classes, which the old
version of this document listed as networking that "has not been looked at properly yet". There
was nothing to look at.

| Bucket | How it was done |
| --- | --- |
| `ResourceLocation` → `Identifier`, `location()` → `identifier()`, `GameProfile.name()`, `player.level().getServer()` | four lines in the substitution table |
| The world render event | one substitution, plus the renderer variant |
| Input: mouse and key events became objects | substitutions, header by header |
| Widgets: `renderContents`, `renderContent`, `CycleButton` | substitutions, plus one new shared file |
| Line width | a variant, and the only real design decision |

**The renderer split in two.** 1.21.10 separated the frame into extraction and drawing: a mod
works out what it is going to draw in the first, and may touch nothing but that in the second.
The frustum only exists in the first, so `PerimeterRenderer.cull` moved to `END_EXTRACTION` and
hands its list to `END_MAIN` through a field. `AFTER_TRANSLUCENT` has no exact heir; `END_MAIN` is
the end of the main pass, after translucent terrain and before the GUI.

**`hasShiftDown` did not disappear, it moved onto the event** — `InputWithModifiers`, which both
`KeyEvent` and `MouseButtonEvent` implement. Inside a handler that is a better place for it than
`Screen` ever was. The awkward callers are the ones outside a handler: `Keys` asks on a tick and
on a scroll, where there is no event, so it asks the window through `InputConstants.isKeyDown`.

**`BeaconatorScreen` stayed one file**, as planned. Every handler keeps its body and unpacks the
event back into the names the body already used.

**`CycleButton` got a shared wrapper rather than five substitutions.** A cycling button used to be
told its starting value after it was built and now takes it as an argument, at five call sites in
a row. `client/gui/Cycler.java` is that one line, and `variants/1.21.11` overrides it: five fragile
multi-line substitutions became one six line file.

**Line width was the one real decision.** `RenderSystem.lineWidth` is gone, because width stopped
being global state: it is now an attribute of each vertex, read by vanilla's own line shader,
which expands a segment into a screen space quad. The choice was to drop the setting or to follow
vanilla, and we followed vanilla — the wireframe is how the perimeter reads from a distance, and
the shader path is also honoured on hardware that always ignored `glLineWidth`. So the lines are
drawn through `core/rendertype_lines` with `POSITION_COLOR_NORMAL_LINE_WIDTH`, each vertex
carrying the segment direction and the configured width. `ShapeRenderer` holds the width now,
instead of the driver.

## What is still not proven

Everything above compiles, and the 65 model tests pass. Nothing has been in the game.

- **The wireframe.** New shader, new vertex format, new mode. This is the part most likely to look
  wrong rather than to fail.
- **`ColourButton`.** `Button` is abstract now and `renderWidget` is final, so the swatch is drawn
  from `renderContents` and the background from `renderDefaultSprite`. The label is collected by
  vanilla in a separate pass, and this button sets its message while drawing, so the text may lag
  a frame or fail to appear.
- **The key mapping category.** Its translation key is built from an identifier now, so the
  language files carry `key.category.beaconator.beaconator` alongside the old key. If it is wrong,
  the controls screen shows a raw key.
- **The extraction split.** A frame that extracts and never draws leaves nothing behind, which is
  the intent, but the interaction with a paused or otherwise skipped frame is untested.

## If 1.21.10 is ever asked for

It has the events, but not the 1.21.11 renames. It needs its own row in `RULES` and its own copies
of the four variant files. Its Fabric API stopped in December and it was current for a month, so
it is not worth carrying speculatively.

## What did not break

`model/` still produces zero errors on every version tested, up to and including 26.2, and its
tests run without the game. Half the mod ports for free, every time.
