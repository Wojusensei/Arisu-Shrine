package dev.arisu.shrine.core.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.arisu.shrine.core.design.Vermilion
import dev.arisu.shrine.data.remote.toApiMessage
import kotlinx.coroutines.launch

@Composable
fun ArisuConfirmDialog(
    title: String,
    body: String? = null,
    confirmText: String = "确认",
    dismissText: String = "取消",
    reasonHint: String? = null,

    reasonMaxChars: Int = 500,
    enabled: Boolean = true,
    onConfirm: suspend (reason: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (body != null) {
                    Text(body)
                }
                if (reasonHint != null) {
                    Spacer(Modifier.height(10.dp))
                    ArisuTextField(
                        value = reason,
                        onValueChange = { if (it.length <= reasonMaxChars) reason = it },
                        placeholder = reasonHint,
                    )
                }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(text = it, color = Vermilion, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = enabled && !busy && (reasonHint == null || reason.isNotBlank()),
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            onConfirm(reason.trim())
                        } catch (e: Exception) {
                            error = e.toApiMessage()
                        } finally {
                            busy = false
                        }
                    }
                },
            ) { Text(confirmText) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissText) }
        },
    )
}
