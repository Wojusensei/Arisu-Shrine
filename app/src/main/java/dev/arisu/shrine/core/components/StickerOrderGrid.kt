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

internal const val GRID_COLS = 4
internal val STICKER_CELL = 64.dp
internal val STICKER_GAP = 10.dp

internal enum class DragDir { Left, Right, Up, Down }

internal fun visualSlot(index: Int): Int = index + 1

internal fun canStep(dir: DragDir, index: Int, count: Int): Boolean = when (dir) {
    DragDir.Right -> visualSlot(index) % GRID_COLS < GRID_COLS - 1 && index + 1 <= count - 1
    DragDir.Left -> visualSlot(index) % GRID_COLS > 0 && index - 1 >= 0
    DragDir.Down -> index + GRID_COLS <= count - 1
    DragDir.Up -> index - GRID_COLS >= 0
}

internal fun slotOffsetPx(slot: Int, pitchPx: Float): Offset =
    Offset((slot % GRID_COLS) * pitchPx, (slot / GRID_COLS) * pitchPx)

@Composable
fun StickerOrderGrid(
    items: SnapshotStateList<Sticker>,
    modifier: Modifier = Modifier,
    firstCell: @Composable () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val pitchPx = with(LocalDensity.current) { (STICKER_CELL + STICKER_GAP).toPx() }

    var dragId by remember { mutableStateOf<String?>(null) }
    var dragIndex by remember { mutableStateOf(-1) }
    var residual by remember { mutableStateOf(Offset.Zero) }
    var liftBase by remember { mutableStateOf(Offset.Zero) }

    var settleId by remember { mutableStateOf<String?>(null) }

    val slides = remember { mutableMapOf<String, Animatable<Offset, AnimationVector2D>>() }
    val glide = remember {
        spring<Offset>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
            visibilityThreshold = Offset.VisibilityThreshold,
        )
    }

    fun slotTopLeft(index: Int): Offset = slotOffsetPx(visualSlot(index), pitchPx)

    fun swap(a: Int, b: Int) {
        if (a !in items.indices || b !in items.indices) return
        val tmp = items[a]
        items[a] = items[b]
        items[b] = tmp
        if (items[a].id == dragId) liftBase = slotTopLeft(a)
        if (items[b].id == dragId) liftBase = slotTopLeft(b)
    }

    fun settleTo(id: String) {
        val index = items.indexOfFirst { it.id == id }
        val to = if (index >= 0) slotTopLeft(index) else liftBase
        val start = residual
        settleId = id
        scope.launch {

            slides[id]?.snapTo(to)
            if (start != Offset.Zero) {

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

                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                        )
                    }
                }
            }
        }
    }
}
