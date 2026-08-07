package com.trevio.android.util

import com.google.common.truth.Truth.assertThat
import com.trevio.android.domain.model.GroupTemplate
import com.trevio.android.domain.model.TripItineraryItem
import com.trevio.android.domain.model.TripLocation
import com.trevio.android.domain.model.TripData
import org.junit.Test

class TripTest {

    @Test
    fun `TripItineraryItem creates with defaults`() {
        val item = TripItineraryItem(title = "Visit Beach")
        assertThat(item.title).isEqualTo("Visit Beach")
        assertThat(item.itemId).isEmpty()
        assertThat(item.completed).isFalse()
        assertThat(item.assignedTo).isEmpty()
        assertThat(item.estimatedCost).isEqualTo(0.0)
    }

    @Test
    fun `TripItineraryItem can be marked completed`() {
        val item = TripItineraryItem(title = "Lunch", completed = false)
        val updated = item.copy(completed = true)
        assertThat(updated.completed).isTrue()
        assertThat(item.completed).isFalse()
    }

    @Test
    fun `TripLocation creates with defaults`() {
        val loc = TripLocation(name = "Hotel Taj")
        assertThat(loc.name).isEqualTo("Hotel Taj")
        assertThat(loc.locationId).isEmpty()
        assertThat(loc.latitude).isEqualTo(0.0)
        assertThat(loc.longitude).isEqualTo(0.0)
    }

    @Test
    fun `TripData creates with defaults`() {
        val trip = TripData(destination = "Goa")
        assertThat(trip.destination).isEqualTo("Goa")
        assertThat(trip.itinerary).isEmpty()
        assertThat(trip.locations).isEmpty()
    }

    @Test
    fun `TripData holds multiple items and locations`() {
        val trip = TripData(
            destination = "Manali",
            itinerary = listOf(
                TripItineraryItem(itemId = "1", title = "A"),
                TripItineraryItem(itemId = "2", title = "B")
            ),
            locations = listOf(
                TripLocation(locationId = "1", name = "X")
            )
        )
        assertThat(trip.itinerary).hasSize(2)
        assertThat(trip.locations).hasSize(1)
    }

    @Test
    fun `itinerary groups by day`() {
        val items = listOf(
            TripItineraryItem(itemId = "1", title = "A", date = 1705276800000L), // Jan 15 2024
            TripItineraryItem(itemId = "2", title = "B", date = 1705276800000L), // Jan 15 2024
            TripItineraryItem(itemId = "3", title = "C", date = 1705363200000L)  // Jan 16 2024
        )
        val grouped = items.groupBy { item ->
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = item.date }
            "${cal.get(java.util.Calendar.YEAR)}-${cal.get(java.util.Calendar.MONTH)}-${cal.get(java.util.Calendar.DAY_OF_MONTH)}"
        }
        assertThat(grouped).hasSize(2)
    }

    @Test
    fun `itinerary calculates total estimated cost`() {
        val items = listOf(
            TripItineraryItem(itemId = "1", title = "A", estimatedCost = 100.0),
            TripItineraryItem(itemId = "2", title = "B", estimatedCost = 200.0)
        )
        val total = items.sumOf { it.estimatedCost }
        assertThat(total).isEqualTo(300.0)
    }

    @Test
    fun `itinerary counts completed items`() {
        val items = listOf(
            TripItineraryItem(itemId = "1", title = "A", completed = true),
            TripItineraryItem(itemId = "2", title = "B", completed = false),
            TripItineraryItem(itemId = "3", title = "C", completed = true)
        )
        val completed = items.count { it.completed }
        assertThat(completed).isEqualTo(2)
    }

    @Test
    fun `GroupTemplate TRIP exists`() {
        assertThat(GroupTemplate.valueOf("TRIP")).isEqualTo(GroupTemplate.TRIP)
    }

    @Test
    fun `TripData with start and end dates`() {
        val start = System.currentTimeMillis()
        val end = start + 7 * 24 * 60 * 60 * 1000L
        val trip = TripData(
            startDate = start,
            endDate = end,
            destination = "Goa"
        )
        assertThat(trip.endDate - trip.startDate).isEqualTo(7 * 24 * 60 * 60 * 1000L)
    }

    // ─── Edge cases ──────────────────────────────────────────────

    @Test
    fun `empty itinerary and locations is valid`() {
        val trip = TripData(destination = "Goa")
        assertThat(trip.itinerary).isEmpty()
        assertThat(trip.locations).isEmpty()
    }

    @Test
    fun `itinerary items sort by date chronologically`() {
        val items = listOf(
            TripItineraryItem(itemId = "3", title = "Dinner", date = 1718520000000L),
            TripItineraryItem(itemId = "1", title = "Breakfast", date = 1718433600000L),
            TripItineraryItem(itemId = "2", title = "Lunch", date = 1718476800000L)
        )
        val sorted = items.sortedBy { it.date }
        assertThat(sorted[0].title).isEqualTo("Breakfast")
        assertThat(sorted[1].title).isEqualTo("Lunch")
        assertThat(sorted[2].title).isEqualTo("Dinner")
    }

    @Test
    fun `trip with only locations is valid`() {
        val trip = TripData(
            destination = "Goa",
            itinerary = emptyList(),
            locations = listOf(
                TripLocation(locationId = "1", name = "Beach", latitude = 15.5, longitude = 73.8),
                TripLocation(locationId = "2", name = "Hotel", latitude = 15.4, longitude = 73.9)
            )
        )
        assertThat(trip.itinerary).isEmpty()
        assertThat(trip.locations).hasSize(2)
    }

    @Test
    fun `completed percentage calculation`() {
        val items = listOf(
            TripItineraryItem(itemId = "1", title = "A", completed = true),
            TripItineraryItem(itemId = "2", title = "B", completed = false),
            TripItineraryItem(itemId = "3", title = "C", completed = true),
            TripItineraryItem(itemId = "4", title = "D", completed = true)
        )
        val completed = items.count { it.completed }
        val pct = (completed.toDouble() / items.size) * 100
        assertThat(pct).isEqualTo(75.0)
    }

    @Test
    fun `location can be linked to an expense via expenseId`() {
        val loc = TripLocation(
            locationId = "loc_1",
            name = "Restaurant",
            category = "food",
            visitedOn = System.currentTimeMillis(),
            expenseId = "exp_123"
        )
        assertThat(loc.expenseId).isEqualTo("exp_123")
    }

    @Test
    fun `itinerary item with assignedTo tracks participants`() {
        val item = TripItineraryItem(
            itemId = "i1",
            title = "Safari",
            estimatedCost = 1500.0,
            assignedTo = listOf("u1", "u2", "u3")
        )
        assertThat(item.assignedTo).hasSize(3)
        assertThat(item.assignedTo).contains("u2")
    }
}
