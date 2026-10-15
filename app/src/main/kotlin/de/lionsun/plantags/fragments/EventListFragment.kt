package de.lionsun.plantags.fragments

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.lionsun.plantags.R
import de.lionsun.plantags.activities.MainActivity
import de.lionsun.plantags.activities.SimpleActivity
import de.lionsun.plantags.adapters.EventListAdapter
import de.lionsun.plantags.databinding.FragmentEventListBinding
import de.lionsun.plantags.extensions.config
import de.lionsun.plantags.extensions.editEvent
import de.lionsun.plantags.extensions.eventsHelper
import de.lionsun.plantags.extensions.getEventListItems
import de.lionsun.plantags.extensions.getViewBitmap
import de.lionsun.plantags.extensions.launchNewEventIntent
import de.lionsun.plantags.extensions.printBitmap
import de.lionsun.plantags.extensions.seconds
import de.lionsun.plantags.helpers.EVENTS_LIST_VIEW
import de.lionsun.plantags.helpers.FETCH_INTERVAL
import de.lionsun.plantags.helpers.Formatter
import de.lionsun.plantags.helpers.INITIAL_EVENTS
import de.lionsun.plantags.helpers.MIN_EVENTS_TRESHOLD
import de.lionsun.plantags.helpers.UPDATE_BOTTOM
import de.lionsun.plantags.helpers.UPDATE_TOP
import de.lionsun.plantags.models.Event
import de.lionsun.plantags.models.ListEvent
import de.lionsun.plantags.models.ListItem
import de.lionsun.plantags.models.ListSectionDay
import org.fossify.commons.extensions.areSystemAnimationsEnabled
import org.fossify.commons.extensions.beGoneIf
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.isGone
import org.fossify.commons.extensions.onGlobalLayout
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.underlineText
import org.fossify.commons.interfaces.RefreshRecyclerViewListener
import org.fossify.commons.views.MyLinearLayoutManager
import org.fossify.commons.views.MyRecyclerView
import org.joda.time.DateTime

class EventListFragment : MyFragmentHolder(), RefreshRecyclerViewListener {
    private var mEvents = ArrayList<Event>()
    private var minFetchedTS = 0L
    private var maxFetchedTS = 0L
    private var wereInitialEventsAdded = false
    private var hasBeenScrolled = false
    private var bottomItemAtRefresh: ListItem? = null

    private var use24HourFormat = false

    private lateinit var binding: FragmentEventListBinding

    override val viewType = EVENTS_LIST_VIEW

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentEventListBinding.inflate(inflater, container, false)
        binding.root.background = ColorDrawable(requireContext().getProperBackgroundColor())
        binding.calendarEventsListHolder.id = (System.currentTimeMillis() % 100000).toInt()
        binding.calendarEmptyListPlaceholder2.apply {
            setTextColor(context.getProperPrimaryColor())
            underlineText()
            setOnClickListener {
                activity?.hideKeyboard()
                context.launchNewEventIntent(getNewEventDayCode())
            }
        }

        use24HourFormat = requireContext().config.use24HourFormat
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        checkEvents()
        val use24Hour = requireContext().config.use24HourFormat
        if (use24Hour != use24HourFormat) {
            use24HourFormat = use24Hour
            (binding.calendarEventsList.adapter as? EventListAdapter)?.toggle24HourFormat(
                use24HourFormat
            )
        }
    }

    override fun onPause() {
        super.onPause()
        use24HourFormat = requireContext().config.use24HourFormat
    }

    private fun checkEvents() {
        if (!wereInitialEventsAdded) {
            minFetchedTS =
                DateTime().minusMinutes(requireContext().config.displayPastEvents).seconds()
            maxFetchedTS = DateTime().plusMonths(6).seconds()
        }

        requireContext().eventsHelper.getEvents(minFetchedTS, maxFetchedTS) { events ->
            if (events.size >= MIN_EVENTS_TRESHOLD) {
                receivedEvents(events, INITIAL_EVENTS)
            } else {
                if (!wereInitialEventsAdded) {
                    maxFetchedTS += FETCH_INTERVAL
                }

                requireContext().eventsHelper.getEvents(minFetchedTS, maxFetchedTS) {
                    mEvents = it
                    receivedEvents(mEvents, INITIAL_EVENTS, !wereInitialEventsAdded)
                }
            }
            wereInitialEventsAdded = true
        }
    }

    private fun receivedEvents(
        events: ArrayList<Event>,
        updateStatus: Int,
        forceRecreation: Boolean = false
    ) {
        if (context == null || activity == null) {
            return
        }

        mEvents = events
        val listItems = requireContext().getEventListItems(mEvents)

        activity?.runOnUiThread {
            if (activity == null) {
                return@runOnUiThread
            }

            val currAdapter = binding.calendarEventsList.adapter
            if (currAdapter == null || forceRecreation) {
                EventListAdapter(
                    activity as SimpleActivity,
                    listItems,
                    true,
                    this,
                    binding.calendarEventsList
                ) {
                    if (it is ListEvent) {
                        context?.editEvent(it)
                    }
                }.apply {
                    binding.calendarEventsList.adapter = this
                }

                if (requireContext().areSystemAnimationsEnabled) {
                    binding.calendarEventsList.scheduleLayoutAnimation()
                }

                binding.calendarEventsList.endlessScrollListener =
                    object : MyRecyclerView.EndlessScrollListener {
                        override fun updateTop() {
                            fetchPreviousPeriod()
                        }

                        override fun updateBottom() {
                            fetchNextPeriod()
                        }
                    }

                binding.calendarEventsList.addOnScrollListener(object :
                    RecyclerView.OnScrollListener() {
                    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                        super.onScrollStateChanged(recyclerView, newState)
                        if (!hasBeenScrolled) {
                            hasBeenScrolled = true
                            (activity as? MainActivity)?.refreshItems()
                            (activity as? MainActivity)?.refreshMenuItems()
                        }
                    }
                })
            } else {
                (currAdapter as EventListAdapter).updateListItems(listItems)
                if (updateStatus == UPDATE_TOP) {
                    val item = listItems.indexOfFirst { it == bottomItemAtRefresh }
                    if (item != -1) {
                        binding.calendarEventsList.scrollToPosition(item)
                    }
                } else if (updateStatus == UPDATE_BOTTOM) {
                    binding.calendarEventsList.smoothScrollBy(
                        0,
                        requireContext().resources.getDimension(R.dimen.endless_scroll_move_height)
                            .toInt()
                    )
                }
            }
            checkPlaceholderVisibility()
        }
    }

    private fun checkPlaceholderVisibility() {
        binding.calendarEmptyListPlaceholder.beVisibleIf(mEvents.isEmpty())
        binding.calendarEmptyListPlaceholder2.beVisibleIf(mEvents.isEmpty())
        binding.calendarEventsList.beGoneIf(mEvents.isEmpty())
        if (activity != null) {
            binding.calendarEmptyListPlaceholder.setTextColor(requireActivity().getProperTextColor())
            if (mEvents.isEmpty()) {
                val placeholderTextId = if (requireActivity().config.displayCalendars.isEmpty()) {
                    R.string.everything_filtered_out
                } else {
                    R.string.no_upcoming_events
                }

                binding.calendarEmptyListPlaceholder.setText(placeholderTextId)
            }
        }
    }

    private fun fetchPreviousPeriod() {
        val lastPosition =
            (binding.calendarEventsList.layoutManager as MyLinearLayoutManager).findLastVisibleItemPosition()
        bottomItemAtRefresh =
            (binding.calendarEventsList.adapter as EventListAdapter).listItems[lastPosition]

        val oldMinFetchedTS = minFetchedTS - 1
        minFetchedTS -= FETCH_INTERVAL
        requireContext().eventsHelper.getEvents(minFetchedTS, oldMinFetchedTS) {
            it.forEach { event ->
                if (mEvents.firstOrNull { it.id == event.id && it.startTS == event.startTS } == null) {
                    mEvents.add(0, event)
                }
            }

            receivedEvents(mEvents, UPDATE_TOP)
        }
    }

    private fun fetchNextPeriod() {
        val oldMaxFetchedTS = maxFetchedTS + 1
        maxFetchedTS += FETCH_INTERVAL
        requireContext().eventsHelper.getEvents(oldMaxFetchedTS, maxFetchedTS) {
            it.forEach { event ->
                if (mEvents.firstOrNull { it.id == event.id && it.startTS == event.startTS } == null) {
                    mEvents.add(0, event)
                }
            }

            receivedEvents(mEvents, UPDATE_BOTTOM)
        }
    }

    override fun refreshItems() {
        checkEvents()
    }

    override fun goToToday() {
        val listItems = requireContext().getEventListItems(mEvents)
        val firstNonPastSectionIndex =
            listItems.indexOfFirst { it is ListSectionDay && !it.isPastSection }
        if (firstNonPastSectionIndex != -1) {
            (binding.calendarEventsList.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(
                firstNonPastSectionIndex,
                0
            )
            binding.calendarEventsList.onGlobalLayout {
                hasBeenScrolled = false
                (activity as? MainActivity)?.refreshItems()
                (activity as? MainActivity)?.refreshMenuItems()
            }
        }
    }

    override fun showGoToDateDialog() {}

    override fun refreshEvents() {
        checkEvents()
    }

    override fun shouldGoToTodayBeVisible() = hasBeenScrolled

    override fun getNewEventDayCode() = Formatter.getTodayCode()

    override fun printView() {
        binding.apply {
            if (calendarEventsList.isGone()) {
                context?.toast(org.fossify.commons.R.string.no_items_found)
                return@apply
            }

            (calendarEventsList.adapter as? EventListAdapter)?.togglePrintMode()
            Handler().postDelayed({
                requireContext().printBitmap(calendarEventsList.getViewBitmap())

                Handler().postDelayed({
                    (calendarEventsList.adapter as? EventListAdapter)?.togglePrintMode()
                }, 1000)
            }, 1000)
        }
    }

    override fun getCurrentDate() = null
}
