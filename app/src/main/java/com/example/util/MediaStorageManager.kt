package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale

object MediaStorageManager {

    private const val PREFS_NAME = "notifai_security_prefs"
    private const val KEY_SAVE_MEDIA = "save_media_in_history"
    private const val MEDIA_DIR_NAME = "chat_media"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isMediaSavingEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_SAVE_MEDIA, true)
    }

    fun setMediaSavingEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SAVE_MEDIA, enabled).apply()
    }

    fun getMediaDir(context: Context): File {
        val dir = File(context.filesDir, MEDIA_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Persists media (from a URI or raw bytes) into internal app storage for persistent chat history.
     * Returns the permanent file path (or file:// URI) if saved, or null if media saving is disabled.
     */
    fun persistMediaForChat(
        context: Context,
        sourceUri: Uri? = null,
        directBytes: ByteArray? = null,
        mimeType: String? = null,
        originalName: String? = null
    ): String? {
        if (!isMediaSavingEnabled(context)) {
            return null
        }

        try {
            val mediaDir = getMediaDir(context)
            val extension = when {
                mimeType?.contains("jpeg") == true || mimeType?.contains("jpg") == true -> ".jpg"
                mimeType?.contains("png") == true -> ".png"
                mimeType?.contains("webp") == true -> ".webp"
                mimeType?.contains("audio") == true || mimeType?.contains("m4a") == true -> ".m4a"
                mimeType?.contains("mp3") == true -> ".mp3"
                mimeType?.contains("wav") == true -> ".wav"
                mimeType?.contains("pdf") == true -> ".pdf"
                originalName?.contains(".") == true -> ".${originalName.substringAfterLast('.')}"
                else -> ".bin"
            }

            val sanitizedName = (originalName?.substringBeforeLast('.') ?: "attachment")
                .replace(Regex("[^a-zA-Z0-9_-]"), "_")
                .take(30)
            val fileName = "chat_${System.currentTimeMillis()}_${sanitizedName}$extension"
            val targetFile = File(mediaDir, fileName)

            if (directBytes != null && directBytes.isNotEmpty()) {
                targetFile.writeBytes(directBytes)
                return targetFile.absolutePath
            }

            if (sourceUri != null) {
                // If it's already a file inside our mediaDir, keep it
                if (sourceUri.scheme == "file") {
                    val srcFile = File(sourceUri.path ?: "")
                    if (srcFile.exists()) {
                        srcFile.copyTo(targetFile, overwrite = true)
                        return targetFile.absolutePath
                    }
                }

                // Copy from content resolver
                var inputStream: InputStream? = null
                var outputStream: FileOutputStream? = null
                try {
                    inputStream = context.contentResolver.openInputStream(sourceUri)
                    if (inputStream != null) {
                        outputStream = FileOutputStream(targetFile)
                        inputStream.copyTo(outputStream)
                        return targetFile.absolutePath
                    }
                } finally {
                    inputStream?.close()
                    outputStream?.close()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return null
    }

    /**
     * Calculates total size of saved media files in bytes.
     */
    fun getTotalSavedMediaSizeBytes(context: Context): Long {
        val mediaDir = File(context.filesDir, MEDIA_DIR_NAME)
        if (!mediaDir.exists() || !mediaDir.isDirectory) return 0L
        return mediaDir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    /**
     * Formats bytes to human-readable string (e.g. 2.4 MB, 450 KB).
     */
    fun formatMediaSize(bytes: Long): String {
        return when {
            bytes <= 0L -> "0 קבצים (0 KB)"
            bytes < 1024L * 1024L -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
            else -> String.format(Locale.getDefault(), "%.2f MB", bytes / (1024.0 * 1024.0))
        }
    }

    /**
     * Clears all saved chat media files to free up device storage.
     */
    fun clearAllSavedMedia(context: Context): Long {
        val mediaDir = File(context.filesDir, MEDIA_DIR_NAME)
        if (!mediaDir.exists() || !mediaDir.isDirectory) return 0L
        var freedBytes = 0L
        mediaDir.listFiles()?.forEach { file ->
            freedBytes += file.length()
            file.delete()
        }
        return freedBytes
    }

    /**
     * Returns a shareable / viewable content URI via FileProvider for local files.
     */
    fun getShareableUri(context: Context, pathOrUriStr: String): Uri? {
        return try {
            val file = if (pathOrUriStr.startsWith("/")) {
                File(pathOrUriStr)
            } else if (pathOrUriStr.startsWith("file://")) {
                File(Uri.parse(pathOrUriStr).path ?: "")
            } else {
                null
            }

            if (file != null && file.exists()) {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            } else {
                Uri.parse(pathOrUriStr)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Builds an ACTION_VIEW Intent to open or inspect the media file.
     */
    fun createViewMediaIntent(context: Context, pathOrUriStr: String, mimeType: String?): Intent? {
        val uri = getShareableUri(context, pathOrUriStr) ?: return null
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
