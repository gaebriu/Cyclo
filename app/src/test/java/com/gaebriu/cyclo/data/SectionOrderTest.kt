package com.gaebriu.cyclo.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** As ordens guardadas ganham as secções novas (a água, beta-3.0) logo a seguir à cafeína. */
class SectionOrderTest {
    @Test
    fun defaultOrderHasWaterBelowCaffeine() {
        assertEquals(listOf(FeedSection.Caffeine, FeedSection.Water, FeedSection.SleepFactors, FeedSection.Feeling), DefaultFeedOrder)
        assertEquals(
            listOf(TrackingSection.Restfulness, TrackingSection.Caffeine, TrackingSection.Water, TrackingSection.Mood),
            DefaultTrackingOrder,
        )
    }

    @Test
    fun savedOrderGetsWaterRightAfterCaffeine() {
        assertEquals(
            listOf(FeedSection.Feeling, FeedSection.Caffeine, FeedSection.Water, FeedSection.SleepFactors),
            normalizeFeedOrder(listOf(FeedSection.Feeling, FeedSection.Caffeine, FeedSection.SleepFactors)),
        )
        assertEquals(
            listOf(TrackingSection.Mood, TrackingSection.Restfulness, TrackingSection.Caffeine, TrackingSection.Water),
            normalizeTrackingOrder(listOf(TrackingSection.Mood, TrackingSection.Restfulness, TrackingSection.Caffeine)),
        )
        assertEquals(
            listOf(TrackingSection.Caffeine, TrackingSection.Water, TrackingSection.Mood, TrackingSection.Restfulness),
            normalizeTrackingOrder(listOf(TrackingSection.Caffeine, TrackingSection.Mood, TrackingSection.Restfulness)),
        )
    }

    @Test
    fun orderWithWaterStaysAsIs() {
        val moved = listOf(FeedSection.Water, FeedSection.Feeling, FeedSection.Caffeine, FeedSection.SleepFactors)
        assertEquals(moved, normalizeFeedOrder(moved))
    }

    @Test
    fun missingAndRepeatedSectionsAreCompleted() {
        assertEquals(DefaultFeedOrder, normalizeFeedOrder(emptyList()))
        assertEquals(
            // A cafeína não tem nenhuma antes dela: vai para o início, e a água logo a seguir.
            listOf(FeedSection.Caffeine, FeedSection.Water, FeedSection.SleepFactors, FeedSection.Feeling),
            completeOrder(listOf(FeedSection.SleepFactors, FeedSection.SleepFactors, FeedSection.Feeling), DefaultFeedOrder),
        )
    }
}
