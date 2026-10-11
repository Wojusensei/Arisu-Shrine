package dev.arisu.shrine.feature.shrine

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.arisu.shrine.core.components.ArisuTextField
import dev.arisu.shrine.core.components.ArisuTopBar
import dev.arisu.shrine.core.components.SectionCard
import dev.arisu.shrine.core.components.SectionLabel
import dev.arisu.shrine.core.components.TagChipSelectable
import dev.arisu.shrine.core.components.accentRaised
import dev.arisu.shrine.core.components.neuInset
import dev.arisu.shrine.core.components.softCard
import dev.arisu.shrine.core.design.NeuInset
import dev.arisu.shrine.data.remote.toApiMessage
import dev.arisu.shrine.data.repo.Shrine
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import dev.arisu.shrine.data.model.EmaTag

private data class MarkdownAction(
    val icon: ImageVector,
    val label: String,
    val format: String,
)

private val MarkdownActions = listOf(
    MarkdownAction(Icons.Rounded.FormatBold, "加粗", "**%s**"),
    MarkdownAction(Icons.Rounded.FormatItalic, "斜体", "*%s*"),
    MarkdownAction(Icons.Rounded.FormatSize, "标题", "## %s"),
    MarkdownAction(Icons.AutoMirrored.Rounded.FormatListBulleted, "列表", "- %s"),
    MarkdownAction(Icons.Rounded.Code, "代码块", "```\n%s\n```"),
    MarkdownAction(Icons.Rounded.Link, "链接", "[%s](https://)"),
)

@Composable
fun NewEmaScreen(
    onBack: () -> Unit,
    onCreated: (Int) -> Unit,
    modifier: Modifier = Modifier,
    editEma: dev.arisu.shrine.data.model.Ema? = null,
) {

    var title by rememberSaveable(editEma) { mutableStateOf(editEma?.title ?: "") }
    var body by rememberSaveable(editEma) { mutableStateOf(editEma?.body ?: "") }
    val tags = remember(editEma) { mutableStateListOf<EmaTag>().apply { editEma?.tags?.let { addAll(it) } } }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val canSubmit = title.isNotBlank() && body.isNotBlank() && !submitting

    fun wrap(action: MarkdownAction) {
        val marker = action.format
        body = if (body.isNotEmpty() && marker.contains("%s")) {
            body + "\n" + marker.replace("%s", "")
        } else {
            marker.replace("%s", "")
        }
    }

    Column(modifier.fillMaxSize().imePadding()) {
        ArisuTopBar(
            title = if (editEma != null) "编辑绘马" else "发绘马",
            subtitle = editEma?.let { "编辑 #${it.id}" } ?: "提交后自动分配编号",
            onBack = onBack,
            actions = {
                SubmitAction(enabled = canSubmit) {
                    if (!canSubmit) return@SubmitAction
                    submitting = true
                    error = null
                    scope.launch {
                        try {
                            val ema = if (editEma != null) {
                                Shrine.updateEma(editEma.id, title.trim(), body.trim(), tags.toList())
                            } else {
                                Shrine.createEma(title.trim(), body.trim(), tags.toList())
                            }
                            onCreated(ema.id)
                        } catch (e: Exception) {
                            error = e.toApiMessage()
                            submitting = false
                        }
                    }
                }
            },
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(6.dp))

            if (error != null) {
                Text(
                    text = error.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = dev.arisu.shrine.core.design.Vermilion,
                    modifier = Modifier.padding(start = 3.dp, bottom = 8.dp),
                )
            }

            SectionLabel(text = "标题与正文")
            SectionCard {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 17.dp, vertical = 16.dp),
                ) {
                    Text(
                        text = "标题",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    ArisuTextField(
                        value = title,
                        onValueChange = { if (it.length <= 60) title = it },
                        placeholder = "一句话说明您的建议或问题（最多 60 字）",
                    )

                    Spacer(Modifier.height(18.dp))

                    Text(
                        text = "正文",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(9.dp))
                    MarkdownToolbar(onAction = { wrap(it) })
                    Spacer(Modifier.height(10.dp))
                    BodyField(
                        value = body,

                        onValueChange = { if (it.length <= 16000) body = it },
                        placeholder = "支持 Markdown：标题、加粗、列表、代码块、图片与链接…",
                    )
                }
            }

            SectionLabel(text = "标签")
            SectionCard {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 17.dp, vertical = 16.dp),
                ) {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        EmaTag.entries.forEach { tag ->
                            val selected = tag in tags
                            TagChipSelectable(
                                tag = tag,
                                selected = selected,
                                onClick = { if (selected) tags.remove(tag) else tags.add(tag) },
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "可用标签：enhance（改进）/ bug（问题）/ questions（提问）/ tips（提示）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionLabel(text = "提交说明")
            SectionCard {
                Text(
                    text = "提交后将自动分配编号，如 #9",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 17.dp, vertical = 16.dp),
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SubmitAction(enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .then(
                if (enabled) {
                    Modifier.accentRaised(shape = RoundedCornerShape(12.dp))
                } else {
                    Modifier.neuInset(shape = RoundedCornerShape(12.dp), color = NeuInset)
                }
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 15.dp, vertical = 7.dp),
    ) {
        Text(
            text = "提交",
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) Color.White
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun MarkdownToolbar(onAction: (MarkdownAction) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MarkdownActions.forEach { action ->
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                Row(
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
                        ) { onAction(action) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = action.icon,
                        contentDescription = action.label,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = action.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun BodyField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 180.dp)
            .neuInset(shape = RoundedCornerShape(12.dp), color = NeuInset)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    )
                }
                inner()
            }
        },
    )
}
