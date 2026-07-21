# Runtime validation checklist

Milestone acceptance requires one validation-clean run on Windows or Linux.
macOS is useful only for development smoke testing because Minecraft 26.2's
Vulkan backend remains experimental.

## Setup

1. Install Java 25, a current Vulkan driver, and the Khronos validation layer.
2. Run `./gradlew check build`.
3. Run `./gradlew runFixtureClient`.
4. Record the operating system, GPU, driver, Java version, and resulting commit.

## Required run

- Confirm both directory and ZIP forms of `cinder-fixture` load.
- Edit `shaders/final.fsh`; press F6 and confirm the frame changes.
- Load `cinder-fixture-invalid`; confirm the prior valid shader stays active.
- Alternate valid and invalid reloads twenty times while resizing, alt-tabbing,
  and changing worlds.
- Confirm the GUI stays visible over Cinder's output.
- Shut down and confirm there are no Vulkan validation errors or stale Cinder
  texture, view, or pipeline resources.

Attach the full validation output to the milestone record. A macOS smoke test
does not replace the Windows/Linux acceptance run.
