package com.example.stoneworks.data;

import com.mojang.serialization.Codec;

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