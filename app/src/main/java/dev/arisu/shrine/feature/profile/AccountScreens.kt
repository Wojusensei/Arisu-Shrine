package dev.arisu.shrine.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.arisu.shrine.core.components.ArisuAvatar
import dev.arisu.shrine.core.components.ArisuConfirmDialog
import dev.arisu.shrine.core.components.ArisuTopBar
import dev.arisu.shrine.core.components.HairlineDivider
import dev.arisu.shrine.core.components.SectionCard
import dev.arisu.shrine.core.components.SectionLabel
import dev.arisu.shrine.core.components.SettingNavRow
import dev.arisu.shrine.core.components.ShrineEmptyState
import dev.arisu.shrine.core.components.ShrineLoadingState
import dev.arisu.shrine.core.components.ShrineNoticeBar
import dev.arisu.shrine.core.components.SoftPill
import dev.arisu.shrine.core.design.NeuBaseHigh
import dev.arisu.shrine.core.design.Vermilion
import dev.arisu.shrine.core.util.TimeFmt
import dev.arisu.shrine.data.VaultAccount
import dev.arisu.shrine.data.model.avatarUrl
import dev.arisu.shrine.data.remote.SessionDto
import dev.arisu.shrine.data.remote.toApiMessage
import dev.arisu.shrine.data.repo.Shrine
import kotlinx.coroutines.launch

@Composable
fun AccountManageScreen(onBack: () -> Unit, onSwitched: () -> Unit, modifier: Modifier = Modifier) {
    val accounts by Shrine.vaultAccounts.collectAsStateWithLifecycle()
    val me by Shrine.me.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var switching by remember { mutableStateOf<Long?>(null) }
    var removing by remember { mutableStateOf<VaultAccount?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    Column(modifier.fillMaxSize()) {
        ArisuTopBar(
            title = "切换账号",
            subtitle = "本机保存的账号可免密快速切换",
            onBack = onBack,
        )

        ShrineNoticeBar(
            text = notice,
            onDismiss = { notice = null },
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 6.dp),
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            SectionLabel("本机账号")
            SectionCard {
                if (accounts.isEmpty()) {
                    Text(
                        text = "还没有保存过账号，登录过的账号会出现在这里",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                    )
                }
                accounts.forEachIndexed { index, account ->
                    val current = me?.id == account.no
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !current && switching == null) {
                                scope.launch {
                                    switching = account.userId
                                    val ok = Shrine.switchAccount(account)
                                    switching = null
                                    if (ok) {

                                        onSwitched()
                                    } else {
                                        notice = "该账号需要重新登录，请用密码登录后再试"
                                    }
                                }
                            }
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ArisuAvatar(
                            seed = account.no.removePrefix("User_").toIntOrNull() ?: 0,
                            name = account.nickname,
                            size = 42.dp,
                            url = account.avatarUrl.ifEmpty { null },
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = account.nickname,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = account.email,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (current) {
                            SoftPill(
                                text = "使用中",
                                color = MaterialTheme.colorScheme.primary,
                                background = NeuBaseHigh,
                            )
                        } else {
                            Text(
                                text = if (switching == account.userId) "切换中…" else "移除",
                                style = MaterialTheme.typography.labelLarge,
                                color = Vermilion,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(enabled = switching == null) { removing = account }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                            )
                        }
                    }
                    if (index != accounts.lastIndex) {
                        HairlineDivider(startIndent = 72.dp)
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            SectionCard {
                SettingNavRow(
                    title = "添加账号",
                    subtitle = "保留当前登录状态，去登录新的账号",
                    icon = Icons.Rounded.PersonAdd,
                    onClick = {
                        scope.launch {
                            if (!Shrine.parkCurrentSession()) {
                                notice = "当前没有可保留的登录状态"
                            }
                        }
                    },
                    showDivider = false,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    removing?.let { target ->
        ArisuConfirmDialog(
            title = "移除这个账号？",
            body = "移除后这台设备不再保存它的登录状态，下次使用需要重新登录。",
            confirmText = "移除",
            onConfirm = {
                Shrine.removeVaultAccount(target)
                removing = null
            },
            onDismiss = { removing = null },
        )
    }
}

@Composable
fun SessionsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var items by remember { mutableStateOf<List<SessionDto>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var kicking by remember { mutableStateOf<SessionDto?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            try {
                items = Shrine.sessions()
                failed = false
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                failed = true
            }
        }
    }
    LaunchedEffect(Unit) { load() }

    Column(modifier.fillMaxSize()) {
        ArisuTopBar(
            title = "登录设备",
            subtitle = "同一账号可以同时在多台设备登录",
            onBack = onBack,
        )

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
            else -> {
                val list = items.orEmpty()
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    itemsIndexed(list, key = { _, session -> session.id }) { index, session ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Devices,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = session.device.ifEmpty { "未知设备" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "最近活跃 " + TimeFmt.epoch(session.lastUsed),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (session.current) {
                                SoftPill(
                                    text = "本机",
                                    color = MaterialTheme.colorScheme.primary,
                                    background = NeuBaseHigh,
                                )
                            } else {
                                Text(
                                    text = "下线",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Vermilion,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { kicking = session }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                )
                            }
                        }
                        if (index != list.lastIndex) {
                            HairlineDivider(startIndent = 54.dp)
                        }
                    }
                }
            }
        }
    }

    kicking?.let { target ->
        ArisuConfirmDialog(
            title = "将该设备下线？",
            body = "下线后，它需要重新登录才能进入您的账号。",
            confirmText = "下线",
            onConfirm = {
                Shrine.kickSession(target.id)
                kicking = null
                items = null
                load()
            },
            onDismiss = { kicking = null },
        )
    }
}
