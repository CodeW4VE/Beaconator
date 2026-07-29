#!/usr/bin/env python3
"""Builds the mod for every Minecraft version we ship, from the one source tree.

The differences between 1.21 and 1.21.4 are a handful of renamed methods, so each version is
built by copying the source, applying that version's substitutions, and compiling. The compiler
checks the result against the real mappings, which matters because these builds are not play
tested: if a method does not exist, the build fails rather than the game crashing on someone.

    python3 tools/multiversion.py            # build every version
    python3 tools/multiversion.py 1.21.4     # just one

Jars land in build/multiversion/.
"""
import json
import pathlib
import shutil
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
WORK = ROOT / "build" / "multiversion"
JDK = pathlib.Path.home() / ".local/opt/jdk-21"

# Minecraft version -> the Fabric API build for it. Anything not listed is not shipped.
TARGETS = {
    "1.21": "0.102.0+1.21",
    "1.21.1": "0.115.6+1.21.1",
    "1.21.2": "0.106.1+1.21.2",
    "1.21.3": "0.114.1+1.21.3",
    "1.21.4": "0.119.4+1.21.4",
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

RULES = {"1.21.2": SINCE_1_21_2, "1.21.3": SINCE_1_21_2, "1.21.4": SINCE_1_21_4}


def rules_for(version):
    return RULES.get(version, [])


def build(version, api):
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

    applied = 0

    for path in (target / "src").rglob("*.java"):
        original = path.read_text(encoding="utf-8")
        patched = original

        for old, new in rules_for(version):
            patched = patched.replace(old, new)

        if patched != original:
            path.write_text(patched, encoding="utf-8")
            applied += 1

    print(f"  {version}: patched {applied} files, building...", flush=True)
    result = subprocess.run(
        ["./gradlew", "build", "-q", "--console=plain"],
        cwd=target, capture_output=True, text=True,
        env={**__import__("os").environ, "JAVA_HOME": str(JDK)})

    if result.returncode != 0:
        errors = [line for line in (result.stdout + result.stderr).splitlines()
                  if "error:" in line]
        print(f"  {version}: FAILED")

        for line in errors[:12]:
            print("     ", line.strip())

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
    wanted = sys.argv[1:] or list(TARGETS)
    WORK.mkdir(parents=True, exist_ok=True)
    built = {}

    for version in wanted:
        if version not in TARGETS:
            print(f"  {version}: not a version we ship")
            continue

        jar = build(version, TARGETS[version])

        if jar:
            built[version] = jar.name

    print("\nbuilt:", json.dumps(built, indent=2))
    return 0 if len(built) == len(wanted) else 1


if __name__ == "__main__":
    raise SystemExit(main())
