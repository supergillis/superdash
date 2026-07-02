#include <jni.h>

#if SUPERDASH_WHISPER_REAL
#include "whisper.h"
#include <string>
#include <vector>
#endif

extern "C" JNIEXPORT jboolean JNICALL
Java_com_superdash_voice_WhisperCppNative_runtimeAvailable(JNIEnv *, jclass) {
#if SUPERDASH_WHISPER_REAL
    return JNI_TRUE;
#else
    return JNI_FALSE;
#endif
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_superdash_voice_WhisperCppNative_createNative(JNIEnv * env, jobject, jstring modelPath) {
#if SUPERDASH_WHISPER_REAL
    const char * path = env->GetStringUTFChars(modelPath, nullptr);
    if (path == nullptr || env->ExceptionCheck()) {
        return 0;
    }
    whisper_context * context = whisper_init_from_file(path);
    env->ReleaseStringUTFChars(modelPath, path);
    return reinterpret_cast<jlong>(context);
#else
    return 0;
#endif
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_superdash_voice_WhisperCppNative_transcribeNative(JNIEnv * env, jobject, jlong handle, jshortArray input) {
#if SUPERDASH_WHISPER_REAL
    auto * context = reinterpret_cast<whisper_context *>(handle);
    if (context == nullptr) {
        jstring empty = env->NewStringUTF("");
        if (env->ExceptionCheck()) {
            return nullptr;
        }
        return empty;
    }

    jsize sampleCount = env->GetArrayLength(input);
    std::vector<jshort> pcm(sampleCount);
    env->GetShortArrayRegion(input, 0, sampleCount, pcm.data());
    if (env->ExceptionCheck()) {
        return nullptr;
    }

    std::vector<float> samples(sampleCount);
    for (int i = 0; i < sampleCount; ++i) {
        samples[i] = static_cast<float>(pcm[i]) / 32768.0f;
    }

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.translate = false;
    params.language = "en";
    params.n_threads = 4;

    int result = whisper_full(context, params, samples.data(), samples.size());
    if (result != 0) {
        jstring empty = env->NewStringUTF("");
        if (env->ExceptionCheck()) {
            return nullptr;
        }
        return empty;
    }

    std::string text;
    int segments = whisper_full_n_segments(context);
    for (int i = 0; i < segments; ++i) {
        text += whisper_full_get_segment_text(context, i);
    }
    jstring out = env->NewStringUTF(text.c_str());
    if (env->ExceptionCheck()) {
        return nullptr;
    }
    return out;
#else
    jstring empty = env->NewStringUTF("");
    if (env->ExceptionCheck()) {
        return nullptr;
    }
    return empty;
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_com_superdash_voice_WhisperCppNative_releaseNative(JNIEnv *, jobject, jlong handle) {
#if SUPERDASH_WHISPER_REAL
    auto * context = reinterpret_cast<whisper_context *>(handle);
    if (context != nullptr) {
        whisper_free(context);
    }
#endif
}
