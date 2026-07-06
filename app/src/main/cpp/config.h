/*
 * Minimal LAME build configuration for the Android NDK (Bionic libc).
 *
 * LAME normally generates config.h via autotools `configure`, which we don't run for a
 * CMake/NDK build. This hand-written file lives OUTSIDE third_party/lame/ so the vendored
 * LAME sources stay unmodified (see third_party/lame/README.vendored.md); CMake puts this
 * directory on the include path and defines HAVE_CONFIG_H so the LAME sources pick it up.
 *
 * Deliberately absent: HAVE_MPGLIB (we decode with Android MediaCodec, so LAME's decoder is
 * not built — mpglib_interface.c is wrapped in #ifdef HAVE_MPGLIB and compiles to nothing)
 * and HAVE_XMMINTRIN_H (no x86 SSE intrinsics; vector/xmm_quantize_sub.c likewise empties).
 */
#ifndef MP4TOMP3_LAME_CONFIG_H
#define MP4TOMP3_LAME_CONFIG_H

#define PACKAGE "lame"
#define VERSION "3.100"
#define LAME_URL "https://lame.sourceforge.io/"

/* Standard headers, all present in the NDK sysroot. */
#define STDC_HEADERS 1
#define HAVE_SYS_TYPES_H 1
#define HAVE_SYS_STAT_H 1
#define HAVE_STDLIB_H 1
#define HAVE_STDINT_H 1
#define HAVE_INTTYPES_H 1
#define HAVE_STRING_H 1
#define HAVE_STRINGS_H 1
#define HAVE_UNISTD_H 1
#define HAVE_LIMITS_H 1
#define HAVE_FCNTL_H 1
#define HAVE_ERRNO_H 1

/* Library functions used by LAME. */
#define HAVE_MEMCPY 1
#define HAVE_MEMSET 1
#define HAVE_STRCHR 1

/* 64-bit integer support (Bionic has long long everywhere). */
#define HAVE_LONG_LONG 1

/*
 * LAME's util.h uses these IEEE-754 float typedefs, which its `configure` normally emits into
 * config.h. All our ABIs use IEEE-754 `float`/`double`, so map them directly.
 */
#define ieee754_float32_t float
#define ieee754_float64_t double

/*
 * Fast float→int rounding via IEEE-754 bit tricks. Valid on all our ABIs (arm64-v8a,
 * armeabi-v7a, x86_64), which are little-endian IEEE-754.
 */
#define TAKEHIRO_IEEE754_HACK 1

#endif /* MP4TOMP3_LAME_CONFIG_H */
