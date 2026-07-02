cmake_minimum_required(VERSION 3.22.1)
project(superdash_native)

set(WHISPER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp)
set(SUPERDASH_WHISPER_SOURCE_PRESENT OFF CACHE BOOL "Build superdash Whisper JNI against vendored whisper.cpp")
set(MICROFRONTEND_DIR
    ${CMAKE_CURRENT_SOURCE_DIR}/third_party/tflite_microfrontend/tensorflow/lite/experimental/microfrontend/lib)
set(KISSFFT_DIR ${CMAKE_CURRENT_SOURCE_DIR}/third_party/kissfft)

add_library(superdash_whisper SHARED whisper_jni.cpp)

if(SUPERDASH_WHISPER_SOURCE_PRESENT AND EXISTS "${WHISPER_DIR}/CMakeLists.txt")
    # Debug APKs are what we deploy locally, but unoptimized whisper.cpp is too slow on tablets.
    string(APPEND CMAKE_C_FLAGS_DEBUG " -O3 -DNDEBUG")
    string(APPEND CMAKE_CXX_FLAGS_DEBUG " -O3 -DNDEBUG")
    set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
    set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
    set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
    add_subdirectory(${WHISPER_DIR} whisper-build)
    target_compile_definitions(superdash_whisper PRIVATE SUPERDASH_WHISPER_REAL=1)
    target_link_libraries(superdash_whisper whisper android log)
else()
    target_compile_definitions(superdash_whisper PRIVATE SUPERDASH_WHISPER_REAL=0)
    target_link_libraries(superdash_whisper android log)
endif()

add_library(
    superdash_audio_features
    SHARED
    superdash_audio_features_jni.cpp
    ${KISSFFT_DIR}/kiss_fft.c
    ${KISSFFT_DIR}/kiss_fftr.c
    ${MICROFRONTEND_DIR}/fft.cc
    ${MICROFRONTEND_DIR}/fft_util.cc
    ${MICROFRONTEND_DIR}/filterbank.c
    ${MICROFRONTEND_DIR}/filterbank_util.c
    ${MICROFRONTEND_DIR}/frontend.c
    ${MICROFRONTEND_DIR}/frontend_util.c
    ${MICROFRONTEND_DIR}/log_lut.c
    ${MICROFRONTEND_DIR}/log_scale.c
    ${MICROFRONTEND_DIR}/log_scale_util.c
    ${MICROFRONTEND_DIR}/noise_reduction.c
    ${MICROFRONTEND_DIR}/noise_reduction_util.c
    ${MICROFRONTEND_DIR}/pcan_gain_control.c
    ${MICROFRONTEND_DIR}/pcan_gain_control_util.c
    ${MICROFRONTEND_DIR}/window.c
    ${MICROFRONTEND_DIR}/window_util.c
)

target_include_directories(
    superdash_audio_features
    PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/third_party/tflite_microfrontend
    ${KISSFFT_DIR}
)

target_compile_features(superdash_audio_features PRIVATE cxx_std_17)
target_compile_definitions(superdash_audio_features PRIVATE FIXED_POINT=16)
