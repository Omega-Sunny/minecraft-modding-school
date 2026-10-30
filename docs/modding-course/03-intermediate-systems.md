# Module 3: Intermediate Systems, State, Data, and Interactions

**Target:** Minecraft 1.21.1 · NeoForge 21.1.x · Java 21

Module 2 registered static content. This module adds runtime state and interaction. The core design question is ownership: a block definition is shared by every placement, a block entity belongs to one placement, a menu represents one player's current interaction, and an attachment adds data to an existing entity/chunk/block entity without replacing its class.

## Learning Objectives

- Decide when block state is sufficient and when a `BlockEntity` is appropriate.
- Persist server state, mark chunks dirty, tick efficiently, and sync only client-visible data.
- Register `MenuType`s and screens, use vanilla menu synchronization for slots and simple values, and validate all actions on the server.
- Register and use NeoForge data attachments while distinguishing attachments from item components and capabilities.
- Register a custom entity, define its attributes, and isolate model/renderer setup to the physical client.
- Build and verify two small systems without letting client state become authoritative.

## 1. Block Entities: Per-Placement State

### Pick The State Owner First

A `Block` is a registered singleton. A `BlockState` is a member of its finite set of immutable values and works well for small facts such as facing, open, powered, or lit. A `BlockEntity` is a runtime object at one world position. It is the right place for values with a large/unbounded domain (inventory, progress, owner, text, stored energy) or logic that belongs to that particular placement.

A block entity is not a custom block state and is not automatically a ticking machine. The block implements `EntityBlock` (or extends a suitable base such as `BaseEntityBlock`) and creates the block entity. A registered `BlockEntityType<T>` describes which block entity class is allowed for which blocks. The type is a registry singleton; block entity instances are created per position.

Avoid using a block entity just to store a Boolean that can be a state property. Conversely, never put a machine's changing inventory or progress in a field on the shared `Block` instance.

### Registration And Construction

A typical type registration uses a supplier constructor and the set of blocks that can host it:

```java
public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Stoneworks.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SurveyTerminalBlockEntity>>
            SURVEY_TERMINAL = TYPES.register(
                    "survey_terminal",
                    () -> BlockEntityType.Builder.of(
                            SurveyTerminalBlockEntity::new,
                            ModBlocks.SURVEY_TERMINAL.get()
                    ).build(null)
            );

    private ModBlockEntities() {}
}
```

Attach `TYPES` to the mod event bus. Register the block before the block-entity type is created; organize static declarations so the block holder exists before its type factory is evaluated. The block entity's constructor passes its registered type, its position, and its initial state to `super`:

```java
public SurveyTerminalBlockEntity(BlockPos pos, BlockState state) {
    super(ModBlockEntities.SURVEY_TERMINAL.get(), pos, state);
}
```

The host block must return a new instance from `newBlockEntity`. A factory must not return a cached instance: two world positions require two distinct block entity objects.

### Persistence Is Not Client Synchronization

Persistence and synchronization answer different questions:

- **Persistence:** what must be saved with the chunk and restored after a world reload?
- **Synchronization:** what data must a client know to render or interact with the current state?

For a block entity that owns its own data, direct NBT serialization is normally appropriate. In 1.21.1 override `saveAdditional` and `loadAdditional`, accept the registry lookup provider, and call the superclass implementation. Mark persistent changes with `setChanged()` so the containing chunk is considered dirty.

```java
public final class SurveyTerminalBlockEntity extends BlockEntity {
    private int storedSamples;

    public SurveyTerminalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SURVEY_TERMINAL.get(), pos, state);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("stored_samples", this.storedSamples);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.storedSamples = tag.getInt("stored_samples");
    }

    public void addSample() {
        this.storedSamples = Math.min(this.storedSamples + 1, 64);
        setChanged();
    }
}
```

Use your own stable tag keys; do not use keys reserved by the superclass such as `id`, coordinates, `NeoForgeData`, or `neoforge:attachments`. Validate decoded values too: old, malformed, or edited saves may contain a number outside the expected range.

For client synchronization, select the narrowest mechanism:

1. **Chunk load:** override `getUpdateTag` when a newly tracking client needs a display snapshot. `handleUpdateTag` defaults through the load path in the standard case.
2. **Block update:** supply an update packet (`getUpdatePacket`) and send an update after server-side visible data changes. `ClientboundBlockEntityDataPacket.create(this)` uses the update tag.
3. **Custom payload:** use when you need custom audience, timing, or a more specialized message.

For block updates the server generally calls `level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS)` after mutating display data. Do not send an entire inventory or private server-only state if the client only needs a progress bar. Frequent oversized sync packets are a bandwidth and privacy bug. Every inbound custom packet must also account for the possibility that its target block entity was replaced or its chunk unloaded before handling.

### Tickers And Tick Budgets

A ticker is returned by the host block's `EntityBlock#getTicker`, not registered as a global scan. Use `createTickerHelper` (or an equivalent type check) to avoid ticking a different block entity accidentally. Keep game rules and mutation on the server; a client ticker is normally only for transient visual animation.

```java
@Override
public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
        Level level,
        BlockState state,
        BlockEntityType<T> type
) {
    return level.isClientSide()
            ? null
            : createTickerHelper(type, ModBlockEntities.SURVEY_TERMINAL.get(), SurveyTerminalBlockEntity::serverTick);
}

private static void serverTick(
        Level level,
        BlockPos pos,
        BlockState state,
        SurveyTerminalBlockEntity terminal
) {
    if (terminal.storedSamples > 0 && level.getGameTime() % 20L == 0L) {
        terminal.storedSamples--;
        terminal.setChanged();
    }
}
```

A block entity ticker is called every game tick while its block entity is ticking. Avoid scanning the whole level, allocating large temporary graphs, doing I/O, or recalculating expensive values each tick. For slow operations, use a countdown or tick modulo, and only sync when visible state actually changes. Do not create a thread to mutate the block entity asynchronously.

## 2. Menus, Containers, And Screens

### Three Pieces With Different Jobs

- A **container/menu** (`AbstractContainerMenu`) is the shared server/client interaction model: slots, quick-move rules, validity, and synchronized integer data.
- A **screen** (`AbstractContainerScreen`) is a physical-client view: layout, textures, labels, and buttons.
- A **block entity or inventory** owns the persistent server data. The menu presents a view of that data; it should not become a second source of truth.

Vanilla menu packets already synchronize slot contents, slot clicks, and values registered through `ContainerData`/`DataSlot`. This is often enough for a basic machine GUI. A screen button is not a permission check: the server must check the player, current menu, distance, block identity, available inputs, and requested action before mutating data.

### Registering A Menu Type

Register menu types in `Registries.MENU`. In NeoForge 21.1.x, use `IMenuTypeExtension.create` when the client menu needs extra opening data (for example a block position):

```java
public static final DeferredRegister<MenuType<?>> MENUS =
        DeferredRegister.create(Registries.MENU, Stoneworks.MOD_ID);

public static final DeferredHolder<MenuType<?>, MenuType<SurveyTerminalMenu>> SURVEY_TERMINAL =
        MENUS.register("survey_terminal", () -> IMenuTypeExtension.create(SurveyTerminalMenu::new));
```

Attach the register on the mod event bus. Use `IMenuTypeExtension` when the opening packet needs extra data; use the standard menu supplier for menus that need no extra client constructor data.

The server opens a menu through a `MenuProvider`. For a position-based menu, the provider writes the position into the extra opening buffer. The client factory reads that position and resolves the matching client-side block entity. The block entity can expose a `MenuProvider`, or a custom block can create one after validating the clicked position.

### Menu Data And Slot Synchronization

A menu has two constructors: one used by the server with the real backing inventory/block entity, and one used by the client with the opening buffer. Both must create the same slot/data layout so slot indices and synchronized values agree on both sides. A mismatch can desynchronize inventory operations or crash when a packet refers to a slot the client did not create.

Use `addSlot` for inventory slots and `addDataSlots` for a small fixed number of integer values such as progress, energy, or a mode index. For a block-entity-backed menu, access the same server-owned inventory rather than copying items into an unrelated temporary container. Implement `stillValid` to check the world, block identity, and interaction distance; it is called on the server and is not a security substitute for validating individual actions.

The server performs the actual crafting or transfer. `quickMoveStack` must carefully preserve slot ranges, respect stack limits, and return a copy of the original stack or `ItemStack.EMPTY` according to vanilla's contract. For each menu, test cursor pickup, shift-click in both directions, closing, disconnecting, and changing the block while open.

### Registering The Client Screen

Screens are client-only. Register `RegisterMenuScreensEvent` from a mod entry point restricted to the physical client. This keeps client classes out of the common mod constructor:

```java
@Mod(value = Stoneworks.MOD_ID, dist = Dist.CLIENT)
public final class StoneworksClient {
    public StoneworksClient(IEventBus modBus) {
        modBus.addListener(this::registerScreens);
    }

    private void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.SURVEY_TERMINAL.get(), SurveyTerminalScreen::new);
    }
}
```

`SurveyTerminalScreen` extends `AbstractContainerScreen<SurveyTerminalMenu>`. It renders the background, labels, and widgets. The client screen may ask the server to perform an operation, but it never writes the authoritative inventory or energy value directly. Keep client renderer and GUI classes out of common classes that are loaded on a dedicated server.

## 3. Data Attachments And Capabilities

### Attachments Add Data To Existing Holders

The NeoForge data attachment system adds namespaced data to supported holders such as entities, chunks, and block entities. An `AttachmentType<T>` defines a default-value factory and optional serialization behavior. Register the type in `NeoForgeRegistries.ATTACHMENT_TYPES` using a `DeferredRegister`.

Attachments are useful when the data belongs to an entity or block entity that your mod does not own, or when you want a small independent piece of data attached to a supported object. If a custom block entity is yours, storing its own state directly in that class is usually clearer. Attachments on chunks must be marked dirty after mutable changes. Changes obtained from `getData` that mutate an object in place require explicit dirty marking; `setData` handles dirty marking for the supported holders.

```java
public record ResearchProgress(int points) {
    public static final Codec<ResearchProgress> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(Codec.intRange(0, 1_000_000)
                            .fieldOf("points")
                            .forGetter(ResearchProgress::points))
                    .apply(instance, ResearchProgress::new)
    );
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

Attach `TYPES` to the mod bus. Then server gameplay can immutably update player progress:

```java
ResearchProgress old = player.getData(ModAttachments.RESEARCH);
player.setData(ModAttachments.RESEARCH, new ResearchProgress(old.points() + 1));
```

`copyOnDeath()` is a policy choice, not an automatic universal rule. If data has complex copy/merge semantics, handle `PlayerEvent.Clone` and distinguish death respawn from returning from the End. Do not assume the client receives attachment changes automatically: attachment synchronization is your responsibility. Use a packet or a menu data slot if a client UI needs the value.

### Attachments Are Not A Universal Capability Replacement

In 1.21.1, data attachments are a natural replacement for many old patterns that added arbitrary data to players, chunks, or block entities. They do **not** make every capability or component API obsolete:

- For custom per-stack values, use vanilla data components, not attachments.
- For a custom machine you own, use block-entity fields/inventory and its own persistence.
- Capabilities remain an interoperability contract when you expose or consume inventories, energy, fluids, or other services through NeoForge's capability system.
- Attachments store mod data; they are not automatically a capability query API and do not automatically synchronize to clients.

This distinction prevents accidentally replacing a cross-mod service contract with private storage other mods cannot discover.

## 4. Custom Entities And Rendering

### Entity Type, Instance, Attributes, And Renderer

An `EntityType<T>` is the registered descriptor/factory. Each spawned entity is an instance. A custom living entity usually needs:

1. An entity class with appropriate movement/AI and spawn behavior.
2. A registered `EntityType` with dimensions, category, and tracking configuration.
3. Attributes registered from `EntityAttributeCreationEvent` on the mod bus.
4. A client entity model, model layer definition, texture, and renderer registration.
5. Spawn rules/data or an explicit summon/spawn path, plus translation if it has a name.

Never reference `net.minecraft.client` renderer classes from a common entity class. The server simulates the entity; clients render its synchronized state. Entity attributes, dimensions, tracking range, update interval, and spawn category affect gameplay/networking and should be selected deliberately.

A common registration shape is:

```java
public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
        DeferredRegister.create(Registries.ENTITY_TYPE, Stoneworks.MOD_ID);

public static final DeferredHolder<EntityType<?>, EntityType<SurveyDrone>> SURVEY_DRONE =
        ENTITY_TYPES.register("survey_drone", () -> EntityType.Builder
                .of(SurveyDrone::new, MobCategory.CREATURE)
                .sized(0.7F, 0.9F)
                .clientTrackingRange(8)
                .updateInterval(3)
                .build("survey_drone"));
```

Register attributes for a `Mob` subclass during `EntityAttributeCreationEvent`:

```java
@SubscribeEvent
public static void registerAttributes(EntityAttributeCreationEvent event) {
    event.put(ModEntities.SURVEY_DRONE.get(), SurveyDrone.createAttributes().build());
}
```

The model layer and renderer are registered client-side through the mod-bus events `EntityRenderersEvent.RegisterLayerDefinitions` and `EntityRenderersEvent.RegisterRenderers`. This minimal model shows the ownership chain:

```java
public final class SurveyDroneModel extends HierarchicalModel<SurveyDrone> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(Stoneworks.MOD_ID, "survey_drone"), "main");
    private final ModelPart root;

    public SurveyDroneModel(ModelPart root) { this.root = root; }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -4.0F, -4.0F, 8.0F, 8.0F, 8.0F),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 32);
    }

    @Override
    public void setupAnim(SurveyDrone entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {}

    @Override
    public ModelPart root() { return root; }
}

public final class SurveyDroneRenderer extends MobRenderer<SurveyDrone, SurveyDroneModel> {
    public SurveyDroneRenderer(EntityRendererProvider.Context context) {
        super(context, new SurveyDroneModel(context.bakeLayer(SurveyDroneModel.LAYER)), 0.35F);
    }

    @Override
    public ResourceLocation getTextureLocation(SurveyDrone entity) {
        return ResourceLocation.fromNamespaceAndPath(Stoneworks.MOD_ID,
                "textures/entity/survey_drone.png");
    }
}
```

From the physical-client mod entry point, add listeners for `EntityRenderersEvent.RegisterLayerDefinitions` and `EntityRenderersEvent.RegisterRenderers`; register `SurveyDroneModel.LAYER` with `SurveyDroneModel::createLayer` and the renderer with `context -> new SurveyDroneRenderer(context)`. The texture belongs at `assets/stoneworks/textures/entity/survey_drone.png`. Keep this setup in the client-only class and test loading on a dedicated server.

Entity rendering is not a place to run gameplay. Use synchronized entity data for values needed by the renderer; use server AI/entity methods for behavior. Keep update frequency and tracked data lean, especially for many entities.

## Exercise 1: Persistent Sample Counter

### Problem Statement

Create a `sample_crate` block with a `BlockEntity` that stores a count from 0 to 64. The server-side `addSample` method changes it, marks the chunk dirty, and publishes a block update. The count survives save/reload and is sent to tracking clients through the block entity update tag/packet. This storage device does not tick, so its host block returns no ticker; the menu exercise adds the player interaction.

### Common Failure Modes

- Storing the count on the `Block` singleton, causing every crate to share one count.
- Forgetting `setChanged()`, so the chunk does not save the modified value.
- Saving a field but never sending a block update, so the client display is stale.
- Syncing on every tick even though the count only changes on player interaction.
- Calling `getData`/registry holders before their registration window.
- Incrementing on both logical sides.

### Reference Implementation

`SampleCrateBlockEntity` owns and persists the value:

```java
public final class SampleCrateBlockEntity extends BlockEntity {
    private int samples;

    public SampleCrateBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SAMPLE_CRATE.get(), pos, state);
    }

    public int samples() {
        return samples;
    }

    public boolean addSample() {
        if (samples >= 64) return false;
        samples++;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("samples", samples);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        samples = Mth.clamp(tag.getInt("samples"), 0, 64);
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
}
```

Register the host block and block item:

```java
public static final DeferredBlock<SampleCrateBlock> SAMPLE_CRATE = BLOCKS.registerBlock(
        "sample_crate",
        SampleCrateBlock::new,
        BlockBehaviour.Properties.of().strength(2.0F)
);
public static final DeferredItem<BlockItem> SAMPLE_CRATE_ITEM =
        ITEMS.registerSimpleBlockItem("sample_crate", SAMPLE_CRATE);
```

The host block creates the instance and opens its provider. It does not own the counter field:

```java
public final class SampleCrateBlock extends Block implements EntityBlock {
    public SampleCrateBlock(BlockBehaviour.Properties properties) { super(properties); }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SampleCrateBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return null;
    }

    @Override
    public InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof SampleCrateBlockEntity crate) {
            crate.addSample();
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
```

Register the block entity type as shown in the previous paragraph and attach its deferred register to the mod bus. This first version increments on server-side empty-hand use. A production crate should accept a sample item only when the server verifies and consumes it.

Register a `BlockEntityType<SampleCrateBlockEntity>` using `BlockEntityType.Builder.of(SampleCrateBlockEntity::new, SAMPLE_CRATE.get()).build(null)`. Add the block and its block item to the existing deferred registers. The same registration/lifecycle wiring used by Module 2 applies.

The menu exercise displays the value; do not expect an integer stored on the server to appear in the client merely because it was saved. The update tag/packet makes the value available to client code. Verify by placing two crates, incrementing only one, saving, restarting, and checking each value independently.

## Exercise 2: Sample Crate Menu

### Problem Statement

Extend the sample crate from Exercise 1 with a menu and client screen. The menu displays the saved count and has one button that increments it. Use vanilla's menu-button packet path rather than a custom payload. The server accepts the button only when ID 0 is requested, the player still has this menu open, the crate still exists at the menu position, and the count is below 64. The client must never increment the count directly.

### Step-By-Step Build

1. Register a `MenuType<SampleCrateMenu>` using `IMenuTypeExtension.create` and attach it to the mod bus.
2. Implement both menu constructors and one `ContainerData` value for the sample count.
3. Open the block entity's `MenuProvider` on the server and include its `BlockPos` in the opening buffer.
4. Implement `stillValid` and `clickMenuButton` on the server-side menu.
5. Register a client-only screen with a button that calls vanilla's `handleInventoryButtonClick`.
6. Try an invalid button ID, a stale menu, maximum count, distance failure, and replacing the block while open.

### Reference Solution

```java
public final class SampleCrateMenu extends AbstractContainerMenu {
    private final SampleCrateBlockEntity crate;
    private final ContainerLevelAccess access;
    private final ContainerData data;
    private int clientSamples;

    public SampleCrateMenu(int id, Inventory inventory, FriendlyByteBuf openingData) {
        this(id, inventory, resolveCrate(inventory, openingData.readBlockPos()));
    }

    public SampleCrateMenu(int id, Inventory inventory, SampleCrateBlockEntity crate) {
        super(ModMenus.SAMPLE_CRATE.get(), id);
        this.crate = crate;
        this.access = ContainerLevelAccess.create(Objects.requireNonNull(crate.getLevel()), crate.getBlockPos());
        this.data = new ContainerData() {
            @Override
            public int get(int index) {
                if (index != 0) return 0;
                return inventory.player.level().isClientSide() ? clientSamples : crate.samples();
            }

            @Override
            public void set(int index, int value) {
                if (index == 0) clientSamples = Mth.clamp(value, 0, 64);
            }

            @Override
            public int getCount() { return 1; }
        };

        addDataSlots(data);
    }

    private static SampleCrateBlockEntity resolveCrate(Inventory inventory, BlockPos pos) {
        if (inventory.player.level().getBlockEntity(pos) instanceof SampleCrateBlockEntity crate) {
            return crate;
        }
        throw new IllegalStateException("No sample crate at menu position " + pos);
    }

    public int samples() {
        return data.get(0);
    }

    public BlockPos blockPos() { return crate.getBlockPos(); }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.SAMPLE_CRATE.get());
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId != 0 || player.level().isClientSide() || player.containerMenu != this
                || !stillValid(player)
                || player.level().getBlockEntity(blockPos()) != crate) {
            return false;
        }
        return crate.addSample();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
```

For this second exercise, make the block entity a `MenuProvider` and add these methods:

```java
public final class SampleCrateBlockEntity extends BlockEntity implements MenuProvider {
    // Keep the fields, persistence, and addSample implementation from Exercise 1.

    @Override
    public Component getDisplayName() {
        return Component.translatable("menu.stoneworks.sample_crate");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new SampleCrateMenu(id, inventory, this);
    }
}
```

Replace the block's empty-hand handler so it opens the menu instead of incrementing directly:

```java
if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
        && level.getBlockEntity(pos) instanceof SampleCrateBlockEntity crate) {
    serverPlayer.openMenu(crate, buffer -> buffer.writeBlockPos(pos));
}
```

Register the menu type:

```java
public static final DeferredRegister<MenuType<?>> MENUS =
        DeferredRegister.create(Registries.MENU, Stoneworks.MOD_ID);
public static final DeferredHolder<MenuType<?>, MenuType<SampleCrateMenu>> SAMPLE_CRATE =
        MENUS.register("sample_crate", () -> IMenuTypeExtension.create(SampleCrateMenu::new));
```

The server opens the block entity, which implements `MenuProvider`, with `serverPlayer.openMenu(crate, buffer -> buffer.writeBlockPos(crate.getBlockPos()))`. The block entity's `createMenu` returns `new SampleCrateMenu(id, inventory, this)`, and its `getDisplayName` returns a translated component. `ContainerData.get` reads the live value on the server; `set` only receives synchronized display state on the client and does not mutate the block entity.

Register the client screen on the mod event bus with `RegisterMenuScreensEvent`. Its button uses vanilla's container-button packet:

```java
public final class SampleCrateScreen extends AbstractContainerScreen<SampleCrateMenu> {
    public SampleCrateScreen(SampleCrateMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 100;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.translatable("screen.stoneworks.add_sample"), button -> {
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0);
            }
        }).bounds(leftPos + 40, topPos + 55, 96, 20).build());
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF202830);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 8, 8, 0xFFFFFF, false);
        graphics.drawString(font, Component.translatable("screen.stoneworks.samples", menu.samples()),
                8, 30, 0xFFFFFF, false);
    }
}
```

The menu is intentionally slotless, so `quickMoveStack` returns `ItemStack.EMPTY`. Add a language entry for `screen.stoneworks.add_sample`, `screen.stoneworks.samples`, and `menu.stoneworks.sample_crate`. Verify that button clicks arrive on the server, that the count caps at 64, and that relogging restores the saved value.

## Verification Checklist

- [ ] The data owner is correct: state, block entity, attachment, or item component.
- [ ] Every persisted mutation marks its owning chunk/block entity dirty.
- [ ] Server state is authoritative; clients only display synchronized data.
- [ ] Tickers have a clear purpose and no broad world scans.
- [ ] Menus create identical slot/data layouts on client and server.
- [ ] Menu actions and packets validate the current player, menu, target, distance, and inputs server-side.
- [ ] Attachments use a registered type and an explicit serialization/sync policy.
- [ ] Client renderers/screens are isolated from dedicated-server classloading.

## References

- [NeoForge 1.21.1 block entities](https://docs.neoforged.net/docs/1.21.1/blockentities/)
- [NeoForge 1.21.1 data attachments](https://docs.neoforged.net/docs/1.21.1/datastorage/attachments/)
- [NeoForge 1.21.1 data components](https://docs.neoforged.net/docs/1.21.1/items/datacomponents/)
- [NeoForge 1.21.1 networking payloads](https://docs.neoforged.net/docs/1.21.1/networking/payload/)
- [NeoForge 1.21.1 registries](https://docs.neoforged.net/docs/1.21.1/concepts/registries/)
