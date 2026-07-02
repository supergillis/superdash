package com.superdash.core.log

import android.util.Log as AndroidLog

/** Thin wrapper around android.util.Log. Centralises the tag and gives us one place
 *  to add file logging or filtering later.
 *
 *  Per-file usage:
 *  ```
 *  private val log = Log("HaWs")
 *  log.i("seeded entities", "count" to count)
 *  ```
 *  Top-level [Log.i]/[Log.w]/etc. functions remain available for the rare site
 *  without a meaningful name prefix.
 *
 *  ## Structured fields (slog-style)
 *  Each level method accepts trailing `Pair<String, Any?>` varargs that get
 *  appended to the message in `key=value` form, with whitespace-containing
 *  values quoted:
 *  ```
 *  log.i("renderer gone", "didCrash" to detail.didCrash(), "rendererPid" to pid)
 *  // → I/superdash: WebView: renderer gone didCrash=true rendererPid=1234
 *  ```
 *  Pass no pairs and the message logs as-is.
 *
 *  Non-ASCII characters in message + value strings are stripped before logging.
 *  most error paths interpolate exception messages from third-party libraries
 *  (Ktor, OkHttp, ExoPlayer, HA server responses) which can carry IDN hostnames,
 *  UTF-8 captive-portal HTML, or codec strings. Crashing in the very code path
 *  that's already handling a failure converts recoverable errors into hard
 *  Crashes here are unacceptable for a 24/7 kiosk. */
class Log private constructor(
    private val name: String,
) {
    private val isAndroid =
        try {
            Class.forName("android.util.Log")
            true
        } catch (e: Exception) {
            false
        }

    fun v(msg: String, vararg attrs: Pair<String, Any?>) {
        if (isAndroid) {
            AndroidLog.v(TAG, "$name: ${format(msg, attrs)}")
        } else {
            println("V/$TAG: $name: ${format(msg, attrs)}")
        }
    }

    fun d(msg: String, vararg attrs: Pair<String, Any?>) {
        if (isAndroid) {
            AndroidLog.d(TAG, "$name: ${format(msg, attrs)}")
        } else {
            println("D/$TAG: $name: ${format(msg, attrs)}")
        }
    }

    fun i(msg: String, vararg attrs: Pair<String, Any?>) {
        if (isAndroid) {
            AndroidLog.i(TAG, "$name: ${format(msg, attrs)}")
        } else {
            println("I/$TAG: $name: ${format(msg, attrs)}")
        }
    }

    fun w(msg: String, t: Throwable? = null, vararg attrs: Pair<String, Any?>) {
        if (isAndroid) {
            AndroidLog.w(TAG, "$name: ${format(msg, attrs)}", t)
        } else {
            println("W/$TAG: $name: ${format(msg, attrs)}")
            t?.printStackTrace()
        }
    }

    fun e(msg: String, t: Throwable? = null, vararg attrs: Pair<String, Any?>) {
        if (isAndroid) {
            AndroidLog.e(TAG, "$name: ${format(msg, attrs)}", t)
        } else {
            println("E/$TAG: $name: ${format(msg, attrs)}")
            t?.printStackTrace()
        }
    }

    companion object {
        private const val TAG = "superdash"
        private val isAndroid =
            try {
                Class.forName("android.util.Log")
                true
            } catch (e: Exception) {
                false
            }

        operator fun invoke(name: String): Log = Log(name)

        fun v(msg: String, vararg attrs: Pair<String, Any?>) {
            if (isAndroid) {
                AndroidLog.v(TAG, format(msg, attrs))
            } else {
                println("V/$TAG: ${format(msg, attrs)}")
            }
        }

        fun d(msg: String, vararg attrs: Pair<String, Any?>) {
            if (isAndroid) {
                AndroidLog.d(TAG, format(msg, attrs))
            } else {
                println("D/$TAG: ${format(msg, attrs)}")
            }
        }

        fun i(msg: String, vararg attrs: Pair<String, Any?>) {
            if (isAndroid) {
                AndroidLog.i(TAG, format(msg, attrs))
            } else {
                println("I/$TAG: ${format(msg, attrs)}")
            }
        }

        fun w(msg: String, t: Throwable? = null, vararg attrs: Pair<String, Any?>) {
            if (isAndroid) {
                AndroidLog.w(TAG, format(msg, attrs), t)
            } else {
                println("W/$TAG: ${format(msg, attrs)}")
                t?.printStackTrace()
            }
        }

        fun e(msg: String, t: Throwable? = null, vararg attrs: Pair<String, Any?>) {
            if (isAndroid) {
                AndroidLog.e(TAG, format(msg, attrs), t)
            } else {
                println("E/$TAG: ${format(msg, attrs)}")
                t?.printStackTrace()
            }
        }

        private fun format(msg: String, attrs: Array<out Pair<String, Any?>>): String {
            val safeMsg = msg.asciiSafe()
            if (attrs.isEmpty()) {
                return safeMsg
            }
            val rendered =
                attrs.joinToString(" ") { (key, value) ->
                    "$key=${renderValue(value)}"
                }
            return "$safeMsg $rendered"
        }

        private fun renderValue(value: Any?): String {
            val text = value?.toString().orEmpty().asciiSafe()
            return if (text.any { it.isWhitespace() || it == '=' || it == '"' }) {
                "\"${text.replace("\"", "\\\"")}\""
            } else {
                text
            }
        }

        private fun String.asciiSafe(): String =
            if (all { it.code < 0x80 }) {
                this
            } else {
                map { if (it.code < 0x80) it else '?' }.joinToString("")
            }
    }
}
