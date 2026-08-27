package mx.ssauroncorp.ecos

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * DrivingTtsManager — Centralized TextToSpeech controller for driving mode.
 *
 * Responsibilities:
 * - Manages a single TextToSpeech instance (init, language, shutdown)
 * - Runs the geofence proximity state machine:
 *     entered → spoke → spoke again (max 2) → exited → reset
 * - Tracks "recently created" reminders to suppress immediate geofence TTS
 *   (user just saved a reminder at their current location — don't read it back)
 * - Provides high-level speak helpers for confirmations, errors, etc.
 *
 * Thread-safety: all mutable state is accessed from the main thread only.
 */
class DrivingTtsManager(private val context: Context) {

    /** Callback fired when a reminder is triggered (entered/exited radius). */
    var onReminderTriggered: ((reminder: Reminder, distance: String) -> Unit)? = null

    companion object {
        private const val TAG = "DrivingTtsManager"
        private const val MAX_INSIDE_SPOKEN = 2
        private const val EXIT_DISTANCE_MULTIPLIER = 3.0
    }

    // ── TextToSpeech ────────────────────────────────────────────────
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    // ── Geofence state machine ──────────────────────────────────────
    private val insideReminders = mutableSetOf<String>()   // currently inside radius
    private val spokenCount = mutableMapOf<String, Int>()   // times spoken while inside
    private val exitSpoken = mutableSetOf<String>()         // already spoken on exit

    // ── Recently-created suppress ───────────────────────────────────
    // IDs of reminders just saved — skip the first geofence TTS cycle for them
    private val recentlyCreatedIds = mutableSetOf<String>()

    // ── Lifecycle ───────────────────────────────────────────────────

    /**
     * Initialize TTS asynchronously. Safe to call multiple times — only the first call matters.
     */
    fun initialize() {
        if (tts != null) return
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.getDefault())
                ttsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                           result != TextToSpeech.LANG_NOT_SUPPORTED
                if (ttsReady) {
                    Log.d(TAG, "TTS initialized — language ${Locale.getDefault()}")
                } else {
                    Log.e(TAG, "TTS language not supported")
                }
            } else {
                Log.e(TAG, "TTS init failed (status=$status)")
            }
        }
    }

    /** Stop any in-progress utterance. */
    fun stop() {
        tts?.stop()
    }

    /** Release TTS resources. Call when driving mode ends or the host is destroyed. */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
    }

    // ── State machine ───────────────────────────────────────────────

    /** Reset all state for a fresh driving-mode session. */
    fun reset() {
        insideReminders.clear()
        spokenCount.clear()
        exitSpoken.clear()
        recentlyCreatedIds.clear()
    }

    /** Mark a reminder ID as "just created" — the next geofence cycle will skip TTS for it. */
    fun markRecentlyCreated(reminderId: String) {
        recentlyCreatedIds.add(reminderId)
    }

    /**
     * Process a list of reminders against [currentLocation] and speak as needed.
     *
     * Call this from a location callback (every 2-5 s). The state machine decides
     * whether to speak based on entry/exit/repeat rules.
     *
     * @return the nearest [Reminder] (for the overlay UI).
     */
    fun processReminders(
        reminders: List<Reminder>,
        currentLocation: Pair<Double, Double>
    ): Reminder? {
        if (reminders.isEmpty()) return null

        val df = java.text.DecimalFormat("#.#")
        var nearest: Reminder? = null
        var minDistance = Double.MAX_VALUE

        reminders.forEach { reminder ->
            val distance = GeoUtils.haversineM(
                currentLocation.first, currentLocation.second,
                reminder.lat, reminder.lng
            )

            // Track nearest for UI overlay
            if (distance < minDistance) {
                minDistance = distance
                nearest = reminder
            }

            val wasInside = insideReminders.contains(reminder.id)
            val isInside = distance <= reminder.radiusM
            val distanceStr = df.format(distance)

            // ── Recently-created suppression ────────────────────────
            if (recentlyCreatedIds.remove(reminder.id)) {
                // Track entry state silently — don't speak
                if (isInside && !wasInside) {
                    insideReminders.add(reminder.id)
                    exitSpoken.remove(reminder.id)
                    spokenCount[reminder.id] = 0
                }
                return@forEach
            }

            // ── Proximity state machine ─────────────────────────────
            when {
                // ENTERED radius for the first time
                isInside && !wasInside -> {
                    insideReminders.add(reminder.id)
                    exitSpoken.remove(reminder.id)
                    spokenCount[reminder.id] = 1
                    speakReminderAlert(reminder.text, distanceStr)
                    onReminderTriggered?.invoke(reminder, distanceStr)
                }
                // STILL inside — repeat up to MAX_INSIDE_SPOKEN
                isInside && wasInside -> {
                    val count = spokenCount[reminder.id] ?: 0
                    if (count < MAX_INSIDE_SPOKEN) {
                        spokenCount[reminder.id] = count + 1
                        speakReminderAlert(reminder.text, distanceStr)
                    }
                }
                // EXITED radius — speak once on exit
                !isInside && wasInside -> {
                    insideReminders.remove(reminder.id)
                    spokenCount.remove(reminder.id)
                    if (reminder.id !in exitSpoken) {
                        exitSpoken.add(reminder.id)
                        speakReminderAlert(reminder.text, distanceStr)
                        onReminderTriggered?.invoke(reminder, distanceStr)
                    }
                }
                // Far enough away — reset exit flag so re-entry triggers fresh TTS
                !isInside && !wasInside
                        && reminder.id in exitSpoken
                        && distance > reminder.radiusM * EXIT_DISTANCE_MULTIPLIER -> {
                    exitSpoken.remove(reminder.id)
                }
            }
        }

        return nearest
    }

    // ── Speak helpers ───────────────────────────────────────────────

    /**
     * Speak an arbitrary message using QUEUE_FLUSH (interrupts any current utterance).
     * Use for confirmations, errors, voice-command prompts, etc.
     */
    fun speakFlush(text: String, utteranceId: String = "flush_${System.currentTimeMillis()}") {
        if (!ttsReady) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    /**
     * Speak an arbitrary message using QUEUE_ADD (plays after current utterance).
     */
    fun speakAdd(text: String, utteranceId: String = "add_${System.currentTimeMillis()}") {
        if (!ttsReady) return
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
    }

    /**
     * Speak a geofence reminder alert (formatted) via QUEUE_ADD.
     * Use from services or external callers that don't need state-machine tracking.
     */
    fun speakReminderAlert(text: String, distance: String) {
        val speechText = "Recordatorio: $text. Distancia: $distance metros"
        speakAdd(speechText)
    }
}
