/*
 * JNI bridge between io.github.entewurzelauskuh.mp4tomp3.engine.jni.LameEncoder and the
 * vendored LAME encoder (liblame.so). Deliberately tiny — the exact surface fixed in
 * spec §6.3: nativeInit / nativeEncode / nativeFlush / nativeClose. All MP4 demux/decode
 * happens on the Kotlin side (MediaExtractor + MediaCodec); this only encodes PCM → MP3.
 *
 * Engine defaults are fixed (spec §6.3, D1): CBR at the requested bitrate, quality 2,
 * source sample rate and channel count. Never VBR in v1.
 */
#include <jni.h>
#include <stdint.h>
#include "lame.h"

#define LAME_HANDLE(ptr) ((lame_global_flags *) (intptr_t) (ptr))

JNIEXPORT jlong JNICALL
Java_io_github_entewurzelauskuh_mp4tomp3_engine_jni_LameEncoder_nativeInit(
        JNIEnv *env, jclass clazz, jint sampleRate, jint channels, jint bitrateKbps) {
    lame_global_flags *gfp = lame_init();
    if (gfp == NULL) {
        return 0;
    }
    lame_set_in_samplerate(gfp, sampleRate);
    lame_set_num_channels(gfp, channels);
    if (channels == 1) {
        lame_set_mode(gfp, MONO);
    }
    lame_set_brate(gfp, bitrateKbps);   /* CBR */
    lame_set_VBR(gfp, vbr_off);         /* explicitly no VBR (avoids Xing seek rewrite) */
    lame_set_quality(gfp, 2);
    if (lame_init_params(gfp) < 0) {
        lame_close(gfp);
        return 0;
    }
    return (jlong) (intptr_t) gfp;
}

JNIEXPORT jint JNICALL
Java_io_github_entewurzelauskuh_mp4tomp3_engine_jni_LameEncoder_nativeEncode(
        JNIEnv *env, jclass clazz, jlong handle, jshortArray pcm,
        jint samplesPerChannel, jbyteArray out) {
    lame_global_flags *gfp = LAME_HANDLE(handle);
    if (gfp == NULL) {
        return -1;
    }

    jshort *pcmPtr = (*env)->GetShortArrayElements(env, pcm, NULL);
    jbyte *outPtr = (*env)->GetByteArrayElements(env, out, NULL);
    if (pcmPtr == NULL || outPtr == NULL) {
        if (pcmPtr) (*env)->ReleaseShortArrayElements(env, pcm, pcmPtr, JNI_ABORT);
        if (outPtr) (*env)->ReleaseByteArrayElements(env, out, outPtr, JNI_ABORT);
        return -1;
    }
    const jsize outCapacity = (*env)->GetArrayLength(env, out);
    const int channels = lame_get_num_channels(gfp);

    /* Guard against a caller whose sample count would read past the PCM array (out-of-bounds
     * native read). samplesPerChannel * channels must fit within the array length. */
    const jsize pcmLen = (*env)->GetArrayLength(env, pcm);
    if (samplesPerChannel < 0 || (jlong) samplesPerChannel * channels > pcmLen) {
        (*env)->ReleaseShortArrayElements(env, pcm, pcmPtr, JNI_ABORT);
        (*env)->ReleaseByteArrayElements(env, out, outPtr, JNI_ABORT);
        return -1;
    }

    int written;
    if (channels == 1) {
        /* Mono: LAME reads only the left buffer; pass the same pointer for right. */
        written = lame_encode_buffer(
                gfp, pcmPtr, pcmPtr, samplesPerChannel,
                (unsigned char *) outPtr, outCapacity);
    } else {
        written = lame_encode_buffer_interleaved(
                gfp, pcmPtr, samplesPerChannel,
                (unsigned char *) outPtr, outCapacity);
    }

    (*env)->ReleaseShortArrayElements(env, pcm, pcmPtr, JNI_ABORT); /* PCM not modified */
    /* Commit MP3 bytes back to the Java array (mode 0). */
    (*env)->ReleaseByteArrayElements(env, out, outPtr, 0);
    return written;
}

JNIEXPORT jint JNICALL
Java_io_github_entewurzelauskuh_mp4tomp3_engine_jni_LameEncoder_nativeFlush(
        JNIEnv *env, jclass clazz, jlong handle, jbyteArray out) {
    lame_global_flags *gfp = LAME_HANDLE(handle);
    if (gfp == NULL) {
        return -1;
    }
    jbyte *outPtr = (*env)->GetByteArrayElements(env, out, NULL);
    if (outPtr == NULL) {
        return -1;
    }
    const jsize outCapacity = (*env)->GetArrayLength(env, out);
    int written = lame_encode_flush(gfp, (unsigned char *) outPtr, outCapacity);
    (*env)->ReleaseByteArrayElements(env, out, outPtr, 0);
    return written;
}

JNIEXPORT void JNICALL
Java_io_github_entewurzelauskuh_mp4tomp3_engine_jni_LameEncoder_nativeClose(
        JNIEnv *env, jclass clazz, jlong handle) {
    lame_global_flags *gfp = LAME_HANDLE(handle);
    if (gfp != NULL) {
        lame_close(gfp);
    }
}
