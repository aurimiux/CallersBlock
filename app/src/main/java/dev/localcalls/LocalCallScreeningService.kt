package dev.localcalls

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract.PhoneLookup
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telephony.TelephonyManager
import android.util.Log
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class LocalCallScreeningService : CallScreeningService() {

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    override fun onScreenCall(details: Call.Details) {
        // Only incoming calls are screened; anything else passes untouched.
        if (details.callDirection != Call.Details.DIRECTION_INCOMING) {
            respondToCall(details, allowResponse())
            return
        }

        // Contacts lookup is I/O, keep it off the main thread.
        executor.execute {
            val handle = details.handle
            val number = if (handle?.scheme == "tel") handle.schemeSpecificPart else null

            val decision = try {
                PhoneRules.decide(
                    enabled = Prefs.isEnabled(this),
                    rawNumber = number,
                    countryCode = Prefs.countryCode(this),
                    trunkPrefixes = Prefs.trunkPrefixes(this),
                    isEmergency = { isEmergencyNumber(this, it) },
                    isContact = { isInContacts(this, it) },
                )
            } catch (t: Throwable) {
                // Fail open: never block a call because of a bug.
                Log.w(TAG, "Screening failed, allowing call", t)
                PhoneRules.Decision.ALLOW_DISABLED
            }

            respondToCall(details, if (decision.allow) allowResponse() else blockResponse())

            if (!decision.allow && number != null) {
                Prefs.addBlocked(this, PhoneRules.normalize(number))
            }
        }
    }

    override fun onDestroy() {
        executor.shutdown() // queued screenings still finish
        super.onDestroy()
    }

    private fun allowResponse() = CallResponse.Builder().build()

    private fun blockResponse() = CallResponse.Builder()
        .setDisallowCall(true)
        .setRejectCall(true)
        .setSkipNotification(true)
        .setSkipCallLog(false) // keep blocked attempts visible in call history
        .build()

    companion object {
        private const val TAG = "LocalCallScreening"

        fun isEmergencyNumber(ctx: Context, number: String): Boolean = try {
            ctx.getSystemService(TelephonyManager::class.java)?.isEmergencyNumber(number) == true
        } catch (e: Exception) {
            false
        }

        fun isInContacts(ctx: Context, number: String): Boolean {
            if (ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) !=
                PackageManager.PERMISSION_GRANTED
            ) return false
            val uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            return try {
                ctx.contentResolver.query(uri, arrayOf(PhoneLookup._ID), null, null, null)
                    ?.use { it.moveToFirst() } ?: false
            } catch (e: Exception) {
                false
            }
        }
    }
}
