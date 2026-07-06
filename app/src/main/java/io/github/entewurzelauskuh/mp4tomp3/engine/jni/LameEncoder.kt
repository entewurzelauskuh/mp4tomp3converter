package io.github.entewurzelauskuh.mp4tomp3.engine.jni

/**
 * Thin Kotlin wrapper over the LAME encoder, loaded from the dynamically linked
 * `liblame_jni.so` (which links against `liblame.so` — decision D1). The JNI surface is kept
 * tiny (spec §6.3); all MP4 demux/decode is done by [MediaCodecLameConverter], which feeds
 * 16-bit PCM here and streams the returned MP3 bytes to the output sink.
 *
 * Not thread-safe: each conversion creates its own instance and uses it from one thread.
 * The caller MUST [close] it (the native `lame_global_flags` is heap-allocated).
 *
 * @param sampleRate source sample rate in Hz (LAME resamples to a valid MP3 rate internally).
 * @param channels 1 (mono) or 2 (stereo); >2 is rejected upstream.
 * @param bitrateKbps CBR bitrate in kbps (192 in v1).
 */
class LameEncoder(sampleRate: Int, channels: Int, bitrateKbps: Int) {
    private var handle: Long = nativeInit(sampleRate, channels, bitrateKbps)

    init {
        check(handle != 0L) { "LAME initialisation failed (rate=$sampleRate, ch=$channels)" }
    }

    /**
     * Encode one chunk of interleaved 16-bit PCM into [out].
     * @param pcm interleaved samples (length ≥ [samplesPerChannel] * channels).
     * @param samplesPerChannel samples per channel in this chunk.
     * @param out scratch buffer to receive MP3 bytes; size it generously (≈ 1.25·samples + 7200).
     * @return number of MP3 bytes written to [out], or negative on a LAME error.
     */
    fun encode(pcm: ShortArray, samplesPerChannel: Int, out: ByteArray): Int {
        check(handle != 0L) { "encode() after close()" }
        return nativeEncode(handle, pcm, samplesPerChannel, out)
    }

    /** Flush LAME's final frame(s) into [out]; call once after the last [encode]. */
    fun flush(out: ByteArray): Int {
        check(handle != 0L) { "flush() after close()" }
        return nativeFlush(handle, out)
    }

    /** Release the native encoder. Idempotent. */
    fun close() {
        if (handle != 0L) {
            nativeClose(handle)
            handle = 0L
        }
    }

    private companion object {
        init {
            // Loading the JNI bridge pulls in liblame.so via its DT_NEEDED entry.
            System.loadLibrary("lame_jni")
        }

        @JvmStatic
        external fun nativeInit(sampleRate: Int, channels: Int, bitrateKbps: Int): Long

        @JvmStatic
        external fun nativeEncode(handle: Long, pcm: ShortArray, samplesPerChannel: Int, out: ByteArray): Int

        @JvmStatic
        external fun nativeFlush(handle: Long, out: ByteArray): Int

        @JvmStatic
        external fun nativeClose(handle: Long)
    }
}
