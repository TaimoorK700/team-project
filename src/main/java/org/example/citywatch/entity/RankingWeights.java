package org.example.citywatch.entity;

import java.util.*;
import java.time.*;

/** Immutable shared value used by CityWatch. */
public final class RankingWeights {
    private final double proximity;
    private final double recency;
    private final double category;
    private final double severity;

    public RankingWeights(double proximity, double recency, double category, double severity) {
        double[] weights = {proximity, recency, category, severity};
        double sum = 0;
        for (double weight : weights) {
            if (!Double.isFinite(weight) || weight < 0) throw new IllegalArgumentException("Invalid weight");
            sum += weight;
        }
        if (!Double.isFinite(sum) || sum <= 0) throw new IllegalArgumentException("Positive total required");
        this.proximity = proximity;
        this.recency = recency;
        this.category = category;
        this.severity = severity;
    }

    public double getProximity() { return proximity; }

    public double getRecency() { return recency; }

    public double getCategory() { return category; }

    public double getSeverity() { return severity; }

}
