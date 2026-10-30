package com.example.stoneworks.performance;

import com.example.stoneworks.registry.ModBlocks;
import com.example.stoneworks.world.block.entity.SurveyRelayBlockEntity;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

public final class PerformanceExamples {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_RADIUS = 8;
    private static final int MAX_MEASUREMENTS = 4_096;

    private PerformanceExamples() {}

    public record SurveySnapshot(ResourceKey<Level> dimension, BlockPos pos, List<Integer> measurements) {
        public SurveySnapshot {
            pos = pos.immutable();
            measurements = List.copyOf(measurements);
            if (measurements.size() > MAX_MEASUREMENTS) {
                throw new IllegalArgumentException("Too many survey measurements");
            }
        }
    }

    public record SurveyResult(int score) {}

    public static int countCopperOreInLoadedRadius(ServerLevel level, BlockPos center, int radius) {
        int boundedRadius = Mth.clamp(radius, 0, MAX_RADIUS);
        int minimumY = Math.max(level.getMinBuildHeight(), center.getY() - boundedRadius);
        int maximumY = Math.min(level.getMaxBuildHeight() - 1, center.getY() + boundedRadius);
        int count = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int x = center.getX() - boundedRadius; x <= center.getX() + boundedRadius; x++) {
            for (int z = center.getZ() - boundedRadius; z <= center.getZ() + boundedRadius; z++) {
                if (!level.getChunkSource().hasChunk(x >> 4, z >> 4)) {
                    continue;
                }

                for (int y = minimumY; y <= maximumY; y++) {
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    if (state.is(ModBlocks.COPPER_ORE.get()) || state.is(ModBlocks.DEEPSLATE_COPPER_ORE.get())) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    public static void scheduleSurvey(
            MinecraftServer server,
            Executor boundedWorker,
            SurveySnapshot snapshot
    ) {
        try {
            CompletableFuture.supplyAsync(() -> calculate(snapshot), boundedWorker)
                    .whenComplete((result, failure) -> {
                        try {
                            server.execute(() -> {
                                if (failure != null) {
                                    LOGGER.error("Survey calculation failed", failure);
                                    return;
                                }

                                ServerLevel currentLevel = server.getLevel(snapshot.dimension());
                                if (currentLevel == null
                                        || !currentLevel.getChunkSource().hasChunk(
                                                snapshot.pos().getX() >> 4, snapshot.pos().getZ() >> 4)) {
                                    return;
                                }

                                if (currentLevel.getBlockEntity(snapshot.pos())
                                        instanceof SurveyRelayBlockEntity relay) {
                                    relay.applyCalculatedResult(result.score());
                                }
                            });
                        } catch (RejectedExecutionException rejected) {
                            LOGGER.debug("Discarding survey result after server shutdown", rejected);
                        }
                    });
        } catch (RejectedExecutionException rejected) {
            LOGGER.debug("Survey worker queue is full; rejecting request", rejected);
        }
    }

    public static void scheduleSurvey(MinecraftServer server, SurveySnapshot snapshot) {
        scheduleSurvey(server, ServerWorkerPool.executorFor(server), snapshot);
    }

    private static SurveyResult calculate(SurveySnapshot snapshot) {
        if (snapshot.measurements().isEmpty()) {
            return new SurveyResult(0);
        }

        long total = 0L;
        for (int measurement : snapshot.measurements()) {
            total += Mth.clamp(measurement, 0, 100);
        }
        return new SurveyResult((int) (total / snapshot.measurements().size()));
    }
}