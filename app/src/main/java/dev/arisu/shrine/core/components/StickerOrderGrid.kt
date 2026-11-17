package dev.arisu.shrine.core.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.arisu.shrine.core.design.NeuInset
import dev.arisu.shrine.data.model.Sticker
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 一行四格（与面板同规）；格 64 + 间距 10 = 74dp 一格位 */
internal const val GRID_COLS = 4
internal val STICKER_CELL = 64.dp
internal val STICKER_GAP = 10.dp

/** 拖拽跨格的方向 */
internal enum class DragDir { Left, Right, Up, Down }

/** 视觉槽位：首格（「添加」）占 0 号位，表情从 1 号位起 */
internal fun visualSlot(index: Int): Int = index + 1

/**
 * 还能不能往这个方向再跨一格：左右不越行（换行靠上下拖），上下不出格，
 * 也不能挪进 0 号位——那儿是「添加」格，不属于表情。
 */
internal fun canStep(dir: DragDir, index: Int, count: Int): Boolean = when (dir) {
    DragDir.Right -> visualSlot(index) % GRID_COLS < GRID_COLS - 1 && index + 1 <= count - 1
    DragDir.Left -> visualSlot(index) % GRID_COLS > 0 && index - 1 >= 0
    DragDir.Down -> index + GRID_COLS <= count - 1
    DragDir.Up -> index - GRID_COLS >= 0
}

/** 槽位 → 格栅内左上角像素坐标 */
internal fun slotOffsetPx(slot: Int, pitchPx: Float): Offset =
    Offset((slot % GRID_COLS) * pitchPx, (slot / GRID_COLS) * pitchPx)

/**
 * 表情格栅：长按拖起 → 拖动中其余格子滑开让路 → 松手落位。
 *
 * 直接改写 [items] 的顺序，只当草稿：拖完不落库（用户收拾到一半退出不该生效），
 * 由调用方在「保存」时统一提交。[firstCell] 是 0 号槽位的固定格（「添加」），不参与排序。
 */
@Composable
fun StickerOrderGrid(
    items: SnapshotStateList<Sticker>,
    modifier: Modifier = Modifier,
    firstCell: @Composable () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val pitchPx = with(LocalDensity.current) { (STICKER_CELL + STICKER_GAP).toPx() }

    // 拖拽态：拖起的 id、它的下标、跟手余量（每跨一格就扣掉整步）、起拖格的槽位基准
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragIndex by remember { mutableStateOf(-1) }
    var residual by remember { mutableStateOf(Offset.Zero) }
    var liftBase by remember { mutableStateOf(Offset.Zero) }
    // 松手后还在滑回槽位的那一格：动画走完才算归位（缩放也跟着收回）
    var settleId by remember { mutableStateOf<String?>(null) }

    // 每格的位置动画，按 id 存：顺序一变，格子的树位置跟着变，状态不能丢
    val slides = remember { mutableMapOf<String, Animatable<Offset, AnimationVector2D>>() }
    val glide = remember {
        spring<Offset>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
            visibilityThreshold = Offset.VisibilityThreshold,
        )
    }

    fun slotTopLeft(index: Int): Offset = slotOffsetPx(visualSlot(index), pitchPx)

    /** 相邻换位。拖起的那格把基准挪到新槽，配合调用方对跟手余量的扣减，视觉上不跳 */
    fun swap(a: Int, b: Int) {
        if (a !in items.indices || b !in items.indices) return
        val tmp = items[a]
        items[a] = items[b]
        items[b] = tmp
        if (items[a].id == dragId) liftBase = slotTopLeft(a)
        if (items[b].id == dragId) liftBase = slotTopLeft(b)
    }

    /** 松手落位：从当前位置滑进槽位。取消手势也走这里，免得半路闪跳 */
    fun settleTo(id: String) {
        val index = items.indexOfFirst { it.id == id }
        val to = if (index >= 0) slotTopLeft(index) else liftBase
        val start = residual
        settleId = id
        scope.launch {
            // 落位结束后由位置动画接管渲染，先把终槽写上，交接不闪
            slides[id]?.snapTo(to)
            if (start != Offset.Zero) {
                // 临时位移量从松手点滑回零：这一格的落位动画
                Animatable(start, Offset.VectorConverter).animateTo(Offset.Zero, glide) {
                    if (settleId == id) residual = value
                }
            }
            if (settleId == id) {
                residual = Offset.Zero
                settleId = null
            }
        }
    }

    val rows = (items.size + 1 + GRID_COLS - 1) / GRID_COLS
    val gridWidth = STICKER_CELL * GRID_COLS + STICKER_GAP * (GRID_COLS - 1)
    val gridHeight = STICKER_CELL * rows + STICKER_GAP * (rows - 1)

    Box(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .width(gridWidth)
                .height(gridHeight),
        ) {
            firstCell()

            items.forEachIndexed { index, sticker ->
                key(sticker.id) {
                    val target = slotTopLeft(index)
                    val slide = slides.getOrPut(sticker.id) {
                        Animatable(target, Offset.VectorConverter)
                    }
                    // 槽位一变就滑过去；拖起的那格自己跟手，不参与
                    LaunchedEffect(target) {
                        if (sticker.id != dragId) slide.animateTo(target, glide)
                    }
                    val lifted = sticker.id == dragId || sticker.id == settleId
                    val liftScale by animateFloatAsState(
                        if (lifted) 1.08f else 1f,
                        label = "stickerLift",
                    )

                    Box(
                        Modifier
                            .zIndex(if (lifted) 1f else 0f)
                            .offset {
                                val p = if (lifted) liftBase + residual else slide.value
                                IntOffset(p.x.roundToInt(), p.y.roundToInt())
                            }
                            .size(STICKER_CELL)
                            .graphicsLayer {
                                scaleX = liftScale
                                scaleY = liftScale
                            }
                            .clip(RoundedCornerShape(12.dp))
                            .neuInset(shape = RoundedCornerShape(12.dp), color = NeuInset)
                            .pointerInput(sticker.id, items.size) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        val i = items.indexOfFirst { it.id == sticker.id }
                                        if (i < 0) return@detectDragGesturesAfterLongPress
                                        // 上一格还没落完位就被抓起来：这一轮接管
                                        settleId = null
                                        dragId = sticker.id
                                        dragIndex = i
                                        residual = Offset.Zero
                                        liftBase = slotTopLeft(i)
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        if (dragIndex !in items.indices) {
                                            return@detectDragGesturesAfterLongPress
                                        }
                                        residual += amount
                                        // 越过半格就与相邻格换位：其余格子滑开让路，拖起的那格始终跟手
                                        val half = pitchPx / 2f
                                        while (residual.x > half &&
                                            canStep(DragDir.Right, dragIndex, items.size)
                                        ) {
                                            swap(dragIndex, dragIndex + 1)
                                            dragIndex++
                                            residual -= Offset(pitchPx, 0f)
                                        }
                                        while (residual.x < -half &&
                                            canStep(DragDir.Left, dragIndex, items.size)
                                        ) {
                                            swap(dragIndex, dragIndex - 1)
                                            dragIndex--
                                            residual += Offset(pitchPx, 0f)
                                        }
                                        while (residual.y > half &&
                                            canStep(DragDir.Down, dragIndex, items.size)
                                        ) {
                                            swap(dragIndex, dragIndex + GRID_COLS)
                                            dragIndex += GRID_COLS
                                            residual -= Offset(0f, pitchPx)
                                        }
                                        while (residual.y < -half &&
                                            canStep(DragDir.Up, dragIndex, items.size)
                                        ) {
                                            swap(dragIndex, dragIndex - GRID_COLS)
                                            dragIndex -= GRID_COLS
                                            residual += Offset(0f, pitchPx)
                                        }
                                    },
                                    onDragEnd = {
                                        val id = dragId
                                        dragId = null
                                        dragIndex = -1
                                        if (id != null) settleTo(id)
                                    },
                                    onDragCancel = {
                                        val id = dragId
                                        dragId = null
                                        dragIndex = -1
                                        // 已经换过的位不回滚：那一版就是草稿顺序
                                        if (id != null) settleTo(id)
                                    },
                                )
                            }
                            .padding(4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        coil3.compose.AsyncImage(
                            model = sticker.url,
                            contentDescription = if (sticker.animated) "动图表情" else "表情",
                            contentScale = ContentScale.Fit,
                            // 管理界面一律圆角：方角缩略图跟这套拟物材质不搭
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                        )
                    }
                }
            }
        }
    }
}
