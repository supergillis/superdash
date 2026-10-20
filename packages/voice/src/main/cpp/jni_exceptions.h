#pragma once

#include <jni.h>

#include <exception>
#include <new>

// Does nothing when an exception is already pending, so the first failure wins.
inline void throwJava(JNIEnv *env, const char *class_name, const char *message) {
    if (env->ExceptionCheck()) {
        return;
    }
    jclass error_class = env->FindClass(class_name);
    if (error_class == nullptr) {
        return;
    }
    env->ThrowNew(error_class, message);
    env->DeleteLocalRef(error_class);
}

// A C++ exception crossing a JNI frame calls std::terminate and aborts the app.
// Run every allocating entry point body through this so Kotlin sees a Java exception.
template <typename Result, typename Body>
Result guardNative(JNIEnv *env, Result fallback, Body body) {
    try {
        return body();
    } catch (const std::bad_alloc &) {
        throwJava(env, "java/lang/OutOfMemoryError", "Native allocation failed");
    } catch (const std::exception &error) {
        throwJava(env, "java/lang/IllegalStateException", error.what());
    } catch (...) {
        throwJava(env, "java/lang/IllegalStateException", "Unknown native failure");
    }
    return fallback;
}
