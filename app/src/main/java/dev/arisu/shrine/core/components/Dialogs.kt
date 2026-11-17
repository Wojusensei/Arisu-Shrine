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

/**
 * 通用确认弹窗：确认钮内建防抖（在途期间禁用，杜绝连点重复提交），
 * 可选理由输入（管理删除、举报这类必须填理由的场景）——填写内容经 [onConfirm]
 * 的 reason 参数带回（未启用理由时为空串）。
 * onConfirm 是挂起函数——失败时错误原文显示在弹窗里，弹窗保持打开可重试；
 * 成功后的关窗由调用方在 onConfirm 里自己决定。
 * 此前五六个确认弹窗各写一份、全都没有防抖，连点会向服务器发两笔请求。
 */
@Composable
fun ArisuConfirmDialog(
    title: String,
    body: String? = null,
    confirmText: String = "确认",
    dismissText: String = "取消",
    reasonHint: String? = null,
    // 理由上限：举报/管理理由服务端一律 500 字，整页粘贴不该等服务端来拒
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
                        // 理由不必挤在一行里：回车即换行（举报理由、删除理由、申请理由共用）
                        singleLine = false,
                        minLines = 2,
                        maxLines = 5,
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
