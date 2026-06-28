package app.olauncher.helper

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import app.olauncher.data.AppModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads the device contacts so they can be searched from the app drawer. Reading
 * requires the runtime READ_CONTACTS permission; callers must check / request it.
 */
object ContactsHelper {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
                PackageManager.PERMISSION_GRANTED

    // Loads visible contacts (display name + lookup uri) once, de-duplicated by name.
    suspend fun loadContacts(context: Context): List<AppModel.Contact> = withContext(Dispatchers.IO) {
        val result = mutableListOf<AppModel.Contact>()
        if (!hasPermission(context)) return@withContext result
        val seen = HashSet<String>()
        try {
            val projection = arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.LOOKUP_KEY,
                ContactsContract.Contacts.DISPLAY_NAME,
            )
            context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                projection,
                "${ContactsContract.Contacts.DISPLAY_NAME} IS NOT NULL AND ${ContactsContract.Contacts.DISPLAY_NAME} != ''",
                null,
                "${ContactsContract.Contacts.DISPLAY_NAME} COLLATE LOCALIZED ASC",
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
                val lookupCol = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY)
                val nameCol = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameCol) ?: continue
                    if (!seen.add(name.lowercase())) continue
                    val id = cursor.getLong(idCol)
                    val lookup = cursor.getString(lookupCol) ?: continue
                    val uri = ContactsContract.Contacts.getLookupUri(id, lookup) ?: continue
                    result.add(AppModel.Contact(appLabel = name, lookupUri = uri.toString()))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        result
    }

    /** Opens the contact card for the given contact. */
    fun openContact(context: Context, contact: AppModel.Contact) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(contact.lookupUri))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            context.showToast(context.getString(app.olauncher.R.string.unable_to_open_app))
        }
    }
}
