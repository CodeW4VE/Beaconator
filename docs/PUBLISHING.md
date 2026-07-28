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

The project has to be created once by hand at <https://modrinth.com/dashboard/projects>, because
the API cannot create a project from nothing without an approved account and it needs a human to
accept the rules. After that, releases are automated.

### One time: create the project

| Field | Value |
| --- | --- |
| Name | `Beaconator` |
| Slug / URL | `beaconator` |
| Summary | Plan and build beacon perimeters: grid from one point, real coverage volumes, beams to the sky where beacons are still missing. |
| Categories | `utility`, `management` (secondary: `optimization` does not apply, leave it off) |
| Client side | **Required** |
| Server side | **Unsupported** — the mod is client only |
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
