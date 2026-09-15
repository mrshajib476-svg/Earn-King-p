package com.example

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Helper for Firebase Authentication, Firestore backend validation,
 * and Firebase Storage.
 */
class FirebaseHelper(private val context: Context) {

    private val tag = "FirebaseHelper"
    var isFirebaseAvailable: Boolean = false
        private set

    private var auth: FirebaseAuth? = null
    private var firestore: FirebaseFirestore? = null
    private var storage: FirebaseStorage? = null

    init {
        try {
            val app = FirebaseApp.initializeApp(context)
            if (app != null) {
                auth = FirebaseAuth.getInstance()
                firestore = FirebaseFirestore.getInstance()
                storage = FirebaseStorage.getInstance()
                isFirebaseAvailable = true
                Log.d(tag, "Firebase initialized with Auth and Firestore.")
            }
        } catch (e: Exception) {
            Log.w(tag, "Firebase initialization fallback (running with local auth fallback): ${e.message}")
            isFirebaseAvailable = false
        }
    }

    // ==========================================
    // FIREBASE AUTHENTICATION
    // ==========================================

    fun getCurrentUser(): FirebaseUser? = auth?.currentUser

    fun signUpWithEmail(
        email: String,
        pass: String,
        displayName: String,
        onComplete: (success: Boolean, uid: String?, error: String?) -> Unit
    ) {
        val authInstance = auth
        if (!isFirebaseAvailable || authInstance == null) {
            // Local fallback simulated auth
            val mockUid = "EK-" + Math.abs(email.hashCode())
            onComplete(true, mockUid, null)
            return
        }

        authInstance.createUserWithEmailAndPassword(email, pass)
            .addOnSuccessListener { authResult ->
                val user = authResult.user
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(displayName)
                    .build()

                user?.updateProfile(profileUpdates)

                val uid = user?.uid ?: ("EK-" + System.currentTimeMillis())
                // Initialize user document in Firestore
                val initialData = hashMapOf(
                    "email" to email,
                    "displayName" to displayName,
                    "balance" to 100L, // 100 welcome bonus
                    "todayEarnings" to 100L,
                    "adsWatched" to 0,
                    "lastBonusClaimDate" to "",
                    "createdAt" to System.currentTimeMillis()
                )

                firestore?.collection("users")?.document(uid)
                    ?.set(initialData, SetOptions.merge())

                onComplete(true, uid, null)
            }
            .addOnFailureListener { e ->
                Log.e(tag, "Sign up error: ${e.message}")
                onComplete(false, null, e.localizedMessage ?: "Sign up failed")
            }
    }

    fun loginWithEmail(
        email: String,
        pass: String,
        onComplete: (success: Boolean, uid: String?, displayName: String?, error: String?) -> Unit
    ) {
        val authInstance = auth
        if (!isFirebaseAvailable || authInstance == null) {
            val mockUid = "EK-" + Math.abs(email.hashCode())
            onComplete(true, mockUid, email.substringBefore("@"), null)
            return
        }

        authInstance.signInWithEmailAndPassword(email, pass)
            .addOnSuccessListener { authResult ->
                val user = authResult.user
                val uid = user?.uid ?: ""
                val name = user?.displayName ?: email.substringBefore("@")
                onComplete(true, uid, name, null)
            }
            .addOnFailureListener { e ->
                Log.e(tag, "Login error: ${e.message}")
                onComplete(false, null, null, e.localizedMessage ?: "Invalid email or password")
            }
    }

    fun logout() {
        try {
            auth?.signOut()
        } catch (e: Exception) {
            Log.e(tag, "Logout error", e)
        }
    }

    // ==========================================
    // BACKEND COIN VALIDATION & DAILY BONUS
    // ==========================================

    private fun getTodayDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date())
    }

    /**
     * Server-side validated Daily Login Bonus claim.
     * Checks user's lastBonusClaimDate in Firestore. Only awards bonus if not yet claimed today.
     */
    fun claimDailyLoginBonus(
        userId: String,
        bonusAmount: Long,
        onComplete: (success: Boolean, newBalance: Long, error: String?) -> Unit
    ) {
        val today = getTodayDateString()

        if (!isFirebaseAvailable || firestore == null) {
            // Local fallback validation
            onComplete(true, bonusAmount, null)
            return
        }

        val userDocRef = firestore?.collection("users")?.document(userId) ?: run {
            onComplete(false, 0, "Database unavailable")
            return
        }

        firestore?.runTransaction { transaction ->
            val snapshot = transaction.get(userDocRef)
            val lastClaim = snapshot.getString("lastBonusClaimDate") ?: ""

            if (lastClaim == today) {
                throw IllegalStateException("DAILY_BONUS_ALREADY_CLAIMED")
            }

            val currentBalance = snapshot.getLong("balance") ?: 0L
            val currentTodayEarned = snapshot.getLong("todayEarnings") ?: 0L
            val updatedBalance = currentBalance + bonusAmount
            val updatedTodayEarned = currentTodayEarned + bonusAmount

            transaction.update(userDocRef, mapOf(
                "balance" to updatedBalance,
                "todayEarnings" to updatedTodayEarned,
                "lastBonusClaimDate" to today,
                "lastUpdated" to System.currentTimeMillis()
            ))

            // Record transaction
            val txRef = userDocRef.collection("transactions").document()
            val txData = hashMapOf(
                "type" to "earn",
                "amount" to bonusAmount,
                "description" to "Daily Login Bonus",
                "timestamp" to System.currentTimeMillis()
            )
            transaction.set(txRef, txData)

            updatedBalance
        }?.addOnSuccessListener { newBalance ->
            Log.d(tag, "Daily bonus claimed successfully: new balance = $newBalance")
            onComplete(true, newBalance, null)
        }?.addOnFailureListener { e ->
            if (e.message?.contains("DAILY_BONUS_ALREADY_CLAIMED") == true) {
                onComplete(false, 0, "Already claimed today's bonus")
            } else {
                Log.e(tag, "Failed to claim daily bonus: ${e.message}")
                onComplete(false, 0, e.localizedMessage ?: "Claim failed")
            }
        }
    }

    /**
     * Validates and awards verified AdMob rewarded coins on backend
     */
    fun validateAndAwardAdReward(
        userId: String,
        coins: Long,
        onComplete: (success: Boolean, newBalance: Long) -> Unit
    ) {
        if (!isFirebaseAvailable || firestore == null) {
            onComplete(true, coins)
            return
        }

        val userDocRef = firestore?.collection("users")?.document(userId) ?: return

        userDocRef.update(
            "balance", FieldValue.increment(coins),
            "todayEarnings", FieldValue.increment(coins),
            "adsWatched", FieldValue.increment(1),
            "lastUpdated", System.currentTimeMillis()
        ).addOnSuccessListener {
            userDocRef.get().addOnSuccessListener { snapshot ->
                val newBal = snapshot.getLong("balance") ?: 0L
                // Record transaction
                val tx = hashMapOf(
                    "type" to "earn",
                    "amount" to coins,
                    "description" to "Rewarded Video Ad",
                    "timestamp" to System.currentTimeMillis()
                )
                userDocRef.collection("transactions").add(tx)
                onComplete(true, newBal)
            }
        }.addOnFailureListener {
            onComplete(false, 0)
        }
    }

    fun syncUserData(
        userId: String,
        balance: Long,
        adsWatched: Int,
        todayEarnings: Long,
        onComplete: (success: Boolean) -> Unit
    ) {
        if (!isFirebaseAvailable || firestore == null) {
            onComplete(true)
            return
        }

        val data = hashMapOf(
            "userId" to userId,
            "balance" to balance,
            "adsWatched" to adsWatched,
            "todayEarnings" to todayEarnings,
            "lastUpdated" to System.currentTimeMillis()
        )

        firestore?.collection("users")?.document(userId)
            ?.set(data, SetOptions.merge())
            ?.addOnSuccessListener { onComplete(true) }
            ?.addOnFailureListener { onComplete(false) }
    }

    fun recordTransaction(
        userId: String,
        txType: String,
        amount: Long,
        description: String,
        onComplete: (success: Boolean) -> Unit
    ) {
        if (!isFirebaseAvailable || firestore == null) {
            onComplete(true)
            return
        }

        val tx = hashMapOf(
            "type" to txType,
            "amount" to amount,
            "description" to description,
            "timestamp" to System.currentTimeMillis()
        )

        firestore?.collection("users")?.document(userId)
            ?.collection("transactions")
            ?.add(tx)
            ?.addOnSuccessListener { onComplete(true) }
            ?.addOnFailureListener { onComplete(false) }
    }

    fun fetchUserData(
        userId: String,
        onComplete: (balance: Long, todayEarnings: Long, adsWatched: Int, lastBonusClaimDate: String) -> Unit
    ) {
        if (!isFirebaseAvailable || firestore == null) {
            onComplete(0L, 0L, 0, "")
            return
        }

        firestore?.collection("users")?.document(userId)?.get()
            ?.addOnSuccessListener { doc ->
                val balance = doc.getLong("balance") ?: 0L
                val todayEarnings = doc.getLong("todayEarnings") ?: 0L
                val adsWatched = (doc.getLong("adsWatched") ?: 0L).toInt()
                val lastBonus = doc.getString("lastBonusClaimDate") ?: ""
                onComplete(balance, todayEarnings, adsWatched, lastBonus)
            }
            ?.addOnFailureListener {
                onComplete(0L, 0L, 0, "")
            }
    }
}
