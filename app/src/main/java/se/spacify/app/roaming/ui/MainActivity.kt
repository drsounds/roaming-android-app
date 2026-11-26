package se.spacify.app.roaming.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import se.spacify.app.roaming.R
import se.spacify.app.roaming.databinding.ActivityMainBinding
import se.spacify.app.roaming.motion.MotionBus
import se.spacify.app.roaming.motion.StepSensorSource
import se.spacify.app.roaming.service.MotionService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val required = requiredPermissions()
            if (required.all { results[it] == true || isGranted(it) }) {
                startTracking()
            } else {
                Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val hasStepDetector = StepSensorSource.isAvailable(this)
        if (!hasStepDetector) {
            binding.switchSteps.isChecked = false
            binding.switchSteps.isEnabled = false
            binding.switchSteps.text = getString(R.string.no_step_sensor)
            binding.switchCamera.isChecked = true
        }

        binding.buttonToggle.setOnClickListener {
            if (MotionBus.running.value) MotionService.stop(this) else requestAndStart()
        }
        binding.buttonOverlay.setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    MotionBus.running.collect { running ->
                        binding.buttonToggle.setText(if (running) R.string.stop else R.string.start)
                        binding.textStatus.setText(if (running) R.string.status_running else R.string.status_idle)
                        binding.switchSteps.isEnabled = !running && hasStepDetector
                        binding.switchCamera.isEnabled = !running
                    }
                }
                launch {
                    MotionBus.latest.collect { event ->
                        binding.textEvent.text = event?.toJson()?.toString(2) ?: "—"
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        binding.buttonOverlay.visibility = if (Settings.canDrawOverlays(this)) View.GONE else View.VISIBLE
    }

    private fun requestAndStart() {
        if (!binding.switchSteps.isChecked && !binding.switchCamera.isChecked) {
            Toast.makeText(this, R.string.choose_input, Toast.LENGTH_SHORT).show()
            return
        }
        val missing = requiredPermissions() + optionalPermissions()
        val toRequest = missing.filterNot(::isGranted)
        if (toRequest.isEmpty()) startTracking() else permissionLauncher.launch(toRequest.toTypedArray())
    }

    private fun startTracking() {
        MotionService.start(this, binding.switchSteps.isChecked, binding.switchCamera.isChecked)
    }

    private fun requiredPermissions(): List<String> = buildList {
        if (binding.switchSteps.isChecked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        if (binding.switchCamera.isChecked) add(Manifest.permission.CAMERA)
    }

    private fun optionalPermissions(): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun isGranted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
