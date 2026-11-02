package dev.arisu.shrine.feature.profile

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.PersonOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import java.io.File
import androidx.core.content.FileProvider
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import dev.arisu.shrine.core.components.ArisuTextField
import dev.arisu.shrine.core.components.ArisuTopBar
import dev.arisu.shrine.core.components.SectionCard
import dev.arisu.shrine.core.components.SectionLabel
import dev.arisu.shrine.core.components.SettingNavRow
import dev.arisu.shrine.core.components.SettingSwitchRow
import dev.arisu.shrine.core.design.ArisuTheme
import dev.arisu.shrine.core.design.NeuInsetDeep
import dev.arisu.shrine.core.design.NeuLight
import dev.arisu.shrine.core.design.NeuShadow
import dev.arisu.shrine.core.design.ReadingScale
import dev.arisu.shrine.core.design.Vermilion
import dev.arisu.shrine.data.ReadingPrefs
import dev.arisu.shrine.data.ThemePrefs
import dev.arisu.shrine.data.remote.ProfilePatchReq
import dev.arisu.shrine.data.remote.toApiMessage
import dev.arisu.shrine.data.repo.Shrine
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

private val ProfileDisplayItems: List<Pair<String, String>> = listOf(
    "个性签名" to "signature",
    "个人状态" to "status",
    "个人博客网址" to "blog",
    "自定义区域" to "custom_area",
    "总在线小时数" to "online_hours",
    "个人邮箱" to "email",
)

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenSessions: () -> Unit = {},
    onOpenAccountManage: () -> Unit = {},
    onOpenBlocked: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val me by Shrine.me.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val visibility = remember(me) {
        mutableStateMapOf<String, Boolean>().apply {
            ProfileDisplayItems.forEach { (title, key) ->
                put(title, me?.showFields?.get(key) ?: true)
            }
        }
    }

    var showChangePassword by remember { mutableStateOf(false) }
    var showLogout by remember { mutableStateOf(false) }

    var deactivateStage by remember { mutableStateOf(0) }
    var showExport by remember { mutableStateOf(false) }

    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var deactivateInput by remember { mutableStateOf("") }

    var notifyFollowNickname by remember(me) { mutableStateOf(me?.notifyScope?.get("nickname") ?: true) }
    var notifyFollowAvatar by remember(me) { mutableStateOf(me?.notifyScope?.get("follow_avatar") ?: false) }

    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    fun patchShowField(key: String, value: Boolean, revert: () -> Unit) {
        scope.launch {
            try {
                Shrine.updateMe(ProfilePatchReq(showFields = mapOf(key to value)))
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                revert()
                notice = e.toApiMessage()
            }
        }
    }

    fun patchNotifyScope(key: String, value: Boolean, revert: () -> Unit) {
        scope.launch {
            try {
                Shrine.updateMe(ProfilePatchReq(notifyScope = mapOf(key to value)))
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                revert()
                notice = e.toApiMessage()
            }
        }
    }

    Column(modifier.fillMaxSize()) {
        ArisuTopBar(title = "设置", onBack = onBack)

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {

            SectionLabel("外观")
            SectionCard {
                SettingSwitchRow(
                    title = "黑夜模式",
                    subtitle = "开启后界面变为暗色",
                    icon = Icons.Rounded.DarkMode,
                    checked = ArisuTheme.isDark,
                    onCheckedChange = { dark ->

                        ArisuTheme.setNightPref(dark)
                        scope.launch {
                            try {
                                ThemePrefs.setNight(context, dark)
                            } catch (e: Exception) {
                                if (e is kotlinx.coroutines.CancellationException) throw e
                                notice = e.toApiMessage()
                                ArisuTheme.setNightPref(!dark)
                            }
                        }
                    },
                    showDivider = false,
                )
            }

            Spacer(Modifier.height(6.dp))
            SectionLabel("阅读")
            SectionCard {
                ReadingSizeRow()
            }

            Spacer(Modifier.height(6.dp))
            SectionLabel("个人主页展示")
            SectionCard {
                Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 13.dp)) {
                    Text(
                        text = "关闭后，该项不会出现在您的个人主页上",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ProfileDisplayItems.forEachIndexed { index, (title, key) ->
                    SettingSwitchRow(
                        title = title,
                        checked = visibility[title] ?: true,
                        onCheckedChange = { on ->
                            visibility[title] = on
                            patchShowField(key, on) { visibility[title] = !on }
                        },
                        showDivider = index != ProfileDisplayItems.lastIndex,
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            SectionLabel("账号安全")
            SectionCard {
                SettingNavRow(
                    title = "改密码",
                    subtitle = "修改后其他设备需要重新登录",
                    icon = Icons.Rounded.Lock,
                    onClick = { showChangePassword = true },
                )
                SettingNavRow(
                    title = "登录设备",
                    subtitle = "查看与下线正在登录该账号的设备",
                    icon = Icons.Rounded.Devices,
                    onClick = onOpenSessions,
                )
                SettingNavRow(
                    title = "拉黑名单",
                    subtitle = "查看与解除您拉黑的社员",
                    icon = Icons.Rounded.PersonOff,
                    onClick = onOpenBlocked,
                )
                SettingNavRow(
                    title = "切换账号",
                    subtitle = "本机保存的账号可免密快速切换",
                    icon = Icons.Rounded.ManageAccounts,
                    onClick = onOpenAccountManage,
                )
                SettingNavRow(
                    title = "退出登录",
                    icon = Icons.Rounded.Logout,
                    onClick = { showLogout = true },
                )
                SettingNavRow(
                    title = "注销账号",
                    subtitle = "注销后帖子保留，冷静期 24 小时，期间登录即可取消",
                    icon = Icons.Rounded.Block,
                    onClick = { deactivateStage = 1 },
                    showDivider = false,
                    danger = true,
                )
            }

            Spacer(Modifier.height(6.dp))
            SectionLabel("通知范围")
            SectionCard {
                SettingSwitchRow(
                    title = "关注通知（对方修改昵称）",
                    checked = notifyFollowNickname,
                    onCheckedChange = {
                        notifyFollowNickname = it
                        patchNotifyScope("nickname", it) { notifyFollowNickname = !it }
                    },
                )
                SettingSwitchRow(
                    title = "关注通知（对方修改头像）",
                    checked = notifyFollowAvatar,
                    onCheckedChange = {
                        notifyFollowAvatar = it
                        patchNotifyScope("follow_avatar", it) { notifyFollowAvatar = !it }
                    },
                    showDivider = false,
                )
            }

            Spacer(Modifier.height(6.dp))
            SectionLabel("数据")
            SectionCard {
                SettingNavRow(
                    title = "导出我的帖子",
                    subtitle = "生成 Markdown 文件，可分享给其他应用",
                    icon = Icons.Rounded.FileDownload,
                    onClick = { showExport = true },
                    showDivider = false,
                )
            }

            Spacer(Modifier.height(6.dp))
            SectionLabel("关于")
            SectionCard {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 17.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "有栖神社",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = "建造于人间之里外的飘渺存在",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "v" + dev.arisu.shrine.core.util.AppInfo.version(context),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showChangePassword) {
        val mismatch = newPassword.isNotEmpty() && confirmPassword.isNotEmpty() && newPassword != confirmPassword
        AlertDialog(
            onDismissRequest = { showChangePassword = false },
            title = { Text("改密码") },
            text = {
                Column {
                    ArisuTextField(
                        value = oldPassword,
                        onValueChange = { oldPassword = it },
                        placeholder = "旧密码",
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    Spacer(Modifier.height(10.dp))
                    ArisuTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        placeholder = "新密码（至少 8 位）",
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    Spacer(Modifier.height(10.dp))
                    ArisuTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        placeholder = "确认新密码",
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    if (mismatch) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "两次输入的新密码不一致",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = oldPassword.isNotEmpty() && newPassword.length >= 8 && newPassword == confirmPassword && !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            try {
                                Shrine.changePassword(oldPassword, newPassword)
                                showChangePassword = false
                                oldPassword = ""
                                newPassword = ""
                                confirmPassword = ""
                                notice = "密码已修改"
                            } catch (e: Exception) {
                                notice = e.toApiMessage()
                            }
                            busy = false
                        }
                    },
                ) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { showChangePassword = false }) { Text("取消") }
            },
        )
    }

    if (showLogout) {
        AlertDialog(
            onDismissRequest = { showLogout = false },
            title = { Text("退出登录") },
            text = { Text("退出后需要重新登录才能继续发帖与追踪。") },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            try {
                                Shrine.logout()
                            } catch (e: Exception) {
                                notice = e.toApiMessage()
                            }
                            busy = false
                            showLogout = false
                        }
                    }
                ) { Text("退出") }
            },
            dismissButton = {
                TextButton(onClick = { showLogout = false }) { Text("取消") }
            },
        )
    }

    if (deactivateStage == 1) {
        AlertDialog(
            onDismissRequest = { deactivateStage = 0 },
            title = { Text("注销账号") },
            text = { Text("注销后帖子保留，冷静期 24 小时，期间登录即可取消。") },
            confirmButton = {
                TextButton(onClick = { deactivateStage = 2 }) { Text("继续注销") }
            },
            dismissButton = {
                TextButton(onClick = { deactivateStage = 0 }) { Text("取消") }
            },
        )
    }
    if (deactivateStage == 2) {
        AlertDialog(
            onDismissRequest = { deactivateStage = 0 },
            title = { Text("二次确认") },
            text = {
                Column {
                    Text(
                        text = "注销后帖子保留，冷静期 24 小时，期间登录即可取消。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "请输入「注销」二字以确认",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    ArisuTextField(
                        value = deactivateInput,
                        onValueChange = { deactivateInput = it },
                        placeholder = "注销",
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = deactivateInput == "注销" && !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            try {
                                Shrine.deactivate()
                            } catch (e: Exception) {
                                notice = e.toApiMessage()
                            }
                            busy = false
                            deactivateStage = 0
                            deactivateInput = ""
                        }
                    },
                ) { Text("注销账号", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        deactivateStage = 0
                        deactivateInput = ""
                    },
                ) { Text("取消") }
            },
        )
    }

    if (showExport) {
        AlertDialog(
            onDismissRequest = { showExport = false },
            title = { Text("数据导出") },
            text = { Text("将您的全部帖子生成为一个 Markdown 文件。") },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            try {
                                val file = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {

                                    val md = Shrine.exportPostsMarkdown()
                                    val dir = File(context.cacheDir, "export").apply { mkdirs() }
                                    File(dir, "arisu_posts_export.md").apply { writeText(md) }
                                }
                                val uri = FileProvider.getUriForFile(
                                    context, context.packageName + ".fileprovider", file
                                )
                                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/markdown"
                                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(
                                    android.content.Intent.createChooser(intent, "分享帖子导出")
                                )
                                showExport = false
                            } catch (e: Exception) {
                                notice = e.toApiMessage()
                            }
                            busy = false
                        }
                    }
                ) { Text("生成并分享") }
            },
            dismissButton = {
                TextButton(onClick = { showExport = false }) { Text("关闭") }
            },
        )
    }

    notice?.let { msg ->
        AlertDialog(
            onDismissRequest = { notice = null },
            title = { Text("说明") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { notice = null }) { Text("知道了") }
            },
        )
    }
}

@Composable
private fun ReadingSizeRow() {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.FormatSize,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "正文字号",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "仅影响正文阅读，不改变界面字号",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp)
                .padding(bottom = 14.dp),
        ) {
            ReadingSizeSlider()
        }
    }
}

@Composable
private fun ReadingSizeSlider() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var selected by remember {
        mutableIntStateOf(ReadingScale.steps.indexOf(ReadingScale.nearest(ReadingScale.value)))
    }

    var dragValue by remember { mutableFloatStateOf(selected.toFloat()) }
    var dragging by remember { mutableStateOf(false) }

    fun pick(index: Int) {
        val idx = index.coerceIn(0, ReadingScale.steps.lastIndex)
        if (idx == selected) return
        selected = idx
        ReadingScale.apply(ReadingScale.steps[idx])
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun persist() {
        scope.launch { ReadingPrefs.setScale(context, ReadingScale.steps[selected]) }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(68.dp)
            .pointerInput(Unit) {
                fun fractionAt(x: Float): Float {
                    val start = 14.dp.toPx()
                    val end = size.width - 14.dp.toPx()
                    if (end <= start) return 0f
                    return ((x - start) / (end - start) * 3f).coerceIn(0f, 3f)
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    dragging = true
                    dragValue = fractionAt(down.position.x)
                    pick(dragValue.roundToInt())
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        dragValue = fractionAt(change.position.x)
                        pick(dragValue.roundToInt())
                        change.consume()
                    }
                    dragging = false
                    persist()
                }
            },
    ) {
        val inset = 14.dp
        val trackStart = inset
        val trackEnd = maxWidth - inset
        val span = (trackEnd - trackStart).coerceAtLeast(0.dp)
        val thumbFraction by animateFloatAsState(
            targetValue = if (dragging) dragValue else selected.toFloat(),
            animationSpec = if (dragging) snap() else spring(dampingRatio = 0.78f, stiffness = 520f),
            label = "readingThumb",
        )
        val thumbCenter = trackStart + span * (thumbFraction / 3f)

        Box(
            Modifier
                .offset(x = trackStart, y = 20.dp)
                .width(span)
                .height(8.dp)
                .clip(CircleShape)
                .background(NeuInsetDeep)
        )
        Box(
            Modifier
                .offset(x = trackStart, y = 20.dp)
                .width((thumbCenter - trackStart).coerceAtLeast(0.dp))
                .height(8.dp)
                .clip(CircleShape)
                .background(Vermilion.copy(alpha = 0.32f))
        )
        ReadingScale.labels.forEachIndexed { index, _ ->
            val x = trackStart + span * (index / 3f)
            Box(
                Modifier
                    .offset(x = x - 2.dp, y = 22.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(if (index <= selected) Vermilion else NeuShadow.copy(alpha = 0.45f))
            )
        }
        Box(
            Modifier
                .offset(x = thumbCenter - 14.dp, y = 10.dp)
                .size(28.dp)
                .shadow(5.dp, CircleShape, ambientColor = NeuLight, spotColor = NeuShadow)
                .background(Vermilion, CircleShape)
                .border(2.dp, NeuLight, CircleShape)
        )
        Layout(
            content = {
                ReadingScale.labels.forEachIndexed { index, label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (index == selected) Vermilion
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (index == selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = 42.dp),
        ) { measurables, constraints ->
            val width = constraints.maxWidth
            val placeables = measurables.map { it.measure(Constraints()) }
            val height = placeables.maxOfOrNull { it.height } ?: 0
            layout(width, height) {
                val start = 14.dp.roundToPx()
                val end = width - 14.dp.roundToPx()
                placeables.forEachIndexed { index, placeable ->
                    val center = start + ((end - start) * index / 3f).roundToInt()
                    val x = (center - placeable.width / 2).coerceIn(0, (width - placeable.width).coerceAtLeast(0))
                    placeable.place(x, 0)
                }
            }
        }
    }
}
