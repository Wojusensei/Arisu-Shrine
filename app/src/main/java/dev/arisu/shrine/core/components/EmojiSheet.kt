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

/** 表情面板的可选表情。帖子详情与绘马回复共用一份。 */
val EmojiPalette = listOf(
    "😀", "😄", "😅", "😂", "🙂", "😉", "😍", "🤔",
    "😴", "😭", "😡", "🥺", "👍", "👏", "🙏", "🎉",
    "🔥", "⭐", "🌸", "🍵", "🎴", "⛩️", "❄️", "🌙",
)

/** 面板里的三格：表情、颜文字与自留表情包 */
private enum class SheetTab(val label: String) {
    EMOJI("表情"),
    KAOMOJI("颜文字"),
    STICKER("表情包"),
}

/**
 * 底部表情面板（ModalBottomSheet）：点选即回调并自动收起。
 * 帖子详情的评论输入条与绘马回复输入条共用同一套交互；
 * 传了 [onSticker] 才出现「表情包」那一格。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmojiSheet(
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    onSticker: ((Sticker) -> Unit)? = null,
) {
    var tab by remember { mutableStateOf(SheetTab.EMOJI) }
    // 表情包是用户自己的东西，没有就不占一格；颜文字人人可用
    val tabs = remember(onSticker) {
        if (onSticker != null) {
            listOf(SheetTab.EMOJI, SheetTab.KAOMOJI, SheetTab.STICKER)
        } else {
            listOf(SheetTab.EMOJI, SheetTab.KAOMOJI)
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp)
        ) {
            UnderlineTabs(
                items = tabs,
                selected = tab,
                onSelect = { tab = it },
                label = { it.label },
            )
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

                SheetTab.KAOMOJI -> KaomojiPanel(
                    onPick = { text ->
                        onSelect(text)
                        onDismiss()
                    },
                )

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
