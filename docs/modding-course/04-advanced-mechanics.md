# Module 4: Advanced Mechanics And Worldbuilding

**Target:** Minecraft 1.21.1 · NeoForge 21.1.x · Java 21

This module combines data-driven world generation, client/server communication, and compatibility-sensitive interception. The advanced-engineering theme is to make the boundary explicit: data files describe registry-backed world content, packets carry bounded requests or snapshots, the server validates every gameplay decision, and bytecode changes are narrow and justified.

## Learning Objectives

- Trace a worldgen feature from its configured definition to its biome placement.
- Build an ore feature and inject its placed feature into biomes through a NeoForge biome modifier.
- Define and register `CustomPacketPayload`s with `PayloadRegistrar` and `StreamCodec`.
- Design a secure serverbound request and a minimal clientbound synchronization payload.
- Choose events, extension APIs, AI goals, or Mixins in that order of preference.
- Implement the Survey Relay mini-project, combining a block entity, menu, attachment, and validated networking.

## 1. Data-Driven World Generation

### The Pipeline And The Ownership Boundary

World generation is a sequence of registry-backed data definitions, not a feature constructor called every chunk from your mod class:

1. A **configured feature** describes what is generated and its configuration. For ore, this includes target block predicates, the replacement state, vein size, and exposure discard chance.
2. A **placed feature** applies location/selection modifiers to that configured feature: count, horizontal spreading, height distribution, biome filtering, and similar constraints.
3. A **biome modifier** adds or removes a placed feature from selected biomes at a named generation step such as `underground_ores`.
4. Chunk generation asks the biome's configured generation settings to run that feature at the relevant step.

Configured and placed features are datapack registry entries loaded as world data. A Java static initializer should not directly place ore in the world. The data-driven design gives data packs control, allows configuration to be validated during reload, and avoids a mod repeatedly mutating chunks after generation.

Existing worlds only generate newly created chunks with new settings. Already-generated chunks are not retroactively regenerated. Test in a fresh world and travel far enough to create new chunks; do not conclude that a feature is broken merely because a spawn chunk was generated before the mod or datapack change.

### Example Configured Ore Feature

A feature JSON can use Minecraft's built-in `minecraft:ore` feature type. Save this as `data/stoneworks/worldgen/configured_feature/copper_ore.json`:

```json
{
  "type": "minecraft:ore",
  "config": {
    "size": 8,
    "discard_chance_on_air_exposure": 0.0,
    "targets": [
      {
        "target": {
          "predicate_type": "minecraft:tag_match",
          "tag": "minecraft:stone_ore_replaceables"
        },
        "state": {
          "Name": "stoneworks:copper_ore"
        }
      },
      {
        "target": {
          "predicate_type": "minecraft:tag_match",
          "tag": "minecraft:deepslate_ore_replaceables"
        },
        "state": {
          "Name": "stoneworks:deepslate_copper_ore"
        }
      }
    ]
  }
}
```

Both replacement blocks must be registered. The target tags matter: only blocks matching one of the predicates can be replaced. `size` is the approximate vein size, not a guarantee of that many exposed blocks. The discard chance reduces ore exposed to air; gameplay balance depends on the target tags, placement frequency, height, and loot table together.

A mod may instead add a tag for its own replaceable blocks and use it in the target predicate. Prefer vanilla tags when their semantics fit and data-pack tags when modpack makers should be able to extend the target set.

### Example Placed Feature

Save as `data/stoneworks/worldgen/placed_feature/copper_ore.json`:

```json
{
  "feature": "stoneworks:copper_ore",
  "placement": [
    { "type": "minecraft:count", "count": 8 },
    { "type": "minecraft:in_square" },
    {
      "type": "minecraft:height_range",
      "height": {
        "type": "minecraft:uniform",
        "min_inclusive": { "absolute": -48 },
        "max_inclusive": { "absolute": 32 }
      }
    },
    { "type": "minecraft:biome" }
  ]
}
```

The modifiers form a placement pipeline. `count` samples attempts per generation unit, `in_square` spreads positions over the chunk, `height_range` samples vertical positions, and `biome` ensures the selected position is valid for a biome that has the feature. Modifiers do not change ore block properties or loot. Those are defined elsewhere.

Choose a frequency and height distribution based on a deliberate target. Excessive counts, overlapping features, or expensive predicates add worldgen cost. Verify the generated density over multiple seeds and compare fresh chunks rather than relying on one vein.

### Add It To Biomes With A Biome Modifier

Save as `data/stoneworks/neoforge/biome_modifier/add_copper_ore.json`:

```json
{
  "type": "neoforge:add_features",
  "biomes": "#minecraft:is_overworld",
  "features": "stoneworks:copper_ore",
  "step": "underground_ores"
}
```

This selects all biomes in the vanilla overworld tag and adds the placed feature at the underground ore step. A custom biome tag is usually safer for a mod-specific distribution. Use the actual path `data/<namespace>/neoforge/biome_modifier/` for the NeoForge biome modifier registry.

A biome modifier determines which biome generation settings receive a placed feature; it does not guarantee a vein at every location. The placed feature still applies its position modifiers, and all feature targets must be valid at that location.

### Datagen For Dynamic Registries

For built-in/worldgen datapack registries, datagen uses `RegistrySetBuilder` and `DatapackBuiltinEntriesProvider`, registered in `GatherDataEvent`. The bootstrap context registers entries by `ResourceKey` and resolves cross-registry dependencies through `lookup`. For simpler features, hand-authored JSON is often easiest to inspect; datagen becomes valuable when many configured/placed features share parameters or when generation must validate registry references.

Do not confuse ordinary registries, registered during mod startup, with datapack registries, loaded with world data. Do not query dynamic registry values from a static initializer. Validate output with datagen, confirm each referenced key exists, and inspect server logs during datapack reload.

## 2. Payload Networking

### Request And Snapshot, Not Shared Memory

Client and server are separate logical sides even in singleplayer. A packet is a serialized message across that boundary; it is not a shared object reference. NeoForge 1.21.1 uses vanilla custom payloads, `CustomPacketPayload`, `StreamCodec`, `RegisterPayloadHandlersEvent`, and `PayloadRegistrar`.

Choose a payload direction based on authority:

- **Serverbound request:** the client asks the server to attempt an action. It must not be phrased as a command to accept client state.
- **Clientbound update:** the server sends a small authoritative result or view snapshot to one or more clients.
- **Bidirectional:** use only when the same payload meaningfully flows in both directions and the handler is safe for both contexts.

Register payloads during `RegisterPayloadHandlersEvent` on the mod event bus. The registrar version is part of the network compatibility contract; bump or negotiate it when the wire format changes incompatibly.

### A Bounded Serverbound Request

```java
public record ActivateRelayPayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<ActivateRelayPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Stoneworks.MOD_ID, "activate_relay"));

    public static final StreamCodec<ByteBuf, ActivateRelayPayload> STREAM_CODEC =
            StreamCodec.composite(BlockPos.STREAM_CODEC, ActivateRelayPayload::pos, ActivateRelayPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
```

The record contains a position only. The client does **not** send an energy amount, player score, item output, permission flag, or claimed success. The server reads those from its own level, player, open menu, and block entity.

Register the serverbound handler:

```java
@SubscribeEvent
public static void registerPayloads(RegisterPayloadHandlersEvent event) {
    PayloadRegistrar registrar = event.registrar("1");
    registrar.playToServer(
            ActivateRelayPayload.TYPE,
            ActivateRelayPayload.STREAM_CODEC,
            ActivateRelayHandler::handle
    );
}
```

Payload handlers run on the main thread by default, which is the right place for short world mutations. Do not change the handler to the network thread merely to make it appear faster. Network-thread handlers must not access level/entity/menu state; expensive pure computation can be done off-thread, then enqueued back through `IPayloadContext#enqueueWork` with assumptions revalidated.

### Validate Every Client Request

An action handler should reject malformed, stale, unauthorized, or out-of-range requests before changing anything:

```java
public final class ActivateRelayHandler {
    private ActivateRelayHandler() {}

    public static void handle(ActivateRelayPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }

        ServerLevel level = player.serverLevel();
        BlockPos pos = payload.pos();
        if (!level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
            || player.distanceToSqr(Vec3.atCenterOf(pos)) > 64.0D) {
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof SurveyRelayBlockEntity relay)) {
            return;
        }
        if (!(player.containerMenu instanceof SurveyRelayMenu menu)
                || !menu.blockPos().equals(pos)
                || !menu.stillValid(player)) {
            return;
        }

        relay.tryActivate(player);
    }
}
```

This is the security boundary. Do not force-load an arbitrary chunk just because a client supplied a position. Check that the player is still interacting with the expected menu, re-check the block entity, enforce distance and permission rules, validate resources and output room, and apply rate limits if the action is expensive. Client-side checks are useful for feedback but are not security controls.

The screen sends a request using `PacketDistributor.sendToServer(new ActivateRelayPayload(menu.blockPos()))`. It can optimistically animate a button, but it should display success only after authoritative state arrives.

### Synchronize Minimal State Back

For data already represented by menu slots or `ContainerData`, use the menu's built-in synchronization. For a block-entity update, use its update packet/tag. For a custom result that needs a specific audience or a player attachment, send a small clientbound payload:

```java
public record ResearchProgressPayload(int points) implements CustomPacketPayload {
    public static final Type<ResearchProgressPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Stoneworks.MOD_ID, "research_progress"));

    public static final StreamCodec<ByteBuf, ResearchProgressPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, ResearchProgressPayload::points,
                    ResearchProgressPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
```

Register it with `playToClient`, handle it on the client main thread, and clamp the received value before using it. If only the player who acted needs the update, send only to that player. If all clients tracking a chunk need a block display change, use the chunk tracking distributor or standard block update instead.

Keep packets small and bounded. Avoid unbounded strings/lists, arbitrary NBT from clients, and trusting client-provided registry IDs without validating that they are allowed. A codec proves that bytes have a parseable shape; it does not prove that the request is legal.

## 3. Advanced Events And Mixin Interception

### Prefer Behavioral Extension Points

Before a Mixin, search for:

1. A public event whose semantics match the behavior.
2. A NeoForge extension interface or registry entry designed for extension.
3. A data-driven tag, biome modifier, loot modifier, recipe, or data map.
4. A subclass, goal, or strategy object that can replace only your entity's behavior.
5. A Mixin only when the required hook does not exist.

For AI, add or replace a `Goal` on your own mob rather than patching vanilla `Mob` globally. If you need to change a vanilla mob's behavior, check spawn/attribute/goal events and NeoForge's supported AI hooks first. A global Mixin can affect every entity, every mod, and every server tick.

### Injection Point Tradeoffs

- `@At("HEAD")`: runs before the target body. Good for a narrow precondition; dangerous when cancelling because it can skip initialization or invariants.
- `@At("RETURN")`: runs after a return point. It may match multiple returns; use `ordinal` only after verifying bytecode/source and expected matches.
- `@At("INVOKE")`: hooks around a specific call. Specify the target method descriptor and injection shift intentionally. It is often more brittle because local control flow changes.
- `@Redirect`: replaces a specific invocation. It can conflict with another mod redirecting the same call and removes the original behavior unless reimplemented.
- `@Overwrite`: replaces the whole method and is the most compatibility-hostile choice. Avoid unless there is no narrower alternative and compatibility is documented.

A schematic example, not a copy-paste target, shows the expected discipline:

```java
@Mixin(Mob.class)
abstract class MobMixin {
    @Inject(method = "serverAiStep()V", at = @At("HEAD"), require = 1)
    private void stoneworks$beforeServerAiStep(CallbackInfo callback) {
        // Observe or update only a narrow, documented invariant.
        // Do not cancel vanilla AI unless replacing its full contract is intentional.
    }
}
```

The method descriptor and injector callback signature must match the exact mapped 1.21.1 target. A production Mixin requires the project's supported Mixin configuration, a rationale, and a test for a missing/multiple injection. An injection that silently stops matching can be just as dangerous as one that crashes startup; use `require`/`expect` according to the intended compatibility policy.

### Edge Cases And Compatibility

- A cancellable event or injected method may have multiple listeners/transformers. Preserve other mods' behavior where possible.
- An `ordinal` or local capture binds to bytecode layout. Re-check it after every Minecraft/NeoForge update.
- A Mixin into common code must not load client-only types on a dedicated server.
- Do not hold stale entity/world references across ticks, reloads, or packet boundaries.
- Test the integrated client, dedicated server, and a minimally modded compatibility profile.
- Log a concise diagnostic for unexpected state; do not log every tick.
- Keep injected code tiny and delegate to ordinary code that can be unit tested.

A good Mixin has a small blast radius and a clear reason. If you cannot state the target invariant and expected vanilla behavior in one sentence, the patch is not ready.

## Exercise: Survey Relay Mini-Project

### Challenge

Build a `survey_relay` block that has persistent charge, a server-authoritative GUI, and per-player research progress:

- The block entity starts with 100 charge, persists it, and exposes it to the menu.
- The GUI shows charge and the player's research points.
- A screen button asks the server to activate the relay.
- Each valid activation consumes 10 charge and grants 1 research point, capped at 1,000,000.
- The research points live in a serialized player data attachment and copy on death.
- The server sends a small clientbound update so the player's HUD/menu can show the new total.
- Requests fail safely if the chunk is unloaded, the block is replaced, the player is too far away, the wrong menu is open, or charge is insufficient.

### Architecture Decisions

- `SurveyRelayBlockEntity` owns and persists charge because charge belongs to one placement.
- `SurveyRelayMenu` exposes charge through `ContainerData`; research points use a bounded custom payload because their range exceeds the menu data slot's safe range.
- `ResearchProgress` is an immutable record attachment on the player.
- `ActivateRelayPayload` carries only a `BlockPos` from client to server.
- `ResearchProgressPayload` carries the authoritative integer from server to client.
- The menu's existing slot/data synchronization handles displayed menu values; the custom payload updates player attachment state for the HUD or other client displays.

### Reference Solution: Registrations And Data Owners

The following code is organized as normal Java classes under `com.example.stoneworks`. Add each deferred register to the mod event bus from the mod constructor. The block entity, not the block, owns charge; the player attachment owns research progress.

```java
public record ResearchProgress(int points) {
    public static final int MAX_POINTS = 1_000_000;
    public static final Codec<ResearchProgress> CODEC = Codec.intRange(0, MAX_POINTS)
            .fieldOf("points")
            .xmap(ResearchProgress::new, ResearchProgress::points)
            .codec();

    public ResearchProgress addPoint() {
        return new ResearchProgress(Math.min(points + 1, MAX_POINTS));
    }
}
```

```java
public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Stoneworks.MOD_ID);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ResearchProgress>> RESEARCH =
            TYPES.register("research", () -> AttachmentType.builder(() -> new ResearchProgress(0))
                    .serialize(ResearchProgress.CODEC)
                    .copyOnDeath()
                    .build());

    private ModAttachments() {}
}
```

Register `TYPES` on the mod bus. Register the block entity type and host block:

```java
public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Stoneworks.MOD_ID);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SurveyRelayBlockEntity>> RELAY =
            TYPES.register("survey_relay", () -> BlockEntityType.Builder
                    .of(SurveyRelayBlockEntity::new, ModBlocks.SURVEY_RELAY.get())
                    .build(null));

    private ModBlockEntities() {}
}
```

```java
public static final DeferredBlock<SurveyRelayBlock> SURVEY_RELAY = BLOCKS.registerBlock(
        "survey_relay",
        SurveyRelayBlock::new,
        BlockBehaviour.Properties.of().strength(3.0F).requiresCorrectToolForDrops()
);
public static final DeferredItem<BlockItem> SURVEY_RELAY_ITEM =
        ITEMS.registerSimpleBlockItem("survey_relay", SURVEY_RELAY);
```

`SurveyRelayBlockEntity` persists the value, clamps corrupted data, and updates clients only when charge changes:

```java
public final class SurveyRelayBlockEntity extends BlockEntity {
    private int charge = 100;

    public SurveyRelayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RELAY.get(), pos, state);
    }

    public int charge() { return charge; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("charge", charge);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        charge = Mth.clamp(tag.getInt("charge"), 0, 100);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public boolean tryActivate(ServerPlayer player) {
        ResearchProgress current = player.getData(ModAttachments.RESEARCH);
        if (charge < 10 || current.points() >= ResearchProgress.MAX_POINTS) return false;

        charge -= 10;
        ResearchProgress updated = current.addPoint();
        player.setData(ModAttachments.RESEARCH, updated);
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        PacketDistributor.sendToPlayer(player, new ResearchProgressPayload(updated.points()));
        return true;
    }
}
```

`SurveyRelayBlock` creates the per-position block entity and opens its menu only on the server:

```java
public final class SurveyRelayBlock extends Block implements EntityBlock {
    public SurveyRelayBlock(BlockBehaviour.Properties properties) { super(properties); }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SurveyRelayBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return null;
    }

    @Override
    public InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof SurveyRelayBlockEntity relay) {
            MenuProvider provider = new SimpleMenuProvider(
                    (id, inventory, opener) -> new SurveyRelayMenu(id, inventory, relay),
                    Component.translatable("menu.stoneworks.survey_relay")
            );
            serverPlayer.openMenu(provider, buffer -> buffer.writeBlockPos(pos));
                PacketDistributor.sendToPlayer(serverPlayer, new ResearchProgressPayload(
                    serverPlayer.getData(ModAttachments.RESEARCH).points()));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
```

Register `ModBlockEntities.TYPES` and `ModMenus.MENUS` on the mod bus, in addition to the block/item/attachment registers. Wire `SurveyRelayPayloads::register` to the mod event bus as well.

### Reference Solution: Payloads And Validation

```java
public record ActivateRelayPayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<ActivateRelayPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Stoneworks.MOD_ID, "activate_relay"));
    public static final StreamCodec<ByteBuf, ActivateRelayPayload> CODEC =
            StreamCodec.composite(BlockPos.STREAM_CODEC, ActivateRelayPayload::pos, ActivateRelayPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

public record ResearchProgressPayload(int points) implements CustomPacketPayload {
    public static final Type<ResearchProgressPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Stoneworks.MOD_ID, "research_progress"));
    public static final StreamCodec<ByteBuf, ResearchProgressPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, ResearchProgressPayload::points,
                    ResearchProgressPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
```

Register them in `RegisterPayloadHandlersEvent`:

```java
@SubscribeEvent
public static void registerPayloads(RegisterPayloadHandlersEvent event) {
    PayloadRegistrar registrar = event.registrar("1");
    registrar.playToServer(ActivateRelayPayload.TYPE, ActivateRelayPayload.CODEC,
            SurveyRelayPayloadHandler::activate);
    registrar.playToClient(ResearchProgressPayload.TYPE, ResearchProgressPayload.CODEC,
            SurveyRelayPayloadHandler::syncResearch);
}
```

The handler verifies all mutable world assumptions at receipt time:

```java
public final class SurveyRelayPayloadHandler {
    private SurveyRelayPayloadHandler() {}

    public static void activate(ActivateRelayPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        ServerLevel level = player.serverLevel();
        BlockPos pos = payload.pos();
        if (!level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
            || player.distanceToSqr(Vec3.atCenterOf(pos)) > 64.0D) return;
        if (!(level.getBlockEntity(pos) instanceof SurveyRelayBlockEntity relay)) return;
        if (!(player.containerMenu instanceof SurveyRelayMenu menu)
                || !menu.blockPos().equals(pos)
            || !menu.isFor(relay)
                || !menu.stillValid(player)) return;
        relay.tryActivate(player);
    }

    public static void syncResearch(ResearchProgressPayload payload, IPayloadContext context) {
        Player player = context.player();
        int points = Mth.clamp(payload.points(), 0, ResearchProgress.MAX_POINTS);
        player.setData(ModAttachments.RESEARCH, new ResearchProgress(points));
    }
}
```

The `syncResearch` handler uses only common `Player`/attachment APIs, so it is safe to register on both physical distributions. If the handler needs a client-only API, move it behind client-only registration rather than loading `net.minecraft.client` classes on a dedicated server. The `PayloadRegistrar` defaults to main-thread handling, so these short handlers can safely access world/player state. The client accepts only bounded server state; it never sends a player score back as authority.

### Reference Solution: Menu And Screen Contract

Register a `MenuType<SurveyRelayMenu>` with `IMenuTypeExtension.create`. The server opens a `MenuProvider` with the block position in the extra buffer. The client menu constructor resolves the relay from that position. The menu synchronizes only charge through `ContainerData`:

```java
public final class SurveyRelayMenu extends AbstractContainerMenu {
    private final BlockPos pos;
    private final ContainerLevelAccess access;
    private final ContainerData data;
    private final SurveyRelayBlockEntity relay;
    private final Player player;
    private int clientCharge;

    public SurveyRelayMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, resolveRelay(inventory, buffer.readBlockPos()));
    }

    public SurveyRelayMenu(int id, Inventory inventory, SurveyRelayBlockEntity relay) {
        super(ModMenus.SURVEY_RELAY.get(), id);
        this.pos = relay.getBlockPos();
        this.relay = relay;
        this.player = inventory.player;
        this.access = ContainerLevelAccess.create(Objects.requireNonNull(relay.getLevel()), pos);
        this.data = new ContainerData() {
            @Override
            public int get(int index) {
                if (index != 0) return 0;
                return player.level().isClientSide() ? clientCharge : relay.charge();
            }

            @Override
            public void set(int index, int value) {
                if (index == 0) {
                    clientCharge = Mth.clamp(value, 0, 100);
                }
            }

            @Override
            public int getCount() { return 1; }
        };
        addDataSlots(data);
    }

    private static SurveyRelayBlockEntity resolveRelay(Inventory inventory, BlockPos pos) {
        if (inventory.player.level().getBlockEntity(pos) instanceof SurveyRelayBlockEntity relay) {
            return relay;
        }
        throw new IllegalStateException("No survey relay at menu position " + pos);
    }

    public BlockPos blockPos() { return pos; }
    public boolean isFor(SurveyRelayBlockEntity candidate) { return relay == candidate; }
    public int charge() { return data.get(0); }
    public int researchPoints() { return player.getData(ModAttachments.RESEARCH).points(); }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.SURVEY_RELAY.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY; // This menu has no inventory slots.
    }
}
```

Register the menu type:

```java
public static final DeferredRegister<MenuType<?>> MENUS =
        DeferredRegister.create(Registries.MENU, Stoneworks.MOD_ID);
public static final DeferredHolder<MenuType<?>, MenuType<SurveyRelayMenu>> SURVEY_RELAY =
        MENUS.register("survey_relay", () -> IMenuTypeExtension.create(SurveyRelayMenu::new));
```

The `ContainerData` implementation exposes live server-owned charge and caches only the synchronized view on the client. Research progress is read from the player's client attachment, initialized when the menu opens, then updated by the bounded server-to-client payload. This avoids truncating a million-point value through menu data slots, whose integer synchronization range is limited. A client cannot set relay charge by sending a menu data value: menu data packets flow from server to client. `quickMoveStack` returns `ItemStack.EMPTY` because this menu has no slots.

Register a minimal screen from the physical-client mod entry point:

```java
@Mod(value = Stoneworks.MOD_ID, dist = Dist.CLIENT)
public final class StoneworksClient {
    public StoneworksClient(IEventBus modBus) {
        modBus.addListener(this::registerRelayScreen);
    }

    private void registerRelayScreen(RegisterMenuScreensEvent event) {
        event.register(ModMenus.SURVEY_RELAY.get(), SurveyRelayScreen::new);
    }
}

public final class SurveyRelayScreen extends AbstractContainerScreen<SurveyRelayMenu> {
    public SurveyRelayScreen(SurveyRelayMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 100;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.translatable("screen.stoneworks.activate"), button ->
                PacketDistributor.sendToServer(new ActivateRelayPayload(menu.blockPos())))
                .bounds(leftPos + 40, topPos + 60, 96, 20)
                .build());
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF202830);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 8, 8, 0xFFFFFF, false);
        graphics.drawString(font, Component.translatable("screen.stoneworks.charge", menu.charge()),
                8, 28, 0xFFFFFF, false);
        graphics.drawString(font, Component.translatable("screen.stoneworks.research", menu.researchPoints()),
                8, 42, 0xFFFFFF, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
```

Add translations for `menu.stoneworks.survey_relay`, `screen.stoneworks.activate`, `screen.stoneworks.charge`, and `screen.stoneworks.research`. The screen button callback does not change values locally. On success, the block update refreshes charge, the clientbound payload updates the player's attachment, and the menu's normal `ContainerData` synchronization refreshes displayed values. On failure, no authoritative data changes.

### Acceptance Tests

- Place two relays, open one, and confirm the other is unaffected.
- Activate three times: charge becomes 70 and research points increase by 3.
- Restart the world: charge and research attachment values persist.
- Die and respawn: points copy once, without duplication on End return.
- Send an out-of-range request, a request while another menu is open, and a request for a non-relay position; none changes data or force-loads a chunk.
- Test a dedicated server and a client HUD/menu update.

## References

- [NeoForge 1.21.1 worldgen concepts](https://docs.neoforged.net/docs/1.21.1/worldgen/)
- [NeoForge 1.21.1 registries and dynamic registries](https://docs.neoforged.net/docs/1.21.1/concepts/registries/)
- [NeoForge 1.21.1 payload registration](https://docs.neoforged.net/docs/1.21.1/networking/payload/)
- [NeoForge 1.21.1 data attachments](https://docs.neoforged.net/docs/1.21.1/datastorage/attachments/)
- [NeoForge 1.21.1 block entities](https://docs.neoforged.net/docs/1.21.1/blockentities/)
- [NeoForge 1.21.1 events](https://docs.neoforged.net/docs/1.21.1/concepts/events/)
