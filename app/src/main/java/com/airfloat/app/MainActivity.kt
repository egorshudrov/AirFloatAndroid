package com.airfloat.app

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.PointF
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.util.Rational
import android.util.Size
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import com.airfloat.app.pose.ConditionCode
import com.airfloat.app.pose.OverlayView
import com.airfloat.app.pose.PoseDetector
import com.airfloat.app.pose.RepRejectReason
import com.airfloat.app.pushup.PushupCounter
import com.airfloat.app.pushup.PushupCounterResult
import com.airfloat.app.pushup.PushupQualityLabel
import com.airfloat.app.pushup.PushupV1BinaryClassifier
import com.airfloat.app.record.ScreenRecordService
import com.airfloat.app.situp.SitupCounter
import com.airfloat.app.situp.SitupCounterResult
import com.airfloat.app.situp.SitupQualityLabel
import com.airfloat.app.situp.SitupV1BinaryClassifier
import com.airfloat.app.squat.SquatCounter
import com.airfloat.app.squat.SquatCounterResult
import com.airfloat.app.squat.SquatQualityLabel
import com.airfloat.app.squat.SquatV1BinaryClassifier
import com.airfloat.app.stats.FirstLaunchRepository
import com.airfloat.app.ui.HomeFragment
import com.airfloat.app.ui.FirstLaunchFragment
import com.airfloat.app.ui.ProgressFragment
import com.airfloat.app.ui.TodaySurfaceFactory
import com.airfloat.app.ui.TodayUiState
import com.airfloat.app.ui.WorkoutFragment
import com.airfloat.app.ui.WorkoutSurfaceFactory
import com.airfloat.app.ui.charts.LiveFormScoreRingView
import com.airfloat.app.stats.AppState
import com.airfloat.app.stats.AppStateCalculator
import com.airfloat.app.stats.AppTimeContext
import com.airfloat.app.stats.ProgressTodayWriteBack
import com.airfloat.app.stats.SessionStatsCalculator
import com.airfloat.app.stats.WorkoutSessionAttemptRecord
import com.airfloat.app.stats.SessionStatsRepository
import com.airfloat.app.stats.WorkoutSessionRecord
import java.time.DayOfWeek
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

class MainActivity : AppCompatActivity(), WorkoutFragment.Host, HomeFragment.Host, FirstLaunchFragment.Host {
    // Temporary product decision: hide/disable AI quality panels for all exercises.
    private val qualityUiEnabled = false

    private lateinit var previewView: PreviewView
    private lateinit var overlayView: OverlayView
    private lateinit var prestartPanel: FrameLayout
    private lateinit var workoutContentContainer: FragmentContainerView
    private lateinit var rootContentContainer: FrameLayout
    private lateinit var progressContentContainer: FrameLayout
    private lateinit var firstLaunchContentContainer: FrameLayout
    private lateinit var rootSwipeSnapshotOverlay: ImageView
    private lateinit var finishButton: Button
    private lateinit var benchButton: Button
    private lateinit var skeletonButton: Button
    private lateinit var countdownText: TextView
    private lateinit var repFlashText: TextView
    private lateinit var conditionCard: View
    private lateinit var conditionDot: View
    private lateinit var activeBanner: TextView
    private lateinit var conditionHintText: TextView
    private lateinit var squatShadowCard: View
    private lateinit var squatShadowTitleText: TextView
    private lateinit var squatShadowClassText: TextView
    private lateinit var squatShadowConfidenceText: TextView
    private lateinit var repGaugePanel: View
    private lateinit var repGaugeTrack: View
    private lateinit var repGaugeMarker: View
    private lateinit var repGaugeTitleText: TextView
    private lateinit var repGaugeHintText: TextView
    private lateinit var repTotalsText: TextView
    private lateinit var repTablePanel: View
    private lateinit var repTableTitle: TextView
    private lateinit var repTableScroll: NestedScrollView
    private lateinit var repTableRows: LinearLayout
    private lateinit var hudTopScrim: View
    private lateinit var hudBottomScrim: View
    private lateinit var liveCleanFlashOverlay: View
    private lateinit var liveMissFlashOverlay: View
    private lateinit var livePerfectFlashOverlay: View
    private lateinit var liveHeaderCard: View
    private lateinit var liveHeaderBadgeText: TextView
    private lateinit var liveHeaderTitleText: TextView
    private lateinit var liveHeaderMetaText: TextView
    private lateinit var liveFeedbackCard: View
    private lateinit var liveFeedbackText: TextView
    private lateinit var liveFeedbackMetaText: TextView
    private lateinit var liveScorePanel: View
    private lateinit var liveScoreRing: LiveFormScoreRingView
    private lateinit var liveScoreStateText: TextView
    private lateinit var liveRepCounterCluster: View
    private lateinit var liveRepCounterLabelText: TextView
    private lateinit var liveRepCounterText: TextView
    private lateinit var bottomNavBar: View
    private lateinit var bottomNavButtonsRow: LinearLayout
    private lateinit var navMagmaGlow: View
    private lateinit var navHomeButton: TextView
    private lateinit var navWorkoutButton: TextView
    private lateinit var navProgressButton: TextView
    private lateinit var successOverlay: FrameLayout
    private lateinit var successPanel: View
    private lateinit var successText: TextView
    private lateinit var successMetaText: TextView

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var poseDetector: PoseDetector
    private var cameraProviderBound: ProcessCameraProvider? = null
    private var cameraStarted = false
    private var pendingStartAfterPermission = false
    private var countdown: CountDownTimer? = null
    private var goalReps: Int = 0
    private var goalReached = false
    private var lastReps = 0
    private var recordEnabled = false
    private var pendingStartRecording = false
    private var screenCaptureRequested = false
    private var mediaProjectionManager: MediaProjectionManager? = null
    private var projectionData: Intent? = null
    private var projectionResultCode: Int? = null
    private var benchActive = false
    private var benchTimer: CountDownTimer? = null
    private var benchFrames = 0
    private var benchLatencySum = 0f
    private var benchStartMs = 0L
    private var skeletonEnabled = true
    private var squatShadowEnabled = false
    private var trainingPreset = TrainingPreset.PRESS_BARBELL
    private var exerciseMode = ExerciseMode.PRESS
    private var movementProfile = MovementProfile.NO_WEIGHT
    private val alertInsetPx by lazy { 12f * resources.displayMetrics.density }
    private var shownCondition: ConditionCode? = null
    private var pendingCondition: ConditionCode? = null
    private var pendingConditionFrames = 0
    private var lastReasonEvent: ConditionCode? = null
    private var lastReasonEventAtMs = 0L
    private val nonCriticalConditionFrames = 4
    private val okConditionFrames = 8
    private val reasonEventHoldMs = 2200L
    private var lastRepRejectReason: RepRejectReason? = null
    private var lastRepRejectAtMs = 0L
    private val repRejectHoldMs = 2200L
    private var repAttemptCounter = 0
    private var successAttempts = 0
    private var failedAttempts = 0
    private var sessionStartRealtimeMs = 0L
    private var sessionStartWallClockMs = 0L
    private var sessionPersisted = false
    private var lastPersistedSessionRecord: WorkoutSessionRecord? = null
    private val sessionAttemptTimeline = mutableListOf<WorkoutSessionAttemptRecord>()
    private var liveDisplayedScore = 0
    private var liveFeedbackFillColor = 0xD6151A1E.toInt()
    private var liveFeedbackStrokeColor = 0x33FFFFFF
    private var liveFeedbackAnimator: ValueAnimator? = null
    private var liveMockSequenceRunning = false
    private var lastFailedLogReason: RepRejectReason? = null
    private var lastFailedLogRepSnapshot = -1
    private var lastFailedLogAtMs = 0L
    private val failedLogCooldownMs = 300L
    private val defaultBodyWeightKg = 75f
    private val defaultSpotifyPlaylistUri = "spotify:playlist:37i9dQZF1DX70RN3TfWWJh"
    private var squatShadowClassifier: SquatV1BinaryClassifier? = null
    private var squatCounter = SquatCounter()
    private var lastSquatCounterResult: SquatCounterResult? = null
    private var lastSquatShadowQuality: SquatQualityLabel? = null
    private var lastSquatShadowLogAtMs = 0L
    private var pushupClassifier: PushupV1BinaryClassifier? = null
    private var pushupCounter = PushupCounter()
    private var lastPushupCounterResult: PushupCounterResult? = null
    private var lastPushupQuality: PushupQualityLabel? = null
    private var lastPushupLogAtMs = 0L
    private var situpClassifier: SitupV1BinaryClassifier? = null
    private var situpCounter = SitupCounter()
    private var lastSitupCounterResult: SitupCounterResult? = null
    private var lastSitupQuality: SitupQualityLabel? = null
    private var lastSitupLogAtMs = 0L
    private val squatShadowLogIntervalMs = 1200L
    private var homeFragment: HomeFragment? = null
    private var workoutFragment: WorkoutFragment? = null
    private var progressFragment: ProgressFragment? = null
    private var firstLaunchFragment: FirstLaunchFragment? = null
    private var currentRootTab = RootTab.HOME
    private var firstLaunchActive = false
    private var rootSwipeDownX = 0f
    private var rootSwipeDownY = 0f
    private var rootSwipeDownAtMs = 0L
    private var rootSwipeBlocked = false
    private var rootSwipeActive = false
    private var rootSwipeDirection = RootMotionDirection.NONE
    private var rootSwipeTargetTab: RootTab? = null
    private var rootSwipeCurrentPanel: View? = null
    private var rootSwipeTargetPanel: View? = null
    private var rootSwipeCurrentContentLayer: View? = null
    private var rootSwipeTargetContentLayer: View? = null
    private var rootSwipeSnapshotBitmap: Bitmap? = null
    private var rootSwipeWidth = 0f
    private var navMagmaFlowAnimator: ValueAnimator? = null
    private var todayLaunchPresetKey: String? = null
    private var lastProgressReadAtMs = 0L
    private var progressTodayWriteBack: ProgressTodayWriteBack? = null
    private val surfaceEase = DecelerateInterpolator(1.7f)
    private val springEase = OvershootInterpolator(0.9f)
    private val magmaEase = PathInterpolator(0.22f, 1f, 0.36f, 1f)
    private val bannerColorNeutral = 0xFFFFD766.toInt()
    private val bannerColorTitle = 0xFFFFF3B0.toInt()
    private val bannerColorOk = 0xFF7EFF93.toInt()
    private val bannerColorWarn = 0xFFFFC247.toInt()
    private val bannerColorBad = 0xFFFF667D.toInt()
    private val sessionStatsRepository by lazy { SessionStatsRepository(this) }
    private val firstLaunchRepository by lazy { FirstLaunchRepository(this) }
    private val hideLiveFeedback = Runnable {
        if (!::liveFeedbackCard.isInitialized) return@Runnable
        liveFeedbackCard.animate().cancel()
        liveFeedbackCard.animate()
            .alpha(0f)
            .translationY((-12f).dpPx())
            .setDuration(180L)
            .setInterpolator(surfaceEase)
            .withEndAction {
                liveFeedbackCard.visibility = View.GONE
                liveFeedbackCard.translationY = 0f
            }
            .start()
    }
    private val liveHeaderTicker =
        object : Runnable {
            override fun run() {
                if (!isLiveSessionActive()) return
                updateLiveSessionChrome()
                liveHeaderMetaText.postDelayed(this, 1000L)
            }
        }

    private val screenCaptureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            handleScreenCaptureResult(result.resultCode, result.data)
        }

    private val isFrontCamera = true

    private enum class MovementProfile {
        NO_WEIGHT,
        DUMBBELL_SIM
    }

    private enum class ExerciseMode {
        PRESS,
        SQUAT_BETA,
        PUSHUP,
        SITUP
    }

    private enum class TrainingPreset {
        PRESS_BARBELL,
        PRESS_DUMBBELL,
        SQUAT_BETA,
        PUSHUP,
        SITUP
    }

    private enum class RootTab {
        HOME,
        WORKOUT,
        PROGRESS
    }

    private enum class RootMotionDirection {
        BACKWARD,
        FORWARD,
        NONE
    }

    private enum class LiveFeedbackTone {
        NEUTRAL,
        CLEAN,
        MISS,
        PERFECT
    }

    private data class MotionProfileConfig(
        val down: Float,
        val up: Float,
        val gaugeBottom: Float,
        val holdFrames: Int,
        val syncWindow: Int,
        val emaAlpha: Float,
        val minRepBottomAngle: Float,
        val minUnderTopTravel: Float,
        val underTopTopSlack: Float,
        val minRepDurationMs: Long,
        val minRangeSymmetryRatio: Float
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        overlayView = findViewById(R.id.overlayView)
        prestartPanel = findViewById(R.id.prestartPanel)
        workoutContentContainer = findViewById(R.id.workoutContentContainer)
        rootContentContainer = findViewById(R.id.rootContentContainer)
        progressContentContainer = findViewById(R.id.progressContentContainer)
        firstLaunchContentContainer = findViewById(R.id.firstLaunchContentContainer)
        rootSwipeSnapshotOverlay = findViewById(R.id.rootSwipeSnapshotOverlay)
        finishButton = findViewById(R.id.finishButton)
        benchButton = findViewById(R.id.benchButton)
        skeletonButton = findViewById(R.id.skeletonButton)
        countdownText = findViewById(R.id.countdownText)
        repFlashText = findViewById(R.id.repFlashText)
        conditionCard = findViewById(R.id.conditionCard)
        conditionDot = findViewById(R.id.conditionDot)
        activeBanner = findViewById(R.id.activeBanner)
        conditionHintText = findViewById(R.id.conditionHintText)
        squatShadowCard = findViewById(R.id.squatShadowCard)
        squatShadowTitleText = findViewById(R.id.squatShadowTitleText)
        squatShadowClassText = findViewById(R.id.squatShadowClassText)
        squatShadowConfidenceText = findViewById(R.id.squatShadowConfidenceText)
        repGaugePanel = findViewById(R.id.repGaugePanel)
        repGaugeTrack = findViewById(R.id.repGaugeTrack)
        repGaugeMarker = findViewById(R.id.repGaugeMarker)
        repGaugeTitleText = findViewById(R.id.repGaugeTitleText)
        repGaugeHintText = findViewById(R.id.repGaugeHintText)
        repTotalsText = findViewById(R.id.repTotalsText)
        repTablePanel = findViewById(R.id.repTablePanel)
        repTableTitle = findViewById(R.id.repTableTitle)
        repTableScroll = findViewById(R.id.repTableScroll)
        repTableRows = findViewById(R.id.repTableRows)
        hudTopScrim = findViewById(R.id.hudTopScrim)
        hudBottomScrim = findViewById(R.id.hudBottomScrim)
        liveCleanFlashOverlay = findViewById(R.id.liveCleanFlashOverlay)
        liveMissFlashOverlay = findViewById(R.id.liveMissFlashOverlay)
        livePerfectFlashOverlay = findViewById(R.id.livePerfectFlashOverlay)
        liveHeaderCard = findViewById(R.id.liveHeaderCard)
        liveHeaderBadgeText = findViewById(R.id.liveHeaderBadgeText)
        liveHeaderTitleText = findViewById(R.id.liveHeaderTitleText)
        liveHeaderMetaText = findViewById(R.id.liveHeaderMetaText)
        liveFeedbackCard = findViewById(R.id.liveFeedbackCard)
        liveFeedbackText = findViewById(R.id.liveFeedbackText)
        liveFeedbackMetaText = findViewById(R.id.liveFeedbackMetaText)
        liveScorePanel = findViewById(R.id.liveScorePanel)
        liveScoreRing = findViewById(R.id.liveScoreRing)
        liveScoreStateText = findViewById(R.id.liveScoreStateText)
        liveRepCounterCluster = findViewById(R.id.liveRepCounterCluster)
        liveRepCounterLabelText = findViewById(R.id.liveRepCounterLabelText)
        liveRepCounterText = findViewById(R.id.liveRepCounterText)
        bottomNavBar = findViewById(R.id.bottomNavBar)
        bottomNavButtonsRow = findViewById(R.id.bottomNavButtonsRow)
        navMagmaGlow = findViewById(R.id.navMagmaGlow)
        navHomeButton = findViewById(R.id.navHomeButton)
        navWorkoutButton = findViewById(R.id.navWorkoutButton)
        navProgressButton = findViewById(R.id.navProgressButton)
        successOverlay = findViewById(R.id.successOverlay)
        successPanel = findViewById(R.id.successPanel)
        successText = findViewById(R.id.successText)
        successMetaText = findViewById(R.id.successMetaText)

        window.statusBarColor = ContextCompat.getColor(this, R.color.obsidian_950)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.obsidian_950)

        mediaProjectionManager =
            getSystemService(MediaProjectionManager::class.java)
        // overlay совпадает с preview
        previewView.scaleType = PreviewView.ScaleType.FIT_CENTER
        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        overlayView.setDebugHudVisible(false)
        liveFeedbackCard.background = buildLiveFeedbackBackground()
        updateLiveFeedbackTone(LiveFeedbackTone.NEUTRAL, immediate = true)
        liveScoreRing.setScore(0, animate = false, pulse = false)

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (qualityUiEnabled) {
            squatShadowClassifier = SquatV1BinaryClassifier.fromAsset(this)
            if (squatShadowClassifier == null) {
                Log.w("AirFloatSquat", "Squat binary model unavailable; squat AI feedback disabled")
            }
            pushupClassifier = PushupV1BinaryClassifier.fromAsset(this)
            if (pushupClassifier == null) {
                Log.w("AirFloatPushup", "Pushup binary model unavailable; pushup AI feedback disabled")
            }
            situpClassifier = SitupV1BinaryClassifier.fromAsset(this)
            if (situpClassifier == null) {
                Log.w("AirFloatSitup", "Sit-up binary model unavailable; sit-up AI feedback disabled")
            }
        } else {
            squatShadowEnabled = false
            squatShadowCard.visibility = View.GONE
        }

        ensureWorkoutFragment()
        createPoseDetector()
        updateSkeletonButton()
        applyTrainingPreset(trainingPreset, recreateDetector = false)
        updateSquatShadowButton()
        configureGaugeUiForExercise()
        switchRootTab(RootTab.HOME, animate = false)

        updateRecordButton()
        prestartPanel.post {
            if (currentRootTab == RootTab.WORKOUT) {
                animatePrestartEntrance()
                startLogoIdleMotion()
            }
        }

        finishButton.setOnClickListener {
            finishSession()
        }
        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            finishButton.setOnLongClickListener {
                if (!isLiveSessionActive()) return@setOnLongClickListener false
                runMockSessionCompletion()
                true
            }
        }

        benchButton.setOnClickListener {
            startBenchmark()
        }

        skeletonButton.setOnClickListener {
            toggleSkeletonEnabled()
        }
        navHomeButton.setOnClickListener {
            animateNavTap(it)
            switchRootTab(RootTab.HOME)
        }
        navWorkoutButton.setOnClickListener {
            animateNavTap(it)
            switchRootTab(RootTab.WORKOUT)
        }
        navProgressButton.setOnClickListener {
            animateNavTap(it)
            switchRootTab(RootTab.PROGRESS)
        }

        syncFirstLaunchGate()
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        return super.dispatchTouchEvent(ev)
    }

    private fun handleRootPageSwipe(ev: android.view.MotionEvent) {
        when (ev.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                rootSwipeDownX = ev.rawX
                rootSwipeDownY = ev.rawY
                rootSwipeDownAtMs = ev.eventTime
                rootSwipeActive = false
                rootSwipeDirection = RootMotionDirection.NONE
                rootSwipeTargetTab = null
                rootSwipeCurrentPanel = null
                rootSwipeTargetPanel = null
                rootSwipeCurrentContentLayer = null
                rootSwipeTargetContentLayer = null
                rootSwipeWidth = 0f
                resetRootSwipeOverlay()
                rootSwipeBlocked = !isRootSwipeEnabled() || shouldBlockRootSwipe(ev.rawX, ev.rawY)
            }

            android.view.MotionEvent.ACTION_MOVE -> {
                if (!rootSwipeBlocked) {
                    handleRootSwipeMove(ev.rawX, ev.rawY)
                }
            }

            android.view.MotionEvent.ACTION_UP -> {
                if (rootSwipeActive) {
                    finishInteractiveRootSwipe(ev.rawX, ev.rawY, ev.eventTime, cancelled = false)
                }
                rootSwipeActive = false
                rootSwipeBlocked = false
            }

            android.view.MotionEvent.ACTION_CANCEL -> {
                if (rootSwipeActive) {
                    finishInteractiveRootSwipe(rootSwipeDownX, rootSwipeDownY, ev.eventTime, cancelled = true)
                }
                rootSwipeActive = false
                rootSwipeBlocked = false
            }
        }
    }

    private fun isRootSwipeEnabled(): Boolean =
        bottomNavBar.visibility == View.VISIBLE &&
            previewView.visibility != View.VISIBLE &&
            successOverlay.visibility != View.VISIBLE &&
            countdownText.visibility != View.VISIBLE

    private fun shouldBlockRootSwipe(rawX: Float, rawY: Float): Boolean {
        val contentRoot = findViewById<ViewGroup>(android.R.id.content) ?: return false
        val touchedView = findTouchTarget(contentRoot, rawX, rawY) ?: return false
        return generateSequence(touchedView) { current ->
            current.parent as? View
        }.any { candidate ->
            candidate is HorizontalScrollView
        }
    }

    private fun findTouchTarget(view: View, rawX: Float, rawY: Float): View? {
        if (!view.isShown) return null
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val left = location[0].toFloat()
        val top = location[1].toFloat()
        val right = left + view.width
        val bottom = top + view.height
        if (rawX < left || rawX > right || rawY < top || rawY > bottom) return null
        if (view is ViewGroup) {
            for (index in view.childCount - 1 downTo 0) {
                val hit = findTouchTarget(view.getChildAt(index), rawX, rawY)
                if (hit != null) return hit
            }
        }
        return view
    }

    private fun handleRootSwipeMove(rawX: Float, rawY: Float) {
        val deltaX = rawX - rootSwipeDownX
        val deltaY = rawY - rootSwipeDownY

        if (!rootSwipeActive) {
            if (kotlin.math.abs(deltaX) < 14f.dpPx()) return
            if (kotlin.math.abs(deltaX) < kotlin.math.abs(deltaY) * 1.1f) return

            val direction =
                if (deltaX < 0f) {
                    RootMotionDirection.FORWARD
                } else {
                    RootMotionDirection.BACKWARD
                }
            val target = adjacentRootTab(direction) ?: run {
                rootSwipeBlocked = true
                return
            }
            startInteractiveRootSwipe(target, direction)
            if (!rootSwipeActive) return
        }

        updateInteractiveRootSwipe(deltaX)
    }

    private fun activePanelForTab(tab: RootTab): View =
        when (tab) {
            RootTab.HOME -> rootContentContainer
            RootTab.WORKOUT -> prestartPanel
            RootTab.PROGRESS -> progressContentContainer
        }

    private fun contentLayerForTab(tab: RootTab): View? = contentLayerForPanel(activePanelForTab(tab))

    private fun contentLayerForPanel(panel: View): View? =
        when (panel.id) {
            R.id.prestartPanel -> workoutContentContainer.getChildAt(0) ?: workoutContentContainer
            R.id.rootContentContainer -> rootContentContainer.getChildAt(0) ?: rootContentContainer
            R.id.progressContentContainer -> progressContentContainer.getChildAt(0) ?: progressContentContainer
            else -> null
        }

    private fun startInteractiveRootSwipe(targetTab: RootTab, direction: RootMotionDirection) {
        val currentPanel = activePanelForTab(currentRootTab)
        val targetPanel = preparePanelForTab(targetTab)

        rootSwipeActive = true
        rootSwipeDirection = direction
        rootSwipeTargetTab = targetTab
        rootSwipeCurrentPanel = currentPanel
        rootSwipeTargetPanel = targetPanel
        rootSwipeCurrentContentLayer = contentLayerForTab(currentRootTab)
        rootSwipeTargetContentLayer = contentLayerForTab(targetTab)
        rootSwipeWidth =
            maxOf(
                currentPanel.width,
                prestartPanel.width,
                rootContentContainer.width,
                progressContentContainer.width,
                resources.displayMetrics.widthPixels
            ).toFloat()

        currentPanel.animate().cancel()
        targetPanel.animate().cancel()
        currentPanel.visibility = View.VISIBLE
        currentPanel.alpha = 1f
        currentPanel.translationX = 0f
        currentPanel.translationY = 0f
        currentPanel.scaleX = 1f
        currentPanel.scaleY = 1f
        currentPanel.cameraDistance = 24000f * resources.displayMetrics.density
        currentPanel.rotationY = 0f
        rootSwipeCurrentContentLayer?.animate()?.cancel()
        rootSwipeCurrentContentLayer?.alpha = 1f
        rootSwipeCurrentContentLayer?.translationX = 0f
        rootSwipeCurrentContentLayer?.translationY = 0f
        rootSwipeCurrentContentLayer?.scaleX = 1f
        rootSwipeCurrentContentLayer?.scaleY = 1f
        targetPanel.visibility = View.VISIBLE
        targetPanel.alpha = 0.68f
        targetPanel.translationX =
            when (direction) {
                RootMotionDirection.FORWARD -> rootSwipeWidth
                RootMotionDirection.BACKWARD -> -rootSwipeWidth
                RootMotionDirection.NONE -> 0f
            }
        targetPanel.translationY = 20f.dpPx()
        targetPanel.scaleX = 0.986f
        targetPanel.scaleY = 0.986f
        targetPanel.cameraDistance = 24000f * resources.displayMetrics.density
        targetPanel.rotationY =
            when (direction) {
                RootMotionDirection.FORWARD -> 6f
                RootMotionDirection.BACKWARD -> -6f
                RootMotionDirection.NONE -> 0f
            }
        rootSwipeTargetContentLayer?.animate()?.cancel()
        rootSwipeTargetContentLayer?.alpha = 0.74f
        rootSwipeTargetContentLayer?.translationX =
            when (direction) {
                RootMotionDirection.FORWARD -> rootSwipeWidth * 0.14f
                RootMotionDirection.BACKWARD -> -rootSwipeWidth * 0.14f
                RootMotionDirection.NONE -> 0f
            }
        rootSwipeTargetContentLayer?.translationY = 20f.dpPx()
        rootSwipeTargetContentLayer?.scaleX = 0.992f
        rootSwipeTargetContentLayer?.scaleY = 0.992f
    }

    private fun updateInteractiveRootSwipe(deltaX: Float) {
        val currentPanel = rootSwipeCurrentPanel ?: return
        val targetPanel = rootSwipeTargetPanel ?: return
        val currentContent = rootSwipeCurrentContentLayer
        val targetContent = rootSwipeTargetContentLayer
        val width = rootSwipeWidth.takeIf { it > 0f } ?: return

        val constrained =
            when (rootSwipeDirection) {
                RootMotionDirection.FORWARD -> deltaX.coerceIn(-width, 0f)
                RootMotionDirection.BACKWARD -> deltaX.coerceIn(0f, width)
                RootMotionDirection.NONE -> 0f
            }
        val progress = (kotlin.math.abs(constrained) / width).coerceIn(0f, 1f)
        val depthLift = 18f.dpPx() * progress
        val targetLift = 18f.dpPx() * (1f - progress)

        currentPanel.translationX = constrained
        currentPanel.alpha = 1f - 0.1f * progress
        currentPanel.translationY = depthLift * 0.38f
        currentPanel.scaleX = 1f - 0.016f * progress
        currentPanel.scaleY = 1f - 0.016f * progress
        currentPanel.rotationY =
            when (rootSwipeDirection) {
                RootMotionDirection.FORWARD -> -5.2f * progress
                RootMotionDirection.BACKWARD -> 5.2f * progress
                RootMotionDirection.NONE -> 0f
            }
        currentContent?.translationX = constrained * 0.16f
        currentContent?.translationY = depthLift * 0.12f
        currentContent?.alpha = 1f - 0.08f * progress
        currentContent?.scaleX = 1f - 0.01f * progress
        currentContent?.scaleY = 1f - 0.01f * progress

        targetPanel.translationX =
            when (rootSwipeDirection) {
                RootMotionDirection.FORWARD -> width + constrained
                RootMotionDirection.BACKWARD -> -width + constrained
                RootMotionDirection.NONE -> 0f
            }
        targetPanel.alpha = 0.68f + 0.32f * progress
        targetPanel.translationY = targetLift
        targetPanel.scaleX = 0.986f + 0.014f * progress
        targetPanel.scaleY = 0.986f + 0.014f * progress
        targetPanel.rotationY =
            when (rootSwipeDirection) {
                RootMotionDirection.FORWARD -> 6f * (1f - progress)
                RootMotionDirection.BACKWARD -> -6f * (1f - progress)
                RootMotionDirection.NONE -> 0f
            }
        targetContent?.translationX =
            when (rootSwipeDirection) {
                RootMotionDirection.FORWARD -> width * 0.14f * (1f - progress)
                RootMotionDirection.BACKWARD -> -width * 0.14f * (1f - progress)
                RootMotionDirection.NONE -> 0f
            }
        targetContent?.translationY = targetLift * 0.6f
        targetContent?.alpha = 0.74f + 0.26f * progress
        targetContent?.scaleX = 0.992f + 0.008f * progress
        targetContent?.scaleY = 0.992f + 0.008f * progress

    }

    private fun finishInteractiveRootSwipe(
        rawX: Float,
        rawY: Float,
        eventTime: Long,
        cancelled: Boolean
    ) {
        val currentPanel = rootSwipeCurrentPanel ?: return
        val targetPanel = rootSwipeTargetPanel ?: return
        val currentContent = rootSwipeCurrentContentLayer
        val targetContent = rootSwipeTargetContentLayer
        val targetTab = rootSwipeTargetTab ?: return
        val width = rootSwipeWidth.takeIf { it > 0f } ?: return
        val deltaX = rawX - rootSwipeDownX
        val deltaY = rawY - rootSwipeDownY
        val elapsedMs = (eventTime - rootSwipeDownAtMs).coerceAtLeast(1L)
        val velocityX = deltaX / elapsedMs.toFloat()
        val progress = (kotlin.math.abs(currentPanel.translationX) / width).coerceIn(0f, 1f)
        val commit =
            !cancelled &&
                kotlin.math.abs(deltaX) > kotlin.math.abs(deltaY) * 1.1f &&
                when (rootSwipeDirection) {
                    RootMotionDirection.FORWARD -> progress > 0.28f || velocityX < -0.65f
                    RootMotionDirection.BACKWARD -> progress > 0.28f || velocityX > 0.65f
                    RootMotionDirection.NONE -> false
                }

        currentPanel.animate().cancel()
        targetPanel.animate().cancel()

        if (commit) {
            val outgoingX =
                when (rootSwipeDirection) {
                    RootMotionDirection.FORWARD -> -width
                    RootMotionDirection.BACKWARD -> width
                    RootMotionDirection.NONE -> 0f
                }
            currentPanel.animate()
                .translationX(outgoingX)
                .translationY(8f.dpPx())
                .alpha(0.88f)
                .scaleX(0.984f)
                .scaleY(0.984f)
                .rotationY(
                    when (rootSwipeDirection) {
                        RootMotionDirection.FORWARD -> -6f
                        RootMotionDirection.BACKWARD -> 6f
                        RootMotionDirection.NONE -> 0f
                    }
                )
                .setDuration(240L)
                .setInterpolator(surfaceEase)
                .start()
            currentContent?.animate()
                ?.translationX(outgoingX * 0.14f)
                ?.translationY(10f.dpPx())
                ?.alpha(0.9f)
                ?.scaleX(0.992f)
                ?.scaleY(0.992f)
                ?.setDuration(240L)
                ?.setInterpolator(surfaceEase)
                ?.start()

            targetPanel.animate()
                .translationX(0f)
                .translationY(0f)
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .rotationY(0f)
                .setDuration(300L)
                .setInterpolator(surfaceEase)
                .withEndAction {
                    currentRootTab = targetTab
                    updateBottomNavState()
                    syncPanelsForCurrentRootTab()
                    clearRootSwipeState()
                }
                .start()
            targetContent?.animate()
                ?.translationX(0f)
                ?.translationY(0f)
                ?.alpha(1f)
                ?.scaleX(1f)
                ?.scaleY(1f)
                ?.setDuration(300L)
                ?.setInterpolator(surfaceEase)
                ?.start()
        } else {
            val targetRestX =
                when (rootSwipeDirection) {
                    RootMotionDirection.FORWARD -> width
                    RootMotionDirection.BACKWARD -> -width
                    RootMotionDirection.NONE -> 0f
                }
            currentPanel.animate()
                .translationX(0f)
                .translationY(0f)
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .rotationY(0f)
                .setDuration(240L)
                .setInterpolator(surfaceEase)
                .start()
            currentContent?.animate()
                ?.translationX(0f)
                ?.translationY(0f)
                ?.alpha(1f)
                ?.scaleX(1f)
                ?.scaleY(1f)
                ?.setDuration(240L)
                ?.setInterpolator(surfaceEase)
                ?.start()

            targetPanel.animate()
                .translationX(targetRestX)
                .translationY(20f.dpPx())
                .alpha(0.68f)
                .scaleX(0.986f)
                .scaleY(0.986f)
                .rotationY(
                    when (rootSwipeDirection) {
                        RootMotionDirection.FORWARD -> 6f
                        RootMotionDirection.BACKWARD -> -6f
                        RootMotionDirection.NONE -> 0f
                    }
                )
                .setDuration(240L)
                .setInterpolator(surfaceEase)
                .withEndAction {
                    syncPanelsForCurrentRootTab()
                    clearRootSwipeState()
                }
                .start()
            targetContent?.animate()
                ?.translationX(
                    when (rootSwipeDirection) {
                        RootMotionDirection.FORWARD -> width * 0.14f
                        RootMotionDirection.BACKWARD -> -width * 0.14f
                        RootMotionDirection.NONE -> 0f
                    }
                )
                ?.translationY(20f.dpPx())
                ?.alpha(0.74f)
                ?.scaleX(0.992f)
                ?.scaleY(0.992f)
                ?.setDuration(240L)
                ?.setInterpolator(surfaceEase)
                ?.start()
        }
    }

    private fun buildPanelSnapshot(panel: View): Bitmap? {
        if (panel.width <= 0 || panel.height <= 0) return null
        return try {
            Bitmap.createBitmap(panel.width, panel.height, Bitmap.Config.ARGB_8888).also { bitmap ->
                val canvas = Canvas(bitmap)
                panel.draw(canvas)
            }
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun syncPanelsForCurrentRootTab() {
        prestartPanel.animate().cancel()
        rootContentContainer.animate().cancel()
        progressContentContainer.animate().cancel()
        prestartPanel.alpha = 1f
        prestartPanel.translationX = 0f
        prestartPanel.translationY = 0f
        prestartPanel.scaleX = 1f
        prestartPanel.scaleY = 1f
        prestartPanel.rotationY = 0f
        rootContentContainer.alpha = 1f
        rootContentContainer.translationX = 0f
        rootContentContainer.translationY = 0f
        rootContentContainer.scaleX = 1f
        rootContentContainer.scaleY = 1f
        rootContentContainer.rotationY = 0f
        progressContentContainer.alpha = 1f
        progressContentContainer.translationX = 0f
        progressContentContainer.translationY = 0f
        progressContentContainer.scaleX = 1f
        progressContentContainer.scaleY = 1f
        progressContentContainer.rotationY = 0f
        listOfNotNull(
            contentLayerForPanel(prestartPanel),
            contentLayerForPanel(rootContentContainer),
            contentLayerForPanel(progressContentContainer)
        ).forEach { layer ->
            layer.animate().cancel()
            layer.alpha = 1f
            layer.translationX = 0f
            layer.translationY = 0f
            layer.scaleX = 1f
            layer.scaleY = 1f
        }

        if (currentRootTab == RootTab.WORKOUT) {
            ensureWorkoutFragment()
            prestartPanel.visibility = View.VISIBLE
            rootContentContainer.visibility = View.GONE
            progressContentContainer.visibility = View.GONE
            workoutFragment?.startLogoIdleMotion()
        } else if (currentRootTab == RootTab.HOME) {
            showRootFragment(currentRootTab)
            rootContentContainer.visibility = View.VISIBLE
            prestartPanel.visibility = View.GONE
            progressContentContainer.visibility = View.GONE
            workoutFragment?.stopLogoIdleMotion()
        } else {
            showRootFragment(currentRootTab)
            progressContentContainer.visibility = View.VISIBLE
            prestartPanel.visibility = View.GONE
            rootContentContainer.visibility = View.GONE
            workoutFragment?.stopLogoIdleMotion()
        }
    }

    private fun clearRootSwipeState() {
        resetRootSwipeOverlay()
        rootSwipeActive = false
        rootSwipeDirection = RootMotionDirection.NONE
        rootSwipeTargetTab = null
        rootSwipeCurrentPanel = null
        rootSwipeTargetPanel = null
        rootSwipeCurrentContentLayer = null
        rootSwipeTargetContentLayer = null
        rootSwipeWidth = 0f
    }

    private fun resetRootSwipeOverlay() {
        rootSwipeSnapshotOverlay.animate().cancel()
        rootSwipeSnapshotOverlay.visibility = View.GONE
        rootSwipeSnapshotOverlay.alpha = 1f
        rootSwipeSnapshotOverlay.translationX = 0f
        rootSwipeSnapshotOverlay.translationY = 0f
        rootSwipeSnapshotOverlay.scaleX = 1f
        rootSwipeSnapshotOverlay.scaleY = 1f
        rootSwipeSnapshotOverlay.setImageDrawable(null)
        rootSwipeSnapshotBitmap?.recycle()
        rootSwipeSnapshotBitmap = null
    }

    private fun openSpotifyPlaylist(playlistUri: String) {
        val deepLinkIntent =
            Intent(Intent.ACTION_VIEW, Uri.parse(playlistUri)).apply {
                setPackage("com.spotify.music")
            }
        try {
            startActivity(deepLinkIntent)
            return
        } catch (_: ActivityNotFoundException) {
            // fallback to web below
        }

        val playlistId = playlistUri.removePrefix("spotify:playlist:")
        if (playlistId.isNotBlank() && playlistId != playlistUri) {
            val webIntent =
                Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com/playlist/$playlistId"))
            try {
                startActivity(webIntent)
                return
            } catch (_: ActivityNotFoundException) {
                // show toast below
            }
        }

        Toast.makeText(this, "Spotify is not available on this device", Toast.LENGTH_SHORT).show()
    }

    private fun parseGoal(rawValue: String) {
        if (exerciseMode == ExerciseMode.SQUAT_BETA) {
            goalReps = 0
            return
        }
        val v = rawValue.trim().toIntOrNull()
        goalReps = if (v != null && v in 1..99) v else 0
    }

    private fun currentProfileConfig(): MotionProfileConfig {
        return when (movementProfile) {
            MovementProfile.NO_WEIGHT ->
                MotionProfileConfig(
                    down = 86f,
                    up = 118f,
                    gaugeBottom = 30f,
                    holdFrames = 1,
                    syncWindow = 20,
                    emaAlpha = 0.55f,
                    minRepBottomAngle = 28f,
                    minUnderTopTravel = 18f,
                    underTopTopSlack = 1.0f,
                    minRepDurationMs = 430L,
                    minRangeSymmetryRatio = 0.60f
                )
            MovementProfile.DUMBBELL_SIM ->
                MotionProfileConfig(
                    down = 92f,
                    up = 112f,
                    gaugeBottom = 52f,
                    holdFrames = 1,
                    syncWindow = 20,
                    emaAlpha = 0.35f,
                    minRepBottomAngle = 35f,
                    minUnderTopTravel = 14f,
                    underTopTopSlack = 2.0f,
                    minRepDurationMs = 360L,
                    minRangeSymmetryRatio = 0.50f
                )
        }
    }

    private fun updateTrainingPresetButton() {
        syncWorkoutFragmentUi()
    }

    private fun updateExerciseCopy() {
        syncWorkoutFragmentUi()
    }

    private fun applyTrainingPreset(preset: TrainingPreset, recreateDetector: Boolean) {
        trainingPreset = preset
        when (preset) {
            TrainingPreset.PRESS_BARBELL -> {
                exerciseMode = ExerciseMode.PRESS
                movementProfile = MovementProfile.NO_WEIGHT
            }
            TrainingPreset.PRESS_DUMBBELL -> {
                exerciseMode = ExerciseMode.PRESS
                movementProfile = MovementProfile.DUMBBELL_SIM
            }
            TrainingPreset.SQUAT_BETA -> {
                exerciseMode = ExerciseMode.SQUAT_BETA
                movementProfile = MovementProfile.NO_WEIGHT
                if (qualityUiEnabled && squatShadowClassifier != null) {
                    squatShadowEnabled = true
                }
            }
            TrainingPreset.PUSHUP -> {
                exerciseMode = ExerciseMode.PUSHUP
                movementProfile = MovementProfile.NO_WEIGHT
                if (qualityUiEnabled && pushupClassifier != null) {
                    squatShadowEnabled = true
                }
            }
            TrainingPreset.SITUP -> {
                exerciseMode = ExerciseMode.SITUP
                movementProfile = MovementProfile.NO_WEIGHT
                if (qualityUiEnabled && situpClassifier != null) {
                    squatShadowEnabled = true
                }
            }
        }

        updateTrainingPresetButton()
        updateExerciseCopy()
        updateSquatShadowButton()
        configureQualityCardForExercise()
        configureGaugeUiForExercise()

        if (recreateDetector) {
            poseDetector.close()
            createPoseDetector()
        }
    }

    private fun toggleTrainingPreset() {
        if (prestartPanel.visibility != View.VISIBLE) {
            Toast.makeText(this, "Change preset before Start", Toast.LENGTH_SHORT).show()
            return
        }

        val nextPreset =
            when (trainingPreset) {
                TrainingPreset.PRESS_BARBELL -> TrainingPreset.PRESS_DUMBBELL
                TrainingPreset.PRESS_DUMBBELL -> TrainingPreset.SQUAT_BETA
                TrainingPreset.SQUAT_BETA -> TrainingPreset.PUSHUP
                TrainingPreset.PUSHUP -> TrainingPreset.SITUP
                TrainingPreset.SITUP -> TrainingPreset.PRESS_BARBELL
            }
        applyTrainingPreset(nextPreset, recreateDetector = true)
        Toast.makeText(this, "Preset switched", Toast.LENGTH_SHORT).show()
    }

    private fun updateSkeletonButton() {
        skeletonButton.text = if (skeletonEnabled) "Skeleton On" else "Skeleton Off"
    }

    private fun updateSquatShadowButton() {
        syncWorkoutFragmentUi()
    }

    private fun configureQualityCardForExercise() {
        if (!qualityUiEnabled) {
            squatShadowCard.visibility = View.GONE
            return
        }
        if (!::squatShadowTitleText.isInitialized) return
        squatShadowTitleText.text =
            when (exerciseMode) {
                ExerciseMode.SQUAT_BETA -> "SQUAT FORM AI"
                ExerciseMode.PUSHUP -> "PUSH FORM AI"
                ExerciseMode.SITUP -> "SIT-UP FORM AI"
                ExerciseMode.PRESS -> "FORM AI"
            }
        if (!squatShadowEnabled || exerciseMode == ExerciseMode.PRESS) {
            squatShadowClassText.text = "FORM --"
            squatShadowClassText.setTextColor(bannerColorNeutral)
            squatShadowConfidenceText.text = "BAD -- · CONF --"
        }
    }

    private fun hasQualityModelForMode(): Boolean {
        if (!qualityUiEnabled) return false
        return when (exerciseMode) {
            ExerciseMode.SQUAT_BETA -> squatShadowClassifier != null
            ExerciseMode.PUSHUP -> pushupClassifier != null
            ExerciseMode.SITUP -> situpClassifier != null
            ExerciseMode.PRESS -> false
        }
    }

    private fun toggleSquatShadow() {
        if (!qualityUiEnabled) {
            squatShadowEnabled = false
            squatShadowCard.visibility = View.GONE
            return
        }
        val available =
            when (exerciseMode) {
                ExerciseMode.SQUAT_BETA -> squatShadowClassifier != null
                ExerciseMode.PUSHUP -> pushupClassifier != null
                ExerciseMode.SITUP -> situpClassifier != null
                ExerciseMode.PRESS -> false
            }
        if (!available) {
            val msg =
                when (exerciseMode) {
                    ExerciseMode.SQUAT_BETA -> "Squat model unavailable"
                    ExerciseMode.PUSHUP -> "Push-up model unavailable"
                    ExerciseMode.SITUP -> "Sit-up model unavailable"
                    ExerciseMode.PRESS -> "AI quality is available only for Squat, Push-up or Sit-up preset"
                }
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            return
        }

        squatShadowEnabled = !squatShadowEnabled
        updateSquatShadowButton()
        val inWorkout = prestartPanel.visibility != View.VISIBLE
        squatShadowCard.visibility =
            if (inWorkout && squatShadowEnabled && exerciseMode != ExerciseMode.PRESS)
                View.VISIBLE
            else
                View.GONE
        if (!squatShadowEnabled) {
            squatShadowClassText.text = "FORM --"
            squatShadowConfidenceText.text = "BAD -- · CONF --"
        }
        configureQualityCardForExercise()
    }

    private fun toggleSkeletonEnabled() {
        skeletonEnabled = !skeletonEnabled
        updateSkeletonButton()
        if (!skeletonEnabled) {
            overlayView.setPointsPx(emptyList())
        }
    }

    private fun createPoseDetector() {
        val cfg = currentProfileConfig()
        // Detector keeps reps internally; on recreation we reset flash baseline too.
        lastReps = 0
        repFlashText.removeCallbacks(hideRepFlash)
        repFlashText.visibility = View.GONE
        resetLiveSessionSurface()
        lastRepRejectReason = null
        lastRepRejectAtMs = 0L
        repAttemptCounter = 0
        successAttempts = 0
        failedAttempts = 0
        squatCounter.reset()
        lastSquatCounterResult = null
        pushupCounter.reset()
        lastPushupCounterResult = null
        situpCounter.reset()
        lastSitupCounterResult = null
        squatShadowClassifier?.reset()
        pushupClassifier?.reset()
        situpClassifier?.reset()
        lastPushupQuality = null
        lastPushupLogAtMs = 0L
        lastSitupQuality = null
        lastSitupLogAtMs = 0L
        resetFailedRejectState()
        updateRepGauge(0f)
        configureQualityCardForExercise()
        configureGaugeUiForExercise()
        updateRepTableSummary()

        val onFrame: (frame: com.airfloat.app.pose.FrameResult) -> Unit = { frame ->
            runOnUiThread {

                val w = overlayView.width.toFloat()
                val h = overlayView.height.toFloat()

                if (w <= 1f || h <= 1f) return@runOnUiThread
                if (frame.srcWidth <= 0 || frame.srcHeight <= 0) return@runOnUiThread

                val srcW = frame.srcWidth.toFloat()
                val srcH = frame.srcHeight.toFloat()

                val scale = min(w / srcW, h / srcH)
                val dx = (w - srcW * scale) * 0.5f
                val dy = (h - srcH * scale) * 0.5f
                var displayCondition = frame.conditionCode

                when (exerciseMode) {
                    ExerciseMode.PRESS -> {
                        frame.reasonEvent?.let { onReasonEvent(it) }
                        frame.repRejectEvent?.let {
                            onRepRejectEvent(it)
                            scheduleRejectedAttempt(it)
                        }
                        updateRepGauge(frame.repProgress)
                        updateRepGaugeHint()
                    }
                    ExerciseMode.SQUAT_BETA -> {
                        val squat = squatCounter.update(frame.normPoints, SystemClock.uptimeMillis())
                        lastSquatCounterResult = squat
                        displayCondition = squat.conditionCode

                        updateRepGauge(squat.progress)
                        if (frame.normPoints.isEmpty()) {
                            updateSquatGaugeHint(null)
                        } else {
                            updateSquatGaugeHint(squat.progress)
                        }

                        squat.repRejectReason?.let {
                            onRepRejectEvent(it)
                            scheduleRejectedAttempt(it)
                        }

                        if (squat.reps > lastReps) {
                            val gained = squat.reps - lastReps
                            lastReps = squat.reps
                            repeat(gained) {
                                addAttemptLogRow(success = true)
                            }
                            handleSuccessfulRep(squat.reps)
                        }
                        checkGoal(squat.reps)
                    }
                    ExerciseMode.PUSHUP -> {
                        val pushup = pushupCounter.update(frame.normPoints, SystemClock.uptimeMillis())
                        lastPushupCounterResult = pushup
                        displayCondition = pushup.conditionCode

                        updateRepGauge(pushup.progress)
                        if (frame.normPoints.isEmpty()) {
                            updatePushupGaugeHint(null)
                        } else {
                            updatePushupGaugeHint(pushup.progress)
                        }

                        pushup.repRejectReason?.let {
                            onRepRejectEvent(it)
                            scheduleRejectedAttempt(it)
                        }

                        if (pushup.reps > lastReps) {
                            val gained = pushup.reps - lastReps
                            lastReps = pushup.reps
                            repeat(gained) {
                                addAttemptLogRow(success = true)
                            }
                            handleSuccessfulRep(pushup.reps)
                        }
                        checkGoal(pushup.reps)
                    }
                    ExerciseMode.SITUP -> {
                        val situp = situpCounter.update(frame.normPoints, SystemClock.uptimeMillis())
                        lastSitupCounterResult = situp
                        displayCondition = situp.conditionCode

                        updateRepGauge(situp.progress)
                        if (frame.normPoints.isEmpty()) {
                            updateSitupGaugeHint(null)
                        } else {
                            updateSitupGaugeHint(situp.progress)
                        }

                        situp.repRejectReason?.let {
                            onRepRejectEvent(it)
                            scheduleRejectedAttempt(it)
                        }

                        if (situp.reps > lastReps) {
                            val gained = situp.reps - lastReps
                            lastReps = situp.reps
                            repeat(gained) {
                                addAttemptLogRow(success = true)
                            }
                            handleSuccessfulRep(situp.reps)
                        }
                        checkGoal(situp.reps)
                    }
                }

                updateConditionBanner(displayCondition)
                val shouldShowSkeleton =
                    if (exerciseMode == ExerciseMode.PRESS) {
                        shownCondition == ConditionCode.OK && skeletonEnabled
                    } else {
                        skeletonEnabled && frame.normPoints.isNotEmpty()
                    }
                if (shouldShowSkeleton) {
                    val ptsPx = frame.normPoints.map { p ->
                        var x = p.x * srcW * scale + dx
                        val y = p.y * srcH * scale + dy

                        if (frame.isFrontCamera)
                            x = w - x

                        PointF(x, y)
                    }
                    overlayView.setPointsPx(ptsPx)
                } else {
                    overlayView.setPointsPx(emptyList())
                }

                overlayView.setDebug(
                    frame.leftAngle,
                    frame.rightAngle,
                    frame.reps,
                    frame.fps,
                    frame.latencyMs,
                    frame.pipeline
                )
                runExerciseQuality(frame)

                if (benchActive) {
                    benchFrames += 1
                    benchLatencySum += frame.latencyMs
                }

                if (exerciseMode == ExerciseMode.PRESS) {
                    if (frame.reps > lastReps) {
                        val gained = frame.reps - lastReps
                        lastReps = frame.reps
                        repeat(gained) {
                            addAttemptLogRow(success = true)
                        }
                        handleSuccessfulRep(frame.reps)
                    }
                    checkGoal(frame.reps)
                }
            }
        }

        poseDetector = PoseDetector(
            context = this,
            isFrontCamera = isFrontCamera,
            enablePressRepPipeline = exerciseMode == ExerciseMode.PRESS,
            emitReasonEvents = exerciseMode == ExerciseMode.PRESS,
            requireArmsUpForTracking = exerciseMode == ExerciseMode.PRESS,
            trackingPointIndices =
                when (exerciseMode) {
                    ExerciseMode.PRESS -> intArrayOf(11, 13, 15, 12, 14, 16)
                    ExerciseMode.SQUAT_BETA -> intArrayOf(11, 12, 23, 24, 25, 26, 27, 28)
                    // Push-up uses its own counter-level reliability checks.
                    // Disabling detector-level strict gate avoids constant tracking-gap resets
                    // when body is partially outside frame in floor camera setups.
                    ExerciseMode.PUSHUP -> intArrayOf()
                    // Sit-up also runs dedicated counter-level validity checks.
                    // Keep detector-level gate relaxed for floor camera setups.
                    ExerciseMode.SITUP -> intArrayOf()
                },
            minPoseDetectionConfidence =
                if (exerciseMode == ExerciseMode.PUSHUP || exerciseMode == ExerciseMode.SITUP) 0.35f else 0.5f,
            minPosePresenceConfidence =
                if (exerciseMode == ExerciseMode.PUSHUP || exerciseMode == ExerciseMode.SITUP) 0.35f else 0.5f,
            minTrackingConfidence =
                if (exerciseMode == ExerciseMode.PUSHUP || exerciseMode == ExerciseMode.SITUP) 0.30f else 0.5f,
            downThreshold = cfg.down,
            upThreshold = cfg.up,
            gaugeBottomAngleDeg = cfg.gaugeBottom,
            holdFrames = cfg.holdFrames,
            syncWindowFrames = cfg.syncWindow,
            emaAlpha = cfg.emaAlpha,
            minRepBottomAngleDeg = cfg.minRepBottomAngle,
            minUnderTopTravelDeg = cfg.minUnderTopTravel,
            underTopTopSlackDeg = cfg.underTopTopSlack,
            minRepDurationMs = cfg.minRepDurationMs,
            minRangeSymmetryRatio = cfg.minRangeSymmetryRatio,
            onFrame = onFrame
        )

        overlayView.setDebug(
            null,
            null,
            0,
            0f,
            0f,
            "MediaPipe"
        )
    }

    private fun checkGoal(reps: Int) {
        if (goalReached) return
        if (goalReps > 0 && reps >= goalReps) {
            goalReached = true
            showSuccess()
        }
    }

    private fun animateViewIn(
        view: View,
        delayMs: Long,
        fromYDp: Float = 0f,
        fromXDp: Float = 0f,
        fromScale: Float = 0.96f,
        durationMs: Long = 560L
    ) {
        view.animate().cancel()
        view.alpha = 0f
        view.translationX = fromXDp.dpPx()
        view.translationY = fromYDp.dpPx()
        view.scaleX = fromScale
        view.scaleY = fromScale
        view.animate()
            .alpha(1f)
            .translationX(0f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setStartDelay(delayMs)
            .setDuration(durationMs)
            .setInterpolator(surfaceEase)
            .start()
    }

    private fun animatePrestartEntrance() {
        workoutFragment?.animateEntrance()
    }

    private fun startLogoIdleMotion() {
        workoutFragment?.startLogoIdleMotion()
    }

    private fun updateBottomNavState(immediate: Boolean = false) {
        if (firstLaunchActive) {
            cancelMagmaIndicatorAnimation()
            bottomNavBar.visibility = View.GONE
            return
        }
        val activeBg = R.drawable.nav_tab_active
        val idleBg = R.drawable.nav_tab_idle
        val activeText = ContextCompat.getColor(this, R.color.acid_lime_400)
        val idleText = ContextCompat.getColor(this, R.color.hud_text_secondary)

        val allTabs = listOf(navHomeButton, navWorkoutButton, navProgressButton)
        allTabs.forEach {
            it.animate().cancel()
            it.setBackgroundResource(idleBg)
            it.setTextColor(idleText)
            it.alpha = 0.74f
            it.translationY = 0f
            it.scaleX = 1f
            it.scaleY = 1f
        }

        val activeButton =
            when (currentRootTab) {
                RootTab.HOME -> navHomeButton
                RootTab.WORKOUT -> navWorkoutButton
                RootTab.PROGRESS -> navProgressButton
            }

        activeButton.setBackgroundResource(activeBg)
        activeButton.setTextColor(activeText)
        activeButton.alpha = 1f
        activeButton.translationY = (-2f).dpPx()
        animateMagmaIndicatorTo(activeButton, immediate)
    }

    private fun navButtonForTab(tab: RootTab): TextView =
        when (tab) {
            RootTab.HOME -> navHomeButton
            RootTab.WORKOUT -> navWorkoutButton
            RootTab.PROGRESS -> navProgressButton
        }

    private fun lerp(start: Float, end: Float, progress: Float): Float =
        start + (end - start) * progress

    private fun cancelMagmaIndicatorAnimation() {
        navMagmaFlowAnimator?.cancel()
        navMagmaFlowAnimator = null
        navMagmaGlow.animate().cancel()
    }

    private fun applyMagmaIndicatorFrame(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        alpha: Float,
        scaleX: Float,
        scaleY: Float
    ) {
        val layoutParams = navMagmaGlow.layoutParams as FrameLayout.LayoutParams
        val targetWidth = width.toInt().coerceAtLeast(1)
        val targetHeight = height.toInt().coerceAtLeast(1)
        if (layoutParams.width != targetWidth || layoutParams.height != targetHeight) {
            layoutParams.width = targetWidth
            layoutParams.height = targetHeight
            navMagmaGlow.layoutParams = layoutParams
        }
        navMagmaGlow.visibility = View.VISIBLE
        navMagmaGlow.x = x
        navMagmaGlow.y = y
        navMagmaGlow.alpha = alpha
        navMagmaGlow.scaleX = scaleX
        navMagmaGlow.scaleY = scaleY
    }

    private fun animateMagmaIndicatorTo(
        activeButton: TextView,
        immediate: Boolean
    ) {
        if (bottomNavButtonsRow.width == 0 || activeButton.width == 0) {
            bottomNavBar.post { animateMagmaIndicatorTo(activeButton, true) }
            return
        }

        val targetX = bottomNavButtonsRow.x + activeButton.x
        val targetY = bottomNavButtonsRow.y + activeButton.y
        val targetWidth = activeButton.width.toFloat()
        val targetHeight = activeButton.height.toFloat()

        if (immediate || navMagmaGlow.visibility != View.VISIBLE || navMagmaGlow.width == 0) {
            cancelMagmaIndicatorAnimation()
            applyMagmaIndicatorFrame(
                x = targetX,
                y = targetY,
                width = targetWidth,
                height = targetHeight,
                alpha = 0.98f,
                scaleX = 1f,
                scaleY = 1f
            )
            return
        }

        cancelMagmaIndicatorAnimation()
        val startX = navMagmaGlow.x
        val startY = navMagmaGlow.y
        val startWidth = navMagmaGlow.width.toFloat()
        val startHeight = navMagmaGlow.height.toFloat()
        val startAlpha = navMagmaGlow.alpha

        navMagmaFlowAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 420L
            interpolator = magmaEase
            addUpdateListener { animator ->
                val t = animator.animatedValue as Float
                applyMagmaIndicatorFrame(
                    x = lerp(startX, targetX, t),
                    y = lerp(startY, targetY, t),
                    width = lerp(startWidth, targetWidth, t),
                    height = lerp(startHeight, targetHeight, t),
                    alpha = lerp(startAlpha, 1f, t),
                    scaleX = 1f,
                    scaleY = 1f
                )
            }
            start()
        }
    }

    private fun animateNavTap(view: View) {
        view.animate().cancel()
        view.animate()
            .scaleX(0.88f)
            .scaleY(0.88f)
            .setDuration(90L)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(180L)
                    .setInterpolator(springEase)
                    .start()
            }
            .start()
    }

    private fun workoutSubtitleText(appState: AppState): String =
        todayLaunchPresetKey?.let { presetKey ->
            WorkoutSurfaceFactory.exerciseLabel(presetKey)
        } ?: run {
            when (exerciseMode) {
                ExerciseMode.PRESS -> "BARBELL PRESS"
                ExerciseMode.SQUAT_BETA -> "SQUATS"
                ExerciseMode.PUSHUP -> "PUSH-UP"
                ExerciseMode.SITUP -> "SIT-UP"
            }
        }

    private fun qualityButtonLabel(): String {
        val modeTitle =
            when (exerciseMode) {
                ExerciseMode.SQUAT_BETA -> "Squat AI"
                ExerciseMode.PUSHUP -> "Push-up AI"
                ExerciseMode.SITUP -> "Sit-up AI"
                ExerciseMode.PRESS -> "Exercise AI"
            }
        val available = hasQualityModelForMode()
        return if (!available) {
            "$modeTitle: N/A"
        } else if (squatShadowEnabled) {
            "$modeTitle: On"
        } else {
            "$modeTitle: Off"
        }
    }

    private fun buildLoopAppState(now: Long = System.currentTimeMillis()): AppState {
        val sessions = sessionStatsRepository.loadSessions()
        val baseAppState =
            AppStateCalculator.getAppState(
                sessions = sessions,
                timeContext = AppTimeContext(nowEpochMs = now),
                lastProgressRead = lastProgressReadAtMs
            )
        return progressTodayWriteBack?.let { writeBack ->
            baseAppState.copy(
                recommendedExercise = writeBack.recommendedExercise,
                recommendedIntensity = writeBack.recommendedIntensity,
                streakRisk = writeBack.streakRisk,
                lastProgressRead = writeBack.readAtMs
            )
        } ?: baseAppState
    }

    private fun buildWorkoutUiState(): WorkoutFragment.UiState {
        val now = System.currentTimeMillis()
        val sessions = sessionStatsRepository.loadSessions()
        val appState = buildLoopAppState(now)
        return WorkoutSurfaceFactory.build(
            subtitleText = workoutSubtitleText(appState),
            qualityButtonText = qualityButtonLabel(),
            qualityButtonVisible = qualityUiEnabled,
            qualityButtonEnabled = hasQualityModelForMode(),
            recordButtonText = if (recordEnabled) "Record: On" else "Record: Off",
            repsEnabled = exerciseMode != ExerciseMode.SQUAT_BETA,
            repsHint = if (exerciseMode == ExerciseMode.SQUAT_BETA) "N/A" else "Reps",
            sessions = sessions,
            appState = appState,
            todayEntryPresetKey = todayLaunchPresetKey,
            selectedPresetKey = currentPresetStatsKey()
        )
    }

    private fun syncWorkoutFragmentUi() {
        workoutFragment?.render(buildWorkoutUiState())
    }

    private fun buildTodayUiState(): TodayUiState {
        val sessions = sessionStatsRepository.loadSessions()
        val now = System.currentTimeMillis()
        val appState = buildLoopAppState(now)
        return TodaySurfaceFactory.build(
            appState = appState,
            sessions = sessions,
            nowEpochMs = now
        )
    }

    private fun syncHomeFragmentUi() {
        homeFragment?.render(buildTodayUiState())
    }

    private fun applyProgressWriteBack(nowEpochMs: Long = System.currentTimeMillis()) {
        val writeBack = progressFragment?.consumeTodayWriteBack(nowEpochMs)
        lastProgressReadAtMs = writeBack?.readAtMs ?: nowEpochMs
        progressTodayWriteBack = writeBack
        syncHomeFragmentUi()
    }

    private fun ensureWorkoutFragment(): WorkoutFragment {
        val existing =
            supportFragmentManager.findFragmentByTag(WorkoutFragment.TAG) as? WorkoutFragment
        if (existing != null) {
            workoutFragment = existing
            syncWorkoutFragmentUi()
            return existing
        }

        val fragment = workoutFragment ?: WorkoutFragment()
        workoutFragment = fragment
        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(workoutContentContainer.id, fragment, WorkoutFragment.TAG)
            .commitNowAllowingStateLoss()
        syncWorkoutFragmentUi()
        return fragment
    }

    private fun ensureHomeFragment(): HomeFragment {
        val existing =
            supportFragmentManager.findFragmentByTag(HomeFragment.TAG) as? HomeFragment
        if (existing != null) {
            homeFragment = existing
            syncHomeFragmentUi()
            return existing
        }

        val fragment = homeFragment ?: HomeFragment()
        homeFragment = fragment
        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(rootContentContainer.id, fragment, HomeFragment.TAG)
            .commitNowAllowingStateLoss()
        syncHomeFragmentUi()
        return fragment
    }

    private fun ensureProgressFragment(): ProgressFragment {
        val existing =
            supportFragmentManager.findFragmentByTag(ProgressFragment.TAG) as? ProgressFragment
        if (existing != null) {
            progressFragment = existing
            return existing
        }

        val fragment = progressFragment ?: ProgressFragment()
        progressFragment = fragment
        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(progressContentContainer.id, fragment, ProgressFragment.TAG)
            .commitNowAllowingStateLoss()
        return fragment
    }

    private fun ensureFirstLaunchFragment(): FirstLaunchFragment {
        val existing =
            supportFragmentManager.findFragmentByTag(FirstLaunchFragment.TAG) as? FirstLaunchFragment
        if (existing != null) {
            firstLaunchFragment = existing
            return existing
        }

        val fragment = firstLaunchFragment ?: FirstLaunchFragment()
        firstLaunchFragment = fragment
        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(firstLaunchContentContainer.id, fragment, FirstLaunchFragment.TAG)
            .commitNowAllowingStateLoss()
        return fragment
    }

    private fun syncFirstLaunchGate() {
        if (firstLaunchRepository.shouldShowFirstLaunching()) {
            showFirstLaunch()
        } else {
            hideFirstLaunch(immediate = true)
        }
    }

    private fun showFirstLaunch() {
        ensureFirstLaunchFragment()
        firstLaunchActive = true
        firstLaunchContentContainer.visibility = View.VISIBLE
        firstLaunchContentContainer.alpha = 0f
        firstLaunchContentContainer.translationY = 16f.dpPx()
        firstLaunchContentContainer.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(260L)
            .setInterpolator(surfaceEase)
            .start()
        bottomNavBar.animate().cancel()
        bottomNavBar.visibility = View.GONE
    }

    private fun hideFirstLaunch(immediate: Boolean) {
        firstLaunchActive = false
        firstLaunchContentContainer.animate().cancel()
        if (immediate) {
            firstLaunchContentContainer.visibility = View.GONE
            firstLaunchContentContainer.alpha = 1f
            firstLaunchContentContainer.translationY = 0f
        } else {
            firstLaunchContentContainer.animate()
                .alpha(0f)
                .translationY((-12f).dpPx())
                .setDuration(180L)
                .setInterpolator(surfaceEase)
                .withEndAction {
                    firstLaunchContentContainer.visibility = View.GONE
                    firstLaunchContentContainer.alpha = 1f
                    firstLaunchContentContainer.translationY = 0f
                }
                .start()
        }
        setBottomNavVisible(true)
        switchRootTab(RootTab.HOME, animate = false)
    }

    private fun showRootFragment(tab: RootTab) {
        if (tab == RootTab.WORKOUT) return

        when (tab) {
            RootTab.HOME -> ensureHomeFragment()
            RootTab.PROGRESS -> ensureProgressFragment()
            RootTab.WORKOUT -> return
        }
    }

    private fun preparePanelForTab(tab: RootTab): View {
        when (tab) {
            RootTab.HOME -> ensureHomeFragment()
            RootTab.WORKOUT -> ensureWorkoutFragment()
            RootTab.PROGRESS -> ensureProgressFragment()
        }
        return activePanelForTab(tab)
    }

    private fun rootTabIndex(tab: RootTab): Int =
        when (tab) {
            RootTab.HOME -> 0
            RootTab.WORKOUT -> 1
            RootTab.PROGRESS -> 2
        }

    private fun inferRootMotionDirection(from: RootTab, to: RootTab): RootMotionDirection =
        when {
            rootTabIndex(to) > rootTabIndex(from) -> RootMotionDirection.FORWARD
            rootTabIndex(to) < rootTabIndex(from) -> RootMotionDirection.BACKWARD
            else -> RootMotionDirection.NONE
        }

    private fun adjacentRootTab(direction: RootMotionDirection): RootTab? {
        val nextIndex =
            when (direction) {
                RootMotionDirection.FORWARD -> rootTabIndex(currentRootTab) + 1
                RootMotionDirection.BACKWARD -> rootTabIndex(currentRootTab) - 1
                RootMotionDirection.NONE -> rootTabIndex(currentRootTab)
            }
        return when (nextIndex) {
            0 -> RootTab.HOME
            1 -> RootTab.WORKOUT
            2 -> RootTab.PROGRESS
            else -> null
        }
    }

    private fun animateHorizontalPanelIn(
        view: View,
        direction: RootMotionDirection
    ) {
        val width = view.width.takeIf { it > 0 }?.toFloat() ?: resources.displayMetrics.widthPixels.toFloat()
        val startX =
            when (direction) {
                RootMotionDirection.FORWARD -> width * 0.14f
                RootMotionDirection.BACKWARD -> -width * 0.14f
                RootMotionDirection.NONE -> 0f
            }
        view.animate().cancel()
        view.visibility = View.VISIBLE
        view.alpha = 0f
        view.translationX = startX
        view.scaleX = 0.992f
        view.scaleY = 0.992f
        view.animate()
            .alpha(1f)
            .translationX(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(360L)
            .setInterpolator(surfaceEase)
            .start()
    }

    private fun animateHorizontalSwap(
        outgoing: View,
        incoming: View,
        direction: RootMotionDirection,
        onIncomingReady: (() -> Unit)? = null,
        onTransitionEnd: (() -> Unit)? = null
    ) {
        val width = max(outgoing.width, incoming.width).takeIf { it > 0 }?.toFloat()
            ?: resources.displayMetrics.widthPixels.toFloat()
        val outgoingX =
            when (direction) {
                RootMotionDirection.FORWARD -> -width * 0.18f
                RootMotionDirection.BACKWARD -> width * 0.18f
                RootMotionDirection.NONE -> 0f
            }
        val incomingX =
            when (direction) {
                RootMotionDirection.FORWARD -> width * 0.14f
                RootMotionDirection.BACKWARD -> -width * 0.14f
                RootMotionDirection.NONE -> 0f
            }

        incoming.animate().cancel()
        outgoing.animate().cancel()
        onIncomingReady?.invoke()
        incoming.visibility = View.VISIBLE
        incoming.alpha = 0f
        incoming.translationX = incomingX
        incoming.translationY = 18f.dpPx()
        incoming.scaleX = 0.992f
        incoming.scaleY = 0.992f
        incoming.cameraDistance = 24000f * resources.displayMetrics.density
        incoming.rotationY =
            when (direction) {
                RootMotionDirection.FORWARD -> 5.5f
                RootMotionDirection.BACKWARD -> -5.5f
                RootMotionDirection.NONE -> 0f
            }
        outgoing.cameraDistance = 24000f * resources.displayMetrics.density

        outgoing.animate()
            .alpha(0f)
            .translationX(outgoingX)
            .translationY(8f.dpPx())
            .scaleX(0.986f)
            .scaleY(0.986f)
            .rotationY(
                when (direction) {
                    RootMotionDirection.FORWARD -> -4.5f
                    RootMotionDirection.BACKWARD -> 4.5f
                    RootMotionDirection.NONE -> 0f
                }
            )
            .setDuration(220L)
            .setInterpolator(surfaceEase)
            .withEndAction {
                outgoing.visibility = View.GONE
                outgoing.alpha = 1f
                outgoing.translationX = 0f
                outgoing.translationY = 0f
                outgoing.scaleX = 1f
                outgoing.scaleY = 1f
                outgoing.rotationY = 0f
            }
            .start()

        incoming.animate()
            .alpha(1f)
            .translationX(0f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .rotationY(0f)
            .setDuration(360L)
            .setInterpolator(surfaceEase)
            .withEndAction {
                onTransitionEnd?.invoke()
            }
            .start()
    }

    private fun switchRootTab(
        tab: RootTab,
        animate: Boolean = true,
        directionOverride: RootMotionDirection? = null
    ) {
        if (tab == currentRootTab) {
            updateBottomNavState(immediate = !animate)
            val alreadyVisible = activePanelForTab(tab).visibility == View.VISIBLE
            if (tab == RootTab.PROGRESS) {
                ensureProgressFragment().refresh()
            }
            if (alreadyVisible) return
        }

        val previousTab = currentRootTab
        if (previousTab == RootTab.PROGRESS && tab != RootTab.PROGRESS) {
            applyProgressWriteBack()
        }
        val direction = directionOverride ?: inferRootMotionDirection(previousTab, tab)
        currentRootTab = tab

        updateBottomNavState(immediate = !animate)

        val outgoingPanel = activePanelForTab(previousTab)
        val incomingPanel = preparePanelForTab(tab)

        if (!animate || previousTab == tab) {
            syncPanelsForCurrentRootTab()
            if (tab == RootTab.PROGRESS) {
                ensureProgressFragment().refresh()
            }
            if (tab == RootTab.WORKOUT) {
                prestartPanel.post {
                    animatePrestartEntrance()
                    startLogoIdleMotion()
                }
            }
            return
        }

        animateHorizontalSwap(
            outgoing = outgoingPanel,
            incoming = incomingPanel,
            direction = direction,
            onTransitionEnd = {
                syncPanelsForCurrentRootTab()
                if (tab == RootTab.PROGRESS) {
                    ensureProgressFragment().refresh()
                }
                if (tab == RootTab.WORKOUT) {
                    prestartPanel.post {
                        animatePrestartEntrance()
                        startLogoIdleMotion()
                    }
                }
            }
        )
    }

    private fun setBottomNavVisible(visible: Boolean) {
        bottomNavBar.animate().cancel()
        if (visible) {
            bottomNavBar.visibility = View.VISIBLE
            bottomNavBar.alpha = 0f
            bottomNavBar.translationY = 20f.dpPx()
            bottomNavBar.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(260L)
                .setInterpolator(surfaceEase)
                .start()
        } else {
            bottomNavBar.animate()
                .alpha(0f)
                .translationY(20f.dpPx())
                .setDuration(180L)
                .setInterpolator(surfaceEase)
                .withEndAction {
                    bottomNavBar.visibility = View.GONE
                    bottomNavBar.translationY = 0f
                }
                .start()
        }
    }

    private fun pulseCountdownBadge() {
        countdownText.animate().cancel()
        countdownText.alpha = 0.7f
        countdownText.scaleX = 0.9f
        countdownText.scaleY = 0.9f
        countdownText.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(320L)
            .setInterpolator(springEase)
            .start()
    }

    private fun resetLaunchTransitionVisualState() {
        prestartPanel.animate().cancel()
        countdownText.animate().cancel()
        val trainLayer = contentLayerForPanel(prestartPanel) ?: workoutContentContainer
        trainLayer.animate().cancel()
        prestartPanel.alpha = 1f
        prestartPanel.translationY = 0f
        prestartPanel.scaleX = 1f
        prestartPanel.scaleY = 1f
        trainLayer.alpha = 1f
        trainLayer.translationY = 0f
        trainLayer.translationX = 0f
        trainLayer.scaleX = 1f
        trainLayer.scaleY = 1f
        countdownText.alpha = 1f
        countdownText.scaleX = 1f
        countdownText.scaleY = 1f
        countdownText.translationY = 0f
    }

    private fun collapseTrainSurfaceForLaunch() {
        val trainLayer = contentLayerForPanel(prestartPanel) ?: workoutContentContainer
        trainLayer.animate().cancel()
        trainLayer.pivotX = trainLayer.width * 0.5f
        trainLayer.pivotY = trainLayer.height * 0.5f
        trainLayer.animate()
            .alpha(0.14f)
            .translationY((-22f).dpPx())
            .scaleX(0.91f)
            .scaleY(0.91f)
            .setDuration(360L)
            .setInterpolator(surfaceEase)
            .start()
    }

    private fun prepareLiveSurfaceBehindLaunch() {
        previewView.visibility = View.VISIBLE
        overlayView.visibility = View.VISIBLE
        hudTopScrim.visibility = View.VISIBLE
        hudBottomScrim.visibility = View.VISIBLE
        liveHeaderCard.visibility = View.GONE
        liveScorePanel.visibility = View.GONE
        liveRepCounterCluster.visibility = View.GONE
        liveFeedbackCard.visibility = View.GONE

        previewView.animate().cancel()
        overlayView.animate().cancel()
        hudTopScrim.animate().cancel()
        hudBottomScrim.animate().cancel()

        previewView.alpha = 0f
        previewView.scaleX = 1.08f
        previewView.scaleY = 1.08f
        overlayView.alpha = 0f
        hudTopScrim.alpha = 0f
        hudBottomScrim.alpha = 0f
    }

    private fun setCountdownValue(value: Int) {
        countdownText.text = value.toString()
        countdownText.visibility = View.VISIBLE
        countdownText.translationY = 16f.dpPx()
        pulseCountdownBadge()
    }

    private fun revealLiveSurfaceFromLaunch() {
        countdownText.animate().cancel()
        countdownText.animate()
            .alpha(0f)
            .scaleX(1.08f)
            .scaleY(1.08f)
            .translationY((-18f).dpPx())
            .setDuration(180L)
            .setInterpolator(surfaceEase)
            .withEndAction {
                countdownText.visibility = View.GONE
                countdownText.alpha = 1f
                countdownText.scaleX = 1f
                countdownText.scaleY = 1f
                countdownText.translationY = 0f
            }
            .start()

        prestartPanel.animate().cancel()
        prestartPanel.animate()
            .alpha(0f)
            .scaleX(1.03f)
            .scaleY(1.03f)
            .setDuration(400L)
            .setInterpolator(surfaceEase)
            .withEndAction {
                prestartPanel.visibility = View.GONE
                resetLaunchTransitionVisualState()
            }
            .start()

        conditionCard.visibility = View.GONE
        squatShadowCard.visibility = View.GONE
        finishButton.visibility = View.VISIBLE
        benchButton.visibility = View.GONE
        skeletonButton.visibility = View.GONE
        configureGaugeUiForExercise()
        repGaugePanel.visibility = View.VISIBLE
        repTablePanel.visibility = View.VISIBLE
        liveHeaderCard.visibility = View.VISIBLE
        liveScorePanel.visibility = View.GONE
        liveRepCounterCluster.visibility = View.GONE
        resetBenchButtonUi()
        startRepTableSession()
        resetLiveSessionSurface()
        updateLiveSessionChrome()
        showLiveFeedback(
            headline = "FIND FIRST CLEAN REP",
            detail = "Find first clean rep.",
            tone = LiveFeedbackTone.NEUTRAL,
            autoHide = false
        )
        animateWorkoutReveal()
        startLiveHeaderTicker()

        if (recordEnabled) {
            startRecording()
        }
    }

    private fun animateWorkoutReveal() {
        previewView.animate().cancel()
        overlayView.animate().cancel()

        previewView.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(520L)
            .setInterpolator(surfaceEase)
            .start()

        overlayView.animate()
            .alpha(1f)
            .setStartDelay(60L)
            .setDuration(220L)
            .setInterpolator(surfaceEase)
            .start()

        hudTopScrim.animate().alpha(1f).setDuration(280L).setInterpolator(surfaceEase).start()
        hudBottomScrim.animate().alpha(1f).setDuration(320L).setInterpolator(surfaceEase).start()

        animateViewIn(liveHeaderCard, 36L, fromYDp = -14f, fromScale = 0.96f)
        animateViewIn(squatShadowCard, 90L, fromScale = 0.94f)
        animateViewIn(repGaugePanel, 120L, fromXDp = 18f, fromScale = 0.96f)
        animateViewIn(repTablePanel, 160L, fromXDp = -18f, fromScale = 0.96f)
        animateViewIn(finishButton, 210L, fromYDp = 14f, fromScale = 0.97f)
    }

    private fun startCountdown() {
        cancelBenchmark()
        pendingStartAfterPermission = false
        workoutFragment?.stopLogoIdleMotion()
        resetLaunchTransitionVisualState()
        setBottomNavVisible(false)
        prepareLiveSurfaceBehindLaunch()
        if (!cameraStarted) {
            startCamera()
            cameraStarted = true
        }
        setCountdownValue(3)
        collapseTrainSurfaceForLaunch()

        countdown?.cancel()
        countdown = object : CountDownTimer(1500L, 500L) {
            override fun onTick(millisUntilFinished: Long) {
                val nextValue =
                    when {
                        millisUntilFinished > 500L -> 2
                        else -> 1
                    }
                setCountdownValue(nextValue)
            }

            override fun onFinish() {
                revealLiveSurfaceFromLaunch()
            }
        }.start()
    }

    private fun startCamera() {

        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({

            val cameraProvider =
                cameraProviderFuture.get()
            cameraProviderBound = cameraProvider

            val preview = Preview.Builder().build().apply {
                setSurfaceProvider(previewView.surfaceProvider)
            }

            val analyzer =
                ImageAnalysis.Builder()
                    .setTargetResolution(Size(640, 480))
                    .setBackpressureStrategy(
                        ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                    )
                    .build()

            analyzer.setAnalyzer(
                cameraExecutor
            ) { imageProxy ->

                poseDetector.process(imageProxy)

            }

            val selector =
                if (isFrontCamera)
                    CameraSelector.DEFAULT_FRONT_CAMERA
                else
                    CameraSelector.DEFAULT_BACK_CAMERA

            // Sync preview and analysis to the same viewport to remove crop/scale mismatch
            val rotation = previewView.display?.rotation ?: Surface.ROTATION_0
            val viewPortAspect =
                if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270)
                    Rational(3, 4)
                else
                    Rational(4, 3)

            val viewPort = ViewPort.Builder(
                viewPortAspect,
                rotation
            )
                .setScaleType(ViewPort.FIT)
                .build()

            val useCaseGroup = UseCaseGroup.Builder()
                .addUseCase(preview)
                .addUseCase(analyzer)
                .setViewPort(viewPort)
                .build()

            cameraProvider.unbindAll()

            cameraProvider.bindToLifecycle(
                this,
                selector,
                useCaseGroup
            )

        }, ContextCompat.getMainExecutor(this))
    }

    private fun updateRecordButton() {
        syncWorkoutFragmentUi()
    }

    override fun onStartWorkoutRequested(goalText: String) {
        todayLaunchPresetKey = null
        parseGoal(goalText)
        if (allPermissionsGranted()) {
            startCountdown()
            return
        }

        pendingStartAfterPermission = true
        ActivityCompat.requestPermissions(this, requiredPermissions(), 1)
    }

    override fun onWorkoutPresetRequested(presetKey: String) {
        todayLaunchPresetKey = null
        val nextPreset = trainingPresetForKey(presetKey) ?: return
        if (nextPreset != trainingPreset) {
            applyTrainingPreset(nextPreset, recreateDetector = true)
        } else {
            syncWorkoutFragmentUi()
        }
    }

    override fun onFirstLaunchCompleted(restDaysOfWeek: Set<DayOfWeek>) {
        firstLaunchRepository.completeWithWeeklyProgram(restDaysOfWeek)
        hideFirstLaunch(immediate = false)
        progressFragment?.refresh()
    }

    override fun onToggleQualityRequested() {
        toggleSquatShadow()
    }

    override fun onToggleRecordingRequested() {
        if (recordEnabled) {
            disableRecording()
            Toast.makeText(this, "Recording disabled", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Enable recording?")
            .setMessage("AirFloat will capture the workout session and may request microphone or storage permissions when needed.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Enable") { _, _ ->
                recordEnabled = true
                updateRecordButton()
                requestScreenCapturePermission()
            }
            .show()
    }

    override fun onOpenSpotifyRequested() {
        openSpotifyPlaylist(defaultSpotifyPlaylistUri)
    }

    override fun onQuickLaunchPresetRequested(presetKey: String) {
        todayLaunchPresetKey = presetKey
        val nextPreset = trainingPresetForKey(presetKey) ?: return
        ensureWorkoutFragment().resetHubState()
        if (nextPreset != trainingPreset) {
            applyTrainingPreset(nextPreset, recreateDetector = true)
        } else {
            syncWorkoutFragmentUi()
        }
        switchRootTab(RootTab.WORKOUT)
    }

    private fun trainingPresetForKey(presetKey: String?): TrainingPreset? =
        when (presetKey) {
            WorkoutFragment.SOURCE_PRESS_BARBELL,
            HomeFragment.LAUNCH_PRESS_BARBELL -> TrainingPreset.PRESS_BARBELL
            WorkoutFragment.SOURCE_PRESS_DUMBBELL -> TrainingPreset.PRESS_DUMBBELL
            WorkoutFragment.SOURCE_PUSHUP,
            HomeFragment.LAUNCH_PUSHUP -> TrainingPreset.PUSHUP
            WorkoutFragment.SOURCE_SITUP,
            HomeFragment.LAUNCH_SITUP -> TrainingPreset.SITUP
            HomeFragment.LAUNCH_SQUAT_BETA -> TrainingPreset.SQUAT_BETA
            else -> null
        }

    private fun requestScreenCapturePermission() {
        if (projectionData != null) return
        if (screenCaptureRequested) return

        val mgr = mediaProjectionManager
        if (mgr == null) {
            disableRecording()
            Toast.makeText(this, "Screen capture not available", Toast.LENGTH_SHORT).show()
            return
        }

        screenCaptureRequested = true
        screenCaptureLauncher.launch(mgr.createScreenCaptureIntent())
    }

    private fun handleScreenCaptureResult(resultCode: Int, data: Intent?) {
        screenCaptureRequested = false
        if (resultCode == Activity.RESULT_OK && data != null) {
            projectionResultCode = resultCode
            projectionData = data
            if (pendingStartRecording) {
                pendingStartRecording = false
                startRecording()
            }
        } else {
            pendingStartRecording = false
            disableRecording()
            Toast.makeText(this, "Screen recording permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private fun disableRecording() {
        recordEnabled = false
        pendingStartRecording = false
        screenCaptureRequested = false
        projectionData = null
        projectionResultCode = null
        updateRecordButton()
        stopRecording()
    }

    private fun startRecording() {
        if (!recordEnabled) return
        val data = projectionData
        val resultCode = projectionResultCode
        if (data == null || resultCode == null) {
            pendingStartRecording = true
            requestScreenCapturePermission()
            return
        }

        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        var width = metrics.widthPixels
        var height = metrics.heightPixels

        val shortSide = min(width, height)
        val scale = min(1f, 720f / shortSide.toFloat())
        width = (width * scale).toInt()
        height = (height * scale).toInt()
        width -= width % 2
        height -= height % 2

        val rotation = previewView.display?.rotation ?: Surface.ROTATION_0

        ScreenRecordService.start(
            this,
            resultCode,
            data,
            width,
            height,
            metrics.densityDpi,
            rotation
        )
    }

    private fun stopRecording() {
        pendingStartRecording = false
        ScreenRecordService.stop(this)
    }
    private fun requiredPermissions(): Array<String> {
        val perms = mutableListOf(Manifest.permission.CAMERA)
        if (recordEnabled) {
            perms.add(Manifest.permission.RECORD_AUDIO)
        }
        if (recordEnabled && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        return perms.toTypedArray()
    }

    private fun allPermissionsGranted(): Boolean {
        val cameraOk = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        val storageOk =
            if (recordEnabled && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }

        val audioOk =
            if (recordEnabled) {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }

        return cameraOk && storageOk && audioOk
    }

    override fun onDestroy() {
        workoutFragment?.stopLogoIdleMotion()
        super.onDestroy()

        stopRecording()
        cancelBenchmark()
        stopLiveHeaderTicker()
        poseDetector.close()
        countdown?.cancel()
        cameraProviderBound?.unbindAll()

        cameraExecutor.shutdown()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            if (allPermissionsGranted()) {
                if (pendingStartAfterPermission) {
                    startCountdown()
                }
            } else {
                pendingStartAfterPermission = false
                if (recordEnabled &&
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    disableRecording()
                    Toast.makeText(
                        this,
                        "Recording disabled (storage permission denied)",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                if (recordEnabled &&
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.RECORD_AUDIO
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    disableRecording()
                    Toast.makeText(
                        this,
                        "Recording disabled (microphone permission denied)",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun handoffLiveSessionToProgress(completed: Boolean) {
        val record = persistCurrentSession(completed) ?: run {
            finishBenchmark(completed = false)
            stopRecording()
            countdown?.cancel()
            cameraProviderBound?.unbindAll()
            cameraStarted = false
            resetToStart()
            return
        }
        finishBenchmark(completed = false)
        stopRecording()
        countdown?.cancel()
        cameraProviderBound?.unbindAll()
        cameraStarted = false
        stopLiveHeaderTicker()
        setBottomNavVisible(false)

        val movement = SessionStatsCalculator.exerciseLabel(currentExerciseStatsKey()).uppercase(Locale.US)
        liveHeaderBadgeText.text = if (completed) "SESSION COMPLETE" else "PARTIAL SAVED"
        liveHeaderTitleText.text = movement
        liveHeaderMetaText.text =
            "${record.successfulAttempts + record.failedAttempts} ATTEMPTS • ${formatDuration(record.durationMs)} • ${record.estimatedKcal.toInt()} KCAL"
        liveRepCounterText.text = record.reps.toString()
        liveRepCounterLabelText.text = if (completed) "FINAL REPS" else "PARTIAL REPS"
        showLiveFeedback(
            headline = if (completed) "SESSION COMPLETE" else "PARTIAL SESSION",
            detail = "${record.reps} REPS / ${record.completionRate}% PRECISION",
            tone =
                when {
                    record.completionRate >= 90 -> LiveFeedbackTone.PERFECT
                    record.failedAttempts > 0 -> LiveFeedbackTone.MISS
                    else -> LiveFeedbackTone.CLEAN
                },
            autoHide = false
        )
        setLiveScore(
            record.completionRate,
            animate = true,
            pulse = record.completionRate >= 90
        )

        val progress = ensureProgressFragment()
        progress.openPostSessionArrival(record)
        progressContentContainer.visibility = View.VISIBLE
        progressContentContainer.alpha = 0f
        progressContentContainer.translationY = 24f.dpPx()
        progressContentContainer.scaleX = 1.05f
        progressContentContainer.scaleY = 1.05f

        val liveLayers =
            listOf(
                previewView,
                overlayView,
                hudTopScrim,
                hudBottomScrim,
                liveHeaderCard,
                liveScorePanel,
                liveRepCounterCluster,
                liveFeedbackCard,
                conditionCard,
                repGaugePanel,
                repTablePanel,
                skeletonButton,
                finishButton,
                benchButton,
                repFlashText
            )

        previewView.postDelayed({
            liveLayers.forEach { layer ->
                layer.animate().cancel()
            }
            progressContentContainer.animate().cancel()

            progressContentContainer.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(400L)
                .setInterpolator(surfaceEase)
                .start()

            previewView.animate()
                .alpha(0f)
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(400L)
                .setInterpolator(surfaceEase)
                .start()
            overlayView.animate().alpha(0f).setDuration(320L).setInterpolator(surfaceEase).start()
            hudTopScrim.animate().alpha(0f).setDuration(280L).setInterpolator(surfaceEase).start()
            hudBottomScrim.animate().alpha(0f).setDuration(280L).setInterpolator(surfaceEase).start()
            listOf(
                liveHeaderCard,
                liveScorePanel,
                liveRepCounterCluster,
                liveFeedbackCard,
                conditionCard,
                repGaugePanel,
                repTablePanel,
                skeletonButton,
                finishButton,
                benchButton,
                repFlashText
            ).forEachIndexed { index, layer ->
                layer.animate()
                    .alpha(0f)
                    .translationY((8f + index * 0.35f).dpPx())
                    .setDuration(260L)
                    .setInterpolator(surfaceEase)
                    .start()
            }

            progressContentContainer.postDelayed({
                resetSessionState(RootTab.PROGRESS)
            }, 420L)
        }, 1200L)
    }

    private fun runMockSessionCompletion() {
        if (sessionStartRealtimeMs == 0L) {
            sessionStartRealtimeMs = SystemClock.elapsedRealtime() - 42_000L
            sessionStartWallClockMs = System.currentTimeMillis() - 42_000L
        }
        goalReps = 0
        lastReps = 1
        repAttemptCounter = 4
        successAttempts = 1
        failedAttempts = 3
        sessionAttemptTimeline.clear()
        sessionAttemptTimeline +=
            listOf(
                WorkoutSessionAttemptRecord(1, 0, false, 7_000L, 1.1f, "FORM BREAK — left arm drifted early."),
                WorkoutSessionAttemptRecord(2, 0, false, 14_000L, 2.1f, "FORM BREAK — bottom position collapsed."),
                WorkoutSessionAttemptRecord(3, 0, false, 21_000L, 3.2f, "LOCKOUT MISS — rep never closed high enough."),
                WorkoutSessionAttemptRecord(4, 1, true, 34_000L, 4.8f, "PERFECT REP — rep closed clean with elite control.")
            )
        updateRepTableSummary()
        updateLiveSessionChrome()
        handoffLiveSessionToProgress(completed = true)
    }

    private fun showSuccess() {
        handoffLiveSessionToProgress(completed = true)
    }

    private fun finishSession() {
        handoffLiveSessionToProgress(completed = goalReps == 0 && lastReps > 0)
    }

    private val hideRepFlash = Runnable {
        repFlashText.animate().cancel()
        repFlashText.animate()
            .alpha(0f)
            .translationY((-18f).dpPx())
            .scaleX(0.92f)
            .scaleY(0.92f)
            .setDuration(220L)
            .setInterpolator(surfaceEase)
            .withEndAction {
                repFlashText.visibility = View.GONE
                repFlashText.translationY = 0f
            }
            .start()
    }

    private fun showRepFlash(reps: Int, perfect: Boolean = false) {
        repFlashText.text = if (perfect) "$reps ✦" else reps.toString()
        repFlashText.setTextColor(
            ContextCompat.getColor(
                this,
                if (perfect) R.color.acid_lime_400 else R.color.hud_text_primary
            )
        )
        repFlashText.visibility = View.VISIBLE
        repFlashText.alpha = 0f
        repFlashText.translationY = 22f.dpPx()
        repFlashText.scaleX = 0.72f
        repFlashText.scaleY = 0.72f

        repFlashText.animate().cancel()
        repFlashText.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(280L)
            .setInterpolator(springEase)
            .start()

        repFlashText.removeCallbacks(hideRepFlash)
        repFlashText.postDelayed(hideRepFlash, 660L)
    }

    private fun buildLiveFeedbackBackground(): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 22f.dpPx()
            setColor(liveFeedbackFillColor)
            setStroke(1.dp(), liveFeedbackStrokeColor)
        }

    private fun isLiveSessionActive(): Boolean =
        previewView.visibility == View.VISIBLE &&
            prestartPanel.visibility != View.VISIBLE &&
            successOverlay.visibility != View.VISIBLE

    private fun startLiveHeaderTicker() {
        if (!::liveHeaderMetaText.isInitialized) return
        liveHeaderMetaText.removeCallbacks(liveHeaderTicker)
        liveHeaderMetaText.post(liveHeaderTicker)
    }

    private fun stopLiveHeaderTicker() {
        if (!::liveHeaderMetaText.isInitialized) return
        liveHeaderMetaText.removeCallbacks(liveHeaderTicker)
    }

    private fun resetLiveSessionSurface() {
        liveDisplayedScore = 0
        liveMockSequenceRunning = false
        liveScoreRing.setScore(0, animate = false, pulse = false)
        liveScoreStateText.text = "CALIBRATING"
        liveRepCounterLabelText.text = "REPS"
        liveRepCounterText.text = "0"
        liveHeaderBadgeText.visibility = View.GONE
        liveHeaderBadgeText.text = ""
        liveHeaderTitleText.text = SessionStatsCalculator.exerciseLabel(currentExerciseStatsKey()).uppercase(Locale.US)
        liveHeaderMetaText.text = "00:00 • CLEAN 0 / MISS 0"
        liveFeedbackCard.removeCallbacks(hideLiveFeedback)
        liveFeedbackCard.animate().cancel()
        liveFeedbackCard.visibility = View.GONE
        liveFeedbackCard.alpha = 1f
        liveFeedbackCard.translationY = 0f
        updateLiveFeedbackTone(LiveFeedbackTone.NEUTRAL, immediate = true)
        hideLiveEventOverlay(liveCleanFlashOverlay)
        hideLiveEventOverlay(liveMissFlashOverlay)
        hideLiveEventOverlay(livePerfectFlashOverlay)
    }

    private fun hideLiveEventOverlay(view: View) {
        view.animate().cancel()
        view.alpha = 0f
        view.visibility = View.GONE
        view.scaleX = 1f
        view.scaleY = 1f
    }

    private fun updateLiveSessionChrome() {
        val elapsedMs =
            if (sessionStartRealtimeMs == 0L) 0L else (SystemClock.elapsedRealtime() - sessionStartRealtimeMs).coerceAtLeast(0L)
        val movement = SessionStatsCalculator.exerciseLabel(currentExerciseStatsKey()).uppercase(Locale.US)
        liveHeaderBadgeText.visibility = View.GONE
        liveHeaderTitleText.text = movement
        liveHeaderMetaText.text =
            "${formatDuration(elapsedMs)} • CLEAN $successAttempts / MISS $failedAttempts"
        liveRepCounterLabelText.text = "REPS"
        liveRepCounterText.text = lastReps.toString()
    }

    private fun setLiveScore(score: Int, animate: Boolean = true, pulse: Boolean = false) {
        liveDisplayedScore = score.coerceIn(0, 100)
        liveScoreRing.setScore(liveDisplayedScore, animate = animate, pulse = pulse)
        updateLiveSessionChrome()
    }

    private fun updateLiveFeedbackTone(tone: LiveFeedbackTone, immediate: Boolean = false) {
        val targetFill =
            when (tone) {
                LiveFeedbackTone.NEUTRAL -> 0xD6151A1E.toInt()
                LiveFeedbackTone.CLEAN -> 0xD5131C19.toInt()
                LiveFeedbackTone.MISS -> 0xDA231316.toInt()
                LiveFeedbackTone.PERFECT -> 0xD71E2511.toInt()
            }
        val targetStroke =
            when (tone) {
                LiveFeedbackTone.NEUTRAL -> 0x33FFFFFF
                LiveFeedbackTone.CLEAN -> 0x6647FFB2
                LiveFeedbackTone.MISS -> 0x88FF4D4D.toInt()
                LiveFeedbackTone.PERFECT -> 0x99E8FF47.toInt()
            }

        liveFeedbackAnimator?.cancel()
        val background = liveFeedbackCard.background as? GradientDrawable ?: return
        if (immediate) {
            liveFeedbackFillColor = targetFill
            liveFeedbackStrokeColor = targetStroke
            background.setColor(targetFill)
            background.setStroke(1.dp(), targetStroke)
            return
        }

        val startFill = liveFeedbackFillColor
        val startStroke = liveFeedbackStrokeColor
        liveFeedbackAnimator =
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 300L
                interpolator = surfaceEase
                addUpdateListener { animator ->
                    val t = animator.animatedFraction
                    val fill = blendArgb(startFill, targetFill, t)
                    val stroke = blendArgb(startStroke, targetStroke, t)
                    liveFeedbackFillColor = fill
                    liveFeedbackStrokeColor = stroke
                    background.setColor(fill)
                    background.setStroke(1.dp(), stroke)
                }
                start()
            }
    }

    private fun blendArgb(start: Int, end: Int, fraction: Float): Int {
        val t = fraction.coerceIn(0f, 1f)
        val a = ((start ushr 24) + (((end ushr 24) - (start ushr 24)) * t)).toInt()
        val r = (((start shr 16) and 0xFF) + ((((end shr 16) and 0xFF) - ((start shr 16) and 0xFF)) * t)).toInt()
        val g = (((start shr 8) and 0xFF) + ((((end shr 8) and 0xFF) - ((start shr 8) and 0xFF)) * t)).toInt()
        val b = ((start and 0xFF) + (((end and 0xFF) - (start and 0xFF)) * t)).toInt()
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun showLiveFeedback(
        headline: String,
        detail: String,
        tone: LiveFeedbackTone,
        autoHide: Boolean = true
    ) {
        updateLiveFeedbackTone(tone)
        liveFeedbackText.text = headline
        liveFeedbackMetaText.text = detail
        liveFeedbackCard.removeCallbacks(hideLiveFeedback)
        liveFeedbackCard.animate().cancel()
        liveFeedbackCard.visibility = View.VISIBLE
        liveFeedbackCard.alpha = 0f
        liveFeedbackCard.translationY = (-12f).dpPx()
        liveFeedbackCard.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(220L)
            .setInterpolator(surfaceEase)
            .start()
        if (autoHide) {
            liveFeedbackCard.postDelayed(hideLiveFeedback, if (tone == LiveFeedbackTone.MISS) 1700L else 1300L)
        }
    }

    private fun pulseLiveEventOverlay(view: View, peakAlpha: Float, durationMs: Long, scaleBoost: Float = 0.04f) {
        view.animate().cancel()
        view.visibility = View.VISIBLE
        view.alpha = 0f
        view.scaleX = 1f
        view.scaleY = 1f
        view.animate()
            .alpha(peakAlpha)
            .scaleX(1f + scaleBoost)
            .scaleY(1f + scaleBoost)
            .setDuration((durationMs * 0.45f).toLong())
            .setInterpolator(surfaceEase)
            .withEndAction {
                view.animate()
                    .alpha(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration((durationMs * 0.55f).toLong())
                    .setInterpolator(surfaceEase)
                    .withEndAction {
                        view.visibility = View.GONE
                    }
                    .start()
            }
            .start()
    }

    private fun buildMissFeedback(reason: RepRejectReason): Pair<String, String> =
        when (reason) {
            RepRejectReason.INVALID_BOTTOM_ANGLE -> "FORM BREAK" to "Bottom shape drifted out of stack."
            RepRejectReason.INSUFFICIENT_TOP -> "LOCKOUT MISS" to "Finish the rep higher before counting it."
            RepRejectReason.TOO_FAST -> "TEMPO MISS" to "Slow the cycle down and own the transition."
            RepRejectReason.ASYMMETRIC_RANGE -> "ASYMMETRY" to "Left and right travel no longer matched."
            RepRejectReason.ASYNC_ARMS -> "SYNC BREAK" to "Arms stopped moving together through the rep."
            RepRejectReason.TRACKING_LOST -> "TRACK LOST" to "Camera lost the line. Re-center and go again."
        }

    private fun missScoreForReason(reason: RepRejectReason): Int =
        when (reason) {
            RepRejectReason.INSUFFICIENT_TOP -> 56
            RepRejectReason.TOO_FAST -> 44
            RepRejectReason.ASYMMETRIC_RANGE -> 38
            RepRejectReason.ASYNC_ARMS -> 34
            RepRejectReason.INVALID_BOTTOM_ANGLE -> 41
            RepRejectReason.TRACKING_LOST -> 22
        }

    private fun shouldTreatSuccessfulRepAsPerfect(): Boolean {
        if (failedAttempts > 0) return false
        if (successAttempts < 2) return false
        val sinceReject = SystemClock.uptimeMillis() - lastRepRejectAtMs
        return sinceReject > 4_000L
    }

    private fun cleanRepScore(perfect: Boolean): Int {
        if (perfect) return 94
        val attempts = (successAttempts + failedAttempts).coerceAtLeast(1)
        val precision = (successAttempts * 100f / attempts.toFloat()).toInt()
        return precision.coerceIn(78, 89)
    }

    private fun handleSuccessfulRep(reps: Int, perfect: Boolean = shouldTreatSuccessfulRepAsPerfect()) {
        showRepFlash(reps, perfect = perfect)
        pulseLiveEventOverlay(liveCleanFlashOverlay, peakAlpha = 0.22f, durationMs = 200L)
        if (perfect) {
            pulseLiveEventOverlay(livePerfectFlashOverlay, peakAlpha = 0.12f, durationMs = 240L, scaleBoost = 0.07f)
        }
        showLiveFeedback(
            headline = if (perfect) "PERFECT REP" else "CLEAN REP",
            detail = if (perfect) "Arc stayed locked. Counter sealed with premium closure." else "Rep closed clean. Keep the same bar path and tempo.",
            tone = if (perfect) LiveFeedbackTone.PERFECT else LiveFeedbackTone.CLEAN
        )
        setLiveScore(cleanRepScore(perfect), animate = true, pulse = perfect)
    }

    private fun handleRejectedRep(reason: RepRejectReason) {
        val (headline, detail) = buildMissFeedback(reason)
        pulseLiveEventOverlay(liveMissFlashOverlay, peakAlpha = 0.26f, durationMs = 200L)
        showLiveFeedback(
            headline = headline,
            detail = detail,
            tone = LiveFeedbackTone.MISS
        )
        setLiveScore(missScoreForReason(reason), animate = true, pulse = false)
    }

    private fun playLiveSurfaceMockSequence() {
        if (liveMockSequenceRunning) return
        liveMockSequenceRunning = true
        val restoreScore = liveDisplayedScore
        val mockRepValue = (lastReps + 1).coerceAtLeast(1)
        val steps =
            listOf(
                0L to { handleRejectedRep(RepRejectReason.ASYNC_ARMS) },
                360L to { handleRejectedRep(RepRejectReason.INVALID_BOTTOM_ANGLE) },
                720L to { handleRejectedRep(RepRejectReason.INSUFFICIENT_TOP) },
                1140L to { showRepFlash(mockRepValue, perfect = true); pulseLiveEventOverlay(liveCleanFlashOverlay, 0.22f, 200L); pulseLiveEventOverlay(livePerfectFlashOverlay, 0.12f, 240L, 0.07f); showLiveFeedback("PERFECT REP", "Mock pass: score 94. This is the premium success beat.", LiveFeedbackTone.PERFECT); setLiveScore(94, animate = true, pulse = true) },
                1880L to {
                    liveMockSequenceRunning = false
                    setLiveScore(restoreScore, animate = true, pulse = false)
                    updateLiveSessionChrome()
                }
            )
        steps.forEach { (delay, block) ->
            liveScorePanel.postDelayed(block, delay)
        }
        Toast.makeText(this, "Live mock: 3 misses + 1 perfect rep", Toast.LENGTH_SHORT).show()
    }

    private fun startBenchmark() {
        if (benchActive) return

        benchActive = true
        benchFrames = 0
        benchLatencySum = 0f
        benchStartMs = SystemClock.uptimeMillis()
        benchButton.isEnabled = false
        benchButton.text = "Bench 30s"

        benchTimer?.cancel()
        benchTimer = object : CountDownTimer(30_000L, 1_000L) {
            override fun onTick(millisUntilFinished: Long) {
                val leftSec = (millisUntilFinished / 1000L).toInt()
                benchButton.text = "Bench ${leftSec}s"
            }

            override fun onFinish() {
                finishBenchmark(completed = true)
            }
        }.start()

        Toast.makeText(this, "Benchmark started for 30 seconds", Toast.LENGTH_SHORT).show()
    }

    private fun finishBenchmark(completed: Boolean) {
        if (!benchActive) {
            cancelBenchmark()
            return
        }

        val elapsedMs = (SystemClock.uptimeMillis() - benchStartMs).coerceAtLeast(1L)
        val avgFps = benchFrames * 1000f / elapsedMs.toFloat()
        val avgLat = if (benchFrames > 0) benchLatencySum / benchFrames else 0f
        val statusSuffix = if (completed) "" else " (stopped)"

        Log.i(
            "AirFloatBench",
            "Bench MediaPipe: frames=$benchFrames avgFps=${fmt1(avgFps)} avgLat=${fmt1(avgLat)}ms$statusSuffix"
        )

        cancelBenchmark()

        if (completed) {
            Toast.makeText(this, "Bench finished. Check AirFloatBench in Logcat", Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun cancelBenchmark() {
        benchActive = false
        benchTimer?.cancel()
        benchTimer = null
        benchFrames = 0
        benchLatencySum = 0f
        benchStartMs = 0L
        resetBenchButtonUi()
    }

    private fun resetBenchButtonUi() {
        if (!::benchButton.isInitialized) return
        benchButton.text = "Bench 30s"
        benchButton.isEnabled = true
    }

    private fun fmt1(value: Float?): String {
        if (value == null) return "--"
        if (!value.isFinite()) return "NaN"
        return String.format(Locale.US, "%.1f", value)
    }

    private fun updateConditionBanner(code: ConditionCode) {
        if (code == ConditionCode.TRACKING_LOST) {
            showConditionImmediately(code)
            return
        }

        if (pendingCondition != code) {
            pendingCondition = code
            pendingConditionFrames = 1
        } else {
            pendingConditionFrames += 1
        }

        val requiredFrames =
            if (code == ConditionCode.OK) okConditionFrames else nonCriticalConditionFrames
        if (pendingConditionFrames >= requiredFrames && shownCondition != code) {
            showConditionImmediately(code)
        }
    }

    private fun showConditionImmediately(code: ConditionCode) {
        pendingCondition = null
        pendingConditionFrames = 0
        shownCondition = code

        if (exerciseMode == ExerciseMode.SQUAT_BETA) {
            when (code) {
                ConditionCode.OK -> {
                    applyConditionUi(
                        title = "Squat tracking active",
                        hint = "Control depth and keep knees stable.",
                        color = bannerColorOk
                    )
                }
                ConditionCode.TRACKING_LOST -> {
                    applyConditionUi(
                        title = "Tracking lost",
                        hint = "Keep full body in frame (hips, knees, ankles).",
                        color = bannerColorBad
                    )
                }
                ConditionCode.BAD_START -> {
                    applyConditionUi(
                        title = "Start squat stance",
                        hint = "Stand upright, feet shoulder-width.",
                        color = bannerColorWarn
                    )
                }
                ConditionCode.RANGE_TOO_SMALL -> {
                    applyConditionUi(
                        title = "Depth is too small",
                        hint = "Go deeper while keeping heels down.",
                        color = bannerColorWarn
                    )
                }
            }
            return
        }

        if (exerciseMode == ExerciseMode.PUSHUP) {
            when (code) {
                ConditionCode.OK -> {
                    applyConditionUi(
                        title = "Push-up tracking active",
                        hint = "Keep body line stable and full range.",
                        color = bannerColorOk
                    )
                }
                ConditionCode.TRACKING_LOST -> {
                    applyConditionUi(
                        title = "Tracking lost",
                        hint = "Keep shoulders, elbows, wrists and hips in frame.",
                        color = bannerColorBad
                    )
                }
                ConditionCode.BAD_START -> {
                    applyConditionUi(
                        title = "Start push-up stance",
                        hint = "Take plank position and keep elbows visible.",
                        color = bannerColorWarn
                    )
                }
                ConditionCode.RANGE_TOO_SMALL -> {
                    applyConditionUi(
                        title = "Range is too small",
                        hint = "Go lower and press up fully.",
                        color = bannerColorWarn
                    )
                }
            }
            return
        }

        if (exerciseMode == ExerciseMode.SITUP) {
            when (code) {
                ConditionCode.OK -> {
                    applyConditionUi(
                        title = "Sit-up tracking active",
                        hint = "Full down-up motion, keep shoulders/hips/knees visible.",
                        color = bannerColorOk
                    )
                }
                ConditionCode.TRACKING_LOST -> {
                    applyConditionUi(
                        title = "Tracking lost",
                        hint = "Keep head, shoulders, hips and knees in frame.",
                        color = bannerColorBad
                    )
                }
                ConditionCode.BAD_START -> {
                    applyConditionUi(
                        title = "Start sit-up stance",
                        hint = "Lie down and keep knees visible.",
                        color = bannerColorWarn
                    )
                }
                ConditionCode.RANGE_TOO_SMALL -> {
                    applyConditionUi(
                        title = "Range is too small",
                        hint = "Lift torso higher and return fully down.",
                        color = bannerColorWarn
                    )
                }
            }
            return
        }

        when (code) {
            ConditionCode.OK -> {
                val stickyHint = resolveStickyReasonHint()
                applyConditionUi(
                    title = "Technique is good",
                    hint = stickyHint ?: "Keep this tempo and full range.",
                    color = bannerColorOk
                )
            }
            ConditionCode.TRACKING_LOST -> {
                applyConditionUi(
                    title = "Tracking lost",
                    hint = "Step back and keep both arms visible.",
                    color = bannerColorBad
                )
            }
            ConditionCode.BAD_START -> {
                applyConditionUi(
                    title = "Start position required",
                    hint = "Raise both hands above shoulder level.",
                    color = bannerColorWarn
                )
            }
            ConditionCode.RANGE_TOO_SMALL -> {
                applyConditionUi(
                    title = "Range is too small",
                    hint = "Go lower and press up fully.",
                    color = bannerColorWarn
                )
            }
        }
    }

    private fun applyConditionUi(title: String, hint: String, color: Int) {
        activeBanner.text = title
        activeBanner.setTextColor(bannerColorTitle)
        conditionHintText.text = hint
        conditionHintText.setTextColor(bannerColorNeutral)
        conditionDot.backgroundTintList = ColorStateList.valueOf(color)
    }

    private fun onReasonEvent(code: ConditionCode) {
        if (code == ConditionCode.OK) return
        lastReasonEvent = code
        lastReasonEventAtMs = SystemClock.uptimeMillis()
    }

    private fun onRepRejectEvent(reason: RepRejectReason) {
        lastRepRejectReason = reason
        lastRepRejectAtMs = SystemClock.uptimeMillis()
    }

    private fun scheduleRejectedAttempt(reason: RepRejectReason) {
        // Tracking gaps are session-state issues, not failed rep attempts.
        if (reason == RepRejectReason.TRACKING_LOST) return
        val now = SystemClock.uptimeMillis()
        if (
            reason == lastFailedLogReason &&
            lastReps == lastFailedLogRepSnapshot &&
            now - lastFailedLogAtMs < failedLogCooldownMs
        ) {
            return
        }
        lastFailedLogReason = reason
        lastFailedLogRepSnapshot = lastReps
        lastFailedLogAtMs = now
        addAttemptLogRow(success = false)
        handleRejectedRep(reason)
    }

    private fun resetFailedRejectState() {
        lastFailedLogReason = null
        lastFailedLogRepSnapshot = -1
        lastFailedLogAtMs = 0L
    }

    private fun buildAttemptTelemetryDetail(success: Boolean): String {
        if (success) return "Counted clean with stable closure."
        val reason = lastRepRejectReason ?: return "Attempt missed. Cleanup or range drift detected."
        return when (exerciseMode) {
            ExerciseMode.PRESS ->
                when (reason) {
                    RepRejectReason.INVALID_BOTTOM_ANGLE -> "Missed rep: unstable bottom position."
                    RepRejectReason.INSUFFICIENT_TOP -> "Missed rep: lockout was not high enough."
                    RepRejectReason.TOO_FAST -> "Missed rep: tempo was too fast."
                    RepRejectReason.ASYMMETRIC_RANGE -> "Missed rep: left/right range drifted."
                    RepRejectReason.ASYNC_ARMS -> "Missed rep: arms moved out of sync."
                    RepRejectReason.TRACKING_LOST -> "Missed rep: tracking was lost."
                }
            ExerciseMode.SQUAT_BETA ->
                when (reason) {
                    RepRejectReason.INSUFFICIENT_TOP -> "Missed rep: go deeper before driving up."
                    RepRejectReason.TOO_FAST -> "Missed rep: squat cadence was too fast."
                    RepRejectReason.ASYMMETRIC_RANGE -> "Missed rep: depth mismatch left to right."
                    RepRejectReason.INVALID_BOTTOM_ANGLE -> "Missed rep: bottom shape was unstable."
                    RepRejectReason.ASYNC_ARMS -> "Missed rep: movement stayed uneven."
                    RepRejectReason.TRACKING_LOST -> "Missed rep: tracking was lost."
                }
            ExerciseMode.PUSHUP ->
                when (reason) {
                    RepRejectReason.INSUFFICIENT_TOP -> "Missed rep: chest did not travel low enough."
                    RepRejectReason.TOO_FAST -> "Missed rep: push-up was too fast."
                    RepRejectReason.ASYMMETRIC_RANGE -> "Missed rep: left/right depth mismatch."
                    RepRejectReason.INVALID_BOTTOM_ANGLE -> "Missed rep: bottom shape was unstable."
                    RepRejectReason.ASYNC_ARMS -> "Missed rep: elbows moved out of sync."
                    RepRejectReason.TRACKING_LOST -> "Missed rep: tracking was lost."
                }
            ExerciseMode.SITUP ->
                when (reason) {
                    RepRejectReason.INSUFFICIENT_TOP -> "Missed rep: torso did not return high enough."
                    RepRejectReason.TOO_FAST -> "Missed rep: sit-up tempo was too fast."
                    RepRejectReason.ASYMMETRIC_RANGE -> "Missed rep: travel mismatch left to right."
                    RepRejectReason.INVALID_BOTTOM_ANGLE -> "Missed rep: range looked unstable."
                    RepRejectReason.ASYNC_ARMS -> "Missed rep: movement stayed uneven."
                    RepRejectReason.TRACKING_LOST -> "Missed rep: tracking was lost."
                }
        }
    }

    private fun resolveStickyReasonHint(): String? {
        val code = lastReasonEvent ?: return null
        val ageMs = SystemClock.uptimeMillis() - lastReasonEventAtMs
        if (ageMs > reasonEventHoldMs) return null
        return when (code) {
            ConditionCode.TRACKING_LOST -> "Last issue: tracking was lost."
            ConditionCode.BAD_START -> "Last issue: start position was not ready."
            ConditionCode.RANGE_TOO_SMALL -> "Last issue: range was too small."
            ConditionCode.OK -> null
        }
    }

    private fun updateRepGauge(progress: Float) {
        val p = progress.coerceIn(0f, 1f)
        if (!repGaugePanel.isShown) return
        val trackH = repGaugeTrack.height.toFloat()
        val markerH = repGaugeMarker.height.toFloat()
        if (trackH <= 1f || markerH <= 0f) {
            repGaugeTrack.post { updateRepGauge(p) }
            return
        }
        val travel = (trackH - markerH).coerceAtLeast(0f)
        // Marker is bottom-anchored in layout; move it up with negative translation.
        repGaugeMarker.translationY = -travel * p
    }

    private fun updateRepGaugeHint() {
        val reason = lastRepRejectReason
        val age = SystemClock.uptimeMillis() - lastRepRejectAtMs
        if (reason != null && age <= repRejectHoldMs) {
            repGaugeHintText.text = when (reason) {
                RepRejectReason.INVALID_BOTTOM_ANGLE -> "Not counted: unstable bottom position."
                RepRejectReason.INSUFFICIENT_TOP -> "Not counted: raise higher at the top."
                RepRejectReason.TOO_FAST -> "Not counted: movement is too fast."
                RepRejectReason.ASYMMETRIC_RANGE -> "Not counted: left/right range mismatch."
                RepRejectReason.ASYNC_ARMS -> "Not counted: arms moved out of sync."
                RepRejectReason.TRACKING_LOST -> "Not counted: tracking was lost."
            }
            return
        }
        repGaugeHintText.text = "Progress down -> up"
    }

    private fun updateSquatGaugeHint(progress: Float?) {
        val reason = lastRepRejectReason
        val age = SystemClock.uptimeMillis() - lastRepRejectAtMs
        if (reason != null && age <= repRejectHoldMs) {
            repGaugeHintText.text = when (reason) {
                RepRejectReason.INSUFFICIENT_TOP -> "Not counted: go deeper."
                RepRejectReason.TOO_FAST -> "Not counted: movement is too fast."
                RepRejectReason.ASYMMETRIC_RANGE -> "Not counted: left/right depth mismatch."
                RepRejectReason.INVALID_BOTTOM_ANGLE -> "Not counted: unstable bottom position."
                RepRejectReason.ASYNC_ARMS -> "Not counted: uneven movement."
                RepRejectReason.TRACKING_LOST -> "Not counted: tracking was lost."
            }
            return
        }

        repGaugeHintText.text =
            if (progress == null) {
                "Squat depth: keep hips/knees/ankles in frame."
            } else {
                "Squat depth: ${fmt1(progress * 100f)}%"
            }
    }

    private fun updatePushupGaugeHint(progress: Float?) {
        val reason = lastRepRejectReason
        val age = SystemClock.uptimeMillis() - lastRepRejectAtMs
        if (reason != null && age <= repRejectHoldMs) {
            repGaugeHintText.text = when (reason) {
                RepRejectReason.INSUFFICIENT_TOP -> "Not counted: go lower."
                RepRejectReason.TOO_FAST -> "Not counted: movement is too fast."
                RepRejectReason.ASYMMETRIC_RANGE -> "Not counted: left/right depth mismatch."
                RepRejectReason.INVALID_BOTTOM_ANGLE -> "Not counted: unstable bottom position."
                RepRejectReason.ASYNC_ARMS -> "Not counted: elbows moved out of sync."
                RepRejectReason.TRACKING_LOST -> "Not counted: tracking was lost."
            }
            return
        }

        repGaugeHintText.text =
            if (progress == null) {
                "Push-up depth: keep body line and full range."
            } else {
                "Push-up depth: ${fmt1(progress * 100f)}%"
            }
    }

    private fun updateSitupGaugeHint(progress: Float?) {
        val reason = lastRepRejectReason
        val age = SystemClock.uptimeMillis() - lastRepRejectAtMs
        if (reason != null && age <= repRejectHoldMs) {
            repGaugeHintText.text = when (reason) {
                RepRejectReason.INSUFFICIENT_TOP -> "Not counted: lift torso higher."
                RepRejectReason.TOO_FAST -> "Not counted: movement is too fast."
                RepRejectReason.ASYMMETRIC_RANGE -> "Not counted: left/right travel mismatch."
                RepRejectReason.INVALID_BOTTOM_ANGLE -> "Not counted: unstable range."
                RepRejectReason.ASYNC_ARMS -> "Not counted: uneven movement."
                RepRejectReason.TRACKING_LOST -> "Not counted: tracking was lost."
            }
            return
        }

        repGaugeHintText.text =
            if (progress == null) {
                "Sit-up range: down -> up"
            } else {
                "Sit-up range: ${fmt1(progress * 100f)}%"
            }
    }

    private fun configureGaugeUiForExercise() {
        if (!::repGaugeTitleText.isInitialized) return
        repTotalsText.visibility = View.VISIBLE
        when (exerciseMode) {
            ExerciseMode.SQUAT_BETA -> {
                repGaugeTitleText.text = "DEPTH"
                repTableTitle.text = "ATTEMPTS"
                updateSquatGaugeHint(null)
            }
            ExerciseMode.PUSHUP -> {
                repGaugeTitleText.text = "DEPTH"
                repTableTitle.text = "ATTEMPTS"
                updatePushupGaugeHint(null)
            }
            ExerciseMode.SITUP -> {
                repGaugeTitleText.text = "RANGE"
                repTableTitle.text = "ATTEMPTS"
                updateSitupGaugeHint(null)
            }
            ExerciseMode.PRESS -> {
                repGaugeTitleText.text = "ARC"
                repTableTitle.text = "ATTEMPTS"
                updateRepGaugeHint()
            }
        }
        updateRepTableSummary()
    }

    private fun startRepTableSession() {
        repAttemptCounter = 0
        successAttempts = 0
        failedAttempts = 0
        sessionAttemptTimeline.clear()
        sessionStartRealtimeMs = SystemClock.elapsedRealtime()
        sessionStartWallClockMs = System.currentTimeMillis()
        sessionPersisted = false
        lastPersistedSessionRecord = null
        progressTodayWriteBack = null
        resetFailedRejectState()
        repTableRows.removeAllViews()
        updateRepTableSummary()
    }

    private fun addAttemptLogRow(success: Boolean) {
        if (sessionStartRealtimeMs == 0L) {
            sessionStartRealtimeMs = SystemClock.elapsedRealtime()
        }

        repAttemptCounter += 1
        if (success) successAttempts += 1 else failedAttempts += 1
        val kcal = estimateKcalSinceSessionStart()
        val textColor = 0xFFF0EDE8.toInt()
        val secondaryTextColor = 0xB8F0EDE8.toInt()
        val accentColor = if (success) 0xFF47FFB2.toInt() else 0xFFFF4D4D.toInt()
        val statusLabel = if (success) "CLEAN" else "MISS"
        val elapsedMs = (SystemClock.elapsedRealtime() - sessionStartRealtimeMs).coerceAtLeast(0L)
        val detail = buildAttemptTelemetryDetail(success)

        sessionAttemptTimeline +=
            WorkoutSessionAttemptRecord(
                index = repAttemptCounter,
                repSnapshot = lastReps,
                success = success,
                elapsedMs = elapsedMs,
                estimatedKcal = kcal,
                detail = detail
            )

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 6.dp()
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 14f.dpPx()
                setColor(0x9912161A.toInt())
                setStroke(1.dp(), 0x2EFFFFFF)
            }
            setPadding(8.dp(), 8.dp(), 8.dp(), 8.dp())
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val chip = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 8f.dpPx()
                setColor(if (success) 0x1F47FFB2 else 0x22FF4D4D)
                setStroke(1.dp(), accentColor)
            }
            setPadding(6.dp(), 3.dp(), 6.dp(), 3.dp())
            text = statusLabel
            setTextColor(accentColor)
            textSize = 9f
            typeface = android.graphics.Typeface.MONOSPACE
        }

        val repText = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = 6.dp()
                weight = 1f
            }
            text = "ATTEMPT ${repAttemptCounter.toString().padStart(2, '0')}"
            setTextColor(textColor)
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
        }

        val kcalText = TextView(this).apply {
            text = String.format(Locale.US, "%.1fKCAL", kcal)
            setTextColor(secondaryTextColor)
            textSize = 10f
            typeface = android.graphics.Typeface.MONOSPACE
        }

        val metaRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 6.dp()
            }
        }

        val elapsedText = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                weight = 1f
            }
            text = formatDuration(elapsedMs)
            setTextColor(textColor)
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
        }

        val qualityHintText = TextView(this).apply {
            text = if (success) "closed clean" else "reset form"
            setTextColor(accentColor)
            textSize = 10f
            typeface = android.graphics.Typeface.MONOSPACE
        }

        topRow.addView(chip)
        topRow.addView(repText)
        topRow.addView(kcalText)

        metaRow.addView(elapsedText)
        metaRow.addView(qualityHintText)

        row.addView(topRow)
        row.addView(metaRow)
        repTableRows.addView(row)
        updateRepTableSummary()
        updateLiveSessionChrome()

        repTableScroll.post { repTableScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun updateRepTableSummary() {
        if (!::repTotalsText.isInitialized) return
        repTotalsText.text = "$successAttempts CLEAN · $failedAttempts MISS"
    }

    private fun estimateKcalSinceSessionStart(): Float {
        val elapsedMs = (SystemClock.elapsedRealtime() - sessionStartRealtimeMs).coerceAtLeast(0L)
        val minutes = elapsedMs / 60_000f
        val met = when (exerciseMode) {
            ExerciseMode.PUSHUP -> 8.0f
            ExerciseMode.SQUAT_BETA -> 6.5f
            ExerciseMode.SITUP -> 5.5f
            ExerciseMode.PRESS ->
                when (movementProfile) {
                    MovementProfile.NO_WEIGHT -> 6.0f
                    MovementProfile.DUMBBELL_SIM -> 6.5f
                }
        }
        return met * 3.5f * defaultBodyWeightKg / 200f * minutes
    }

    private fun formatDuration(durationMs: Long): String {
        val minutes = (durationMs / 60_000L).toInt()
        val seconds = ((durationMs % 60_000L) / 1000L).toInt()
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    private fun persistCurrentSession(completed: Boolean): WorkoutSessionRecord? {
        if (sessionPersisted) return lastPersistedSessionRecord
        if (sessionStartRealtimeMs == 0L) return null
        if (repAttemptCounter == 0 && lastReps == 0) return null

        val attemptCount = (successAttempts + failedAttempts).coerceAtLeast(1)
        val completionRate = ((successAttempts * 100f) / attemptCount).toInt().coerceIn(0, 100)
        val record =
            WorkoutSessionRecord(
                id = "${sessionStartWallClockMs}_${currentExerciseStatsKey()}",
                timestampMs = sessionStartWallClockMs.takeIf { it > 0L } ?: System.currentTimeMillis(),
                exerciseKey = currentExerciseStatsKey(),
                presetKey = currentPresetStatsKey(),
                goalReps = goalReps,
                completed = completed,
                reps = lastReps,
                successfulAttempts = successAttempts,
                failedAttempts = failedAttempts,
                durationMs = (SystemClock.elapsedRealtime() - sessionStartRealtimeMs).coerceAtLeast(0L),
                estimatedKcal = estimateKcalSinceSessionStart(),
                completionRate = completionRate,
                attempts = sessionAttemptTimeline.toList()
            )
        sessionStatsRepository.saveSession(record)
        sessionPersisted = true
        lastPersistedSessionRecord = record
        progressFragment?.refresh()
        return record
    }

    private fun currentExerciseStatsKey(): String =
        when (exerciseMode) {
            ExerciseMode.PRESS ->
                when (trainingPreset) {
                    TrainingPreset.PRESS_DUMBBELL -> "press_dumbbell"
                    else -> "press_barbell"
                }
            ExerciseMode.SQUAT_BETA -> "squat_beta"
            ExerciseMode.PUSHUP -> "pushup"
            ExerciseMode.SITUP -> "situp"
        }

    private fun currentPresetStatsKey(): String =
        when (trainingPreset) {
            TrainingPreset.PRESS_BARBELL -> "press_barbell"
            TrainingPreset.PRESS_DUMBBELL -> "press_dumbbell"
            TrainingPreset.SQUAT_BETA -> "squat_beta"
            TrainingPreset.PUSHUP -> "pushup"
            TrainingPreset.SITUP -> "situp"
        }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    private fun Float.dpPx(): Float = this * resources.displayMetrics.density

    private fun runExerciseQuality(frame: com.airfloat.app.pose.FrameResult) {
        if (!qualityUiEnabled) return
        when (exerciseMode) {
            ExerciseMode.SQUAT_BETA -> runSquatShadow(frame)
            ExerciseMode.PUSHUP -> runPushupQuality(frame)
            ExerciseMode.SITUP -> runSitupQuality(frame)
            ExerciseMode.PRESS -> Unit
        }
    }

    private fun runSquatShadow(frame: com.airfloat.app.pose.FrameResult) {
        if (exerciseMode != ExerciseMode.SQUAT_BETA) return
        if (!squatShadowEnabled) return
        val classifier = squatShadowClassifier ?: return
        val squatState = lastSquatCounterResult
        if (squatState == null) {
            showSquatShadowUnknown()
            return
        }
        val now = SystemClock.uptimeMillis()
        val tooIdleForQuality = !squatState.isCycleActive && (now - squatState.lastRepAtMs > 900L)
        val mlPoints = if (frame.rawNormPoints.isNotEmpty()) frame.rawNormPoints else frame.normPoints
        if (mlPoints.isEmpty() || frame.conditionCode != ConditionCode.OK || tooIdleForQuality) {
            showSquatShadowUnknown()
            return
        }
        val prediction = classifier.predict(mlPoints)
        if (prediction == null) {
            showSquatShadowUnknown()
            return
        }

        val qualityText =
            when (prediction.quality) {
                SquatQualityLabel.GOOD -> "GOOD"
                SquatQualityLabel.BAD -> "BAD"
                SquatQualityLabel.UNKNOWN -> "UNKNOWN"
            }
        val qualityColor =
            when (prediction.quality) {
                SquatQualityLabel.GOOD -> bannerColorOk
                SquatQualityLabel.BAD -> bannerColorBad
                SquatQualityLabel.UNKNOWN -> bannerColorWarn
            }

        squatShadowClassText.text = "FORM $qualityText"
        squatShadowClassText.setTextColor(qualityColor)
        squatShadowConfidenceText.text =
            "BAD ${fmt1(prediction.badScoreSmoothed * 100f)}% · CONF ${fmt1(prediction.confidence * 100f)}%"

        val shouldLog =
            prediction.quality != lastSquatShadowQuality ||
                now - lastSquatShadowLogAtMs >= squatShadowLogIntervalMs

        if (!shouldLog) return

        lastSquatShadowQuality = prediction.quality
        lastSquatShadowLogAtMs = now
        Log.i(
            "AirFloatSquat",
            "shadow quality=${prediction.qualityName} " +
                "badRaw=${fmt1(prediction.badScoreRaw * 100f)}% badSmoothed=${fmt1(prediction.badScoreSmoothed * 100f)}% " +
                "conf=${fmt1(prediction.confidence * 100f)}% reps=$lastReps cond=${frame.conditionCode}"
        )
    }

    private fun showSquatShadowUnknown() {
        squatShadowClassText.text = "FORM UNKNOWN"
        squatShadowClassText.setTextColor(bannerColorWarn)
        squatShadowConfidenceText.text = "BAD -- · CONF --"
    }

    private fun runPushupQuality(frame: com.airfloat.app.pose.FrameResult) {
        if (exerciseMode != ExerciseMode.PUSHUP) return
        if (!squatShadowEnabled) return
        val classifier = pushupClassifier ?: return
        val pushupState = lastPushupCounterResult
        if (pushupState == null) {
            showPushupQualityUnknown()
            return
        }
        val now = SystemClock.uptimeMillis()
        val tooIdleForQuality = !pushupState.isCycleActive && (now - pushupState.lastRepAtMs > 900L)
        val mlPoints = if (frame.rawNormPoints.isNotEmpty()) frame.rawNormPoints else frame.normPoints
        if (mlPoints.isEmpty() || frame.conditionCode != ConditionCode.OK || tooIdleForQuality) {
            showPushupQualityUnknown()
            return
        }
        val prediction = classifier.predict(mlPoints)
        if (prediction == null) {
            showPushupQualityUnknown()
            return
        }

        val qualityText =
            when (prediction.quality) {
                PushupQualityLabel.GOOD -> "GOOD"
                PushupQualityLabel.BAD -> "BAD"
                PushupQualityLabel.UNKNOWN -> "UNKNOWN"
            }
        val qualityColor =
            when (prediction.quality) {
                PushupQualityLabel.GOOD -> bannerColorOk
                PushupQualityLabel.BAD -> bannerColorBad
                PushupQualityLabel.UNKNOWN -> bannerColorWarn
            }

        squatShadowClassText.text = "FORM $qualityText"
        squatShadowClassText.setTextColor(qualityColor)
        squatShadowConfidenceText.text =
            "BAD ${fmt1(prediction.badScoreSmoothed * 100f)}% · CONF ${fmt1(prediction.confidence * 100f)}%"

        val shouldLog =
            prediction.quality != lastPushupQuality ||
                now - lastPushupLogAtMs >= squatShadowLogIntervalMs

        if (!shouldLog) return

        lastPushupQuality = prediction.quality
        lastPushupLogAtMs = now
        Log.i(
            "AirFloatPushup",
            "quality=${prediction.qualityName} " +
                "badRaw=${fmt1(prediction.badScoreRaw * 100f)}% badSmoothed=${fmt1(prediction.badScoreSmoothed * 100f)}% " +
                "conf=${fmt1(prediction.confidence * 100f)}% reps=$lastReps cond=${frame.conditionCode}"
        )
    }

    private fun showPushupQualityUnknown() {
        squatShadowClassText.text = "FORM UNKNOWN"
        squatShadowClassText.setTextColor(bannerColorWarn)
        squatShadowConfidenceText.text = "BAD -- · CONF --"
    }

    private fun runSitupQuality(frame: com.airfloat.app.pose.FrameResult) {
        if (exerciseMode != ExerciseMode.SITUP) return
        if (!squatShadowEnabled) return
        val classifier = situpClassifier ?: return
        val situpState = lastSitupCounterResult
        if (situpState == null) {
            showSitupQualityUnknown()
            return
        }
        val now = SystemClock.uptimeMillis()
        val tooIdleForQuality = !situpState.isCycleActive && (now - situpState.lastRepAtMs > 900L)
        val mlPoints = if (frame.rawNormPoints.isNotEmpty()) frame.rawNormPoints else frame.normPoints
        if (mlPoints.isEmpty() || frame.conditionCode != ConditionCode.OK || tooIdleForQuality) {
            showSitupQualityUnknown()
            return
        }
        val prediction = classifier.predict(mlPoints)
        if (prediction == null) {
            showSitupQualityUnknown()
            return
        }

        val qualityText =
            when (prediction.quality) {
                SitupQualityLabel.GOOD -> "GOOD"
                SitupQualityLabel.BAD -> "BAD"
                SitupQualityLabel.UNKNOWN -> "UNKNOWN"
            }
        val qualityColor =
            when (prediction.quality) {
                SitupQualityLabel.GOOD -> bannerColorOk
                SitupQualityLabel.BAD -> bannerColorBad
                SitupQualityLabel.UNKNOWN -> bannerColorWarn
            }

        squatShadowClassText.text = "FORM $qualityText"
        squatShadowClassText.setTextColor(qualityColor)
        squatShadowConfidenceText.text =
            "BAD ${fmt1(prediction.badScoreSmoothed * 100f)}% · CONF ${fmt1(prediction.confidence * 100f)}%"

        val shouldLog =
            prediction.quality != lastSitupQuality ||
                now - lastSitupLogAtMs >= squatShadowLogIntervalMs

        if (!shouldLog) return

        lastSitupQuality = prediction.quality
        lastSitupLogAtMs = now
        Log.i(
            "AirFloatSitup",
            "quality=${prediction.qualityName} " +
                "badRaw=${fmt1(prediction.badScoreRaw * 100f)}% badSmoothed=${fmt1(prediction.badScoreSmoothed * 100f)}% " +
                "conf=${fmt1(prediction.confidence * 100f)}% reps=$lastReps cond=${frame.conditionCode}"
        )
    }

    private fun showSitupQualityUnknown() {
        squatShadowClassText.text = "FORM UNKNOWN"
        squatShadowClassText.setTextColor(bannerColorWarn)
        squatShadowConfidenceText.text = "BAD -- · CONF --"
    }

    private fun placeConditionCardInPreview(left: Float, top: Float, right: Float, bottom: Float) {
        if (!::conditionCard.isInitialized) return
        if (conditionCard.visibility != View.VISIBLE) return
        val cardW = conditionCard.width.toFloat()
        val cardH = conditionCard.height.toFloat()
        if (cardW <= 0f || cardH <= 0f) return

        val contentLeft = left.coerceAtLeast(0f)
        val contentTop = top.coerceAtLeast(0f)
        val contentRight = right.coerceAtLeast(contentLeft + 1f)
        val contentBottom = bottom.coerceAtLeast(contentTop + 1f)

        val rawX = contentLeft + 16f.dpPx()
        val rawY = contentTop + 172f.dpPx()
        val maxX = max(contentLeft, contentRight - cardW - alertInsetPx)
        val maxY = max(contentTop, contentBottom - cardH - alertInsetPx)

        conditionCard.x = rawX.coerceIn(contentLeft, maxX)
        conditionCard.y = rawY.coerceIn(contentTop, maxY)
    }

    private fun resetSessionState(nextTab: RootTab) {
        poseDetector.close()
        createPoseDetector()
        goalReached = false
        lastReps = 0
        cancelBenchmark()
        shownCondition = null
        pendingCondition = null
        pendingConditionFrames = 0
        lastReasonEvent = null
        lastReasonEventAtMs = 0L
        lastRepRejectReason = null
        lastRepRejectAtMs = 0L
        lastSquatShadowQuality = null
        lastSquatShadowLogAtMs = 0L
        lastSquatCounterResult = null
        lastPushupQuality = null
        lastPushupLogAtMs = 0L
        lastPushupCounterResult = null
        repAttemptCounter = 0
        successAttempts = 0
        failedAttempts = 0
        sessionStartRealtimeMs = 0L
        sessionStartWallClockMs = 0L
        sessionPersisted = false
        lastPersistedSessionRecord = null
        sessionAttemptTimeline.clear()
        stopLiveHeaderTicker()
        squatShadowClassifier?.reset()
        pushupClassifier?.reset()
        resetFailedRejectState()
        repTableRows.removeAllViews()
        updateRepTableSummary()
        squatShadowClassText.text = "quality: --"
        squatShadowConfidenceText.text = "bad: --"
        overlayView.setPointsPx(emptyList())
        overlayView.setDebug(null, null, 0, 0f, 0f, "MediaPipe")
        updateRepGauge(0f)
        configureQualityCardForExercise()
        configureGaugeUiForExercise()
        workoutFragment?.resetHubState()
        syncWorkoutFragmentUi()

        successOverlay.visibility = View.GONE
        switchRootTab(nextTab, animate = false)
        countdownText.visibility = View.GONE
        conditionCard.visibility = View.GONE
        squatShadowCard.visibility = View.GONE
        previewView.visibility = View.GONE
        overlayView.visibility = View.GONE
        hudTopScrim.visibility = View.GONE
        hudBottomScrim.visibility = View.GONE
        liveHeaderCard.visibility = View.GONE
        liveScorePanel.visibility = View.GONE
        liveRepCounterCluster.visibility = View.GONE
        liveFeedbackCard.visibility = View.GONE
        finishButton.visibility = View.GONE
        benchButton.visibility = View.GONE
        skeletonButton.visibility = View.GONE
        repGaugePanel.visibility = View.GONE
        repTablePanel.visibility = View.GONE
        repFlashText.visibility = View.GONE
        hideLiveEventOverlay(liveCleanFlashOverlay)
        hideLiveEventOverlay(liveMissFlashOverlay)
        hideLiveEventOverlay(livePerfectFlashOverlay)
        setBottomNavVisible(true)

        pendingStartAfterPermission = false
        if (nextTab == RootTab.WORKOUT) {
            prestartPanel.post {
                animatePrestartEntrance()
                startLogoIdleMotion()
            }
        }
    }

    private fun resetToStart() {
        resetSessionState(RootTab.WORKOUT)
    }

 
}
