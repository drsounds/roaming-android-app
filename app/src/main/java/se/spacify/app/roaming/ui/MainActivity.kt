package se.spacify.app.roaming.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import se.spacify.app.roaming.R
import se.spacify.app.roaming.databinding.ActivityMainBinding
import se.spacify.app.roaming.gamepad.GamepadBus
import se.spacify.app.roaming.gamepad.GamepadControl
import se.spacify.app.roaming.gamepad.GamepadMapping
import se.spacify.app.roaming.gamepad.GamepadSettings
import se.spacify.app.roaming.gamepad.GamepadStatus
import se.spacify.app.roaming.gamepad.GamepadSupport
import se.spacify.app.roaming.motion.MotionBus
import se.spacify.app.roaming.motion.MotionType
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

    private val discoverableLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            if (results.values.all { it }) requestDiscoverable()
            else Toast.makeText(this, R.string.gamepad_unavailable_permission, Toast.LENGTH_LONG).show()
        }

    private val connectLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            if (results.values.all { it }) showPairedDevices()
            else Toast.makeText(this, R.string.gamepad_unavailable_permission, Toast.LENGTH_LONG).show()
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

        setUpGamepad()

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
                        binding.switchGamepad.isEnabled = !running && GamepadSupport.isSupported()
                    }
                }
                launch {
                    GamepadBus.status.collect { binding.textGamepadStatus.text = describe(it) }
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
        MotionService.start(
            this, binding.switchSteps.isChecked, binding.switchCamera.isChecked, binding.switchGamepad.isChecked,
        )
    }

    private fun requiredPermissions(): List<String> = buildList {
        if (binding.switchSteps.isChecked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        if (binding.switchCamera.isChecked) add(Manifest.permission.CAMERA)
        if (binding.switchGamepad.isChecked) addAll(GamepadSupport.requiredPermissions())
    }

    private fun optionalPermissions(): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun isGranted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    // --- Gamepad mode ---

    private fun setUpGamepad() {
        if (!GamepadSupport.isSupported()) {
            binding.switchGamepad.isChecked = false
            binding.switchGamepad.isEnabled = false
            binding.textGamepadHint.setText(R.string.gamepad_unavailable_old_android)
            return
        }
        binding.switchGamepad.isChecked = GamepadSettings.isEnabled(this)
        binding.gamepadSection.visibility = if (binding.switchGamepad.isChecked) View.VISIBLE else View.GONE
        binding.switchGamepad.setOnCheckedChangeListener { _, checked ->
            GamepadSettings.setEnabled(this, checked)
            binding.gamepadSection.visibility = if (checked) View.VISIBLE else View.GONE
        }
        binding.buttonDiscoverable.setOnClickListener {
            val missing = discoverablePermissions().filterNot(::isGranted)
            if (missing.isEmpty()) requestDiscoverable() else discoverableLauncher.launch(missing.toTypedArray())
        }
        binding.buttonConnectHost.setOnClickListener {
            if (!MotionBus.running.value) {
                Toast.makeText(this, R.string.gamepad_connect_needs_tracking, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val missing = GamepadSupport.requiredPermissions().filterNot(::isGranted)
            if (missing.isEmpty()) showPairedDevices() else connectLauncher.launch(missing.toTypedArray())
        }
        binding.buttonResetMapping.setOnClickListener {
            GamepadSettings.setMapping(this, GamepadMapping.DEFAULT)
            buildMappingRows(GamepadMapping.DEFAULT)
        }
        buildMappingRows(GamepadSettings.load(this))
    }

    /** One row per motion type, each with a dropdown of gamepad controls. */
    private fun buildMappingRows(mapping: GamepadMapping) {
        val rows = binding.mappingRows
        rows.removeAllViews()
        val controls = GamepadControl.entries
        val labels = controls.map { getString(controlLabel(it)) }
        for (type in GamepadMapping.BINDABLE) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            val label = TextView(this).apply {
                setText(motionLabel(type))
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
            }
            val spinner = Spinner(this).apply {
                adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, labels).also {
                    it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
                setSelection(controls.indexOf(mapping.controlFor(type)), false)
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        val current = GamepadSettings.mapping.value
                        val chosen = controls[position]
                        if (current.controlFor(type) != chosen) {
                            GamepadSettings.setMapping(this@MainActivity, current.with(type, chosen))
                        }
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                }
            }
            row.addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(spinner, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f))
            rows.addView(row)
        }
    }

    private fun discoverablePermissions(): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE)
        } else {
            emptyList()
        }

    @SuppressLint("MissingPermission") // Checked by discoverablePermissions() before calling.
    private fun requestDiscoverable() {
        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
            .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120)
        try {
            startActivity(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            Toast.makeText(this, R.string.gamepad_unavailable_no_bluetooth, Toast.LENGTH_LONG).show()
        }
    }

    @SuppressLint("MissingPermission") // Checked by GamepadSupport.requiredPermissions() before calling.
    private fun showPairedDevices() {
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter
        val devices = adapter?.bondedDevices.orEmpty().sortedBy { it.name ?: it.address }
        if (devices.isEmpty()) {
            Toast.makeText(this, R.string.gamepad_no_paired_devices, Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.gamepad_connect_title)
            .setItems(devices.map { it.name ?: it.address }.toTypedArray()) { _, which ->
                MotionService.connectHost(this, devices[which].address)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    @SuppressLint("MissingPermission") // The adapter name is only read when the permission is granted.
    private fun describe(status: GamepadStatus): String = when (status) {
        GamepadStatus.Off -> getString(R.string.gamepad_status_off)
        GamepadStatus.Starting -> getString(R.string.gamepad_status_starting)
        GamepadStatus.WaitingForHost -> {
            val name = if (GamepadSupport.hasPermission(this)) {
                getSystemService(BluetoothManager::class.java)?.adapter?.name
            } else {
                null
            }
            getString(R.string.gamepad_status_waiting, name ?: Build.MODEL)
        }
        is GamepadStatus.Connected -> getString(R.string.gamepad_status_connected, status.hostName)
        is GamepadStatus.Unavailable -> getString(
            when (status.reason) {
                GamepadStatus.Reason.OLD_ANDROID -> R.string.gamepad_unavailable_old_android
                GamepadStatus.Reason.NO_BLUETOOTH -> R.string.gamepad_unavailable_no_bluetooth
                GamepadStatus.Reason.BLUETOOTH_OFF -> R.string.gamepad_unavailable_off
                GamepadStatus.Reason.NO_PERMISSION -> R.string.gamepad_unavailable_permission
                GamepadStatus.Reason.NOT_SUPPORTED -> R.string.gamepad_unavailable_not_supported
            }
        )
    }

    @StringRes
    private fun motionLabel(type: MotionType): Int = when (type) {
        MotionType.IDLE, MotionType.STEP -> R.string.motion_step
        MotionType.WALKING -> R.string.motion_walking
        MotionType.RUNNING -> R.string.motion_running
        MotionType.JUMPING -> R.string.motion_jumping
        MotionType.SQUATTING -> R.string.motion_squatting
        MotionType.LEANING -> R.string.motion_leaning
        MotionType.ARMS_RAISED -> R.string.motion_arms_raised
    }

    @StringRes
    private fun controlLabel(control: GamepadControl): Int = when (control) {
        GamepadControl.NONE -> R.string.control_none
        GamepadControl.LEFT_STICK -> R.string.control_left_stick
        GamepadControl.RIGHT_STICK -> R.string.control_right_stick
        GamepadControl.DPAD -> R.string.control_dpad
        GamepadControl.LEFT_TRIGGER -> R.string.control_left_trigger
        GamepadControl.RIGHT_TRIGGER -> R.string.control_right_trigger
        GamepadControl.A -> R.string.control_a
        GamepadControl.B -> R.string.control_b
        GamepadControl.X -> R.string.control_x
        GamepadControl.Y -> R.string.control_y
        GamepadControl.L1 -> R.string.control_l1
        GamepadControl.R1 -> R.string.control_r1
        GamepadControl.L3 -> R.string.control_l3
        GamepadControl.R3 -> R.string.control_r3
        GamepadControl.SELECT -> R.string.control_select
        GamepadControl.START -> R.string.control_start
    }
}
