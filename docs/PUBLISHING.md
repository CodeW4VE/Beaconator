# Publishing Beaconator

Everything needed to put a build on GitHub and Modrinth. Written down because the fiddly parts
are the ones you only do once every few months.

## Release on GitHub

Tag driven. `.github/workflows/release.yml` builds the mod and attaches the jar to a GitHub
Release when a tag starting with `v` is pushed.

```sh
# bump mod_version in gradle.properties first, then:
git tag v1.0.0
git push origin v1.0.0
```

The jar lands in `build/libs/beaconator-<mod version>+<mc version>.jar`.

## Modrinth

**The project already exists as a draft**: id `1LmFuBOw`, slug `beaconator`, icon and body
uploaded, MIT, categories utility and management. A draft is visible to nobody but the owner
until it is submitted for review, and it cannot be submitted until it has at least one version.

So the remaining steps are:

1. Push a `v*` tag. The release workflow builds the jar, makes the GitHub Release and uploads the
   version to Modrinth (`MODRINTH_ID` variable and `MODRINTH_TOKEN` secret are already set on the
   repo).
2. On the Modrinth page, hit **Submit for review**. Only a human can do that.

The client/server side shows as "unknown" until the first version lands, because Modrinth now
derives it from the versions themselves. `fabric.mod.json` declares `"environment": "client"`, so
it sorts itself out on upload.

### For reference: what the project was created with

| Field | Value |
| --- | --- |
| Name | `Beaconator` |
| Slug / URL | `beaconator` |
| Summary | Plan and build beacon perimeters: grid from one point, real coverage volumes, beams to the sky where beacons are still missing. |
| Categories | `utility`, `management` (secondary: `optimization` does not apply, leave it off) |
| Client side | **Required** |
| Server side | **Optional**, the sync only. The mod is client side without it |
| Project type | Mod |
| Loader | Fabric |
| License | MIT |
| Icon | `src/main/resources/assets/beaconator/icon.png` |
| Source code | <https://github.com/CodeW4VE/Beaconator> |
| Issues | <https://github.com/CodeW4VE/Beaconator/issues> |

Body: paste `README.md`. It is written to read as a mod page already.

Game versions for the current build: **1.21 through 1.21.8**. See `docs/VERSIONS.md` for how each
one is built and `docs/PORT-1.21.9-PLUS.md` for why the list stops there.

### The versions the workflow does not upload

The release workflow builds and uploads **one** jar, the 1.21 one. Every other version comes from
`tools/multiversion.py`, which the workflow does not run, so those jars go up by hand after the
tag:

```sh
python3 tools/multiversion.py
gh release upload v1.1.0 build/multiversion/beaconator-1.1.0+1.21.[2-8].jar
```

and then one Modrinth version per jar. Modrinth wants `environment` declared **in the POST that
creates the version**: setting it afterwards with a PATCH returns 204 and takes a long time to
show up, which reads as a failure and is not one. Ours is `client_only_server_optional`.

```sh
curl -H "Authorization: $TOKEN" -X POST https://api.modrinth.com/v3/version \
  -F 'data={"project_id":"1LmFuBOw","version_number":"1.1.0+1.21.8","game_versions":["1.21.8"],
             "loaders":["fabric"],"version_type":"release","file_parts":["file"],
             "primary_file":"file","environment":"client_only_server_optional","dependencies":[]}' \
  -F "file=@build/multiversion/beaconator-1.1.0+1.21.8.jar"
```

1.21.1 gets no version of its own: it runs the 1.21 jar, and that jar's `fabric.mod.json` accepts
the range, so both are listed on the one upload.

### Every release after that

Put a Modrinth API token in the repo secrets as `MODRINTH_TOKEN`
(<https://modrinth.com/settings/pats>, scope: *Create versions*), and the release workflow
uploads to Modrinth on its own. Without the secret that step is skipped and only the GitHub
Release happens, which is a safe default.
