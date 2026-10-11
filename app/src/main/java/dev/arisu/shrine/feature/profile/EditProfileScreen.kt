package dev.arisu.shrine.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.arisu.shrine.core.components.ArisuAvatar
import dev.arisu.shrine.core.components.cssPxValue
import dev.arisu.shrine.core.components.ArisuPrimaryButton
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import dev.arisu.shrine.core.design.Vermilion
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.TextButton
import dev.arisu.shrine.core.components.ArisuTextField
import dev.arisu.shrine.core.components.ArisuTopBar
import dev.arisu.shrine.core.components.FieldRow
import dev.arisu.shrine.core.components.HairlineDivider
import dev.arisu.shrine.core.components.NeuSurface
import dev.arisu.shrine.core.components.PillTabs
import dev.arisu.shrine.core.components.SectionCard
import dev.arisu.shrine.core.components.SectionLabel
import dev.arisu.shrine.core.components.SettingSwitchRow
import dev.arisu.shrine.core.components.neuInset
import dev.arisu.shrine.core.design.NeuBase
import dev.arisu.shrine.core.design.NeuBaseHigh
import dev.arisu.shrine.core.design.NeuInset
import dev.arisu.shrine.core.markdown.MarkdownText
import dev.arisu.shrine.data.remote.ProfilePatchReq
import dev.arisu.shrine.data.remote.toApiMessage
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import dev.arisu.shrine.data.repo.Shrine
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

private const val TARGET_FIXED = "fixed"
private const val TARGET_ACTIVE = "active"

private const val TAB_EDIT = "编辑"
private const val TAB_PREVIEW = "预览"

private val StatusEmojis = listOf(
    "🎉", "⚙️", "🔧", "⛩️", "💻", "📚", "☕", "🌸",
    "😄", "😊", "😴", "🤔", "😎", "🥺", "🚀", "🌙",
)

@Composable
fun EditProfileScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val me by Shrine.me.collectAsState()
    val meState = me ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var nickname by rememberSaveable { mutableStateOf(meState.nickname) }
    var signature by rememberSaveable { mutableStateOf(meState.signature) }
    var statusEmoji by rememberSaveable { mutableStateOf(meState.statusEmoji.orEmpty()) }
    var statusText by rememberSaveable { mutableStateOf(meState.statusText.orEmpty()) }
    var blogUrl by rememberSaveable { mutableStateOf(meState.blogUrl) }
    var customMarkdown by rememberSaveable { mutableStateOf(meState.customMarkdown) }
    var customCss by rememberSaveable { mutableStateOf(meState.customCss) }

    var customTab by rememberSaveable { mutableStateOf(TAB_EDIT) }
    var pickerTarget by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    var avatarBusy by remember { mutableStateOf(false) }

    var email by rememberSaveable { mutableStateOf(meState.email) }

    fun uploadAvatar(uri: Uri, target: String) {
        if (avatarBusy) return
        avatarBusy = true
        scope.launch {
            try {
                val url = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val dir = File(context.cacheDir, "upload").apply { mkdirs() }
                    val file = File(dir, "avatar_${System.currentTimeMillis()}.jpg")
                    val picked = File(dir, "pick_${System.currentTimeMillis()}.bin")
                    try {

                        val input = try {
                            context.contentResolver.openInputStream(uri)
                        } catch (e: Exception) {
                            throw IllegalStateException("系统拒绝了图片读取，请回相册重新选一次", e)
                        } ?: throw IllegalStateException("无法打开所选图片")
                        input.use { stream ->
                            picked.outputStream().use { output -> stream.copyTo(output) }
                        }

                        val bounds = android.graphics.BitmapFactory.Options().apply {
                            inJustDecodeBounds = true
                        }
                        android.graphics.BitmapFactory.decodeFile(picked.absolutePath, bounds)
                        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {

                            throw IllegalStateException("识别不出这张图片的格式，请换一张试试")
                        }
                        val opts = android.graphics.BitmapFactory.Options().apply {
                            inSampleSize = generateImageSample(bounds.outWidth, bounds.outHeight, 1024)
                        }
                        val src = android.graphics.BitmapFactory.decodeFile(picked.absolutePath, opts)
                            ?: android.graphics.BitmapFactory.decodeFile(picked.absolutePath)
                            ?: throw IllegalStateException("识别不出这张图片的格式，请换一张试试")
                        val longest = maxOf(src.width, src.height)
                        val scale = if (longest > 1024) 1024f / longest else 1f
                        val scaled = android.graphics.Bitmap.createScaledBitmap(
                            src, (src.width * scale).toInt().coerceAtLeast(1),
                            (src.height * scale).toInt().coerceAtLeast(1), true,
                        )
                        file.outputStream().use { out ->
                            scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
                        }
                        src.recycle()
                        scaled.recycle()
                        try {
                            Shrine.uploadImage(file)
                        } finally {
                            file.delete()
                        }
                    } finally {
                        picked.delete()
                    }
                }
                if (target == TARGET_FIXED) {
                    Shrine.updateMe(ProfilePatchReq(fixedAvatarUrl = url))
                } else {
                    Shrine.updateMe(ProfilePatchReq(activeAvatarUrl = url))
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                error = e.toApiMessage()
            }
            avatarBusy = false
        }
    }

    var pendingAvatarTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var rebinding by remember { mutableStateOf(false) }
    var rebindEmail by remember { mutableStateOf("") }
    var rebindCode by remember { mutableStateOf("") }
    var rebindCodeSent by remember { mutableStateOf<String?>(null) }
    var rebindCooldown by remember { mutableStateOf(0) }
    LaunchedEffect(rebindCooldown) {
        if (rebindCooldown > 0) {
            delay(1000L)
            rebindCooldown -= 1
        }
    }
    var rebindBusy by remember { mutableStateOf(false) }
    var rebindError by remember { mutableStateOf<String?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val target = pendingAvatarTarget
        pendingAvatarTarget = null
        if (uri != null && target != null) uploadAvatar(uri, target)
    }

    var pendingCameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val target = pendingAvatarTarget
        val file = pendingCameraPath?.let(::File)
        pendingAvatarTarget = null
        pendingCameraPath = null
        if (ok && target != null && file != null) {
            scope.launch {
                try {
                    val url = Shrine.uploadImage(file)
                    if (target == TARGET_FIXED) {
                        Shrine.updateMe(ProfilePatchReq(fixedAvatarUrl = url))
                    } else {
                        Shrine.updateMe(ProfilePatchReq(activeAvatarUrl = url))
                    }
                } catch (e: Exception) {
                    error = e.toApiMessage()
                } finally {

                    file.delete()
                }
            }
        } else {
            file?.delete()
        }
    }

    fun launchCamera(target: String) {

        runCatching {
            File(context.filesDir, "camera").listFiles()?.forEach {
                if (System.currentTimeMillis() - it.lastModified() > 24 * 3600_000L) it.delete()
            }
        }
        val dir = File(context.filesDir, "camera").apply { mkdirs() }
        val file = File(dir, "shot_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        pendingCameraPath = file.absolutePath
        pendingAvatarTarget = target
        cameraLauncher.launch(uri)
    }

    Column(modifier.fillMaxSize().imePadding()) {
        ArisuTopBar(title = "编辑资料", onBack = onBack)

        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {

                SectionLabel("头像")
                SectionCard {
                    AvatarNavRow(
                        seed = meState.fixedAvatarSeed,
                        name = meState.nickname,
                        url = meState.fixedAvatarUrl,
                        title = "固定头像",
                        subtitle = "点击更换固定头像",
                        onClick = { pickerTarget = TARGET_FIXED },
                    )
                    HairlineDivider(startIndent = 88.dp)
                    AvatarNavRow(
                        seed = meState.fixedAvatarSeed,
                        name = meState.nickname,
                        url = meState.activeAvatarUrl,
                        title = "活跃头像",
                        subtitle = "点击更换活跃头像",
                        onClick = { pickerTarget = TARGET_ACTIVE },
                    )
                    HairlineDivider(startIndent = 18.dp)
                    SettingSwitchRow(
                        title = "启用活跃头像",
                        checked = meState.activeAvatarEnabled,
                        onCheckedChange = { on ->
                            scope.launch {
                                try {
                                    Shrine.updateMe(ProfilePatchReq(activeAvatarEnabled = on))
                                } catch (e: Exception) {
                                    error = e.toApiMessage()
                                }
                            }
                        },
                        showDivider = false,
                    )
                    if (!meState.activeAvatarEnabled) {
                        Text(
                            text = "关闭后所有地方将显示固定头像",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 14.dp),
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))
                SectionLabel("基本资料")
                SectionCard {
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                        FieldRow(
                            label = "昵称",
                            value = nickname,
                            onValueChange = { if (it.length <= 20) nickname = it },
                            placeholder = "给自己起个名字",
                        )
                        CounteredField(
                            label = "个性签名",
                            value = signature,
                            onValueChange = { signature = if (it.length <= 50) it else it.take(50) },
                            counter = "${signature.length}/50",
                            placeholder = "一句话介绍您自己",
                            singleLine = false,
                            minLines = 3,
                            maxLines = 4,
                        )

                        Text(
                            text = "个人状态",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 3.dp, top = 12.dp),
                        )
                        Spacer(Modifier.height(9.dp))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            StatusEmojis.forEach { emoji ->
                                EmojiChip(
                                    emoji = emoji,
                                    selected = statusEmoji == emoji,
                                    onClick = { statusEmoji = if (statusEmoji == emoji) "" else emoji },
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        CounteredField(
                            label = "状态文字",
                            value = statusText,
                            onValueChange = { if (it.length <= 10) statusText = it },
                            counter = "${statusText.length}/10",
                            placeholder = "社员随性填写即可",
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))
                SectionLabel("联系方式")
                SectionCard {
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                FieldRow(
                                    label = "个人邮箱",
                                    value = email,
                                    onValueChange = { },
                                    placeholder = "注册邮箱即账号，支持换绑",
                                )
                            }
                            Text(
                                text = "换绑",
                                style = MaterialTheme.typography.labelLarge,
                                color = Vermilion,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { rebinding = true }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                        FieldRow(
                            label = "个人博客 URL",
                            value = blogUrl,

                            onValueChange = { if (it.length <= 300) blogUrl = it },
                            placeholder = "https://",
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))
                SectionLabel("自定义区域")
                SectionCard {
                    PillTabs(
                        items = listOf(TAB_EDIT, TAB_PREVIEW),
                        selected = customTab,
                        onSelect = { customTab = it },
                        label = { it },
                        scrollable = false,
                    )
                }

                Spacer(Modifier.height(8.dp))

                if (customTab == TAB_EDIT) {
                    SectionCard {
                        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                            Text(
                                text = "Markdown",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(start = 3.dp, bottom = 8.dp),
                            )
                            ArisuTextField(
                                value = customMarkdown,

                                onValueChange = { if (it.length <= 20000) customMarkdown = it },
                                placeholder = "支持标题、粗体、列表、代码块、引用、表格……",
                                singleLine = false,
                                minLines = 5,
                                maxLines = 12,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    SectionCard {
                        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                            Text(
                                text = "安全 CSS",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(start = 3.dp, bottom = 8.dp),
                            )
                            ArisuTextField(
                                value = customCss,

                                onValueChange = { if (it.length <= 20000) customCss = it },
                                placeholder = ".arisu-card { border-radius: 16px; background-color: #FDF9F6; }",
                                singleLine = false,
                                minLines = 3,
                                maxLines = 8,
                                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            )
                        }
                    }
                } else {
                    CustomPreview(markdown = customMarkdown, css = customCss)
                    Spacer(Modifier.height(9.dp))
                    Text(
                        text = "仅支持 border-radius / background-color / color / font-size 四个安全属性",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }

                Spacer(Modifier.height(96.dp))
            }

            if (error != null) {
                Text(
                    text = error.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = dev.arisu.shrine.core.design.Vermilion,
                    modifier = Modifier.padding(start = 20.dp, bottom = 6.dp),
                )
            }

            NeuSurface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, bottom = 12.dp),
                shape = RoundedCornerShape(20.dp),
                depth = 6.dp,
                blur = 12.dp,
                color = NeuBaseHigh,
            ) {
                ArisuPrimaryButton(
                    text = if (saving) "保存中…" else "保存",
                    modifier = Modifier.padding(6.dp),

                    enabled = !saving && nickname.isNotBlank(),
                    onClick = {
                        saving = true
                        error = null
                        scope.launch {
                            try {
                                Shrine.updateMe(
                                    ProfilePatchReq(
                                        nickname = nickname.trim(),
                                        signature = signature,
                                        statusEmoji = statusEmoji,
                                        statusText = statusText,
                                        blogUrl = blogUrl.trim(),
                                        customMarkdown = customMarkdown,
                                        customCss = customCss,
                                    )
                                )
                                onBack()
                            } catch (e: Exception) {
                                error = e.toApiMessage()
                            }
                            saving = false
                        }
                    },
                )
            }
        }
    }

    if (rebinding) {
        AlertDialog(
            onDismissRequest = { rebinding = false },
            title = { Text("换绑邮箱") },
            text = {
                Column {
                    Text(
                        text = "验证码将寄到新邮箱；换绑后请用新邮箱登录。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    ArisuTextField(
                        value = rebindEmail,
                        onValueChange = { rebindEmail = it },
                        placeholder = "outlook / qq / gmail / 163 等任意邮箱",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ArisuTextField(
                            value = rebindCode,
                            onValueChange = { rebindCode = it.take(6) },
                            placeholder = "6 位数字",
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = when {
                                rebindCooldown > 0 -> "重发（${rebindCooldown}s）"
                                rebindCodeSent != null -> "重新发送"
                                else -> "发送验证码"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (rebindCooldown > 0) MaterialTheme.colorScheme.onSurfaceVariant else Vermilion,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = rebindCooldown <= 0 && !rebindBusy) {
                                    if (!isEmailLocal(rebindEmail)) {
                                        rebindError = "邮箱格式不正确"
                                        return@clickable
                                    }
                                    rebindError = null
                                    rebindBusy = true
                                    scope.launch {
                                        try {
                                            Shrine.requestEmailChange(rebindEmail.trim())
                                            rebindCodeSent = "sent"
                                            rebindCooldown = 60
                                        } catch (e: Exception) {
                                            if (e is kotlinx.coroutines.CancellationException) throw e
                                            rebindError = e.toApiMessage()
                                        }
                                        rebindBusy = false
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    if (rebindCodeSent != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "验证码已寄到新邮箱（开发模式见服务器日志）",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    rebindError?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = rebindEmail.isNotBlank() && rebindCode.isNotBlank() && !rebindBusy,
                    onClick = {
                        if (!isEmailLocal(rebindEmail)) {
                            rebindError = "邮箱格式不正确"
                            return@TextButton
                        }
                        rebindError = null
                        rebindBusy = true
                        scope.launch {
                            try {
                                Shrine.changeEmail(rebindEmail.trim(), rebindCode.trim())
                                email = Shrine.me.value?.email ?: rebindEmail
                                rebinding = false
                                error = null
                            } catch (e: Exception) {
                                if (e is kotlinx.coroutines.CancellationException) throw e
                                rebindError = e.toApiMessage()
                            }
                            rebindBusy = false
                        }
                    },
                ) { Text("确认换绑") }
            },
            dismissButton = {
                TextButton(onClick = { rebinding = false }) { Text("取消") }
            },
        )
    }

    if (pickerTarget != null) {
        AvatarSourceSheet(
            onDismiss = {
                pendingAvatarTarget = null
                pickerTarget = null
            },
            onPick = { source ->
                val target = pickerTarget
                pickerTarget = null
                if (target != null) {
                    if (source == "album") {
                        pendingAvatarTarget = target
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    } else {
                        launchCamera(target)
                    }
                }
            },
        )
    }
}

@Composable
private fun AvatarNavRow(
    seed: Int,
    name: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    url: String? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArisuAvatar(seed = seed, name = name, size = 56.dp, url = url)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AvatarSourceSheet(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = "更换头像",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 6.dp),
        )
        SheetOption(icon = Icons.Rounded.Collections, text = "从相册选择") { onPick("album") }
        SheetOption(icon = Icons.Rounded.PhotoCamera, text = "拍照") { onPick("camera") }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun SheetOption(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(21.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun CounteredField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    counter: String,
    placeholder: String = "",
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 6,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 3.dp, bottom = 8.dp),
        )
        ArisuTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
        )
        Text(
            text = counter,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 4.dp, top = 5.dp),
        )
    }
}

@Composable
private fun EmojiChip(emoji: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .then(
                if (selected) {
                    Modifier
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.75f), CircleShape)
                } else {
                    Modifier.neuInset(shape = CircleShape, color = NeuInset, highlight = false)
                }
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = emoji, fontSize = 20.sp)
    }
}

private fun utf8Length(s: String) = s.toByteArray(Charsets.UTF_8).size

private fun truncateUtf8(s: String, maxBytes: Int): String {
    if (utf8Length(s) <= maxBytes) return s
    val sb = StringBuilder()
    var used = 0
    for (ch in s) {
        val len = utf8Length(ch.toString())
        if (used + len > maxBytes) break
        sb.append(ch)
        used += len
    }
    return sb.toString()
}

private data class SafeCss(
    val borderRadius: Dp? = null,
    val background: Color? = null,
    val color: Color? = null,
    val fontSize: TextUnit? = null,
)

private fun parseSafeCss(css: String): SafeCss {
    var radius: Dp? = null
    var background: Color? = null
    var color: Color? = null
    var fontSize: TextUnit? = null

    css.split(';', '\n', '{', '}').forEach { declaration ->
        val colon = declaration.indexOf(':')
        if (colon <= 0) return@forEach
        val key = declaration.substring(0, colon).trim().lowercase()
        val value = declaration.substring(colon + 1).trim()
        when (key) {
            "border-radius" -> cssPxValue(value)?.let { radius = it.dp }
            "background", "background-color" -> hexColor(value)?.let { background = it }
            "color" -> hexColor(value)?.let { color = it }
            "font-size" -> cssPxValue(value)?.let { fontSize = it.sp }
        }
    }
    return SafeCss(borderRadius = radius, background = background, color = color, fontSize = fontSize)
}

private fun hexColor(raw: String): Color? {
    val hex = raw.trim().removePrefix("#")
    if (hex.length != 6) return null
    val rgb = hex.toLongOrNull(16) ?: return null
    return Color(0xFF000000L or rgb)
}

@Composable
private fun CustomPreview(markdown: String, css: String) {
    val safe = remember(css) { parseSafeCss(css) }
    val shape = RoundedCornerShape(safe.borderRadius ?: 18.dp)

    NeuSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        depth = 5.dp,
        blur = 10.dp,
        color = safe.background ?: NeuBase,
    ) {
        Box(Modifier.padding(16.dp)) {
            MarkdownText(
                markdown = markdown.ifBlank { "这一页还空着，写点什么吧。" },
                color = safe.color ?: MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = safe.fontSize ?: 14.sp),

                naturalImages = true,
            )
        }
    }
}

private fun generateImageSample(width: Int, height: Int, target: Int): Int {
    var sample = 1
    var longest = maxOf(width, height)
    while (longest / 2 >= target) {
        sample *= 2
        longest /= 2
    }
    return sample
}

private val RebindEmailRegex = Regex("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$")
private fun isEmailLocal(s: String) = RebindEmailRegex.matches(s.trim())
