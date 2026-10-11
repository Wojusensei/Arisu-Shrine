package dev.arisu.shrine.feature.shrine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.arisu.shrine.core.components.ArisuCard
import dev.arisu.shrine.core.components.ArisuTopBar
import dev.arisu.shrine.core.components.NeuSurface
import dev.arisu.shrine.core.components.ShrineEmptyState
import dev.arisu.shrine.core.components.SoftPill
import dev.arisu.shrine.core.design.InkMuted
import dev.arisu.shrine.core.design.NeuInset
import dev.arisu.shrine.core.markdown.MarkdownText
import dev.arisu.shrine.data.repo.Shrine
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun ChangelogDetailScreen(
    version: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var releases by remember { mutableStateOf<List<dev.arisu.shrine.data.model.ReleaseNote>?>(null) }
    var failed by remember { mutableStateOf(false) }

    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(version, reloadKey) {
        try {
            releases = Shrine.releases()
            failed = false
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            failed = true
        }
    }

    val release = releases?.firstOrNull { it.version == version }

    Column(modifier.fillMaxSize()) {
        ArisuTopBar(
            title = release?.version ?: version,
            subtitle = "神社日志",
            onBack = onBack,
        )

        if (releases == null) {
            if (failed) {
                ShrineEmptyState(note = "加载失败，点击重试", onClick = { failed = false; reloadKey++ })
            } else {
                dev.arisu.shrine.core.components.ShrineLoadingState()
            }
            return@Column
        }
        if (release == null) {
            ShrineEmptyState(note = "没有这条日志", onClick = onBack)
            return@Column
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(14.dp))

            NeuSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                ) {
                    Text(
                        text = release.version,
                        style = MaterialTheme.typography.displaySmall.copy(fontSize = 36.sp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SoftPill(
                            text = release.channel.label,
                            color = InkMuted,
                            background = NeuInset,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "发布于 ${release.publishedAt}",
                            style = MaterialTheme.typography.labelSmall,
                            color = InkMuted,
                        )
                    }
                    if (release.kinds.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            release.kinds.forEach { kind ->
                                SoftPill(
                                    text = kind.label,
                                    color = InkMuted,
                                    background = NeuInset,
                                )
                            }
                        }
                    }
                    if (release.latest) {
                        Spacer(Modifier.height(14.dp))
                        SoftPill(
                            text = "当前最新版本",
                            color = MaterialTheme.colorScheme.primary,
                            background = NeuInset,
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            ArisuCard {
                MarkdownText(release.body)
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
