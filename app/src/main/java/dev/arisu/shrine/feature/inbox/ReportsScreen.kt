package dev.arisu.shrine.feature.inbox

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import dev.arisu.shrine.core.components.ShrineListEnd
import dev.arisu.shrine.core.components.ShrineRefreshingHint
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.arisu.shrine.core.components.ArisuAvatar
import dev.arisu.shrine.core.components.ArisuTextField
import dev.arisu.shrine.core.components.ArisuTopBar
import dev.arisu.shrine.core.components.HairlineDivider
import dev.arisu.shrine.core.components.NeuSurface
import dev.arisu.shrine.core.components.PrefetchOnApproach
import dev.arisu.shrine.core.components.RoleTag
import dev.arisu.shrine.core.components.ShrineEmptyState
import dev.arisu.shrine.core.components.ShrineLoadingState
import dev.arisu.shrine.core.components.ShrineNoticeBar
import dev.arisu.shrine.core.components.SoftPill
import dev.arisu.shrine.core.design.InkFaint
import dev.arisu.shrine.core.design.InkTitle
import dev.arisu.shrine.core.design.Slate
import dev.arisu.shrine.core.design.Vermilion
import dev.arisu.shrine.core.util.TimeFmt
import dev.arisu.shrine.data.model.roleFromWire
import dev.arisu.shrine.data.remote.Api
import dev.arisu.shrine.data.remote.ReportDto
import dev.arisu.shrine.data.remote.ResolveReq
import dev.arisu.shrine.data.remote.toApiMessage
import kotlinx.coroutines.launch

/**
 * 举报处理（奉仕+）：每条举报可见举报人、被举报对象、理由与时间。
 * 「前往查看」直达帖子/绘马；处理 / 驳回 的结果通过社务箱通知举报人。
 *
 * [focusReportId] 非空表示从某条举报通知点进来：队列里没有它，说明这条已被
 * 别的管理人员处理（多个管理人员会同时收到同一条举报），此时明确回一句
 * 「没有待处理的举报」，而不是让条目留在列表里被二次处置。
 */
@Composable
fun ReportsScreen(
    focusReportId: String,
    onBack: () -> Unit,
    onOpenPost: (String) -> Unit,
    onOpenEma: (Int) -> Unit,
    onOpenUser: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var reports by remember { mutableStateOf<List<ReportDto>?>(null) }
    var target by remember { mutableStateOf<ReportDto?>(null) }
    var approving by remember { mutableStateOf(true) }
    var comment by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var bar by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // 队列分页：服务端此前只回最新 50 条且无翻页参数，超出的待处理举报永远不可见
    var total by remember { mutableStateOf(0L) }
    var page by remember { mutableStateOf(1) }
    var endReached by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    // 翻页失败给「点击重试」页脚：哨兵是 LaunchedEffect(size)，失败后 size 不变不会自动再触发
    var loadMoreFailed by remember { mutableStateOf(false) }
    // 请求序号：load（刷新）会重置 page，序号递增后，在途的旧 loadMore 响应作废
    var loadSeq by remember { mutableStateOf(0) }
    // 首载失败与「没有待处理」分开：失败给重试入口，不把断网伪装成空队列
    var failed by remember { mutableStateOf(false) }
    // focus 提示只报一次：后续下拉刷新不再重复弹
    var focusChecked by remember { mutableStateOf(false) }

    fun load() {
        val seq = ++loadSeq
        scope.launch {
            try {
                val res = Api.service.reports("pending", 1, REPORTS_PAGE_SIZE)
                if (seq != loadSeq) return@launch
                reports = res.items
                total = res.total
                page = 1
                endReached = res.items.size >= res.total
                failed = false
                if (!focusChecked && focusReportId.isNotEmpty()) {
                    focusChecked = true
                    if (res.items.none { it.id.toString() == focusReportId }) {
                        bar = "没有待处理的举报"
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (seq != loadSeq) return@launch
                // 已有内容时保留旧列表，只有首载无数据才进入失败态
                if (reports == null) failed = true
            }
        }
    }

    fun loadMore() {
        val current = reports ?: return
        if (loadingMore || endReached || loadMoreFailed || current.isEmpty()) return
        val seq = loadSeq
        val snapPage = page
        loadingMore = true
        scope.launch {
            try {
                val res = Api.service.reports("pending", snapPage + 1, REPORTS_PAGE_SIZE)
                // 刷新（seq 变了）或 page 被重置后，旧的第 N+1 页不得再落地
                if (seq != loadSeq || page != snapPage) return@launch
                page += 1
                total = res.total
                endReached = (page - 1) * REPORTS_PAGE_SIZE + res.items.size >= res.total
                reports = (current + res.items).distinctBy { it.id }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                loadMoreFailed = true
            } finally {
                loadingMore = false
            }
        }
    }
    LaunchedEffect(Unit) { load() }

    /**
     * 重取第一页并落地，返回 id 是否仍在待处理队列里（null = 重取本身也失败）。
     * 处置撞车（举报已被他人处理）时用它区分「队列真的没了这条」与「网络/权限坏了」。
     */
    suspend fun refreshQueueContaining(id: Long): Boolean? = try {
        val res = Api.service.reports("pending", 1, REPORTS_PAGE_SIZE)
        loadSeq++
        reports = res.items
        total = res.total
        page = 1
        endReached = res.items.size >= res.total
        failed = false
        loadMoreFailed = false
        res.items.any { it.id == id }
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        null
    }

    Column(modifier.fillMaxSize()) {
        ArisuTopBar(title = "举报处理", subtitle = "奉仕及以上可见", onBack = onBack)

        ShrineNoticeBar(
            text = bar,
            onDismiss = { bar = null },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        val list = reports
        val reportListState = rememberLazyListState()
        PrefetchOnApproach(reportListState, list?.size ?: 0) { loadMore() }
        when {
            list == null && failed -> ShrineEmptyState(
                note = "加载失败，点击重试",
                onClick = { failed = false; load() },
            )
            list == null -> ShrineLoadingState()
            list.isEmpty() -> ShrineEmptyState(note = "没有待处理的举报")
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                state = reportListState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(list, key = { it.id }, contentType = { "report" }) { r ->
                    NeuSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "举报 #" + r.id,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = InkTitle,
                                )
                                Spacer(Modifier.weight(1f))
                                SoftPill(
                                    text = when (r.status) {
                                        "pending" -> "待处理"
                                        "resolved" -> "已处理"
                                        else -> "已驳回"
                                    },
                                    color = if (r.status == "pending") Vermilion else Slate,
                                    background = MaterialTheme.colorScheme.surfaceVariant,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ArisuAvatar(
                                    seed = 0,
                                    name = r.reporter.nickname,
                                    size = 22.dp,
                                    role = roleFromWire(r.reporter.role),
                                    ring = true,
                                    url = r.reporter.avatar.ifEmpty { null },
                                    modifier = Modifier.clickable { onOpenUser(r.reporter.no) },
                                )
                                Spacer(Modifier.width(7.dp))
                                Text(
                                    text = "举报人：" + r.reporter.nickname + " · " + r.reporter.no,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .weight(1f, fill = false)
                                        .clickable { onOpenUser(r.reporter.no) },
                                )
                                Spacer(Modifier.width(6.dp))
                                // 身份徽标随举报人展示：临时奉仕与社员、奉仕与宫司
                                // 在管理层眼里从此有区别（用户实测反馈）
                                RoleTag(role = roleFromWire(r.reporter.role))
                            }
                            Text(
                                text = "理由：" + r.reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = TimeFmt.epoch(r.createdAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = InkFaint,
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                when {
                                    r.postId != null -> Text(
                                        text = "前往查看",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .clickable {
                                                onOpenPost("arisu_" + r.postId.toString().padStart(6, '0'))
                                            }
                                            .padding(horizontal = 4.dp, vertical = 4.dp),
                                    )
                                    r.emaId != null -> Text(
                                        text = "前往绘马",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .clickable { onOpenEma(r.emaId.toInt()) }
                                            .padding(horizontal = 4.dp, vertical = 4.dp),
                                    )
                                }
                                if (r.status == "pending") {
                                    Text(
                                        text = "处理",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .clickable { target = r; approving = true; comment = "" }
                                            .padding(horizontal = 4.dp, vertical = 4.dp),
                                    )
                                    Text(
                                        text = "驳回",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Slate,
                                        modifier = Modifier
                                            .clickable { target = r; approving = false; comment = "" }
                                            .padding(horizontal = 4.dp, vertical = 4.dp),
                                    )
                                }
                            }
                        }
                    }
                    HairlineDivider()
                }
                item(key = "footer") {
                    when {
                        loadingMore -> ShrineRefreshingHint()
                        endReached -> ShrineListEnd(text = "已经到底了 · 共 $total 条")
                        loadMoreFailed -> Text(
                            text = "加载更多失败，点击重试",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable { loadMoreFailed = false; loadMore() }
                                .fillMaxWidth()
                                .padding(vertical = 28.dp),
                            textAlign = TextAlign.Center,
                        )
                        else -> ShrineListEnd(text = "上滑加载更多")
                    }
                }
            }
        }
    }

    target?.let { r ->
        AlertDialog(
            onDismissRequest = { target = null },
            title = { Text(if (approving) "处理举报" else "驳回举报") },
            text = {
                Column {
                    Text(
                        text = if (approving) "认定违规并处置（可在原帖菜单中删除内容）后，结果会通过社务箱通知举报人。附言选填。"
                        else "驳回后举报结案，结果会通知举报人。附言选填。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    ArisuTextField(
                        value = comment,
                        onValueChange = { if (it.length <= 100) comment = it },
                        placeholder = "附言（选填）",
                        // 回车即换行
                        singleLine = false,
                        minLines = 2,
                        maxLines = 4,
                    )
                }
            },
                    confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            try {
                                Api.service.resolveReport(r.id, ResolveReq(approving, false, comment.trim()))
                                bar = if (approving) "举报已处理" else "举报已驳回"
                                target = null
                                load()
                            } catch (e: Exception) {
                                if (e is kotlinx.coroutines.CancellationException) throw e
                                // 大概率是别的管理人员抢先处理了：等重取一次待处理队列再定调，
                                // 该条已不在队列 = 回「没有待处理的举报」；仍在 = 真·失败，透出原始报错
                                bar = if (refreshQueueContaining(r.id) == false) {
                                    "没有待处理的举报"
                                } else {
                                    e.toApiMessage()
                                }
                            }
                            busy = false
                        }
                    },
                ) { Text(if (approving) "确认处理" else "确认驳回") }
            },
            dismissButton = { TextButton(onClick = { target = null }) { Text("取消") } },
        )
    }
}

/** 举报队列每页条数 */
private const val REPORTS_PAGE_SIZE = 20
