package com.vijaychhetry.kidspiano.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Play sessions (Practice, later games) need the wide keyboard.
 * Grown-up tools and Home stay portrait.
 */
@Composable
fun LockScreenOrientation(landscape: Boolean) {
    val activity = LocalContext.current as? Activity ?: return
    DisposableEffect(landscape) {
        activity.requestedOrientation = if (landscape) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        onDispose { }
    }
}
