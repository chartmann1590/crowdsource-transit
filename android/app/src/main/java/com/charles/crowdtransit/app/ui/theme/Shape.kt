package com.charles.crowdtransit.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Sunny Transit shape scale: 14px for buttons/inputs, 20px for cards/sheets, pill for chips/badges.
val CrowdTransitShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(999.dp), // pill: chips, badges, search bar
    medium = RoundedCornerShape(14.dp), // buttons, inputs
    large = RoundedCornerShape(20.dp), // cards, sheets
    extraLarge = RoundedCornerShape(28.dp),
)

val PillShape = RoundedCornerShape(999.dp)
val CardShape = RoundedCornerShape(20.dp)
val ButtonShape = RoundedCornerShape(14.dp)
