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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import dev.arisu.shrine.core.design.Vermilion
import dev.arisu.shrine.core.markdown.MarkdownText
import dev.arisu.shrine.data.model.Comment
import dev.arisu.shrine.data.model.Post
import dev.arisu.shrine.data.model.countCn
import dev.arisu.shrine.data.repo.Shrine
import dev.arisu.shrine.data.remote.toApiMessage
import kotlinx.serialization.json.Json
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.launch

private val replyJson = Json { ignoreUnknownKeys = true }
private val ReplySaver = Saver<Comment?, String>(
    save = { it?.let { c -> replyJson.encodeToString(Comment.serializer(), c) } },
    restore = { it?.let { s -> runCatching { replyJson.decodeFromString(Comment.serializer(), s) }.getOrNull() } },
)

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

    var post by remember { mutableStateOf(Shrine.peekPost(postId)) }
    var comments by remember { mutableStateOf(Shrine.peekComments(postId) ?: emptyList()) }

    val commentsShown = remember(comments) { comments.groupedByThread() }
    var loadState by remember { mutableStateOf(if (post == null) "loading" else "ok") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val me by Shrine.me.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    var menu by remember { mutableStateOf(false) }
    var deletingPost by remember { mutableStateOf(false) }
    var deletingComment by remember { mutableStateOf<Comment?>(null) }

    var toggling by remember { mutableStateOf(false) }

    var likedBusy by remember { mutableStateOf<Set<String>>(emptySet()) }
    var reporting by remember { mutableStateOf(false) }
    var reportTargetComment by remember { mutableStateOf<dev.arisu.shrine.data.model.Comment?>(null) }
    var showCollections by remember { mutableStateOf(false) }
    var sheetBusy by remember { mutableStateOf(false) }
    var collectionItems by remember { mutableStateOf<List<dev.arisu.shrine.data.model.Collection>>(emptyList()) }
    var activeCollectionId by remember { mutableStateOf<String?>(null) }
    var showEmoji by remember { mutableStateOf(false) }

    var offering by remember { mutableStateOf(false) }
    var previewUrl by remember { mutableStateOf<String?>(null) }

    var pendingCommentImage by remember { mutableStateOf<java.io.File?>(null) }
    var uploadProgress by remember { mutableStateOf(-1f) }

    var collectedStickers by remember { mutableStateOf<Set<String>>(emptySet()) }

    var draft by rememberSaveable { mutableStateOf("") }
    var replyTo by rememberSaveable(stateSaver = ReplySaver) { mutableStateOf<Comment?>(null) }

    var draftImage by rememberSaveable { mutableStateOf<String?>(null) }
    var uploadingImage by remember { mutableStateOf(false) }
    var newCollection by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var showMention by remember { mutableStateOf(false) }

    suspend fun refresh() {
        try {
            val p = Shrine.post(postId)
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

    LaunchedEffect(postId) { refresh() }

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

    val currentUser = me
    val isAuthor = post != null && currentUser != null && post!!.authorId == currentUser.id
    val canDeletePost = isAuthor || (currentUser?.role?.canModerate ?: false)

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

                        if ((currentUser?.role?.rank ?: 0) >= 4) {
                            DropdownMenuItem(
                                text = { Text(if (post?.essence == true) "取消精华" else "设为精华") },
                                leadingIcon = { MenuIcon(Icons.Rounded.Star) },
                                onClick = {
                                    menu = false

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

                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 18.dp,

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

                                    if (!toggling) {
                                        toggling = true
                                        post = p.copy(upvoted = !p.upvoted, upCount = p.upCount + if (p.upvoted) -1 else 1)
                                        scope.launch {
                                            try {
                                                val (upped, count) = Shrine.toggleUp(postId)
                                                post = post?.copy(upvoted = upped, upCount = count)
                                            } catch (e: Exception) {
                                                errorMsg = e.toApiMessage()

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
                            itemsIndexed(commentsShown, key = { _, c -> c.id }, contentType = { _, _ -> "comment" }) { index, comment ->

                                val dividerAfter = index == commentsShown.lastIndex ||
                                    commentsShown[index + 1].parentId == null
                                CommentItem(
                                    comment = comment,
                                    showFollowingDivider = dividerAfter,
                                    canModerate = currentUser?.role?.canModerate ?: false,
                                    onOpenUser = onOpenUser,
                                    onLike = label@{
                                        if (comment.id in likedBusy) return@label
                                        likedBusy = likedBusy + comment.id
                                        scope.launch {

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

                    ScrollTopOrb(
                        listState = listState,
                        onClick = { scope.launch { listState.smoothScrollToTop() } },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 92.dp),
                    )

                    Column(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(),
                    ) {
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

                                if (new.length == draft.length + 1 && new.endsWith("@")) showMention = true

                                if (new.length <= 2000) draft = new
                            },
                            onEmoji = { showEmoji = true },
                            onPickImage = pickImage@{ uri ->
                                if (uploadingImage) return@pickImage

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
                                val text = draft.trim()
                                if (text.isNotEmpty() && !sending && !uploadingImage) {
                                    sending = true
                                    scope.launch {
                                        try {
                                            val created = Shrine.addComment(
                                                postId, text,
                                                parentId = replyTo?.id,
                                                replyToUserId = replyTo?.authorId,
                                                imageUrl = draftImage?.takeIf { it.isNotBlank() },
                                            )

                                            draft = ""
                                            draftImage = null
                                            replyTo = null
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

        if (offering) {
            post?.let { cur ->
                OfferSheet(
                    post = cur,
                    submitter = { amount ->
                        val (_, total) = Shrine.offerPost(postId, amount)
                        total
                    },
                    onDone = { newTotal -> post = post?.copy(offerCount = newTotal) },
                    onDismiss = { offering = false },
                )
            }
        }

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

        deletingComment?.let { target ->
            if (target.mine) {
                ArisuConfirmDialog(
                    title = "删除这条评论？",
                    body = "删除后不可恢复。",
                    confirmText = "删除",
                    onConfirm = {
                        Shrine.deleteComment(target.id)
                        comments = comments.filterNot { it.id == target.id }
                        post = post?.copy(commentCount = (post?.commentCount ?: 1) - 1)
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
                        comments = comments.filterNot { it.id == target.id }
                        post = post?.copy(commentCount = (post?.commentCount ?: 1) - 1)
                        deletingComment = null
                    },
                    onDismiss = { deletingComment = null },
                )
            }
        }

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

                    var selectedCollectionId by remember { mutableStateOf<String?>(activeCollectionId) }

                    collectionItems.forEach { collection ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {

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
                                    val favored = Shrine.setFavorite(postId, selectedCollectionId?.toLongOrNull())

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

        if (showEmoji) {
            EmojiSheet(
                onSelect = { draft += it },

                onSticker = { sticker -> draftImage = sticker.url },
                onDismiss = { showEmoji = false },
            )
        }

        if (showMention) {
            MentionPickerSheet(
                visible = true,
                onDismiss = { showMention = false },
                onPick = { u ->
                    draft = draft.dropLast(1) + "@" + u.id + " "
                    showMention = false
                },
            )
        }

        val previewing = previewUrl
        if (previewing != null) {
            ImageViewerDialog(
                url = previewing,
                addedToStickers = previewing in collectedStickers,
                onAddSticker = { imageUrl ->

                    Shrine.addSticker(imageUrl)
                    collectedStickers = collectedStickers + imageUrl
                },
                onDismiss = { previewUrl = null },
            )
        }

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

        if (post.intro.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = post.intro,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(6.dp))

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
                active = post.offerCount > 0,
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CommentItem(
    comment: Comment,
    showFollowingDivider: Boolean,
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
                            fontSize = MaterialTheme.typography.labelMedium.fontSize,
                        ),
                        linkColor = mentionColor,
                        onOpenUser = onOpenUser,
                        mentionNames = comment.mentionNames,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                comment.imageUrl?.let { url ->
                    Spacer(Modifier.height(6.dp))
                    coil3.compose.AsyncImage(

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

@Composable
private fun CommentInputBar(
    draft: String,
    draftImage: String?,
    uploading: Boolean,

    uploadProgress: Float,
    onDraftChange: (String) -> Unit,
    onEmoji: () -> Unit,
    onPickImage: (android.net.Uri) -> Unit,
    onRemoveImage: () -> Unit,
    onSend: () -> Unit,
) {
    val canSend = draft.isNotBlank() && !uploading
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) onPickImage(uri) }

    Column(Modifier.fillMaxWidth().imePadding()) {

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
