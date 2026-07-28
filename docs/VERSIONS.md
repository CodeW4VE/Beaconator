# Moving to newer Minecraft versions

Beaconator targets **1.21**. This is what it actually costs to go further, measured rather than
guessed: the mod was compiled unchanged against each version and the compiler errors counted.

| Minecraft | Compile errors | What it means |
| --- | --- | --- |
| 1.21 | 0 | the target |
| 1.21.1 | **0** | free, same jar works |
| 1.21.4 | 18 | an afternoon |
| 1.21.5 | 88 | the render backend has to be rewritten |
| 1.21.11 | 186 | the above plus the NBT and screen API churn |
| 26.2 | does not even configure | needs Java 25, a newer Loom, and official Mojang mappings that Loom 1.17 cannot resolve for it |

## Where the walls are

**1.21.2** moves `Level.getMinBuildHeight()` / `getMaxBuildHeight()` and `KeyMapping` handling.
Mechanical, one line each.

**1.21.5** is the real wall. The immediate mode rendering this mod draws everything with is gone:

- `RenderSystem.setShader(GameRenderer::getPositionColorShader)`
- `BufferUploader.drawWithShader(mesh)`
- `RenderSystem.depthMask` / `disableDepthTest` / `lineWidth` and friends

They are replaced by the `RenderPipeline` system. `PerimeterRenderer` and `MapTile` have to be
rewritten against it: not hard, but it is a rewrite of the drawing layer rather than a patch.

**1.21.5 onwards** also turns the NBT getters into `Optional`, which is where the 18
`Optional<Integer> cannot be converted to int` and 12 `Optional<CompoundTag>` errors in
`LitematicIO` come from. Mechanical but touches every read.

**26.x** is a separate project. Java 25, new build toolchain, and everything above.

## What does not break

`model/` — the grid maths, the pyramid calculator, the coverage boxes, the litematic bit
packing — produced **zero errors on every version tested**. It has no Minecraft imports and its
50 tests run without the game. That is roughly half the mod, and it ports for free.

The damage by file, going to 1.21.11:

| File | Errors |
| --- | --- |
| `client/gui/BeaconatorScreen.java` | 52 |
| `io/LitematicIO.java` | 40 |
| `render/PerimeterRenderer.java` | 38 |
| `client/map/MapTile.java` | 14 |
| `client/Keys.java` | 10 |
| everything else | under 10 each |
| `model/**` | 0 |

## Recommendation

Ship 1.21 (and list 1.21.1, which is the same jar), because that is what is tested and what the
server this was built for runs. Do not chase the latest version until there is a reason to: a
port to 1.21.5+ is a render rewrite, and the mod would then need testing all over again on a
version nobody here plays.

If it ever has to live on several versions at once, the tool for that is
[Stonecutter](https://stonecutter.kikugie.dev/), not a branch per version.
