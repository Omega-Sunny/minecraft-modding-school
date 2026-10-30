# Minecraft Modding School

A NeoForge modding course and runnable example mod targeting Minecraft 1.21.1 and Java 21.

## Course

Start with the [course overview](docs/modding-course/00-overview.md), then work through [Module 1: Architecture and Lifecycle](docs/modding-course/01-architecture-and-lifecycle.md), [Module 2: Beginner Mods](docs/modding-course/02-beginner-mods.md), [Module 3: Intermediate Systems](docs/modding-course/03-intermediate-systems.md), [Module 4: Advanced Mechanics](docs/modding-course/04-advanced-mechanics.md), and [Module 5: Optimization and Best Practices](docs/modding-course/05-optimization-and-best-practices.md).

## Run The Example Mod

Use a 64-bit JDK 21. The first Gradle run downloads Gradle, Minecraft, NeoForge, and mappings; allow it time to finish.

```powershell
.\gradlew.bat runClient
.\gradlew.bat runServer
.\gradlew.bat runData
.\gradlew.bat runGameTestServer
.\gradlew.bat build
```

Generated data is written to `src/generated/resources`. The built mod jar is written to `build/libs`.
`runGameTestServer` executes the registered `stoneworks` GameTests and exits with a failure status if any required test fails.

After changing generated content, run `runData`, review the generated files, and commit them. Do not hand-edit files under `src/generated/resources`; the model, language, loot, and recipe providers are their source of truth. Worldgen datapack JSON remains under `src/main/resources/data/stoneworks` and is edited directly.

The first `runServer` launch may stop because the Minecraft EULA has not been accepted. Review the EULA, set `eula=true` in the `eula.txt` created in the server run directory, then launch `runServer` again. Keep development servers private and do not weaken authentication on an internet-facing server.

## Try The Advanced Examples

- Place a Sample Crate and use its button to increment the saved count.
- Place a Survey Relay. Each activation spends 10 charge and grants one research point to the player attachment.
- Use `/summon stoneworks:survey_drone` to test the custom entity and renderer.
- Copper ore generates in newly created overworld chunks; existing chunks are not retroactively regenerated.
