package com.bfalls.suntimealerts.alarm.presentation.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private const val FEEDBACK_EMAIL_ADDRESS = "suntimealerts@gmail.com"

@Composable
internal fun FeedbackDialog(
    onDismiss: () -> Unit,
    onEmailUnavailable: () -> Unit
) {
    val context = LocalContext.current
    var feedbackMessage by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send feedback") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "This opens your email app with a draft to $FEEDBACK_EMAIL_ADDRESS."
                )
                OutlinedTextField(
                    value = feedbackMessage,
                    onValueChange = { feedbackMessage = it },
                    label = { Text("Feedback") },
                    placeholder = { Text("What happened? What would you change?") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val body = buildFeedbackBody(
                        message = feedbackMessage.trim(),
                        context = context
                    )
                    val intent = Intent(
                        Intent.ACTION_SENDTO,
                        Uri.parse("mailto:$FEEDBACK_EMAIL_ADDRESS")
                    ).apply {
                        putExtra(Intent.EXTRA_SUBJECT, "Suntime Alerts alpha feedback")
                        putExtra(Intent.EXTRA_TEXT, body)
                    }

                    if (intent.resolveActivity(context.packageManager) != null) {
                        try {
                            context.startActivity(intent)
                            onDismiss()
                        } catch (_: ActivityNotFoundException) {
                            onEmailUnavailable()
                        }
                    } else {
                        onEmailUnavailable()
                    }
                }
            ) {
                Text("Open email")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun buildFeedbackBody(
    message: String,
    context: android.content.Context
): String {
    val packageInfo = context.packageManager.getPackageInfoCompat(context.packageName)
    val versionName = packageInfo?.versionName ?: "unknown"
    val versionCode = packageInfo?.longVersionCode?.toString() ?: "unknown"
    val messageSection = if (message.isBlank()) {
        "(Please describe the issue or suggestion here.)"
    } else {
        message
    }

    return """
$messageSection

---
App version: $versionName ($versionCode)
Android version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})
Device: ${Build.MANUFACTURER} ${Build.MODEL}
    """.trimIndent()
}

private fun PackageManager.getPackageInfoCompat(packageName: String) =
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            getPackageInfo(packageName, 0)
        }
    }.getOrNull()
