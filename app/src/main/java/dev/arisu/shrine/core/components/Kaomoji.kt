package dev.arisu.shrine.core.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val KaomojiItems = listOf(
    "（´・ω・`）",
    "(・ω・)",
    "(｡･ω･｡)",
    "(*´ω`*)",
    "( ˘ω˘ )",
    "(￣▽￣)",
    "(￣ー￣)",
    "(・∀・)",
    "(°∀°)",
    "(＾▽＾)",
    "(＾ω＾)",
    "(≧▽≦)",
    "(≧ω≦)",
    "(◕‿◕)",
    "(•‿•)",
    "(´▽`)",
    "ヽ(´▽`)/",
    "ヽ(•‿•)ノ",
    "ヽ(°〇°)ﾉ",
    "(｡･ω･)ﾉﾞ",
    "(￣▽￣)ノ",
    "(´・ω・)ﾉ",
    "(´・ω・`)ノ",
    "(ノ°∀°)ノ",
    "(ﾉ≧∀≦)ﾉ",
    "(*ﾉωﾉ)",
    "(〃ω〃)",
    "(´,,•ω•,,`)",
    "(๑´ω`๑)",
    "(๑•́ω•̀๑)",
    "(๑˃̵ᴗ˂̵)",
    "(๑•̀ㅂ•́)و",
    "(ง •̀_•́)ง",
    "(｀・ω・´)",
    "(｀・ω・´)ゞ",
    "(｡•́︿•̀｡)",
    "(｡•́ω•̀｡)",
    "(´；ω；`)",
    "(；ω；)",
    "(╥﹏╥)",
    "(ノ_<。)",
    "(´-﹏-`)",
    "(｡╯︵╰｡)",
    "(￣^￣)",
    "(￣ヘ￣)",
    "(¬‿¬)",
    "(・_・;)",
    "(・・;)",
    "(°ー°〃)",
    "(＠_＠)",
    "(°ロ°) !",
    "(¯口¯)",
    "(・へ・)",
    "(´⊙ω⊙`)",
    "(´-ω-`)",
    "(。・ω・。)",
)

@Composable
fun KaomojiPanel(
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = remember { KaomojiItems }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        items(items, key = { it }) { text ->
            Box(
                Modifier
                    .height(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onPick(text) }
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
