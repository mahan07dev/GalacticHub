package com.mahanverse.galactichub

import android.content.ContentProvider
import android.content.ContentValues
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipFile

class VideoProvider : ContentProvider() {

    companion object {
        private const val TAG = "VideoProvider"
    }

    private var obbZipFile: File? = null

    override fun onCreate(): Boolean {
        val context = context ?: return false
        Log.d(TAG, "onCreate")

        val obbDir = context.obbDir
        var obbFile: File? = null
        if (obbDir.exists()) {
            obbDir.listFiles { file -> file.name.endsWith(".obb") }?.firstOrNull()?.let {
                obbFile = it
                Log.i(TAG, "Found OBB in obbDir: ${it.absolutePath}")
            }
        }

        if (obbFile == null) {
            try {
                val versionCode = context.packageManager.getPackageInfo(context.packageName, 0).versionCode
                val manualPath = File("/storage/emulated/0/Android/obb/${context.packageName}/main.$versionCode.${context.packageName}.obb")
                if (manualPath.exists()) {
                    obbFile = manualPath
                    Log.i(TAG, "Found OBB manually: ${manualPath.absolutePath}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Manual OBB check failed", e)
            }
        }

        obbZipFile = obbFile
        return obbFile != null
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
        Log.d(TAG, "openAssetFile: $uri")
        val path = uri.path?.removePrefix("/") ?: return null
        val obb = obbZipFile ?: return null

        return try {
            // Get entry size first (open ZipFile briefly)
            val entrySize = ZipFile(obb).use { zip ->
                zip.getEntry(path)?.size ?: return null
            }
            Log.d(TAG, "Entry size: $entrySize for $path")

            val pipe = ParcelFileDescriptor.createPipe()
            val outputStream = FileOutputStream(pipe[1].fileDescriptor)

            Thread {
                try {
                    // Open ZipFile inside the thread so it stays open during streaming
                    ZipFile(obb).use { zip ->
                        val entry = zip.getEntry(path)
                        if (entry != null) {
                            zip.getInputStream(entry).use { input ->
                                input.copyTo(outputStream)
                                outputStream.flush()
                            }
                            Log.d(TAG, "Streaming finished: $path")
                        } else {
                            Log.e(TAG, "Entry not found: $path")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Streaming error", e)
                } finally {
                    try {
                        outputStream.close()
                        pipe[1].close()
                    } catch (e: IOException) {
                        Log.e(TAG, "Pipe close error", e)
                    }
                }
            }.start()

            AssetFileDescriptor(pipe[0], 0, entrySize)
        } catch (e: Exception) {
            Log.e(TAG, "openAssetFile failed", e)
            null
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        Log.d(TAG, "openFile: $uri")
        val path = uri.path?.removePrefix("/") ?: return null
        val obb = obbZipFile ?: return null

        return try {
            val pipe = ParcelFileDescriptor.createPipe()
            val outputStream = FileOutputStream(pipe[1].fileDescriptor)

            Thread {
                try {
                    ZipFile(obb).use { zip ->
                        val entry = zip.getEntry(path)
                        if (entry != null) {
                            zip.getInputStream(entry).use { input ->
                                input.copyTo(outputStream)
                                outputStream.flush()
                            }
                            Log.d(TAG, "Streaming finished (openFile): $path")
                        } else {
                            Log.e(TAG, "Entry not found: $path")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Streaming error", e)
                } finally {
                    try {
                        outputStream.close()
                        pipe[1].close()
                    } catch (e: IOException) {
                        Log.e(TAG, "Pipe close error", e)
                    }
                }
            }.start()

            pipe[0]
        } catch (e: Exception) {
            Log.e(TAG, "openFile failed", e)
            null
        }
    }

    override fun getType(uri: Uri): String? {
        val path = uri.path ?: return null
        return when {
            path.endsWith(".mp4", ignoreCase = true) -> "video/mp4"
            path.endsWith(".webm", ignoreCase = true) -> "video/webm"
            path.endsWith(".mkv", ignoreCase = true) -> "video/x-matroska"
            path.endsWith(".jpg", ignoreCase = true) -> "image/jpeg"
            else -> "application/octet-stream"
        }
    }

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0
}