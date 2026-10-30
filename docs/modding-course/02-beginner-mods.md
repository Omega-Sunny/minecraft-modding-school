# Module 2: Beginner Mods, Building The Basics

**Target:** Minecraft 1.21.1 · NeoForge 21.1.x · Java 21

This module builds the first content layer of a mod. The key distinction is between **registered definitions** (the item or block type) and **runtime instances** (an `ItemStack` or `BlockState`). Java registers definitions and behavior; JSON resources describe models, translations, recipes, and loot. Keeping those roles separate makes content easier to test, localize, balance, and extend with data packs.

## Learning Objectives

- Register items and blocks using `DeferredRegister.Items`, `DeferredRegister.Blocks`, `DeferredItem`, and `DeferredBlock`.
- Create conventional tools and armor with the 1.21.1 item APIs.
- Explain what block states represent and choose state properties carefully.
- Add content to a custom creative tab.
- Use item data components instead of storing ad hoc mutable data on a shared `Item`.
- Write or generate recipes, block loot, models, and translations.
- Complete two end-to-end exercises and verify them in a development client and dedicated server.

## 1. The Content Pipeline

A visible, craftable block typically requires more than one Java declaration:

1. A registered `Block` definition with default behavior and physical properties.
2. A registered `BlockItem` if players should hold and place it.
3. A blockstate file mapping its state values to models.
4. A block model and texture.
5. A translation for its displayed name.
6. A loot table describing drops.
7. Optional tags and recipes that other game systems can consume.

A basic item usually needs a registered `Item`, an item model, texture, and translation; recipes and tags are optional. Registration makes the object known to the game. It does not create its art or data-pack behavior. Missing resource files are a resource problem, not a registry problem.

This split is intentional. Resource packs can override assets, data packs can override recipes and loot tables, and code provides behavior that cannot be expressed as data. Avoid hardcoding things into Java when vanilla already exposes a resource/data format for them.

## 2. A Minimal Registration Foundation

The examples below use `stoneworks` as the mod ID. It must match the mod metadata in the MDK. Java package names are illustrative; put the files under your own source package.

### Mod Entry Point And Register Wiring

```java
@Mod(Stoneworks.MOD_ID)
public final class Stoneworks {
    public static final String MOD_ID = "stoneworks";

    public Stoneworks(IEventBus modBus) {
        // Force block declarations to load before block-item declarations that reference them.
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
      ModDataComponents.COMPONENTS.register(modBus);
        ModCreativeTabs.TABS.register(modBus);

        // Datagen is a mod-bus event and is only relevant for the data run.
        modBus.addListener(ModDataGenerators::gatherData);
    }
}
```

Register each deferred register once on the mod event bus. The declaration fields do not immediately create registry entries; their factories run during the registry phase. Keep static initializers declarative: avoid looking up registry values, worlds, or client singletons there.

### Basic Items And ItemStack State

An `Item` is a canonical registered definition. An `ItemStack` is a mutable runtime quantity of that item, with a count and a component map. A stack can carry state that differs from another stack of the same item. Never store per-stack mutable state in a field on the shared `Item` singleton.

```java
public final class ModItems {
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(Stoneworks.MOD_ID);

    public static final DeferredItem<Item> RAW_COPPER = ITEMS.registerSimpleItem(
            "raw_copper",
            new Item.Properties().stacksTo(32).rarity(Rarity.UNCOMMON)
    );

    private ModItems() {}
}
```

`DeferredRegister.Items` offers `registerSimpleItem` for plain items and `registerItem` for custom item classes. The holder gives type-safe access to the eventual registered object. Use a supplier/holder rather than constructing a second `Item` with `new` elsewhere.

### Tools

Tools combine item identity with mining rules and player attributes. In 1.21.1, the standard `TieredItem` hierarchy provides familiar pickaxe, axe, shovel, hoe, and sword behavior. A `Tier` supplies shared durability, speed, attack bonus, enchantability, repair ingredient, and an incorrect-tool tag. A tool's `Item.Properties` supplies item-specific configuration and attributes.

```java
public static final DeferredItem<PickaxeItem> COPPER_PICKAXE = ITEMS.registerItem(
        "copper_pickaxe",
        properties -> new PickaxeItem(
                Tiers.IRON,
                properties.attributes(PickaxeItem.createAttributes(Tiers.IRON, 1.0F, -2.8F))
        ),
        new Item.Properties()
);
```

This example reuses the vanilla iron tier to demonstrate registration, not to claim that the resulting tool is copper-balanced. A real custom tier needs a correct incorrect-for-tool block tag and repair ingredient. The block tags govern whether mining drops are allowed; mining speed alone does not grant correct drops. In 1.21.1 tools also represent behavior through data components such as `minecraft:tool`, which is why custom tool designs should use the current component and item-ability APIs rather than old-version examples that hardcode everything in a legacy `Tier` abstraction.

The `createAttributes` overloads are part of the tool class API. Select attack damage and speed intentionally; arbitrary values can make a tool unbalanced. For a full custom set, use the 1.21.1 [tools and armor reference](https://docs.neoforged.net/docs/1.21.1/items/tools/) to create a `Tier`, its tags, and the five tool constructors.

### Armor

Armor shares a registered `ArmorMaterial` across its pieces. The material provides defense by slot, enchantability, equip sound, repair ingredient, texture layer, toughness, and knockback resistance. Each `ArmorItem` still has a specific slot type and a durability value. In 1.21.1 the material is represented by a registry holder.

```java
public static final DeferredItem<ArmorItem> COPPER_HELMET = ITEMS.registerItem(
        "copper_helmet",
        properties -> new ArmorItem(
                ArmorMaterials.IRON,
                ArmorItem.Type.HELMET,
                properties.durability(ArmorItem.Type.HELMET.getDurability(15))
        ),
        new Item.Properties()
);
```

Here `ArmorMaterials.IRON` is deliberately reused to keep the example small. Custom armor requires registering an `ArmorMaterial` and supplying the texture layer resource location (for example `stoneworks:copper`). Armor textures use the vanilla layer convention under `assets/stoneworks/textures/models/armor/`; the leg layer is distinct from the outer layer. Add all armor pieces and verify each slot, durability, repair behavior, texture, and dedicated-server loading.

### Data Components, Not Shared Mutable Fields

Minecraft 1.20.5+ replaced the old “put arbitrary NBT on an `ItemStack`” pattern with typed data components. Components make the meaning of stack data explicit and support codec-based persistence and network synchronization. Use built-in components when they express the data you need: durability (`DataComponents.DAMAGE`), custom names (`CUSTOM_NAME`), enchantments, tool behavior, food properties, and many others.

For example, the name belongs to an individual stack:

```java
ItemStack namedCopper = new ItemStack(ModItems.RAW_COPPER.get());
namedCopper.set(DataComponents.CUSTOM_NAME, Component.literal("Survey Sample"));
```

For mod-specific data, register a `DataComponentType<T>` with a persistence codec and, when needed, a network codec. Keep component values immutable; Java records are a good fit because they provide value equality and stable fields.

```java
public final class ModDataComponents {
  // Custom stack data is a registry entry and must be attached to the mod bus.
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Stoneworks.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> SAMPLE_QUALITY =
            COMPONENTS.registerComponentType(
                    "sample_quality",
                    builder -> builder
                            .persistent(Codec.INT)
                            .networkSynchronized(ByteBufCodecs.VAR_INT)
            );

    private ModDataComponents() {}
}
```

Register `ModDataComponents.COMPONENTS` on the mod bus. Then set/read it on stacks with `stack.set(ModDataComponents.SAMPLE_QUALITY.get(), 3)` and `stack.getOrDefault(ModDataComponents.SAMPLE_QUALITY.get(), 0)`. Validate ranges when values enter your system; a codec describes serialization, not gameplay validity. If the component is purely local presentation data, consider whether it should be synchronized. If it must persist, provide a persistent codec.

Do not mutate component objects in place and expect the stack to notice. Treat component values as immutable and call `set` or `update` with the replacement value.

## 3. Blocks And Block States

### Block Definition Versus Placed State

A `Block` is the canonical definition: its behavior and shared properties. A world location stores a `BlockState`, an immutable value selected from all legal states defined by that block. Even a block with no properties has one default state. This is analogous to an item definition and an `ItemStack`, but a block state is canonical and immutable rather than a mutable stack.

Use a property for a small finite state space such as facing, waterlogged, open, powered, or lit. Use a block entity for persistent, potentially large or unbounded state such as an inventory, text, or machine progress. Keep state counts under control: combining independent properties multiplies the number of possible states. A block with 4 directions, 2 power values, and 2 lit values has 16 states, even if some combinations are redundant.

### Registering A Block And Its Item Form

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

The block's properties affect mining time, resistance, sound, light, collision, and other behavior. Choose values intentionally and test them. `requiresCorrectToolForDrops()` is not cosmetic: it means the resource's loot depends on correct tool rules and tags. Do not assume that declaring a pickaxe alone makes every ore mineable.

Register an inventory representation separately:

```java
public static final DeferredItem<BlockItem> COPPER_ORE_ITEM = ITEMS.registerSimpleBlockItem(
        "copper_ore",
        ModBlocks.COPPER_ORE
);
```

This field belongs in `ModItems`, after `ModBlocks` has been initialized. A block without a `BlockItem` can still exist and be used by world generation or other code, but it will not be a normal inventory item.

### Defining A Block State

This example defines one Boolean state property and makes it affect emitted light. The block is a single registered object; `LIT` chooses one of its precomputed immutable states.

```java
public final class LuminousBlock extends Block {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public LuminousBlock(BlockBehaviour.Properties properties) {
        super(properties.lightLevel(state -> state.getValue(LIT) ? 15 : 0));
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    public InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hit
    ) {
        if (!level.isClientSide()) {
            level.setBlock(pos, state.cycle(LIT), Block.UPDATE_ALL);
        }

        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
```

Register it through `BLOCKS.registerBlock("luminous_block", LuminousBlock::new, properties)`, passing the properties factory parameter required by the helper. The exact overload can be checked in the 1.21.1 `DeferredRegister.Blocks` source. The `stateDefinition` must include every property before `registerDefaultState` uses it. `BlockState#setValue` and `cycle` return a state; they do not mutate the existing one. `Level#setBlock` is the operation that changes the world and sends updates according to its flags.

For real gameplay, consider permissions, client/server interaction results, neighbor updates, sound, particles, and whether interaction should consume an item. Never store a changing value such as “lit” in a field on `LuminousBlock`: one block object represents every placement in every world.

## 4. Creative Mode Tabs

The creative inventory is an item discovery UI, not the item registry. Registering an item does not automatically add it to a tab. For a tab owned by your mod, register a `CreativeModeTab`; for an existing tab, listen to `BuildCreativeModeTabContentsEvent` on the mod bus and filter by its tab key.

```java
public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Stoneworks.MOD_ID);

    public static final Supplier<CreativeModeTab> STONEWORKS = TABS.register(
            "stoneworks",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.stoneworks"))
                    .icon(() -> new ItemStack(ModItems.RAW_COPPER.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.RAW_COPPER.get());
                        output.accept(ModItems.COPPER_ORE_ITEM.get());
                        output.accept(ModItems.COPPER_PICKAXE.get());
                        output.accept(ModItems.COPPER_HELMET.get());
                    })
                    .build()
    );

    private ModCreativeTabs() {}
}
```

The display callback runs when tab contents are assembled; it should be quick and deterministic. Do not create new registry objects there. A `DeferredHolder` is safe to dereference after registry setup, and the supplier for the tab icon delays creating the stack until the tab needs it.

For an existing tab, an event listener may look like this:

```java
private static void buildExistingTab(BuildCreativeModeTabContentsEvent event) {
    if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
        event.accept(ModItems.RAW_COPPER);
    }
}
```

Register this handler on the mod bus. The event fires on the logical client. Use the holder directly when the event accepts an `ItemLike`/supplier; use `.get()` if the exact parameter requires the registered object.

## 5. JSON Resources And Data-Driven Content

### Assets And Data Are Different Inputs

- `assets/stoneworks/...` contains client resources: item/block models, textures, language files, sounds, and related presentation data.
- `data/stoneworks/...` contains data-pack resources: recipes, loot tables, tags, advancements, and other gameplay definitions.

The registry ID is the bridge between Java and JSON. For `stoneworks:copper_ore`, the corresponding block model path is `assets/stoneworks/models/block/copper_ore.json`, the blockstate file is `assets/stoneworks/blockstates/copper_ore.json`, and the block loot path is `data/stoneworks/loot_table/blocks/copper_ore.json`.

The 1.21.1 resource directories use singular names such as `recipe` and `loot_table`; copy paths from version-matched docs/resources rather than older tutorials. Java registration alone does not guarantee a model, translation, recipe, or drop.

### A Shaped Recipe

A simple recipe can be authored directly as JSON. This makes the data contract visible and independently replaceable by a data pack:

```json
{
  "type": "minecraft:crafting_shaped",
  "category": "misc",
  "pattern": [
    " C ",
    "CIC",
    " C "
  ],
  "key": {
    "C": { "item": "minecraft:copper_ingot" },
    "I": { "item": "minecraft:iron_ingot" }
  },
  "result": {
    "id": "stoneworks:survey_token",
    "count": 1
  }
}
```

Save this as `data/stoneworks/recipe/survey_token.json`. Recipes are loaded by the server's `RecipeManager`; the client receives recipe data for display, but recipe decisions and crafting are authoritative server behavior. A recipe advancement/unlock is normally generated alongside datagen output, though the recipe itself remains data-driven.

### Block Loot

A registered block's default loot-table ID is `<namespace>:blocks/<path>`. Explicitly supplying a loot table makes drops reviewable and data-pack replaceable. A basic self-drop table for the example block:

```json
{
  "type": "minecraft:block",
  "pools": [
    {
      "rolls": 1,
      "entries": [
        {
          "type": "minecraft:item",
          "name": "stoneworks:copper_ore"
        }
      ]
    }
  ]
}
```

Save as `data/stoneworks/loot_table/blocks/copper_ore.json`. For a mineable ore, vanilla's usual design also requires correct-tool checks and a loot condition such as `minecraft:match_tool`; consult the 1.21.1 loot table docs and compare a vanilla ore. Do not accidentally award the block item with an incorrect tool when the intended mechanic is raw ore drops.

### Models, Blockstates, And Language

A basic flat item model, at `assets/stoneworks/models/item/survey_token.json`:

```json
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "stoneworks:item/survey_token"
  }
}
```

The referenced PNG belongs at `assets/stoneworks/textures/item/survey_token.png`. Tools generally use `minecraft:item/handheld`; ordinary flat items use `minecraft:item/generated`.

For a plain cube block, create `assets/stoneworks/blockstates/copper_ore.json`:

```json
{
  "variants": {
    "": { "model": "stoneworks:block/copper_ore" }
  }
}
```

Then create `assets/stoneworks/models/block/copper_ore.json`:

```json
{
  "parent": "minecraft:block/cube_all",
  "textures": {
    "all": "stoneworks:block/copper_ore",
    "particle": "stoneworks:block/copper_ore"
  }
}
```

The block texture belongs at `assets/stoneworks/textures/block/copper_ore.png`. A placed block also needs an item model, commonly at `assets/stoneworks/models/item/copper_ore.json` with parent `stoneworks:block/copper_ore`.

Translations belong in `assets/stoneworks/lang/en_us.json`:

```json
{
  "item.stoneworks.raw_copper": "Raw Copper",
  "item.stoneworks.survey_token": "Survey Token",
  "item.stoneworks.copper_pickaxe": "Copper Pickaxe",
  "block.stoneworks.copper_ore": "Copper Ore",
  "itemGroup.stoneworks": "Stoneworks"
}
```

Use lowercase registry paths with stable names. Once a published ID exists, changing it can break references in worlds, recipes, and data packs. Translations are presentation keys, not registry IDs.

## 6. Datagen And Data Providers

Handwritten JSON is useful for learning and small examples. As content grows, it becomes repetitive and easy to desynchronize: an item gets renamed but its model or loot table does not. Datagen expresses these files from Java declarations and helper methods, reducing duplication and allowing provider validation.

NeoForge runs datagen through the `data` run configuration. During the run, `GatherDataEvent` supplies a `DataGenerator`, `PackOutput`, `ExistingFileHelper`, and registry lookup future. Providers should be registered according to whether they generate client assets or server data. Typical providers include blockstate/model, item model, language, recipe, loot table, and tag providers.

```java
public final class ModDataGenerators {
    private ModDataGenerators() {}

    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();
        CompletableFuture<HolderLookup.Provider> lookupProvider = event.getLookupProvider();
        ExistingFileHelper existingFileHelper = event.getExistingFileHelper();

        // Server data: recipes, loot tables, and tags.
        generator.addProvider(
                event.includeServer(),
                new StoneworksRecipeProvider(output, lookupProvider)
        );

        // Client assets: translations, item models, and blockstate/models.
        // Supply the appropriate NeoForge 1.21.1 provider constructors and helpers.
        // existingFileHelper is required by providers that validate model references.
    }
}
```

A recipe provider follows the 1.21.1 `RecipeProvider` contract:

```java
public final class StoneworksRecipeProvider extends RecipeProvider {
    public StoneworksRecipeProvider(
            PackOutput output,
            CompletableFuture<HolderLookup.Provider> lookupProvider
    ) {
        super(output, lookupProvider);
    }

    @Override
    protected void buildRecipes(RecipeOutput output) {
        // Add recipe builders here. Use the 1.21.1 RecipeProvider helpers,
        // and save each recipe through the provided RecipeOutput.
    }
}
```

`RecipeOutput` is important: it supports generated recipe output and advancement data, as well as NeoForge data conditions. Loot tables use `LootTableProvider` with the appropriate block/entity/subprovider; tags need the registry lookup provider so referenced entries are resolved. Provider APIs evolve, so copy constructor and helper signatures from your resolved 21.1.x source/docs, not a different release's tutorial.

Run the generated `data` configuration, inspect `src/generated/resources`, and add that directory to the resource source set if the MDK has not already configured it. Generated files may be committed, but the team should choose one source of truth: don't hand-edit generated output if it will be overwritten. Use generated JSON as a review artifact and run the generator after modifying its provider.

In this repository, `ModDataGenerators` registers providers for block states/models, item models, English translations, block loot, and recipes. These outputs belong in `src/generated/resources`; edit the Java providers rather than their generated JSON. The configured feature, placed feature, and biome modifier remain hand-authored under `src/main/resources/data/stoneworks` because they are intentionally taught as editable datapack JSON.

## Exercise 1: Survey Token Item

### Problem Statement

Register a `survey_token` item that stacks to 16, appears in a Stoneworks creative tab, has a translated name and a valid item model, and can be crafted from copper and iron in a shaped recipe. It must be available with `/give` and appear in the creative inventory. The task is complete when the client loads without missing-model/translation errors, the recipe crafts on a server, and the output stack is the registered item.

### Common Pitfalls

- Registering an `Item` directly outside the item registry or constructing another copy instead of using its deferred holder.
- Forgetting to register the tab, or assuming registry registration automatically adds the item to a creative tab.
- Using `assets` paths for recipe data, or using the older plural `recipes` directory for this 1.21.1 guide.
- Returning an item ID that does not match the Java registration name.
- Creating a model JSON that references a missing texture file.
- Keeping the recipe in an incorrect namespace or using stale output from a previous datagen run.

### Reference Solution

Register the item:

```java
public static final DeferredItem<Item> SURVEY_TOKEN = ITEMS.registerSimpleItem(
        "survey_token",
        new Item.Properties().stacksTo(16)
);
```

Add it to the tab's `displayItems` callback:

```java
output.accept(ModItems.SURVEY_TOKEN.get());
```

Create `data/stoneworks/recipe/survey_token.json`:

```json
{
  "type": "minecraft:crafting_shaped",
  "category": "misc",
  "pattern": [" C ", "CIC", " C "],
  "key": {
    "C": { "item": "minecraft:copper_ingot" },
    "I": { "item": "minecraft:iron_ingot" }
  },
  "result": { "id": "stoneworks:survey_token", "count": 1 }
}
```

Create `assets/stoneworks/models/item/survey_token.json`:

```json
{
  "parent": "minecraft:item/generated",
  "textures": { "layer0": "stoneworks:item/survey_token" }
}
```

Add `assets/stoneworks/textures/item/survey_token.png` (a 16x16 or higher-resolution square PNG) and add the translation entry:

```json
"item.stoneworks.survey_token": "Survey Token"
```

The translation entry belongs inside `assets/stoneworks/lang/en_us.json`. A complete check is: run datagen if this content is provider-generated, launch the client, check the creative tab and recipe book, craft on a server, and verify `/give stoneworks:survey_token` resolves. Also confirm that the stack limit is 16. The item's behavior does not need a custom Java subclass because this exercise adds no new interaction.

## Exercise 2: Luminous Toggle Block

### Problem Statement

Create a `luminous_block` that has a Boolean `LIT` block-state property, starts unlit, toggles when used without an item, and emits light level 15 only while lit. Give it a block item, a visible model/texture and translation, plus a self-drop loot table. Test multiple placements: toggling one block must not change another placement. The server must own the state change.

### Common Pitfalls

- Storing the lit value in a field on the `Block` singleton. That would make all placements share one value.
- Forgetting to add `LIT` to the state definition before registering a default state.
- Calling `setValue` without using the returned immutable state.
- Changing the state on both logical sides and producing desynchronization.
- Registering a block but not its `BlockItem`, blockstate/model files, translation, or loot table.
- Defining the state property in Java but omitting it from a custom blockstate model mapping.

### Reference Solution

Implement the block:

```java
public final class LuminousBlock extends Block {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public LuminousBlock(BlockBehaviour.Properties properties) {
        super(properties.lightLevel(state -> state.getValue(LIT) ? 15 : 0));
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    public InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hit
    ) {
        if (!level.isClientSide()) {
            level.setBlock(pos, state.cycle(LIT), Block.UPDATE_ALL);
        }

        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
```

Register the block in `ModBlocks`:

```java
public static final DeferredBlock<LuminousBlock> LUMINOUS_BLOCK = BLOCKS.registerBlock(
        "luminous_block",
        LuminousBlock::new,
        BlockBehaviour.Properties.of()
                .destroyTime(1.5F)
                .explosionResistance(6.0F)
                .sound(SoundType.STONE)
);
```

Register its item in `ModItems`:

```java
public static final DeferredItem<BlockItem> LUMINOUS_BLOCK_ITEM = ITEMS.registerSimpleBlockItem(
        "luminous_block",
        ModBlocks.LUMINOUS_BLOCK
);
```

Create `assets/stoneworks/blockstates/luminous_block.json`. Both states use the same model because the block's light is a gameplay/render property, not a shape change:

```json
{
  "variants": {
    "lit=false": { "model": "stoneworks:block/luminous_block" },
    "lit=true": { "model": "stoneworks:block/luminous_block" }
  }
}
```

Create `assets/stoneworks/models/block/luminous_block.json`:

```json
{
  "parent": "minecraft:block/cube_all",
  "textures": {
    "all": "stoneworks:block/luminous_block",
    "particle": "stoneworks:block/luminous_block"
  }
}
```

Create `assets/stoneworks/models/item/luminous_block.json`:

```json
{
  "parent": "stoneworks:block/luminous_block"
}
```

Add `assets/stoneworks/textures/block/luminous_block.png` and this entry in `assets/stoneworks/lang/en_us.json`:

```json
"block.stoneworks.luminous_block": "Luminous Block"
```

Create `data/stoneworks/loot_table/blocks/luminous_block.json`:

```json
{
  "type": "minecraft:block",
  "pools": [
    {
      "rolls": 1,
      "entries": [
        {
          "type": "minecraft:item",
          "name": "stoneworks:luminous_block"
        }
      ]
    }
  ]
}
```

Verify the state by placing two blocks, toggling one, and confirming only the clicked block changes and emits light. Break it with the intended tool and confirm it drops the block item. Test on a dedicated server and watch both client and server logs for registry or missing-resource warnings.

## Verification Checklist

- [ ] Mod metadata ID matches `Stoneworks.MOD_ID` and every `stoneworks:` resource namespace.
- [ ] All deferred registers are attached exactly once to the mod event bus.
- [ ] Blocks and block items are separate entries when a block should be obtainable.
- [ ] Registry holders are dereferenced only after registration.
- [ ] Gameplay mutations happen on the logical server; client-only references are isolated from dedicated-server classloading.
- [ ] Every registered item/block has the intended model and translation; blocks have blockstate mappings.
- [ ] Recipes and loot tables use the 1.21.1 resource directory names and valid JSON.
- [ ] Datagen output is regenerated and reviewed after provider changes.
- [ ] The feature is tested in an integrated client and a dedicated server.

## References

- [NeoForge 1.21.1 items](https://docs.neoforged.net/docs/1.21.1/items/)
- [NeoForge 1.21.1 data components](https://docs.neoforged.net/docs/1.21.1/items/datacomponents/)
- [NeoForge 1.21.1 tools and armor](https://docs.neoforged.net/docs/1.21.1/items/tools/)
- [NeoForge 1.21.1 blocks](https://docs.neoforged.net/docs/1.21.1/blocks/)
- [NeoForge 1.21.1 blockstates](https://docs.neoforged.net/docs/1.21.1/blocks/states/)
- [NeoForge 1.21.1 events](https://docs.neoforged.net/docs/1.21.1/concepts/events/)
- [NeoForge 1.21.1 resources and datagen](https://docs.neoforged.net/docs/1.21.1/resources/)
- [NeoForge 1.21.1 recipes](https://docs.neoforged.net/docs/1.21.1/resources/server/recipes/)
- [NeoForge 1.21.1 loot tables](https://docs.neoforged.net/docs/1.21.1/resources/server/loottables/)
