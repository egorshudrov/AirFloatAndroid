package com.airfloat.app.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.airfloat.app.R

class ProgressLoadVelocityHeroView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    private val contentContainer: LinearLayout
    private val rangeLabel: TextView
    private val stateLabel: TextView
    private val metricLabel: TextView
    private val deltaValue: TextView
    private val title: TextView
    private val subtitle: TextView
    private val precisionValue: TextView
    private val precisionDelta: TextView
    private val cadenceValue: TextView
    private val cadenceDelta: TextView
    private val avgRepsValue: TextView
    private val avgRepsDelta: TextView
    private val pulseOverlay: View
    private val shockwave: View

    init {
        LayoutInflater.from(context).inflate(R.layout.view_progress_load_velocity_hero, this, true)
        contentContainer = findViewById(R.id.progressHeroContent)
        rangeLabel = findViewById(R.id.progressHeroRangeLabel)
        stateLabel = findViewById(R.id.progressHeroStateLabel)
        metricLabel = findViewById(R.id.progressHeroMetricLabel)
        deltaValue = findViewById(R.id.progressHeroLoadValue)
        title = findViewById(R.id.progressHeroTitle)
        subtitle = findViewById(R.id.progressHeroSubtitle)
        precisionValue = findViewById(R.id.progressHeroPrecisionValue)
        precisionDelta = findViewById(R.id.progressHeroPrecisionDelta)
        cadenceValue = findViewById(R.id.progressHeroCadenceValue)
        cadenceDelta = findViewById(R.id.progressHeroCadenceDelta)
        avgRepsValue = findViewById(R.id.progressHeroAvgRepsValue)
        avgRepsDelta = findViewById(R.id.progressHeroAvgRepsDelta)
        pulseOverlay = findViewById(R.id.progressHeroPulseOverlay)
        shockwave = findViewById(R.id.progressHeroShockwave)
    }

    fun bind(model: LoadVelocityHeroModel) {
        setBackgroundResource(backgroundRes(model.tone))
        rangeLabel.text = model.rangeLabel
        stateLabel.text = model.stateLabel
        stateLabel.setBackgroundResource(statePillRes(model.tone))
        metricLabel.text = "LOAD VELOCITY"
        animateHeroText(deltaValue, model.deltaValue)
        deltaValue.setTextColor(color(mainToneColor(model.tone)))
        animateHeroText(title, model.title)
        title.setTextColor(color(R.color.hud_text_primary))
        subtitle.text = model.subtitle
        subtitle.visibility = if (model.subtitle.isBlank()) View.GONE else View.VISIBLE

        bindMetric(
            valueView = precisionValue,
            deltaView = precisionDelta,
            metric = model.precisionMetric
        )
        bindMetric(
            valueView = cadenceValue,
            deltaView = cadenceDelta,
            metric = model.cadenceMetric
        )
        bindMetric(
            valueView = avgRepsValue,
            deltaView = avgRepsDelta,
            metric = model.avgRepsMetric
        )
    }

    private fun bindMetric(
        valueView: TextView,
        deltaView: TextView,
        metric: LoadVelocityMetricModel
    ) {
        valueView.text = metric.value
        deltaView.text = metric.delta
        val colorRes = mainToneColor(metric.tone)
        valueView.setTextColor(color(colorRes))
        deltaView.setTextColor(color(colorRes))
    }

    private fun animateHeroText(
        textView: TextView,
        newText: String
    ) {
        if (textView.text == newText) return
        textView.animate().cancel()
        textView.animate()
            .rotationX(-84f)
            .alpha(0f)
            .translationY(-textView.resources.displayMetrics.density * 10f)
            .setDuration(90L)
            .withEndAction {
                textView.text = newText
                textView.rotationX = 84f
                textView.translationY = textView.resources.displayMetrics.density * 10f
                textView.animate()
                    .rotationX(0f)
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(240L)
                    .start()
            }
            .start()
    }

    fun setScrollProgress(progress: Float) {
        val clamped = progress.coerceIn(0f, 1f)
        contentContainer.translationY = -dp(18f) * clamped
        contentContainer.scaleX = 1f - (0.028f * clamped)
        contentContainer.scaleY = 1f - (0.028f * clamped)
        contentContainer.alpha = 1f - (0.16f * clamped)
    }

    fun playPrMoment() {
        pulseOverlay.animate().cancel()
        shockwave.animate().cancel()
        deltaValue.animate().cancel()
        title.animate().cancel()

        pulseOverlay.alpha = 0.32f
        pulseOverlay.scaleX = 0.64f
        pulseOverlay.scaleY = 0.64f
        pulseOverlay.animate()
            .alpha(0f)
            .scaleX(1.22f)
            .scaleY(1.22f)
            .setDuration(640L)
            .start()

        shockwave.alpha = 0.92f
        shockwave.scaleX = 0.92f
        shockwave.scaleY = 0.92f
        shockwave.animate()
            .alpha(0f)
            .scaleX(1.44f)
            .scaleY(1.44f)
            .setDuration(300L)
            .start()

        slamText(deltaValue, distanceDp = 16f)
        slamText(title, distanceDp = 10f)
    }

    private fun slamText(
        textView: TextView,
        distanceDp: Float
    ) {
        textView.scaleX = 0.9f
        textView.scaleY = 0.9f
        textView.translationY = dp(distanceDp)
        textView.alpha = 0.82f
        textView.animate()
            .scaleX(1.08f)
            .scaleY(1.08f)
            .translationY(0f)
            .alpha(1f)
            .setDuration(160L)
            .withEndAction {
                textView.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(220L)
                    .start()
            }
            .start()
    }

    private fun backgroundRes(tone: LoadVelocityHeroTone): Int =
        when (tone) {
            LoadVelocityHeroTone.RISING -> R.drawable.progress_hero_shell_rising
            LoadVelocityHeroTone.FALLING -> R.drawable.progress_hero_shell_falling
            LoadVelocityHeroTone.PLATEAU -> R.drawable.progress_hero_shell_plateau
        }

    private fun statePillRes(tone: LoadVelocityHeroTone): Int =
        when (tone) {
            LoadVelocityHeroTone.RISING -> R.drawable.progress_hero_state_rising
            LoadVelocityHeroTone.FALLING -> R.drawable.progress_hero_state_falling
            LoadVelocityHeroTone.PLATEAU -> R.drawable.progress_hero_state_plateau
        }

    private fun mainToneColor(tone: LoadVelocityHeroTone): Int =
        when (tone) {
            LoadVelocityHeroTone.RISING -> R.color.success_400
            LoadVelocityHeroTone.FALLING -> R.color.danger_400
            LoadVelocityHeroTone.PLATEAU -> R.color.amber_score
        }

    private fun color(resId: Int): Int = ContextCompat.getColor(context, resId)

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
