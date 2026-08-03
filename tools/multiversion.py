#!/usr/bin/env python3
"""Builds the mod for every Minecraft version we ship, from the one source tree.

Two mechanisms, because two kinds of change happened.

Up to 1.21.4 the differences are a handful of renamed methods, so each version is built by
copying the source, applying that version's **substitutions**, and compiling. The compiler checks
the result against the real mappings, which matters because these builds are not play tested: if
a method does not exist, the build fails rather than the game crashing on someone.

From 1.21.5 the drawing layer is a different program (immediate mode is gone, RenderPipeline
replaced it) and a substitution table cannot express that. Those files come from
`variants/<version>/`, whose contents are **copied over** the tree after it is copied and before
anything is substituted. A file lives in a variant directory only if it could not be expressed as
a rename; everything else still goes through the table, so there is one copy of the logic.

Variant directories inherit: `variants/1.21.5` applies to 1.21.5 and everything above it, until
some later version ships its own file of the same name.

    python3 tools/multiversion.py            # build every version
    python3 tools/multiversion.py 1.21.4     # just one
    python3 tools/multiversion.py 1.21.5 --errors   # compile only, list every error

`--errors` skips the jar and prints the full compiler output grouped by file, which is what you
want while a port is still red.
"""
import json
import pathlib
import shutil
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
WORK = ROOT / "build" / "multiversion"
VARIANTS = ROOT / "variants"
# Fedora has no java-21 package, so locally this is the Temurin unpacked by hand. On CI the
# runner has already set JAVA_HOME to a 21, and that is the one to use.
JDK = pathlib.Path(__import__("os").environ.get("JAVA_HOME")
                   or pathlib.Path.home() / ".local/opt/jdk-21")

# Minecraft version -> the Fabric API build for it. Anything not listed is not shipped.
# Order matters: it is the order variant directories stack in.
TARGETS = {
    "1.21": "0.102.0+1.21",
    "1.21.1": "0.115.6+1.21.1",
    "1.21.2": "0.106.1+1.21.2",
    "1.21.3": "0.114.1+1.21.3",
    "1.21.4": "0.119.4+1.21.4",
    "1.21.5": "0.128.2+1.21.5",
    "1.21.6": "0.128.2+1.21.6",
    "1.21.7": "0.129.0+1.21.7",
    "1.21.8": "0.136.1+1.21.8",
    "1.21.11": "0.141.6+1.21.11",
    "26.1.2": "0.155.2+26.1.2",
    "26.2": "0.156.0+26.2",
}

# Measured, not shipped, and neither of these is worth shipping.
#
# 1.21.9 is the version Fabric API dropped WorldRenderEvents in, and it never got them back: the
# redesigned events landed on the 1.21.10 branch, and 1.21.9's last Fabric API is from December.
# There is nothing there to hook to draw in the world, short of a mixin of our own.
#
# 1.21.10 has the events but not the renames of 1.21.11, so it would need its own row in RULES
# and its own copies of the four variant files. It was current for a month and its Fabric API
# stopped in December as well. Left here so `--errors` can still be pointed at either of them.
# See docs/PORT-1.21.9-PLUS.md.
UNSHIPPED = {
    "1.21.9": "0.134.1+1.21.9",
    "1.21.10": "0.138.4+1.21.10",
}

# What changed and when. Applied in order to every file listed, for versions at or above the key.
# Written as plain substitutions rather than a preprocessor: there are five of them, and five
# marked up conditionals in the source would be worse to read than this table.
SINCE_1_21_2 = [
    # Level.getMinBuildHeight/getMaxBuildHeight became getMinY/getMaxY.
    (".getMinBuildHeight()", ".getMinY()"),
    (".getMaxBuildHeight()", ".getMaxY()"),
    # NativeImage went from ABGR (setPixelRGBA, a lie of a name) to ARGB (setPixel), so the
    # pixels have to be swapped as well as the call renamed.
    ("image.setPixelRGBA(pixelX, pixelZ, packed);",
     "image.setPixel(pixelX, pixelZ, argbFromAbgr(packed));"),
    # Registry.get returns an Optional of a holder now.
    ("BuiltInRegistries.BLOCK.get(location)",
     "BuiltInRegistries.BLOCK.get(location).map(net.minecraft.core.Holder::value).orElse(null)"),
    # The core shaders moved out of GameRenderer.
    ("RenderSystem.setShader(GameRenderer::getPositionColorShader);",
     "RenderSystem.setShader(net.minecraft.client.renderer.CoreShaders.POSITION_COLOR);"),
    # GuiGraphics.blit takes the render type, and the uv floats moved ahead of the size.
    ("""graphics.blit(tile.textureId(), screenX, screenZ, drawSize, drawSize,
						0.0f, 0.0f, MapTile.SIZE, MapTile.SIZE, MapTile.SIZE, MapTile.SIZE);""",
     """graphics.blit(net.minecraft.client.renderer.RenderType::guiTextured, tile.textureId(),
						screenX, screenZ, 0.0f, 0.0f, drawSize, drawSize,
						MapTile.SIZE, MapTile.SIZE, MapTile.SIZE, MapTile.SIZE);"""),
]

# TextureManager.register wants a ResourceLocation rather than a name. Negative tile coordinates
# are fine: the minus sign is legal in a resource path.
SINCE_1_21_4 = SINCE_1_21_2 + [
    ('\t\t\ttextureId = Minecraft.getInstance().getTextureManager()\n'
     '\t\t\t\t\t.register("beaconator_map_" + tileX + "_" + tileZ, texture);',
     '\t\t\ttextureId = ResourceLocation.fromNamespaceAndPath("beaconator",\n'
     '\t\t\t\t\t"map_" + tileX + "_" + tileZ);\n'
     '\t\t\tMinecraft.getInstance().getTextureManager().register(textureId, texture);'),
]

# 1.21.5 turned every NBT getter into an Optional and added an `OrElse` twin for each. The twins
# return exactly what the old getters returned when a key was missing: an empty compound, a zero,
# an empty string. So this is a rename and not a behaviour change, which is why it stays in the
# table rather than becoming a variant. All the NBT reads in the mod are in LitematicIO.
#
# The one that is not a rename is the drawing layer, and that is what variants/1.21.5 is for.
SINCE_1_21_5 = SINCE_1_21_4 + [
    (".getCompound(", ".getCompoundOrEmpty("),
    (".getAllKeys()", ".keySet()"),
    ('.getInt("x")', '.getIntOr("x", 0)'),
    ('.getInt("y")', '.getIntOr("y", 0)'),
    ('.getInt("z")', '.getIntOr("z", 0)'),
    ('.getString("Name")', '.getStringOr("Name", "")'),
    # getList lost the element type argument: a ListTag is homogeneous now, so asking for
    # compounds and getting something else is the reader's problem rather than the getter's.
    ('region.getList("BlockStatePalette", Tag.TAG_COMPOUND)',
     'region.getListOrEmpty("BlockStatePalette")'),
    # An absent long array used to read as a zero length one, and the caller already checks for
    # that two lines down.
    ('region.getLongArray("BlockStates")',
     'region.getLongArray("BlockStates").orElse(new long[0])'),
    # Inventory.selected stopped being a public field.
    ("inventory.getSelected()", "inventory.getSelectedItem()"),
    ("inventory.selected = slot;", "inventory.setSelectedSlot(slot);"),
    # DynamicTexture wants a label supplier, used in crash reports and debug dumps.
    ("new DynamicTexture(image)",
     'new DynamicTexture(() -> "beaconator map " + tileX + " " + tileZ, image)'),
]

# 1.21.6 finished what 1.21.5 started: a GUI draw takes the pipeline itself rather than a
# function that hands back a render type for it.
SINCE_1_21_6 = SINCE_1_21_5 + [
    ("net.minecraft.client.renderer.RenderType::guiTextured",
     "net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED"),
]

# 1.21.9 rewrote how a mod draws in the world and how it hears about input. The world render
# event is the one thing here that is not a rename, and it is not a rewrite either: Fabric took
# the event suite away in 1.21.9 and brought it back, redesigned, in 0.137.0+1.21.10. So this
# table describes 1.21.10 and up, and 1.21.9 itself is left where it fell: nothing to hook.
SINCE_1_21_9 = SINCE_1_21_6 + [
    # The world render event moved package and lost AFTER_TRANSLUCENT. END_MAIN is the closest
    # equivalent: the end of the main pass, after translucent terrain and before the GUI. The
    # culling moved with it, into the extraction phase, because that is where the frustum lives.
    ("net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents",
     "net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents"),
    ("WorldRenderEvents.AFTER_TRANSLUCENT.register(PerimeterRenderer::render);",
     "WorldRenderEvents.END_EXTRACTION.register(PerimeterRenderer::extract);\n"
     "\t\tWorldRenderEvents.END_MAIN.register(PerimeterRenderer::render);"),

    # Mouse and key handling take an event object rather than loose numbers. The bodies do not
    # change: each handler unpacks the event back into the names the body already uses. Ugly
    # here, invisible in the source, and it keeps one copy of a 1500 line screen.
    # The event types are spelled out in full rather than imported: these headers are in four
    # files, only one of which has an import block this table could reliably hook onto.
    ("public boolean mouseClicked(double mouseX, double mouseY, int button) {",
     "public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event,\n"
     "\t\t\tboolean doubled) {\n"
     "\t\tdouble mouseX = event.x();\n"
     "\t\tdouble mouseY = event.y();\n"
     "\t\tint button = event.button();"),
    ("super.mouseClicked(mouseX, mouseY, button)", "super.mouseClicked(event, doubled)"),
    ("public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {",
     "public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event,\n"
     "\t\t\tdouble dragX, double dragY) {\n"
     "\t\tdouble mouseX = event.x();\n"
     "\t\tdouble mouseY = event.y();\n"
     "\t\tint button = event.button();"),
    ("super.mouseDragged(mouseX, mouseY, button, dragX, dragY)",
     "super.mouseDragged(event, dragX, dragY)"),
    ("public boolean mouseReleased(double mouseX, double mouseY, int button) {",
     "public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {\n"
     "\t\tdouble mouseX = event.x();\n"
     "\t\tdouble mouseY = event.y();\n"
     "\t\tint button = event.button();"),
    ("super.mouseReleased(mouseX, mouseY, button)", "super.mouseReleased(event)"),
    ("public boolean keyPressed(int keyCode, int scanCode, int modifiers) {",
     "public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {\n"
     "\t\tint keyCode = event.key();\n"
     "\t\tint scanCode = event.scancode();"),
    ("super.keyPressed(keyCode, scanCode, modifiers)", "super.keyPressed(event)"),
    ("InputConstants.getKey(keyCode, scanCode)", "InputConstants.getKey(event)"),

    # Screen.hasShiftDown and friends are gone: they are the event's business now. Keys asks
    # outside of any event, on a tick and on a scroll, so it asks the window instead.
    ("""	public static int heldModifiers() {
		return (Screen.hasShiftDown() ? SHIFT : 0)
				| (Screen.hasControlDown() ? CONTROL : 0)
				| (Screen.hasAltDown() ? ALT : 0);
	}""",
     """	private static boolean held(int left, int right) {
		return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), left)
				|| InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), right);
	}

	private static boolean shiftDown() {
		return held(InputConstants.KEY_LSHIFT, InputConstants.KEY_RSHIFT);
	}

	private static boolean controlDown() {
		return held(InputConstants.KEY_LCONTROL, InputConstants.KEY_RCONTROL);
	}

	private static boolean altDown() {
		return held(InputConstants.KEY_LALT, InputConstants.KEY_RALT);
	}

	public static int heldModifiers() {
		return (shiftDown() ? SHIFT : 0) | (controlDown() ? CONTROL : 0) | (altDown() ? ALT : 0);
	}"""),
    ("Screen.hasShiftDown()", "shiftDown()"),
    ("Screen.hasControlDown()", "controlDown()"),
    ("Screen.hasAltDown()", "altDown()"),
    # What is left is inside a handler, where the event is the one that knows.
    ("hasShiftDown()", "event.hasShiftDown()"),
    ("hasControlDown()", "event.hasControlDown()"),
    ("hasAltDown()", "event.hasAltDown()"),

    # A key mapping's category is a registered object rather than a bare translation key, and the
    # key is built from its identifier: hence the second entry in the language files.
    ('private static final String CATEGORY = "key.categories.beaconator";',
     "private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(\n"
     '\t\t\tnet.minecraft.resources.ResourceLocation.fromNamespaceAndPath("beaconator",\n'
     '\t\t\t\t\t"beaconator"));'),

    # A cycling button is told its starting value when it is built rather than after. That is the
    # whole of variants/1.21.10/.../Cycler.java, which is why nothing about it appears here.

    # A button no longer draws itself: AbstractButton.renderWidget is final and calls
    # renderContents, which is where a subclass gets to draw. The default background comes from
    # renderDefaultSprite, because Button is abstract now and has no renderContents to call.
    # Only the button: a plain AbstractWidget still overrides renderWidget, so the next line of
    # ColourButton comes along to tell the two identical headers apart.
    ("""	protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		var font = net.minecraft.client.Minecraft.getInstance().font;""",
     """	protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		var font = net.minecraft.client.Minecraft.getInstance().font;"""),
    ("super.renderWidget(graphics, mouseX, mouseY, delta);", "renderDefaultSprite(graphics);"),

    # A list entry is told where it is rather than being handed its bounds every frame.
    ("""		public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
				int mouseX, int mouseY, boolean hovered, float partialTick) {
			graphics.drawString(font, name, left + 4, top + 5, 0xFFFFFF, false);""",
     """		public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered,
				float partialTick) {
			graphics.drawString(font, name, getX() + 4, getY() + 5, 0xFFFFFF, false);"""),
]

# 1.21.11 renamed a pile of things that had kept their names since forever, none of which change
# behaviour: ResourceLocation is called Identifier, the key inside a ResourceKey is reached with
# identifier() rather than location(), GameProfile became a record, and a player reaches the
# server through its level instead of directly.
SINCE_1_21_11 = SINCE_1_21_9 + [
    ("ResourceLocation", "Identifier"),
    (".dimension().location()", ".dimension().identifier()"),
    (".getGameProfile().getName()", ".getGameProfile().name()"),
    ("player.getServer()", "player.level().getServer()"),
]

RULES = {
    "1.21.2": SINCE_1_21_2,
    "1.21.3": SINCE_1_21_2,
    "1.21.4": SINCE_1_21_4,
    "1.21.5": SINCE_1_21_5,
    "1.21.6": SINCE_1_21_6,
    "1.21.7": SINCE_1_21_6,
    "1.21.8": SINCE_1_21_6,
    "1.21.9": SINCE_1_21_9,
    "1.21.10": SINCE_1_21_9,
    "1.21.11": SINCE_1_21_11,
    "26.1.2": SINCE_1_21_11,
    "26.2": SINCE_1_21_11,
}


ALL = {**TARGETS, **UNSHIPPED}


def rules_for(version):
    return RULES.get(version, [])


def variant_dirs_for(version):
    """Every variant directory that applies to this version, oldest first.

    Oldest first means a newer directory's copy of a file wins, which is what inheriting from
    the previous version means in practice.
    """
    order = list(ALL)
    upto = order[:order.index(version) + 1]
    return [VARIANTS / name for name in upto if (VARIANTS / name).is_dir()]


def apply_variants(version, target):
    """Copy variant files over the working tree. Returns the paths that came from a variant.

    Those paths are then left out of the substitution pass: a variant file is already written
    for the version it is under, so running the rename table over it would be a second guess at
    something that is already right.
    """
    overridden = set()

    for directory in variant_dirs_for(version):
        for source in directory.rglob("*"):
            if not source.is_file():
                continue

            destination = target / source.relative_to(directory)
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source, destination)
            overridden.add(destination.resolve())

    return overridden


def report(errors, target, limit=12):
    """Print compiler errors grouped by file, worst file first.

    javac emits them in whatever order it got to them, which for a port reads as noise. Grouped
    by file it reads as a plan: this file is the work, that one is two lines.
    """
    by_file = {}

    for line in errors:
        name = line.split(".java:")[0].split("/")[-1] + ".java" if ".java:" in line else "?"
        by_file.setdefault(name, []).append(line.strip().replace(str(target) + "/", ""))

    for name, lines in sorted(by_file.items(), key=lambda item: -len(item[1])):
        print(f"     {name}: {len(lines)}")

        for line in lines if limit is None else lines[:limit]:
            print("        ", line)


def build(version, api, errors_only=False):
    target = WORK / version
    shutil.rmtree(target, ignore_errors=True)
    target.mkdir(parents=True)

    for item in ["src", "gradle", "gradlew", "build.gradle", "settings.gradle",
                 "gradle.properties"]:
        source = ROOT / item
        destination = target / item

        if source.is_dir():
            shutil.copytree(source, destination)
        else:
            shutil.copy2(source, destination)

    properties = target / "gradle.properties"
    text = properties.read_text(encoding="utf-8")
    text = text.replace(f"minecraft_version={TARGETS['1.21'] and '1.21'}",
                        f"minecraft_version={version}")
    lines = []

    for line in text.splitlines():
        if line.startswith("minecraft_version="):
            line = f"minecraft_version={version}"
        elif line.startswith("fabric_api_version="):
            line = f"fabric_api_version={api}"

        lines.append(line)

    properties.write_text("\n".join(lines) + "\n", encoding="utf-8")

    # Each jar declares the version it was compiled against. Without this they all keep the
    # 1.21 range from the source tree and Fabric refuses to load them on anything newer.
    manifest = target / "src" / "main" / "resources" / "fabric.mod.json"
    accepts = ">=1.21 <1.21.2" if version in ("1.21", "1.21.1") else version
    manifest.write_text(
        manifest.read_text(encoding="utf-8").replace('"minecraft": ">=1.21 <1.21.2"',
                                                     f'"minecraft": "{accepts}"'),
        encoding="utf-8")

    overridden = apply_variants(version, target)
    applied = 0

    for path in (target / "src").rglob("*.java"):
        if path.resolve() in overridden:
            continue

        original = path.read_text(encoding="utf-8")
        patched = original

        for old, new in rules_for(version):
            patched = patched.replace(old, new)

        if patched != original:
            path.write_text(patched, encoding="utf-8")
            applied += 1

    print(f"  {version}: {len(overridden)} variant files, patched {applied}, building...",
          flush=True)
    task = "compileJava" if errors_only else "build"
    result = subprocess.run(
        ["./gradlew", task, "-q", "--console=plain"],
        cwd=target, capture_output=True, text=True,
        env={**__import__("os").environ, "JAVA_HOME": str(JDK)})

    if result.returncode != 0:
        errors = [line for line in (result.stdout + result.stderr).splitlines()
                  if "error:" in line]
        print(f"  {version}: FAILED, {len(errors)} errors")
        report(errors, target, limit=None if errors_only else 12)
        return None

    if errors_only:
        print(f"  {version}: compiles clean")
        return None

    jars = [jar for jar in (target / "build" / "libs").glob("*.jar")
            if "sources" not in jar.name]

    if not jars:
        print(f"  {version}: built but produced no jar")
        return None

    out = WORK / f"beaconator-{ROOT.joinpath('gradle.properties').read_text().split('mod_version=')[1].splitlines()[0]}+{version}.jar"
    shutil.copy2(jars[0], out)
    print(f"  {version}: OK -> {out.name}")
    return out


def main():
    arguments = sys.argv[1:]
    errors_only = "--errors" in arguments
    wanted = [item for item in arguments if not item.startswith("--")] or list(TARGETS)
    WORK.mkdir(parents=True, exist_ok=True)
    built = {}

    for version in wanted:
        if version not in ALL:
            print(f"  {version}: not a version we know about")
            continue

        jar = build(version, ALL[version], errors_only)

        if jar:
            built[version] = jar.name

    if errors_only:
        return 0

    print("\nbuilt:", json.dumps(built, indent=2))
    return 0 if len(built) == len(wanted) else 1


if __name__ == "__main__":
    raise SystemExit(main())
