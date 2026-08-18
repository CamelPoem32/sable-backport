# Veil 1.20 API Matrix

This matrix accounts for all 82 Veil imports in the upstream Sable 2.0.0 common source graph. It was checked against the mapped and raw forms of `foundry.veil:Veil-forge-1.20.1:1.0.0.296`. Rows are grouped by imported API; the occurrence column preserves the full import count.

## Summary

| Recommendation | Imports |
|---|---:|
| `USE_VEIL_1_20` | 30 |
| `SMALL_ADAPTER` | 4 |
| `REPLACE_WITH_FORGE` | 23 |
| `DEFER` | 25 |
| **Total** | **82** |

`USE_VEIL_1_20` APIs are present in the official 1.20.1 Forge artifact and remain on the minimal source graph where needed. `SMALL_ADAPTER` entries have a direct 1.20.1 replacement but not the same modern API. `REPLACE_WITH_FORGE` is reserved for the modern Veil packet surface that the 1.20 artifact does not provide. `DEFER` covers advanced rendering and editor work that is not needed to expose the core compiler frontier.

## Import Matrix

| Current Sable / Veil API | Imports | Veil 1.20 equivalent | Difficulty | Runtime role | Recommendation |
|---|---:|---|---|---|---|
| `foundry.veil.Veil` | 3 | Same class; `Veil.platform()` is available | Direct | Platform/client bootstrap | `USE_VEIL_1_20` |
| `foundry.veil.api.client.render.VeilRenderSystem` | 9 | Same class; selected renderer accessors are present | Direct to moderate per method | Basic client/world rendering | `USE_VEIL_1_20` |
| `foundry.veil.api.client.render.CullFrustum` | 5 | Same class | Direct | Basic client/world rendering | `USE_VEIL_1_20` |
| `foundry.veil.api.client.render.VeilRenderBridge` | 5 | Same class | Direct | Basic client/world rendering | `USE_VEIL_1_20` |
| `foundry.veil.api.client.render.MatrixStack` | 4 | Same class | Direct | Basic client/world rendering | `USE_VEIL_1_20` |
| `foundry.veil.platform.registry.RegistrationProvider` | 2 | Same service-backed registry API | Direct | Registry/world load | `USE_VEIL_1_20` |
| `foundry.veil.platform.registry.RegistryObject` | 2 | Same registry handle API | Direct | Registry/world load | `USE_VEIL_1_20` |
| `foundry.veil.api.compat.SodiumCompat` | 2 | `Veil.platform().isSodiumLoaded()` for the selected plugin gate | Small call-site adapter | Optional renderer detection | `SMALL_ADAPTER` |
| `foundry.veil.api.client.render.profiler.VeilRenderProfiler` | 1 | Vanilla `ProfilerFiller` sections | Small call-site adapter | Basic render diagnostics | `SMALL_ADAPTER` |
| `foundry.veil.api.client.render.profiler.RenderProfilerCounter` | 1 | Vanilla profiler labels/sections; no counter object is required by the minimal path | Small call-site adapter | Basic render diagnostics | `SMALL_ADAPTER` |
| `foundry.veil.api.network.handler.PacketContext` | 17 | No practical Veil 1.20 equivalent | Behavior-sensitive packet adaptation | Core networking/world load | `REPLACE_WITH_FORGE` |
| `foundry.veil.api.network.VeilPacketManager` | 6 | No practical Veil 1.20 equivalent | Behavior-sensitive packet registration and sinks | Core networking/world load | `REPLACE_WITH_FORGE` |
| `foundry.veil.api.client.render.shader.processor.ShaderPreProcessor` | 4 | Same 1.20 API family, with version-specific processor contracts | Moderate | Advanced shaders | `DEFER` |
| `foundry.veil.api.client.render.vertex.VertexArray` | 2 | Same class | Moderate | Advanced rendering | `DEFER` |
| `foundry.veil.api.client.render.shader.program.ShaderProgram` | 2 | Same class | Moderate | Advanced shaders | `DEFER` |
| `foundry.veil.api.event.VeilRenderLevelStageEvent` | 2 | Same event API family | Moderate because Minecraft render stages differ | Advanced rendering | `DEFER` |
| `foundry.veil.platform.VeilEventPlatform` | 2 | Same platform service family | Moderate because listener signatures differ | Advanced rendering | `DEFER` |
| `foundry.veil.api.client.render.framebuffer.AdvancedFbo` | 2 | Same class | Moderate | Advanced rendering | `DEFER` |
| `foundry.veil.api.client.render.rendertype.VeilRenderType` | 2 | Same class | Moderate | Advanced rendering | `DEFER` |
| `foundry.veil.impl.client.render.dynamicbuffer.VanillaShaderCompiler` | 1 | No stable public equivalent; 1.20 uses its older shader compiler pipeline | High | Advanced shaders | `DEFER` |
| `foundry.veil.api.client.render.VeilLevelPerspectiveRenderer` | 1 | 1.20 perspective renderer API is present | Moderate because render entry points differ | Advanced rendering | `DEFER` |
| `foundry.veil.api.client.render.vertex.VertexArrayBuilder` | 1 | Same class | Moderate | Advanced rendering | `DEFER` |
| `foundry.veil.api.client.render.shader.uniform.ShaderUniform` | 1 | Older `UniformAccess` / `MutableUniformAccess` model | Moderate | Advanced shaders | `DEFER` |
| `foundry.veil.api.client.render.shader.block.ShaderBlock` | 1 | `foundry.veil.api.client.render.shader.definition.ShaderBlock` | Moderate package/API adaptation | Advanced shaders | `DEFER` |
| `foundry.veil.api.client.render.shader.block.DynamicShaderBlock` | 1 | `foundry.veil.api.client.render.shader.definition.DynamicShaderBlock` | Moderate package/API adaptation | Advanced shaders | `DEFER` |
| `foundry.veil.api.client.editor.SingleWindowInspector` | 1 | No direct class; the 1.20 editor API uses `SingleWindowEditor` | High/editor redesign | Debug/editor | `DEFER` |
| `foundry.veil.api.compat.IrisCompat` | 1 | Loader detection exists, but modern public compatibility helpers do not match | Moderate | Optional visuals | `DEFER` |
| `foundry.veil.api.client.editor.EditorManager` | 1 | Same class | Moderate because editor registration differs | Debug/editor | `DEFER` |

## Implemented Boundary

M3 consumes the official Veil artifact directly for registry/platform and basic renderer types. Render profiling now uses `ProfilerFiller`, and the Mixin plugin uses `Veil.platform().isSodiumLoaded()`. Shader preprocessing, directional lighting, water occlusion, fancy rendering, Iris/Sodium render bridges, and editor/gizmo sources remain intact but outside the Forge compilation target.

The unresolved core boundary is intentionally narrow: `VeilPacketManager` and `PacketContext` have no usable Veil 1.20 implementation. M3 does not introduce a packet stub or a premature transport abstraction. Their Forge 1.20.1 replacement is the recommended M4 scope.
