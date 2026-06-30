package com.charles.crowdtransit.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.charles.crowdtransit.app.navigation.CrowdTransitNavGraph
import com.charles.crowdtransit.app.ui.theme.CrowdTransitTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CrowdTransitTheme {
                CrowdTransitNavGraph()
            }
        }
    }
}
