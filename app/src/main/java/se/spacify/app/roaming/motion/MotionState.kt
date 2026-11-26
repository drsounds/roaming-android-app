package se.spacify.app.roaming.motion

import org.json.JSONObject

/** The kind of body motion Roaming detected. Serialized as lower_snake_case strings. */
enum class MotionType(val wire: String) {
    IDLE("idle"),
    STEP("step"),
    WALKING("walking"),
    RUNNING("running"),
    JUMPING("jumping"),
    SQUATTING("squatting"),
    LEANING("leaning"),
    ARMS_RAISED("arms_raised");

    companion object {
        fun fromWire(value: String?): MotionType = entries.firstOrNull { it.wire == value } ?: IDLE
    }
}

/**
 * Direction of the motion, from the user's point of view.
 * Each direction maps to a unit vector so games can treat Roaming like an accelerometer / joystick:
 * x = right(+)/left(-), y = up(+)/down(-), z = forward(+)/backward(-).
 */
enum class Direction(val wire: String, val x: Float, val y: Float, val z: Float) {
    NONE("none", 0f, 0f, 0f),
    FORWARD("forward", 0f, 0f, 1f),
    BACKWARD("backward", 0f, 0f, -1f),
    LEFT("left", -1f, 0f, 0f),
    RIGHT("right", 1f, 0f, 0f),
    UP("up", 0f, 1f, 0f),
    DOWN("down", 0f, -1f, 0f);

    companion object {
        fun fromWire(value: String?): Direction = entries.firstOrNull { it.wire == value } ?: NONE
    }
}

/** One motion sample: `{ motion_type, direction, intensity }`. Intensity is clamped to 0..1. */
data class MotionState(
    val motionType: MotionType,
    val direction: Direction,
    val intensity: Float,
) {
    init {
        require(!intensity.isNaN()) { "intensity must be a number" }
    }

    /** Acceleration-style vector: direction scaled by intensity. */
    val vectorX: Float get() = direction.x * intensity
    val vectorY: Float get() = direction.y * intensity
    val vectorZ: Float get() = direction.z * intensity

    fun toJson(): JSONObject = JSONObject()
        .put(KEY_MOTION_TYPE, motionType.wire)
        .put(KEY_DIRECTION, direction.wire)
        .put(KEY_INTENSITY, intensity.toDouble())
        .put(
            KEY_VECTOR,
            JSONObject()
                .put("x", vectorX.toDouble())
                .put("y", vectorY.toDouble())
                .put("z", vectorZ.toDouble())
        )

    companion object {
        const val KEY_MOTION_TYPE = "motion_type"
        const val KEY_DIRECTION = "direction"
        const val KEY_INTENSITY = "intensity"
        const val KEY_VECTOR = "vector"

        val IDLE = MotionState(MotionType.IDLE, Direction.NONE, 0f)

        fun of(type: MotionType, direction: Direction, intensity: Float) =
            MotionState(type, direction, intensity.coerceIn(0f, 1f))

        fun fromJson(json: JSONObject): MotionState = of(
            MotionType.fromWire(json.optString(KEY_MOTION_TYPE)),
            Direction.fromWire(json.optString(KEY_DIRECTION)),
            json.optDouble(KEY_INTENSITY, 0.0).toFloat(),
        )
    }
}

/** A transition, broadcast as `{ new: {...}, prev: {...} }`. */
data class MotionEvent(
    val new: MotionState,
    val prev: MotionState,
    val timestampMillis: Long,
    val source: Source,
) {
    enum class Source(val wire: String) { STEP_SENSOR("step_sensor"), CAMERA("camera") }

    fun toJson(): JSONObject = JSONObject()
        .put("new", new.toJson())
        .put("prev", prev.toJson())
        .put("timestamp", timestampMillis)
        .put("source", source.wire)
}
