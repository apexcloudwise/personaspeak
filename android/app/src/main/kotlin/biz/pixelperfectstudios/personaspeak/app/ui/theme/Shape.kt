package biz.pixelperfectstudios.personaspeak.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// DESIGN.md radii. M3 components draw from these slots: buttons pull `small`,
// cards pull `medium`/`large`. The keycap radius is a :keyboard concern and is
// exposed here only as shared reference.

val PersonaSpeakShapes = Shapes(
    small = RoundedCornerShape(12.dp),      // buttons, action items
    medium = RoundedCornerShape(16.dp),     // cards, result containers
    large = RoundedCornerShape(16.dp),      // large cards
    extraLarge = RoundedCornerShape(28.dp), // sheets, dialogs
)

// Chips and selectors are always pill-shaped to distinguish them from buttons.
val PillShape = RoundedCornerShape(percent = 50)

// Standard alphanumeric keycaps — utilitarian 4dp.
val KeyShape = RoundedCornerShape(4.dp)
