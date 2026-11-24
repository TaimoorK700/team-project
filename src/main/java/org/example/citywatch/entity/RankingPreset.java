package org.example.citywatch.entity;

public enum RankingPreset {
    RECOMMENDED(
            "Recommended",
            "Balance distance, recency, preferred types, and reported severity.",
            UserPreferences.defaults().getRankingWeights()),

    NEARBY_FIRST(
            "Prioritize nearby reports",
            "Give more importance to reports closer to your saved locations.",
            new RankingWeights(0.70, 0.10, 0.15, 0.05)),

    RECENT_FIRST(
            "Prioritize recent reports",
            "Give more importance to reports with recent source timestamps.",
            new RankingWeights(0.20, 0.60, 0.15, 0.05));

    private final String label;
    private final String description;
    private final RankingWeights weights;

    RankingPreset(
            String label,
            String description,
            RankingWeights weights) {
        this.label = label;
        this.description = description;
        this.weights = weights;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public RankingWeights getWeights() {
        return weights;
    }

    public boolean matches(RankingWeights candidate) {
        return Double.compare(
                weights.getProximity(), candidate.getProximity()) == 0
            && Double.compare(
                    weights.getRecency(), candidate.getRecency()) == 0
            && Double.compare(
                    weights.getCategory(), candidate.getCategory()) == 0
            && Double.compare(
                    weights.getSeverity(), candidate.getSeverity()) == 0;
    }
}
