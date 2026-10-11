package dev.arisu.shrine.feature.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.arisu.shrine.core.components.RoleTag
import dev.arisu.shrine.core.components.SoftPill
import dev.arisu.shrine.core.design.InkMuted
import dev.arisu.shrine.core.design.NeuInset
import dev.arisu.shrine.data.model.Role

@Composable
internal fun ProfileIdentity(nickname: String, role: Role, status: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = nickname,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(7.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (status.isNotBlank()) {
                SoftPill(text = status, color = InkMuted, background = NeuInset)
                Spacer(Modifier.width(6.dp))
            }
            RoleTag(role)
        }
    }
}
