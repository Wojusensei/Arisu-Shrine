package dev.arisu.shrine.feature.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.arisu.shrine.core.components.ArisuAvatar
import dev.arisu.shrine.core.components.ArisuTextField
import dev.arisu.shrine.core.components.YinYangOrb
import dev.arisu.shrine.data.model.User
import dev.arisu.shrine.data.model.avatarUrl
import dev.arisu.shrine.data.model.displayAvatarSeed
import dev.arisu.shrine.data.remote.ApiError
import dev.arisu.shrine.data.remote.toApiMessage
import dev.arisu.shrine.data.repo.Shrine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MentionPickerSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onPick: (User) -> Unit,
) {
    if (!visible) return
    var query by remember { mutableStateOf("") }
    var attempt by remember { mutableStateOf(0) }
    var state by remember { mutableStateOf<MentionSearch>(MentionSearch.Idle) }

    val cache = remember {
        object : LinkedHashMap<String, List<User>>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<User>>?) =
                size > 40
        }
    }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        delay(260)
        runCatching { focus.requestFocus() }
        keyboard?.show()
    }

    LaunchedEffect(query, attempt) {
        val q = query.trim().removePrefix("@").trim()
        if (q.isEmpty()) {
            state = MentionSearch.Idle
            return@LaunchedEffect
        }
        cache[q]?.let { users ->
            state = if (users.isEmpty()) MentionSearch.Empty else MentionSearch.Results(users)
            return@LaunchedEffect
        }

        state = MentionSearch.Loading
        delay(220)
        state = try {
            val users = fetchMentionUsers(q)
            cache[q] = users
            if (users.isEmpty()) MentionSearch.Empty else MentionSearch.Results(users)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            MentionSearch.Failed(e.toApiMessage())
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp)
        ) {
            Text(
                text = "提及社员",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            ArisuTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = "搜索昵称或编号",
                fieldModifier = Modifier.focusRequester(focus),
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.heightIn(min = 56.dp, max = 320.dp)) {
                when (val s = state) {
                    MentionSearch.Idle -> item {
                        MentionStatusRow(text = "输入昵称或编号", loading = false)
                    }

                    MentionSearch.Loading -> item {
                        MentionStatusRow(text = "正在查找……", loading = true)
                    }

                    MentionSearch.Empty -> item {
                        MentionStatusRow(text = "未找到相关社员", loading = false)
                    }

                    is MentionSearch.Failed -> item {
                        MentionStatusRow(
                            text = s.message,
                            loading = false,
                            retry = { attempt++ },
                        )
                    }

                    is MentionSearch.Results -> items(s.users) { u ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(u) }
                                .padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ArisuAvatar(
                                seed = u.displayAvatarSeed,
                                name = u.nickname,
                                size = 30.dp,
                                role = u.role,
                                ring = true,
                                url = u.avatarUrl,
                            )
                            Spacer(Modifier.width(9.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(
                                    text = u.nickname,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                )
                                Text(
                                    text = u.id,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MentionStatusRow(
    text: String,
    loading: Boolean,
    retry: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (retry != null) Modifier.clickable { retry?.invoke() } else Modifier)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            YinYangOrb(size = 14.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (retry != null) {
            Text(
                text = "重试",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private suspend fun fetchMentionUsers(q: String): List<User> = try {
    Shrine.searchMentionUsers(q)
} catch (e: ApiError) {
    if (e.code != 429) throw e
    delay(260)
    Shrine.searchMentionUsers(q)
}

private sealed interface MentionSearch {
    data object Idle : MentionSearch
    data object Loading : MentionSearch
    data object Empty : MentionSearch
    data class Results(val users: List<User>) : MentionSearch
    data class Failed(val message: String) : MentionSearch
}
