package dev.arisu.shrine.feature.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Markunread
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.arisu.shrine.core.components.ArisuConfirmDialog
import dev.arisu.shrine.core.components.ArisuPrimaryButton
import dev.arisu.shrine.core.components.ArisuTextField
import dev.arisu.shrine.core.components.ArisuTopBar
import dev.arisu.shrine.core.components.CodeLabel
import dev.arisu.shrine.core.components.FadeIn
import dev.arisu.shrine.core.components.NeuSurface
import dev.arisu.shrine.core.components.RedDot
import dev.arisu.shrine.core.components.SectionCard
import dev.arisu.shrine.core.components.SettingNavRow
import dev.arisu.shrine.core.components.SettingSwitchRow
import dev.arisu.shrine.core.components.ShrineEmptyState
import dev.arisu.shrine.core.components.ShrineLoadingState
import dev.arisu.shrine.core.components.ShrineNoticeBar
import dev.arisu.shrine.core.design.InkFaint
import dev.arisu.shrine.core.design.InkTitle
import dev.arisu.shrine.data.model.Notice
import dev.arisu.shrine.data.remote.toApiMessage
import dev.arisu.shrine.data.repo.Shrine
import kotlinx.coroutines.launch

object NoticeDetailHolder {

    var current: Notice? = null
}

@Composable
fun NoticeDetailScreen(
    noticeId: String,
    onBack: () -> Unit,
    onOpenRoute: (String) -> Unit,
    onChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {

    var notice by remember {
        mutableStateOf(NoticeDetailHolder.current?.takeIf { it.id == noticeId })
    }
    var loadFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var replyOpen by rememberSaveable { mutableStateOf(false) }

    var reply by rememberSaveable { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var bar by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(noticeId) {
        if (notice == null) {
            loadFailed = if (noticeId.isEmpty()) true else try {
                notice = Shrine.notice(noticeId)
                notice == null
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                true
            }
        }
    }

    LaunchedEffect(notice?.id) {

        notice?.let { n ->

            var ok = false
            for (attempt in 0 until 3) {
                if (runCatching { Shrine.markRead(n.id) }.isSuccess) {
                    ok = true
                    break
                }
                kotlinx.coroutines.delay(400L * (attempt + 1))
            }
            if (ok) {
                notice = n.copy(read = true)

                onChanged()
            }
        }
    }

    Column(modifier.fillMaxSize().imePadding()) {
        ArisuTopBar(
            title = "通知详情",
            subtitle = notice?.kind?.label,
            onBack = onBack,
        )

        ShrineNoticeBar(
            text = bar,
            onDismiss = { bar = null },
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = 6.dp),
        )

        if (notice == null) {
            if (loadFailed) {

                ShrineEmptyState(
                    note = "通知内容已不可用，请返回社务箱重新打开",
                    onClick = {
                        loadFailed = false
                        scope.launch {
                            loadFailed = if (noticeId.isEmpty()) true else try {
                                notice = Shrine.notice(noticeId)
                                notice == null
                            } catch (e: Exception) {
                                if (e is kotlinx.coroutines.CancellationException) throw e
                                true
                            }
                        }
                    },
                )
            } else {
                ShrineLoadingState()
            }
            return@Column
        }

        val current = notice ?: return@Column

        FadeIn {
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                Spacer(Modifier.height(10.dp))
                NeuSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            KindBadge(kind = current.kind)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = current.kind.label,
                                style = MaterialTheme.typography.titleSmall,
                                color = InkTitle,
                            )
                            Spacer(Modifier.weight(1f))
                            CodeLabel(text = current.id, color = InkFaint)
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = current.createdAt,
                                style = MaterialTheme.typography.labelSmall,
                                color = InkFaint,
                            )
                            if (!current.read) {
                                Spacer(Modifier.width(8.dp))
                                RedDot(size = 6.dp)
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = current.summary,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 30.sp,
                        )
                    }
                }

                SectionCard(modifier = Modifier.padding(top = 10.dp)) {
                    SettingSwitchRow(
                        title = "收藏通知",
                        checked = current.starred,
                        icon = Icons.Rounded.Star,
                        onCheckedChange = { want ->

                            notice = current.copy(starred = want)
                            scope.launch {
                                try {
                                    Shrine.starNotice(current.id, want)
                                    onChanged()
                                } catch (e: Exception) {
                                    notice = current
                                    bar = e.toApiMessage()
                                }
                            }
                        },
                    )
                    if (current.read) {
                        SettingNavRow(
                            title = "标为未读",
                            icon = Icons.Rounded.Markunread,
                            onClick = {
                                notice = current.copy(read = false)
                                scope.launch {
                                    try {
                                        Shrine.markUnread(current.id)
                                        onChanged()
                                    } catch (e: Exception) {
                                        notice = current.copy(read = true)
                                        bar = e.toApiMessage()
                                    }
                                }
                            },
                        )
                    }
                    SettingNavRow(
                        title = "删除通知",
                        icon = Icons.Rounded.Delete,
                        danger = true,
                        showDivider = current.read,
                        onClick = { confirmDelete = true },
                    )
                }

                val route = current.targetRoute
                if (route != null) {
                    Spacer(Modifier.height(14.dp))
                    ArisuPrimaryButton(
                        text = when {
                            route.startsWith("post/") -> "前往帖子"
                            route.startsWith("ema/") -> "前往绘马"
                            route.startsWith("user/") -> "前往主页"
                            route.startsWith("report/") -> "前往处理举报"
                            route == "staff" -> "前往奉仕会"
                            route == "changelog" -> "前往神社日志"
                            else -> "前往查看"
                        },
                        onClick = { onOpenRoute(route) },
                    )
                }

                if (current.kind == dev.arisu.shrine.data.model.NoticeKind.REPLY && route?.startsWith("post/") == true) {
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = { replyOpen = !replyOpen }) {
                        Text(if (replyOpen) "收起回复框" else "快捷回复")
                    }
                    if (replyOpen) {
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ArisuTextField(
                                value = reply,
                                onValueChange = { if (it.length <= 2000) reply = it },
                                placeholder = "回复这篇帖子…",
                                modifier = Modifier.weight(1f),

                                singleLine = false,
                                maxLines = 5,
                            )
                            Spacer(Modifier.width(8.dp))
                            IconButton(
                                enabled = reply.isNotBlank() && !sending,
                                onClick = {
                                    sending = true
                                    scope.launch {
                                        try {

                                            val postNo = route.removePrefix("post/")
                                            Shrine.addComment(postNo, reply.trim())
                                            reply = ""
                                            replyOpen = false
                                            bar = "回复已发送"
                                        } catch (e: Exception) {
                                            bar = e.toApiMessage()
                                        }
                                        sending = false
                                    }
                                },
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.Send,
                                    contentDescription = "发送",
                                    tint = if (reply.isNotBlank()) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (confirmDelete) {
        ArisuConfirmDialog(
            title = "删除这条通知？",
            body = "删除后无法恢复。",
            confirmText = "删除",
            dismissText = "取消",
            onConfirm = {
                val target = notice ?: return@ArisuConfirmDialog
                Shrine.deleteNotice(target.id)
                onChanged()
                onBack()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun KindBadge(kind: dev.arisu.shrine.data.model.NoticeKind) {
    val (icon, color) = noticeKindVisual(kind)
    Box(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
    }
}
