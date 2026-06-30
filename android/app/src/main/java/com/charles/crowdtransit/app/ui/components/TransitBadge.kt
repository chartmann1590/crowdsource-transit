package com.charles.crowdtransit.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.charles.crowdtransit.app.R
import com.charles.crowdtransit.app.ui.theme.Primary
import com.charles.crowdtransit.app.ui.theme.TransitBus
import com.charles.crowdtransit.app.ui.theme.TransitFerry
import com.charles.crowdtransit.app.ui.theme.TransitSubway
import com.charles.crowdtransit.app.ui.theme.TransitTrain
import com.charles.crowdtransit.app.ui.theme.TransitTram

@Composable
fun TransitBadge(
    type: String,
    label: String = "",
    modifier: Modifier = Modifier,
) {
    val (bgColor, icon) = when (type) {
        "bus" -> TransitBus to R.drawable.ic_bus
        "train" -> TransitTrain to R.drawable.ic_train
        "subway" -> TransitSubway to R.drawable.ic_subway
        "ferry" -> TransitFerry to R.drawable.ic_ferry
        "tram" -> TransitTram to R.drawable.ic_tram
        else -> Primary to R.drawable.ic_transit
    }

    Row(
        modifier = modifier
            .background(bgColor, RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = type,
            tint = Color.White,
            modifier = Modifier.size(12.dp),
        )
        if (label.isNotEmpty()) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
