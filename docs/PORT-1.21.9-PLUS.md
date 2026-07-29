# Porting past 1.21.8: what we are walking into

We ship **1.21 through 1.21.8** today. This is the briefing for the day we sit down to go
further, written while the measurements are fresh. Everything here was compiled, not guessed.

The old briefing, [PORT-1.21.5-PLUS.md](PORT-1.21.5-PLUS.md), predicted 1.21.5 would be a day of
work and a renderer rewrite. It was about two hours and two files. Read that before trusting any
estimate below, including these ones.

## The short version

| Jump | Errors, unported | What kind of work |
| --- | --- | --- |
| 1.21.5 → 1.21.8 | 36, then 8 | **done.** Two variant files and a substitution table |
| 1.21.8 → **1.21.9** | **36** | a hook we have to write ourselves, then input and screen churn |
| 1.21.9 → 1.21.10 | none | free, if 1.21.9 lands |
| 1.21.10 → 1.21.11 | 70 total | the above, plus another round of screen and payload churn |

## Wall 1: there is no world render event any more

This is the one that matters, and it is not in any of the compiler counts above in a way that
does it justice. Up to 1.21.8 the mod draws by registering here:

```java
WorldRenderEvents.AFTER_TRANSLUCENT.register(PerimeterRenderer::render);
```

In 1.21.9 that class does not exist. Not moved, not renamed, not deprecated: **Fabric API has no
world render event at all**. Checked by unzipping all 60 jars of `fabric-api 0.134.1+1.21.9` and
grepping every class file for `AFTER_TRANSLUCENT` and for anything named `WorldRender*`. The only
hits are Fabric's own internal mixins. `fabric-rendering-v1` went to 16.0.1 and is now about HUD
elements, render states and entity renderers.

The cause is upstream: 1.21.9 turned world rendering into a frame graph of passes, and
`LevelRenderer.renderLevel` now takes a `GraphicsResourceAllocator`, three matrices and a
`GpuBufferSlice`. The old "here is a spot after the translucent pass, draw what you like" hook
does not describe the new renderer.

**So the mod has to bring its own hook**: a mixin into `LevelRenderer` that finds the point after
translucent geometry and calls `PerimeterRenderer.render` with something shaped like the old
`WorldRenderContext` (camera, matrix stack, frustum, tick delta). That is a new file plus a
`mixins.json` entry, neither of which the project has today.

Two things make this different from the work we have done so far:

- **The compiler cannot check it.** Everything in this port so far has been verified by building:
  a method that does not exist fails the build. A mixin target that does not match fails at
  runtime, in the game, on somebody's machine. This is the first piece that has to be play tested
  before it can be shipped honestly.
- **It is ours to maintain.** Substitutions and variants track vanilla renames. A mixin tracks
  vanilla's internals, and 1.21.9, 1.21.10 and 1.21.11 are three chances for the injection point
  to move.

Worth checking first, in this order:

1. Whether a later `fabric-rendering-v1` has brought a replacement back. This is the outcome
   worth waiting for and it costs nothing to check before writing anything.
2. What [Xaero's minimap or Litematica](https://modrinth.com) do on 1.21.9, since they have the
   same problem and are open source. Do not invent an injection point if someone has already
   found the stable one.

## Wall 2: input became objects (1.21.9)

Mouse and key handling stopped taking loose primitives:

| Was | Became |
| --- | --- |
| `mouseClicked(double, double, int)` | `mouseClicked(MouseButtonEvent, boolean)` |
| `mouseDragged(double, double, int, double, double)` | `mouseDragged(MouseButtonEvent, double, double)` |
| `mouseReleased(double, double, int)` | `mouseReleased(MouseButtonEvent)` |
| `keyPressed(int, int, int)` | `keyPressed(KeyEvent)` |
| `InputConstants.getKey(int, int)` | `InputConstants.getKey(KeyEvent)` |
| `Screen.hasShiftDown()` / `hasControlDown()` / `hasAltDown()` | gone from `Screen` |

`BeaconatorScreen` is 1500 lines and holds most of these; `Keys` holds the rest.

**This is still a substitution job, not a rewrite.** The method bodies do not change, only their
headers and the first line that unpacks the event. Something like:

```python
("public boolean mouseClicked(double mouseX, double mouseY, int button) {",
 "public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {\n"
 "\t\tdouble mouseX = event.x();\n\t\tdouble mouseY = event.y();\n\t\tint button = event.button();")
```

Ugly in the table, invisible in the source, and it keeps one copy of a 1500 line file. Do not
turn `BeaconatorScreen` into a variant: duplicating it is how the two copies start drifting.

Also in this bucket, all mechanical:

- `KeyMapping`'s constructor takes a `Category` rather than a `String`.
- `GameProfile.getName()` became the record accessor `name()`.
- `ServerPlayer.getServer()` moved.
- `ObjectSelectionList.Entry` wants `renderContent`, `AbstractButton` wants `renderContents`.
- `RenderSystem.getModelOffset()` is gone, which `Pipelines` uses. One more variant, or a check
  of what replaced it in `DynamicUniforms.writeTransform`.

## Wall 3: 1.21.11 does it again

1.21.11 doubles the count: 70 unported errors against 1.21.9's 36. `BeaconatorScreen` alone goes
from 16 to 25, and the custom payload classes (`PlanPayload`, `NodePayload`, `PlanListPayload`,
`PlanRequestPayload`) start failing, which the 1.21.9 build did not. The networking has not been
looked at properly yet.

Do not aim at 1.21.11 first. Land 1.21.9, get 1.21.10 free, then measure again.

## How I would do it

1. **Check whether Fabric has restored a world render hook**, and what other rendering mods on
   1.21.9 are doing. Ten minutes, and it decides whether wall 1 exists.
2. **Write the mixin on 1.21.9 and launch the game.** Nothing else can be tested until the mod
   draws, and nothing else is risky. This is the one piece that needs a human looking at a
   screen.
3. **Then the input substitutions**, which are tedious rather than hard, and which the compiler
   checks completely.
4. **Then 1.21.10**, which should be free.
5. **Only then look at 1.21.11**, and measure it again rather than trusting the 70 above.

## The honest estimate

1.21.5 was predicted at a day and took two hours, because the expensive part turned out to be a
rename with a good twin for every getter.

1.21.9 is the other shape: small on the compiler and real everywhere else. The mixin is perhaps
fifty lines, and it is the first thing in this whole port that cannot be declared finished from a
green build. Budget an evening for the code and a session in game for the part that matters.

## What does not break

`model/` still produces zero errors on every version tested, up to and including 26.2, and its 56
tests run without the game. Whatever happens above, half the mod ports for free.
