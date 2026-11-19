package app.olauncher.helper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.util.Log
import androidx.core.graphics.scale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal object LocalPhotos {
    suspend fun nextInFolder(context: Context, tree: Uri, lastPhoto: String, maxSide: Int): Pair<Bitmap, String>? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        try {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(
                tree, DocumentsContract.getTreeDocumentId(tree)
            )
            val imageIds = mutableListOf<String>()
            resolver.query(
                children, arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_MIME_TYPE),
                null, null, null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    if (cursor.getString(1)?.startsWith("image/") == true) imageIds.add(cursor.getString(0))
                }
            }
            imageIds.sort()
            val lastIndex = imageIds.indexOf(lastPhoto)
            for (offset in imageIds.indices) {
                ensureActive()
                val id = imageIds[(lastIndex + 1 + offset) % imageIds.size]
                val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                val bitmap = try {
                    decode(context, uri, maxSide)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("LocalPhotos", "Could not decode local wallpaper", e)
                    null
                }
                if (bitmap != null) return@withContext bitmap to id
            }
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("LocalPhotos", "Could not read the wallpaper folder", e)
            null
        }
    }

    fun decode(context: Context, uri: Uri, maxSide: Int): Bitmap? {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(resolver, uri)
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val sourceSide = maxOf(info.size.width, info.size.height)
                if (sourceSide > maxSide) {
                    val scale = maxSide.toDouble() / sourceSide
                    decoder.setTargetSize(
                        (info.size.width * scale).toInt().coerceAtLeast(1),
                        (info.size.height * scale).toInt().coerceAtLeast(1),
                    )
                }
            }
        }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        options.inSampleSize = 1
        while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize / 2 >= maxSide) {
            options.inSampleSize *= 2
        }
        options.inJustDecodeBounds = false
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: return null
        val side = maxOf(bitmap.width, bitmap.height)
        if (side <= maxSide) return bitmap
        val scale = maxSide.toDouble() / side
        return bitmap.scale((bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1)).also { bitmap.recycle() }
    }
}
