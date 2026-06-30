package com.charles.crowdtransit.app.data.repository

import com.charles.crowdtransit.model.Agency
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.getValue
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgencyRepository @Inject constructor(
    private val db: FirebaseDatabase,
) {

    suspend fun getAgency(agencyId: String): Agency? =
        db.reference.child("agencies/")
            .get().await()
            .getValue<Agency>()
}
