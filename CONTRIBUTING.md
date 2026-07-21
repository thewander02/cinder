# Contributing

## Development requirements

- Java 25
- A Vulkan 1.2-capable GPU and current driver for runtime work
- The Khronos Vulkan validation layer for GPU-path changes

## Before opening a pull request

1. Run `./gradlew check build`.
2. Run `./gradlew runFixtureClient` for rendering changes.
3. Confirm that invalid-shader reload restores the previous valid pipeline.
4. Include validation output and platform/GPU details for GPU-path changes.
5. Keep changes greenfield: do not copy Iris or other third-party source code,
   assets, strings, or fixtures without an explicit licensing review.

Changes to the renderer must preserve vanilla fallback when Cinder is disabled,
the selected pack fails, or Minecraft uses OpenGL.
