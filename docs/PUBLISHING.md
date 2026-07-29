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

Game versions for the current build: **1.21 and 1.21.1 only**. See `docs/VERSIONS.md` for why
nothing above that is listed.

### Every release after that

Put a Modrinth API token in the repo secrets as `MODRINTH_TOKEN`
(<https://modrinth.com/settings/pats>, scope: *Create versions*), and the release workflow
uploads to Modrinth on its own. Without the secret that step is skipped and only the GitHub
Release happens, which is a safe default.
