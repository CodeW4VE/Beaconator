#!/usr/bin/env python3
"""Uploads every jar `multiversion.py` built to Modrinth, one version per jar.

This exists because the generic publishing action got two things wrong, both of which had to be
repaired by hand after every release.

It uploaded **one jar**, the one `gradlew build` produces, which is the 1.21 one. The other nine
are built by `multiversion.py` and never reached Modrinth from CI.

And it declared the wrong **environment**. Modrinth rebuilt that field in 2025 and its staff
rejects projects that leave it empty or wrong, citing the content rules. The action derives it
from `fabric.mod.json`, where `"environment": "*"` means "loads on both sides", and calls that
`client_and_server`. This mod is a client mod that can talk to a server that also has it, which
is `client_only_server_optional`. So the value is stated here rather than guessed.

    MODRINTH_TOKEN=... python3 tools/publish_modrinth.py            # upload
    MODRINTH_TOKEN=... python3 tools/publish_modrinth.py --dry-run  # say what it would upload

Versions that are already up are skipped, so a re-run after a half finished release finishes it
rather than failing on the first jar.
"""
import json
import os
import pathlib
import sys
import urllib.error
import urllib.request

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from multiversion import ROOT, TARGETS, WORK  # noqa: E402

API = "https://api.modrinth.com/v3"
PROJECT = os.environ.get("MODRINTH_ID", "1LmFuBOw")

# What this mod is, in Modrinth's terms: a client mod, and the server side is a bonus rather than
# a requirement. Do not let this drift from `fabric.mod.json`.
ENVIRONMENT = "client_only_server_optional"

FABRIC_API = "P7dR8mSH"

# 1.21 and 1.21.1 take the same jar, which declares `>=1.21 <1.21.2` and really does run on both.
# Everything else is one jar per version. Keys not listed cover only themselves.
COVERS = {"1.21": ["1.21", "1.21.1"]}
SKIP = {"1.21.1"}


def request(method, path, token, data=None, headers=None):
    req = urllib.request.Request(API + path, method=method, data=data)
    req.add_header("Authorization", token)

    for key, value in (headers or {}).items():
        req.add_header(key, value)

    with urllib.request.urlopen(req) as response:
        body = response.read()

    return json.loads(body) if body else None


def multipart(fields, files):
    """A multipart body, written out by hand so this script needs nothing but the stdlib."""
    boundary = "----beaconator-release-boundary"
    body = b""

    for name, value in fields.items():
        body += (f"--{boundary}\r\nContent-Disposition: form-data; name=\"{name}\"\r\n"
                 f"Content-Type: application/json\r\n\r\n{value}\r\n").encode("utf-8")

    for name, path in files.items():
        body += (f"--{boundary}\r\nContent-Disposition: form-data; name=\"{name}\"; "
                 f"filename=\"{path.name}\"\r\n"
                 f"Content-Type: application/java-archive\r\n\r\n").encode("utf-8")
        body += path.read_bytes() + b"\r\n"

    body += f"--{boundary}--\r\n".encode("utf-8")
    return body, f"multipart/form-data; boundary={boundary}"


def main():
    dry_run = "--dry-run" in sys.argv
    token = os.environ.get("MODRINTH_TOKEN", "")

    if not token and not dry_run:
        print("MODRINTH_TOKEN is not set")
        return 1

    version = (ROOT / "gradle.properties").read_text(encoding="utf-8")
    version = version.split("mod_version=")[1].splitlines()[0].strip()

    changelog = f"https://github.com/CodeW4VE/Beaconator/releases/tag/v{version}"
    notes = (ROOT / "CHANGELOG.md").read_text(encoding="utf-8")

    if f"## {version}\n" in notes:
        start = notes.index(f"## {version}\n") + len(f"## {version}\n")
        rest = notes[start:]
        end = rest.index("\n## ") if "\n## " in rest else len(rest)
        changelog = rest[:end].strip() + f"\n\nFull notes: {changelog}"

    # By file name as well as by version number: the release that first ran this had already had
    # its 1.21 jar uploaded under a different number by the action this replaces, and uploading a
    # second copy of a jar is worse than skipping one.
    existing = set()

    if token:
        for known in request("GET", f"/project/{PROJECT}/version", token) or []:
            existing.add(known["version_number"])
            existing.update(file["filename"] for file in known.get("files", []))

    for minecraft in TARGETS:
        if minecraft in SKIP:
            continue

        jar = WORK / f"beaconator-{version}+{minecraft}.jar"
        number = f"{version}+{minecraft}"

        if not jar.is_file():
            print(f"  {minecraft}: no jar at {jar}, run tools/multiversion.py first")
            return 1

        if number in existing or jar.name in existing:
            print(f"  {minecraft}: {number} is already up, skipped")
            continue

        data = {
            "project_id": PROJECT,
            "name": f"v{version} for {minecraft}",
            "version_number": number,
            "changelog": changelog,
            "game_versions": COVERS.get(minecraft, [minecraft]),
            "version_type": "release",
            "loaders": ["fabric"],
            # The newest version is the one to hand someone who lands on the page.
            "featured": minecraft == list(TARGETS)[-1],
            "environment": ENVIRONMENT,
            "dependencies": [{"project_id": FABRIC_API, "dependency_type": "required"}],
            "file_parts": ["file"],
        }

        if dry_run:
            print(f"  {minecraft}: would upload {jar.name} as {number} "
                  f"for {data['game_versions']}, {ENVIRONMENT}")
            continue

        body, content_type = multipart({"data": json.dumps(data)}, {"file": jar})

        try:
            result = request("POST", "/version", token, body, {"Content-Type": content_type})
            print(f"  {minecraft}: {result['version_number']} uploaded")
        except urllib.error.HTTPError as error:
            print(f"  {minecraft}: failed, {error.code} {error.read().decode('utf-8')[:300]}")
            return 1

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
