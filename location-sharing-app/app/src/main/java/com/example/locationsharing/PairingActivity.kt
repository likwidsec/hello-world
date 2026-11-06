package com.example.locationsharing

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.locationsharing.databinding.ActivityPairingBinding
import com.google.firebase.auth.FirebaseAuth

/**
 * The first screen. The person types a display name and a shared pair code
 * (both phones enter the same code to meet in the same room), grants location
 * permission, and then starts sharing. After that this screen hands off to the
 * map.
 */
class PairingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPairingBinding
    private lateinit var pairSettings: PairSettings

    private val requestForegroundPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grantedResults ->
        val fineLocationGranted =
            grantedResults[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (fineLocationGranted) {
            requestBackgroundLocationIfNeeded()
        } else {
            showMessage(getString(R.string.location_permission_required))
        }
    }

    private val requestBackgroundLocationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // Whether or not "allow all the time" was granted, we can start sharing.
        // Background permission only affects updates while the app is fully closed.
        startSharingAndOpenMap()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPairingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        pairSettings = PairSettings(this)
        binding.displayNameInput.setText(pairSettings.displayName)
        binding.pairCodeInput.setText(pairSettings.pairCode)

        binding.startSharingButton.setOnClickListener { onStartSharingTapped() }
    }

    private fun onStartSharingTapped() {
        val enteredDisplayName = binding.displayNameInput.text.toString().trim()
        val enteredPairCode = binding.pairCodeInput.text.toString().trim()

        if (enteredDisplayName.isBlank() || enteredPairCode.isBlank()) {
            showMessage(getString(R.string.name_and_code_required))
            return
        }

        pairSettings.displayName = enteredDisplayName
        pairSettings.pairCode = enteredPairCode

        signInThenSaveProfile(enteredDisplayName, enteredPairCode)
    }

    private fun signInThenSaveProfile(displayName: String, pairCode: String) {
        FirebaseAuth.getInstance().signInAnonymously()
            .addOnSuccessListener {
                val signedInUserId = FirebaseAuth.getInstance().currentUser?.uid
                if (signedInUserId != null) {
                    LocationRepository(pairCode).saveDisplayName(signedInUserId, displayName)
                }
                requestLocationPermissions()
            }
            .addOnFailureListener {
                showMessage(getString(R.string.sign_in_failed))
            }
    }

    private fun requestLocationPermissions() {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        requestForegroundPermissions.launch(permissionsToRequest.toTypedArray())
    }

    private fun requestBackgroundLocationIfNeeded() {
        val backgroundAlreadyGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> startSharingAndOpenMap()
            backgroundAlreadyGranted -> startSharingAndOpenMap()
            else -> requestBackgroundLocationPermission.launch(
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            )
        }
    }

    private fun startSharingAndOpenMap() {
        LocationSharingService.startSharing(this)
        startActivity(Intent(this, MapActivity::class.java))
        finish()
    }

    private fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
