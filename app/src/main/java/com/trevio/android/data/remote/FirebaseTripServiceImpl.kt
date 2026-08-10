package com.trevio.android.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.trevio.android.util.friendlyNetworkMessage
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.trevio.android.domain.model.TripData
import com.trevio.android.domain.model.TripItineraryItem
import com.trevio.android.domain.model.TripLocation
import com.trevio.android.domain.repository.TripService
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseTripServiceImpl @Inject constructor(
    private val auth: FirebaseAuth
) : TripService {

    private val db = FirebaseFirestore.getInstance()

    private fun tripDocRef(groupId: String) =
        db.collection("groups").document(groupId).collection("trip").document("data")

    override suspend fun getTripData(groupId: String): Result<TripData?> {
        return try {
            auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))
            if (groupId.isBlank()) return Result.failure(Exception("Group ID is required"))
            val snap = tripDocRef(groupId).get().await()
            if (!snap.exists()) return Result.success(null)
            val data = snap.data ?: return Result.success(null)

            @Suppress("UNCHECKED_CAST")
            val itinerary = (data["itinerary"] as? List<Map<String, Any>>)?.map { item ->
                TripItineraryItem(
                    itemId = item["itemId"] as? String ?: "",
                    title = item["title"] as? String ?: "",
                    description = item["description"] as? String ?: "",
                    date = (item["date"] as? Number)?.toLong() ?: 0,
                    location = item["location"] as? String ?: "",
                    latitude = (item["latitude"] as? Number)?.toDouble() ?: 0.0,
                    longitude = (item["longitude"] as? Number)?.toDouble() ?: 0.0,
                    category = item["category"] as? String ?: "other",
                    estimatedCost = (item["estimatedCost"] as? Number)?.toDouble() ?: 0.0,
                    assignedTo = run {
                        @Suppress("UNCHECKED_CAST")
                        val list = item["assignedTo"] as? List<String>
                        list ?: emptyList()
                    },
                    completed = item["completed"] as? Boolean ?: false
                )
            } ?: emptyList()

            @Suppress("UNCHECKED_CAST")
            val locations = (data["locations"] as? List<Map<String, Any>>)?.map { loc ->
                TripLocation(
                    locationId = loc["locationId"] as? String ?: "",
                    name = loc["name"] as? String ?: "",
                    latitude = (loc["latitude"] as? Number)?.toDouble() ?: 0.0,
                    longitude = (loc["longitude"] as? Number)?.toDouble() ?: 0.0,
                    address = loc["address"] as? String ?: "",
                    category = loc["category"] as? String ?: "other",
                    visitedOn = (loc["visitedOn"] as? Number)?.toLong() ?: 0,
                    expenseId = loc["expenseId"] as? String ?: ""
                )
            } ?: emptyList()

            Result.success(TripData(
                startDate = (data["startDate"] as? Number)?.toLong() ?: 0,
                endDate = (data["endDate"] as? Number)?.toLong() ?: 0,
                destination = data["destination"] as? String ?: "",
                coverPhotoURL = data["coverPhotoURL"] as? String ?: "",
                itinerary = itinerary,
                locations = locations
            ))
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun updateTripData(groupId: String, tripData: TripData): Result<Unit> {
        return try {
            auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))
            if (groupId.isBlank()) return Result.failure(Exception("Group ID is required"))
            val data = mapOf(
                "startDate" to tripData.startDate,
                "endDate" to tripData.endDate,
                "destination" to tripData.destination,
                "coverPhotoURL" to tripData.coverPhotoURL,
                "itinerary" to tripData.itinerary.map { item ->
                    mapOf(
                        "itemId" to item.itemId,
                        "title" to item.title,
                        "description" to item.description,
                        "date" to item.date,
                        "location" to item.location,
                        "latitude" to item.latitude,
                        "longitude" to item.longitude,
                        "category" to item.category,
                        "estimatedCost" to item.estimatedCost,
                        "assignedTo" to item.assignedTo,
                        "completed" to item.completed
                    )
                },
                "locations" to tripData.locations.map { loc ->
                    mapOf(
                        "locationId" to loc.locationId,
                        "name" to loc.name,
                        "latitude" to loc.latitude,
                        "longitude" to loc.longitude,
                        "address" to loc.address,
                        "category" to loc.category,
                        "visitedOn" to loc.visitedOn,
                        "expenseId" to loc.expenseId
                    )
                }
            )
            tripDocRef(groupId).set(data, SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun addItineraryItem(groupId: String, item: TripItineraryItem): Result<String> {
        return try {
            auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))
            if (groupId.isBlank()) return Result.failure(Exception("Group ID is required"))
            val tripData = getTripData(groupId).getOrNull() ?: TripData()
            val itemId = "item_${System.currentTimeMillis()}_${(1..9999).random()}"
            val newItem = item.copy(itemId = itemId)
            val updated = tripData.copy(itinerary = tripData.itinerary + newItem)
            updateTripData(groupId, updated)
            Result.success(itemId)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun updateItineraryItem(groupId: String, itemId: String, updates: TripItineraryItem): Result<Unit> {
        return try {
            auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))
            if (groupId.isBlank() || itemId.isBlank()) return Result.failure(Exception("Group ID and Item ID are required"))
            val tripData = getTripData(groupId).getOrNull() ?: return Result.failure(Exception("Trip data not found"))
            val itinerary = tripData.itinerary.map {
                if (it.itemId == itemId) it.copy(
                    title = if (updates.title.isNotEmpty()) updates.title else it.title,
                    description = if (updates.description.isNotEmpty()) updates.description else it.description,
                    date = if (updates.date != 0L) updates.date else it.date,
                    location = if (updates.location.isNotEmpty()) updates.location else it.location,
                    latitude = if (updates.latitude != 0.0) updates.latitude else it.latitude,
                    longitude = if (updates.longitude != 0.0) updates.longitude else it.longitude,
                    category = if (updates.category.isNotEmpty()) updates.category else it.category,
                    estimatedCost = if (updates.estimatedCost != 0.0) updates.estimatedCost else it.estimatedCost,
                    assignedTo = if (updates.assignedTo.isNotEmpty()) updates.assignedTo else it.assignedTo,
                    completed = updates.completed
                ) else it
            }
            updateTripData(groupId, tripData.copy(itinerary = itinerary))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun removeItineraryItem(groupId: String, itemId: String): Result<Unit> {
        return try {
            auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))
            if (groupId.isBlank() || itemId.isBlank()) return Result.failure(Exception("Group ID and Item ID are required"))
            val tripData = getTripData(groupId).getOrNull() ?: return Result.failure(Exception("Trip data not found"))
            val itinerary = tripData.itinerary.filter { it.itemId != itemId }
            updateTripData(groupId, tripData.copy(itinerary = itinerary))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun addLocation(groupId: String, location: TripLocation): Result<String> {
        return try {
            auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))
            if (groupId.isBlank()) return Result.failure(Exception("Group ID is required"))
            val tripData = getTripData(groupId).getOrNull() ?: TripData()
            val locationId = "loc_${System.currentTimeMillis()}_${(1..9999).random()}"
            val newLoc = location.copy(locationId = locationId)
            val updated = tripData.copy(locations = tripData.locations + newLoc)
            updateTripData(groupId, updated)
            Result.success(locationId)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }

    override suspend fun removeLocation(groupId: String, locationId: String): Result<Unit> {
        return try {
            auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))
            if (groupId.isBlank() || locationId.isBlank()) return Result.failure(Exception("Group ID and Location ID are required"))
            val tripData = getTripData(groupId).getOrNull() ?: return Result.failure(Exception("Trip data not found"))
            val locations = tripData.locations.filter { it.locationId != locationId }
            updateTripData(groupId, tripData.copy(locations = locations))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyNetworkMessage(e) ?: e.message, e))
        }
    }
}
