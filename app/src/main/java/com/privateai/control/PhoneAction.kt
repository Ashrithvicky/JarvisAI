package com.privateai.control

enum class SettingsTarget { WIFI, BLUETOOTH, MOBILE_DATA }

enum class MediaApp { SPOTIFY, YOUTUBE }

/** A phone-level action Jarvis can perform for the user. */
sealed interface PhoneAction {
    data class OpenApp(val name: String) : PhoneAction
    data object GoBack : PhoneAction
    data object GoHome : PhoneAction
    data object ShowRecents : PhoneAction
    data class Flashlight(val on: Boolean) : PhoneAction
    data class Volume(val direction: Int) : PhoneAction
    data class AutoRotate(val on: Boolean) : PhoneAction
    data class SetAlarm(val hour: Int, val minute: Int) : PhoneAction
    data class AddEvent(
        val title: String, val month: Int, val day: Int,
        val startHour: Int, val startMinute: Int, val endHour: Int, val endMinute: Int
    ) : PhoneAction
    data class OpenSettings(val target: SettingsTarget) : PhoneAction
    data class PlayMedia(val query: String, val app: MediaApp) : PhoneAction

    companion object {
        private val OPEN = Regex("^(?:please\\s+)?(?:open|launch|start)\\s+(.+)$")
        private const val MONTHS = "january|february|march|april|may|june|july|august|september|october|november|december"
        private val MONTH_DAY = Regex("($MONTHS)\\s+(\\d{1,2})(?:st|nd|rd|th)?")
        private val DAY_MONTH = Regex("(\\d{1,2})(?:st|nd|rd|th)?\\s+(?:of\\s+)?($MONTHS)")
        private val RANGE = Regex(
            "(\\d{1,2})(?::(\\d{2}))?\\s*(?:([ap])\\.?m\\.?)?\\s*(?:to|until|till|-)\\s*(\\d{1,2})(?::(\\d{2}))?\\s*(?:([ap])\\.?m\\.?)?"
        )
        private val SINGLE = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(?:([ap])\\.?m\\.?)?")
        private val ALARM_TIME = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*([ap])\\.?m\\.?|(\\d{1,2}):(\\d{2})")
        private val TITLE = Regex("\\b(?:name it|named|called|titled|title it)\\s+(.+)$")
        private val PLAY = Regex("^play\\s+(.+)$")
        private val PLAY_IN = Regex("\\s+(?:in|on|using|with)\\s+(youtube|spotify)$")

        /** Returns the action for a spoken command, or null if it is a normal chat message. */
        fun parse(command: String): PhoneAction? {
            val text = command.lowercase().trim().trimEnd('.', '!', '?')
            val plain = text.removePrefix("please ").removePrefix("can you ").removePrefix("could you ").trim()
            when (plain) {
                "go back", "back", "press back" -> return GoBack
                "go home", "home", "home screen", "go to home screen" -> return GoHome
                "recent apps", "show recent apps", "show recents" -> return ShowRecents
            }
            val off = Regex("\\b(off|disable|stop)\\b").containsMatchIn(plain)
            PLAY.find(plain)?.let { return play(it.groupValues[1]) }
            return when {
                Regex("\\b(flash ?light|torch)\\b").containsMatchIn(plain) -> Flashlight(!off)
                Regex("\\bauto[- ]?rotat").containsMatchIn(plain) -> AutoRotate(!off && !plain.contains("lock"))
                Regex("\\bvolume\\b|\\bmute\\b|\\blouder\\b|\\bquieter\\b").containsMatchIn(plain) -> volume(plain)
                Regex("\\bwi-?\\s?fi\\b").containsMatchIn(plain) -> OpenSettings(SettingsTarget.WIFI)
                plain.contains("bluetooth") -> OpenSettings(SettingsTarget.BLUETOOTH)
                plain.contains("mobile data") || plain.contains("cellular data") -> OpenSettings(SettingsTarget.MOBILE_DATA)
                Regex("\\balarm\\b").containsMatchIn(plain) -> alarm(plain)
                Regex("\\b(event|meeting|appointment)\\b").containsMatchIn(plain) -> event(plain) ?: openApp(plain)
                else -> openApp(plain)
            }
        }

        private fun play(request: String): PhoneAction? {
            val target = PLAY_IN.find(request)
            val query = (if (target != null) request.removeRange(target.range) else request).trim()
            if (query.isEmpty()) return null
            val app = when {
                target?.groupValues?.get(1) == "youtube" -> MediaApp.YOUTUBE
                target != null -> MediaApp.SPOTIFY
                Regex("\\b(podcast|video|episode|interview)\\b").containsMatchIn(query) -> MediaApp.YOUTUBE
                else -> MediaApp.SPOTIFY
            }
            return PlayMedia(query, app)
        }

        private fun openApp(text: String): PhoneAction? = OPEN.find(text)?.groupValues?.get(1)?.trim()
            ?.removePrefix("the ")?.removeSuffix(" app")?.takeIf { it.isNotBlank() }
            ?.let(::OpenApp)

        private fun volume(text: String): PhoneAction = when {
            Regex("\\b(mute|down|decrease|lower|reduce|quieter)\\b").containsMatchIn(text) -> Volume(-1)
            else -> Volume(1)
        }

        private fun alarm(text: String): PhoneAction? {
            val m = ALARM_TIME.find(text) ?: return null
            val v = m.groupValues
            val hour: Int
            val minute: Int
            if (v[3].isNotEmpty()) {
                hour = to24(v[1].toInt(), v[3], false)
                minute = v[2].ifEmpty { "0" }.toInt()
            } else {
                hour = v[4].toInt()
                minute = v[5].toInt()
            }
            return if (hour in 0..23 && minute in 0..59) SetAlarm(hour, minute) else null
        }

        private fun event(text: String): PhoneAction? {
            val titleMatch = TITLE.find(text)
            val title = titleMatch?.groupValues?.get(1)?.trim()?.trimEnd('.', '!', ',')?.takeIf { it.isNotBlank() }
                ?.replaceFirstChar { it.uppercase() } ?: "Event"
            val body = if (titleMatch != null) text.substring(0, titleMatch.range.first) else text

            val md = MONTH_DAY.find(body)
            val dm = if (md == null) DAY_MONTH.find(body) else null
            val month: String
            val day: Int
            val dateRange: IntRange
            when {
                md != null -> { month = md.groupValues[1]; day = md.groupValues[2].toInt(); dateRange = md.range }
                dm != null -> { month = dm.groupValues[2]; day = dm.groupValues[1].toInt(); dateRange = dm.range }
                else -> return null
            }
            if (day !in 1..31) return null
            val rest = body.removeRange(dateRange)

            var sh = 9; var sm = 0; var eh = 10; var em = 0
            val range = RANGE.find(rest)
            if (range != null) {
                val v = range.groupValues
                val endAp = v[6]
                sh = to24(v[1].toInt(), v[3].ifEmpty { endAp }, true)
                sm = v[2].ifEmpty { "0" }.toInt()
                eh = to24(v[4].toInt(), endAp, true)
                em = v[5].ifEmpty { "0" }.toInt()
                if (eh * 60 + em <= sh * 60 + sm) eh = (eh + 12).coerceAtMost(23)
            } else {
                SINGLE.find(rest)?.takeIf { it.groupValues[2].isNotEmpty() || it.groupValues[3].isNotEmpty() }?.let {
                    sh = to24(it.groupValues[1].toInt(), it.groupValues[3], true)
                    sm = it.groupValues[2].ifEmpty { "0" }.toInt()
                    eh = (sh + 1).coerceAtMost(23); em = sm
                }
            }
            return AddEvent(title, MONTHS.split('|').indexOf(month) + 1, day, sh, sm, eh, em)
        }

        /** [guessAfternoon]: bare hours 1-7 are read as PM, matching how people say meeting times. */
        private fun to24(hour: Int, ampm: String, guessAfternoon: Boolean): Int = when {
            ampm == "p" -> if (hour < 12) hour + 12 else hour
            ampm == "a" -> if (hour == 12) 0 else hour
            guessAfternoon && hour in 1..7 -> hour + 12
            else -> hour
        }
    }
}
