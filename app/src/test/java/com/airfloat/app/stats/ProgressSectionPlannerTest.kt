package com.airfloat.app.stats

import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressSectionPlannerTest {
    @Test
    fun expands_low_priority_sections_on_demand() {
        val ranked =
            listOf(
                RankedProgressSection(ProgressSectionKey.LATEST_SESSION_MAP, 10, SectionRenderMode.FULL, "latest"),
                RankedProgressSection(ProgressSectionKey.OUTPUT_TREND, 4, SectionRenderMode.COLLAPSED, "plateau"),
                RankedProgressSection(ProgressSectionKey.RECENT_SESSIONS, 2, SectionRenderMode.COLLAPSED, "archive"),
                RankedProgressSection(ProgressSectionKey.DEEP_DIVE_SHEET, 1, SectionRenderMode.ON_DEMAND, "sheet")
            )
        val summaries =
            mapOf(
                ProgressSectionKey.LATEST_SESSION_MAP to "Push-up • 78% • miss on attempt 5",
                ProgressSectionKey.OUTPUT_TREND to "+3% load • plateau",
                ProgressSectionKey.RECENT_SESSIONS to "5 recent sessions"
            )

        val defaultPlan = ProgressSectionPlanner.buildPlans(ranked, summaries, expandedSections = emptySet())
        val expandedPlan =
            ProgressSectionPlanner.buildPlans(
                ranked,
                summaries,
                expandedSections = setOf(ProgressSectionKey.OUTPUT_TREND)
            )

        println("SECTION_PLAN_DEFAULT=$defaultPlan")
        println("SECTION_PLAN_EXPANDED=$expandedPlan")

        assertEquals(3, defaultPlan.size)
        assertEquals(SectionRenderMode.COLLAPSED, defaultPlan[1].renderMode)
        assertEquals(SectionRenderMode.FULL, expandedPlan[1].renderMode)
    }
}
