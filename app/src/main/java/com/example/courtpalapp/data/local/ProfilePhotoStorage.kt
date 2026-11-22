package com.example.courtpalapp.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

private fun profilePhotoFolder(context: Context): File =
    File(context.filesDir, "profile_photos").apply {
        mkdirs()
    }

fun copyProfilePhoto(
    context: Context,
    uri: Uri
): String {
    val file = File(
        profilePhotoFolder(context),
        "${UUID.randomUUID()}.jpg"
    )

    try {
        val bitmap = context.contentResolver
            .openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: error("Unable to read image")

        FileOutputStream(file).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output))
        }

        return file.absolutePath
    } catch (e: Exception) {
        file.delete()
        throw e
    }
}

fun saveProfileBitmap(
    context: Context,
    bitmap: Bitmap
): String {
    val file = File(
        profilePhotoFolder(context),
        "${UUID.randomUUID()}.jpg"
    )

    try {
        FileOutputStream(file).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output))
        }

        return file.absolutePath
    } catch (e: Exception) {
        file.delete()
        throw e
    }
}

fun deleteProfilePhoto(
    context: Context,
    path: String
) {
    val folder = profilePhotoFolder(context).canonicalFile
    val file = File(path).canonicalFile

    if (file.parentFile == folder) {
        file.delete()
    }
}