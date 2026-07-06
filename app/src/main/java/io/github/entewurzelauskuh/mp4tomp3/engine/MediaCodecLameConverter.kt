package io.github.entewurzelauskuh.mp4tomp3.engine

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import io.github.entewurzelauskuh.mp4tomp3.engine.jni.LameEncoder
import io.github.entewurzelauskuh.mp4tomp3.jobs.FailureReason
import io.github.entewurzelauskuh.mp4tomp3.output.StorageErrors
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteOrder

/**
 * The production [AudioConverter] (decision D1): demux + decode the first audio track with
 * `MediaExtractor` + `MediaCodec` to 16-bit PCM, then encode MP3 with LAME via
 * [LameEncoder]. Blocking; the service calls it off the main thread.
 *
 * Fixed engine settings (spec §6.3): CBR [BITRATE_KBPS] kbps, quality 2, source sample rate
 * and channel count (LAME resamples internally). Expected failures never throw — they return
 * [ConverterResult.Failure] with a categorised [FailureReason].
 */
class MediaCodecLameConverter : AudioConverter {
    override fun convert(
        context: Context,
        sourceUri: Uri,
        output: OutputStream,
        onProgress: (percent: Int) -> Unit,
        isCancelled: () -> Boolean,
    ): ConverterResult {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var encoder: LameEncoder? = null
        try {
            val fd = try {
                context.contentResolver.openFileDescriptor(sourceUri, "r")
            } catch (e: FileNotFoundException) {
                return fail(FailureReason.SourceUnreadable, e)
            } catch (e: SecurityException) {
                return fail(FailureReason.SourceUnreadable, e)
            } ?: return fail(FailureReason.SourceUnreadable, null)

            fd.use { pfd ->
                try {
                    extractor.setDataSource(pfd.fileDescriptor)
                } catch (e: IOException) {
                    return fail(FailureReason.SourceUnreadable, e)
                }

                val trackIndex = firstAudioTrack(extractor)
                    ?: return ConverterResult.Failure(FailureReason.NoAudioTrack)
                extractor.selectTrack(trackIndex)
                val inputFormat = extractor.getTrackFormat(trackIndex)
                val mime = inputFormat.getString(MediaFormat.KEY_MIME)
                    ?: return ConverterResult.Failure(FailureReason.UnsupportedAudioCodec)
                val durationUs =
                    if (inputFormat.containsKey(MediaFormat.KEY_DURATION)) {
                        inputFormat.getLong(MediaFormat.KEY_DURATION)
                    } else {
                        0L
                    }

                // Ask the decoder for 16-bit PCM. Most honour this; the decode loop still handles
                // ENCODING_PCM_FLOAT defensively in case a codec emits floats anyway.
                inputFormat.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)

                decoder = try {
                    MediaCodec.createDecoderByType(mime).apply {
                        configure(inputFormat, null, null, 0)
                        start()
                    }
                } catch (e: Exception) {
                    return fail(FailureReason.UnsupportedAudioCodec, e)
                }

                return decodeAndEncode(
                    decoder = decoder!!,
                    extractor = extractor,
                    output = output,
                    durationUs = durationUs,
                    onProgress = onProgress,
                    isCancelled = isCancelled,
                    setEncoder = { encoder = it },
                )
            }
        } catch (e: IOException) {
            // Write failures surface here; ENOSPC is the only one we can name.
            val reason = if (StorageErrors.isOutOfSpace(e)) FailureReason.StorageFull else FailureReason.Unknown
            return fail(reason, e)
        } catch (e: Exception) {
            return fail(FailureReason.Unknown, e)
        } finally {
            encoder?.close()
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            extractor.release()
        }
    }

    /**
     * Standard synchronous MediaCodec decode loop feeding LAME. The encoder is created lazily
     * once the decoder's OUTPUT format reveals the true sample rate / channel count (which can
     * differ from the input format). Returns [ConverterResult.Success] on completion OR on
     * cancellation (the service, not the converter, aborts the sink and marks the job
     * cancelled — see spec §6.4).
     */
    private fun decodeAndEncode(
        decoder: MediaCodec,
        extractor: MediaExtractor,
        output: OutputStream,
        durationUs: Long,
        onProgress: (Int) -> Unit,
        isCancelled: () -> Boolean,
        setEncoder: (LameEncoder) -> Unit,
    ): ConverterResult {
        val throttler = ProgressThrottler()
        val info = MediaCodec.BufferInfo()
        var encoder: LameEncoder? = null
        var channels = 0
        var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
        var mp3Buffer = ByteArray(0)
        var inputDone = false

        // Create the LAME encoder from the decoder's OUTPUT format (the authoritative sample
        // rate / channel count / PCM encoding, which can differ from the input). Returns a
        // [FailureReason] to abort, or null once the encoder exists.
        fun ensureEncoder(format: MediaFormat): FailureReason? {
            if (encoder != null) return null
            val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            if (channelCount > MAX_CHANNELS) return FailureReason.UnsupportedChannelLayout
            val encoding = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                format.getInteger(MediaFormat.KEY_PCM_ENCODING)
            } else {
                AudioFormat.ENCODING_PCM_16BIT
            }
            if (encoding != AudioFormat.ENCODING_PCM_16BIT && encoding != AudioFormat.ENCODING_PCM_FLOAT) {
                return FailureReason.UnsupportedAudioCodec
            }
            channels = channelCount
            pcmEncoding = encoding
            encoder = LameEncoder(format.getInteger(MediaFormat.KEY_SAMPLE_RATE), channelCount, BITRATE_KBPS)
                .also(setEncoder)
            return null
        }

        while (true) {
            if (isCancelled()) return ConverterResult.Success // service handles the cancel path

            if (!inputDone) {
                val inIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                if (inIndex >= 0) {
                    val inBuf = decoder.getInputBuffer(inIndex)!!
                    val sampleSize = extractor.readSampleData(inBuf, 0)
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        decoder.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            when (val outIndex = decoder.dequeueOutputBuffer(info, TIMEOUT_US)) {
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED ->
                    ensureEncoder(decoder.outputFormat)?.let { return ConverterResult.Failure(it) }

                // No output ready yet.
                MediaCodec.INFO_TRY_AGAIN_LATER -> Unit

                // Deprecated no-op on modern APIs.
                MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit

                else -> {
                    if (outIndex >= 0) {
                        if (info.size > 0) {
                            // Some codecs deliver PCM before signalling the format change, so
                            // initialise the encoder here too rather than dropping the samples.
                            ensureEncoder(decoder.outputFormat)?.let {
                                decoder.releaseOutputBuffer(outIndex, false)
                                return ConverterResult.Failure(it)
                            }
                            val pcm = readPcm(decoder, outIndex, info, pcmEncoding)
                            val samplesPerChannel = pcm.size / channels
                            mp3Buffer = ensureCapacity(mp3Buffer, samplesPerChannel)
                            val encoded = encoder!!.encode(pcm, samplesPerChannel, mp3Buffer)
                            if (encoded < 0) {
                                decoder.releaseOutputBuffer(outIndex, false)
                                return fail(FailureReason.Unknown, null)
                            }
                            if (encoded > 0) output.write(mp3Buffer, 0, encoded)
                        }
                        decoder.releaseOutputBuffer(outIndex, false)

                        if (durationUs > 0) {
                            val pct = (info.presentationTimeUs * 100 / durationUs).toInt()
                            throttler.onProgress(pct)?.let(onProgress)
                        }

                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            return finish(encoder, output, onProgress, throttler)
                        }
                    }
                }
            }
        }
    }

    /** Flush LAME's trailing frames, report 100%, and return success. */
    private fun finish(
        encoder: LameEncoder?,
        output: OutputStream,
        onProgress: (Int) -> Unit,
        throttler: ProgressThrottler,
    ): ConverterResult {
        // No encoder at end-of-stream means no audio was ever decoded — a zero-byte MP3 would
        // otherwise be reported as a (false) success.
        if (encoder == null) return fail(FailureReason.Unknown, null)
        val tail = ByteArray(FLUSH_BUFFER_BYTES)
        val flushed = encoder.flush(tail)
        if (flushed < 0) return fail(FailureReason.Unknown, null)
        if (flushed > 0) output.write(tail, 0, flushed)
        output.flush()
        throttler.onProgress(100)?.let(onProgress)
        return ConverterResult.Success
    }

    /**
     * Read one output buffer as interleaved 16-bit PCM. If the decoder emitted float PCM
     * ([AudioFormat.ENCODING_PCM_FLOAT]) despite our request, convert (clamp) to 16-bit so LAME
     * never receives garbage reinterpreted bytes.
     */
    private fun readPcm(
        decoder: MediaCodec,
        index: Int,
        info: MediaCodec.BufferInfo,
        pcmEncoding: Int,
    ): ShortArray {
        val buffer = decoder.getOutputBuffer(index)!!
        buffer.position(info.offset)
        buffer.limit(info.offset + info.size)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        return if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
            val floats = buffer.asFloatBuffer()
            ShortArray(floats.remaining()) { i ->
                (floats.get(i) * PCM_SHORT_MAX).coerceIn(PCM_SHORT_MIN, PCM_SHORT_MAX).toInt().toShort()
            }
        } else {
            val shorts = buffer.asShortBuffer()
            ShortArray(shorts.remaining()).also { shorts.get(it) }
        }
    }

    /** LAME needs ≈ 1.25·samples + 7200 bytes of headroom; grow the scratch buffer as needed. */
    private fun ensureCapacity(current: ByteArray, samplesPerChannel: Int): ByteArray {
        val needed = (samplesPerChannel * 5 / 4) + 7200
        return if (current.size >= needed) current else ByteArray(needed)
    }

    private fun firstAudioTrack(extractor: MediaExtractor): Int? {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("audio/") == true) return i
        }
        return null
    }

    private fun fail(reason: FailureReason, cause: Throwable?): ConverterResult {
        if (reason == FailureReason.Unknown) {
            Log.e(TAG, "Conversion failed", cause)
        }
        return ConverterResult.Failure(reason)
    }

    private companion object {
        const val TAG = "MediaCodecLame"
        const val BITRATE_KBPS = 192
        const val MAX_CHANNELS = 2
        const val TIMEOUT_US = 10_000L
        const val FLUSH_BUFFER_BYTES = 7200

        // Float PCM is in [-1, 1]; scale to signed 16-bit and clamp.
        const val PCM_SHORT_MAX = 32767f
        const val PCM_SHORT_MIN = -32768f
    }
}
