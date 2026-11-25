package dev.localcalls

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.service.quicksettings.TileService
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

data class BlockedCall(val number: String, val timestamp: Long)

/** All app state lives in one private SharedPreferences file on the device. */
object Prefs {
    private const val FILE = "local_calls_prefs"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_COUNTRY = "country_code"
    private const val KEY_TRUNK = "trunk_prefixes"
    private const val KEY_BLOCKED = "blocked_log"

    const val DEFAULT_COUNTRY = "+370"
    const val DEFAULT_TRUNK = "0, 8"
    private const val MAX_BLOCKED = 10

    fun get(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isEnabled(ctx: Context): Boolean = get(ctx).getBoolean(KEY_ENABLED, false)

    fun setEnabled(ctx: Context, enabled: Boolean) {
        get(ctx).edit { putBoolean(KEY_ENABLED, enabled) }
        // Ask the system to refresh the Quick Settings tile so it stays in sync with the app.
        TileService.requestListeningState(
            ctx, ComponentName(ctx, LocalCallsTileService::class.java)
        )
    }

    fun countryCode(ctx: Context): String =
        get(ctx).getString(KEY_COUNTRY, DEFAULT_COUNTRY) ?: DEFAULT_COUNTRY

    fun setCountryCode(ctx: Context, value: String) =
        get(ctx).edit { putString(KEY_COUNTRY, value) }

    fun trunkPrefixesRaw(ctx: Context): String =
        get(ctx).getString(KEY_TRUNK, DEFAULT_TRUNK) ?: DEFAULT_TRUNK

    fun setTrunkPrefixesRaw(ctx: Context, value: String) =
        get(ctx).edit { putString(KEY_TRUNK, value) }

    fun trunkPrefixes(ctx: Context): List<String> = PhoneRules.parseTrunkPrefixes(trunkPrefixesRaw(ctx))

    fun blockedLog(ctx: Context): List<BlockedCall> {
        val json = get(ctx).getString(KEY_BLOCKED, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                BlockedCall(o.getString("n"), o.getLong("t"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun addBlocked(ctx: Context, number: String, timestamp: Long = System.currentTimeMillis()) {
        val updated = (listOf(BlockedCall(number, timestamp)) + blockedLog(ctx)).take(MAX_BLOCKED)
        val arr = JSONArray()
        updated.forEach { arr.put(JSONObject().put("n", it.number).put("t", it.timestamp)) }
        get(ctx).edit { putString(KEY_BLOCKED, arr.toString()) }
    }

    fun clearBlocked(ctx: Context) = get(ctx).edit { remove(KEY_BLOCKED) }
}
