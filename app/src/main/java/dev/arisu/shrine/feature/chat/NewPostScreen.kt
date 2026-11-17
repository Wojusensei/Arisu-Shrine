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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import dev.arisu.shrine.core.components.ArisuTextField
import dev.arisu.shrine.core.components.ArisuTopBar
import dev.arisu.shrine.core.components.EmojiSheet
import dev.arisu.shrine.core.components.SectionLabel
import dev.arisu.shrine.core.components.SoftPill
import dev.arisu.shrine.core.components.UnderlineTabs
import dev.arisu.shrine.core.components.YinYangOrb
import dev.arisu.shrine.core.components.accentRaised
import dev.arisu.shrine.core.components.neuInset
import dev.arisu.shrine.core.design.NeuInset
import dev.arisu.shrine.core.markdown.MARKDOWN_STICKER_ALT
import dev.arisu.shrine.core.markdown.MdAction
import dev.arisu.shrine.core.markdown.MdMark
import dev.arisu.shrine.core.markdown.applyMarkdownAction
import dev.arisu.shrine.core.markdown.insertMark
import dev.arisu.shrine.core.markdown.replaceRangeWith
import dev.arisu.shrine.core.markdown.replaceSpan
import dev.arisu.shrine.core.markdown.withText
import dev.arisu.shrine.core.media.downloadImageToFile
import dev.arisu.shrine.core.media.stagePickedImage
import dev.arisu.shrine.data.local.Draft
import dev.arisu.shrine.data.local.DraftStore
import dev.arisu.shrine.data.model.Board
import dev.arisu.shrine.data.model.Post
import dev.arisu.shrine.data.remote.toApiMessage
import dev.arisu.shrine.data.repo.Shrine
import dev.arisu.shrine.feature.editor.COVER_ASPECT
import dev.arisu.shrine.feature.editor.ImageEditorOverlay
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 一次最多挑几张：与 QQ 同一量级，再多正文里的图就淹了 */
private const val MAX_PICK = 9

/** 上传途中进程被杀会留下没有暂存件的占位符：恢复草稿与发布前都清掉 */
private val StaleUploadMark = Regex("""\n?!\[图片]\(上传中-\d+\)\n?""")

internal fun stripStaleUploads(text: String): String =
    text.replace(StaleUploadMark, "")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()

/**
 * 撰写帖子（整页）。[editPost] 非空即为改帖：预填原稿、标题栏与按钮改成「编辑 / 保存」，
 * 提交走 PATCH，改完由 [onPublished] 把帖子编号交回调用方（页面本身不负责导航）。
 *
 * 标题栏：返回 + 「撰写帖子」+ 右侧小号朱色「发布」
 * 板块：  正殿 / 幻想乡闻 / /dev.，默认选中进入时的板块
 * 正文：  稿纸式输入卡 + Markdown 工具栏（加粗 / 斜体 / 标题 / 列表 / 代码块 / 图片 / 链接）
 * 配图：  可多选，先落暂存件再逐张上传（每张一格圆环进度），上传前可裁剪/旋转
 * 封面：  选图先取景再上传；改帖时旧封面也会取回本地，同样能重新取景
 * 发布：  标题与正文都非空、且没有图还在路上，才可点
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewPostScreen(
    initialBoard: Board,
    onBack: () -> Unit,
    onPublished: (String) -> Unit,
    modifier: Modifier = Modifier,
    editPost: Post? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 选封面图会短暂离开本页，回来不重置已选板块
    var board by rememberSaveable(editPost) { mutableStateOf(editPost?.board ?: initialBoard) }
    // 表单草稿驻留：跳相册选图、进程回收都不丢稿（改帖时以原稿起手）
    var title by rememberSaveable(editPost) { mutableStateOf(editPost?.title.orEmpty()) }
    var intro by rememberSaveable(editPost) { mutableStateOf(editPost?.intro.orEmpty()) }
    // 正文带光标选区：Markdown 工具条要插到光标处，还得能把选中内容包进记号里
    var body by rememberSaveable(editPost, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(editPost?.body.orEmpty()))
    }
    var showImageSheet by remember { mutableStateOf(false) }
    var showStickers by remember { mutableStateOf(false) }
    var showMention by remember { mutableStateOf(false) }
    var publishing by remember { mutableStateOf(false) }
    var coverUrl by rememberSaveable(editPost) { mutableStateOf(editPost?.coverUrl?.takeIf { it.isNotBlank() }) }
    // 新帖草稿从本地恢复一次；改帖不存草稿，避免和线上原文打架
    var draftLoaded by rememberSaveable(editPost) { mutableStateOf(editPost != null) }
    // 检测到的草稿先不落进表单：由用户决定继续写还是从头开始
    var draftPrompt by remember { mutableStateOf<Draft?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val bodyField = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    /** 认下这份草稿：表单回填，随后自动保存接着跑 */
    fun keepDraft(saved: Draft) {
        title = saved.title
        intro = saved.intro
        // 上传没走完就退出/被杀的那几张，暂存件已经没了；占位符不能留在稿子里
        val restored = stripStaleUploads(saved.body)
        body = TextFieldValue(restored, TextRange(restored.length))
        coverUrl = saved.coverUrl
        Board.entries.firstOrNull { it.name == saved.board }?.let { board = it }
        draftLoaded = true
        draftPrompt = null
    }

    /** 重新开始：本地那份删掉，空表单之后若落笔会重新存一份 */
    fun dropDraft() {
        Shrine.me.value?.id?.let { DraftStore.deleteAsync(it, DraftStore.POST, "new") }
        draftLoaded = true
        draftPrompt = null
    }

    // 首帧后找上次没发出去的稿；当前表单已经有字就不覆盖
    LaunchedEffect(Unit) {
        if (editPost != null || draftLoaded) return@LaunchedEffect
        val uid = Shrine.me.value?.id
        if (uid != null && title.isBlank() && intro.isBlank() && body.text.isBlank() && coverUrl == null) {
            val saved = DraftStore.get(uid, DraftStore.POST, "new")
            // 读盘这几毫秒里用户可能已经落笔：那就别再用旧稿盖掉
            if (saved != null && title.isBlank() && intro.isBlank() && body.text.isBlank() && coverUrl == null) {
                // 选之前不放行自动保存，免得空表单先把本地那份删掉
                draftPrompt = saved
                return@LaunchedEffect
            }
        }
        draftLoaded = true
    }

    // 输入停顿后落盘；清空即删掉本地那份
    LaunchedEffect(title, intro, body.text, board, coverUrl, draftLoaded) {
        if (editPost != null || !draftLoaded) return@LaunchedEffect
        val uid = Shrine.me.value?.id ?: return@LaunchedEffect
        if (title.isBlank() && intro.isBlank() && body.text.isBlank() && coverUrl == null) {
            DraftStore.deleteAsync(uid, DraftStore.POST, "new")
            return@LaunchedEffect
        }
        delay(600)
        DraftStore.saveAsync(
            Draft(
                userId = uid,
                kind = DraftStore.POST,
                targetId = "new",
                title = title,
                intro = intro,
                body = body.text,
                board = board.name,
                coverUrl = coverUrl,
            ),
        )
    }

    // 待发配图：本地暂存件 + 每张自己的上传进度/地址/失败原因。
    // 进程回收后网格会空（暂存件在 cacheDir 里），但正文里的 Markdown 已落稿，不会丢图
    val staged = remember { mutableStateListOf<StagedImage>() }
    var coverFile by remember { mutableStateOf<File?>(null) }
    var coverProgress by remember { mutableStateOf(-1f) }
    // 改帖时旧封面是站上的一张图，取回本地取景的空档界面得有忙态
    var coverFetching by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<EditTarget?>(null) }
    val uploading = staged.any { it.url == null && it.error == null } || coverProgress in 0f..1f

    val canPublish = title.isNotBlank() && body.text.isNotBlank() && !publishing && !uploading
    var confirmDiscard by remember { mutableStateOf(false) }
    // 改帖按改动算：一字未动就直接退，不必再问一次；新帖按有没有落笔算
    // （封面也算稿：只选了封面就返回，此前一声不响地丢掉）
    val hasDraft = if (editPost != null) {
        title != editPost.title || intro != editPost.intro || body.text != editPost.body ||
            board != editPost.board || (coverUrl ?: "") != (editPost.coverUrl ?: "") ||
            staged.isNotEmpty()
    } else {
        title.isNotBlank() || body.text.isNotBlank() || staged.isNotEmpty() ||
            coverUrl != null || coverFile != null
    }
    androidx.activity.compose.BackHandler(enabled = hasDraft && !publishing) { confirmDiscard = true }

    /** 把某段 Markdown 从正文里撤掉（连同插入时补的换行），不留空行 */
    fun stripMark(md: String) {
        // 先按 span 撤掉让光标跟着缩，再收拾多余空行
        val next = body.replaceSpan(md, "")
        val cleaned = next.text.replace(Regex("\\n{3,}"), "\n\n").trimEnd()
        body = next.withText(cleaned)
    }

    /** 上传一张暂存件；完成后把正文里的占位符换成真地址（编辑过的图换地址，不挪位置） */
    fun upload(item: StagedImage) {
        scope.launch {
            item.error = null
            item.progress = 0f
            try {
                val url = Shrine.uploadImage(item.file) { item.progress = it }
                if (item.removed) {
                    item.url = url
                    return@launch
                }
                val snippet = "![图片]($url)"
                val old = item.markdown
                // 正常情况下正文里还留着选图时放下的占位符，原位换成真地址；
                // 占位符被手动删掉/改掉时退化为「插到当前光标处」。
                // 替换必须带着光标走：真地址比占位符长，按旧偏移截断会把光标留在
                // 刚换上的 Markdown 中间，接着打字/插下一张就把这张拆坏。
                body = if (old != null && body.text.contains(old)) {
                    body.replaceSpan(old, snippet)
                } else {
                    insertMark(body, MdMark("$snippet\n", block = true))
                }
                item.markdown = snippet
                item.url = url
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // 占位符不能留在正文里：失败就撤掉，网格里那张仍可点重试
                item.markdown?.let { stripMark(it) }
                item.markdown = null
                item.error = e.toApiMessage()
            }
        }
    }

    /** 选图 → 问答缓存件（顺带压缩/转正）→ 挂上网格 → 立刻上传 */
    fun stage(uri: Uri) {
        scope.launch {
            val item = try {
                val file = withContext(Dispatchers.IO) {
                    stagePickedImage(context, uri, File(context.cacheDir, "upload"))
                }
                StagedImage(id = System.nanoTime(), file = file).also { staged += it }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                error = e.toApiMessage()
                return@launch
            }
            // 先在光标处放占位符，上传完成原位换成真地址：
            // 上传期间继续打字，图也不会被挤到句子中间或帖子末尾
            val placeholder = "![图片](上传中-${item.id})"
            item.markdown = placeholder
            body = insertMark(body, MdMark("$placeholder\n", block = true))
            upload(item)
        }
    }

    fun removeStaged(item: StagedImage) {
        item.removed = true
        item.markdown?.let { stripMark(it) }
        item.file.delete()
        staged -= item
    }

    // 相册多选（Photo Picker，无需存储权限）：一次可以挑一组
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_PICK)
    ) { uris -> uris.forEach { stage(it) } }

    fun uploadCover(file: File) {
        scope.launch {
            val previous = coverUrl
            coverProgress = 0f
            try {
                coverUrl = Shrine.uploadImage(file) { coverProgress = it }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // 传失败退回上一张：改帖时旧封面得留着，一次网络抖动不该把封面抹掉
                coverUrl = previous
                error = e.toApiMessage()
            }
            coverProgress = -1f
        }
    }

    fun stageCover(uri: Uri) {
        scope.launch {
            val file = try {
                withContext(Dispatchers.IO) {
                    stagePickedImage(context, uri, File(context.cacheDir, "upload"))
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                coverProgress = -1f
                error = e.toApiMessage()
                return@launch
            }
            // 先取景再上传：封面在列表条与详情横幅里都会被裁成固定比例，
            // 让上传的人自己框显示范围，而不是被系统从中间截一刀。
            // 取景期间不动旧封面也不亮上传态——返回即作废（见编辑页 onCancel）
            editing = EditTarget.Cover(file)
        }
    }

    // 封面图：与正文配图同一套暂存/上传通道；不选则展示时回落作者头像
    val coverLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) stageCover(uri) }

    /**
     * 封面重新取景。
     *
     * 本地已有取景过的稿就用它；改帖的旧封面只有站上的地址，先取回本地——
     * 取景器只认本地文件，否则「调整封面位置」在改帖时永远是灰的。
     */
    fun refineCover() {
        coverFile?.let { editing = EditTarget.Cover(it); return }
        val remote = coverUrl
        if (remote.isNullOrBlank() || coverFetching) return
        coverFetching = true
        scope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    downloadImageToFile(remote, File(context.cacheDir, "upload"))
                }
                editing = EditTarget.Cover(file)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                error = e.toApiMessage()
            }
            coverFetching = false
        }
    }

    // 拍照：先造一个临时文件 Uri（FileProvider），拍完回调；
    // Uri 与文件路径都驻留进 saved state——拍摄期间进程被回收，照片才不会无声丢掉；
    // 原图留在 filesDir/camera，上传的是拷贝件，成败都要删原图（照编辑资料页同一套约定）
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
            stage(uri)
        }
        file?.delete()
    }

    fun publish() {
        if (publishing) return
        // 上传中占位符不会流进正文：进程被回收时暂存件没了、占位符还留在正文里，发布前统一清掉
        val finalBody = stripStaleUploads(body.text)
        if (title.isBlank() || finalBody.isBlank()) return
        publishing = true
        error = null
        scope.launch {
            try {
                val post = if (editPost != null) {
                    Shrine.updatePost(
                        editPost.id, title.trim(), intro.trim(), finalBody, board,
                        cover = coverUrl ?: "",
                    )
                } else {
                    Shrine.createPost(board, title.trim(), finalBody, intro.trim(), cover = coverUrl ?: "")
                }
                // 暂存件是本次草稿的中间产物，交出去就没用了
                staged.forEach { it.file.delete() }
                staged.clear()
                coverFile?.delete()
                coverFile = null
                if (editPost == null) {
                    Shrine.me.value?.id?.let { DraftStore.deleteAsync(it, DraftStore.POST, "new") }
                }
                onPublished(post.id)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                error = e.toApiMessage()
            }
            publishing = false
        }
    }

    // 编辑配图：全屏覆盖在撰写页上，确定后换掉暂存件并重传
    val editingTarget = editing
    if (editingTarget != null) {
        ImageEditorOverlay(
            sourcePath = editingTarget.path,
            // 返回 = 放弃这次选的图：暂存件删掉、上传态收回，旧封面原样留着；
            // 只有点「确定」才代表认下取景结果并上传
            onCancel = {
                if (editingTarget is EditTarget.Cover) {
                    editingTarget.file.delete()
                    coverProgress = -1f
                }
                editing = null
            },
            lockedAspect = if (editingTarget is EditTarget.Cover) COVER_ASPECT else null,
            onConfirm = { path ->
                editing = null
                val edited = File(path)
                when (editingTarget) {
                    is EditTarget.Cover -> {
                        // 旧封面（本地暂存那份）与新取景无关，现在才换掉
                        editingTarget.file.delete()
                        coverFile?.takeIf { it.absolutePath != edited.absolutePath }?.delete()
                        coverFile = edited
                        // 旧地址先留着：上传成功后由 uploadCover 换上，传失败还退回它
                        uploadCover(edited)
                    }

                    is EditTarget.Staged -> {
                        val item = editingTarget.item
                        item.file.delete()
                        item.file = edited
                        item.url = null
                        // markdown 要留着：重传后按它把正文里的旧地址原位换掉，
                        // 清掉就变成「又插一条」，同一张图在正文里出现两次
                        upload(item)
                    }
                }
            },
        )
        return
    }

    // 页面底色由最外层的 PageBackground 提供，本页保持透明。
    draftPrompt?.let { saved ->
        androidx.compose.material3.AlertDialog(
            // 点外面/返回键按「继续编辑」处理：草稿不能因为误触丢
            onDismissRequest = { keepDraft(saved) },
            title = { Text("继续编辑草稿？") },
            text = { Text("上次的草稿还没有发布。") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { keepDraft(saved) }) {
                    Text("继续编辑")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { dropDraft() }) {
                    Text("重新开始")
                }
            },
        )
    }

    if (confirmDiscard) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(if (editPost != null) "放弃修改？" else "放弃撰写？") },
            text = { Text(if (editPost != null) "尚未保存的修改不会被保留。" else "尚未发布的内容会留在草稿箱。") },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        confirmDiscard = false
                        staged.forEach { it.file.delete() }
                        staged.clear()
                        coverFile?.delete()
                        onBack()
                    },
                ) { Text("放弃") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmDiscard = false }) {
                    Text(if (editPost != null) "继续编辑" else "继续撰写")
                }
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
            title = if (editPost != null) "编辑帖子" else "撰写帖子",
            subtitle = "当前板块：${board.label}",
            // 顶栏返回与系统返回同一道确认，草稿不再被静默丢弃
            onBack = { if (hasDraft && !publishing) confirmDiscard = true else onBack() },
            actions = {
                if (publishing) {
                    YinYangOrb(size = 16.dp, modifier = Modifier.padding(end = 14.dp))
                } else {
                    PublishButton(
                        text = if (editPost != null) "保存" else "发布",
                        enabled = canPublish,
                        onClick = { publish() },
                    )
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
                progress = coverProgress,
                fetching = coverFetching,
                onPick = {
                    coverLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onEdit = if (coverFile != null || coverUrl != null) ({ refineCover() }) else null,
                onRemove = {
                    coverFile?.delete()
                    coverFile = null
                    coverUrl = null
                    coverProgress = -1f
                },
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
                onAction = { action ->
                    // 行内样式再点取消、列表/引用按整行、代码按单行/多行分流，全在纯函数里
                    body = applyMarkdownAction(body, action)
                    runCatching { bodyField.requestFocus() }
                    keyboard?.show()
                },
                onPickImage = { showImageSheet = true },
            )

            if (staged.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                StagedImageRow(
                    list = staged,
                    onEdit = { editing = EditTarget.Staged(it) },
                    onRemove = { removeStaged(it) },
                    onRetry = { upload(it) },
                )
            }

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

            // 正文区：输入槽直接在页面上，不再套一层凸起稿纸卡
            Box(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                ArisuTextField(
                    value = body,
                    onValueChange = { new ->
                        // 服务端正文上限 32000 字：超长粘贴在输入侧截住
                        if (new.text.length <= 32000) {
                            // 刚敲下的这个字符是 @ 就唤起提及选择器（同评论区）
                            val caret = new.selection.start
                            if (new.text.length == body.text.length + 1 && new.selection.collapsed &&
                                new.text.getOrNull(caret - 1) == '@'
                            ) {
                                showMention = true
                            }
                            body = new
                        }
                    },
                    placeholder = "写点什么……\n\n可用 **加粗**、*斜体*、`行内代码`，也可插入列表、引用与图片。",
                    singleLine = false,
                    minLines = 12,
                    fieldModifier = Modifier.focusRequester(bodyField),
                )
            }

            Spacer(Modifier.height(12.dp))

            // 板块选择放在表单末尾：发布前最后确认“发到哪”，避免再出现发错板块
            SectionLabel(text = "发送到板块", trailing = board.label)
            UnderlineTabs(
                items = Board.entries,
                selected = board,
                onSelect = { board = it },
                label = { it.label },
            )

            // 编号是发布时才分配的，改帖时这句没有意义
            if (editPost == null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "发布后的帖子将自动获得编号，如 arisu_000001",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 3.dp),
                )
            }
        }

        /* ── 图片来源选择 ───────────────────────────────── */
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
                        subtitle = "一次最多挑 $MAX_PICK 张，发前还能裁剪旋转",
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
                            // 顺手清掉历次进程死亡/失败遗留的孤儿截图（超过一天的都算）
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
                    Spacer(Modifier.height(10.dp))
                    ImageSourceRow(
                        icon = Icons.Rounded.EmojiEmotions,
                        title = "表情包",
                        subtitle = "从自留表情里挑一张，会动的那种也在",
                        onClick = {
                            showImageSheet = false
                            showStickers = true
                        },
                    )
                }
            }
        }

        /* ── 表情包：点选即按 Markdown 插进正文 ── */
        if (showStickers) {
            EmojiSheet(
                onSelect = { emoji -> body = insertMark(body, MdMark(emoji)) },
                onSticker = { sticker ->
                    body = insertMark(body, MdMark("![$MARKDOWN_STICKER_ALT](${sticker.url})"))
                },
                onDismiss = { showStickers = false },
            )
        }

        /* ── 提及选择器：正文里输入 @ 唤起，选人后把触发用的 @ 替换成完整编号 ── */
        if (showMention) {
            MentionPickerSheet(
                visible = true,
                onDismiss = { showMention = false },
                onPick = { u ->
                    body = applyMention(body, u.id)
                    showMention = false
                },
            )
        }
    }
}

/* ══════════════════════════════════════════════════════════════════
 * 待发配图
 * ══════════════════════════════════════════════════════════════════ */

/** 一张待发图：暂存件一路带着自己的上传状态，编辑过就换件重传 */
private class StagedImage(val id: Long, var file: File) {
    var progress by mutableStateOf(0f)
    var url by mutableStateOf<String?>(null)

    /** 已插进正文的那一段 Markdown：编辑重传时要原位换掉，而不是又插一条 */
    var markdown by mutableStateOf<String?>(null)
    var error by mutableStateOf<String?>(null)

    /** 用户已把这张图从网格移除：在途上传完成后不得再把地址插回正文 */
    var removed by mutableStateOf(false)
}

/** 正在编辑的是哪张：封面或某张正文配图 */
private sealed interface EditTarget {
    val path: String

    data class Staged(val item: StagedImage) : EditTarget {
        override val path: String get() = item.file.absolutePath
    }

    data class Cover(val file: File) : EditTarget {
        override val path: String get() = file.absolutePath
    }
}

/**
 * 待发图网格：一张一格，上传中是圆环加百分比，失败点一下重传，点图进编辑器。
 */
@Composable
private fun StagedImageRow(
    list: List<StagedImage>,
    onEdit: (StagedImage) -> Unit,
    onRemove: (StagedImage) -> Unit,
    onRetry: (StagedImage) -> Unit,
) {
    LazyRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 2.dp),
    ) {
        items(list, key = { it.id }) { item ->
            Box(Modifier.size(76.dp)) {
                coil3.compose.AsyncImage(
                    model = item.file,
                    contentDescription = "待发配图",
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = item.url != null) { onEdit(item) },
                )
                when {
                    item.error != null -> Box(
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.45f))
                            .clickable { onRetry(item) },
                        contentAlignment = Alignment.Center,
                    ) {
                        SoftPill(
                            text = "重试",
                            color = Color.White,
                            background = MaterialTheme.colorScheme.primary,
                        )
                    }

                    item.url == null -> Box(
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            progress = { item.progress },
                            strokeWidth = 3.dp,
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.25f),
                            modifier = Modifier.size(38.dp),
                        )
                        Text(
                            text = "${(item.progress * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                        )
                    }

                    else -> Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(3.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.45f))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = "编辑这张图",
                                tint = Color.White,
                                modifier = Modifier.size(10.dp),
                            )
                            Spacer(Modifier.width(2.dp))
                            Text(
                                text = "编辑",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                            )
                        }
                    }
                }
                // 移除：右上角小叉，压在图的上层
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .size(20.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable { onRemove(item) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "移除这张图",
                        tint = Color.White,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }
    }
}

/**
 * 封面选择区：未选时一枚凹槽占位，选中后展示缩略图，可更换/编辑/移除。
 * [fetching] 是「把旧封面取回本地取景」的空档，此时封面已是站上那张图，
 * 只是还不能改，所以只压一层转圈，不显示百分比。
 */
@Composable
private fun CoverPicker(
    coverUrl: String?,
    /** 0f–1f = 上传中；-1f = 空闲 */
    progress: Float,
    fetching: Boolean,
    onPick: () -> Unit,
    onEdit: (() -> Unit)?,
    onRemove: () -> Unit,
) {
    val busy = progress in 0f..1f || fetching
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 128.dp, height = 80.dp)) {
            if (coverUrl != null) {
                coil3.compose.AsyncImage(
                    model = coverUrl,
                    contentDescription = "封面预览",
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = onEdit != null) { onEdit?.invoke() },
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .neuInset(shape = RoundedCornerShape(12.dp), color = NeuInset)
                        .clickable(enabled = !busy) { onPick() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AddPhotoAlternate,
                        contentDescription = "选择封面图",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            if (busy) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        progress = { if (progress in 0f..1f) progress else 0f },
                        strokeWidth = 3.dp,
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.25f),
                        modifier = Modifier.size(38.dp),
                    )
                    if (progress in 0f..1f) {
                        Text(
                            text = "${(progress * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = when {
                    progress in 0f..1f -> "正在寄送封面……"
                    busy -> "封面已就绪"
                    coverUrl != null -> "封面已就绪"
                    else -> "从相册选择一张封面图"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(5.dp))
            Row {
                SoftPill(
                    text = if (coverUrl != null) "更换封面" else "选择封面图",
                    color = MaterialTheme.colorScheme.primary,
                    background = NeuInset,
                    modifier = Modifier.clickable(enabled = !busy) { onPick() },
                )
                if (onEdit != null) {
                    Spacer(Modifier.width(8.dp))
                    SoftPill(
                        text = "编辑封面",
                        color = MaterialTheme.colorScheme.primary,
                        background = NeuInset,
                        modifier = Modifier.clickable(enabled = !busy) { onEdit() },
                    )
                }
                if (coverUrl != null) {
                    Spacer(Modifier.width(8.dp))
                    SoftPill(
                        text = "移除封面",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        background = NeuInset,
                        modifier = Modifier.clickable(enabled = !busy) { onRemove() },
                    )
                }
            }
        }
    }
}

private fun createCameraFile(context: Context): Pair<Uri, File> {
    val dir = File(context.filesDir, "camera").apply { mkdirs() }
    val file = File(dir, "shot_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file) to file
}

/* ══════════════════════════════════════════════════════════════════
 * 顶部「发布」
 * ══════════════════════════════════════════════════════════════════ */

@Composable
private fun PublishButton(text: String, enabled: Boolean, onClick: () -> Unit) {
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
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) Color.White
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
    }
}

/* ══════════════════════════════════════════════════════════════════
 * Markdown 工具栏
 * ══════════════════════════════════════════════════════════════════ */

private data class MdTool(
    val icon: ImageVector,
    val label: String,
    /** null 表示需要额外弹面板（图片），由 onPickImage 处理 */
    val action: MdAction?,
)

private val MdTools = listOf(
    MdTool(Icons.Rounded.FormatBold, "加粗", MdAction.Bold),
    MdTool(Icons.Rounded.FormatItalic, "斜体", MdAction.Italic),
    MdTool(dev.arisu.shrine.core.components.ShrineIcons.Title, "标题", MdAction.Heading),
    MdTool(Icons.Rounded.FormatListBulleted, "列表", MdAction.List),
    MdTool(Icons.Rounded.Code, "代码", MdAction.Code),
    MdTool(Icons.Rounded.FormatQuote, "引用", MdAction.Quote),
    MdTool(Icons.Rounded.Image, "图片", null),
    MdTool(Icons.Rounded.Link, "链接", MdAction.Link),
)

@Composable
private fun MarkdownToolbar(
    onAction: (MdAction) -> Unit,
    onPickImage: () -> Unit,
) {
    // Markdown 工具条：扁平文字按钮，上下细线分割（与 WebUI 一致）
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
                        val action = tool.action
                        if (action != null) onAction(action) else onPickImage()
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

/* ══════════════════════════════════════════════════════════════════
 * 小件
 * ══════════════════════════════════════════════════════════════════ */

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

/** 提及补全：把触发用的那个 @ 换成完整编号（光标未必在末尾，按光标前一位找）。 */
private fun applyMention(value: TextFieldValue, userId: String): TextFieldValue {
    val text = value.text
    val caret = value.selection.max.coerceIn(0, text.length)
    val at = if (caret in 1..text.length && text[caret - 1] == '@') caret - 1 else caret
    val insert = "@$userId "
    val end = if (at < text.length && text[at] == '@') at + 1 else at
    return value.replaceRangeWith(at, end, insert)
}
