package biz.pixelperfectstudios.personaspeak.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import biz.pixelperfectstudios.personaspeak.app.ui.theme.OnPrimary
import biz.pixelperfectstudios.personaspeak.app.ui.theme.OnPrimaryContainer
import biz.pixelperfectstudios.personaspeak.app.ui.theme.OnSurfaceVariant
import biz.pixelperfectstudios.personaspeak.app.ui.theme.OutlineVariant
import biz.pixelperfectstudios.personaspeak.app.ui.theme.PillShape
import biz.pixelperfectstudios.personaspeak.app.ui.theme.Primary
import biz.pixelperfectstudios.personaspeak.app.ui.theme.PrimaryContainer
import biz.pixelperfectstudios.personaspeak.app.ui.theme.SecondaryContainer
import biz.pixelperfectstudios.personaspeak.app.ui.theme.SurfaceVariant

/**
 * The teal→cyan gradient that marks every primary call-to-action across
 * onboarding. One brush so the brand line stays a single, identifiable shape.
 */
private val CtaGradient = Brush.linearGradient(listOf(Primary, SecondaryContainer))

/**
 * Full-width teal-gradient call-to-action used by every onboarding screen.
 *
 * Built as a hand-rolled Box rather than a Material3 [androidx.compose.material3.Button]
 * because the design brief specifies a gradient container, which Button's
 * color slots don't accommodate cleanly. Min height honours 48dp touch target.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val brush = if (enabled) CtaGradient else Brush.linearGradient(listOf(SurfaceVariant, SurfaceVariant))
    Row(
        modifier = modifier
            .heightIn(min = 52.dp)
            .clip(MaterialTheme.shapes.small)
            .background(brush)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) OnPrimary else OnSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * Low-emphasis text link used for "skip" paths through onboarding.
 * Color shifts to [Primary] when pressed/active to signal it's tappable.
 */
@Composable
fun SecondaryTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = OnSurfaceVariant,
        fontWeight = FontWeight.Medium,
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

/**
 * Pill-shaped badge for small tags: reassurance chips on Welcome
 * ("Autocorrect"), plan tags on AI Selection ("Free" / "Soon").
 */
@Composable
fun PillBadge(
    text: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    containerColor: Color = PrimaryContainer,
    contentColor: Color = OnPrimaryContainer,
) {
    Row(
        modifier = modifier
            .clip(PillShape)
            .background(containerColor)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * Brand app bar: robot mark + "PersonaSpeak" wordmark in primary teal.
 * Appears on Setup and ApiKey. Drawn as a plain Row so screens own their
 * window insets; no M3 TopAppBar so the back-button contract stays explicit
 * per screen.
 */
@Composable
fun PersonaSpeakTopAppBar(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "🤖",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = "PersonaSpeak",
            style = MaterialTheme.typography.headlineMedium,
            color = Primary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
