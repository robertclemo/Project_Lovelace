package com.roanokeresistance.lovelace.profile

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

private const val INVITES_COLLECTION = "invites"
private const val USERS_COLLECTION = "users"

enum class RedeemResult { SUCCESS, INVALID_CODE, CODE_EXHAUSTED }

private class InvalidCodeException : Exception()
private class CodeExhaustedException : Exception()

/**
 * Invite-code admin-approval gate (§4 of the architecture doc) — a stray
 * Google sign-in isn't enough to join; the account also needs a code an
 * admin handed out. Codes are minted by hand in the Firebase console
 * (no in-app generation yet), each with a `maxUses` cap enforced here via
 * an atomic transaction so two people can't race the same code past its
 * limit.
 */
class InviteRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    suspend fun redeem(code: String, uid: String, displayName: String): RedeemResult {
        val inviteRef = firestore.collection(INVITES_COLLECTION).document(code)
        val userRef = firestore.collection(USERS_COLLECTION).document(uid)

        return try {
            firestore.runTransaction { txn ->
                val invite = txn.get(inviteRef)
                if (!invite.exists() || invite.getBoolean("active") != true) {
                    throw InvalidCodeException()
                }
                val maxUses = invite.getLong("maxUses") ?: 0L
                val usedCount = invite.getLong("usedCount") ?: 0L
                if (usedCount >= maxUses) {
                    throw CodeExhaustedException()
                }

                txn.update(inviteRef, "usedCount", usedCount + 1)
                txn.set(
                    userRef,
                    mapOf(
                        "displayName" to displayName,
                        "approved" to true,
                        "invitedByCode" to code
                    ),
                    SetOptions.merge()
                )
                null
            }.await()
            RedeemResult.SUCCESS
        } catch (e: InvalidCodeException) {
            RedeemResult.INVALID_CODE
        } catch (e: CodeExhaustedException) {
            RedeemResult.CODE_EXHAUSTED
        }
    }
}
