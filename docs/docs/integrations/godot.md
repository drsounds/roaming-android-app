---
sidebar_position: 4
title: Godot
---

# Godot

In Godot 4, write an Android plugin (Godot Android plugin v2) that registers the receiver and emits a signal with the JSON payload.

**Plugin (Kotlin)**

```kotlin
class RoamingPlugin(godot: Godot) : GodotPlugin(godot) {
    override fun getPluginName() = "Roaming"

    override fun getPluginSignals() = setOf(SignalInfo("motion", String::class.java))

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            intent.getStringExtra("data")?.let { emitSignal("motion", it) }
        }
    }

    override fun onMainResume() {
        ContextCompat.registerReceiver(
            activity!!, receiver,
            IntentFilter("se.spacify.app.roaming.event.MOTION"),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    override fun onMainPause() {
        activity?.unregisterReceiver(receiver)
    }
}
```

**GDScript**

```gdscript
extends Node

func _ready() -> void:
    if Engine.has_singleton("Roaming"):
        Engine.get_singleton("Roaming").connect("motion", _on_motion)

func _on_motion(json: String) -> void:
    var event: Dictionary = JSON.parse_string(json)
    var state: Dictionary = event["new"]
    match state["motion_type"]:
        "jumping":
            $Player.jump(state["intensity"])
        "walking", "running":
            $Player.speed = state["vector"]["z"]
```
