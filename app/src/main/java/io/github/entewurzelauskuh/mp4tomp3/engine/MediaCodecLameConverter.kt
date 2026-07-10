package io.github.entewurzelauskuh.mp4tomp3.engine

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import io.github.entewurzelauskuh.mp4tomp3.engine.jni.LameEncoder
import io.github.entewurzelauskuh.mp4tomp3.jobs.ConversionOptions
import io.github.entewurzelauskuh.mp4tomp3.jobs.FailureReason
import io.github.entewurzelauskuh.mp4tomp3.output.StorageErrors
import java.io.BufferedOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteOrder
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The production [AudioConverter] (decision D1): demux + decode the first audio track with
 * `MediaExtractor` + `MediaCodec` to 16-bit PCM, then encode MP3 with LAME via [LameEncoder].
 *
 * **Pipelined for throughput.** Decode and encode are the two CPU-heavy stages and were previously
 * run serially on one thread. They now run concurrently: a producer thread decodes into a bounded
 * queue of PCM chunks, and the calling thread consumes them (encode + write). Overlapping the two
 * on separate cores — and keeping both continuously busy — is substantially faster on multi-core
 * devices, at no quality cost, and still processes one file at a time (the queue policy is
 * unchanged; this is parallelism *within* a single conversion). Cancellation stops both threads
 * promptly; the service, not the converter, aborts the sink and marks the job cancelled (spec §6.4).
 *
 * Engine settings: CBR at [ConversionOptions.bitrateKbps] (issue #6), quality 2, source sample
 * rate and channel count (LAME resamples internally). Expected failures never throw — they return
 * [ConverterResult.Failure] with a categorised [FailureReason].
 */
class MediaCodecLameConverter : AudioConverter {
    override fun convert(
        context: Context,
        sourceUri: Uri,
        output: OutputStream,
        options: ConversionOptions,
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

                // Ask the decoder for 16-bit PCM. Most honour this; the pipeline still handles
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
                    bitrateKbps = options.bitrateKbps,
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
            // decodeAndEncode joins the producer before returning (even on throw), so the decoder
            // is no longer in use here; releasing it and the extractor is safe.
            encoder?.close()
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            extractor.release()
        }
    }

    /**
     * Run the decode ‖ encode pipeline. A producer thread drives [decoder], reading each decoded
     * buffer into a pooled PCM array and handing it over [queue]; this (calling) thread is the
     * consumer, encoding + writing on the fly. Returns [ConverterResult.Success] on completion or
     * cancellation; a decode/encode/format failure returns the categorised [ConverterResult.Failure].
     *
     * The producer always terminates the stream with exactly one [PipeMsg.End] or [PipeMsg.Error]
     * (or stops silently on cancel, which the consumer also observes), so the consumer never blocks
     * forever. The `finally` joins the producer before returning, so [convert]'s cleanup never races
     * the decoder.
     */
    private fun decodeAndEncode(
        decoder: MediaCodec,
        extractor: MediaExtractor,
        output: OutputStream,
        durationUs: Long,
        bitrateKbps: Int,
        onProgress: (Int) -> Unit,
        isCancelled: () -> Boolean,
        setEncoder: (LameEncoder) -> Unit,
    ): ConverterResult {
        val sink = BufferedOutputStream(output, OUTPUT_BUFFER_BYTES) // A1: batch small MP3-frame writes
        val queue = ArrayBlockingQueue<PipeMsg>(QUEUE_CAPACITY)
        val pool = ConcurrentLinkedQueue<ShortArray>() // A3: recycle PCM buffers across the pipeline
        val abort = AtomicBoolean(false)

        val producer = Thread({
            try {
                runProducer(decoder, extractor, queue, pool, abort, isCancelled)
            } catch (t: Throwable) {
                // Deliver the failure to the consumer BEFORE tripping abort — offerBlocking only
                // enqueues while abort is clear, so setting it first would drop the terminal Error
                // and hang the consumer. (The consumer also has a dead-producer safety net below.)
                offerBlocking(queue, PipeMsg.Error(reasonFor(t)), abort)
                abort.set(true)
            }
        }, "mp3-decode")
        producer.start()

        val throttler = ProgressThrottler()
        var encoder: LameEncoder? = null
        var mp3Buffer = ByteArray(0)
        var result: ConverterResult = ConverterResult.Failure(FailureReason.Unknown)

        try {
            while (true) {
                if (isCancelled()) {
                    abort.set(true)
                    result = ConverterResult.Success // service handles the cancel path
                    break
                }
                val msg = try {
                    queue.poll(POLL_MS, TimeUnit.MILLISECONDS)
                } catch (_: InterruptedException) {
                    null
                }
                when (msg) {
                    null -> {
                        // Timed out waiting for the producer. Re-check cancel, then a safety net:
                        // if the producer has exited leaving nothing more to process, stop cleanly
                        // instead of spinning forever (guards against any lost terminal message).
                        if (isCancelled()) {
                            abort.set(true)
                            result = ConverterResult.Success
                            break
                        }
                        if (!producer.isAlive() && queue.isEmpty()) {
                            result = fail(FailureReason.Unknown, null)
                            break
                        }
                        continue
                    }

                    is PipeMsg.Format -> {
                        encoder = LameEncoder(msg.sampleRate, msg.channels, bitrateKbps).also(setEncoder)
                    }

                    is PipeMsg.Pcm -> {
                        val enc = encoder
                        if (enc == null) {
                            result = fail(FailureReason.Unknown, null)
                            abort.set(true)
                            break
                        }
                        mp3Buffer = ensureCapacity(mp3Buffer, msg.samplesPerChannel)
                        val encoded = enc.encode(msg.data, msg.samplesPerChannel, mp3Buffer)
                        pool.offer(msg.data) // return the buffer for reuse (A3)
                        if (encoded < 0) {
                            result = fail(FailureReason.Unknown, null)
                            abort.set(true)
                            break
                        }
                        if (encoded > 0) sink.write(mp3Buffer, 0, encoded)
                        if (durationUs > 0) {
                            throttler.onProgress((msg.ptsUs * 100 / durationUs).toInt())?.let(onProgress)
                        }
                    }

                    is PipeMsg.End -> {
                        val enc = encoder
                        if (enc == null) {
                            // End-of-stream with no encoder means no audio was decoded — a zero-byte
                            // MP3 would otherwise be reported as a (false) success.
                            result = fail(FailureReason.Unknown, null)
                            break
                        }
                        val tail = ByteArray(FLUSH_BUFFER_BYTES)
                        val flushed = enc.flush(tail)
                        if (flushed < 0) {
                            result = fail(FailureReason.Unknown, null)
                            break
                        }
                        if (flushed > 0) sink.write(tail, 0, flushed)
                        sink.flush()
                        throttler.onProgress(100)?.let(onProgress)
                        result = ConverterResult.Success
                        break
                    }

                    is PipeMsg.Error -> {
                        result = ConverterResult.Failure(msg.reason)
                        break
                    }
                }
            }
        } finally {
            // Stop the producer and wait for it to release its hold on the decoder before returning,
            // so convert()'s finally can safely stop/release the decoder. join() must complete even
            // if this thread is interrupted (else the decoder could be released mid-use).
            abort.set(true)
            while (producer.isAlive()) {
                try {
                    producer.join()
                } catch (_: InterruptedException) {
                    // Keep waiting: the producer must finish with the decoder first.
                }
            }
        }
        return result
    }

    /**
     * Producer: drive [decoder] and hand decoded PCM chunks to the consumer over [queue], stopping
     * on [abort] or cancellation. Emits exactly one [PipeMsg.Format] before the first PCM, then a
     * [PipeMsg.End] at end-of-stream (or a [PipeMsg.Error] and stops if the format is unsupported).
     */
    private fun runProducer(
        decoder: MediaCodec,
        extractor: MediaExtractor,
        queue: ArrayBlockingQueue<PipeMsg>,
        pool: ConcurrentLinkedQueue<ShortArray>,
        abort: AtomicBoolean,
        isCancelled: () -> Boolean,
    ) {
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var formatSent = false
        var channels = 0
        var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT

        // Emit the format once (from the decoder's OUTPUT format, the authoritative sample rate /
        // channel count / PCM encoding). Returns false (and enqueues an Error) if it can't be encoded.
        fun sendFormat(format: MediaFormat): Boolean {
            if (formatSent) return true
            val ch = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            if (ch > MAX_CHANNELS) {
                offerBlocking(queue, PipeMsg.Error(FailureReason.UnsupportedChannelLayout), abort)
                return false
            }
            val enc = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                format.getInteger(MediaFormat.KEY_PCM_ENCODING)
            } else {
                AudioFormat.ENCODING_PCM_16BIT
            }
            if (enc != AudioFormat.ENCODING_PCM_16BIT && enc != AudioFormat.ENCODING_PCM_FLOAT) {
                offerBlocking(queue, PipeMsg.Error(FailureReason.UnsupportedAudioCodec), abort)
                return false
            }
            channels = ch
            pcmEncoding = enc
            offerBlocking(queue, PipeMsg.Format(format.getInteger(MediaFormat.KEY_SAMPLE_RATE), ch), abort)
            formatSent = true
            return true
        }

        while (!abort.get()) {
            if (isCancelled()) {
                abort.set(true)
                return
            }

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

            val outIndex = decoder.dequeueOutputBuffer(info, TIMEOUT_US)
            when {
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED ->
                    if (!sendFormat(decoder.outputFormat)) return

                outIndex >= 0 -> {
                    val isEos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    if (info.size > 0) {
                        // Some codecs deliver PCM before signalling the format change, so ensure the
                        // format has been sent here too rather than dropping the samples.
                        if (!sendFormat(decoder.outputFormat)) {
                            decoder.releaseOutputBuffer(outIndex, false)
                            return
                        }
                        val bytesPerSample = if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) 4 else 2
                        val totalSamples = info.size / bytesPerSample
                        val buf = takePooled(pool, totalSamples)
                        fillPcm(decoder, outIndex, info, pcmEncoding, buf, totalSamples)
                        val ptsUs = info.presentationTimeUs
                        decoder.releaseOutputBuffer(outIndex, false)
                        val ch = if (channels > 0) channels else 1
                        if (!offerBlocking(queue, PipeMsg.Pcm(buf, totalSamples / ch, ptsUs), abort)) return
                    } else {
                        decoder.releaseOutputBuffer(outIndex, false)
                    }
                    if (isEos) {
                        offerBlocking(queue, PipeMsg.End, abort)
                        return
                    }
                }

                // INFO_TRY_AGAIN_LATER / INFO_OUTPUT_BUFFERS_CHANGED: nothing to do.
            }
        }
    }

    /** Take a recycled PCM buffer of at least [minSize] shorts, else allocate one (A3). */
    private fun takePooled(pool: ConcurrentLinkedQueue<ShortArray>, minSize: Int): ShortArray {
        val buf = pool.poll()
        return if (buf != null && buf.size >= minSize) buf else ShortArray(minSize)
    }

    /**
     * Copy one decoder output buffer into [buf] as interleaved 16-bit PCM (filling
     * `buf[0 until totalSamples]`). If the decoder emitted float PCM ([AudioFormat.ENCODING_PCM_FLOAT])
     * despite our request, convert (clamp) to 16-bit so LAME never receives reinterpreted bytes.
     */
    private fun fillPcm(
        decoder: MediaCodec,
        index: Int,
        info: MediaCodec.BufferInfo,
        pcmEncoding: Int,
        buf: ShortArray,
        totalSamples: Int,
    ) {
        val bb = decoder.getOutputBuffer(index)!!
        bb.position(info.offset)
        bb.limit(info.offset + info.size)
        bb.order(ByteOrder.LITTLE_ENDIAN)
        if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
            val fb = bb.asFloatBuffer()
            for (i in 0 until totalSamples) {
                buf[i] = (fb.get(i) * PCM_SHORT_MAX).coerceIn(PCM_SHORT_MIN, PCM_SHORT_MAX).toInt().toShort()
            }
        } else {
            bb.asShortBuffer().get(buf, 0, totalSamples)
        }
    }

    /** Enqueue [msg], blocking on backpressure until delivered; returns false if [abort] is set first. */
    private fun offerBlocking(queue: ArrayBlockingQueue<PipeMsg>, msg: PipeMsg, abort: AtomicBoolean): Boolean {
        while (!abort.get()) {
            try {
                if (queue.offer(msg, POLL_MS, TimeUnit.MILLISECONDS)) return true
            } catch (_: InterruptedException) {
                return false
            }
        }
        return false
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

    private fun reasonFor(t: Throwable): FailureReason = if (t is IOException && StorageErrors.isOutOfSpace(t)) FailureReason.StorageFull else FailureReason.Unknown

    private fun fail(reason: FailureReason, cause: Throwable?): ConverterResult {
        if (reason == FailureReason.Unknown) {
            Log.e(TAG, "Conversion failed", cause)
        }
        return ConverterResult.Failure(reason)
    }

    private companion object {
        const val TAG = "MediaCodecLame"
        const val MAX_CHANNELS = 2
        const val TIMEOUT_US = 10_000L
        const val FLUSH_BUFFER_BYTES = 7200

        /** Bounded PCM chunks in flight between decode and encode (backpressure). */
        const val QUEUE_CAPACITY = 8

        /** Output buffering (A1): coalesce many small MP3-frame writes into larger FD writes. */
        const val OUTPUT_BUFFER_BYTES = 64 * 1024

        /** Poll/offer timeout: bounds how long either thread waits before re-checking cancel/abort. */
        const val POLL_MS = 20L

        // Float PCM is in [-1, 1]; scale to signed 16-bit and clamp.
        const val PCM_SHORT_MAX = 32767f
        const val PCM_SHORT_MIN = -32768f
    }
}

/** Producer→consumer messages for the decode ‖ encode pipeline. */
private sealed interface PipeMsg {
    class Format(val sampleRate: Int, val channels: Int) : PipeMsg
    class Pcm(val data: ShortArray, val samplesPerChannel: Int, val ptsUs: Long) : PipeMsg
    object End : PipeMsg
    class Error(val reason: FailureReason) : PipeMsg
}
