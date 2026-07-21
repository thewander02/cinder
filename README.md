# Cinder

Cinder is a greenfield, Vulkan-native shaderpack loader for Minecraft Java
Edition. The current `0.1.0-SNAPSHOT` milestone proves one pack-derived final
pass on Minecraft's native Vulkan renderer; it is not yet compatible with
general Iris or OptiFine shaderpacks.

## Current milestone

- Minecraft 26.2, Fabric Loader 0.19.2, Fabric API 0.152.1, Sodium 0.9.1.
- Java 25 and Minecraft's generic GPU API.
- Directory and ZIP pack discovery under `shaderpacks/`.
- A single Vulkan-ready GLSL 330 program: `shaders/final.vsh` and
  `shaders/final.fsh`.
- Transactional F6 reload with previous-pipeline rollback.
- Vulkan-only execution; an actual OpenGL fallback leaves vanilla rendering
  untouched.

Terrain replacement, legacy GLSL legalization, pack options, multiple passes,
uniforms, OpenGL, NeoForge, and a selection UI are intentionally deferred.

## Build

Install a Java 25 JDK, then run:

```shell
./gradlew check build
```

The remapped mod JAR is written under `build/libs/`.

## Run the fixture

Install the Khronos validation layer for your platform, then run:

```shell
./gradlew runFixtureClient
```

This copies the original fixture into the ignored `run/` directory and forces
Minecraft to start with `--graphicsBackend VULKAN --vulkanValidation
--renderDebugLabels`. Enter a world and press F6 to reload after editing the
fixture shaders. Record platform validation using the
[runtime checklist](docs/VALIDATION.md).

## Configuration

`config/cinder.json`:

```json
{
  "enabled": true,
  "shaderPack": "cinder-fixture"
}
```

`shaderPack` must name one immediate directory or ZIP file under
`shaderpacks/`. Cinder reads only `shaders/final.vsh` and
`shaders/final.fsh`. Each source must be valid UTF-8 and no larger than 1 MiB.

## Project relationship

Cinder is not an Iris fork. Iris is consulted only as a public behavioral
reference, and no Iris code, assets, strings, fixtures, or Git history are
included. See [NOTICE](NOTICE) and
[ADR-0001](docs/adr/0001-greenfield-vulkan.md).

## License

Apache License 2.0. See [LICENSE](LICENSE).
