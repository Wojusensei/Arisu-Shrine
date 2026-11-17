package dev.arisu.shrine.core.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.arisu.shrine.core.design.Gold
import dev.arisu.shrine.core.design.InkMuted
import dev.arisu.shrine.core.design.NeuBaseHigh
import dev.arisu.shrine.core.design.NeuShadow
import dev.arisu.shrine.core.design.SerifFamily
import dev.arisu.shrine.core.design.Vermilion
import dev.arisu.shrine.core.design.VermilionDeep
import dev.arisu.shrine.data.model.Post
import dev.arisu.shrine.data.model.yenText
import dev.arisu.shrine.data.remote.toApiMessage
import dev.arisu.shrine.data.repo.Shrine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/**
 * 奉纳弹窗（朱鸟居 + 圆印）：红色鸟居居中为记，奉纳成立时一枚
 * 斜置的圆形朱印盖章其上（正式印章形制：双环 + 衬线印文）。
 * 面板为真正的悬浮投影卡片——不用新拟物投影也不用细线描边。
 */

/** 奉纳弹窗状态：输入 / 提交中 / 印已落（不可关闭） */
private sealed interface OfferPhase {
    data object Input : OfferPhase
    data object Busy : OfferPhase
    data class Stamped(val newTotal: Int) : OfferPhase
}

/**
 * 圆形朱印：外粗环 + 内细环 + 衬线印文，整体斜置。
 * 盖章感来自印泥浓度与微斜，不靠材质仿真
 */
@Composable
private fun RoundSeal(ring: Float, modifier: Modifier = Modifier) {
    Canvas(modifier.size(66.dp)) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension / 2f
        // 印环（落章时向外扩散的一圈朱）
        if (ring > 0f && ring < 1f) {
            drawCircle(
                color = Vermilion.copy(alpha = 0.5f * (1f - ring)),
                radius = r * (1.15f + ring * 0.75f),
                center = c,
                style = Stroke(width = 2.dp.toPx() * (1f - ring * 0.5f)),
            )
        }
        // 外粗环
        drawCircle(
            color = VermilionDeep,
            radius = r * 0.94f,
            center = c,
            style = Stroke(width = r * 0.14f),
        )
        // 内细环
        drawCircle(
            color = VermilionDeep.copy(alpha = 0.65f),
            radius = r * 0.70f,
            center = c,
            style = Stroke(width = r * 0.035f),
        )
        // 环上四点定位（正式印章的排布感）
        repeat(4) { i ->
            val ang = (45f + i * 90f) * 3.14159f / 180f
            drawCircle(
                color = VermilionDeep.copy(alpha = 0.5f),
                radius = r * 0.035f,
                center = Offset(c.x + cos(ang) * r * 0.82f, c.y + sin(ang) * r * 0.82f),
            )
        }
    }
}

/**
 * 奉纳弹窗：submitter 携网络提交（抛错留在原地、错误交给 onError）；
 * 成功则圆印斜盖鸟居，播完 onDone 回传新总额、随后自行收场
 */
@Composable
fun OfferSheet(
    post: Post,
    submitter: suspend (amount: Long) -> Int,
    onDone: (newTotal: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val me by Shrine.me.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf("") }
    var phase by remember { mutableStateOf<OfferPhase>(OfferPhase.Input) }
    val amount = text.toLongOrNull() ?: 0L
    // 账面单位是 0.1 円；奉纳只花整数円（amount×10 <= 账面）
    val tenths = me?.balanceTenths ?: 0L
    var errMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    // 印章：入场缩放/旋转、印环扩散——只在变换与绘制层读值
    val sealScale = remember { Animatable(1f) }
    val sealRot = remember { Animatable(-7f) }
    val sealRing = remember { Animatable(1f) }
    val totalF = remember { Animatable(post.offerCount.toFloat()) }

    val locked = phase !is OfferPhase.Input
    val stamped = phase is OfferPhase.Stamped
    val shownTotal = totalF.value.toInt()
    var totalPop by remember { mutableStateOf(false) }
    val totalScale by animateFloatAsState(
        targetValue = if (totalPop) 1.16f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 760f),
        label = "totalPop",
    )

    Dialog(
        onDismissRequest = { if (!locked) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BackHandler(enabled = locked) { /* 印已落不许退 */ }
        Column(
            Modifier
                .padding(horizontal = 34.dp)
                .fillMaxWidth()
                // 真悬浮卡片：一处干净的大投影，替代新拟物与描边
                .shadow(
                    elevation = 18.dp,
                    shape = RoundedCornerShape(26.dp),
                    ambientColor = NeuShadow.copy(alpha = 0.35f),
                    spotColor = NeuShadow.copy(alpha = 0.55f),
                )
                .clip(RoundedCornerShape(26.dp))
                .background(dev.arisu.shrine.core.design.NeuBaseHigh)
                .padding(horizontal = 24.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ── 徽记：朱鸟居居中，圆形斜印常驻盖其右下 ────
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(118.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = ShrineIcons.Torii,
                    contentDescription = null,
                    tint = Vermilion,
                    modifier = Modifier
                        .offset(y = (-6).dp)
                        .size(width = 78.dp, height = 60.dp),
                )
                // 圆印：初始隐去，奉纳成立时端正地盖在鸟居正中、轻微倾斜
                if (stamped) {
                    Box(
                        Modifier.graphicsLayer {
                            scaleX = sealScale.value
                            scaleY = sealScale.value
                            rotationZ = sealRot.value
                        },
                    ) {
                        Text(
                            text = "奉",
                            style = MaterialTheme.typography.headlineMedium,
                            fontFamily = SerifFamily,
                            color = VermilionDeep,
                            modifier = Modifier.align(Alignment.Center),
                        )
                        RoundSeal(ring = sealRing.value, modifier = Modifier.align(Alignment.Center))
                    }
                }
            }

            Text(
                text = "已奉纳 " + shownTotal + " 円",
                style = MaterialTheme.typography.titleMedium,
                fontFamily = SerifFamily,
                color = Gold,
                modifier = Modifier.graphicsLayer {
                    scaleX = totalScale
                    scaleY = totalScale
                },
            )

            // ── 桥：细线 + 居中阴阳玉（印落即停） ──────────
            Spacer(Modifier.height(13.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(1.dp).background(NeuShadow.copy(alpha = 0.28f)))
                YinYangOrb(
                    size = 14.dp,
                    rotating = !stamped,
                    modifier = Modifier.padding(horizontal = 10.dp),
                )
                Box(Modifier.weight(1f).height(1.dp).background(NeuShadow.copy(alpha = 0.28f)))
            }
            Spacer(Modifier.height(13.dp))

            Text(
                text = "我的赛钱 " + tenths.yenText() + " 円 · 作者收其一成",
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
            )

            Spacer(Modifier.height(14.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 金额输入：平面细线框（弹窗内不再用凹槽新拟物）
                Box(
                    Modifier
                        .weight(1f)
                        .height(42.dp)
                        .border(1.dp, NeuShadow.copy(alpha = 0.40f), RoundedCornerShape(13.dp))
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = text,
                        onValueChange = { v -> text = v.filter { it.isDigit() }.take(6); errMsg = null },
                        singleLine = true,
                        enabled = !locked,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(Vermilion),
                        decorationBox = { inner ->
                            if (text.isEmpty()) {
                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "金额（円）",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = InkMuted.copy(alpha = 0.7f),
                                    )
                                }
                            }
                            inner()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                listOf(5L, 10L, 50L).forEach { n ->
                    val chipOn = !locked && tenths >= n * 10
                    Box(
                        Modifier
                            .size(42.dp)
                            .border(1.dp, NeuShadow.copy(alpha = 0.40f), CircleShape)
                            .let { m -> if (chipOn) m.clickableNoRipple { text = n.toString() } else m },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = n.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (chipOn) MaterialTheme.colorScheme.onSurface else InkMuted.copy(alpha = 0.6f),
                        )
                    }
                }
            }

            // 就地错误槽（固定占位，不顶动布局）
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(18.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = errMsg ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = Vermilion,
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(44.dp)
                        .let { m ->
                            // 余额不足也要能点：此前按钮直接不可点，用户只看到没反应。
                            // 先就地提示，真被服务端拒绝时下面的 catch 还有一层兜底
                            if (!locked && amount >= 1) {
                                m.clickableNoRipple {
                                    if (amount * 10 > tenths) {
                                        errMsg = "赛钱不足，请明日再来"
                                        return@clickableNoRipple
                                    }
                                    scope.launch {
                                        phase = OfferPhase.Busy
                                        try {
                                            val newTotal = submitter(amount)
                                            phase = OfferPhase.Stamped(newTotal)
                                            // 圆印砸落：大尺入场 → 弹性落定 -7°，印环扩散
                                            sealScale.snapTo(2.2f)
                                            sealRot.snapTo(6f)
                                            sealRing.snapTo(0f)
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            launch {
                                                sealScale.animateTo(1f, spring(dampingRatio = 0.34f, stiffness = 760f))
                                            }
                                            launch {
                                                sealRot.animateTo(-7f, spring(dampingRatio = 0.4f, stiffness = 420f))
                                            }
                                            launch { sealRing.animateTo(1f, tween(520)) }
                                            launch { totalF.animateTo(newTotal.toFloat(), tween(700)) }
                                            totalPop = true
                                            delay(190)
                                            totalPop = false
                                            delay(560)
                                            onDone(newTotal)
                                            onDismiss()
                                        } catch (e: Exception) {
                                            phase = OfferPhase.Input
                                            errMsg = (e as Throwable).toApiMessage()
                                        }
                                    }
                                }
                            } else {
                                m
                            }
                        }
                        .background(Vermilion, RoundedCornerShape(15.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = when (phase) {
                            OfferPhase.Busy -> "奉纳中"
                            is OfferPhase.Stamped -> "已奉纳"
                            OfferPhase.Input -> "奉纳"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                    )
                }
                Box(
                    Modifier
                        .size(width = 76.dp, height = 44.dp)
                        .let { m -> if (!locked) m.clickableNoRipple { onDismiss() } else m },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("取消", style = MaterialTheme.typography.labelLarge, color = InkMuted)
                }
            }
        }
    }
}

/** 无涟漪点击（弹窗里的轻量触点，涟漪会破坏面板的静） */
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.then(
        Modifier.clickable(
            interactionSource = MutableInteractionSource(),
            indication = null,
            onClick = onClick,
        ),
    )
