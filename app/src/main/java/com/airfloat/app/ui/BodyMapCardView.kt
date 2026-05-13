package com.airfloat.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Matrix
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.airfloat.app.R
import java.util.Locale

class BodyMapCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val warmMutedTextColor = 0xFFC9C0B3.toInt()
    private val warmQuietTextColor = 0xFFD9C8B5.toInt()
    private val noDataToneColor = 0xFF9F9283.toInt()
    private val expandedCardsColumnWidthPx = dp(92f).toInt()
    private val collapsedVisualWidthPx = dp(160f).toInt()
    private val compactCardHorizontalPaddingPx = dp(8f).toInt()
    private val compactCardVerticalPaddingPx = dp(10f).toInt()
    private val regularCardPaddingPx = dp(10f).toInt()

    private data class ZoneCardBinding(
        val container: View,
        val name: TextView,
        val score: TextView,
        val progressTrack: View,
        val progressFill: View,
        val lastSeen: TextView
    )

    interface BodyMapCardListener {
        fun onExerciseSelected(presetKey: String)
    }

    private val bodyMapTopSplit: LinearLayout
    private val bodyMapPrimaryVisualFrame: FrameLayout
    private val bodyMapCardsColumn: LinearLayout
    private val bodyMapView: BodyMapView
    private val bodyMapExercisePreviewImage: ImageView
    private val detailSheet: FrameLayout
    private val detailContent: View
    private val detailTitle: TextView
    private val detailBadge: TextView
    private val detailMeta: TextView
    private val detailHint: TextView
    private val detailActionButton: Button
    private val exercisesContainer: LinearLayout
    private val cardBindings = linkedMapOf<MuscleZone, ZoneCardBinding>()
    private var zoneModels: List<MuscleZoneModel> = emptyList()
    private var selectedZone: MuscleZone? = null
    private var previewActivated = false
    private var detailSheetAnimator: ValueAnimator? = null
    private var splitAnimator: ValueAnimator? = null
    private var listener: BodyMapCardListener? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.view_body_map_card, this, true)
        bodyMapTopSplit = findViewById(R.id.bodyMapTopSplit)
        bodyMapPrimaryVisualFrame = findViewById(R.id.bodyMapPrimaryVisualFrame)
        bodyMapCardsColumn = findViewById(R.id.bodyMapCardsColumn)
        bodyMapView = findViewById(R.id.bodyMapView)
        bodyMapExercisePreviewImage = findViewById(R.id.bodyMapExercisePreviewImage)
        bodyMapExercisePreviewImage.scaleType = ImageView.ScaleType.MATRIX
        detailSheet = findViewById(R.id.bodyMapDetailSheet)
        detailContent = findViewById(R.id.bodyMapDetailContent)
        detailTitle = findViewById(R.id.bodyMapDetailTitle)
        detailBadge = findViewById(R.id.bodyMapDetailBadge)
        detailMeta = findViewById(R.id.bodyMapDetailMeta)
        detailHint = findViewById(R.id.bodyMapDetailHint)
        detailActionButton = findViewById(R.id.bodyMapDetailActionButton)
        exercisesContainer = findViewById(R.id.bodyMapExercisesContainer)
        cardBindings[MuscleZone.CHEST] = bindCard(findViewById(R.id.bodyMapZoneCardChest))
        cardBindings[MuscleZone.CORE] = bindCard(findViewById(R.id.bodyMapZoneCardCore))
        cardBindings[MuscleZone.ARMS] = bindCard(findViewById(R.id.bodyMapZoneCardArms))
        cardBindings[MuscleZone.LEGS] = bindCard(findViewById(R.id.bodyMapZoneCardLegs))

        bodyMapView.setBodyMapListener(object : BodyMapView.BodyMapListener {
            override fun onZoneSelected(zone: MuscleZone) {
                setSelectedZone(zone, revealDetail = true)
            }
        })

        cardBindings.forEach { (zone, binding) ->
            binding.container.setOnClickListener {
                setSelectedZone(zone, revealDetail = true)
            }
        }

        bodyMapTopSplit.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (right - left != oldRight - oldLeft && selectedZone != null) {
                renderLayoutMode(shouldExpandForZone(selectedZone), animate = false)
            }
        }
    }

    fun setBodyMapCardListener(listener: BodyMapCardListener?) {
        this.listener = listener
    }

    fun setZones(zones: List<MuscleZoneModel>) {
        zoneModels = zones
        bodyMapView.setZoneData(zones)
        val modelsByZone = zones.associateBy { it.zone }
        cardBindings.forEach { (zone, binding) ->
            val model = modelsByZone[zone]
            if (model == null) {
                binding.container.visibility = View.GONE
            } else {
                binding.container.visibility = View.VISIBLE
                bindCard(
                    binding = binding,
                    model = model,
                    selected = zone == selectedZone,
                    compact = shouldExpandForZone(selectedZone)
                )
            }
        }
        if (selectedZone == null) {
            previewActivated = true
            preferredInitialZone(zones)?.let { zone ->
                setSelectedZone(zone, animateBody = false, revealDetail = false)
            }
        } else {
            renderPrimaryVisual(selectedZone, allowPreview = previewActivated)
            renderLayoutMode(shouldExpandForZone(selectedZone), animate = false)
        }
    }

    private fun setSelectedZone(
        zone: MuscleZone,
        animateBody: Boolean = true,
        revealDetail: Boolean
    ) {
        selectedZone = zone
        if (revealDetail && ExerciseArtwork.forZone(zone) != null) {
            previewActivated = true
        }
        val expanded = shouldExpandForZone(zone)
        bodyMapView.setSelectedZone(zone, animate = animateBody)
        val modelsByZone = zoneModels.associateBy { it.zone }
        cardBindings.forEach { (muscleZone, binding) ->
            modelsByZone[muscleZone]?.let {
                bindCard(
                    binding = binding,
                    model = it,
                    selected = muscleZone == zone,
                    compact = expanded
                )
            }
        }
        renderPrimaryVisual(zone, allowPreview = previewActivated)
        renderLayoutMode(expanded, animate = revealDetail)
        renderDetailSheet(modelsByZone[zone], animate = revealDetail)
    }

    private fun renderPrimaryVisual(
        zone: MuscleZone?,
        allowPreview: Boolean
    ) {
        val previewRes =
            if (allowPreview && zone != null) {
                ExerciseArtwork.forZone(zone)
            } else {
                null
            }
        if (previewRes == null) {
            bodyMapView.visibility = View.VISIBLE
            bodyMapExercisePreviewImage.visibility = View.GONE
            bodyMapExercisePreviewImage.setImageDrawable(null)
            return
        }
        bodyMapView.visibility = View.GONE
        bodyMapExercisePreviewImage.visibility = View.VISIBLE
        bodyMapExercisePreviewImage.setImageResource(previewRes)
        bodyMapExercisePreviewImage.post {
            applyPreviewTransform(zone)
        }
    }

    private fun applyPreviewTransform(zone: MuscleZone?) {
        val drawable = bodyMapExercisePreviewImage.drawable ?: return
        val viewWidth = bodyMapExercisePreviewImage.width
        val viewHeight = bodyMapExercisePreviewImage.height
        val drawableWidth = drawable.intrinsicWidth
        val drawableHeight = drawable.intrinsicHeight
        if (viewWidth <= 0 || viewHeight <= 0 || drawableWidth <= 0 || drawableHeight <= 0) return

        val baseScale =
            maxOf(
                viewWidth / drawableWidth.toFloat(),
                viewHeight / drawableHeight.toFloat()
            )
        val boostedScale = baseScale * previewScaleMultiplier(zone)
        val scaledWidth = drawableWidth * boostedScale
        val scaledHeight = drawableHeight * boostedScale
        val centeredTranslateX = (viewWidth - scaledWidth) / 2f
        val centeredTranslateY = (viewHeight - scaledHeight) / 2f
        val focusPoint = previewFocusPoint(zone)
        val translateX =
            if (focusPoint == null) {
                centeredTranslateX
            } else {
                val focusFractionX = ((focusPoint.first + 10f) / 20f).coerceIn(0f, 1f)
                val targetTranslateX = (viewWidth / 2f) - (drawableWidth * focusFractionX * boostedScale)
                targetTranslateX.coerceIn(viewWidth - scaledWidth, 0f)
            }
        val translateY =
            if (focusPoint == null) {
                centeredTranslateY
            } else {
                val focusFractionY = ((10f - focusPoint.second) / 20f).coerceIn(0f, 1f)
                val targetTranslateY = (viewHeight / 2f) - (drawableHeight * focusFractionY * boostedScale)
                targetTranslateY.coerceIn(viewHeight - scaledHeight, 0f)
            }

        bodyMapExercisePreviewImage.imageMatrix =
            Matrix().apply {
                postScale(boostedScale, boostedScale)
                postTranslate(translateX, translateY)
            }
    }

    private fun bindCard(
        binding: ZoneCardBinding,
        model: MuscleZoneModel,
        selected: Boolean,
        compact: Boolean
    ) {
        val hasData = hasZoneData(model)
        val zoneColor = if (hasData) zoneColor(model.precision) else noDataToneColor
        binding.name.text = zoneLabel(model.zone)
        binding.score.text = if (hasData) model.rank.name else "—"
        binding.name.setTextColor(if (hasData) 0xFFF0EDE8.toInt() else warmQuietTextColor)
        binding.lastSeen.text = model.lastSeenLabel
        binding.lastSeen.setTextColor(if (hasData) warmMutedTextColor else withAlphaFraction(warmMutedTextColor, 0.72f))
        binding.score.setTextColor(zoneColor)
        binding.progressFill.backgroundTintList = ColorStateList.valueOf(zoneColor)
        binding.progressTrack.backgroundTintList = ColorStateList.valueOf(0xFF1A1A1A.toInt())

        binding.progressTrack.post {
            val params = binding.progressFill.layoutParams
            params.width =
                if (hasData) {
                    ((model.precision.coerceIn(0, 100) / 100f) * binding.progressTrack.width).toInt().coerceAtLeast(1)
                } else {
                    0
                }
            binding.progressFill.layoutParams = params
        }

        val background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(10f)
            setColor(if (selected) tintedColorFor(zoneColor, hasData) else 0xFF111111.toInt())
            if (selected) {
                setStroke(dp(1f).toInt(), if (hasData) zoneColor else 0xFF2F2F2F.toInt())
            }
        }
        binding.container.background = background
        applyCardMode(binding, compact)
    }

    private fun bindCard(container: View): ZoneCardBinding =
        ZoneCardBinding(
            container = container,
            name = container.findViewById(R.id.bodyMapZoneName),
            score = container.findViewById(R.id.bodyMapZoneScore),
            progressTrack = container.findViewById(R.id.bodyMapZoneProgressTrack),
            progressFill = container.findViewById(R.id.bodyMapZoneProgressFill),
            lastSeen = container.findViewById(R.id.bodyMapZoneLastSeen)
        )

    private fun renderDetailSheet(
        model: MuscleZoneModel?,
        animate: Boolean
    ) {
        if (model == null) {
            collapseDetailSheet(animate)
            return
        }

        val hasData = hasZoneData(model)
        val zoneColor = if (hasData) zoneColor(model.precision) else noDataToneColor
        detailTitle.text = zoneLabel(model.zone)
        detailTitle.setTextColor(zoneColor)
        detailBadge.text =
            if (hasData) {
                model.rank.name
            } else {
                "UNMAPPED"
            }
        detailBadge.setTextColor(badgeTextColor(zoneColor))
        detailBadge.backgroundTintList = ColorStateList.valueOf(withAlpha(zoneColor, 0x33))
        detailMeta.text = detailMetaFor(model)
        detailMeta.setTextColor(if (hasData) warmMutedTextColor else warmQuietTextColor)
        detailHint.text = detailHintFor(model)
        detailHint.setTextColor(
            when {
                model.missReason != null && model.rank == FormRank.RAW -> 0xFFE24B4A.toInt()
                model.missReason != null && model.rank == FormRank.FORMING -> 0xFFEF9F27.toInt()
                else -> warmMutedTextColor
            }
        )
        detailHint.visibility = if (detailHint.text.isNullOrBlank()) View.GONE else View.VISIBLE
        renderActionButton(model, zoneColor)

        exercisesContainer.removeAllViews()
        model.exercises.forEach { exercise ->
            val row =
                LayoutInflater.from(context).inflate(
                    R.layout.view_body_map_exercise_row,
                    exercisesContainer,
                    false
                )
            row.findViewById<View>(R.id.bodyMapExerciseDot).backgroundTintList =
                ColorStateList.valueOf(zoneColor)
            row.findViewById<TextView>(R.id.bodyMapExerciseName).text = exercise.name
            row.findViewById<TextView>(R.id.bodyMapExerciseAction).apply {
                text = "OPEN"
                setTextColor(warmMutedTextColor)
                backgroundTintList = ColorStateList.valueOf(withAlpha(zoneColor, 0x1A))
            }
            row.setOnClickListener { listener?.onExerciseSelected(exercise.presetKey) }
            exercisesContainer.addView(row)
        }

        expandDetailSheet(animate)
    }

    private fun renderLayoutMode(
        expanded: Boolean,
        animate: Boolean
    ) {
        if (bodyMapTopSplit.width == 0) {
            bodyMapTopSplit.post { renderLayoutMode(expanded, animate = false) }
            return
        }

        splitAnimator?.cancel()

        val totalWidth = bodyMapTopSplit.width.coerceAtLeast(collapsedVisualWidthPx + expandedCardsColumnWidthPx)
        val visualMarginEnd = (bodyMapPrimaryVisualFrame.layoutParams as MarginLayoutParams).marginEnd
        val targetVisualWidth =
            if (expanded) {
                (totalWidth - expandedCardsColumnWidthPx - visualMarginEnd).coerceAtLeast(collapsedVisualWidthPx)
            } else {
                collapsedVisualWidthPx
            }
        val targetCardsWidth = (totalWidth - visualMarginEnd - targetVisualWidth).coerceAtLeast(0)

        val startVisualWidth =
            bodyMapPrimaryVisualFrame.width
                .takeIf { it > 0 }
                ?: bodyMapPrimaryVisualFrame.layoutParams.width
                    .takeIf { it > 0 }
                ?: collapsedVisualWidthPx
        val startCardsWidth =
            bodyMapCardsColumn.width
                .takeIf { it > 0 }
                ?: bodyMapCardsColumn.layoutParams.width
                    .takeIf { it > 0 }
                ?: (totalWidth - visualMarginEnd - startVisualWidth).coerceAtLeast(0)

        applySplitWidths(startVisualWidth, startCardsWidth)
        if (!animate) {
            applySplitWidths(targetVisualWidth, targetCardsWidth)
            return
        }

        splitAnimator =
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 240L
                interpolator = android.view.animation.DecelerateInterpolator()
                addUpdateListener { animator ->
                    val progress = animator.animatedValue as Float
                    val visualWidth = lerp(startVisualWidth, targetVisualWidth, progress)
                    val cardsWidth = lerp(startCardsWidth, targetCardsWidth, progress)
                    applySplitWidths(visualWidth, cardsWidth)
                }
                start()
            }
    }

    private fun applySplitWidths(
        visualWidth: Int,
        cardsWidth: Int
    ) {
        bodyMapPrimaryVisualFrame.layoutParams =
            bodyMapPrimaryVisualFrame.layoutParams.apply {
                width = visualWidth
            }
        bodyMapCardsColumn.layoutParams =
            (bodyMapCardsColumn.layoutParams as LinearLayout.LayoutParams).apply {
                width = cardsWidth
                weight = 0f
            }
    }

    private fun applyCardMode(
        binding: ZoneCardBinding,
        compact: Boolean
    ) {
        val container = binding.container as LinearLayout
        val headerRow = binding.name.parent as LinearLayout
        val nameParams = binding.name.layoutParams as LinearLayout.LayoutParams

        if (compact) {
            container.gravity = Gravity.CENTER
            container.setPadding(
                compactCardHorizontalPaddingPx,
                compactCardVerticalPaddingPx,
                compactCardHorizontalPaddingPx,
                compactCardVerticalPaddingPx
            )
            headerRow.gravity = Gravity.CENTER
            binding.score.visibility = View.GONE
            binding.progressTrack.visibility = View.GONE
            binding.lastSeen.visibility = View.GONE
            nameParams.width = LinearLayout.LayoutParams.WRAP_CONTENT
            nameParams.weight = 0f
            binding.name.layoutParams = nameParams
            binding.name.gravity = Gravity.CENTER
            binding.name.textAlignment = View.TEXT_ALIGNMENT_CENTER
        } else {
            container.gravity = Gravity.NO_GRAVITY
            container.setPadding(
                regularCardPaddingPx,
                regularCardPaddingPx,
                regularCardPaddingPx,
                regularCardPaddingPx
            )
            headerRow.gravity = Gravity.CENTER_VERTICAL
            binding.score.visibility = View.VISIBLE
            binding.progressTrack.visibility = View.VISIBLE
            binding.lastSeen.visibility = View.VISIBLE
            nameParams.width = 0
            nameParams.weight = 1f
            binding.name.layoutParams = nameParams
            binding.name.gravity = Gravity.START
            binding.name.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        }
    }

    private fun expandDetailSheet(animate: Boolean) {
        val targetHeight = measuredDetailHeight()
        if (targetHeight <= 0) return
        detailSheetAnimator?.cancel()
        val startHeight = detailSheet.layoutParams.height.coerceAtLeast(0)
        if (!animate) {
            detailSheet.layoutParams = detailSheet.layoutParams.apply { height = targetHeight }
            detailSheet.alpha = 1f
            return
        }
        detailSheetAnimator =
            android.animation.ValueAnimator.ofInt(startHeight, targetHeight).apply {
                duration = 220L
                interpolator = android.view.animation.DecelerateInterpolator()
                addUpdateListener {
                    val height = it.animatedValue as Int
                    detailSheet.layoutParams = detailSheet.layoutParams.apply { this.height = height }
                    detailSheet.alpha =
                        if (targetHeight == 0) {
                            0f
                        } else {
                            height / targetHeight.toFloat()
                        }
                }
                start()
            }
    }

    private fun collapseDetailSheet(animate: Boolean) {
        detailSheetAnimator?.cancel()
        if (!animate) {
            detailSheet.layoutParams = detailSheet.layoutParams.apply { height = 0 }
            detailSheet.alpha = 0f
            return
        }
        val startHeight = detailSheet.height
        detailSheetAnimator =
            android.animation.ValueAnimator.ofInt(startHeight, 0).apply {
                duration = 220L
                interpolator = android.view.animation.DecelerateInterpolator()
                addUpdateListener {
                    val height = it.animatedValue as Int
                    detailSheet.layoutParams = detailSheet.layoutParams.apply { this.height = height }
                    detailSheet.alpha = if (startHeight == 0) 0f else height / startHeight.toFloat()
                }
                start()
            }
    }

    private fun measuredDetailHeight(): Int {
        val width = (measuredWidth - paddingLeft - paddingRight).coerceAtLeast(1)
        val widthSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)
        val heightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        detailContent.measure(widthSpec, heightSpec)
        return detailContent.measuredHeight
    }

    private fun detailHintFor(model: MuscleZoneModel): String =
        when {
            !hasZoneData(model) -> "No tracked reps for this zone yet. Start with the lane below."
            model.missReason != null && model.rank == FormRank.RAW -> model.missReason
            model.missReason != null && model.rank == FormRank.FORMING -> model.missReason
            else -> ""
        }

    private fun detailMetaFor(model: MuscleZoneModel): String =
        when {
            !hasZoneData(model) -> "ZONE NOT MAPPED YET"
            else -> "${model.exercises.size} EXERCISES · ${model.lastSeenLabel}"
        }

    private fun renderActionButton(
        model: MuscleZoneModel,
        zoneColor: Int
    ) {
        val primaryExercise = primaryExercise(model)
        val buttonLabel =
            when {
                primaryExercise == null -> "NO EXERCISE AVAILABLE"
                hasZoneData(model) -> "LAUNCH ${primaryExercise.name.uppercase(Locale.US)}"
                else -> "START ${primaryExercise.name.uppercase(Locale.US)}"
            }
        detailActionButton.text = buttonLabel
        detailActionButton.backgroundTintList = ColorStateList.valueOf(zoneColor)
        detailActionButton.setTextColor(buttonTextColor(zoneColor))
        detailActionButton.isEnabled = primaryExercise != null
        detailActionButton.alpha = if (primaryExercise != null) 1f else 0.5f
        detailActionButton.setOnClickListener {
            primaryExercise?.let { exercise ->
                listener?.onExerciseSelected(exercise.presetKey)
            }
        }
    }

    private fun primaryExercise(model: MuscleZoneModel): ZoneExercise? =
        model.exercises.maxWithOrNull(
            compareBy<ZoneExercise> { it.precision }
                .thenBy { it.name }
        ) ?: model.exercises.firstOrNull()

    private fun preferredInitialZone(zones: List<MuscleZoneModel>): MuscleZone? =
        zones.firstOrNull { it.zone == MuscleZone.CHEST }?.zone
            ?: zones.firstOrNull { ExerciseArtwork.forZone(it.zone) != null }?.zone
            ?: zones.firstOrNull()?.zone

    private fun hasZoneData(model: MuscleZoneModel): Boolean =
        model.lastSeenLabel != "—" || model.exercises.any { it.precision > 0 }

    private fun zoneLabel(zone: MuscleZone): String =
        when (zone) {
            MuscleZone.CHEST -> "CHEST"
            MuscleZone.CORE -> "CORE"
            MuscleZone.ARMS -> "SHOULDERS"
            MuscleZone.LEGS -> "LEGS"
        }

    private fun zoneColor(precision: Int): Int =
        when (precision) {
            in 89..100 -> 0xFFE8FF47.toInt()
            in 76..88 -> 0xFF1D9E75.toInt()
            in 61..75 -> 0xFFEF9F27.toInt()
            else -> 0xFFE24B4A.toInt()
        }

    private fun tintedColorFor(
        zoneColor: Int,
        hasData: Boolean
    ): Int =
        when {
            !hasData -> 0xFF171717.toInt()
            zoneColor == 0xFF1D9E75.toInt() -> 0xFF0D1F1A.toInt()
            zoneColor == 0xFFEF9F27.toInt() -> 0xFF24180A.toInt()
            zoneColor == 0xFFE24B4A.toInt() -> 0xFF241111.toInt()
            else -> 0xFF25280A.toInt()
        }

    private fun badgeTextColor(zoneColor: Int): Int =
        when (zoneColor) {
            0xFFE8FF47.toInt() -> 0xFF0A0A0B.toInt()
            0xFF1D9E75.toInt() -> 0xFF04342C.toInt()
            0xFFEF9F27.toInt() -> 0xFF412402.toInt()
            0xFFE24B4A.toInt() -> 0xFF3E1010.toInt()
            else -> 0xFFE0E0E0.toInt()
        }

    private fun buttonTextColor(zoneColor: Int): Int =
        when (zoneColor) {
            0xFFE8FF47.toInt() -> 0xFF0A0A0B.toInt()
            0xFF1D9E75.toInt() -> 0xFF0A0A0B.toInt()
            0xFFEF9F27.toInt() -> 0xFF0A0A0B.toInt()
            0xFFE24B4A.toInt() -> 0xFFFFFFFF.toInt()
            else -> 0xFF0A0A0B.toInt()
        }

    private fun withAlpha(
        color: Int,
        alpha: Int
    ): Int = (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    private fun withAlphaFraction(
        color: Int,
        alphaFraction: Float
    ): Int = withAlpha(color, (255 * alphaFraction).toInt())

    private fun shouldExpandForZone(zone: MuscleZone?): Boolean =
        previewActivated && zone != null && ExerciseArtwork.forZone(zone) != null

    private fun previewScaleMultiplier(zone: MuscleZone?): Float =
        when (zone) {
            MuscleZone.CHEST -> 1.5f
            MuscleZone.CORE -> 1.6f
            MuscleZone.ARMS -> 1.95f
            MuscleZone.LEGS,
            null -> 1f
        }

    private fun previewFocusPoint(zone: MuscleZone?): Pair<Float, Float>? =
        when (zone) {
            MuscleZone.CHEST -> -3.5f to 2.2f
            MuscleZone.CORE -> -6f to 1.75f
            MuscleZone.ARMS -> -4.75f to 2.4f
            else -> null
        }

    private fun lerp(
        start: Int,
        end: Int,
        progress: Float
    ): Int = (start + ((end - start) * progress)).toInt()

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
