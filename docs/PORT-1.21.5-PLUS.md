# Porting past 1.21.4: what we are walking into

We ship **1.21 through 1.21.4** today. Going further is not more of the same, and this is the
briefing for the day we sit down to do it, written while the measurements are fresh.

Everything here is measured, not guessed: the mod was compiled unchanged against each version and
the errors read one by one.

## The short version

| Jump | Errors | What kind of work |
| --- | --- | --- |
| 1.21 → 1.21.4 | 9 to 10 | **done.** Six renamed methods, a substitution table |
| 1.21.4 → **1.21.5** | **88** | a rewrite of how the mod draws, plus every NBT read |
| 1.21.5 → 1.21.11 | 186 total | the above, plus screen and key API churn |
| 1.21.11 → 26.x | does not even configure | Java 25, new toolchain, mappings Loom cannot resolve yet |

Up to 1.21.4 the mod is the same program with different method names. From 1.21.5 it is a
different program that happens to do the same thing.

## Wall 1: the way we draw everything is gone (1.21.5)

The whole mod draws with immediate mode: build a buffer of vertices, hand it a shader, upload it.
That entire API was removed:

| What we call | Times | Gone in 1.21.5 |
| --- | --- | --- |
| `BufferUploader.drawWithShader` | 4 | yes |
| `RenderSystem.setShader(GameRenderer::getPositionColorShader)` | 2 | yes |
| `RenderSystem.depthMask` | 4 | yes |
| `enableBlend` / `disableBlend` / `defaultBlendFunc` | 6 | yes |
| `enableDepthTest` / `disableDepthTest` | 4 | yes |
| `enableCull` / `disableCull` | 4 | yes |

They are replaced by `RenderPipeline`, where blending, depth and culling are **baked into a
pipeline object declared up front** instead of being switches you flip before drawing. That is
not a rename, it is a different model: our renderer flips depth testing per draw depending on the
"see through terrain" setting, and per node depending on what it is drawing.

**Files:** `render/PerimeterRenderer.java` (28 errors) and `client/map/MapTile.java` (8).

**Realistically:** the renderer gets rewritten. Maybe 300 lines. The good news is that it is a
self contained file with no logic in it, and everything it draws is boxes and lines.

There is a second consequence nobody notices until it happens: **`ShapeRenderer` and the beam
widening code assume we control the shader**. Reading a pipeline's depth state back is not a
thing, so "see through" has to become two pipelines, chosen per draw.

## Wall 2: NBT returns Optional now (1.21.5)

Every read from a tag changed shape:

- `Optional<Integer> cannot be converted to int`, 18 times
- `Optional<CompoundTag> cannot be converted to CompoundTag`, 12 times
- `Optional<long[]> cannot be converted to long[]`, 2 times

**File:** `io/LitematicIO.java`, 40 of the 88 errors, on its own.

This is mechanical but it is not a substitution: each read needs a decision about what happens
when the value is missing. Right now a malformed litematic throws and we catch it; afterwards
every field needs an explicit default or an explicit failure. It is an afternoon of careful,
boring work, and the schematic tests are what will keep it honest.

## Wall 3: the screen API keeps moving (1.21.6 through 1.21.11)

By 1.21.11, `client/gui/BeaconatorScreen.java` alone has **52 errors**: widget constructors,
`Font.getSelected`, `getAllKeys`, list entries needing `renderContent` instead of `render`. None
of it is hard. All of it is one by one, in a 1300 line file, with the only way to check being to
launch the game.

Also gone by then: `ResourceLocation` from strings, `Level.getMinY` moved again, the Fabric
`WorldRenderEvents` package.

## Wall 4: 26.x is a different build (26.1, 26.2)

- Minecraft **requires Java 25**. Our toolchain, CI and the `options.release = 21` all move.
- Fabric Loom 1.17 **cannot resolve official Mojang mappings** for it at all. Loom has to be
  updated first, and when I tried, the newer Loom was not published under the plugin id we use.
- Everything from walls 1 to 3, accumulated.

This one is not "port the mod", it is "set up the project again and then port the mod".

## What does not break, ever

`model/` (the grid maths, the pyramid footprint calculator, the coverage boxes, the litematic
bit packing) produced **zero errors on every single version tested**, up to and including 26.2.
It has no Minecraft imports and its tests run without the game.

That is roughly half the mod, and the half with the actual thinking in it. Whatever we do, we
port a shell around code that already works.

## How I would do it

1. **Do not chase 26.x.** Pick 1.21.5 through 1.21.8 as the next target, since that is where
   people actually are, and 1.21.5 is where the expensive work lives. Once the renderer is
   rewritten, the rest of that range is cheap.
2. **Rewrite the renderer first, on 1.21.5, in isolation.** Nothing else can be tested until the
   mod launches, and nothing else is risky.
3. **Then LitematicIO**, with the existing tests as the safety net. They run without Minecraft,
   so they can be red until they are green with no game launches in between.
4. **Then the screen**, which is tedious rather than hard.
5. **Only then** decide whether the substitution table still works or whether it is time for
   [Stonecutter](https://stonecutter.kikugie.dev/). Two rendering backends in one source tree is
   exactly the case it exists for, and it is the point where our `tools/multiversion.py` stops
   being enough.

## The honest estimate

The 1.21 to 1.21.4 range took an hour, most of it spent finding six method names.

1.21.5 is a day of work before the game launches once, and it needs real testing afterwards
because a renderer that compiles is not a renderer that draws. Do not promise it in an evening.
