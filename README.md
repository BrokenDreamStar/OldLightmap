# Old Lightmap

A Fabric client mod that restores the **1.21.8 lightmap rendering** using two
UBO-compatible distribution JARs:

- **1.21.9–1.21.11** — uses the pre-26.1 `LightmapInfo` UBO shader.
- **26.1.x** — uses the 26.1+ `LightmapInfo` UBO shader.
- **26.2** — uses the same 26.1+ UBO layout, but is distributed separately.

The output is grouped by UBO layout, not by every Minecraft patch version.
Install only the JAR matching the target range.

## Distribution JARs

```text
oldlightmap-1.0.0-1.21.9-1.21.11.jar
oldlightmap-1.0.0-26.1.x.jar
oldlightmap-1.0.0-26.2.jar
```

The first JAR declares the Minecraft dependency range `>=1.21.9 <=1.21.11`.
The 26.1.x JAR declares `26.1.x`, while the 26.2 JAR declares `26.2`.
Although they share the same UBO layout, they are released separately.

## What it fixes

The mod restores the 1.21.8 lightmap shader and old block-light behaviour.
End flash is disabled by default and is automatically disabled while old lighting
is enabled, because the two rendering paths are incompatible. It avoids the incompatible `pre-1.21.9-lighting` resource
pack, whose shader layout does not match the newer lightmap uniform block.

Each distribution explicitly declares its UBO family in:

```text
assets/oldlightmap/shaders/shader_ubo_group.properties
```

The 1.21.9–1.21.11 JAR uses the `1_21` shader group. Both the 26.1.x and
26.2 JARs use the `26_1` shader group.

## Configuration

The config file is written to `config/oldlightmap.json`:

```json
{"oldLightmap":true,"endFlash":false}
```

| Option | Default | Effect |
|---|---:|---|
| `oldLightmap` | `true` | Use the 1.21.8 lightmap algorithm instead of the target version's vanilla algorithm. |
| `endFlash` | `false` | Keep the target version's End flash effect; unavailable while old lighting is enabled. |

A config page is available in the following places:

- **Sodium 0.7 on 1.21.9:** the 1.21.x JAR adds an "Old Lightmap" page
  directly to Sodium's own options screen. No Reese's Sodium Options or Mod Menu
  is required.
- **Sodium releases with the official config API:** the JAR registers the same
  page through Sodium's `sodium:config_api_user` entrypoint, following the
  integration pattern used by projects such as Voxy.
- **Sodium 0.7.x:** when the official API is unavailable, the JAR adds the page
  through Sodium's native `SodiumOptionsGUI` path, which avoids registering a
  second page when Reese's Sodium Options is installed.
- **26.1.x and 26.2:** the JAR uses the official Sodium config API directly.

If no supported options screen is installed, edit `config/oldlightmap.json`
directly on all versions.

Sodium remains optional and its Fabric metadata version constraint is `"*"`.

## Installation

1. Choose the JAR matching the Minecraft version:
   - `oldlightmap-1.0.0-1.21.9-1.21.11.jar` for 1.21.9–1.21.11.
   - `oldlightmap-1.0.0-26.1.x.jar` for 26.1.x.
   - `oldlightmap-1.0.0-26.2.jar` for 26.2.
2. Put it in the instance's `.minecraft/mods/` directory.
3. Install Fabric Loader **0.18.0 or newer**.
4. Remove the old **Pre 1.21.9 Lighting** resource pack if it is installed.

## Build

Requires JDK 21 for the 1.21.x distribution and JDK 25 for the 26.1.x/26.2
distributions. The active Java installation must be able to compile the target
being built.

```sh
./gradlew build
```

JARs are generated under:

```text
versions/mc1_21/build/libs/oldlightmap-1.0.0-1.21.9-1.21.11.jar
versions/mc26_1/build/libs/oldlightmap-1.0.0-26.1.x.jar
versions/mc26_2/build/libs/oldlightmap-1.0.0-26.2.jar
```

Run the structural metadata check separately with:

```sh
./gradlew verifyVersionMatrix
```

## Project layout

```text
common/                 Shared Java classes and shader assets
versions/mc1_21/        1.21.9–1.21.11 UBO distribution
versions/mc26_1/        26.1.x UBO distribution
versions/mc26_2/        26.2 UBO distribution
```
