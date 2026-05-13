package com.airfloat.app.stats

data class ProgressSectionRenderPlan(
    val key: ProgressSectionKey,
    val renderMode: SectionRenderMode,
    val summary: String
)

object ProgressSectionPlanner {
    fun buildPlans(
        rankedSections: List<RankedProgressSection>,
        summaries: Map<ProgressSectionKey, String>,
        expandedSections: Set<ProgressSectionKey>
    ): List<ProgressSectionRenderPlan> {
        return rankedSections
            .filter { it.key != ProgressSectionKey.DEEP_DIVE_SHEET }
            .map { ranked ->
                val mode =
                    if (ranked.key in expandedSections && ranked.renderMode == SectionRenderMode.COLLAPSED) {
                        SectionRenderMode.FULL
                    } else {
                        ranked.renderMode
                    }
                ProgressSectionRenderPlan(
                    key = ranked.key,
                    renderMode = mode,
                    summary = summaries[ranked.key].orEmpty()
                )
            }
    }
}
