package com.trevio.android.domain.repository

import com.trevio.android.domain.model.TripData
import com.trevio.android.domain.model.TripItineraryItem
import com.trevio.android.domain.model.TripLocation

interface TripService {
    suspend fun getTripData(groupId: String): Result<TripData?>
    suspend fun updateTripData(groupId: String, tripData: TripData): Result<Unit>
    suspend fun addItineraryItem(groupId: String, item: TripItineraryItem): Result<String>
    suspend fun updateItineraryItem(groupId: String, itemId: String, updates: TripItineraryItem): Result<Unit>
    suspend fun removeItineraryItem(groupId: String, itemId: String): Result<Unit>
    suspend fun addLocation(groupId: String, location: TripLocation): Result<String>
    suspend fun removeLocation(groupId: String, locationId: String): Result<Unit>
}
