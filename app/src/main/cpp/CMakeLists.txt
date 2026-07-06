# Native build for the MP3 engine (decision D1).
#
# Produces two SHARED libraries:
#   * liblame.so     — the vendored LAME 3.100 encoder, built as a separate, dynamically
#                      linked shared object (NEVER statically linked into app code — this is
#                      what keeps LAME's LGPL compatible with the MIT app license, see D4).
#   * liblame_jni.so — the thin JNI bridge, which links AGAINST liblame.so.
cmake_minimum_required(VERSION 3.22)
project(mp4tomp3_native C)

# third_party/lame lives at the repo root: cpp -> main -> src -> app -> <root>.
get_filename_component(
    LAME_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/lame" ABSOLUTE
)

# All encoder translation units. mpglib_interface.c (#ifdef HAVE_MPGLIB) and
# vector/xmm_quantize_sub.c (#ifdef HAVE_XMMINTRIN_H) compile to nothing under our config.h,
# so globbing them in is harmless and keeps the list maintenance-free.
file(GLOB LAME_SOURCES
    "${LAME_ROOT}/libmp3lame/*.c"
    "${LAME_ROOT}/libmp3lame/vector/*.c"
)

add_library(lame SHARED ${LAME_SOURCES})
target_include_directories(lame PRIVATE
    "${CMAKE_CURRENT_SOURCE_DIR}"        # our hand-written config.h
    "${LAME_ROOT}/include"               # public lame.h
    "${LAME_ROOT}/libmp3lame"            # internal headers
)
target_compile_definitions(lame PRIVATE HAVE_CONFIG_H)
# LAME is warning-heavy legacy C; silence it and optimise. libm is folded into Bionic libc.
target_compile_options(lame PRIVATE -Wno-everything -O2 -ffast-math)

add_library(lame_jni SHARED lame_jni.c)
target_include_directories(lame_jni PRIVATE "${LAME_ROOT}/include")
# Dynamically link the bridge against liblame.so and the Android log library.
target_link_libraries(lame_jni lame log)
