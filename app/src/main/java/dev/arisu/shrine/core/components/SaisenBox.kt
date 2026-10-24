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
import androidx.compose.runtime.collectAsState
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

private sealed interface OfferPhase {
    data object Input : OfferPhase
    data object Busy : OfferPhase
    data class Stamped(val newTotal: Int) : OfferPhase
}

@Composable
private fun RoundSeal(ring: Float, modifier: Modifier = Modifier) {
    Canvas(modifier.size(66.dp)) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension / 2f

        if (ring > 0f && ring < 1f) {
            drawCircle(
                color = Vermilion.copy(alpha = 0.5f * (1f - ring)),
                radius = r * (1.15f + ring * 0.75f),
                center = c,
                style = Stroke(width = 2.dp.toPx() * (1f - ring * 0.5f)),
            )
        }

        drawCircle(
            color = VermilionDeep,
            radius = r * 0.94f,
            center = c,
            style = Stroke(width = r * 0.14f),
        )

        drawCircle(
            color = VermilionDeep.copy(alpha = 0.65f),
            radius = r * 0.70f,
            center = c,
            style = Stroke(width = r * 0.035f),
        )

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

@Composable
fun OfferSheet(
    post: Post,
    submitter: suspend (amount: Long) -> Int,
    onDone: (newTotal: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val me by Shrine.me.collectAsState()
    var text by remember { mutableStateOf("") }
    var phase by remember { mutableStateOf<OfferPhase>(OfferPhase.Input) }
    val amount = text.toLongOrNull() ?: 0L

    val tenths = me?.balanceTenths ?: 0L
    var errMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

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
        BackHandler(enabled = locked) {  }
        Column(
            Modifier
                .padding(horizontal = 34.dp)
                .fillMaxWidth()

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
                            if (!locked && amount >= 1 && amount * 10 <= tenths) {
                                m.clickableNoRipple {
                                    scope.launch {
                                        phase = OfferPhase.Busy
                                        try {
                                            val newTotal = submitter(amount)
                                            phase = OfferPhase.Stamped(newTotal)

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

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.then(
        Modifier.clickable(
            interactionSource = MutableInteractionSource(),
            indication = null,
            onClick = onClick,
        ),
    )
