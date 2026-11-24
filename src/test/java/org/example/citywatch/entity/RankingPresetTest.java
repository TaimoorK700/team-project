package org.example.citywatch.entity;

import java.util.Arrays;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.*;

class RankingPresetTest {
    @Test
    void recommendedUsesSharedDefaults() {
        RankingWeights expected = UserPreferences.defaults().getRankingWeights();
        RankingWeights actual = RankingPreset.RECOMMENDED.getWeights();

        assertAll(
                () -> assertEquals(expected.getProximity(), actual.getProximity()),
                () -> assertEquals(expected.getRecency(), actual.getRecency()),
                () -> assertEquals(expected.getCategory(), actual.getCategory()),
                () -> assertEquals(expected.getSeverity(), actual.getSeverity()));
    }

    @ParameterizedTest
    @EnumSource(RankingPreset.class)
    void matchesWeightsByValueRatherThanObjectIdentity(RankingPreset preset) {
        RankingWeights original = preset.getWeights();
        RankingWeights restored = new RankingWeights(
                original.getProximity(), original.getRecency(),
                original.getCategory(), original.getSeverity());

        assertNotSame(original, restored);
        assertTrue(preset.matches(restored));
        for (RankingPreset other : RankingPreset.values()) {
            if (other != preset) {
                assertFalse(other.matches(restored),
                        other + " must not match " + preset);
            }
        }
    }

    @ParameterizedTest(name = "{0}: changing weight {1} stays custom")
    @MethodSource("slightlyChangedWeights")
    void rejectsEvenTheSmallestChangeToEachWeight(
            RankingPreset preset, int changedIndex, RankingWeights changed) {
        assertFalse(preset.matches(changed),
                "Changing weight " + changedIndex + " must not match " + preset);
    }

    static Stream<Arguments> slightlyChangedWeights() {
        return Arrays.stream(RankingPreset.values()).flatMap(preset ->
                IntStream.range(0, 4).mapToObj(index -> {
                    RankingWeights original = preset.getWeights();
                    double[] values = {
                            original.getProximity(), original.getRecency(),
                            original.getCategory(), original.getSeverity()
                    };
                    values[index] = Math.nextUp(values[index]);
                    return Arguments.of(preset, index, new RankingWeights(
                            values[0], values[1], values[2], values[3]));
                }));
    }

    @ParameterizedTest
    @MethodSource("customWeights")
    void doesNotClassifyExistingCustomWeightsAsAPreset(RankingWeights custom) {
        for (RankingPreset preset : RankingPreset.values()) {
            assertFalse(preset.matches(custom),
                    "Existing custom values must not be replaced by " + preset);
        }
    }

    static Stream<RankingWeights> customWeights() {
        return Stream.of(
                new RankingWeights(0.6, 0.2, 0.15, 0.05),
                new RankingWeights(5, 3, 1.5, 0.5));
    }

    @Test
    void nearbyFirstIncreasesTheRelativeInfluenceOfProximity() {
        RankingWeights recommended = RankingPreset.RECOMMENDED.getWeights();
        RankingWeights nearby = RankingPreset.NEARBY_FIRST.getWeights();

        assertTrue(nearby.getProximity() / total(nearby)
                > recommended.getProximity() / total(recommended));
    }

    @Test
    void recentFirstIncreasesTheRelativeInfluenceOfRecency() {
        RankingWeights recommended = RankingPreset.RECOMMENDED.getWeights();
        RankingWeights recent = RankingPreset.RECENT_FIRST.getWeights();

        assertTrue(recent.getRecency() / total(recent)
                > recommended.getRecency() / total(recommended));
    }

    private static double total(RankingWeights weights) {
        return weights.getProximity() + weights.getRecency()
                + weights.getCategory() + weights.getSeverity();
    }
}
