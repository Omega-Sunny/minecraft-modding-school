# NeoForge Modding Masterclass

**Target:** Minecraft 1.21.1, NeoForge 21.1.x, Java 21  
**Audience:** Java developers who want to understand and build maintainable Minecraft mods  
**Course files:** [Module 1: Architecture and Lifecycle](01-architecture-and-lifecycle.md) · [Module 2: Beginner Mods](02-beginner-mods.md) · [Module 3: Intermediate Systems](03-intermediate-systems.md) · [Module 4: Advanced Mechanics](04-advanced-mechanics.md) · [Module 5: Optimization and Best Practices](05-optimization-and-best-practices.md)

This course treats a mod as a participant in a running game engine, not as a collection of snippets. Each system is introduced through its responsibilities, timing, ownership, and failure modes before its API is used. The examples use current 1.21.1-era registries, data components, resource/data packs, and Java 21 conventions.

## Course Outcomes

After completing the material currently included in this course, you should be able to:

- Explain how client and server simulation interact, and choose the correct logical side for gameplay changes.
- Register content safely and understand when registered objects become usable.
- Select the right event bus and lifecycle phase, and avoid unsafe work during parallel setup.
- Explain when a Mixin is justified and how to keep one narrow and diagnosable.
- Add registered items, tools, armor, blocks, and creative tabs.
- Separate Java behavior from resource-pack assets and data-pack gameplay definitions.
- Generate and validate recipes, loot tables, translations, and models with NeoForge datagen.
- Profile server ticks, allocations, GC, and client rendering before optimizing.
- Offload pure computation safely and keep world mutations on the owning game thread.
- Design optional integrations and public APIs for long-term modpack compatibility.
- Diagnose the common failures caused by identifiers, registration order, missing resources, or side mistakes.

## Curriculum

| File | Focus | You will build or understand |
| --- | --- | --- |
| [01-architecture-and-lifecycle.md](01-architecture-and-lifecycle.md) | Engine architecture and NeoForge integration | Ticks and sides, thread ownership, event buses, lifecycle, deferred registration, and Mixin tradeoffs |
| [02-beginner-mods.md](02-beginner-mods.md) | First complete content | Items, tools, armor, blocks and block states, tabs, recipes, loot, resources, datagen, and two full exercises |
| [03-intermediate-systems.md](03-intermediate-systems.md) | Runtime state and interaction | Block entities, persistence/sync, menus, attachments, custom entities/rendering, and two exercises |
| [04-advanced-mechanics.md](04-advanced-mechanics.md) | Worldbuilding and integration | Data-driven worldgen, secure payload networking, advanced interception, and a Survey Relay project |
| [05-optimization-and-best-practices.md](05-optimization-and-best-practices.md) | Performance and production readiness | Tick/memory and render optimization, safe async, Spark/JFR profiling, compatibility, and release checks |

## How To Use The Guide

1. Read the architectural explanation before copying code. Most difficult mod bugs are timing, ownership, or data-contract bugs, not Java syntax errors.
2. Use one consistent mod ID in Java identifiers, resource paths, metadata, and generated data. The examples use `stoneworks`; replace it everywhere if you adopt the examples.
3. Keep your NeoForge MDK's Minecraft and NeoForge versions aligned to 1.21.1 and the 21.1.x line. API details can change between Minecraft versions and even between NeoForge minor versions.
4. Run the client, run a dedicated server, run datagen, and execute `runGameTestServer`. A singleplayer test does not prove that physical-server classloading is safe.
5. Inspect the generated JSON and test it in-game. Datagen is code that produces data; generated output still needs review and version control.

## Baseline Environment

- JDK 21, 64-bit.
- A NeoForge 1.21.1 MDK and its Gradle wrapper.
- IDE support for Gradle and the Minecraft/NeoForge sources. Source navigation is part of the learning process: when an API's behavior is unclear, inspect its declaration and nearby vanilla usages.
- Git, so changes to generated resources and code can be reviewed and reproduced.

The repository includes a root-level NeoForge Gradle project for the `stoneworks` example mod. Its registered items, tool, armor, luminous block, creative tab, custom data component, and generated recipe provide a runnable companion to the course. The sample models deliberately reuse vanilla textures so the project runs without bundled art; replace them with mod textures as an asset-authoring exercise. See the repository [README](../../README.md) for run commands.

## Version And Reference Policy

The guide is pinned to 1.21.1. Prefer the versioned documentation and source attached to the exact MDK dependency over tutorials for older Forge releases or newer Minecraft versions. Official references:

- [NeoForge 1.21.1 getting started](https://docs.neoforged.net/docs/1.21.1/gettingstarted/)
- [Registries](https://docs.neoforged.net/docs/1.21.1/concepts/registries)
- [Sides](https://docs.neoforged.net/docs/1.21.1/concepts/sides)
- [Events](https://docs.neoforged.net/docs/1.21.1/concepts/events)
- [Items and data components](https://docs.neoforged.net/docs/1.21.1/items/)
- [Blocks and blockstates](https://docs.neoforged.net/docs/1.21.1/blocks/)
- [Tools and armor](https://docs.neoforged.net/docs/1.21.1/items/tools/)
- [Resources and data generation](https://docs.neoforged.net/docs/1.21.1/resources/)
- [Spark command usage](https://spark.lucko.me/docs/Command-Usage)
- [Java 21 `jcmd` reference](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jcmd.html)

## Suggested Study Rhythm

For each module, make a small change, start the relevant run configuration, inspect the log, and record what you expected versus what happened. When a change crosses a server/client boundary, validate both an integrated client and a dedicated server. When it changes resource or data files, run datagen and inspect output before loading a world.
