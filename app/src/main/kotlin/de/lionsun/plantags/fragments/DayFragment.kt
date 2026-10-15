package de.lionsun.plantags.fragments

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.collection.LongSparseArray
import androidx.fragment.app.Fragment
import de.lionsun.plantags.R
import de.lionsun.plantags.activities.MainActivity
import de.lionsun.plantags.databinding.FragmentDayBinding
import de.lionsun.plantags.databinding.TopNavigationBinding
import de.lionsun.plantags.extensions.config
import de.lionsun.plantags.extensions.eventsHelper
import de.lionsun.plantags.extensions.getViewBitmap
import de.lionsun.plantags.extensions.printBitmap
import de.lionsun.plantags.extensions.seconds
import de.lionsun.plantags.helpers.EVENT_ID
import de.lionsun.plantags.helpers.EVENT_OCCURRENCE_TS
import de.lionsun.plantags.helpers.Formatter
import de.lionsun.plantags.helpers.IS_TASK_COMPLETED
import de.lionsun.plantags.helpers.DAY_CODE
import de.lionsun.plantags.helpers.NEW_EVENT_SET_HOUR_DURATION
import de.lionsun.plantags.helpers.NEW_EVENT_START_TS
import de.lionsun.plantags.helpers.getActivityToOpen
import de.lionsun.plantags.interfaces.NavigationListener
import de.lionsun.plantags.models.Event
import de.lionsun.plantags.views.WeekStripView
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.joda.time.DateTime
import java.util.Locale

class DayFragment : Fragment() {
    var mListener: NavigationListener? = null
    private var mTextColor = 0
    private var mDayCode = ""
    private var lastHash = 0
    private var calendarColors = LongSparseArray<Int>()
    private var weekEvents = ArrayList<Event>()

    private lateinit var binding: FragmentDayBinding
    private lateinit var topNavigationBinding: TopNavigationBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentDayBinding.inflate(inflater, container, false)
        topNavigationBinding = TopNavigationBinding.bind(binding.root)
        mDayCode = requireArguments().getString(DAY_CODE)!!
        setupButtons()
        setupTimeline()
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        binding.weekStrip.updateColors()
        binding.dayTimeline.updateColors()

        requireContext().eventsHelper.getCalendars(requireActivity(), false) {
            it.map { calendar ->
                calendarColors.put(calendar.id!!, calendar.color)
            }
            if (isAdded) {
                lastHash = 0
                updateCalendar()
            }
        }
    }

    private fun setupButtons() {
        mTextColor = requireContext().getProperTextColor()

        topNavigationBinding.topLeftArrow.apply {
            applyColorFilter(mTextColor)
            background = null
            setOnClickListener {
                mListener?.goLeft()
            }

            val pointerLeft = requireContext().getDrawable(org.fossify.commons.R.drawable.ic_chevron_left_vector)
            pointerLeft?.isAutoMirrored = true
            setImageDrawable(pointerLeft)
            contentDescription = getString(R.string.accessibility_previous_day)
        }

        topNavigationBinding.topRightArrow.apply {
            applyColorFilter(mTextColor)
            background = null
            setOnClickListener {
                mListener?.goRight()
            }

            val pointerRight = requireContext().getDrawable(org.fossify.commons.R.drawable.ic_chevron_right_vector)
            pointerRight?.isAutoMirrored = true
            setImageDrawable(pointerRight)
            contentDescription = getString(R.string.accessibility_next_day)
        }

        val day = Formatter.getDayTitle(requireContext(), mDayCode)
        topNavigationBinding.topValue.apply {
            text = day
            contentDescription = text
            setOnClickListener {
                (activity as MainActivity).showGoToDateDialog()
            }
            setTextColor(context.getProperTextColor())
        }
    }

    private fun setupTimeline() {
        binding.weekStrip.onDayClick = { dayCode ->
            if (dayCode != mDayCode) {
                mListener?.goToDateTime(Formatter.getLocalDateTimeFromCode(dayCode))
            }
        }

        binding.dayTimeline.onEventClick = { editEvent(it) }
        binding.dayTimeline.onEmptyClick = { minute ->
            val ts = Formatter.getLocalDateTimeFromCode(mDayCode).plusMinutes(minute).seconds()
            Intent(context, getActivityToOpen(false)).apply {
                putExtra(NEW_EVENT_START_TS, ts)
                putExtra(NEW_EVENT_SET_HOUR_DURATION, true)
                startActivity(this)
            }
        }
    }

    fun updateCalendar() {
        val weekStart = getWeekStart()
        val startTS = weekStart.seconds()
        val endTS = weekStart.plusDays(7).minusMinutes(1).seconds()
        context?.eventsHelper?.getEvents(startTS, endTS) {
            receivedEvents(it)
        }
    }

    private fun getWeekStart(): DateTime {
        val day = Formatter.getLocalDateTimeFromCode(mDayCode).withTimeAtStartOfDay()
        val firstDay = requireContext().config.firstDayOfWeek
        val diff = (day.dayOfWeek - firstDay + 7) % 7
        return day.minusDays(diff)
    }

    private fun receivedEvents(events: List<Event>) {
        val newHash = events.hashCode() + calendarColors.size()
        if (newHash == lastHash || !isAdded) {
            return
        }
        lastHash = newHash

        val replaceDescription = requireContext().config.replaceDescription
        val sorted = ArrayList(events.sortedWith(compareBy({ it.startTS }, { it.endTS }, { it.title }, {
            if (replaceDescription) it.location else it.description
        })))

        activity?.runOnUiThread {
            if (activity != null && isAdded) {
                weekEvents = sorted
                updateViews()
            }
        }
    }

    private fun eventColor(event: Event): Int {
        return if (event.color != 0) {
            event.color
        } else {
            calendarColors.get(event.calendarId, requireContext().getProperPrimaryColor())
        }
    }

    private fun updateViews() {
        binding.dayTimeline.setEvents(mDayCode, weekEvents) { eventColor(it) }
        binding.weekStrip.setWeek(buildWeekCells(), mDayCode)

        // bring the interesting part of the day into view: now for today, 07:00 otherwise
        binding.dayScroll.post {
            if (!isAdded) return@post
            val focusMinute = if (mDayCode == Formatter.getTodayCode()) {
                (Formatter.getDateTimeFromTS(System.currentTimeMillis() / 1000L).minuteOfDay - 90).coerceAtLeast(0)
            } else {
                7 * 60
            }
            binding.dayScroll.scrollTo(0, binding.dayTimeline.getYForMinute(focusMinute))
        }
    }

    private fun buildWeekCells(): List<WeekStripView.DayCell> {
        val weekStart = getWeekStart()
        val todayCode = Formatter.getTodayCode()
        val locale = Locale.getDefault()
        val cells = ArrayList<WeekStripView.DayCell>()

        for (i in 0 until 7) {
            val day = weekStart.plusDays(i)
            val dayCode = Formatter.getDayCodeFromDateTime(day)
            val dayStartTS = day.seconds()
            val dayEndTS = day.plusDays(1).seconds()

            // minutes per tag color on that day
            val minutesByColor = LinkedHashMap<Int, Int>()
            for (event in weekEvents) {
                if (event.startTS >= dayEndTS || event.endTS < dayStartTS) {
                    continue
                }
                if (event.endTS == dayStartTS && event.startTS < dayStartTS) {
                    continue
                }

                val from = maxOf(event.startTS, dayStartTS)
                val to = minOf(event.endTS, dayEndTS)
                val minutes = if (event.getIsAllDay()) 0 else maxOf(((to - from) / 60).toInt(), 15)
                if (minutes > 0) {
                    val color = eventColor(event)
                    minutesByColor[color] = (minutesByColor[color] ?: 0) + minutes
                }
            }

            var label = day.dayOfWeek().getAsShortText(locale)
            if (label.any { it.isLetter() && it.isLowerCase() && it.code < 128 }) {
                label = label.uppercase(locale)
            }

            cells.add(
                WeekStripView.DayCell(
                    dayCode = dayCode,
                    weekdayLabel = label,
                    dayOfMonth = day.dayOfMonth,
                    isToday = dayCode == todayCode,
                    segments = minutesByColor.map { it.key to it.value },
                )
            )
        }

        return cells
    }

    private fun editEvent(event: Event) {
        Intent(context, getActivityToOpen(event.isTask())).apply {
            putExtra(EVENT_ID, event.id)
            putExtra(EVENT_OCCURRENCE_TS, event.startTS)
            putExtra(IS_TASK_COMPLETED, event.isTaskCompleted())
            startActivity(this)
        }
    }

    fun printCurrentView() {
        topNavigationBinding.apply {
            topLeftArrow.beGone()
            topRightArrow.beGone()
            topValue.setTextColor(resources.getColor(org.fossify.commons.R.color.theme_light_text_color))

            Handler(Looper.getMainLooper()).postDelayed({
                requireContext().printBitmap(binding.dayHolder.getViewBitmap())

                Handler(Looper.getMainLooper()).postDelayed({
                    topLeftArrow.beVisible()
                    topRightArrow.beVisible()
                    topValue.setTextColor(requireContext().getProperTextColor())
                }, 1000)
            }, 1000)
        }
    }
}
