#include <jni.h>

#include <array>
#include <cstdint>
#include <memory>
#include <stdexcept>
#include <vector>

#include "jni_exceptions.h"

extern "C" {
#include "tensorflow/lite/experimental/microfrontend/lib/frontend.h"
#include "tensorflow/lite/experimental/microfrontend/lib/frontend_util.h"
}

namespace {

constexpr int kFeatureSize = 40;
constexpr int kFeatureDurationMs = 30;
constexpr float kFilterbankLowerBandLimit = 125.0f;
constexpr float kFilterbankUpperBandLimit = 7500.0f;
constexpr int kNoiseReductionSmoothingBits = 10;
constexpr float kNoiseReductionEvenSmoothing = 0.025f;
constexpr float kNoiseReductionOddSmoothing = 0.06f;
constexpr float kNoiseReductionMinSignalRemaining = 0.05f;
constexpr bool kPcanGainControlEnabled = true;
constexpr float kPcanGainControlStrength = 0.95f;
constexpr float kPcanGainControlOffset = 80.0f;
constexpr int kPcanGainControlGainBits = 21;
constexpr bool kLogScaleEnabled = true;
constexpr int kLogScaleScaleShift = 6;
constexpr float kFrontendFloatScale = 25.6f;

struct SuperdashFrontend {
    FrontendConfig config;
    FrontendState state;
};

void configureFrontend(FrontendConfig *config, int step_size_ms) {
    config->window.size_ms = kFeatureDurationMs;
    config->window.step_size_ms = step_size_ms;
    config->filterbank.num_channels = kFeatureSize;
    config->filterbank.lower_band_limit = kFilterbankLowerBandLimit;
    config->filterbank.upper_band_limit = kFilterbankUpperBandLimit;
    config->noise_reduction.smoothing_bits = kNoiseReductionSmoothingBits;
    config->noise_reduction.even_smoothing = kNoiseReductionEvenSmoothing;
    config->noise_reduction.odd_smoothing = kNoiseReductionOddSmoothing;
    config->noise_reduction.min_signal_remaining = kNoiseReductionMinSignalRemaining;
    config->pcan_gain_control.enable_pcan = kPcanGainControlEnabled;
    config->pcan_gain_control.strength = kPcanGainControlStrength;
    config->pcan_gain_control.offset = kPcanGainControlOffset;
    config->pcan_gain_control.gain_bits = kPcanGainControlGainBits;
    config->log_scale.enable_log = kLogScaleEnabled;
    config->log_scale.scale_shift = kLogScaleScaleShift;
}

jlong initFrontend(jint sample_rate_hz, jint step_size_ms) {
    auto frontend = std::make_unique<SuperdashFrontend>();
    configureFrontend(&frontend->config, step_size_ms);
    if (!FrontendPopulateState(&frontend->config, &frontend->state, sample_rate_hz)) {
        throw std::runtime_error("Failed to initialize audio feature extractor");
    }
    return reinterpret_cast<jlong>(frontend.release());
}

SuperdashFrontend *openFrontend(jlong handle) {
    if (handle == 0) {
        throw std::runtime_error("AudioFeatureExtractor already closed");
    }
    return reinterpret_cast<SuperdashFrontend *>(handle);
}

jobject extractFeatures(JNIEnv *env, jlong handle, jshortArray samples) {
    SuperdashFrontend *frontend = openFrontend(handle);

    const jsize sample_count = env->GetArrayLength(samples);
    std::vector<int16_t> audio(static_cast<size_t>(sample_count));
    env->GetShortArrayRegion(samples, 0, sample_count, reinterpret_cast<jshort *>(audio.data()));
    if (env->ExceptionCheck()) {
        return nullptr;
    }

    jclass array_list_class = env->FindClass("java/util/ArrayList");
    if (array_list_class == nullptr || env->ExceptionCheck()) {
        return nullptr;
    }
    jmethodID array_list_constructor = env->GetMethodID(array_list_class, "<init>", "()V");
    jmethodID array_list_add = env->GetMethodID(array_list_class, "add", "(Ljava/lang/Object;)Z");
    if (array_list_constructor == nullptr || array_list_add == nullptr || env->ExceptionCheck()) {
        return nullptr;
    }
    jobject result = env->NewObject(array_list_class, array_list_constructor);
    if (result == nullptr || env->ExceptionCheck()) {
        return nullptr;
    }

    size_t offset = 0;
    while (offset < audio.size()) {
        size_t samples_read = 0;
        FrontendOutput output =
                FrontendProcessSamples(
                        &frontend->state,
                        audio.data() + offset,
                        audio.size() - offset,
                        &samples_read);
        const bool made_progress = samples_read > 0;
        offset += samples_read;

        if (output.size == 0 || output.values == nullptr) {
            if (made_progress) {
                continue;
            }
            break;
        }
        if (output.size != kFeatureSize) {
            throw std::runtime_error("Unexpected audio feature size");
        }

        std::array<float, kFeatureSize> values;
        for (size_t i = 0; i < values.size(); ++i) {
            values[i] = static_cast<float>(output.values[i]) / kFrontendFloatScale;
        }
        jfloatArray frame = env->NewFloatArray(static_cast<jsize>(values.size()));
        if (frame == nullptr || env->ExceptionCheck()) {
            return nullptr;
        }
        env->SetFloatArrayRegion(frame, 0, static_cast<jsize>(values.size()), values.data());
        if (!env->ExceptionCheck()) {
            env->CallBooleanMethod(result, array_list_add, frame);
        }
        env->DeleteLocalRef(frame);
        if (env->ExceptionCheck()) {
            return nullptr;
        }

        if (!made_progress) {
            break;
        }
    }

    return result;
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_superdash_voice_features_AudioFeatureExtractor_nativeInit(
        JNIEnv *env,
        jobject,
        jint sample_rate_hz,
        jint step_size_ms) {
    return guardNative<jlong>(env, 0, [&] { return initFrontend(sample_rate_hz, step_size_ms); });
}

extern "C" JNIEXPORT void JNICALL
Java_com_superdash_voice_features_AudioFeatureExtractor_nativeRelease(
        JNIEnv *,
        jobject,
        jlong handle) {
    if (handle == 0) {
        return;
    }
    auto *frontend = reinterpret_cast<SuperdashFrontend *>(handle);
    FrontendFreeStateContents(&frontend->state);
    delete frontend;
}

extern "C" JNIEXPORT void JNICALL
Java_com_superdash_voice_features_AudioFeatureExtractor_nativeReset(
        JNIEnv *env,
        jobject,
        jlong handle) {
    if (handle == 0) {
        throwJava(env, "java/lang/IllegalStateException", "AudioFeatureExtractor already closed");
        return;
    }
    FrontendReset(&reinterpret_cast<SuperdashFrontend *>(handle)->state);
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_superdash_voice_features_AudioFeatureExtractor_nativeExtract(
        JNIEnv *env,
        jobject,
        jlong handle,
        jshortArray samples) {
    return guardNative<jobject>(env, nullptr, [&] { return extractFeatures(env, handle, samples); });
}
