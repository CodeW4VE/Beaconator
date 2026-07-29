# Moving to newer Minecraft versions

Beaconator ships **1.21 through 1.21.8**, one jar per version, all built from this one source
tree. This is what each jump actually cost, measured rather than guessed: the mod was compiled
against every version and the compiler errors read one by one.

| Minecraft | Compile errors, unported | Status |
| --- | --- | --- |
| 1.21 | 0 | the target |
| 1.21.1 | **0** | same jar |
| 1.21.2 | 9 | **shipped**, six substitutions |
| 1.21.3 | 9 | **shipped** |
| 1.21.4 | 10 | **shipped**, one more substitution |
| 1.21.5 | 36 | **shipped**, the drawing layer rewritten |
| 1.21.6 | 8 on top of 1.21.5 | **shipped**, the drawing layer rewritten again |
| 1.21.7 | same as 1.21.6 | **shipped**, free |
| 1.21.8 | same as 1.21.6 | **shipped**, free |
| 1.21.9 | 36 | Fabric API has no world render event any more. See [PORT-1.21.9-PLUS.md](PORT-1.21.9-PLUS.md) |
| 1.21.10 | 36 | as above |
| 1.21.11 | 70 | as above, plus more screen churn |

## How the shipped versions are built

`tools/multiversion.py` copies the source, applies that version's changes and compiles. The
compiler checks the result against the real mappings, which is the point: these builds are not
play tested, so a method that does not exist has to fail the build rather than crash someone's
game. The model tests run on every one of them, all 56, because they do not need Minecraft.

There are two mechanisms, because two kinds of thing happened.

**Substitutions** for everything that was only renamed. A table of plain string pairs in the
script, applied to the copied tree. This covers all of 1.21 to 1.21.4 and most of 1.21.5.

**Variant files** for the things a rename cannot express, in `variants/<version>/`, copied over
the tree. A variant directory applies to its version and everything above it until some newer
one ships its own copy of the same file. A file earns a place here only when it could not be a
substitution, so there is still one copy of the logic. Two files qualify today:

| File | Where | Why |
| --- | --- | --- |
| `render/Pipelines.java` | `variants/1.21.5`, `variants/1.21.6` | new file: declares the pipelines and submits meshes |
| `render/PerimeterRenderer.java` | `variants/1.21.5` | the state switches around each draw are gone |

## What changed, by version

**1.21.2** moved `Level.getMinBuildHeight()` / `getMaxBuildHeight()` to `getMinY` / `getMaxY`,
`NativeImage.setPixelRGBA` (ABGR) to `setPixel` (ARGB, so the bytes get swapped), made
`BuiltInRegistries.BLOCK.get` return an `Optional` of a holder, moved the core shaders out of
`GameRenderer`, and changed `GuiGraphics.blit` to take a render type with the uv floats ahead of
the size. One line each.

**1.21.4** made `TextureManager.register` take a `ResourceLocation` and return void.

**1.21.5** is where it stopped being renames. Immediate mode drawing is gone: no
`BufferUploader.drawWithShader`, no `RenderSystem.setShader`, no `depthMask` / `enableBlend` /
`enableCull` / `disableDepthTest`. Blending, culling and depth are baked into a `RenderPipeline`
declared up front. That is why "see through terrain" became two pipelines picked per draw
instead of a switch flipped before one, and why there are four and not two.

The rest of 1.21.5 was renaming after all. Every NBT getter returns an `Optional` now, but each
one gained an `OrEmpty` or `OrElse` twin that returns exactly what the old getter returned for a
missing key: an empty compound, a zero, an empty string. So `LitematicIO` needed no decisions,
just the twins. `Inventory.selected` stopped being a public field and `DynamicTexture` wants a
label supplier.

**1.21.6** moved the shader's matrices out of loose uniforms and into a uniform buffer handed to
the render pass per draw, addressed the render target by texture view rather than texture, and
changed `drawIndexed` to take four arguments. `Pipelines` is a variant again for that; nothing
else in the mod noticed. GUI draws take the pipeline itself rather than a function returning a
render type for it.

**1.21.7** and **1.21.8** needed nothing at all.

**1.21.9** is a wall of a different kind, and it is not about drawing: Fabric API removed
`WorldRenderEvents` outright, so there is nothing to hook. See
[PORT-1.21.9-PLUS.md](PORT-1.21.9-PLUS.md).

## What does not break

`model/` (the grid maths, the pyramid calculator, the coverage boxes, the litematic bit
packing) has produced **zero errors on every version tested**, up to and including 26.2. It has
no Minecraft imports and its tests run without the game. That is roughly half the mod, and the
half with the actual thinking in it. Every port so far has been a shell around code that already
works.
