package com.airfloat.app.stats

enum class ProgressSectionKey(private val rawValue: String) {
    LATEST_SESSION_MAP("latest_session_map"),
    ADAPTIVE_COACH("adaptive_coach"),
    EXERCISE_LENS("exercise_lens"),
    SUMMARY_METRICS("summary_metrics"),
    CONSISTENCY_HEATMAP("consistency_heatmap"),
    OUTPUT_TREND("output_trend"),
    BODY_METRICS("body_metrics"),
    RECENT_SESSIONS("recent_sessions"),
    DEEP_DIVE_SHEET("deep_dive_sheet");

    override fun toString(): String = rawValue
}

enum class SectionRenderMode {
    FULL,
    COLLAPSED,
    ON_DEMAND
}

data class RankedProgressSection(
    val key: ProgressSectionKey,
    val priority: Int,
    val renderMode: SectionRenderMode,
    val reason: String
)

object ProgressSectionRanker {
    private const val COLLAPSE_THRESHOLD = 6

    private data class MutableRankState(
        val key: ProgressSectionKey,
        val basePriority: Int,
        var priority: Int,
        var signalWeight: Int,
        val reasons: MutableList<String> = mutableListOf()
    )

    fun rankSections(snapshot: StateSnapshot): List<RankedProgressSection> {
        val sections =
            listOf(
                MutableRankState(ProgressSectionKey.LATEST_SESSION_MAP, 9, 9, 0, mutableListOf("latest performance trace")),
                MutableRankState(ProgressSectionKey.ADAPTIVE_COACH, 8, 8, 0, mutableListOf("next-step guidance")),
                MutableRankState(ProgressSectionKey.EXERCISE_LENS, 7, 7, 0, mutableListOf("movement-specific lens")),
                MutableRankState(ProgressSectionKey.SUMMARY_METRICS, 6, 6, 0, mutableListOf("window context")),
                MutableRankState(ProgressSectionKey.CONSISTENCY_HEATMAP, 5, 5, 0, mutableListOf("attendance rhythm")),
                MutableRankState(ProgressSectionKey.OUTPUT_TREND, 4, 4, 0, mutableListOf("load direction")),
                MutableRankState(ProgressSectionKey.BODY_METRICS, 3, 3, 0, mutableListOf("body response")),
                MutableRankState(ProgressSectionKey.RECENT_SESSIONS, 2, 2, 0, mutableListOf("session archive")),
                MutableRankState(ProgressSectionKey.DEEP_DIVE_SHEET, 1, 1, 0, mutableListOf("on-demand reward"))
            ).associateBy { it.key }

        if (snapshot.streakRisk) {
            sections.getValue(ProgressSectionKey.CONSISTENCY_HEATMAP).apply {
                priority = 10
                signalWeight = 400
                reasons += "streak risk override"
            }
        }
        if (snapshot.hasNewPR) {
            sections.getValue(ProgressSectionKey.EXERCISE_LENS).apply {
                priority = 10
                signalWeight = 300
                reasons += "new PR pinned"
            }
        }
        if (snapshot.lastSessionQuality == LastSessionQuality.POOR) {
            sections.getValue(ProgressSectionKey.LATEST_SESSION_MAP).apply {
                priority = 10
                signalWeight = 200
                reasons += "poor session recovery read"
            }
        }
        if (snapshot.bodyRecompSignal == BodyRecompSignal.ACTIVE) {
            sections.getValue(ProgressSectionKey.BODY_METRICS).apply {
                priority = maxOf(priority, 9)
                signalWeight = 100
                reasons += "body recomposition active"
            }
        }
        if (snapshot.daysSinceLastCheckIn > 7) {
            sections.getValue(ProgressSectionKey.BODY_METRICS).apply {
                priority = maxOf(priority, 8)
                signalWeight = maxOf(signalWeight, 140)
                reasons += "check-in stale"
            }
        }
        if (snapshot.loadTrend == LoadTrend.PLATEAU) {
            sections.getValue(ProgressSectionKey.OUTPUT_TREND).apply {
                priority = maxOf(priority, 8)
                signalWeight = 120
                reasons += "plateau annotation"
            }
        }

        return sections.values
            .sortedWith(
                compareByDescending<MutableRankState> { it.priority }
                    .thenByDescending { it.signalWeight }
                    .thenByDescending { it.basePriority }
            )
            .map { state ->
                RankedProgressSection(
                    key = state.key,
                    priority = state.priority,
                    renderMode =
                        when {
                            state.key == ProgressSectionKey.DEEP_DIVE_SHEET -> SectionRenderMode.ON_DEMAND
                            state.priority < COLLAPSE_THRESHOLD -> SectionRenderMode.COLLAPSED
                            else -> SectionRenderMode.FULL
                        },
                    reason = state.reasons.joinToString(" • ")
                )
            }
    }
}
