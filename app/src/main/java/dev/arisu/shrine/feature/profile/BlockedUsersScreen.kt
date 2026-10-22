package dev.arisu.shrine.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.arisu.shrine.core.components.ArisuAvatar
import dev.arisu.shrine.core.components.ArisuTopBar
import dev.arisu.shrine.core.components.HairlineDivider
import dev.arisu.shrine.core.components.RoleTag
import dev.arisu.shrine.core.components.ShrineEmptyState
import dev.arisu.shrine.core.components.ShrineLoadingState
import dev.arisu.shrine.core.components.ShrineNoticeBar
import dev.arisu.shrine.data.model.User
import dev.arisu.shrine.data.model.avatarUrl
import dev.arisu.shrine.data.remote.toApiMessage
import dev.arisu.shrine.data.repo.Shrine
import kotlinx.coroutines.launch

@Composable
fun BlockedUsersScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,

    initial: List<User>? = null,
) {
    var items by remember { mutableStateOf(initial) }
    var failed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<Set<String>>(emptySet()) }
    var notice by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            try {
                items = Shrine.blockedUsers()
                failed = false
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                failed = true
            }
        }
    }
    LaunchedEffect(Unit) { if (initial == null) load() }

    Column(modifier.fillMaxSize()) {
        ArisuTopBar(title = "拉黑名单", onBack = onBack)

        ShrineNoticeBar(
            text = notice,
            onDismiss = { notice = null },
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 6.dp),
        )

        when {
            failed -> ShrineEmptyState(
                note = "加载失败，点击重试",
                onClick = { failed = false; items = null; load() },
            )
            items == null -> ShrineLoadingState()
            items.orEmpty().isEmpty() -> ShrineEmptyState(note = "还没有拉黑任何人")
            else -> {
                val list = items.orEmpty()
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    itemsIndexed(list, key = { _, user -> user.id }) { index, user ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ArisuAvatar(
                                seed = 0,
                                name = user.nickname,
                                size = 44.dp,
                                role = user.role,
                                ring = true,
                                url = user.avatarUrl,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = user.nickname,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(3.dp))
                                RoleTag(user.role)
                            }
                            TextButton(
                                enabled = user.id !in busy,
                                onClick = {
                                    if (user.id in busy) return@TextButton
                                    busy = busy + user.id
                                    scope.launch {
                                        try {
                                            Shrine.unblockUser(user.id)
                                            items = items?.filterNot { it.id == user.id }
                                            notice = "已解除拉黑"
                                        } catch (e: Exception) {
                                            if (e is kotlinx.coroutines.CancellationException) throw e
                                            notice = e.toApiMessage()
                                        }
                                        busy = busy - user.id
                                    }
                                },
                            ) { Text("解除") }
                        }
                        if (index != list.lastIndex) {
                            HairlineDivider(startIndent = 62.dp)
                        }
                    }
                }
            }
        }
    }
}
