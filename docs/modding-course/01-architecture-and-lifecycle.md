# Module 1: Architecture, Lifecycle, and Core Systems

**Target:** Minecraft 1.21.1 · NeoForge 21.1.x · Java 21

This module establishes the mental model used throughout the course: Minecraft owns a simulation, NeoForge offers controlled integration points, and a mod must respect the timing, thread, and side on which each integration point runs. A method can compile and still be incorrect if it mutates server state from the client, accesses an unregistered object, or calls a client-only class on a dedicated server.

## Learning Objectives

By the end of this module, you should be able to trace a tick through the logical client and server, tell physical distribution from logical authority, choose an event bus and lifecycle hook, explain deferred registration, and evaluate whether a Mixin is the right intervention.

## 1. The Game Loop: Simulation, Presentation, and Authority

### Why Minecraft Has More Than One Loop

A game must update its world and present images to a player. Minecraft separates these concerns:

- The **logical server** owns authoritative gameplay state: world changes, entity simulation, inventory changes, time, weather, and most game rules.
- The **logical client** receives synchronized state and handles input, presentation, local prediction, rendering, and effects that do not decide gameplay.
- The **physical client** is the launched client program. In singleplayer it also hosts an integrated logical server, so both logical sides exist in one process.
- The **physical server** is the dedicated server program. It hosts a logical server but has no usable rendering environment.

“Client” and “server” therefore do not always mean separate machines or processes. A singleplayer game has a logical server and a logical client in one physical client, commonly on separate threads. Multiplayer has a client connected to a server process, which may be on another machine.

### Tick Cadence

The server simulation targets 20 ticks per second: one nominal tick is 50 ms. This is a target, not a promise that every tick completes in 50 ms. If server work exceeds its budget, the server falls behind and may report low TPS. Rendering is driven by frames and can run at a different rate; one rendered frame is not one server tick.

A useful conceptual ordering is:

1. The server processes queued work and network input.
2. The server advances world time and runs scheduled world, block, and entity logic.
3. The server publishes the resulting state and events to connected clients.
4. The client handles local input and received updates, then renders a view of its current state.

The precise call order is implementation detail and changes as vanilla changes. The important contracts are that gameplay state is authoritative on the logical server, client rendering is not a substitute for server simulation, and tick callbacks may be frequent. Use the exact event or callback that matches the behavior rather than running broad scans every tick.

### Logical-Side Checks

When code has a `Level`, use `level.isClientSide` to distinguish logical sides. Gameplay mutations such as consuming an item, changing health, dropping an item, or setting a block normally happen on the server:

```java
public InteractionResult useOn(UseOnContext context) {
    Level level = context.getLevel();

    if (!level.isClientSide()) {
        BlockPos target = context.getClickedPos().relative(context.getClickedFace());
        level.setBlockAndUpdate(target, Blocks.GOLD_BLOCK.defaultBlockState());
    }

    return InteractionResult.sidedSuccess(level.isClientSide());
}
```

This example performs the world mutation only on the server while returning an interaction result that is appropriate on both sides. A real item must also validate the target, permissions, stack consumption, and whether the operation is allowed. Do not copy the example as a general “always succeed” interaction pattern.

`level.isClientSide() == false` means “logical server,” not “physical dedicated server.” It is true for the integrated server inside a singleplayer client. Conversely, use physical-side information (`Dist` or a client-only mod/event subscriber) when deciding whether it is safe to reference classes in `net.minecraft.client`.

### Thread Ownership And Safety

Minecraft's world and entity objects are generally not thread-safe. Their normal owner is the relevant game thread: the server thread for logical-server state and the render/client thread for client state. A background executor is useful for independent work such as parsing a file or calculating a large immutable result, but it must not directly mutate `Level`, `Entity`, `ItemStack`, menus, or other live game objects.

A sound pattern is:

1. Snapshot the minimal immutable input on the owning thread.
2. Perform an independent, bounded computation in a worker.
3. Schedule the resulting mutation back onto the correct game executor.

For example, server work can be scheduled with `server.execute(() -> { /* mutate server-owned state */ });`, provided the server reference and any captured values remain valid. Re-check assumptions on the game thread: an entity may have despawned or a level may have unloaded while background work ran. Never block a tick waiting on a worker that itself needs the game thread.

Lifecycle events add another thread boundary. Many mod lifecycle events are dispatched in parallel to speed startup. `FMLCommonSetupEvent` is one of these events. Do lightweight setup directly when the API permits it; use `event.enqueueWork(...)` for operations that must run on the game thread or are documented as not thread-safe. Do not assume that “startup” means “main thread.”

### A Practical Side/Thread Checklist

Before adding logic, ask:

- Is this a gameplay decision or a visual effect?
- Which `Level` or entity owns the state?
- Can the code be called on both logical sides?
- Does it reference a client-only class that could load on a dedicated server?
- Is the callback on the server thread, client thread, or a parallel lifecycle worker?
- If data must cross logical sides, is it synchronized through the vanilla/NeoForge system or an explicit network payload?

A static field does not make state shared safely. In singleplayer, both logical sides can access classes loaded in the same process, and static mutable state can accidentally couple their independent copies of logic. Store gameplay state with the game object that owns it and synchronize only what the client needs to display.

## 2. NeoForge Events And Event Buses

### Event Bus As A Dispatch Boundary

An event is a typed notification or extension point. The code that owns a behavior posts an event; registered listeners receive it and may inspect context, provide a result, or cancel it when the event supports cancellation. Event handlers are called by the code that posts the event, so an event is not automatically asynchronous and is not automatically safe to use from every side.

NeoForge primarily exposes two bus categories:

| Bus | Obtained from | Typical responsibilities |
| --- | --- | --- |
| Mod event bus | Passed to a mod's `@Mod` constructor | Registry registration, lifecycle, datagen, and mod initialization events |
| Game/event bus | `NeoForge.EVENT_BUS` | Runtime gameplay, player, entity, level, and many interaction events |

A wrong-bus subscription is a common silent failure: the handler may never run because the event is posted on another bus. Check the event's version-specific documentation or source before wiring a listener.

### Registering Listeners

For a small mod, typed method references make bus selection explicit:

```java
@Mod(Stoneworks.MOD_ID)
public final class Stoneworks {
    public static final String MOD_ID = "stoneworks";

    public Stoneworks(IEventBus modBus) {
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModCreativeTabs.TABS.register(modBus);

        modBus.addListener(ModDataGenerators::gatherData);
        NeoForge.EVENT_BUS.addListener(GameplayEvents::onLivingJump);
    }
}
```

A class can also use `@SubscribeEvent` after it is registered with the intended bus. `@EventBusSubscriber` can make static handlers discoverable automatically, but specify the mod ID and physical distribution when relevant. Static auto-subscribers are convenient for small, stateless handlers; they are not a reason to put mutable game state in static fields.

Only subscribe to concrete event types. Some event base classes are abstract context types, not events that can actually be posted; listening to one can crash during registration.

### Event Semantics

Read the event API before acting. In particular:

- A cancellable event stops or alters the associated operation only according to its contract. Cancellation is not a generic “undo.”
- Some events use a tri-state or result value to express “allow,” “deny,” or “leave vanilla behavior unchanged.” Prefer the event's result API over cancellation if that is its designed mechanism.
- Event priority controls ordering for many handlers, but should not be used to depend on arbitrary ordering with other mods. Event priority may be ignored for parallel dispatch.
- An event can fire on the client, server, or both. A handler that mutates gameplay may need a logical-side guard.
- Some client-only events need client-only registration. Physical-side classloading and logical-side gameplay checks solve different problems.

Use the narrowest event that represents the behavior. For example, listen for a block-break event when reacting to a block break, rather than polling every loaded block every tick. Prefer an API or event over overwriting behavior with a Mixin.

## 3. Mod Lifecycle And Registration Timing

### Startup Is A Sequence, Not A Constructor

The mod constructor is an early wiring point. It is not a general-purpose place to query every registry, access a loaded world, or perform gameplay work. A simplified 1.21.1 startup sequence is:

1. The mod constructor is called and installs deferred registers and listeners.
2. Automatic event subscribers are discovered and construction events run.
3. Registry events are fired; deferred register entries are instantiated and registered during this window.
4. `FMLCommonSetupEvent` runs for common setup. It is a parallel-dispatch lifecycle event.
5. Physical-side setup runs: `FMLClientSetupEvent` on a client or `FMLDedicatedServerSetupEvent` on a dedicated server.
6. Inter-mod communication is processed and load-complete events run.

This is a conceptual map, not a promise that every internal event or loader detail is listed. Consult the event's Javadoc for its bus, thread, and ordering requirements.

### Choosing The Right Phase

- **Registration:** use deferred registers for items, blocks, tabs, and other registry entries. Create the registration declarations early; NeoForge invokes their factories during registry registration.
- **Common setup:** use `FMLCommonSetupEvent` for one-time common integration that genuinely belongs after registrations. For thread-sensitive work, enqueue it.
- **Client setup:** initialize client-only behavior only on a physical client, and use the thread-safe registration event/API intended for that behavior. If an API is not thread-safe, enqueue work as documented.
- **Runtime gameplay:** use the relevant event/callback on the game bus, or the owning object's methods.
- **Data generation:** subscribe to `GatherDataEvent` on the mod bus. The data run occurs after registry events and terminates after writing providers' output.

Do not defer ordinary item or block registration to `FMLCommonSetupEvent`. Registration has its own lifecycle window, and entries created after registries freeze are invalid. Do not put expensive world scans or ongoing gameplay in a one-time lifecycle callback.

## 4. Deferred Registers And The Registry Contract

### What A Registry Means

Minecraft uses registries as maps from stable resource IDs to canonical objects. For example, a block registry entry such as `stoneworks:copper_ore` is one `Block` object used as the definition for every location where that block appears. Blocks and items are created once; world positions hold `BlockState` values, and inventories hold mutable `ItemStack` values.

Registration is more than adding a Java object to a map. The registered ID participates in saving, loading, networking, data-pack references, commands, and other mods' integrations. A block made with `new Block(...)` outside registration is an unregistered object with no valid identity. It can cause broken saves, missing registry synchronization, or crashes. Treat registry entries as canonical definitions, not per-world mutable storage.

### Why Registration Is Deferred

Minecraft's registries are opened during specific lifecycle events and frozen after registration. Mods are not loaded by eagerly classloading one giant vanilla `Blocks` class, so NeoForge provides `DeferredRegister` to declare factories early and instantiate/register values at the correct event.

```java
public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(Stoneworks.MOD_ID);

    public static final DeferredBlock<Block> COPPER_ORE = BLOCKS.registerSimpleBlock(
            "copper_ore",
            BlockBehaviour.Properties.of()
                    .destroyTime(3.0F)
                    .explosionResistance(3.0F)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops()
    );

    private ModBlocks() {}
}
```

Attach each register to the mod event bus once. In 1.21.1 the specialized APIs are useful: `DeferredRegister.Blocks` returns `DeferredBlock<T>`, and `DeferredRegister.Items` returns `DeferredItem<T>` from item registration helpers. Both are deferred holders and can be passed as suppliers where an API accepts one.

`DeferredHolder#get()` is not permission to query an object before it exists. Avoid calling `get()` from static initializers that cause eager cyclic loading, or from code that runs before registration. Store the holder; dereference it when the lifecycle guarantees the object is registered. Likewise, do not query or iterate registries while their registration is still underway.

Register blocks and their item forms separately. A block does not inherently imply an inventory item: technical blocks and fire have no ordinary `BlockItem`. A normal placed block needs a block registry entry; an obtainable inventory representation also needs an item registry entry, often created through `DeferredRegister.Items.registerSimpleBlockItem`.

## 5. Mixins: Last-Resort, Narrow Patches

### Why Mixins Exist

Events and extension interfaces are the stable integration surface when they cover the required behavior. A Mixin transforms a target class or method at load time, allowing code to inject into, redirect, or alter vanilla/NeoForge bytecode. That reach is useful when there is no suitable event or API, but the cost is coupling to implementation details: method names, descriptors, control flow, and injection points may change or conflict with another mod's transformation.

A Mixin should therefore be a consciously chosen patch, not the default way to add behavior. First check for an event, an overrideable method, a data-driven hook, a capability/component, or a narrowly scoped NeoForge extension point.

### Review Checklist

1. **State the missing hook.** Identify the exact behavior that cannot be implemented through a public API, event, or data resource.
2. **Target the smallest surface.** Prefer an injection into a precise method location over overwriting the whole method. Avoid broad redirects that replace another mod's extension point.
3. **Use a stable selector.** Specify the target class, method descriptor, and injection point deliberately. Confirm them against the 1.21.1 mapped source used by the build.
4. **Fail visibly.** Configure injection expectations (`require`, `expect`, or equivalent supported by the project) so a changed target does not silently turn off important behavior. Avoid both brittle assumptions and permissive “maybe it matched” settings without a reason.
5. **Keep the injected code small.** Delegate to a normal helper method, and preserve vanilla behavior unless the feature explicitly replaces it.
6. **Test combinations.** Run with the base game, NeoForge, and other likely transformers. Test dedicated-server startup if the target is common code.
7. **Document the target.** Record the target method, reason for injection, expected count, and the vanilla/API alternatives considered.

### Configuration Is Toolchain-Specific

Mixin discovery and packaging depend on the exact MDK, build plugin, loader integration, and packaging conventions. Use the Mixin setup supplied or documented for the specific NeoForge 1.21.1 project rather than pasting configuration from Fabric, old Forge, or a different Gradle plugin. Keep client-only mixins isolated from server-loaded targets and validate the final jar, not just the IDE run.

A schematic injection illustrates the shape but is not a complete mixin setup:

```java
@Mixin(TargetClass.class)
abstract class TargetClassMixin {
    @Inject(method = "targetMethod(Descriptor)", at = @At("HEAD"), require = 1)
    private void stoneworks$beforeTargetMethod(CallbackInfo callback) {
        // Delegate to narrow, side-safe code. Preserve vanilla behavior unless intentionally cancelled.
    }
}
```

`TargetClass`, the descriptor, callback parameters, method visibility, and injector type must match the actual 1.21.1 target. This schematic must not be copied as-is. The exact injector API and config belong to the Mixin version and project setup.

## 6. Debugging The Architecture

When a feature fails, categorize it before changing code:

- **Missing registry entry / crash during load:** check mod ID, register attachment, registry type, and whether the holder was dereferenced too early.
- **Works in a singleplayer test but fails on a server:** search for `net.minecraft.client` references in common classes, verify subscriber distribution, and reproduce on a dedicated server.
- **Ghost blocks, duplicated actions, or desync:** locate the authoritative mutation. Ensure server-side changes happen once and return/synchronize appropriate client results.
- **Startup crash or intermittent setup issue:** check whether a lifecycle callback is parallel and whether unsafe work must be enqueued.
- **Feature stops working after another mod loads:** prefer a public hook to a Mixin; inspect event semantics and compatibility with other listeners.
- **Tick lag:** profile the work and its frequency. Avoid world-wide scans, repeated allocations, blocking I/O, and expensive work in per-tick callbacks.

## Knowledge Check

1. In singleplayer, what is the difference between the physical client and logical server?
2. Why should a gameplay event generally mutate state only on the logical server?
3. Why is `FMLCommonSetupEvent` not the place to construct and register a new block?
4. What information does a registry ID provide beyond a convenient Java field name?
5. Name two questions to answer before adding a Mixin.

Suggested answers: Singleplayer is one physical client process hosting a logical client and an integrated logical server; the server owns gameplay state and avoids duplicate/desynchronized outcomes; registries have a controlled registration window that closes before common setup; IDs are stable serialized/networked identities and cross-mod/data references; first confirm the absence of an API/event and then verify the exact target/injection point and compatibility expectations.

## References

- [NeoForge 1.21.1 sides](https://docs.neoforged.net/docs/1.21.1/concepts/sides/)
- [NeoForge 1.21.1 events](https://docs.neoforged.net/docs/1.21.1/concepts/events/)
- [NeoForge 1.21.1 registries](https://docs.neoforged.net/docs/1.21.1/concepts/registries/)
- [NeoForge 1.21.1 getting started](https://docs.neoforged.net/docs/1.21.1/gettingstarted/)
- [NeoForge 1.21.1 resources and datagen](https://docs.neoforged.net/docs/1.21.1/resources/)
