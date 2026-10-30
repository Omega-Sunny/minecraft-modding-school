# Module 5: Optimization, Performance, and Production Readiness

**Target:** Minecraft 1.21.1 · NeoForge 21.1.x · Java 21

Optimization is an engineering process, not a contest to write the fewest allocations or the shortest method. A mod is fast when it performs the required work within the game's tick/render budgets under representative load, and is maintainable when that behavior can be diagnosed and changed without destabilizing other mods. Start with a repeatable symptom, measure the actual hot path, change one cause, and measure again.

This module combines the architecture from Modules 1-4 with profiling, allocation control, render-path discipline, safe asynchronous work, compatibility, and a release checklist.

## Learning Objectives

- Identify work that threatens the 50 ms server tick budget or client frame time.
- Reduce avoidable allocation and retained memory without introducing harmful object pools or unbounded caches.
- Keep render callbacks low-cost and preserve Minecraft's model/buffer batching.
- Offload pure computation safely while keeping all live world mutations on the owning game thread.
- Capture and interpret Spark and Java Flight Recorder data, and inspect heap dumps responsibly.
- Design public APIs and optional integrations that remain robust across NeoForge modpacks.
- Establish a repeatable performance and production-readiness checklist.

## 1. Start With A Measurement

### Budgets And Symptoms

The logical server targets 20 ticks per second. A tick has a nominal budget of 50 ms; a tick that regularly exceeds that budget makes the server fall behind. A long average can indicate sustained overload, while occasional high-percentile spikes can produce rubber-banding even when average TPS looks healthy. On the client, frame time is a separate budget: 16.7 ms corresponds to 60 FPS, 8.3 ms to 120 FPS. Server tick time and render frame time are different signals, even in an integrated singleplayer client.

Record the workload and symptom before optimizing:

- Which physical and logical side is affected?
- Does it happen with one player, many players, a particular dimension, a chunk, or a specific action?
- Is it constant or does it occur during chunk generation, combat, a machine operation, resource reload, or GC?
- What are the TPS/MSPT or frame-time percentiles, heap use, GC pauses, CPU use, and loaded entity/block-entity counts?
- What exact mod list, world, settings, and reproduction steps were used?

A profile without workload context is easy to misread. World generation, rendering, and entity AI may be expensive for entirely different reasons. Compare runs with the same seed/region, view distance, entity count, and player activity wherever possible.

### A Focused Optimization Loop

1. Reproduce the problem and capture a baseline.
2. Profile the affected thread and identify the hottest relevant call path.
3. Decide whether the cost is CPU, allocation/GC, I/O, synchronization, networking, or workload volume.
4. Change one cause. Do not combine a performance change with unrelated refactoring.
5. Repeat the same scenario and compare the result.
6. Run correctness and compatibility checks. A faster bug is still a bug.

Sampling profilers estimate time from observed stacks; they do not prove that the widest node is intrinsically bad. A large total-time method may simply be the legitimate caller of expensive children. Compare self time and total time, follow the path to your own code, and inspect the method's call frequency and inputs.

## 2. Tick And Memory Optimization

### Make Less Work Happen

The largest improvement is often to avoid doing unnecessary work at all:

- Prefer a narrow event, block/entity callback, scheduled tick, or change-driven update over scanning every loaded chunk/entity each tick.
- Tick machines only while active. Use a countdown or scheduled tick for slow work instead of recomputing every game tick.
- Update a cached result when its inputs change rather than rediscovering those inputs repeatedly.
- Use spatial queries and bounded regions. Avoid nested scans over all players, all entities, all chunks, and all blocks.
- Batch repetitive work when the game API offers a bulk operation; do not send one packet or perform one disk operation per object if a bounded batch is appropriate.
- Avoid logging in hot callbacks. A log message often allocates formatting data and may perform I/O.

A tick callback runs frequently because the game is designed around it. A small operation can become a major cost when multiplied by thousands of block entities or entities. Estimate work as `cost per call × call frequency × active instances`; then profile that estimate under realistic world size.

### Allocation And Garbage Collection

The Java garbage collector is designed to reclaim short-lived objects efficiently. A few small allocations are not automatically a problem. The danger is high allocation volume in a hot loop, large temporary object graphs, retained objects that should have died, or allocation bursts that trigger disruptive collection pauses.

Useful practices:

- Measure allocation rate before changing code. Spark's allocation mode and JFR allocation events can identify which call paths allocate.
- Avoid constructing temporary lists, maps, formatted strings, streams, or boxed values inside a per-tick/per-quad loop when a simpler loop or reusable local value is clear and measured to help.
- Pre-size collections when the expected size is known, but don't reserve huge capacities speculatively.
- Reuse immutable constants such as `ResourceLocation`s or fixed lookup tables where appropriate; keep caches bounded and tied to the resource/world lifecycle.
- Use `BlockPos.MutableBlockPos` only for a tight local traversal where the callee does not retain the mutable position. Convert to `immutable()` before storing it, putting it in a map, or passing it to code that may retain it.
- Do not mutate objects that are documented as immutable or shared singleton data.

The compiled example helper bounds the scan radius, skips unloaded chunks without forcing them to load, and reuses a local mutable position:

```java
int oreCount = PerformanceExamples.countCopperOreInLoadedRadius(serverLevel, center, 8);
```

Its implementation is in [PerformanceExamples.java](../../src/main/java/com/example/stoneworks/performance/PerformanceExamples.java). The mutable cursor stays local to the synchronous traversal. If a position must be stored for later, save `cursor.immutable()` instead.

### Object Pooling Is Not The Default

Do not add an object pool just because allocation sounds expensive. Modern JVMs optimize short-lived allocations, and pooling can make performance worse by keeping large objects reachable, increasing synchronization and bookkeeping, reducing locality, retaining stale data, and creating cross-thread ownership bugs. Pools are appropriate only for a measured high-volume workload where the object is expensive to create, reuse is safe, size is bounded, reset is complete, and profiling demonstrates a net win.

If a pool is justified, define ownership and lifecycle explicitly: who checks out an object, who returns it, what happens on exceptions/cancellation, whether access is thread-confined, and how the pool is bounded. Prefer a local reusable value or a purpose-built data structure over a generic pool.

### Avoid Memory Leaks

A Java memory leak is often an object that remains reachable longer than intended, not an object that was never freed. Common mod causes include:

- Static maps retaining `ServerLevel`, `LevelChunk`, `Entity`, `Player`, `ItemStack`, or menu instances after world unload or player logout.
- Registering an event listener every time a screen opens, a block entity loads, or a server tick runs.
- Executor tasks or scheduled futures retaining a world/player closure after the associated server shuts down.
- Unbounded per-position caches that are never cleared on chunk unload, dimension change, datapack reload, or server stop.
- Thread-local state or listeners that reference a class loader across development reloads.
- Keeping both the original and copied `ItemStack`/NBT structures in caches without a size/expiry policy.

Store state with its owner: block entity state with its block entity; per-player state with a player attachment; stack-specific state with data components. For global caches, document capacity, key identity, invalidation events, and whether values may reference live game objects. Prefer stable identifiers and immutable snapshots over retaining entire world object graphs.

## 3. Rendering Performance

### Rendering Is A Repeated Hot Path

Entity renderers and block entity renderers may be called every frame for every visible object. `BakedModel#getQuads` is called repeatedly while chunk meshes are built and can be called by renderers. Small costs multiply rapidly. Rendering must be presentation-only: don't mutate server state, scan the world, perform I/O, or build expensive gameplay results there.

### PoseStack Discipline

`PoseStack` transformations are CPU work used to transform subsequent vertices. Apply only the transforms needed for the current model, and keep the transform tree shallow enough to reason about. Push/pop around a logical transform scope; always pop even if rendering code throws:

The compiled `SurveyDroneRenderer` scopes its one local offset around the inherited batched render call:

```java
@Override
public void render(SurveyDrone entity, float entityYaw, float partialTick, PoseStack poseStack,
                   MultiBufferSource bufferSource, int packedLight) {
    poseStack.pushPose();
    try {
        poseStack.translate(0.0D, 0.0625D, 0.0D);
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    } finally {
        poseStack.popPose();
    }
}
```

The complete renderer lives in [SurveyDroneRenderer.java](../../src/main/java/com/example/stoneworks/client/SurveyDroneRenderer.java). Do not push/pop for every vertex or every face; transform once for the object/submodel and emit its geometry. Do not retain a `PoseStack`, `VertexConsumer`, or `MultiBufferSource` after the current render call. Avoid recomputing invariant transforms, trigonometric tables, or expensive model state per vertex; cache stable values at the appropriate renderer/model lifetime and invalidate them when resources reload.

### Preserve Batching And Baked Models

Minecraft's renderer batches geometry through `MultiBufferSource` and `RenderType`. Request the appropriate `VertexConsumer` from the supplied buffer source and write vertices into that batch. Avoid creating and flushing a new buffer per object, forcing immediate draw calls, or switching render types unnecessarily. Opaque/cutout rendering is generally cheaper than translucent rendering; select the strictest render type that matches the actual material.

Baked models are loaded and stored by the model manager. Reuse them; do not parse or bake JSON, load textures, or create a model for each frame. In a custom `BakedModel#getQuads`, return cached or cheaply assembled quads based on the inputs that actually affect geometry. A cache key may need block state, direction, render type, and `ModelData`; an incomplete key can render stale geometry. Do not return a mutable list that another renderer can corrupt.

For `BlockEntityRenderer` and entity renderer code:

- Read only the state needed to render the visible object. Do not walk nearby blocks or entities on every frame unless the feature requires it and a profile shows it is affordable.
- Keep renderer instances stateless with respect to a particular world object; NeoForge/Minecraft generally registers one renderer per type. Per-instance gameplay/render data belongs in the relevant entity or block entity.
- Avoid `Minecraft.getInstance()` lookups, registry scans, resource decoding, or string formatting inside inner render loops. Resolve stable resources at model reload/renderer construction where safe.
- Use the supplied packed light, overlay, render buffers, and model data instead of recomputing global lighting or bypassing the normal pipeline.
- Keep visual interpolation separate from authoritative simulation. Render partial-tick interpolation must not write back to entity state.

Use a GPU/frame profiler if the server thread is healthy but frames still stutter. A server CPU profile cannot diagnose a GPU fill-rate bottleneck or a client render-thread stall.

## 4. Thread Safety And Asynchronous Work

### The Game Thread Owns Live Game State

`Level`, `ServerLevel`, entities, block entities, inventories, menus, registries during mutation, and many client renderer objects are not general-purpose thread-safe data structures. Treat them as owned by the appropriate game thread. Never read or mutate a live world object from an arbitrary worker thread just because the field is visible or the class compiles.

Use asynchronous work only when it is substantial, independent, and can operate on a detached snapshot. The safe shape is:

1. On the owning game thread, validate the request and copy only immutable values needed for a calculation.
2. Run pure CPU work on a bounded worker executor. Do not pass live `Level`, `Entity`, menu, or mutable stack objects into the worker.
3. Return to the game thread using the owning server/client executor.
4. Revalidate that the world, player, chunk, and target are still valid before applying the result.
5. Handle failures, cancellation, and shutdown; bound the queue so overload cannot become unbounded memory growth.

The project helper accepts an immutable snapshot and obtains a bounded executor owned by the active server. Capture the snapshot on the server thread, then submit it:

```java
PerformanceExamples.SurveySnapshot snapshot = new PerformanceExamples.SurveySnapshot(
        serverLevel.dimension(), relayPosition, measurements
);
PerformanceExamples.scheduleSurvey(serverLevel.getServer(), snapshot);
```

`SurveySnapshot` copies its `BlockPos` and measurement list; the calculation reads only those values. The worker completion schedules a server task, checks that the dimension and chunk are still available, resolves the relay again, and applies the result there. The complete implementation is in [PerformanceExamples.java](../../src/main/java/com/example/stoneworks/performance/PerformanceExamples.java); [ServerWorkerPool.java](../../src/main/java/com/example/stoneworks/performance/ServerWorkerPool.java) creates one worker with a queue bounded to 32 tasks at `ServerStartingEvent` and shuts it down at `ServerStoppingEvent`. Requests are rejected when the server is not running or the queue is full. Do not replace this with an unbounded queue or a new thread per request.

### Bound And Own Worker Resources

Do not start a new `Thread` per block activation or submit unlimited work to an unbounded queue. Use a bounded executor with a named thread factory and a defined shutdown path, or a task system already provided by the owning subsystem. Define what happens when the queue is full: reject, coalesce duplicate requests, or defer a bounded amount. Do not block the server thread waiting for a worker result; that can deadlock if completion needs the server thread.

When a world unloads or server stops, cancel or discard tasks whose results are no longer relevant. A worker completion may happen after a player logs out, a chunk unloads, the target block is replaced, or the datapack/resource generation changes. Revalidate identifiers and versions on the game thread rather than trusting that the original snapshot is still current.

Avoid holding locks while calling into Minecraft or NeoForge APIs. A callback can trigger additional events or acquire locks in another order. Prefer immutable messages and thread confinement over shared mutable state. `ConcurrentHashMap` makes map operations concurrent; it does not make the mutable values stored inside the map thread-safe.

### Lifecycle Work Is Not Gameplay Work

Many mod lifecycle callbacks run once, and some are dispatched in parallel. Use the event's documented scheduling mechanism, such as `enqueueWork`, for operations that require the main thread. Do not put ongoing logic in common/client setup events. Runtime work belongs to the owning game callback or a scheduled task.

## 5. Profiling And Debugging Tools

### Spark: Server Tick And Allocation Profiles

Spark is a sampling profiler for Minecraft server environments. Use a Spark build compatible with the target NeoForge/Minecraft version; do not assume every server distribution bundles it. On a server where the Spark mod is installed and permitted, a focused session can look like:

```text
/spark tps
/spark health
/spark tickmonitor --threshold-tick 50
/spark profiler start --timeout 60
/spark profiler stop
```

The profiler normally samples CPU stacks. Use `/spark profiler start --alloc --timeout 60` when investigating allocation pressure, and `/spark profiler info` or `/spark profiler open` to inspect a session that is still running. Command availability and permissions can differ by Spark/server version; use `/spark help` and the matching Spark documentation.

In the viewer:

1. Select the **Server thread** for world-tick CPU work. Sleeping/parked time is usually not the expensive work to optimize.
2. Expand the largest relevant call trees and compare self time with total time. A method with high total time may mainly be calling expensive child work.
3. Use the flame graph or sources view to locate your mod's call paths. Apply the mappings appropriate to the exact Minecraft version if names are obfuscated.
4. Correlate spikes with player count, chunk generation, entity population, GC events, and scheduled tasks.
5. Save the profile link and reproduction conditions. Sharing links may expose class names, mod names, and server details; treat production profiles as operational data.

Use `tickmonitor` or a health report to catch when spikes happen, then start a short profile during a reproducible spike. A long profile across unrelated idle and busy periods can bury the signal. Sampling gives an estimate, so confirm improvements across multiple representative runs.

### Java Flight Recorder And JDK Mission Control

JDK 21 includes Java Flight Recorder (JFR). JFR can capture thread samples, allocation, GC pauses, locks, class loading, and other JVM events. It complements Spark: Spark is convenient in Minecraft and groups mod/server call paths; JFR gives JVM-level allocation, GC, and thread data for the exact Java process.

Find the server process, then start a short recording using the same JDK 21 installation (use an absolute output path):

```text
jcmd -l
jcmd <pid> JFR.start name=stoneworks settings=profile duration=60s filename=<absolute-path>/stoneworks.jfr
```

For a recording you want to stop manually:

```text
jcmd <pid> JFR.start name=stoneworks settings=profile
jcmd <pid> JFR.check
jcmd <pid> JFR.stop name=stoneworks filename=<absolute-path>/stoneworks.jfr
```

The `profile` configuration records more detail and has more overhead than the low-overhead `default` configuration. Use short captures for focused investigations, and measure profiler overhead if the server is already near its limit. Open the `.jfr` file in JDK Mission Control and inspect hot methods, allocation by class/stack, GC pauses, lock contention, and thread states. JFR is sampling/observational data: it can explain where CPU or allocations go, but it does not replace a controlled before/after test.

Useful low-frequency JVM diagnostics include:

```text
jcmd <pid> GC.heap_info
jcmd <pid> GC.class_histogram
jcmd <pid> Thread.print -l
```

`GC.class_histogram` and heap operations can take noticeable time on a large heap. Use them deliberately, preferably during a maintenance window or on a representative staging server.

### Heap Dumps And Retained Memory

When memory usage grows continuously, a heap dump can identify what remains reachable and which references retain it. With operator approval and enough free disk space:

```text
jcmd <pid> GC.heap_dump <absolute-path>/stoneworks.hprof
```

Open the dump with Eclipse Memory Analyzer (MAT) or another HPROF analyzer. Start with the dominator tree and retained heap, inspect paths to GC roots, and compare the object graph with a baseline or a dump after a controlled period. A large object count alone is not a leak: check whether retained memory continues to grow and what owns those objects.

Heap dumps can pause the JVM, consume disk space comparable to heap size, and contain player names, chat, inventories, server data, and secrets. Restrict access, avoid posting dumps publicly, and delete them securely when no longer needed. Prefer a heap histogram or Spark heap summary first when it can answer the question with less impact.

### Distinguish CPU, GC, And I/O

- **High server-thread CPU:** inspect Spark/JFR stacks and reduce repeated game work.
- **Low CPU with high tick time:** investigate blocking I/O, locks, synchronous network/file calls, or waiting on futures.
- **High allocation and frequent GC:** identify allocation stacks, then reduce avoidable temporary graphs or excessive work; do not reflexively enlarge the heap.
- **Heap steadily grows after players/worlds unload:** inspect heap dominators and references to server, level, player, chunk, and listener objects.
- **Healthy server thread but low client FPS:** profile client render/main threads and GPU use; inspect model complexity, translucent overdraw, draw/buffer behavior, and visible renderer counts.
- **Only new terrain is slow:** profile world generation separately from normal ticking and inspect configured/placed feature counts and expensive predicates.

## 6. Cross-Mod Compatibility And Maintainable Design

### Keep Extension Surfaces Small And Explicit

- Keep registry IDs stable and namespaced. Published IDs are part of saved-world and data-pack compatibility.
- Prefer public events, capabilities, tags, data components, recipe/loot data, biome modifiers, and documented extension interfaces over reflection or broad Mixins.
- Expose a small API with documented contracts, immutable inputs/results, and clear lifecycle. Keep implementation classes internal where possible.
- Validate input at boundaries: packets, commands, config values, codecs, data reloads, menu actions, and optional-mod calls.
- Do not modify another mod's private fields or assume its internal class layout. Integrate through its documented API or a narrow optional adapter.
- Use capabilities for interoperable services such as inventories, energy, or fluid access. Use data attachments for your own additional data, not as a substitute for an interoperability API.

### Optional Dependencies And Soft Integration

An optional mod must not become a required class reference. A runtime `ModList` check alone is not sufficient if a common class has already resolved a type from the missing mod. Put integration-specific imports and calls in a separate adapter that is only loaded when the target mod is present. Keep the base mod loadable without it, and test both cases.

Use `neoforge.mods.toml` to express optional dependency metadata/order when appropriate, but do not declare a hard dependency just to obtain a convenient load order. When there is no need for compile-time API coupling, integrate through shared tags, capabilities, events, or data. Treat another mod's presence as a capability to negotiate, not a promise that every version of that mod exposes the same behavior.

### Maintainable Project Structure

A practical structure groups code by ownership rather than by tutorial number:

- `registry`: deferred registers and registry objects.
- `world.block` / `world.block.entity`: blocks and per-placement state.
- `world.entity`: entity simulation and AI.
- `world.menu`: server-authoritative menus.
- `client`: screens, models, renderers, key mappings, and client-only setup.
- `network`: payload definitions, registration, and validated handlers.
- `data`: codecs, attachments, data providers, and immutable domain records.

Avoid mega-classes that combine registration, gameplay logic, rendering, and networking. Keep pure calculations separate from Minecraft state so they can be tested without constructing a world. Use clear names and targeted comments for non-obvious invariants; do not comment every line.

### Logging, Configuration, And Failure Behavior

Use structured, appropriately leveled logs for exceptional conditions, not every tick or render call. Include identifiers and relevant context but avoid dumping player data or entire NBT payloads. Validate user-configurable values once at load/reload, clamp or reject invalid ranges, and avoid re-reading files from a tick callback.

Fail safely when optional integrations, a target block entity, a menu, or a loaded chunk is absent. A rejected request should not corrupt state, load arbitrary chunks, or disconnect a player unless the protocol itself is invalid and the failure policy calls for a disconnect. Make rate limits and expensive operation limits explicit.

## 7. Production Readiness Checklist

### Performance

- [ ] A reproducible baseline and target workload are documented.
- [ ] Hot paths were profiled rather than guessed.
- [ ] No global world/entity scan runs every tick without a measured need.
- [ ] Tickers, event handlers, and renderers have bounded work and allocation.
- [ ] Caches are size-bounded, keyed correctly, and invalidated on unload/reload.
- [ ] Pools, custom executors, and background queues have a measured reason, ownership, bounds, and shutdown behavior.
- [ ] Server tick, client frame, GC, and memory retention were measured separately.

### Correctness And Compatibility

- [ ] All gameplay mutations run on the logical server; client-only classes are isolated from dedicated-server loading.
- [ ] Async work uses immutable snapshots and revalidates state on the game thread.
- [ ] Client requests are validated on the server and cannot choose arbitrary chunk loads or authoritative values.
- [ ] A dedicated server and a multiplayer session were tested.
- [ ] Optional dependencies are tested both present and absent.
- [ ] Worldgen changes were tested in newly generated chunks, and data reload logs were checked.
- [ ] Save/reload, resource reload, player logout/death, and server shutdown paths were exercised.

### Release Hygiene

- [ ] Gradle build, datagen, and any automated tests pass on the target JDK.
- [ ] Generated resources are reviewed and committed according to the project's source-of-truth policy.
- [ ] The output jar contains only intended runtime resources and no local run/cache files.
- [ ] Logs do not contain avoidable errors, missing models, invalid data, or repeated warnings.
- [ ] Version ranges and dependency metadata match the supported Minecraft/NeoForge versions.
- [ ] Performance claims include the workload, profiler, and before/after observations.

## References

- [NeoForge 1.21.1 events and lifecycle](https://docs.neoforged.net/docs/1.21.1/concepts/events/)
- [NeoForge 1.21.1 logical and physical sides](https://docs.neoforged.net/docs/1.21.1/concepts/sides/)
- [NeoForge 1.21.1 block entity renderers](https://docs.neoforged.net/docs/1.21.1/blockentities/ber/)
- [NeoForge 1.21.1 baked models](https://docs.neoforged.net/docs/1.21.1/resources/client/models/bakedmodel/)
- [Spark command usage](https://spark.lucko.me/docs/Command-Usage)
- [Spark profile viewer](https://spark.lucko.me/docs/Using-the-viewer)
- [Java 21 `jcmd` reference](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jcmd.html)
- [Java Flight Recorder API guide](https://docs.oracle.com/en/java/javase/21/jfapi/)
- [async-profiler](https://github.com/async-profiler/async-profiler)
