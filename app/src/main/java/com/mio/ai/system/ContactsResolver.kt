package com.mio.ai.system

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

/**
 * Resolves "mom" → phone number for dial/SMS commands.
 * Raw numbers pass straight through; names need READ_CONTACTS (asked at runtime).
 */
class ContactsResolver(private val context: Context) {

    data class Hit(val name: String, val number: String)
    data class Outcome(val hit: Hit?, val permissionNeeded: Boolean, val notFound: Boolean)

    fun resolve(raw: String): Outcome {
        val q = raw.trim()
        if (looksLikeNumber(q)) return Outcome(Hit(q, q.filter { it.isDigit() || it == '+' }), false, false)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return Outcome(null, permissionNeeded = true, notFound = false)
        }
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        )
        var first: Hit? = null
        try {
            context.contentResolver.query(
                uri,
                projection,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$q%"),
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(0) ?: continue
                    val number = c.getString(1) ?: continue
                    if (number.isBlank()) continue
                    if (first == null) first = Hit(name, number)
                    if (name.equals(q, ignoreCase = true)) {
                        return Outcome(Hit(name, number), false, false)
                    }
                }
            }
        } catch (_: SecurityException) {
            return Outcome(null, permissionNeeded = true, notFound = false)
        } catch (_: Exception) {
            return Outcome(null, permissionNeeded = false, notFound = true)
        }
        return if (first != null) Outcome(first, false, false)
        else Outcome(null, false, notFound = true)
    }

    private fun looksLikeNumber(s: String): Boolean {
        val compact = s.replace(Regex("[\\s\\-().]"), "")
        if (compact.length < 7) return false
        val digits = compact.filter { it.isDigit() }
        return digits.length >= 7 && digits.length >= compact.length - 1
    }
}
