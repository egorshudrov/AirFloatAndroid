package com.airfloat.app.ui

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.airfloat.app.R
import com.airfloat.app.stats.ProgramScheduleRepository
import java.time.DayOfWeek

class FirstLaunchFragment : Fragment(R.layout.fragment_first_launch) {

    interface Host {
        fun onFirstLaunchCompleted(restDaysOfWeek: Set<DayOfWeek>)
    }

    private var host: Host? = null
    private lateinit var scheduleRepository: ProgramScheduleRepository
    private val draftRestDays = linkedSetOf<DayOfWeek>()
    private var currentStep = 0

    override fun onAttach(context: Context) {
        super.onAttach(context)
        host = context as? Host
    }

    override fun onDetach() {
        super.onDetach()
        host = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        scheduleRepository = ProgramScheduleRepository(requireContext())
        draftRestDays.clear()
        draftRestDays.addAll(scheduleRepository.loadRestDays())
        currentStep = savedInstanceState?.getInt(KEY_STEP_INDEX) ?: 0

        val scrollView = view.findViewById<ScrollView>(R.id.firstLaunchScrollView)
        val stepText = view.findViewById<TextView>(R.id.firstLaunchStepText)
        val titleText = view.findViewById<TextView>(R.id.firstLaunchTitleText)
        val bodyText = view.findViewById<TextView>(R.id.firstLaunchBodyText)
        val featureLabelText = view.findViewById<TextView>(R.id.firstLaunchFeatureLabelText)
        val featureLineOneText = view.findViewById<TextView>(R.id.firstLaunchFeatureLineOneText)
        val featureLineTwoText = view.findViewById<TextView>(R.id.firstLaunchFeatureLineTwoText)
        val programCard = view.findViewById<View>(R.id.firstLaunchProgramCard)
        val backButton = view.findViewById<TextView>(R.id.firstLaunchBackButton)
        val continueButton = view.findViewById<TextView>(R.id.firstLaunchContinueButton)

        val toggles =
            linkedMapOf(
                DayOfWeek.MONDAY to view.findViewById<TextView>(R.id.firstLaunchMon),
                DayOfWeek.TUESDAY to view.findViewById<TextView>(R.id.firstLaunchTue),
                DayOfWeek.WEDNESDAY to view.findViewById<TextView>(R.id.firstLaunchWed),
                DayOfWeek.THURSDAY to view.findViewById<TextView>(R.id.firstLaunchThu),
                DayOfWeek.FRIDAY to view.findViewById<TextView>(R.id.firstLaunchFri),
                DayOfWeek.SATURDAY to view.findViewById<TextView>(R.id.firstLaunchSat),
                DayOfWeek.SUNDAY to view.findViewById<TextView>(R.id.firstLaunchSun)
            )

        fun renderToggles() {
            toggles.forEach { (day, toggle) ->
                val selected = day in draftRestDays
                toggle.background = ContextCompat.getDrawable(
                    requireContext(),
                    if (selected) {
                        R.drawable.first_launch_rest_day_toggle_selected
                    } else {
                        R.drawable.consistency_rest_day_toggle_idle
                    }
                )
                toggle.setTextColor(
                    if (selected) {
                        Color.parseColor("#7F77DD")
                    } else {
                        Color.parseColor("#555555")
                    }
                )
            }
        }

        toggles.forEach { (day, toggle) ->
            toggle.setOnClickListener {
                if (day in draftRestDays) {
                    draftRestDays.remove(day)
                } else {
                    draftRestDays.add(day)
                }
                renderToggles()
            }
        }

        fun renderStep(step: Int) {
            currentStep = step.coerceIn(0, 2)
            stepText.text = "STEP ${currentStep + 1} / 3"
            backButton.visibility = if (currentStep == 0) View.GONE else View.VISIBLE
            programCard.visibility = if (currentStep == 2) View.VISIBLE else View.GONE

            when (currentStep) {
                0 -> {
                    titleText.text = "WELCOME TO AIRFLOAT"
                    bodyText.text = "AIRFLOAT gives you a clear daily loop: what to train, how to start, how to perform, and what to review next."
                    featureLabelText.text = "WHY IT WORKS"
                    featureLineOneText.text = "You always know the next move."
                    featureLineTwoText.text = "Today points you in, Train launches the session, and Progress closes the feedback loop."
                    continueButton.text = "NEXT"
                }

                1 -> {
                    titleText.text = "LEARN THE LOOP"
                    bodyText.text = "The app is designed as one ritual instead of disconnected tabs."
                    featureLabelText.text = "AIRFLOAT LOOP"
                    featureLineOneText.text = "TODAY → TRAIN → LIVE → PROGRESS"
                    featureLineTwoText.text = "Today sets focus. Train prepares the session. Live tracks the set. Progress tells you what changes next."
                    continueButton.text = "SET MY WEEK"
                }

                else -> {
                    titleText.text = "SET YOUR WEEK"
                    bodyText.text = "Choose your weekly rest days. You can still fine-tune individual dates later in Progress."
                    featureLabelText.text = "BASE PROGRAM"
                    featureLineOneText.text = "Rest days shape the calendar."
                    featureLineTwoText.text = "AIRFLOAT will use this weekly template as the base program and then let you override specific dates when needed."
                    continueButton.text = "ENTER AIRFLOAT"
                }
            }

            scrollView.post { scrollView.smoothScrollTo(0, 0) }
        }

        backButton.setOnClickListener {
            renderStep(currentStep - 1)
        }

        continueButton.setOnClickListener {
            if (currentStep < 2) {
                renderStep(currentStep + 1)
            } else {
                host?.onFirstLaunchCompleted(draftRestDays.toSet())
            }
        }

        renderToggles()
        renderStep(currentStep)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_STEP_INDEX, currentStep)
    }

    companion object {
        const val TAG = "root_first_launch"
        private const val KEY_STEP_INDEX = "first_launch_step_index"
    }
}
