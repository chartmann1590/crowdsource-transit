# Phase 6: Android App — Features

> **Goal:** Add all interactive features to the Android app: ratings, comments, crowdsourcing new stops, incident reporting, search, directions to a stop, and a user profile screen.

---

## Step 1: Comment Repository

Create `android/app/src/main/java/com/crowdtransit/app/data/repository/CommentRepository.kt`:

```kotlin
package com.crowdtransit.app.data.repository

import com.crowdtransit.app.data.firebase.observeAsFlow
import com.crowdtransit.app.model.Comment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.getValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommentRepository @Inject constructor(
    private val db: FirebaseDatabase,
    private val auth: FirebaseAuth,
) {
    
    fun observeComments(targetType: String, targetId: String): Flow<List<Comment>> =
        db.reference.child("comments/$targetType/$targetId")
            .observeAsFlow()
            .map { snapshot ->
                snapshot?.children?.mapNotNull { it.getValue<Comment>() }
                    ?.sortedByDescending { it.createdAt }
                    ?: emptyList()
            }
    
    suspend fun addComment(
        targetType: String,
        targetId: String,
        text: String,
        rating: Int,
        transitType: String,
        isAnonymous: Boolean,
    ): Comment {
        val user = auth.currentUser ?: throw IllegalStateException("Not authenticated")
        val profile = db.reference.child("users/${user.uid}").get().await()
        val displayName = if (isAnonymous) "Anonymous Rider" else (profile.child("displayName").value as? String ?: "Rider")
        val initials = if (isAnonymous) "?" else displayName.split(" ").take(2).map { it.firstOrNull()?.uppercaseChar() ?: 'R' }.joinToString("")
        
        val ref = db.reference.child("comments/$targetType/$targetId").push()
        val commentId = ref.key ?: throw IllegalStateException("No key generated")
        val now = System.currentTimeMillis()
        
        val comment = Comment(
            commentId = commentId,
            userId = user.uid,
            displayName = displayName,
            isAnonymous = isAnonymous,
            avatarInitials = initials,
            targetType = targetType,
            targetId = targetId,
            text = text,
            transitType = transitType,
            rating = rating,
            helpfulCount = 0,
            flagged = false,
            createdAt = now,
            updatedAt = now,
        )
        
        ref.setValue(comment).await()
        
        // Increment comment count on the target
        if (targetType == "stop") {
            db.reference.child("stops/$targetId/commentCount")
                .runTransaction(object : com.google.firebase.database.Transaction.Handler {
                    override fun doTransaction(mutableData: com.google.firebase.database.MutableData): com.google.firebase.database.Transaction.Result {
                        val count = mutableData.getValue(Long::class.java) ?: 0L
                        mutableData.value = count + 1
                        return com.google.firebase.database.Transaction.success(mutableData)
                    }
                    override fun onComplete(error: com.google.firebase.database.DatabaseError?, committed: Boolean, snapshot: com.google.firebase.database.DataSnapshot?) {}
                })
        }
        
        // Update user review count
        db.reference.child("users/${user.uid}/stats/reviewCount")
            .runTransaction(object : com.google.firebase.database.Transaction.Handler {
                override fun doTransaction(mutableData: com.google.firebase.database.MutableData): com.google.firebase.database.Transaction.Result {
                    val count = mutableData.getValue(Long::class.java) ?: 0L
                    mutableData.value = count + 1
                    return com.google.firebase.database.Transaction.success(mutableData)
                }
                override fun onComplete(error: com.google.firebase.database.DatabaseError?, committed: Boolean, snapshot: com.google.firebase.database.DataSnapshot?) {}
            })
        
        return comment
    }
    
    suspend fun markHelpful(targetType: String, targetId: String, commentId: String) {
        val user = auth.currentUser ?: return
        val helpfulRef = db.reference.child("comments/$targetType/$targetId/$commentId/helpful/${user.uid}")
        val countRef = db.reference.child("comments/$targetType/$targetId/$commentId/helpfulCount")
        
        val alreadyMarked = helpfulRef.get().await().getValue(Boolean::class.java) ?: false
        
        if (alreadyMarked) {
            helpfulRef.removeValue().await()
            countRef.runTransaction(object : com.google.firebase.database.Transaction.Handler {
                override fun doTransaction(m: com.google.firebase.database.MutableData): com.google.firebase.database.Transaction.Result {
                    m.value = maxOf(0, (m.getValue(Long::class.java) ?: 1L) - 1)
                    return com.google.firebase.database.Transaction.success(m)
                }
                override fun onComplete(e: com.google.firebase.database.DatabaseError?, c: Boolean, s: com.google.firebase.database.DataSnapshot?) {}
            })
        } else {
            helpfulRef.setValue(true).await()
            countRef.runTransaction(object : com.google.firebase.database.Transaction.Handler {
                override fun doTransaction(m: com.google.firebase.database.MutableData): com.google.firebase.database.Transaction.Result {
                    m.value = (m.getValue(Long::class.java) ?: 0L) + 1
                    return com.google.firebase.database.Transaction.success(m)
                }
                override fun onComplete(e: com.google.firebase.database.DatabaseError?, c: Boolean, s: com.google.firebase.database.DataSnapshot?) {}
            })
        }
    }
}
```

---

## Step 2: Rating Repository

Create `android/app/src/main/java/com/crowdtransit/app/data/repository/RatingRepository.kt`:

```kotlin
package com.crowdtransit.app.data.repository

import com.crowdtransit.app.model.Rating
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.getValue
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RatingRepository @Inject constructor(
    private val db: FirebaseDatabase,
    private val auth: FirebaseAuth,
) {
    
    suspend fun getUserRating(targetType: String, targetId: String): Rating? {
        val uid = auth.currentUser?.uid ?: return null
        return db.reference.child("ratings/$targetType/$targetId/$uid")
            .get().await()
            .getValue<Rating>()
    }
    
    suspend fun submitRating(
        targetType: String,
        targetId: String,
        overall: Int,
        subcategories: Map<String, Int> = emptyMap(),
        transitType: String = "",
        isAnonymous: Boolean = false,
    ) {
        val user = auth.currentUser ?: throw IllegalStateException("Not authenticated")
        val profile = db.reference.child("users/${user.uid}").get().await()
        val displayName = if (isAnonymous) "Anonymous" else (profile.child("displayName").value as? String ?: "Rider")
        
        // Get previous rating to compute delta
        val prevRating = getUserRating(targetType, targetId)
        val prevOverall = prevRating?.overall ?: 0
        
        val now = System.currentTimeMillis()
        val rating = Rating(
            userId = user.uid,
            displayName = displayName,
            isAnonymous = isAnonymous,
            targetType = targetType,
            targetId = targetId,
            overall = overall,
            subcategories = subcategories,
            transitType = transitType,
            createdAt = prevRating?.createdAt ?: now,
            updatedAt = now,
        )
        
        // Write rating
        db.reference.child("ratings/$targetType/$targetId/${user.uid}").setValue(rating).await()
        
        // Update denormalized ratingSum and ratingCount on the target
        val targetRef = db.reference.child(if (targetType == "stop") "stops/$targetId" else "routes/$targetId")
        
        if (prevRating == null) {
            // New rating
            targetRef.child("ratingSum").runTransaction(object : com.google.firebase.database.Transaction.Handler {
                override fun doTransaction(m: com.google.firebase.database.MutableData): com.google.firebase.database.Transaction.Result {
                    m.value = (m.getValue(Long::class.java) ?: 0L) + overall
                    return com.google.firebase.database.Transaction.success(m)
                }
                override fun onComplete(e: com.google.firebase.database.DatabaseError?, c: Boolean, s: com.google.firebase.database.DataSnapshot?) {}
            })
            targetRef.child("ratingCount").runTransaction(object : com.google.firebase.database.Transaction.Handler {
                override fun doTransaction(m: com.google.firebase.database.MutableData): com.google.firebase.database.Transaction.Result {
                    m.value = (m.getValue(Long::class.java) ?: 0L) + 1
                    return com.google.firebase.database.Transaction.success(m)
                }
                override fun onComplete(e: com.google.firebase.database.DatabaseError?, c: Boolean, s: com.google.firebase.database.DataSnapshot?) {}
            })
        } else {
            // Updated rating — adjust sum by delta
            val delta = overall - prevOverall
            if (delta != 0) {
                targetRef.child("ratingSum").runTransaction(object : com.google.firebase.database.Transaction.Handler {
                    override fun doTransaction(m: com.google.firebase.database.MutableData): com.google.firebase.database.Transaction.Result {
                        m.value = (m.getValue(Long::class.java) ?: 0L) + delta
                        return com.google.firebase.database.Transaction.success(m)
                    }
                    override fun onComplete(e: com.google.firebase.database.DatabaseError?, c: Boolean, s: com.google.firebase.database.DataSnapshot?) {}
                })
            }
        }
    }
}
```

---

## Step 3: Stop Detail Screen (with Reviews)

Create `android/app/src/main/java/com/crowdtransit/app/ui/screens/stop/StopDetailViewModel.kt`:

```kotlin
package com.crowdtransit.app.ui.screens.stop

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crowdtransit.app.data.repository.CommentRepository
import com.crowdtransit.app.data.repository.RatingRepository
import com.crowdtransit.app.data.repository.StopRepository
import com.crowdtransit.app.model.Comment
import com.crowdtransit.app.model.Rating
import com.crowdtransit.app.model.Stop
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StopDetailUiState(
    val stop: Stop? = null,
    val comments: List<Comment> = emptyList(),
    val userRating: Rating? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class StopDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val stopRepository: StopRepository,
    private val commentRepository: CommentRepository,
    private val ratingRepository: RatingRepository,
) : ViewModel() {
    
    private val stopId: String = checkNotNull(savedStateHandle["stopId"])
    
    private val _uiState = MutableStateFlow(StopDetailUiState())
    val uiState: StateFlow<StopDetailUiState> = _uiState.asStateFlow()
    
    init {
        viewModelScope.launch {
            // Observe stop data
            stopRepository.observeStop(stopId).collect { stop ->
                _uiState.update { it.copy(stop = stop, isLoading = false) }
            }
        }
        viewModelScope.launch {
            // Observe comments
            commentRepository.observeComments("stops", stopId).collect { comments ->
                _uiState.update { it.copy(comments = comments) }
            }
        }
        viewModelScope.launch {
            // Load user's existing rating
            val rating = ratingRepository.getUserRating("stops", stopId)
            _uiState.update { it.copy(userRating = rating) }
        }
    }
    
    fun markHelpful(commentId: String) {
        viewModelScope.launch {
            commentRepository.markHelpful("stops", stopId, commentId)
        }
    }
}
```

Create `android/app/src/main/java/com/crowdtransit/app/ui/screens/stop/StopDetailScreen.kt`:

```kotlin
package com.crowdtransit.app.ui.screens.stop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crowdtransit.app.ui.components.ReviewCard
import com.crowdtransit.app.ui.components.StarRating
import com.crowdtransit.app.ui.components.TransitBadge
import com.crowdtransit.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopDetailScreen(
    stopId: String,
    onBack: () -> Unit,
    onRouteClick: (String) -> Unit,
    onRateClick: () -> Unit,
    viewModel: StopDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val stop = uiState.stop

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stop?.name ?: "Stop", color = OnSurface) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = OnSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onRateClick,
                icon = { Icon(Icons.Default.Star, "Rate") },
                text = { Text("Write a Review") },
                containerColor = Secondary,
                contentColor = OnSecondary,
            )
        },
        containerColor = SurfaceDark,
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
            return@Scaffold
        }

        if (stop == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Stop not found", color = OnSurfaceSecondary)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item {
                // Stop info header
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stop.name, style = MaterialTheme.typography.headlineMedium, color = OnSurface)
                    Text("${stop.city}, ${stop.state}", style = MaterialTheme.typography.bodyLarge, color = OnSurfaceSecondary)
                    
                    // Transit type badges
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        stop.transitTypes.forEach { type ->
                            TransitBadge(type = type)
                        }
                    }
                    
                    // Rating summary
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "%.1f".format(stop.averageRating),
                            style = MaterialTheme.typography.headlineSmall,
                            color = RatingGold,
                        )
                        StarRating(rating = stop.averageRating)
                        Text(
                            "(${stop.ratingCount} reviews)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnSurfaceSecondary,
                        )
                    }
                    
                    Divider(color = SurfaceElevated)
                }
            }
            
            // Reviews
            item {
                Text(
                    "Community Reviews",
                    style = MaterialTheme.typography.titleMedium,
                    color = OnSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            
            items(uiState.comments, key = { it.commentId }) { comment ->
                ReviewCard(
                    comment = comment,
                    onHelpfulClick = { viewModel.markHelpful(comment.commentId) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            
            if (uiState.comments.isEmpty()) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("No reviews yet. Be the first!", color = OnSurfaceSecondary)
                    }
                }
            }
        }
    }
}
```

---

## Step 4: Rate & Review Screen

Create `android/app/src/main/java/com/crowdtransit/app/ui/screens/stop/RateStopScreen.kt`:

```kotlin
package com.crowdtransit.app.ui.screens.stop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.crowdtransit.app.ui.components.StarRating
import com.crowdtransit.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RateStopScreen(
    stopId: String,
    stopName: String,
    onBack: () -> Unit,
    onSubmit: (overall: Int, subcategories: Map<String, Int>, text: String, transitType: String, isAnonymous: Boolean) -> Unit,
) {
    var overallRating by remember { mutableIntStateOf(0) }
    var cleanlinessRating by remember { mutableIntStateOf(0) }
    var safetyRating by remember { mutableIntStateOf(0) }
    var accessibilityRating by remember { mutableIntStateOf(0) }
    var reliabilityRating by remember { mutableIntStateOf(0) }
    var commentText by remember { mutableStateOf("") }
    var selectedTransitType by remember { mutableStateOf("bus") }
    var postAnonymously by remember { mutableStateOf(false) }
    
    val transitTypes = listOf("bus", "train", "subway", "ferry", "tram")
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Write a Review", color = OnSurface) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, "Close", tint = OnSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface),
            )
        },
        containerColor = SurfaceDark,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(stopName, style = MaterialTheme.typography.titleLarge, color = OnSurface)
            
            // Overall rating
            Text("Overall Rating", style = MaterialTheme.typography.titleMedium, color = OnSurface)
            StarRating(
                rating = overallRating.toFloat(),
                starSize = 40.dp,
                interactive = true,
                onRatingChange = { overallRating = it },
            )
            
            Divider(color = SurfaceElevated)
            
            // Subcategory ratings
            Text("Rate by Category", style = MaterialTheme.typography.titleMedium, color = OnSurface)
            listOf(
                "Cleanliness" to cleanlinessRating,
                "Safety" to safetyRating,
                "Accessibility" to accessibilityRating,
                "Reliability" to reliabilityRating,
            ).forEachIndexed { i, (label, rating) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, style = MaterialTheme.typography.bodyLarge, color = OnSurface)
                    StarRating(
                        rating = rating.toFloat(),
                        starSize = 24.dp,
                        interactive = true,
                        onRatingChange = { newVal ->
                            when (i) {
                                0 -> cleanlinessRating = newVal
                                1 -> safetyRating = newVal
                                2 -> accessibilityRating = newVal
                                3 -> reliabilityRating = newVal
                            }
                        },
                    )
                }
            }
            
            Divider(color = SurfaceElevated)
            
            // Comment
            Text("Your Experience", style = MaterialTheme.typography.titleMedium, color = OnSurface)
            OutlinedTextField(
                value = commentText,
                onValueChange = { commentText = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                placeholder = { Text("Share your experience at this stop...", color = OnSurfaceSecondary) },
                maxLines = 8,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = SurfaceElevated,
                    focusedTextColor = OnSurface,
                    unfocusedTextColor = OnSurface,
                    cursorColor = Primary,
                ),
            )
            
            // Transit type selector
            Text("What did you ride?", style = MaterialTheme.typography.titleMedium, color = OnSurface)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                transitTypes.forEach { type ->
                    FilterChip(
                        selected = selectedTransitType == type,
                        onClick = { selectedTransitType = type },
                        label = { Text(type.replaceFirstChar { it.uppercase() }) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Primary,
                            selectedLabelColor = OnPrimary,
                        ),
                    )
                }
            }
            
            // Anonymous toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Post anonymously", style = MaterialTheme.typography.bodyLarge, color = OnSurface)
                Switch(
                    checked = postAnonymously,
                    onCheckedChange = { postAnonymously = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = Primary),
                )
            }
            
            // Submit
            Button(
                onClick = {
                    if (overallRating > 0) {
                        onSubmit(
                            overallRating,
                            mapOf(
                                "cleanliness" to cleanlinessRating,
                                "safety" to safetyRating,
                                "accessibility" to accessibilityRating,
                                "reliability" to reliabilityRating,
                            ),
                            commentText,
                            selectedTransitType,
                            postAnonymously,
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = overallRating > 0,
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
            ) {
                Text("Submit Review", style = MaterialTheme.typography.labelLarge, color = OnPrimary)
            }
        }
    }
}
```

---

## Step 5: Add Stop Screen (Crowdsourcing)

Create `android/app/src/main/java/com/crowdtransit/app/ui/screens/crowdsource/AddStopScreen.kt`:

```kotlin
package com.crowdtransit.app.ui.screens.crowdsource

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.crowdtransit.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddStopScreen(
    userLat: Double,
    userLng: Double,
    onBack: () -> Unit,
    onSubmit: (name: String, lat: Double, lng: Double, transitTypes: List<String>, agencyName: String, notes: String, features: Map<String, Boolean>) -> Unit,
) {
    var stopName by remember { mutableStateOf("") }
    var stopCode by remember { mutableStateOf("") }
    var agencyName by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var pinLat by remember { mutableDoubleStateOf(userLat) }
    var pinLng by remember { mutableDoubleStateOf(userLng) }
    
    val transitTypeOptions = listOf("bus", "train", "subway", "ferry", "tram")
    val selectedTypes = remember { mutableStateListOf<String>() }
    
    val featureOptions = listOf("shelter", "bench", "lighting", "elevator", "bikeParking", "parking")
    val selectedFeatures = remember { mutableStateMapOf<String, Boolean>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add a Stop", color = OnSurface) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, "Close", tint = OnSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface),
            )
        },
        containerColor = SurfaceDark,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Info card
            Card(colors = CardDefaults.cardColors(containerColor = SurfaceCard)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Adding a Missing Stop", style = MaterialTheme.typography.titleMedium, color = OnSurface)
                    Text(
                        "Your submission will be reviewed by the community. Once 3 users verify it, the stop will be added.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceSecondary,
                    )
                }
            }
            
            OutlinedTextField(
                value = stopName,
                onValueChange = { stopName = it },
                label = { Text("Stop Name *", color = OnSurfaceSecondary) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = SurfaceElevated,
                    focusedTextColor = OnSurface,
                    unfocusedTextColor = OnSurface,
                ),
            )
            
            OutlinedTextField(
                value = stopCode,
                onValueChange = { stopCode = it },
                label = { Text("Stop Code (optional)", color = OnSurfaceSecondary) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = SurfaceElevated,
                    focusedTextColor = OnSurface,
                    unfocusedTextColor = OnSurface,
                ),
            )
            
            // Transit type multi-select
            Text("Transit Types *", style = MaterialTheme.typography.titleMedium, color = OnSurface)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                transitTypeOptions.forEach { type ->
                    FilterChip(
                        selected = selectedTypes.contains(type),
                        onClick = {
                            if (selectedTypes.contains(type)) selectedTypes.remove(type)
                            else selectedTypes.add(type)
                        },
                        label = { Text(type.replaceFirstChar { it.uppercase() }) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Primary,
                            selectedLabelColor = OnPrimary,
                        ),
                    )
                }
            }
            
            OutlinedTextField(
                value = agencyName,
                onValueChange = { agencyName = it },
                label = { Text("Transit Agency Name", color = OnSurfaceSecondary) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = SurfaceElevated,
                    focusedTextColor = OnSurface,
                    unfocusedTextColor = OnSurface,
                ),
            )
            
            // Accessibility features
            Text("Accessibility & Amenities", style = MaterialTheme.typography.titleMedium, color = OnSurface)
            featureOptions.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { feature ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = selectedFeatures[feature] ?: false,
                                onCheckedChange = { selectedFeatures[feature] = it },
                                colors = CheckboxDefaults.colors(checkedColor = Primary),
                            )
                            Text(
                                feature.replaceFirstChar { it.uppercase() },
                                color = OnSurface,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
            
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Additional Notes", color = OnSurfaceSecondary) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = SurfaceElevated,
                    focusedTextColor = OnSurface,
                    unfocusedTextColor = OnSurface,
                ),
            )
            
            // Location display
            Card(colors = CardDefaults.cardColors(containerColor = SurfaceCard)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Location", style = MaterialTheme.typography.titleMedium, color = OnSurface)
                    Text(
                        "Lat: ${String.format("%.6f", pinLat)}, Lng: ${String.format("%.6f", pinLng)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceSecondary,
                    )
                    Text(
                        "(Your current location will be used. Walk to the stop first.)",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceSecondary,
                    )
                }
            }
            
            Button(
                onClick = {
                    if (stopName.isNotBlank() && selectedTypes.isNotEmpty()) {
                        onSubmit(
                            stopName,
                            pinLat,
                            pinLng,
                            selectedTypes.toList(),
                            agencyName,
                            notes,
                            selectedFeatures.toMap(),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = stopName.isNotBlank() && selectedTypes.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Secondary),
            ) {
                Text("Submit Stop", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
```

---

## Step 6: Search Screen

Create `android/app/src/main/java/com/crowdtransit/app/ui/screens/search/SearchViewModel.kt`:

```kotlin
package com.crowdtransit.app.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crowdtransit.app.model.Stop
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.getValue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val results: List<Stop> = emptyList(),
    val isSearching: Boolean = false,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val db: FirebaseDatabase,
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()
    
    private var searchJob: Job? = null
    
    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        
        searchJob?.cancel()
        if (query.length < 2) {
            _uiState.update { it.copy(results = emptyList(), isSearching = false) }
            return
        }
        
        searchJob = viewModelScope.launch {
            delay(300) // Debounce
            _uiState.update { it.copy(isSearching = true) }
            
            try {
                // Query Firebase for stops whose name starts with the query
                // RTDB doesn't support full-text search, so we use orderByChild + startAt/endAt
                val queryLower = query.lowercase()
                val snap = db.reference.child("stops")
                    .orderByChild("name")
                    .startAt(query)
                    .endAt(query + "")
                    .limitToFirst(20)
                    .get().await()
                
                val results = snap.children.mapNotNull { it.getValue<Stop>() }
                _uiState.update { it.copy(results = results, isSearching = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSearching = false) }
            }
        }
    }
}
```

---

## Step 7: Directions to Stop (OpenRouteService)

Create `android/app/src/main/java/com/crowdtransit/app/data/repository/DirectionsRepository.kt`:

```kotlin
package com.crowdtransit.app.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DirectionsRepository @Inject constructor() {
    
    /**
     * Open the OS maps app with walking directions to the given coordinates.
     * This uses the Android geo: intent which opens Google Maps, Apple Maps, or
     * any installed maps app — no API key required.
     */
    fun openDirectionsToStop(context: Context, lat: Double, lng: Double, name: String) {
        val uri = Uri.parse("geo:$lat,$lng?q=${Uri.encode(name)}")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        intent.setPackage("com.google.android.apps.maps")
        
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            // Fallback: open in browser
            val browserUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng&travelmode=walking")
            context.startActivity(Intent(Intent.ACTION_VIEW, browserUri))
        }
    }
    
    /**
     * Build a maps URI for walking directions.
     */
    fun getWalkingDirectionsUri(destLat: Double, destLng: Double): Uri {
        return Uri.parse("google.navigation:q=$destLat,$destLng&mode=w")
    }
}
```

---

## Step 8: ReviewCard Component

Create `android/app/src/main/java/com/crowdtransit/app/ui/components/ReviewCard.kt`:

```kotlin
package com.crowdtransit.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.crowdtransit.app.model.Comment
import com.crowdtransit.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ReviewCard(
    comment: Comment,
    onHelpfulClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateStr = remember(comment.createdAt) {
        SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(comment.createdAt))
    }
    
    Column(
        modifier = modifier
            .background(SurfaceCard, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Avatar circle
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (comment.isAnonymous) "?" else comment.avatarInitials,
                    color = OnPrimary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Column {
                Text(
                    text = if (comment.isAnonymous) "Anonymous Rider" else comment.displayName,
                    style = if (comment.isAnonymous) MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic) 
                            else MaterialTheme.typography.bodyMedium,
                    color = OnSurface,
                )
                Text(dateStr, style = MaterialTheme.typography.labelSmall, color = OnSurfaceSecondary)
            }
            Spacer(Modifier.weight(1f))
            if (comment.rating > 0) {
                StarRating(rating = comment.rating.toFloat(), starSize = 14.dp)
            }
        }
        
        Text(comment.text, style = MaterialTheme.typography.bodyMedium, color = OnSurface)
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (comment.transitType.isNotEmpty()) {
                TransitBadge(type = comment.transitType)
                Spacer(Modifier.weight(1f))
            }
            TextButton(onClick = onHelpfulClick) {
                Icon(Icons.Outlined.ThumbUp, "Helpful", tint = OnSurfaceSecondary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    "Helpful (${comment.helpfulCount})",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceSecondary,
                )
            }
        }
    }
}
```

---

## Verification

- [ ] Star rating component responds to taps (interactive mode)
- [ ] Submitting a review writes to `/comments/stops/{stopId}` in RTDB
- [ ] After submitting, comment appears immediately in the stop detail (real-time)
- [ ] Rating updates `/stops/{stopId}/ratingSum` and `/ratingCount`
- [ ] Add Stop form submits to `/crowdsourced` in RTDB
- [ ] Search finds stops by name (minimum 2 characters)
- [ ] "Get Directions" button opens Google Maps in walking mode
- [ ] "Mark Helpful" toggles on/off and updates helpfulCount in real-time
- [ ] Anonymous posting shows "Anonymous Rider" instead of user's name
- [ ] User profile screen shows correct review count and stops added count
