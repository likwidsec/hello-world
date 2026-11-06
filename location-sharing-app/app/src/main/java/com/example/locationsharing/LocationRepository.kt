package com.example.locationsharing

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener

/**
 * The current known state of one person in the pair: who they are and where
 * they were last seen. [liveLocation] is null until that person has published
 * at least one position.
 */
data class MemberLiveState(
    val userId: String,
    val displayName: String,
    val liveLocation: LocationPoint?
)

/**
 * All reads and writes to Firebase Realtime Database live here, so the rest of
 * the app never touches Firebase directly.
 *
 * Database layout for one pair:
 *
 *   pairs/{pairCode}/members/{userId}/displayName  -> String
 *   pairs/{pairCode}/members/{userId}/live          -> LocationPoint (overwritten each update)
 *   pairs/{pairCode}/history/{userId}/{autoId}      -> LocationPoint (appended, kept forever)
 *
 * "live" is kept tiny so the map updates cheaply, while "history" grows without
 * limit. The map only ever reads back the most recent slice of history for the
 * trail (see [TRAIL_POINT_LIMIT]).
 */
class LocationRepository(pairCode: String) {

    private val database = FirebaseDatabase.getInstance()
    private val pairRoot = database.getReference("pairs").child(sanitizePairCode(pairCode))
    private val membersReference = pairRoot.child("members")
    private val historyReference = pairRoot.child("history")

    // Every listener we attach is remembered here so the UI can detach them all
    // at once when it goes away, avoiding leaks and duplicate callbacks.
    private val activeListeners = mutableListOf<Pair<Query, ValueEventListener>>()

    /** Record this person's chosen name under their member node. */
    fun saveDisplayName(userId: String, displayName: String) {
        membersReference.child(userId).child("displayName").setValue(displayName)
    }

    /**
     * Publish a new position: overwrite the lightweight "live" value and append a
     * permanent copy to this person's history.
     */
    fun publishLiveLocation(userId: String, point: LocationPoint) {
        membersReference.child(userId).child("live").setValue(point)
        historyReference.child(userId).push().setValue(point)
    }

    /**
     * Watch every member of the pair. The callback fires immediately with the
     * current state and again whenever anyone's name or live position changes.
     */
    fun listenToMembers(onMembersChanged: (List<MemberLiveState>) -> Unit) {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val members = snapshot.children.mapNotNull { memberSnapshot ->
                    val userId = memberSnapshot.key ?: return@mapNotNull null
                    val displayName = memberSnapshot.child("displayName")
                        .getValue(String::class.java) ?: "Unknown"
                    val liveLocation = memberSnapshot.child("live")
                        .getValue(LocationPoint::class.java)
                    MemberLiveState(userId, displayName, liveLocation)
                }
                onMembersChanged(members)
            }

            override fun onCancelled(error: DatabaseError) {
                // Permission or network error; leave the last known state on screen.
            }
        }
        membersReference.addValueEventListener(listener)
        activeListeners.add(membersReference to listener)
    }

    /**
     * Watch the recent movement trail for one person. Only the most recent
     * [TRAIL_POINT_LIMIT] points are read back, even though every point is kept
     * in the database forever.
     */
    fun listenToMemberHistory(userId: String, onHistoryChanged: (List<LocationPoint>) -> Unit) {
        val query = historyReference.child(userId)
            .orderByChild("recordedAtEpochMillis")
            .limitToLast(TRAIL_POINT_LIMIT)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val orderedPoints = snapshot.children
                    .mapNotNull { it.getValue(LocationPoint::class.java) }
                    .sortedBy { it.recordedAtEpochMillis }
                onHistoryChanged(orderedPoints)
            }

            override fun onCancelled(error: DatabaseError) {
                // Ignore; the existing trail stays drawn.
            }
        }
        query.addValueEventListener(listener)
        activeListeners.add(query to listener)
    }

    /** Detach every listener this repository created. Call from onDestroy. */
    fun detachAllListeners() {
        activeListeners.forEach { (query, listener) -> query.removeEventListener(listener) }
        activeListeners.clear()
    }

    companion object {
        /** How many recent history points to draw as the on-map trail. */
        const val TRAIL_POINT_LIMIT = 500

        /**
         * Firebase database keys may not contain '.', '#', '$', '[', ']' or '/'.
         * Replace anything unsafe and lowercase the code so "Us-2024" and
         * "us-2024" land in the same shared room.
         */
        fun sanitizePairCode(rawPairCode: String): String =
            rawPairCode.trim().lowercase().replace(Regex("[.#$\\[\\]/\\s]"), "_")
    }
}
