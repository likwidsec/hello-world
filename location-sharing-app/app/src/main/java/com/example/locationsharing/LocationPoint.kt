package com.example.locationsharing

/**
 * A single recorded position for one person at one moment in time.
 *
 * This class is stored directly in Firebase Realtime Database, so it needs a
 * no-argument constructor and simple public fields that Firebase can read back.
 */
data class LocationPoint(
    var latitude: Double = 0.0,
    var longitude: Double = 0.0,
    var accuracyInMeters: Float = 0f,
    var recordedAtEpochMillis: Long = 0L
) {
    /** Required by Firebase to reconstruct the object when reading from the database. */
    constructor() : this(0.0, 0.0, 0f, 0L)
}
