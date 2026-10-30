package com.example.stoneworks.world.block.entity;

import com.example.stoneworks.Stoneworks;
import com.example.stoneworks.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

@GameTestHolder(Stoneworks.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StoneworksGameTests {
    private StoneworksGameTests() {}

    public static void register(RegisterGameTestsEvent event) {
        event.register(StoneworksGameTests.class);
    }

    @GameTest(template = "empty3x3x3", templateNamespace = Stoneworks.MOD_ID, timeoutTicks = 20)
    public static void sampleCrateCapsAndPersists(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.SAMPLE_CRATE.get());

        SampleCrateBlockEntity crate = helper.getBlockEntity(pos);
        for (int index = 0; index < 64; index++) {
            helper.assertTrue(crate.addSample(), "crate should accept samples up to its cap");
        }
        helper.assertFalse(crate.addSample(), "crate must reject samples beyond 64");
        helper.assertTrue(crate.samples() == 64, "crate count should be 64");

        CompoundTag saved = new CompoundTag();
        crate.saveAdditional(saved, helper.getLevel().registryAccess());

        SampleCrateBlockEntity restored = new SampleCrateBlockEntity(pos, crate.getBlockState());
        restored.loadAdditional(saved, helper.getLevel().registryAccess());
        helper.assertTrue(restored.samples() == 64, "saved count should restore");
        helper.succeed();
    }

    @GameTest(template = "empty3x3x3", templateNamespace = Stoneworks.MOD_ID, timeoutTicks = 20)
    public static void relayClampsAndPersistsSurveyScore(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.SURVEY_RELAY.get());

        SurveyRelayBlockEntity relay = helper.getBlockEntity(pos);
        relay.applyCalculatedResult(120);
        helper.assertTrue(relay.lastSurveyScore() == 100, "score should clamp to 100");

        relay.applyCalculatedResult(-5);
        helper.assertTrue(relay.lastSurveyScore() == 0, "score should clamp to zero");

        relay.applyCalculatedResult(42);
        CompoundTag saved = new CompoundTag();
        relay.saveAdditional(saved, helper.getLevel().registryAccess());

        SurveyRelayBlockEntity restored = new SurveyRelayBlockEntity(pos, relay.getBlockState());
        restored.loadAdditional(saved, helper.getLevel().registryAccess());
        helper.assertTrue(restored.lastSurveyScore() == 42, "saved score should restore");
        helper.succeed();
    }
}