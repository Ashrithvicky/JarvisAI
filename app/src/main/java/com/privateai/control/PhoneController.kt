package com.privateai.control

import android.content.Context
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.provider.MediaStore
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import android.util.Log
import com.privateai.monitor.VivvaAccessibilityService
import java.util.Calendar

/** Executes device actions that need no accessibility global gesture. Returns the spoken reply, or null on failure. */
object PhoneController {
    private const val TAG = "PhoneController"

    fun perform(context: Context, action: PhoneAction): String? = try {
        when (action) {
            is PhoneAction.Flashlight -> flashlight(context, action.on)
            is PhoneAction.Volume -> volume(context, action.direction)
            is PhoneAction.AutoRotate -> autoRotate(context, action.on)
            is PhoneAction.SetAlarm -> alarm(context, action)
            is PhoneAction.AddEvent -> event(context, action)
            is PhoneAction.OpenSettings -> openSettings(context, action.target)
            is PhoneAction.PlayMedia -> playMedia(context, action)
            else -> null
        }
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "Target app missing for $action", e)
        "I don't have that app on this phone, boss."
    } catch (e: Exception) {
        Log.w(TAG, "Action failed: $action", e)
        null
    }

    private fun launch(context: Context, intent: Intent) =
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    private fun flashlight(context: Context, on: Boolean): String? {
        val cm = context.getSystemService(CameraManager::class.java)
        val id = cm.cameraIdList.firstOrNull {
            val c = cm.getCameraCharacteristics(it)
            c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: return null
        cm.setTorchMode(id, on)
        return if (on) "Flashlight on." else "Flashlight off."
    }

    private fun volume(context: Context, direction: Int): String {
        val audio = context.getSystemService(AudioManager::class.java)
        repeat(3) {
            audio.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                if (direction > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                if (it == 2) AudioManager.FLAG_SHOW_UI else 0
            )
        }
        return if (direction > 0) "Volume up." else "Volume down."
    }

    private fun autoRotate(context: Context, on: Boolean): String {
        if (!Settings.System.canWrite(context)) {
            return "I don't have access to system settings, boss. Allow Modify system settings for Jarvis in the app info."
        }
        Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (on) 1 else 0)
        return if (on) "Auto rotate on." else "Auto rotate off."
    }

    private fun alarm(context: Context, a: PhoneAction.SetAlarm): String {
        launch(
            context,
            Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, a.hour)
                .putExtra(AlarmClock.EXTRA_MINUTES, a.minute)
                .putExtra(AlarmClock.EXTRA_MESSAGE, "Jarvis")
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        )
        return "Alarm set for %d:%02d.".format(a.hour, a.minute)
    }

    private fun event(context: Context, e: PhoneAction.AddEvent): String {
        val now = Calendar.getInstance()
        fun at(year: Int, h: Int, m: Int) = Calendar.getInstance().apply {
            clear(); set(year, e.month - 1, e.day, h, m)
        }.timeInMillis
        var year = now.get(Calendar.YEAR)
        if (at(year, e.endHour, e.endMinute) < now.timeInMillis) year++
        launch(
            context,
            Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.Events.TITLE, e.title)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, at(year, e.startHour, e.startMinute))
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, at(year, e.endHour, e.endMinute))
        )
        (context as? VivvaAccessibilityService ?: VivvaAccessibilityService.instance)?.clickLabel("save")
        return "Adding ${e.title} to your calendar."
    }

    private fun playMedia(context: Context, a: PhoneAction.PlayMedia): String {
        val service = context as? VivvaAccessibilityService ?: VivvaAccessibilityService.instance
        when (a.app) {
            MediaApp.SPOTIFY -> launch(
                context,
                Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
                    .setPackage("com.spotify.music")
                    .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
                    .putExtra(SearchManager.QUERY, a.query)
            )
            MediaApp.YOUTUBE -> {
                val recent = Regex("\\b(recent|latest|new|newest)\\b").containsMatchIn(a.query)
                val url = "https://www.youtube.com/results?search_query=${Uri.encode(a.query)}" +
                    if (recent) "&sp=CAI%3D" else ""
                launch(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage("com.google.android.youtube"))
                service?.clickBestVideo(a.query) ?: return "I opened the search, boss, but I don't have accessibility access to pick the video."
            }
        }
        return "Playing ${a.query}."
    }

    private fun openSettings(context: Context, target: SettingsTarget): String {
        val modern = Build.VERSION.SDK_INT >= 30
        val action = when (target) {
            SettingsTarget.WIFI -> if (modern) Settings.Panel.ACTION_WIFI else Settings.ACTION_WIFI_SETTINGS
            SettingsTarget.BLUETOOTH -> Settings.ACTION_BLUETOOTH_SETTINGS
            SettingsTarget.MOBILE_DATA -> if (modern) Settings.Panel.ACTION_INTERNET_CONNECTIVITY else Settings.ACTION_WIRELESS_SETTINGS
        }
        launch(context, Intent(action))
        return "Android doesn't let apps switch that on. I've opened the panel, boss."
    }
}
