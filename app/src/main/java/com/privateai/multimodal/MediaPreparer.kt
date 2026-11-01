package com.privateai.multimodal

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayInputStream
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.privateai.llm.MediaKind
import com.privateai.llm.MediaPart
import java.io.ByteArrayOutputStream
import java.io.File

/** Result of turning an attachment into something the model can consume. */
class PreparedMedia(val parts: List<MediaPart>, val inlineText: String?)

/** Converts picked files (image, video, audio, text) into model inputs. */
class MediaPreparer(private val context: Context) {

    companion object {
        private const val MAX_IMAGE_SIDE = 1280
        private const val VIDEO_FRAMES = 6
        private const val MAX_FILE_BYTES = 25 * 1024 * 1024
        private const val MAX_TEXT_CHARS = 6000
        private val TEXT_TYPES = listOf("text/", "application/json", "application/xml", "application/x-yaml")
    }

    /** Prepares a picked file without loading videos into memory; throws if a non-video file is too large. */
    fun prepareUri(name: String, mimeType: String, uri: Uri): PreparedMedia {
        if (mimeType.startsWith("video/")) {
            val retriever = MediaMetadataRetriever()
            return try {
                retriever.setDataSource(context, uri)
                videoFrames(retriever)
            } catch (e: Exception) {
                PreparedMedia(emptyList(), "[Video could not be read.]")
            } finally {
                retriever.release()
            }
        }
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                if (out.size() > MAX_FILE_BYTES) {
                    return PreparedMedia(emptyList(), "[The file \"$name\" is too large to read on this device.]")
                }
            }
            out.toByteArray()
        } ?: return PreparedMedia(emptyList(), "[The file \"$name\" could not be opened.]")
        return prepare(name, mimeType, bytes)
    }

    fun prepare(name: String, mimeType: String, data: ByteArray): PreparedMedia = when {
        mimeType.startsWith("image/") -> imageParts(data)
        mimeType.startsWith("video/") -> videoParts(data)
        mimeType.startsWith("audio/") -> PreparedMedia(listOf(MediaPart(MediaKind.AUDIO, data)), null)
        TEXT_TYPES.any { mimeType.startsWith(it) } -> textFile(name, data)
        else -> PreparedMedia(emptyList(), "[The file \"$name\" has a type ($mimeType) I cannot read.]")
    }

    private fun imageParts(data: ByteArray): PreparedMedia {
        val bitmap = decodeScaled(data) ?: return PreparedMedia(emptyList(), "[Image could not be decoded.]")
        return PreparedMedia(listOf(MediaPart(MediaKind.IMAGE, toJpeg(bitmap))), null)
    }

    private fun videoParts(data: ByteArray): PreparedMedia {
        val temp = File.createTempFile("vid", ".tmp", context.cacheDir)
        val retriever = MediaMetadataRetriever()
        return try {
            temp.writeBytes(data)
            retriever.setDataSource(temp.absolutePath)
            videoFrames(retriever)
        } catch (e: Exception) {
            PreparedMedia(emptyList(), "[Video could not be read.]")
        } finally {
            retriever.release()
            temp.delete()
        }
    }

    private fun videoFrames(retriever: MediaMetadataRetriever): PreparedMedia {
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull() ?: 0L
        val times = (0 until VIDEO_FRAMES).map { i -> durationMs * (i + 1) / (VIDEO_FRAMES + 1) }
        val frames = times.mapNotNull { atMs ->
            retriever.getFrameAtTime(atMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?.let { MediaPart(MediaKind.IMAGE, toJpeg(scale(it))) }
        }
        if (frames.isEmpty()) return PreparedMedia(emptyList(), "[Video could not be read.]")
        val stamps = times.joinToString(", ") { "%d:%02d".format(it / 60000, (it / 1000) % 60) }
        val text = "[The attached video is ${durationMs / 1000} seconds long. The ${frames.size} images are frames " +
            "in order, taken at $stamps. Describe what happens in the video from these frames.]"
        return PreparedMedia(frames, text)
    }

    private fun textFile(name: String, data: ByteArray): PreparedMedia {
        val text = data.toString(Charsets.UTF_8)
        val clipped = if (text.length > MAX_TEXT_CHARS) text.take(MAX_TEXT_CHARS) + "\n...[truncated]" else text
        return PreparedMedia(emptyList(), "[Contents of file \"$name\"]\n$clipped")
    }

    private fun decodeScaled(data: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_IMAGE_SIDE) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = BitmapFactory.decodeByteArray(data, 0, data.size, opts) ?: return null
        return scale(rotateUpright(decoded, exifRotation(data)))
    }

    private fun exifRotation(data: ByteArray): Int = try {
        when (ExifInterface(ByteArrayInputStream(data))
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } catch (e: Exception) {
        0
    }

    private fun rotateUpright(src: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return src
        val m = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    private fun scale(src: Bitmap): Bitmap {
        val longest = maxOf(src.width, src.height)
        if (longest <= MAX_IMAGE_SIDE) return src
        val ratio = MAX_IMAGE_SIDE.toFloat() / longest
        return Bitmap.createScaledBitmap(src, (src.width * ratio).toInt(), (src.height * ratio).toInt(), true)
    }

    private fun toJpeg(bitmap: Bitmap): ByteArray =
        ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
}
