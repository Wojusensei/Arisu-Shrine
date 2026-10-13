package dev.arisu.shrine.core.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.arisu.shrine.data.model.Sticker

val EmojiPalette = listOf(
    "😀", "😄", "😅", "😂", "🙂", "😉", "😍", "🤔",
    "😴", "😭", "😡", "🥺", "👍", "👏", "🙏", "🎉",
    "🔥", "⭐", "🌸", "🍵", "🎴", "⛩️", "❄️", "🌙",
)

private enum class SheetTab(val label: String) {
    EMOJI("表情"),
    STICKER("表情包"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmojiSheet(
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    onSticker: ((Sticker) -> Unit)? = null,
) {
    var tab by remember { mutableStateOf(SheetTab.EMOJI) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp)
        ) {
            if (onSticker != null) {
                UnderlineTabs(
                    items = SheetTab.entries,
                    selected = tab,
                    onSelect = { tab = it },
                    label = { it.label },
                )
            } else {
                Text(
                    text = "表情",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(12.dp))

            when (tab) {
                SheetTab.EMOJI -> EmojiPalette.chunked(8).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        row.forEach { emoji ->
                            Box(
                                Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        onSelect(emoji)
                                        onDismiss()
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(emoji, style = MaterialTheme.typography.titleLarge)
                            }
                        }
                        repeat(8 - row.size) { Spacer(Modifier.size(38.dp)) }
                    }
                    Spacer(Modifier.height(6.dp))
                }

                SheetTab.STICKER -> if (onSticker != null) {
                    StickerPanel(onPick = { sticker ->
                        onSticker(sticker)
                        onDismiss()
                    })
                }
            }
        }
    }
}
