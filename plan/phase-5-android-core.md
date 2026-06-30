# Phase 5: Android App — Core

> **Goal:** Build the core Android app with authentication, MapLibre map, nearby stops display, stop detail, and real-time Firebase data. This phase produces a fully functional app that lets users find transit stops near them.

---

## Overview

**Stack:** Kotlin + Jetpack Compose + Hilt + Firebase KTX + MapLibre Native Android + GeoFire

**Architecture:** MVVM
- `ui/` — Compose screens and components
- `viewmodel/` — ViewModels that hold UI state
- `data/` — Repositories that read/write Firebase
- `model/` — Data classes (from Phase 3)
- `di/` — Hilt modules

**Navigation:** Compose Navigation with typed routes

---

## Project Structure

```
android/app/src/main/java/com/crowdtransit/app/
├── CrowdTransitApp.kt              # Application class (@HiltAndroidApp)
├── MainActivity.kt                 # Single activity
├── navigation/
│   ├── NavGraph.kt                 # Compose nav graph
│   └── Screen.kt                   # Sealed class of routes
├── ui/
│   ├── theme/
│   │   ├── Color.kt                # Design tokens from DESIGN.md
│   │   ├── Type.kt                 # Typography
│   │   ├── Shape.kt                # Border radii
│   │   └── Theme.kt                # MaterialTheme setup
│   ├── components/
│   │   ├── StopCard.kt             # Reusable stop card
│   │   ├── TransitBadge.kt         # Bus/Train/etc colored pill
│   │   ├── StarRating.kt           # 1-5 star display/input
│   │   ├── ReviewCard.kt           # User review display
│   │   ├── SearchBar.kt            # Top search input
│   │   ├── MapLibreView.kt         # MapLibre Compose wrapper
│   │   └── BottomSheetHandle.kt    # Drag handle for bottom sheets
│   ├── screens/
│   │   ├── map/
│   │   │   ├── MapHomeScreen.kt    # Main map screen
│   │   │   └── MapHomeViewModel.kt
│   │   ├── stop/
│   │   │   ├── StopDetailScreen.kt
│   │   │   └── StopDetailViewModel.kt
│   │   ├── route/
│   │   │   ├── RouteDetailScreen.kt
│   │   │   └── RouteDetailViewModel.kt
│   │   ├── search/
│   │   │   ├── SearchScreen.kt
│   │   │   └── SearchViewModel.kt
│   │   ├── auth/
│   │   │   ├── OnboardingScreen.kt
│   │   │   └── AuthViewModel.kt
│   │   └── profile/
│   │       ├── ProfileScreen.kt
│   │       └── ProfileViewModel.kt
├── data/
│   ├── repository/
│   │   ├── StopRepository.kt
│   │   ├── RouteRepository.kt
│   │   ├── AgencyRepository.kt
│   │   └── AuthRepository.kt
│   └── firebase/
│       ├── FirebaseExtensions.kt   # Flow wrappers for RTDB
│       └── GeoFireHelper.kt        # Radius query helper
├── model/
│   └── TransitModels.kt            # (from Phase 3)
└── di/
    ├── AppModule.kt
    └── FirebaseModule.kt
```

---

## Step 1: Application Class & Hilt Setup

Create `android/app/src/main/java/com/crowdtransit/app/CrowdTransitApp.kt`:

```kotlin
package com.crowdtransit.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CrowdTransitApp : Application()
```

Update `AndroidManifest.xml`:
```xml
<application
    android:name=".CrowdTransitApp"
    android:label="CrowdTransit"
    android:theme="@style/Theme.CrowdTransit"
    ... >
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
```

---

## Step 2: Hilt Modules

Create `android/app/src/main/java/com/crowdtransit/app/di/FirebaseModule.kt`:

```kotlin
package com.crowdtransit.app.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {
    
    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = Firebase.auth
    
    @Provides
    @Singleton
    fun provideFirebaseDatabase(): FirebaseDatabase {
        val db = Firebase.database
        db.setPersistenceEnabled(true) // Offline cache
        return db
    }
}
```

---

## Step 3: Firebase Extensions (Flow Wrappers)

Create `android/app/src/main/java/com/crowdtransit/app/data/firebase/FirebaseExtensions.kt`:

```kotlin
package com.crowdtransit.app.data.firebase

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Observe a Firebase RTDB reference as a Flow.
 * Emits on every value change. Closes when the Flow is cancelled.
 */
fun DatabaseReference.observeAsFlow(): Flow<DataSnapshot?> = callbackFlow {
    val listener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            trySend(snapshot)
        }
        override fun onCancelled(error: DatabaseError) {
            close(error.toException())
        }
    }
    addValueEventListener(listener)
    awaitClose { removeEventListener(listener) }
}

/**
 * Read a Firebase RTDB reference once as a suspend function.
 */
suspend fun DatabaseReference.getOnce(): DataSnapshot {
    return kotlinx.coroutines.tasks.await(this.get())
}
```

---

## Step 4: Theme (from DESIGN.md)

Create `android/app/src/main/java/com/crowdtransit/app/ui/theme/Color.kt`:

```kotlin
package com.crowdtransit.app.ui.theme

import androidx.compose.ui.graphics.Color

// Primary
val Primary = Color(0xFF1565C0)
val PrimaryLight = Color(0xFF1E88E5)
val PrimaryDark = Color(0xFF0D47A1)

// Secondary (accent)
val Secondary = Color(0xFFFF6F00)
val SecondaryLight = Color(0xFFFFA000)
val SecondaryDark = Color(0xFFE65100)

// Surfaces (dark theme)
val SurfaceDark = Color(0xFF0F1724)
val Surface = Color(0xFF1A2332)
val SurfaceElevated = Color(0xFF243044)
val SurfaceCard = Color(0xFF2A3650)

// On surfaces
val OnSurface = Color(0xFFE8EAED)
val OnSurfaceSecondary = Color(0xFF9AA0A6)
val OnPrimary = Color(0xFFFFFFFF)
val OnSecondary = Color(0xFF000000)

// Status
val Success = Color(0xFF00C853)
val Warning = Color(0xFFFFB300)
val Error = Color(0xFFF44336)

// Transit types
val RatingGold = Color(0xFFFFC107)
val TransitBus = Color(0xFF4CAF50)
val TransitTrain = Color(0xFF2196F3)
val TransitSubway = Color(0xFF9C27B0)
val TransitFerry = Color(0xFF00BCD4)
val TransitTram = Color(0xFFFF9800)
```

Create `android/app/src/main/java/com/crowdtransit/app/ui/theme/Theme.kt`:

```kotlin
package com.crowdtransit.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    secondary = Secondary,
    onSecondary = OnSecondary,
    background = SurfaceDark,
    surface = Surface,
    onSurface = OnSurface,
    onSurfaceVariant = OnSurfaceSecondary,
    error = Error,
    tertiary = Success,
)

@Composable
fun CrowdTransitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = CrowdTransitTypography,
        shapes = CrowdTransitShapes,
        content = content,
    )
}
```

---

## Step 5: Core Components

Create `android/app/src/main/java/com/crowdtransit/app/ui/components/TransitBadge.kt`:

```kotlin
package com.crowdtransit.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crowdtransit.app.R
import com.crowdtransit.app.ui.theme.*

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
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            )
        }
    }
}
```

Create `android/app/src/main/java/com/crowdtransit/app/ui/components/StarRating.kt`:

```kotlin
package com.crowdtransit.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.crowdtransit.app.ui.theme.OnSurfaceSecondary
import com.crowdtransit.app.ui.theme.RatingGold

@Composable
fun StarRating(
    rating: Float,
    maxStars: Int = 5,
    starSize: Dp = 20.dp,
    interactive: Boolean = false,
    onRatingChange: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier) {
        for (i in 1..maxStars) {
            val filled = i <= rating
            Icon(
                imageVector = if (filled) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = "$i star",
                tint = if (filled) RatingGold else OnSurfaceSecondary,
                modifier = Modifier
                    .size(starSize)
                    .then(
                        if (interactive && onRatingChange != null) {
                            Modifier.clickable { onRatingChange(i) }
                        } else Modifier
                    ),
            )
        }
    }
}
```

Create `android/app/src/main/java/com/crowdtransit/app/ui/components/StopCard.kt`:

```kotlin
package com.crowdtransit.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.crowdtransit.app.model.Stop
import com.crowdtransit.app.ui.theme.*

@Composable
fun StopCard(
    stop: Stop,
    distanceMeters: Float? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(SurfaceCard, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stop.name,
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    color = OnSurface,
                    maxLines = 2,
                )
                Text(
                    text = stop.city,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceSecondary,
                )
            }
            if (distanceMeters != null) {
                val distText = if (distanceMeters < 1000) {
                    "${distanceMeters.toInt()}m"
                } else {
                    "${"%.1f".format(distanceMeters / 1000)}km"
                }
                Text(
                    text = distText,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    color = Primary,
                )
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(stop.transitTypes) { type ->
                TransitBadge(type = type)
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            StarRating(
                rating = stop.averageRating,
                starSize = 14.dp,
            )
            Text(
                text = "(${stop.ratingCount})",
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                color = OnSurfaceSecondary,
            )
        }
    }
}
```

---

## Step 6: Stop Repository

Create `android/app/src/main/java/com/crowdtransit/app/data/repository/StopRepository.kt`:

```kotlin
package com.crowdtransit.app.data.repository

import com.crowdtransit.app.data.firebase.GeoFireHelper
import com.crowdtransit.app.data.firebase.observeAsFlow
import com.crowdtransit.app.model.Stop
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.getValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StopRepository @Inject constructor(
    private val db: FirebaseDatabase,
    private val geoFireHelper: GeoFireHelper,
) {
    
    /**
     * Observe a single stop by ID as a Flow.
     */
    fun observeStop(stopId: String): Flow<Stop?> =
        db.reference.child("stops/$stopId")
            .observeAsFlow()
            .map { it?.getValue<Stop>() }
    
    /**
     * Get stops within radius of a location.
     * @param lat Latitude
     * @param lng Longitude
     * @param radiusKm Search radius in kilometers
     */
    suspend fun getStopsNearby(lat: Double, lng: Double, radiusKm: Double): List<Stop> {
        val nearbyIds = geoFireHelper.queryRadius(lat, lng, radiusKm)
        return nearbyIds.mapNotNull { stopId ->
            db.reference.child("stops/$stopId").get().await().getValue<Stop>()
        }
    }
    
    /**
     * Get stop by ID once (not reactive).
     */
    suspend fun getStop(stopId: String): Stop? =
        db.reference.child("stops/$stopId")
            .get().await()
            .getValue<Stop>()
    
    /**
     * Get multiple stops by IDs.
     */
    suspend fun getStopsByIds(ids: List<String>): List<Stop> =
        ids.mapNotNull { getStop(it) }
}
```

---

## Step 7: Auth Repository

Create `android/app/src/main/java/com/crowdtransit/app/data/repository/AuthRepository.kt`:

```kotlin
package com.crowdtransit.app.data.repository

import com.crowdtransit.app.model.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseDatabase,
) {
    
    val currentUser: FirebaseUser? get() = auth.currentUser
    
    /**
     * Observe auth state as a Flow.
     */
    val authState: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }
    
    /**
     * Sign in anonymously (called on first launch if no account).
     */
    suspend fun signInAnonymously(): FirebaseUser {
        val result = auth.signInAnonymously().await()
        val user = result.user!!
        ensureUserProfile(user)
        return user
    }
    
    /**
     * Sign in with Google credential (from GoogleSignIn intent result).
     */
    suspend fun signInWithGoogle(idToken: String): FirebaseUser {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        
        return if (auth.currentUser?.isAnonymous == true) {
            // Upgrade anonymous account to Google
            val result = auth.currentUser!!.linkWithCredential(credential).await()
            val user = result.user!!
            ensureUserProfile(user)
            user
        } else {
            val result = auth.signInWithCredential(credential).await()
            val user = result.user!!
            ensureUserProfile(user)
            user
        }
    }
    
    /**
     * Sign in with email and password.
     */
    suspend fun signInWithEmail(email: String, password: String): FirebaseUser {
        val result = auth.signInWithEmailAndPassword(email, password).await()
        return result.user!!
    }
    
    /**
     * Create account with email and password.
     */
    suspend fun createAccount(email: String, password: String, displayName: String): FirebaseUser {
        val result = if (auth.currentUser?.isAnonymous == true) {
            val credential = com.google.firebase.auth.EmailAuthProvider.getCredential(email, password)
            auth.currentUser!!.linkWithCredential(credential).await()
        } else {
            auth.createUserWithEmailAndPassword(email, password).await()
        }
        val user = result.user!!
        
        // Update display name
        val profileUpdate = com.google.firebase.auth.UserProfileChangeRequest.Builder()
            .setDisplayName(displayName)
            .build()
        user.updateProfile(profileUpdate).await()
        
        ensureUserProfile(user, displayName)
        return user
    }
    
    /**
     * Sign out.
     */
    fun signOut() = auth.signOut()
    
    /**
     * Create user profile in RTDB if it doesn't exist.
     */
    private suspend fun ensureUserProfile(user: FirebaseUser, displayName: String? = null) {
        val ref = db.reference.child("users/${user.uid}")
        val exists = ref.get().await().exists()
        
        if (!exists) {
            val name = displayName ?: user.displayName ?: "Rider"
            val initials = name.split(" ").take(2).map { it.firstOrNull()?.uppercaseChar() ?: 'R' }.joinToString("")
            
            val profile = UserProfile(
                userId = user.uid,
                displayName = name,
                isAnonymous = user.isAnonymous,
                avatarInitials = initials,
                joinedAt = System.currentTimeMillis(),
                lastActiveAt = System.currentTimeMillis(),
            )
            ref.setValue(profile).await()
        } else {
            // Update last active
            ref.child("lastActiveAt").setValue(System.currentTimeMillis()).await()
        }
    }
}
```

---

## Step 8: MapHome Screen

Create `android/app/src/main/java/com/crowdtransit/app/ui/screens/map/MapHomeViewModel.kt`:

```kotlin
package com.crowdtransit.app.ui.screens.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crowdtransit.app.data.repository.StopRepository
import com.crowdtransit.app.model.Stop
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MapHomeUiState(
    val nearbyStops: List<Stop> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val userLat: Double? = null,
    val userLng: Double? = null,
)

@HiltViewModel
class MapHomeViewModel @Inject constructor(
    private val stopRepository: StopRepository,
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(MapHomeUiState())
    val uiState: StateFlow<MapHomeUiState> = _uiState.asStateFlow()
    
    fun onLocationUpdate(lat: Double, lng: Double) {
        _uiState.update { it.copy(userLat = lat, userLng = lng) }
        loadNearbyStops(lat, lng)
    }
    
    private fun loadNearbyStops(lat: Double, lng: Double) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val stops = stopRepository.getStopsNearby(lat, lng, radiusKm = 1.0)
                val sorted = stops.sortedBy { stop ->
                    val dlat = stop.lat - lat
                    val dlng = stop.lng - lng
                    dlat * dlat + dlng * dlng
                }
                _uiState.update { it.copy(nearbyStops = sorted, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }
}
```

Create `android/app/src/main/java/com/crowdtransit/app/ui/screens/map/MapHomeScreen.kt`:

```kotlin
package com.crowdtransit.app.ui.screens.map

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crowdtransit.app.ui.components.MapLibreView
import com.crowdtransit.app.ui.components.SearchBar
import com.crowdtransit.app.ui.components.StopCard
import com.crowdtransit.app.ui.theme.SurfaceElevated

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapHomeScreen(
    onStopClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onProfileClick: () -> Unit,
    onAddStopClick: () -> Unit,
    viewModel: MapHomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberBottomSheetScaffoldState()

    BottomSheetScaffold(
        scaffoldState = sheetState,
        sheetPeekHeight = 180.dp,
        sheetContainerColor = SurfaceElevated,
        sheetContent = {
            // Nearby stops bottom sheet
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Nearby Stops",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(12.dp))
                if (uiState.isLoading) {
                    CircularProgressIndicator()
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(uiState.nearbyStops, key = { it.stopId }) { stop ->
                            StopCard(
                                stop = stop,
                                onClick = { onStopClick(stop.stopId) },
                                modifier = Modifier.width(240.dp),
                            )
                        }
                    }
                }
            }
        },
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            // Full-screen map
            MapLibreView(
                modifier = Modifier.fillMaxSize(),
                stops = uiState.nearbyStops,
                userLat = uiState.userLat,
                userLng = uiState.userLng,
                onStopPinClick = onStopClick,
                onLocationUpdate = viewModel::onLocationUpdate,
            )
            
            // Floating search bar at top
            SearchBar(
                onClickSearch = onSearchClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}
```

---

## Step 9: Navigation

Create `android/app/src/main/java/com/crowdtransit/app/navigation/Screen.kt`:

```kotlin
package com.crowdtransit.app.navigation

sealed class Screen(val route: String) {
    object MapHome : Screen("map_home")
    object StopDetail : Screen("stop/{stopId}") {
        fun createRoute(stopId: String) = "stop/$stopId"
    }
    object RouteDetail : Screen("route/{routeId}") {
        fun createRoute(routeId: String) = "route/$routeId"
    }
    object Search : Screen("search")
    object Profile : Screen("profile")
    object Onboarding : Screen("onboarding")
    object AddStop : Screen("add_stop")
    object RateStop : Screen("rate/{stopId}") {
        fun createRoute(stopId: String) = "rate/$stopId"
    }
}
```

Create `android/app/src/main/java/com/crowdtransit/app/navigation/NavGraph.kt`:

```kotlin
package com.crowdtransit.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.crowdtransit.app.ui.screens.map.MapHomeScreen
import com.crowdtransit.app.ui.screens.stop.StopDetailScreen
import com.crowdtransit.app.ui.screens.search.SearchScreen
import com.crowdtransit.app.ui.screens.profile.ProfileScreen
import com.crowdtransit.app.ui.screens.auth.OnboardingScreen

@Composable
fun CrowdTransitNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Onboarding.route,
) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onFinish = {
                    navController.navigate(Screen.MapHome.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.MapHome.route) {
            MapHomeScreen(
                onStopClick = { navController.navigate(Screen.StopDetail.createRoute(it)) },
                onSearchClick = { navController.navigate(Screen.Search.route) },
                onProfileClick = { navController.navigate(Screen.Profile.route) },
                onAddStopClick = { navController.navigate(Screen.AddStop.route) },
            )
        }
        composable(Screen.StopDetail.route) { backStack ->
            val stopId = backStack.arguments?.getString("stopId") ?: return@composable
            StopDetailScreen(
                stopId = stopId,
                onBack = { navController.popBackStack() },
                onRouteClick = { navController.navigate(Screen.RouteDetail.createRoute(it)) },
                onRateClick = { navController.navigate(Screen.RateStop.createRoute(stopId)) },
            )
        }
        composable(Screen.Search.route) {
            SearchScreen(
                onStopClick = { navController.navigate(Screen.StopDetail.createRoute(it)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Screen.Profile.route) {
            ProfileScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}
```

---

## Step 10: MainActivity

Create `android/app/src/main/java/com/crowdtransit/app/MainActivity.kt`:

```kotlin
package com.crowdtransit.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.crowdtransit.app.navigation.CrowdTransitNavGraph
import com.crowdtransit.app.ui.theme.CrowdTransitTheme
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
```

---

## Step 11: Location Permission & Updates

Create `android/app/src/main/java/com/crowdtransit/app/ui/screens/map/LocationHelper.kt`:

```kotlin
package com.crowdtransit.app.ui.screens.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class UserLocation(val lat: Double, val lng: Double)

fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

@SuppressLint("MissingPermission")
fun locationFlow(context: Context): Flow<UserLocation> = callbackFlow {
    val client = LocationServices.getFusedLocationProviderClient(context)
    val request = LocationRequest.Builder(10_000L) // 10 seconds
        .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
        .setMinUpdateIntervalMillis(5_000L)
        .build()
    
    val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { location ->
                trySend(UserLocation(location.latitude, location.longitude))
            }
        }
    }
    
    client.requestLocationUpdates(request, callback, context.mainLooper)
    awaitClose { client.removeLocationUpdates(callback) }
}
```

---

## Verification

- [ ] App compiles and runs on emulator/device without crashes
- [ ] Onboarding shows 3 screens, navigates to map on finish
- [ ] Map shows (dark OSM tiles via MapLibre, centered on user location)
- [ ] Location permission prompt appears correctly
- [ ] Bottom sheet shows nearby stops fetched from Firebase RTDB
- [ ] Tapping a stop card navigates to StopDetail screen
- [ ] Stop name, transit badges, and star rating display correctly
- [ ] Anonymous sign-in happens automatically on first launch
- [ ] Firebase auth state persists across app restarts
- [ ] App works offline (shows cached data, Firebase persistence enabled)
