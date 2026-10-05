package com.salg.myeye.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * Camera permission, the eye's way:
 * - asks once, right away, on first launch;
 * - re-checks on every resume (so coming back from Settings with it granted works at once);
 * - never re-prompts on its own. The returned function asks again when the person taps the eye,
 *   or opens this app's settings page once the system won't show the dialog anymore.
 *
 * @param onPermission reports the current state; the eye panics while it's false.
 * @return call to ask again.
 */
@Composable
fun rememberCameraPermission(onPermission: (Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val report by rememberUpdatedState(onPermission)
    var asked by rememberSaveable { mutableStateOf(false) }

    fun granted() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

    val launcher = rememberLauncherForActivityResult(RequestPermission()) { report(it) }

    LifecycleResumeEffect(Unit) {
        report(granted())
        onPauseOrDispose { }
    }

    LaunchedEffect(Unit) {
        if (!granted() && !asked) {
            asked = true
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    return remember(activity, launcher) {
        {
            val canStillAsk = !asked ||
                activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == true
            when {
                granted() -> report(true)
                canStillAsk -> {
                    asked = true
                    launcher.launch(Manifest.permission.CAMERA)
                }
                // Permanently denied: the system dialog won't appear anymore.
                else -> context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }
}
