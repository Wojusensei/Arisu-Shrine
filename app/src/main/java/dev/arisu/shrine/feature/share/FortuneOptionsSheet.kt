package dev.arisu.shrine.feature.share

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.arisu.shrine.core.components.ArisuPrimaryButton
import dev.arisu.shrine.core.components.SectionCard
import dev.arisu.shrine.core.components.SettingSwitchRow

/** 导出前的卡面内容选择；预览随风格与开关即时重画。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FortuneOptionsSheet(
    preview: Bitmap?,
    style: FortuneCardStyle,
    onStyleChange: (FortuneCardStyle) -> Unit,
    options: FortuneCardOptions,
    onChange: (FortuneCardOptions) -> Unit,
    confirmText: String,
    busy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 18.dp),
        ) {
            Text(
                text = "卡面内容",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(start = 4.dp),
            )
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val bitmap = preview
                if (bitmap != null && !bitmap.isRecycled) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .height(220.dp)
                            .aspectRatio(3f / 4f)
                            .clip(RoundedCornerShape(10.dp)),
                    )
                } else {
                    Box(
                        Modifier
                            .height(220.dp)
                            .aspectRatio(3f / 4f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "卡面风格",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FortuneCardStyle.entries.forEach { item ->
                    FilterChip(
                        selected = item == style,
                        onClick = { onStyleChange(item) },
                        label = { Text(item.label) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "显示内容",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
            )
            SectionCard {
                SettingSwitchRow(
                    title = "签文",
                    checked = options.showVerse,
                    onCheckedChange = { onChange(options.copy(showVerse = it)) },
                )
                SettingSwitchRow(
                    title = "宜忌",
                    checked = options.showYiJi,
                    onCheckedChange = { onChange(options.copy(showYiJi = it)) },
                )
                SettingSwitchRow(
                    title = "连续求签",
                    checked = options.showStreak,
                    onCheckedChange = { onChange(options.copy(showStreak = it)) },
                )
                SettingSwitchRow(
                    title = "二维码",
                    checked = options.showQr,
                    onCheckedChange = { onChange(options.copy(showQr = it)) },
                    showDivider = false,
                )
            }
            Spacer(Modifier.height(18.dp))
            ArisuPrimaryButton(
                text = confirmText,
                onClick = onConfirm,
                enabled = !busy,
                busy = busy,
            )
        }
    }
}
