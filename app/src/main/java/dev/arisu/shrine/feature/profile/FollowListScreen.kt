package dev.arisu.shrine.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import dev.arisu.shrine.core.components.ArisuAvatar
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.arisu.shrine.core.components.ArisuTopBar
import dev.arisu.shrine.core.components.HairlineDivider
import dev.arisu.shrine.core.components.SectionCard
import dev.arisu.shrine.core.components.ShrineEmptyState
import dev.arisu.shrine.data.remote.toApiMessage
import dev.arisu.shrine.core.components.accentRaised
import dev.arisu.shrine.core.components.neuInset
import dev.arisu.shrine.core.design.InkMuted
import dev.arisu.shrine.core.design.NeuInset
import dev.arisu.shrine.data.repo.Shrine
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import dev.arisu.shrine.data.model.User
import dev.arisu.shrine.data.model.avatarUrl
import dev.arisu.shrine.nav.FOLLOW_MODE_FOLLOWING

@Composable
fun FollowListScreen(
    mode: String,
    onBack: () -> Unit,
    onOpenUser: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isFollowing = mode == FOLLOW_MODE_FOLLOWING
    val title = if (isFollowing) "关注" else "粉丝"
    val me by Shrine.me.collectAsState()
    val scope = rememberCoroutineScope()

    var users by remember { mutableStateOf<List<User>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    var failed by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }
    val followState = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(isFollowing, reloadKey) {
        loading = true
        try {
            val list = if (isFollowing) Shrine.followingUsers() else Shrine.followerUsers()
            users = list
            failed = false
            list.forEach { followState[it.id] = if (isFollowing) true else it.following }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e

            if (users.isEmpty()) failed = true
        }
        loading = false
    }

    var busyIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var bar by remember { mutableStateOf<String?>(null) }

    fun toggle(user: User) {
        if (user.id in busyIds) return
        val next = !(followState[user.id] ?: false)
        followState[user.id] = next
        busyIds = busyIds + user.id
        scope.launch {
            try {
                val result = if (next) Shrine.toggleFollow(user.id) else Shrine.unfollow(user.id)
                followState[user.id] = result
            } catch (e: Exception) {
                followState[user.id] = !next
                bar = e.toApiMessage()
            }
            busyIds = busyIds - user.id
        }
    }

    Column(modifier.fillMaxSize()) {
        ArisuTopBar(
            title = title,
            onBack = onBack,
            actions = {
                Text(
                    text = "共 ${users.size} 人",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )

        dev.arisu.shrine.core.components.ShrineNoticeBar(
            text = bar,
            onDismiss = { bar = null },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        if (loading) {
            dev.arisu.shrine.core.components.ShrineLoadingState(note = "少女祈祷中")
        } else if (users.isEmpty() && failed) {
            ShrineEmptyState(note = "加载失败，点击重试", onClick = { reloadKey++ })
        } else if (users.isEmpty()) {
            ShrineEmptyState(note = if (isFollowing) "没有关注" else "没有粉丝")
        } else {
            SectionCard(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(users, key = { _, user -> user.id }) { index, user ->
                        FollowRow(
                            user = user,
                            following = followState[user.id] ?: false,
                            isMe = user.id == me?.id,
                            onOpenUser = onOpenUser,
                            onToggle = { toggle(user) },
                            busy = user.id in busyIds,
                        )
                        if (index != users.lastIndex) {
                            HairlineDivider(startIndent = 62.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FollowRow(
    user: User,
    following: Boolean,
    isMe: Boolean,
    onOpenUser: (String) -> Unit,
    onToggle: () -> Unit,
    busy: Boolean = false,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onOpenUser(user.id) }
            .padding(horizontal = 16.dp, vertical = 13.dp),
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
            if (user.signature.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = user.signature,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (!isMe) {
            Spacer(Modifier.width(12.dp))
            FollowActionButton(
                following = following,
                enabled = !busy,
                onClick = onToggle,
            )
        }
    }
}

@Composable
private fun FollowActionButton(following: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        Modifier
            .then(
                if (following) {
                    Modifier.neuInset(shape = shape, color = NeuInset, highlight = false)
                } else {
                    Modifier.accentRaised(shape = shape)
                }
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 15.dp, vertical = 6.dp),
    ) {
        Text(
            text = if (following) "已关注" else "关注",
            style = MaterialTheme.typography.labelMedium,
            color = if (following) InkMuted else Color.White,
        )
    }
}
