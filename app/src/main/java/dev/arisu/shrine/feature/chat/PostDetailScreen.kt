package dev.arisu.shrine.feature.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.CurrencyYen
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.arisu.shrine.core.components.ArisuConfirmDialog
import dev.arisu.shrine.core.components.DraftRestoredBanner
import dev.arisu.shrine.core.components.OfferSheet
import dev.arisu.shrine.core.components.EmojiSheet
import dev.arisu.shrine.core.components.FadeInOut
import dev.arisu.shrine.core.components.ArisuAvatar
import dev.arisu.shrine.core.components.HairlineDivider
import dev.arisu.shrine.core.components.ScrollTopOrb
import dev.arisu.shrine.core.components.ArisuTextField
import dev.arisu.shrine.core.components.ArisuTopBar
import dev.arisu.shrine.feature.editor.COVER_ASPECT
import dev.arisu.shrine.core.components.CodeLabel
import dev.arisu.shrine.core.components.RoleTag
import dev.arisu.shrine.core.components.SectionLabel
import dev.arisu.shrine.core.components.ShrineEmptyState
import dev.arisu.shrine.core.components.ShrineLoadGate
import dev.arisu.shrine.core.components.ShrineStaleBanner
import dev.arisu.shrine.core.components.ShrineNoticeBar
import dev.arisu.shrine.core.components.ShrinePullRefreshBox
import dev.arisu.shrine.core.components.ShrineLoadingState
import dev.arisu.shrine.core.components.ShrineListEnd
import dev.arisu.shrine.core.components.SoftPill
import dev.arisu.shrine.core.components.TopBarIcon
import dev.arisu.shrine.core.components.accentRaised
import dev.arisu.shrine.core.components.glassPanel
import dev.arisu.shrine.core.components.neuInset
import dev.arisu.shrine.core.components.smoothScrollToTop
import dev.arisu.shrine.core.components.softCard
import dev.arisu.shrine.core.components.ImageViewerDialog
import dev.arisu.shrine.core.design.InkFaint
import dev.arisu.shrine.core.design.InkMuted
import dev.arisu.shrine.core.design.NeuBaseHigh
import dev.arisu.shrine.core.design.NeuInset
import dev.arisu.shrine.core.design.NeuShadow
import dev.arisu.shrine.core.design.ReadingScale
import dev.arisu.shrine.core.design.Vermilion
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import dev.arisu.shrine.core.markdown.MdMark
import dev.arisu.shrine.core.markdown.MarkdownText
import dev.arisu.shrine.core.markdown.insertMark
import dev.arisu.shrine.core.markdown.parseMarkdown
import dev.arisu.shrine.core.markdown.replaceRangeWith
import dev.arisu.shrine.data.local.Draft
import dev.arisu.shrine.data.local.DraftStore
import dev.arisu.shrine.data.local.HistoryStore
import dev.arisu.shrine.data.model.Comment
import dev.arisu.shrine.data.model.Post
import dev.arisu.shrine.data.model.countCn
import dev.arisu.shrine.data.repo.Shrine
import dev.arisu.shrine.feature.share.ShareCard
import dev.arisu.shrine.feature.share.ShareCardData
import dev.arisu.shrine.data.remote.toApiMessage
import kotlinx.serialization.json.Json
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 回复对象的 saved state 载体：Comment 经 kotlinx 序列化成 JSON 字符串。 */
private val replyJson = Json { ignoreUnknownKeys = true }
private val ReplySaver = Saver<Comment?, String>(
    save = { it?.let { c -> replyJson.encodeToString(Comment.serializer(), c) } },
    restore = { it?.let { s -> runCatching { replyJson.decodeFromString(Comment.serializer(), s) }.getOrNull() } },
)

/**
 * 帖子详情。
 *
 * 标题栏：返回 + 更多（编辑 / 删除 / 复制帖子编号 / 举报）
 * 正文：  标题 / 作者卡 / Markdown 正文卡（图片可点开全屏）
 * 互动：  顶 / 追踪 / 收藏 / 评论，四等分，整条一块凸起面
 * 评论：  按主消息归组（回复紧跟父消息，回复与父消息之间不画分割线），长按评论弹出复制 / 举报 / 删除
 * 底部：  悬浮的毛玻璃输入条（表情面板 + 发送）
 *
 * 改帖不在这里做：走 [onEditPost] 去发帖页那套整页面板（弹窗里既写不下长文、
 * 也没地方给封面重新取景）。改完退回来重新取一遍，页面拿到的就是新稿。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PostDetailScreen(
    postId: String,
    onBack: () -> Unit,
    onOpenUser: (String) -> Unit,
    modifier: Modifier = Modifier,
    onEditPost: (String) -> Unit = {},
) {
    val clipboard = LocalClipboardManager.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // 首帧：先把上次读到的帖与评论摆上来（列表点进来几乎无感），网络回来再覆盖。
    // 拿得到缓存就当已就绪，刷新失败也不把用户从内容上踢下去
    var post by remember { mutableStateOf(Shrine.peekPost(postId)) }
    var comments by remember { mutableStateOf(Shrine.peekComments(postId) ?: emptyList()) }
    // 显示用：回复归到主消息名下（分割线规则也依赖这个次序）
    val commentsShown = remember(comments) { comments.groupedByThread() }
    var loadState by remember { mutableStateOf(if (post == null) "loading" else "ok") } // loading | ok | missing | fail
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val me by Shrine.me.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    var menu by remember { mutableStateOf(false) }
    var deletingPost by remember { mutableStateOf(false) }
    var deletingComment by remember { mutableStateOf<Comment?>(null) }
    // 顶/追踪共用一把在途锁：双击不再发两次 toggle
    var toggling by remember { mutableStateOf(false) }
    // 评论点赞的在途锁与失败提示：连点会发多次 toggle（偶数次=没点上）
    var likedBusy by remember { mutableStateOf<Set<String>>(emptySet()) }
    var reporting by remember { mutableStateOf(false) }
    var reportTargetComment by remember { mutableStateOf<dev.arisu.shrine.data.model.Comment?>(null) }
    var showCollections by remember { mutableStateOf(false) }
    var sheetBusy by remember { mutableStateOf(false) }
    var collectionItems by remember { mutableStateOf<List<dev.arisu.shrine.data.model.Collection>>(emptyList()) }
    var activeCollectionId by remember { mutableStateOf<String?>(null) }
    var showEmoji by remember { mutableStateOf(false) }
    // 奉纳弹窗（在途与动画状态由弹窗自管）
    var offering by remember { mutableStateOf(false) }
    var previewUrl by remember { mutableStateOf<String?>(null) }
    // 评论附图：先落暂存件过一遍编辑器，再带圆环进度上传
    var pendingCommentImage by remember { mutableStateOf<java.io.File?>(null) }
    var uploadProgress by remember { mutableStateOf(-1f) }
    // 已经收进表情夹的图：全屏看图里据此把「添加到表情包」变成「已添加」
    var collectedStickers by remember { mutableStateOf<Set<String>>(emptySet()) }
    // 草稿与回复对象跨页面驻留：点评论者头像串门再回来，写到一半的话还在。
    // 带选区：表情/提及插在光标处后，光标要跟着内容走，不能再落进 Markdown 里
    var draft by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    var replyTo by rememberSaveable(stateSaver = ReplySaver) { mutableStateOf<Comment?>(null) }
    // 草稿附带图：存的是本站图床 URL（上传后即定），发出去才离开草稿
    var draftImage by rememberSaveable { mutableStateOf<String?>(null) }
    // 本地草稿只在进入页面时恢复一次；跨页面返回时 rememberSaveable 里的更近
    var draftLoaded by rememberSaveable(postId) { mutableStateOf(false) }
    var draftRestored by rememberSaveable(postId) { mutableStateOf(false) }
    // 续读：只恢复一次；「上次读到」细线停在那条评论上方
    var restoreAttempted by rememberSaveable(postId) { mutableStateOf(false) }
    var lastReadMarker by rememberSaveable(postId) { mutableStateOf<String?>(null) }
    var uploadingImage by remember { mutableStateOf(false) }
    var newCollection by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var showMention by remember { mutableStateOf(false) }
    // 触发选择器的那个 @ 在正文里的位置；选人后原位替换，不再假设它在末尾
    var mentionAt by remember { mutableStateOf(-1) }

    // 首载与下拉刷新共用。刷新失败时保留已有内容继续阅读，只有首载才进入错误态
    suspend fun refresh() {
        try {
            val p = Shrine.post(postId)
            // 长正文的块解析放到后台，主线程只负责渲染缓存结果；
            // 解析失败也照常显示原文，不把整页打进错误态
            withContext(Dispatchers.Default) { runCatching { parseMarkdown(p.body) } }
            post = p
            comments = Shrine.commentsOf(postId)
            loadState = "ok"
            errorMsg = null
        } catch (e: dev.arisu.shrine.data.remote.ApiError) {
            if (e.message?.contains("不存在") == true) {
                loadState = "missing"
            } else if (loadState == "loading") {
                loadState = "fail"
            }
            errorMsg = e.message
        } catch (e: Exception) {
            if (loadState == "loading") loadState = "fail"
            errorMsg = e.toApiMessage()
        }
    }

    LaunchedEffect(postId) {
        refresh()
        if (!draftLoaded) {
            val uid = Shrine.me.value?.id
            if (uid != null && draft.text.isBlank() && draftImage == null && replyTo == null) {
                val saved = DraftStore.get(uid, DraftStore.COMMENT_POST, postId)
                // 读盘这几毫秒里用户可能已经落笔：那就别再用旧稿盖掉
                if (saved != null && draft.text.isBlank() && draftImage == null && replyTo == null &&
                    (saved.body.isNotBlank() || saved.imageUrl != null || saved.replyToId != null)
                ) {
                    draft = TextFieldValue(saved.body, TextRange(saved.body.length))
                    draftImage = saved.imageUrl
                    // 评论列表取成功且找不到 → 原回复对象已删除，丢掉 parentId，
                    // 否则发出去只会被服务端整条打回；正文与附图仍恢复。
                    // 首载失败时列表可能为空，不能据此判断，保留草稿里的对象等重试
                    replyTo = saved.replyToId?.let { id ->
                        comments.firstOrNull { it.id == id }
                            ?: if (loadState == "ok") null else Comment(
                                id = id,
                                postId = postId,
                                authorId = "",
                                authorName = saved.replyToNickname.orEmpty(),
                                content = "",
                                createdAt = "",
                            )
                    }
                    draftRestored = true
                }
            }
            // 恢复完再放行自动保存，否则空表单会先把本地那份删掉
            draftLoaded = true
        }
    }

    // 输入停顿后落盘；清空即删掉本地那份
    LaunchedEffect(draft.text, draftImage, replyTo, draftLoaded, post?.title) {
        if (!draftLoaded) return@LaunchedEffect
        val uid = Shrine.me.value?.id ?: return@LaunchedEffect
        if (draft.text.isBlank() && draftImage == null && replyTo == null) {
            DraftStore.deleteAsync(uid, DraftStore.COMMENT_POST, postId)
            return@LaunchedEffect
        }
        delay(600)
        DraftStore.saveAsync(
            Draft(
                userId = uid,
                kind = DraftStore.COMMENT_POST,
                targetId = postId,
                targetTitle = post?.title.orEmpty(),
                body = draft.text,
                imageUrl = draftImage,
                replyToId = replyTo?.id,
                replyToNickname = replyTo?.authorName,
            ),
        )
    }

    /**
     * 服务端删评论会连整棵子树一起软删；本地列表与缓存也按同一范围移除，
     * 否则子评论会留在界面上，重进还会从旧缓存里先闪一下。
     */
    suspend fun forgetDeletedComment(target: Comment) {
        val removed = comments.subtreeIds(target.id)
        val left = comments.filterNot { it.id in removed }
        comments = left
        post = post?.copy(
            commentCount = ((post?.commentCount ?: removed.size) - removed.size).coerceAtLeast(0),
        )
        post?.let { Shrine.cachePost(postId, it) }
        Shrine.cacheComments(postId, left)
    }

    /** 上传评论附图：走放宽超时的上传通道，进度落在输入条的圆环上 */
    fun uploadCommentImage(file: java.io.File) {
        uploadingImage = true
        uploadProgress = 0f
        scope.launch {
            try {
                draftImage = Shrine.uploadImage(file) { uploadProgress = it }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                errorMsg = e.toApiMessage()
            }
            uploadingImage = false
            uploadProgress = -1f
        }
    }

    // 足迹与续读
    LaunchedEffect(post?.id) {
        val p = post ?: return@LaunchedEffect
        val uid = Shrine.me.value?.id ?: return@LaunchedEffect
        HistoryStore.visit(uid, HistoryStore.POST, postId, p.title, p.board.label)
    }

    LaunchedEffect(loadState, commentsShown) {
        if (restoreAttempted || loadState != "ok" || commentsShown.isEmpty()) return@LaunchedEffect
        restoreAttempted = true
        val uid = Shrine.me.value?.id ?: return@LaunchedEffect
        val entry = HistoryStore.get(uid, HistoryStore.POST, postId) ?: return@LaunchedEffect
        val id = entry.lastCommentId ?: return@LaunchedEffect
        val index = commentsShown.indexOfFirst { it.id == id }
        // 原评论被删时退到记下的下标，至少回到附近；找不到就放弃
        val target = if (index >= 0) index else entry.lastIndex
        if (target < 10 || target >= commentsShown.size) return@LaunchedEffect
        lastReadMarker = if (index >= 0) id else null
        listState.scrollToItem(3 + target, if (index >= 0) entry.lastOffset.coerceAtLeast(0) else 0)
    }

    val shownNow by rememberUpdatedState(commentsShown)
    LaunchedEffect(restoreAttempted) {
        if (!restoreAttempted) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collectLatest { (lazyIndex, offset) ->
                delay(700)
                val uid = Shrine.me.value?.id ?: return@collectLatest
                val index = lazyIndex - 3
                val comment = shownNow.getOrNull(index) ?: return@collectLatest
                HistoryStore.savePositionAsync(uid, HistoryStore.POST, postId, comment.id, index, offset)
            }
    }

    DisposableEffect(postId) {
        onDispose {
            val uid = Shrine.me.value?.id ?: return@onDispose
            val index = listState.firstVisibleItemIndex - 3
            val comment = shownNow.getOrNull(index) ?: return@onDispose
            HistoryStore.savePositionAsync(
                uid, HistoryStore.POST, postId, comment.id, index,
                listState.firstVisibleItemScrollOffset,
            )
        }
    }

    val currentUser = me
    val isAuthor = post != null && currentUser != null && post!!.authorId == currentUser.id
    val canDeletePost = isAuthor || (currentUser?.role?.canModerate ?: false)

    // 页面底色由最外层的 PageBackground 提供，本页保持透明。
    Column(
        modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        ArisuTopBar(
            title = "帖子详情",
            subtitle = post?.let { "${it.board.label} · ${it.id}" } ?: postId,
            onBack = onBack,
            actions = {
                Box {
                    TopBarIcon(
                        icon = Icons.Rounded.MoreVert,
                        contentDescription = "更多操作",
                        onClick = { menu = true },
                    )
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (isAuthor) {
                            DropdownMenuItem(
                                text = { Text("编辑") },
                                leadingIcon = { MenuIcon(Icons.Rounded.Edit) },
                                onClick = {
                                    menu = false
                                    onEditPost(postId)
                                },
                            )
                        }
                        if (canDeletePost) {
                            DropdownMenuItem(
                                text = { Text("删除") },
                                leadingIcon = { MenuIcon(Icons.Rounded.Delete) },
                                onClick = {
                                    menu = false
                                    deletingPost = true
                                },
                            )
                        }
                        // 宫司及以上：精华 / 置顶（服务端二次校验）
                        if ((currentUser?.role?.rank ?: 0) >= 4) {
                            DropdownMenuItem(
                                text = { Text(if (post?.essence == true) "取消精华" else "设为精华") },
                                leadingIcon = { MenuIcon(Icons.Rounded.Star) },
                                onClick = {
                                    menu = false
                                    // 与顶/追踪同一把在途锁：在途连点会来回 toggle
                                    if (!toggling) {
                                        toggling = true
                                        scope.launch {
                                            try {
                                                val e = Shrine.toggleEssence(postId)
                                                post = post?.copy(essence = e)
                                            } catch (e: Exception) {
                                                errorMsg = e.toApiMessage()
                                            }
                                            toggling = false
                                        }
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(if (post?.pinned == true) "取消置顶" else "置顶") },
                                leadingIcon = { MenuIcon(Icons.Rounded.PushPin) },
                                onClick = {
                                    menu = false
                                    if (!toggling) {
                                        toggling = true
                                        scope.launch {
                                            try {
                                                val pp = Shrine.togglePin(postId)
                                                post = post?.copy(pinned = pp)
                                            } catch (e: Exception) {
                                                errorMsg = e.toApiMessage()
                                            }
                                            toggling = false
                                        }
                                    }
                                },
                            )
                        }
                        if (post != null) {
                            DropdownMenuItem(
                                text = { Text("生成分享卡片") },
                                leadingIcon = { MenuIcon(Icons.Rounded.Share) },
                                onClick = {
                                    menu = false
                                    val p = post ?: return@DropdownMenuItem
                                    scope.launch {
                                        try {
                                            val images = (
                                                listOfNotNull(p.coverUrl?.takeIf { it.isNotBlank() }) +
                                                    ShareCard.images(p.body)
                                                ).distinct()
                                            ShareCard.share(
                                                context,
                                                ShareCardData(
                                                    kind = "帖子",
                                                    meta = p.board.label,
                                                    id = p.id,
                                                    title = p.title,
                                                    body = ShareCard.plainText(p.body),
                                                    author = p.authorName,
                                                    time = p.createdAt,
                                                    images = images,
                                                ),
                                            )
                                        } catch (e: Exception) {
                                            if (e is kotlinx.coroutines.CancellationException) throw e
                                            errorMsg = e.toApiMessage()
                                        }
                                    }
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("复制帖子编号") },
                            leadingIcon = { MenuIcon(Icons.Rounded.ContentCopy) },
                            onClick = {
                                menu = false
                                clipboard.setText(AnnotatedString(postId))
                                notice = "编号已复制：" + postId
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("举报") },
                            leadingIcon = { MenuIcon(Icons.Rounded.Flag) },
                            onClick = {
                                menu = false
                                reportTargetComment = null
                                reporting = true
                            },
                        )
                    }
                }
            },
        )

        ShrineStaleBanner()

        ShrineNoticeBar(
            text = notice ?: errorMsg,
            onDismiss = { notice = null; errorMsg = null },
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = 6.dp),
        )

        // weight 必须在 Column 作用域内取好：gate 本体作为 Column 的加权子级
        val detailWeight = Modifier.fillMaxWidth().weight(1f)
        ShrineLoadGate(loading = loadState == "loading", modifier = detailWeight) {
        when (loadState) {
            "loading" -> {}
            "missing" -> ShrineEmptyState(note = "帖子不存在", onClick = onBack)
            "fail" -> ShrineEmptyState(note = "加载失败，点击重试", onClick = { loadState = "loading"; scope.launch { refresh() } })
            else -> {
                val p = post ?: return@ShrineLoadGate
                val replyingTo = replyTo

                ShrinePullRefreshBox(
                    onRefresh = { refresh() },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                    ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        // 底部留白要盖过悬浮输入条，最后一条评论才不会被压住
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 18.dp,
                            // 悬浮输入条会随手写的内容增高：回复条 60 + 附图预览 68 都要让出来
                            bottom = 122.dp +
                                (if (replyingTo != null) 60.dp else 0.dp) +
                                (if (draftImage != null) 68.dp else 0.dp),
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item { PostHead(post = p, onOpenUser = onOpenUser, onImageClick = { previewUrl = it }) }

                        item {
                            PostActionBar(
                                post = p,
                                onOffer = { offering = true },
                                onUp = {
                                    // 乐观更新，再交给服务器确认；在途防抖防双击双重 toggle
                                    if (!toggling) {
                                        toggling = true
                                        post = p.copy(upvoted = !p.upvoted, upCount = p.upCount + if (p.upvoted) -1 else 1)
                                        scope.launch {
                                            try {
                                                val (upped, count) = Shrine.toggleUp(postId)
                                                post = post?.copy(upvoted = upped, upCount = count)
                                            } catch (e: Exception) {
                                                errorMsg = e.toApiMessage()
                                                // 只回滚这两个字段，不整体覆盖期间发生的其它更新；
                                                // 回滚值是点击时的快照，若期间正好刷新过就再拉权威数据纠正
                                                post = post?.copy(upvoted = p.upvoted, upCount = p.upCount)
                                                runCatching { Shrine.post(postId) }.getOrNull()?.let { fresh ->
                                                    post = post?.copy(upvoted = fresh.upvoted, upCount = fresh.upCount)
                                                }
                                            }
                                            toggling = false
                                        }
                                    }
                                },
                                onTrack = {
                                    if (!toggling) {
                                        toggling = true
                                        post = p.copy(tracked = !p.tracked)
                                        scope.launch {
                                            try {
                                                val tracked = Shrine.toggleTrack(postId)
                                                post = post?.copy(tracked = tracked)
                                            } catch (e: Exception) {
                                                errorMsg = e.toApiMessage()
                                                post = post?.copy(tracked = p.tracked)
                                                runCatching { Shrine.post(postId) }.getOrNull()?.let { fresh ->
                                                    post = post?.copy(tracked = fresh.tracked)
                                                }
                                            }
                                            toggling = false
                                        }
                                    }
                                },
                                onFavorite = {
                                    showCollections = true
                                    scope.launch {
                                        try {
                                            collectionItems = Shrine.collections()
                                        } catch (e: Exception) {
                                            // 拉取失败不弹空面板，避免「没有收藏夹可选」的假象
                                            showCollections = false
                                            errorMsg = e.toApiMessage()
                                        }
                                    }
                                },
                                onComment = { scope.launch { listState.animateScrollToItem(2) } },
                            )
                        }

                        item { SectionLabel(text = "评论 ${comments.size}", trailing = "按时间正序") }

                        if (comments.isEmpty()) {
                            item { ShrineEmptyState(note = "没有评论") }
                        } else {
                            itemsIndexed(
                                commentsShown,
                                key = { _, c -> c.id },
                                // 有无图、是否楼中楼分槽，LazyColumn 复用得更准
                                contentType = { _, c ->
                                    when {
                                        c.imageUrl != null && c.parentId != null -> "comment_image_reply"
                                        c.imageUrl != null -> "comment_image"
                                        c.parentId != null -> "comment_reply"
                                        else -> "comment"
                                    }
                                },
                            ) { index, comment ->
                                // 分割线只落在主消息与主消息之间：下一条是回复就不画
                                val dividerAfter = index == commentsShown.lastIndex ||
                                    commentsShown[index + 1].parentId == null
                                CommentItem(
                                    comment = comment,
                                    showFollowingDivider = dividerAfter,
                                    showLastReadMarker = comment.id == lastReadMarker,
                                    canModerate = currentUser?.role?.canModerate ?: false,
                                    onOpenUser = onOpenUser,
                                    onLike = label@{
                                        if (comment.id in likedBusy) return@label
                                        likedBusy = likedBusy + comment.id
                                        scope.launch {
                                            // 乐观更新先行，失败回滚并就地提示
                                            comments = comments.map {
                                                if (it.id == comment.id) it.copy(
                                                    liked = !comment.liked,
                                                    likeCount = it.likeCount + if (comment.liked) -1 else 1,
                                                ) else it
                                            }
                                            try {
                                                val (liked, count) = Shrine.likeComment(comment.id)
                                                comments = comments.map {
                                                    if (it.id == comment.id) it.copy(liked = liked, likeCount = count) else it
                                                }
                                            } catch (e: Exception) {
                                                comments = comments.map {
                                                    if (it.id == comment.id) it.copy(liked = comment.liked, likeCount = comment.likeCount) else it
                                                }
                                                notice = e.toApiMessage()
                                            }
                                            likedBusy = likedBusy - comment.id
                                        }
                                    },
                                    onReply = { replyTo = comment },
                                    onDelete = {
                                        // 删除一律先确认：自己的评论弹简单确认，
                                        // 奉仕及以上删别人的评论需要理由（参考 B 站/QQ 的防误触）
                                        deletingComment = comment
                                    },
                                    onCopy = { clipboard.setText(AnnotatedString(comment.content)) },
                                    onReport = {
                                        reportTargetComment = comment
                                        reporting = true
                                    },
                                    onOpenImage = { previewUrl = it },
                                )
                            }
                            item { ShrineListEnd(text = "已经到底了 · 共 ${comments.size} 条评论") }
                        }
                    }

                    // 回到顶部阴阳玉：滚过一屏后浮现于左下角（输入条上方）
                    ScrollTopOrb(
                        listState = listState,
                        onClick = { scope.launch { listState.smoothScrollToTop() } },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 92.dp),
                    )

                    // 输入条浮在内容之上，不占据列表高度
                    Column(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(),
                    ) {
                        if (draftRestored) {
                            DraftRestoredBanner(onDismiss = { draftRestored = false })
                        }
                        if (replyingTo != null) {
                            ReplyBanner(
                                nickname = replyingTo.authorName,
                                onCancel = { replyTo = null },
                            )
                        }
                        CommentInputBar(
                            draft = draft,
                            draftImage = draftImage,
                            uploading = uploadingImage,
                            uploadProgress = uploadProgress,
                            onDraftChange = { new ->
                                // 只认刚敲下的一个 @：粘贴、撤销、改写不打扰；
                                // 按公共前缀找插入点，光标在句中也能唤起
                                if (new.text.length <= 2000) {
                                    insertedAt(draft.text, new.text)?.let { at ->
                                        if (new.text[at] == '@') {
                                            mentionAt = at
                                            showMention = true
                                        }
                                    }
                                    draft = new
                                }
                            },
                            onEmoji = { showEmoji = true },
                            onPickImage = pickImage@{ uri ->
                                if (uploadingImage) return@pickImage
                                // 先落暂存件再开编辑器：裁剪/旋转确定后才真的上传
                                scope.launch {
                                    val file = try {
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            dev.arisu.shrine.core.media.stagePickedImage(
                                                context, uri, java.io.File(context.cacheDir, "upload"),
                                            )
                                        }
                                    } catch (e: Exception) {
                                        if (e is kotlinx.coroutines.CancellationException) throw e
                                        errorMsg = e.toApiMessage()
                                        null
                                    }
                                    if (file != null) pendingCommentImage = file
                                }
                            },
                            onRemoveImage = { draftImage = null },
                            onSend = {
                                val text = draft.text.trim()
                                if (text.isNotEmpty() && !sending && !uploadingImage) {
                                    sending = true
                                    scope.launch {
                                        try {
                                            val created = Shrine.addComment(
                                                postId, text,
                                                parentId = replyTo?.id,
                                                replyToUserId = replyTo?.authorId?.takeIf { it.isNotBlank() },
                                                imageUrl = draftImage?.takeIf { it.isNotBlank() },
                                            )
                                            // 发送成功即乐观落列表并清稿：此前先清稿再重取，
                                            // 重取失败草稿没了、列表也没更新，用户会以为没发出去
                                            draft = TextFieldValue("")
                                            draftImage = null
                                            replyTo = null
                                            Shrine.me.value?.id?.let {
                                                DraftStore.deleteAsync(it, DraftStore.COMMENT_POST, postId)
                                            }
                                            comments = comments + created
                                            post = post?.copy(commentCount = comments.size)
                                            focusManager.clearFocus()
                                            try {
                                                comments = Shrine.commentsOf(postId)
                                                post = post?.copy(commentCount = comments.size)
                                            } catch (e: Exception) {
                                                if (e is kotlinx.coroutines.CancellationException) throw e
                                            }
                                        } catch (e: Exception) {
                                            errorMsg = e.toApiMessage()
                                        }
                                        sending = false
                                    }
                                }
                            },
                        )
                    }
                }
            }
            }
        }
        }

        /* ── 奉纳赛钱（赛钱箱 + 投币动画） ─────────────── */
        if (offering) {
            post?.let { cur ->
                OfferSheet(
                    post = cur,
                    submitter = { amount ->
                        val (_, total) = Shrine.offerPost(postId, amount)
                        total
                    },
                    onDone = { newTotal -> post = post?.copy(offerCount = newTotal, offered = true) },
                    onDismiss = { offering = false },
                )
            }
        }

        /* ── 删除帖子（作者直接删；管理删除需填理由） ──── */
        if (deletingPost) {
            ArisuConfirmDialog(
                title = "删除帖子",
                body = if (isAuthor) "删除后不可恢复。" else "删除后不可恢复，删除理由将通知给作者。",
                confirmText = "确认删除",
                reasonHint = if (isAuthor) null else "删除理由，此将通知给作者",
                onConfirm = { reason ->
                    Shrine.deletePost(postId, reason.ifBlank { null })
                    deletingPost = false
                    onBack()
                },
                onDismiss = { deletingPost = false },
            )
        }

        /* ── 删除评论（一律先确认） ──────────────────────── */
        deletingComment?.let { target ->
            if (target.mine) {
                ArisuConfirmDialog(
                    title = "删除这条评论？",
                    body = "删除后不可恢复。",
                    confirmText = "删除",
                    onConfirm = {
                        Shrine.deleteComment(target.id)
                        forgetDeletedComment(target)
                        deletingComment = null
                    },
                    onDismiss = { deletingComment = null },
                )
            } else {
                ArisuConfirmDialog(
                    title = "删除评论",
                    body = "删除后不可恢复，将通知该评论作者。",
                    confirmText = "确认删除",
                    reasonHint = "删除理由（必填）",
                    onConfirm = { reason ->
                        Shrine.deleteComment(target.id, reason)
                        forgetDeletedComment(target)
                        deletingComment = null
                    },
                    onDismiss = { deletingComment = null },
                )
            }
        }

        /* ── 举报（理由必填） ───────────────────────────── */
        if (reporting) {
            ArisuConfirmDialog(
                title = "举报内容",
                body = "处理结果会通过社务箱通知您。",
                confirmText = "提交举报",
                reasonHint = "举报理由（必填）",
                onConfirm = { reason ->
                    val target = reportTargetComment
                    if (target != null) Shrine.reportComment(target.id, reason)
                    else Shrine.reportPost(postId, reason)
                    reporting = false
                    notice = "已提交举报，处理结果会通过社务箱通知您"
                },
                onDismiss = { reporting = false },
            )
        }

        /* ── 收藏夹选择面板 ─────────────────────────────── */
        if (showCollections) {
            ModalBottomSheet(onDismissRequest = { showCollections = false }) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, bottom = 28.dp)
                ) {
                    Text(
                        text = "收藏到",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "同一份内容只保存在一个收藏夹",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))

                    // 每次打开面板重新初始化选中（if 块随面板离场，remember 状态随之重置）
                    var selectedCollectionId by remember { mutableStateOf<String?>(activeCollectionId) }

                    collectionItems.forEach { collection ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    // 只改选中，点「确认」才提交；再点已选中项 = 取消选中
                                    selectedCollectionId =
                                        if (selectedCollectionId == collection.id) null else collection.id
                                }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ArisuAvatar(
                                seed = collection.coverSeed,
                                name = collection.name,
                                size = 32.dp,
                                url = collection.coverUrl,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = collection.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "${collection.count} 个内容" +
                                        if (collection.isDefault) " · 默认收藏夹" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            val checked = selectedCollectionId == collection.id
                            Icon(
                                imageVector = if (checked) Icons.Rounded.Check else Icons.Rounded.Add,
                                contentDescription = if (checked) "已选中" else "未选中",
                                tint = if (checked) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // 确认按钮：选中夹子后提交；帖子已收藏但未选中任何夹子时，按钮转为「移出收藏」
                    val moving = selectedCollectionId == null && (post?.favorited == true)
                    SheetButton(
                        text = if (moving) "移出收藏" else "确认",
                        enabled = (selectedCollectionId != null || moving) && !sheetBusy,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = label@{
                            if (sheetBusy) return@label
                            sheetBusy = true
                            scope.launch {
                                try {
                                    val targetName = collectionItems
                                        .firstOrNull { it.id == selectedCollectionId }?.name
                                    // collection_id=0 = 服务端的「移出收藏」显式指令；
                                    // 传默认夹 id 会把别夹的帖子悄悄搬进默认夹
                                    val favored = Shrine.setFavorite(
                                        postId,
                                        if (moving) 0L else selectedCollectionId?.toLongOrNull(),
                                    )
                                    // 收藏数本地同步：换夹子不算增减，只有入藏/移出才 ±1
                                    val delta = if (favored == (post?.favorited == true)) 0 else if (favored) 1 else -1
                                    post = post?.copy(favorited = favored, saveCount = (post?.saveCount ?: 0) + delta)
                                    activeCollectionId = if (favored) selectedCollectionId else null
                                    showCollections = false
                                    notice = if (favored) "已收藏到「" + (targetName ?: "") + "」" else "已移出收藏"
                                } catch (e: Exception) {
                                    notice = e.toApiMessage()
                                }
                                sheetBusy = false
                            }
                        },
                    )

                    Spacer(Modifier.height(14.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ArisuTextField(
                            value = newCollection,
                            onValueChange = { if (it.length <= 20) newCollection = it },
                            placeholder = "新建收藏夹（最多 20 字）",
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(10.dp))
                        SheetButton(
                            text = "新建",
                            // 与确认按钮共用 sheetBusy：连点会并发建出两个同名夹
                            enabled = newCollection.isNotBlank() && !sheetBusy,
                            onClick = label@{
                                if (sheetBusy) return@label
                                sheetBusy = true
                                scope.launch {
                                    try {
                                        val created = Shrine.createCollection(newCollection.trim())
                                        collectionItems = Shrine.collections()
                                        newCollection = ""
                                        activeCollectionId = created.id
                                    } catch (e: Exception) {
                                        errorMsg = e.toApiMessage()
                                    }
                                    sheetBusy = false
                                }
                            },
                        )
                    }
                }
            }
        }

        /* ── 表情面板 ───────────────────────────────────── */
        if (showEmoji) {
            EmojiSheet(
                onSelect = { draft = insertMark(draft, MdMark(it)) },
                // 评论只带一张图，表情包点一下就挂在这张图上
                onSticker = { sticker -> draftImage = sticker.url },
                onDismiss = { showEmoji = false },
            )
        }

        /* ── 提及选择器：输入 @ 唤起，选人后把触发用的 @ 替换成完整编号 ── */
        if (showMention) {
            MentionPickerSheet(
                visible = true,
                onDismiss = {
                    showMention = false
                    mentionAt = -1
                },
                onPick = { u ->
                    val at = mentionAt.takeIf { it in draft.text.indices && draft.text[it] == '@' }
                    draft = if (at != null) {
                        // 光标落到 “@编号 ” 之后：接着打字才不会把编号拆开
                        draft.replaceRangeWith(at, at + 1, "@${u.id} ")
                    } else {
                        val insert = "@${u.id} "
                        TextFieldValue(draft.text + insert, TextRange(draft.text.length + insert.length))
                    }
                    mentionAt = -1
                    showMention = false
                },
            )
        }

        /* ── 图片放大 ───────────────────────────────────── */
        val previewing = previewUrl
        if (previewing != null) {
            ImageViewerDialog(
                url = previewing,
                addedToStickers = previewing in collectedStickers,
                onAddSticker = { imageUrl ->
                    // 成败由看图器自己在底部提示，这里只负责落库与状态
                    Shrine.addSticker(imageUrl)
                    collectedStickers = collectedStickers + imageUrl
                },
                onDismiss = { previewUrl = null },
            )
        }

        /* ── 附图编辑：盖在全屏之上，确定才上传 ─────────── */
        val editingImage = pendingCommentImage
        if (editingImage != null) {
            dev.arisu.shrine.feature.editor.ImageEditorOverlay(
                sourcePath = editingImage.absolutePath,
                onCancel = {
                    editingImage.delete()
                    pendingCommentImage = null
                },
                onConfirm = { path ->
                    editingImage.delete()
                    pendingCommentImage = null
                    uploadCommentImage(java.io.File(path))
                },
            )
        }
    }
}

/* ══════════════════════════════════════════════════════════════════
 * 正文
 * ══════════════════════════════════════════════════════════════════ */

@Composable
private fun PostHead(
    post: Post,
    onOpenUser: (String) -> Unit,
    onImageClick: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        if (post.pinned || post.essence) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (post.pinned) {
                    SoftPill(
                        text = "置顶",
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        background = MaterialTheme.colorScheme.secondaryContainer,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                if (post.essence) {
                    SoftPill(
                        text = "精华",
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        background = MaterialTheme.colorScheme.tertiaryContainer,
                    )
                }
            }
            Spacer(Modifier.height(9.dp))
        }

        Text(
            text = post.title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )

        // 介绍语：列表里那张摘要卡看得到，进来反而没有——补在标题下、编号上
        if (post.intro.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = post.intro,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(6.dp))

        // 帖子编号独立成行，编号是本站的门牌
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "帖子编号",
                style = MaterialTheme.typography.labelMedium,
                color = InkFaint,
            )
            Spacer(Modifier.width(6.dp))
            CodeLabel(text = post.id)
            Spacer(Modifier.width(8.dp))
            Text(
                text = post.board.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(17.dp))

        // 作者信息与正文平铺，一条细线分割
        Row(verticalAlignment = Alignment.CenterVertically) {
            ArisuAvatar(
                seed = 0,
                name = post.authorName,
                size = 42.dp,
                role = post.authorRole,
                ring = true,
                url = post.authorAvatar,
                modifier = Modifier.clickable { onOpenUser(post.authorId) },
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = post.authorName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(6.dp))
                    RoleTag(role = post.authorRole)
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "发布于 ${post.createdAt}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        HairlineDivider()

        // 封面图铺在正文上方，点击放大（与正文图片同一套预览）
        post.coverUrl?.let { cover ->
            Spacer(Modifier.height(16.dp))
            coil3.compose.AsyncImage(
                model = cover,
                contentDescription = "帖子封面",
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(COVER_ASPECT)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onImageClick(cover) },
            )
        }

        Spacer(Modifier.height(16.dp))

        MarkdownText(
            markdown = post.body,
            onImageClick = onImageClick,
            onMentionClick = onOpenUser,
            mentionNames = post.mentionNames,
        )
    }
}

/* ══════════════════════════════════════════════════════════════════
 * 互动栏
 * ══════════════════════════════════════════════════════════════════ */

@Composable
private fun PostActionBar(
    post: Post,
    onOffer: () -> Unit,
    onUp: () -> Unit,
    onTrack: () -> Unit,
    onFavorite: () -> Unit,
    onComment: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        HairlineDivider()
        Row(
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActionCell(
                icon = Icons.Rounded.CurrencyYen,
                text = if (post.offerCount > 0) "奉纳 " + countCn(post.offerCount.toLong()) else "奉纳",
                active = post.offered,
                contentDescription = "奉纳",
                onClick = onOffer,
                modifier = Modifier.weight(1f),
            )
            ActionDivider()
            ActionCell(
                icon = Icons.Rounded.ArrowUpward,
                text = "顶 " + post.upCount,
                active = post.upvoted,
                contentDescription = "顶",
                onClick = onUp,
                modifier = Modifier.weight(1f),
            )
            ActionDivider()
            ActionCell(
                icon = Icons.Rounded.Notifications,
                text = "追踪",
                active = post.tracked,
                contentDescription = "追踪",
                onClick = onTrack,
                modifier = Modifier.weight(1f),
            )
            ActionDivider()
            ActionCell(
                icon = Icons.Rounded.Star,
                text = "收藏 " + post.saveCount,
                active = post.favorited,
                contentDescription = "收藏",
                onClick = onFavorite,
                modifier = Modifier.weight(1f),
            )
            ActionDivider()
            ActionCell(
                icon = Icons.Rounded.ChatBubbleOutline,
                text = "评论 " + post.commentCount,
                active = false,
                contentDescription = "评论",
                onClick = onComment,
                modifier = Modifier.weight(1f),
            )
        }
        HairlineDivider()
    }
}

@Composable
private fun ActionDivider() {
    Box(
        Modifier
            .fillMaxHeight()
            .padding(vertical = 13.dp)
            .width(1.dp)
            .background(NeuShadow.copy(alpha = 0.38f))
    )
}

@Composable
private fun ActionCell(
    icon: ImageVector,
    text: String,
    active: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = if (active) Vermilion else InkMuted
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(5.dp))
        // 长数字宁可缩字号也不折行不出点：BasicText 自动从 12sp 往下找放得下的字号
        androidx.compose.foundation.text.BasicText(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(color = tint),
            maxLines = 1,
            autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(
                minFontSize = 7.sp,
                maxFontSize = 12.sp,
            ),
        )
    }
}

/* ══════════════════════════════════════════════════════════════════
 * 评论
 * ══════════════════════════════════════════════════════════════════ */

// @User_编号 提及：可点跳到对应社员主页；其余 @词保持纯文本。
// 服务端只解析 @User_编号（昵称会重名），所以链接也只给这一形态
private val commentMentionRe = Regex("""@user_0*(\d{1,12})""", RegexOption.IGNORE_CASE)

private fun commentAnnotated(
    content: String,
    replyPrefix: String?,
    prefixStyle: SpanStyle,
    linkColor: Color,
    onOpenUser: (String) -> Unit,
    mentionNames: Map<String, String>? = null,
): AnnotatedString = buildAnnotatedString {
    if (replyPrefix != null) withStyle(prefixStyle) { append(replyPrefix) }
    var last = 0
    for (m in commentMentionRe.findAll(content)) {
        append(content.substring(last, m.range.first))
        // 编号归一成 User_000123 形态：手打的 @user_42 也能落到同一个人
        val no = "User_" + "%06d".format(m.groupValues[1].toLong())
        withLink(
            LinkAnnotation.Clickable(
                no,
                TextLinkStyles(SpanStyle(color = linkColor, fontWeight = FontWeight.SemiBold)),
            ) { onOpenUser(no) },
        ) {
            append(mentionNames?.get(no)?.let { "@$it" } ?: m.value)
        }
        last = m.range.last + 1
    }
    if (last < content.length) append(content.substring(last))
}

/** 续读位置的一条细线，停在上次最后读到的那条评论上方。 */
@Composable
private fun LastReadDivider() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
        )
        Text(
            text = "上次读到",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CommentItem(
    comment: Comment,
    showFollowingDivider: Boolean,
    showLastReadMarker: Boolean,
    canModerate: Boolean,
    onOpenUser: (String) -> Unit,
    onLike: () -> Unit,
    onReply: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onReport: () -> Unit,
    onOpenImage: (String) -> Unit,
) {
    val nested = comment.parentId != null
    val canDelete = comment.mine || canModerate
    var menu by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxWidth()) {
        Column {
        if (showLastReadMarker) {
            LastReadDivider()
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = if (nested) 32.dp else 0.dp)
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(
                    onClick = { },
                    onLongClick = { menu = true },
                )
                .padding(horizontal = 6.dp, vertical = 7.dp),
            verticalAlignment = Alignment.Top,
        ) {
            ArisuAvatar(
                seed = 0,
                name = comment.authorName,
                size = 30.dp,
                role = comment.authorRole,
                ring = true,
                url = comment.authorAvatar,
                modifier = Modifier.clickable { onOpenUser(comment.authorId) },
            )
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 昵称 + 徽标整体占一个弹性槽位，日期由槽位外的固定间隔顶到最右。
                    // 此前昵称自带 weight(fill=false)，名字短时右端空档无人吸收，
                    // 日期会随名字宽度左右漂移（用户实测：四行日期参差不齐）
                    Row(
                        Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = comment.authorName,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .clickable { onOpenUser(comment.authorId) },
                        )
                        Spacer(Modifier.width(5.dp))
                        RoleTag(role = comment.authorRole)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = comment.createdAt,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(4.dp))
                val mentionColor = MaterialTheme.colorScheme.primary
                Text(
                    text = commentAnnotated(
                        content = comment.content,
                        replyPrefix = if (nested) "回复 @${comment.replyToNickname ?: comment.authorName}：" else null,
                        prefixStyle = SpanStyle(
                            color = mentionColor,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = ReadingScale.sp(MaterialTheme.typography.labelMedium.fontSize),
                        ),
                        linkColor = mentionColor,
                        onOpenUser = onOpenUser,
                        mentionNames = comment.mentionNames,
                    ),
                    style = ReadingScale.of(
                        MaterialTheme.typography.bodyMedium,
                        balanced = comment.content.length <= 60,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )

                // 附图：拇指大的缩略图即可，点开仍可全屏看原图（大小关系参考 QQ）
                comment.imageUrl?.let { url ->
                    Spacer(Modifier.height(6.dp))
                    coil3.compose.AsyncImage(
                        // 列表里只显示 132dp 宽，拉原图纯属浪费：让服务端出 400px 缩略图
                        model = dev.arisu.shrine.core.net.thumbUrl(url, 400),
                        contentDescription = "评论附图",
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        modifier = Modifier
                            .widthIn(max = 132.dp)
                            .heightIn(max = 148.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onOpenImage(url) },
                    )
                }

                Spacer(Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onLike() }
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val likeTint = if (comment.liked) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                        Icon(
                            imageVector = Icons.Rounded.ThumbUp,
                            contentDescription = "点赞",
                            tint = likeTint,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        // 计数字槽定宽：「顶」与「顶 12」不再改变按钮宽度，
                        // 右边紧挨的「回复」因此不会随着赞数横移（与日期同一套对齐纪律）
                        Box(Modifier.widthIn(min = 34.dp)) {
                            Text(
                                text = if (comment.likeCount > 0) "顶 " + comment.likeCount else "顶",
                                style = MaterialTheme.typography.labelSmall,
                                color = likeTint,
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "回复",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onReply() }
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    // B 站式三点展开：常驻可见，不必长按摸索
                    IconButton(
                        onClick = { menu = true },
                        modifier = Modifier.size(26.dp),
                    ) {
                        Icon(
                            Icons.Rounded.MoreVert,
                            contentDescription = "更多操作",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
        // 回复与父消息之间不画线：只有下一条是新主消息时才分割
        if (showFollowingDivider) {
            HairlineDivider()
        }
        }

        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("复制") },
                leadingIcon = { MenuIcon(Icons.Rounded.ContentCopy) },
                onClick = {
                    menu = false
                    onCopy()
                },
            )
            DropdownMenuItem(
                text = { Text("举报") },
                leadingIcon = { MenuIcon(Icons.Rounded.Flag) },
                onClick = {
                    menu = false
                    onReport()
                },
            )
            if (canDelete) {
                DropdownMenuItem(
                    text = { Text("删除") },
                    leadingIcon = { MenuIcon(Icons.Rounded.Delete) },
                    onClick = {
                        menu = false
                        onDelete()
                    },
                )
            }
        }
    }
}

/** 悬浮在输入条上方的一行回复提示。 */
@Composable
private fun ReplyBanner(nickname: String, onCancel: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .padding(bottom = 8.dp)
            .softCard(shape = RoundedCornerShape(14.dp), elevation = 4.dp)
            .padding(start = 14.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "回复 @$nickname",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onCancel) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "取消回复",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** 悬浮的毛玻璃输入条：表情 · 输入框 · 附图 · 朱色圆形发送。 */
@Composable
private fun CommentInputBar(
    draft: TextFieldValue,
    draftImage: String?,
    uploading: Boolean,
    /** 0f–1f = 上传中；-1f = 空闲 */
    uploadProgress: Float,
    onDraftChange: (TextFieldValue) -> Unit,
    onEmoji: () -> Unit,
    onPickImage: (android.net.Uri) -> Unit,
    onRemoveImage: () -> Unit,
    onSend: () -> Unit,
) {
    val canSend = draft.text.isNotBlank() && !uploading
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) onPickImage(uri) }

    Column(Modifier.fillMaxWidth().imePadding()) {
        // 附图预览：发送前的落位与撤回，缩略图与评论区里的一致
        if (draftImage != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .padding(bottom = 8.dp)
                    .softCard(shape = RoundedCornerShape(14.dp), elevation = 4.dp)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                coil3.compose.AsyncImage(
                    model = draftImage,
                    contentDescription = "待发送的附图",
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 56.dp, height = 44.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
                Spacer(Modifier.width(10.dp))
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onRemoveImage) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "移除附图",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .padding(bottom = 12.dp)
                .glassPanel(
                    shape = RoundedCornerShape(26.dp),
                    elevation = 14.dp,
                    tint = NeuBaseHigh,
                    alpha = 0.92f,
                )
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            IconButton(onClick = onEmoji) {
                Icon(
                    imageVector = Icons.Rounded.EmojiEmotions,
                    contentDescription = "表情",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
            ArisuTextField(
                value = draft,
                onValueChange = onDraftChange,
                placeholder = "说点什么…",
                modifier = Modifier.weight(1f),
                containerColor = NeuInset,
                // 回车即换行，不再收起键盘或直接发送；条子随行数长高，到五行为止
                singleLine = false,
                maxLines = 5,
            )
            Spacer(Modifier.width(6.dp))
            IconButton(
                onClick = { launcher.launch(androidx.activity.result.PickVisualMediaRequest(
                    androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = !uploading,
            ) {
                if (uploading) {
                    androidx.compose.material3.CircularProgressIndicator(
                        progress = { uploadProgress.coerceAtLeast(0f) },
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.AddPhotoAlternate,
                        contentDescription = "添加图片",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(21.dp),
                    )
                }
            }
            Spacer(Modifier.width(2.dp))
            Box(
                Modifier
                    .size(40.dp)
                    .then(
                        if (canSend) Modifier.accentRaised(shape = CircleShape, color = dev.arisu.shrine.core.design.VermilionButton)
                        else Modifier.neuInset(shape = CircleShape, color = NeuInset)
                    )
                    .clickable(enabled = canSend) { onSend() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Send,
                    contentDescription = "发送",
                    tint = if (canSend) Color.White
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.width(2.dp))
        }
    }
}

/* ══════════════════════════════════════════════════════════════════
 * 小件
 * ══════════════════════════════════════════════════════════════════ */

@Composable
private fun MenuIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(18.dp),
    )
}

@Composable
private fun SheetButton(text: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 把相册图片上传到神社，返回可访问的 URL。 */

/** 只看「刚敲进去的一个字」：长度只多一、返回它落下的位置；粘贴/删除都不算 */
private fun insertedAt(old: String, new: String): Int? {
    if (new.length != old.length + 1) return null
    var i = 0
    while (i < old.length && old[i] == new[i]) i++
    return i
}