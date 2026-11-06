package com.example.locationsharing

import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.locationsharing.databinding.ActivityMapBinding
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions

/**
 * The main screen. Shows a Google Map with a labelled marker for each person's
 * most recent position and, optionally, a coloured trail of where each person
 * has recently been.
 */
class MapActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityMapBinding
    private lateinit var pairSettings: PairSettings
    private lateinit var locationRepository: LocationRepository

    private var googleMap: GoogleMap? = null
    private var hasCenteredOnFirstLocation = false
    private var trailsAreVisible = true

    // Visual state kept per person so we update existing map objects instead of
    // redrawing everything on each change.
    private val markersByUserId = mutableMapOf<String, Marker>()
    private val trailsByUserId = mutableMapOf<String, Polyline>()
    private val colorByUserId = mutableMapOf<String, MemberColor>()
    private val userIdsWithHistoryListener = mutableSetOf<String>()
    private val discoveredUserIdsInOrder = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        pairSettings = PairSettings(this)
        locationRepository = LocationRepository(pairSettings.pairCode)

        binding.pairCodeLabel.text =
            getString(R.string.sharing_in_room, pairSettings.pairCode)

        binding.toggleTrailsButton.setOnClickListener { toggleTrailVisibility() }
        binding.recenterButton.setOnClickListener { fitCameraToEveryone() }
        binding.stopSharingButton.setOnClickListener { stopSharingAndReturnToPairing() }

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(readyMap: GoogleMap) {
        googleMap = readyMap
        startListeningForEveryone()
    }

    private fun startListeningForEveryone() {
        locationRepository.listenToMembers { members ->
            members.forEach { member -> applyMemberLiveState(member) }
        }
    }

    private fun applyMemberLiveState(member: MemberLiveState) {
        val map = googleMap ?: return
        val liveLocation = member.liveLocation ?: return
        val memberColor = colorForUser(member.userId)
        val position = LatLng(liveLocation.latitude, liveLocation.longitude)

        val existingMarker = markersByUserId[member.userId]
        if (existingMarker == null) {
            val newMarker = map.addMarker(
                MarkerOptions()
                    .position(position)
                    .title(member.displayName)
                    .icon(BitmapDescriptorFactory.defaultMarker(memberColor.markerHue))
            )
            if (newMarker != null) markersByUserId[member.userId] = newMarker
        } else {
            existingMarker.position = position
            existingMarker.title = member.displayName
        }

        subscribeToHistoryOnce(member.userId)
        centerOnFirstLocation(position)
    }

    private fun subscribeToHistoryOnce(userId: String) {
        if (userIdsWithHistoryListener.contains(userId)) return
        userIdsWithHistoryListener.add(userId)

        locationRepository.listenToMemberHistory(userId) { historyPoints ->
            drawTrail(userId, historyPoints)
        }
    }

    private fun drawTrail(userId: String, historyPoints: List<LocationPoint>) {
        val map = googleMap ?: return
        val trailLatLngs = historyPoints.map { LatLng(it.latitude, it.longitude) }
        val memberColor = colorForUser(userId)

        val existingTrail = trailsByUserId[userId]
        if (existingTrail == null) {
            val newTrail = map.addPolyline(
                PolylineOptions()
                    .addAll(trailLatLngs)
                    .color(memberColor.trailColor)
                    .width(TRAIL_WIDTH_PIXELS)
            )
            newTrail.isVisible = trailsAreVisible
            trailsByUserId[userId] = newTrail
        } else {
            existingTrail.points = trailLatLngs
        }
    }

    private fun toggleTrailVisibility() {
        trailsAreVisible = !trailsAreVisible
        trailsByUserId.values.forEach { trail -> trail.isVisible = trailsAreVisible }
        binding.toggleTrailsButton.text = getString(
            if (trailsAreVisible) R.string.hide_trails else R.string.show_trails
        )
    }

    private fun centerOnFirstLocation(position: LatLng) {
        if (hasCenteredOnFirstLocation) return
        hasCenteredOnFirstLocation = true
        googleMap?.moveCamera(
            CameraUpdateFactory.newLatLngZoom(position, INITIAL_MAP_ZOOM)
        )
    }

    private fun fitCameraToEveryone() {
        val map = googleMap ?: return
        val allMarkerPositions = markersByUserId.values.map { it.position }
        if (allMarkerPositions.isEmpty()) return

        if (allMarkerPositions.size == 1) {
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(allMarkerPositions.first(), INITIAL_MAP_ZOOM)
            )
            return
        }

        val boundsBuilder = LatLngBounds.Builder()
        allMarkerPositions.forEach { boundsBuilder.include(it) }
        map.animateCamera(
            CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), CAMERA_BOUNDS_PADDING_PIXELS)
        )
    }

    private fun colorForUser(userId: String): MemberColor {
        colorByUserId[userId]?.let { return it }
        if (!discoveredUserIdsInOrder.contains(userId)) {
            discoveredUserIdsInOrder.add(userId)
        }
        val assignmentIndex = discoveredUserIdsInOrder.indexOf(userId) % MEMBER_COLOR_PALETTE.size
        val assignedColor = MEMBER_COLOR_PALETTE[assignmentIndex]
        colorByUserId[userId] = assignedColor
        return assignedColor
    }

    private fun stopSharingAndReturnToPairing() {
        LocationSharingService.stopSharing(this)
        finish()
    }

    override fun onDestroy() {
        locationRepository.detachAllListeners()
        super.onDestroy()
    }

    /** Pairs a map-marker hue with a matching trail-line colour for one person. */
    private data class MemberColor(val markerHue: Float, val trailColor: Int)

    companion object {
        private const val INITIAL_MAP_ZOOM = 15f
        private const val TRAIL_WIDTH_PIXELS = 10f
        private const val CAMERA_BOUNDS_PADDING_PIXELS = 160

        // Distinct colours so the two people never look the same on the map.
        private val MEMBER_COLOR_PALETTE = listOf(
            MemberColor(BitmapDescriptorFactory.HUE_AZURE, Color.rgb(30, 136, 229)),
            MemberColor(BitmapDescriptorFactory.HUE_ROSE, Color.rgb(216, 27, 96)),
            MemberColor(BitmapDescriptorFactory.HUE_GREEN, Color.rgb(67, 160, 71)),
            MemberColor(BitmapDescriptorFactory.HUE_ORANGE, Color.rgb(251, 140, 0))
        )
    }
}
