# Support matrix

## Technical milestone target

| Component | Supported |
| --- | --- |
| Minecraft | 26.2 |
| Loader | Fabric 0.19.2 |
| Renderer mod | Sodium 0.9.x |
| Java | 25 |
| Graphics API | Vulkan only |
| Pack format | One Vulkan-ready GLSL 330 final vertex/fragment pair |
| Windows | Acceptance target |
| Linux | Acceptance target |
| macOS | Development smoke test only |

OpenGL may be selected by Minecraft as a fallback. In that case Cinder loads but
does not create resources or alter the rendered frame.
