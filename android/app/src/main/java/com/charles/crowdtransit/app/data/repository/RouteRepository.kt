package com.charles.crowdtransit.app.data.repository

import com.charles.crowdtransit.app.data.firebase.observeAsFlow
import com.charles.crowdtransit.model.Route
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.getValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RouteRepository @Inject constructor(
    private val db: FirebaseDatabase,
) {

    fun observeRoute(routeId: String): Flow<Route?> =
        db.reference.child("routes/")
            .observeAsFlow()
            .map { it?.getValue<Route>() }

    suspend fun getRoute(routeId: String): Route? =
        db.reference.child("routes/")
            .get().await()
            .getValue<Route>()

    suspend fun getRoutesByIds(ids: List<String>): List<Route> =
        ids.mapNotNull { getRoute(it) }
}
