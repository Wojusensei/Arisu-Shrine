package dev.arisu.shrine.feature.chat

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import dev.arisu.shrine.core.components.ArisuTextField
import dev.arisu.shrine.core.components.ArisuTopBar
import dev.arisu.shrine.core.components.NeuSurface
import dev.arisu.shrine.core.components.SectionLabel
import dev.arisu.shrine.core.components.UnderlineTabs
import dev.arisu.shrine.core.components.YinYangOrb
import dev.arisu.shrine.core.components.accentRaised
import coil3.compose.AsyncImage
import dev.arisu.shrine.core.components.SoftPill
import dev.arisu.shrine.core.components.neuInset
import dev.arisu.shrine.core.components.softCard
import dev.arisu.shrine.core.design.NeuBaseHigh
import dev.arisu.shrine.core.design.NeuInset
import dev.arisu.shrine.data.model.Board
import dev.arisu.shrine.data.remote.toApiMessage
import dev.arisu.shrine.data.repo.Shrine
import java.io.File
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewPostScreen(
    initialBoard: Board,
    onBack: () -> Unit,
    onPublished: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var board by rememberSaveable { mutableStateOf(initialBoard) }

    var title by rememberSaveable { mutableStateOf("") }
    var intro by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var showImageSheet by remember { mutableStateOf(false) }
    var showMention by remember { mutableStateOf(false) }
    var publishing by remember { mutableStateOf(false) }
    var coverUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    var uploadCount by remember { mutableStateOf(0) }
    val uploading = uploadCount > 0
    fun setUploading(on: Boolean) {
        uploadCount = (uploadCount + if (on) 1 else -1).coerceAtLeast(0)
    }

    val canPublish = title.isNotBlank() && body.isNotBlank() && !publishing
    var confirmDiscard by remember { mutableStateOf(false) }
    val hasDraft = title.isNotBlank() || body.isNotBlank()
    androidx.activity.compose.BackHandler(enabled = hasDraft && !publishing) { confirmDiscard = true }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) insertImage(context, scope, uri, onUploading = ::setUploading) { url, err ->
        if (url != null) body = appendSnippet(body, "![图片]($url)") else error = err
    } }

    val coverLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) insertImage(context, scope, uri, onUploading = ::setUploading) { url, err ->
        if (url != null) coverUrl = url else error = err
    } }

    var pendingCameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var pendingCameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        val uri = pendingCameraUri
        val file = pendingCameraPath?.let(::File)
        pendingCameraUri = null
        pendingCameraPath = null
        if (ok && uri != null && file != null) {
            insertImage(context, scope, uri, onUploading = ::setUploading) { url, err ->
                file.delete()
                if (url != null) body = appendSnippet(body, "![图片]($url)") else error = err
            }
        } else {
            file?.delete()
        }
    }

    fun publish() {
        if (publishing) return
        if (title.isBlank() || body.isBlank()) return
        publishing = true
        error = null
        scope.launch {
            try {
                val post = Shrine.createPost(board, title.trim(), body.trim(), intro.trim(), cover = coverUrl ?: "")
                onPublished(post.id)
            } catch (e: Exception) {
                error = e.toApiMessage()
            }
            publishing = false
        }
    }

    if (confirmDiscard) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("放弃撰写？") },
            text = { Text("尚未发布的内容不会被保存。") },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = { confirmDiscard = false; onBack() },
                ) { Text("放弃") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmDiscard = false }) { Text("继续撰写") }
            },
        )
    }

    Column(
        modifier
            .fillMaxSize()
            .background(Color.Transparent)
        .imePadding()
    ) {
        ArisuTopBar(
            title = "撰写帖子",
            subtitle = "当前板块：${board.label}",

            onBack = { if (hasDraft && !publishing) confirmDiscard = true else onBack() },
            actions = {
                if (publishing) {
                    YinYangOrb(size = 16.dp, modifier = Modifier.padding(end = 14.dp))
                } else {
                    PublishButton(enabled = canPublish, onClick = { publish() })
                }
            },
        )

        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 4.dp, bottom = 32.dp),
        ) {
            SectionLabel(text = "标题")
            ArisuTextField(
                value = title,
                onValueChange = { if (it.length <= 60) title = it },
                placeholder = "此处撰写标题",
            )

            Spacer(Modifier.height(18.dp))

            SectionLabel(text = "介绍语", trailing = "选填 · 展示在列表封面")
            ArisuTextField(
                value = intro,
                onValueChange = { if (it.length <= 60) intro = it },
                placeholder = "给帖子配一句简短介绍（最多 60 字）",
            )

            Spacer(Modifier.height(18.dp))

            SectionLabel(text = "封面", trailing = "选填 · 未选时以您的头像为封面")
            CoverPicker(
                coverUrl = coverUrl,
                uploading = uploading,
                onPick = {
                    coverLauncher.launch(androidx.activity.result.PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onRemove = { coverUrl = null },
            )

            Spacer(Modifier.height(18.dp))

            SectionLabel(text = "正文")
            Text(
                text = "正文支持 Markdown，行文简洁即可。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
                modifier = Modifier.padding(start = 3.dp, bottom = 9.dp),
            )

            MarkdownToolbar(
                onInsert = { snippet -> body = appendSnippet(body, snippet) },
                onPickImage = { showImageSheet = true },
            )

            if (uploading) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    YinYangOrb(size = 14.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "正在寄送图片……",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = error.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 3.dp),
                )
            }

            Spacer(Modifier.height(10.dp))

            Box(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                ArisuTextField(
                    value = body,
                    onValueChange = { new ->

                        if (new.length == body.length + 1 && new.endsWith("@")) showMention = true

                        if (new.length <= 32000) body = new
                    },
                    placeholder = "写点什么……\n\n可用 **加粗**、*斜体*、`行内代码`，也可插入列表、引用与图片。",
                    singleLine = false,
                    minLines = 12,
                )
            }

            Spacer(Modifier.height(12.dp))

            SectionLabel(text = "发送到板块", trailing = board.label)
            UnderlineTabs(
                items = Board.entries,
                selected = board,
                onSelect = { board = it },
                label = { it.label },
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = "发布后的帖子将自动获得编号，如 arisu_000001",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 3.dp),
            )
        }

        if (showImageSheet) {
            ModalBottomSheet(onDismissRequest = { showImageSheet = false }) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, bottom = 28.dp)
                ) {
                    Text(
                        text = "插入图片",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "图片先上传到神社，再以 Markdown 插入正文",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))

                    ImageSourceRow(
                        icon = Icons.Rounded.Collections,
                        title = "从相册选择",
                        subtitle = "挑一张本地已保存的图片",
                        onClick = {
                            showImageSheet = false
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                    ImageSourceRow(
                        icon = Icons.Rounded.PhotoCamera,
                        title = "拍照",
                        subtitle = "调用相机现拍一张",
                        onClick = {
                            showImageSheet = false

                            runCatching {
                                File(context.filesDir, "camera").listFiles()?.forEach {
                                    if (System.currentTimeMillis() - it.lastModified() > 24 * 3600_000L) it.delete()
                                }
                            }
                            val (uri, file) = createCameraFile(context)
                            pendingCameraUri = uri
                            pendingCameraPath = file.absolutePath
                            try {
                                cameraLauncher.launch(uri)
                            } catch (_: Exception) {
                                error = "无法打开相机"
                                file.delete()
                            }
                        },
                    )
                }
            }
        }

        if (showMention) {
            MentionPickerSheet(
                visible = true,
                onDismiss = { showMention = false },
                onPick = { u ->
                    body = body.dropLast(1) + "@" + u.id + " "
                    showMention = false
                },
            )
        }
    }
}

@Composable
private fun CoverPicker(
    coverUrl: String?,
    uploading: Boolean,
    onPick: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (coverUrl != null) {
            coil3.compose.AsyncImage(
                model = coverUrl,
                contentDescription = "封面预览",
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier
                    .size(width = 128.dp, height = 80.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
        } else {
            Box(
                Modifier
                    .size(width = 128.dp, height = 80.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .neuInset(shape = RoundedCornerShape(12.dp), color = NeuInset)
                    .clickable { onPick() },
                contentAlignment = Alignment.Center,
            ) {
                if (uploading) {
                    YinYangOrb(size = 18.dp)
                } else {
                    Icon(
                        imageVector = Icons.Rounded.AddPhotoAlternate,
                        contentDescription = "选择封面图",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = if (coverUrl != null) "封面已就绪" else "从相册选择一张封面图",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(5.dp))
            Row {
                SoftPill(
                    text = if (coverUrl != null) "更换封面" else "选择封面图",
                    color = MaterialTheme.colorScheme.primary,
                    background = NeuInset,
                    modifier = Modifier.clickable(enabled = !uploading) { onPick() },
                )
                if (coverUrl != null) {
                    Spacer(Modifier.width(8.dp))
                    SoftPill(
                        text = "移除封面",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        background = NeuInset,
                        modifier = Modifier.clickable { onRemove() },
                    )
                }
            }
        }
    }
}

private fun insertImage(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    uri: Uri,
    onUploading: (Boolean) -> Unit,
    onDone: (String?, String?) -> Unit,
) {
    scope.launch {
        onUploading(true)
        try {

            val url = Shrine.uploadImageFromUri(context, uri)
            onDone(url, null)
        } catch (e: Exception) {
            onDone(null, e.toApiMessage())
        }
        onUploading(false)
    }
}

private fun createCameraFile(context: Context): Pair<Uri, File> {
    val dir = File(context.filesDir, "camera").apply { mkdirs() }
    val file = File(dir, "shot_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file) to file
}

@Composable
private fun PublishButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .then(
                if (enabled) {
                    Modifier.accentRaised(shape = RoundedCornerShape(10.dp))
                } else {
                    Modifier.neuInset(shape = RoundedCornerShape(10.dp), color = NeuInset)
                }
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 15.dp, vertical = 7.dp),
    ) {
        Text(
            text = "发布",
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) Color.White
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
    }
}

private data class MdTool(
    val icon: ImageVector,
    val label: String,

    val snippet: String?,
)

private val MdTools = listOf(
    MdTool(Icons.Rounded.FormatBold, "加粗", "**粗体**"),
    MdTool(Icons.Rounded.FormatItalic, "斜体", "*斜体*"),
    MdTool(dev.arisu.shrine.core.components.ShrineIcons.Title, "标题", "## 小标题"),
    MdTool(Icons.Rounded.FormatListBulleted, "列表", "- 列表项"),
    MdTool(Icons.Rounded.Code, "代码块", "```\n代码\n```"),
    MdTool(Icons.Rounded.Image, "图片", null),
    MdTool(Icons.Rounded.Link, "链接", "[链接文字](https://)"),
)

@Composable
private fun MarkdownToolbar(
    onInsert: (String) -> Unit,
    onPickImage: () -> Unit,
) {

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MdTools.forEach { tool ->
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            Column(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .then(
                        if (pressed) {
                            Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        } else {
                            Modifier
                        }
                    )
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                    ) {
                        val snippet = tool.snippet
                        if (snippet != null) onInsert(snippet) else onPickImage()
                    }
                    .padding(horizontal = 5.dp, vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = tool.icon,
                    contentDescription = tool.label,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = tool.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ImageSourceRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(13.dp))
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
    }
}

private fun appendSnippet(body: String, snippet: String): String =
    if (body.isBlank()) snippet else body.trimEnd('\n') + "\n" + snippet
