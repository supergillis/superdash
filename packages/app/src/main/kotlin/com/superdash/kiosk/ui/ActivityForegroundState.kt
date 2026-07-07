package com.superdash.kiosk.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun rememberActivityForegroundState(): State<Boolean> {
    val lifecycleOwner = LocalLifecycleOwner.current
    val foreground =
        remember(lifecycleOwner) {
            mutableStateOf(lifecycleOwner.lifecycle.currentState == Lifecycle.State.RESUMED)
        }

    DisposableEffect(lifecycleOwner) {
        fun refresh() {
            foreground.value = lifecycleOwner.lifecycle.currentState == Lifecycle.State.RESUMED
        }

        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> {
                        foreground.value = true
                    }
                    Lifecycle.Event.ON_PAUSE -> {
                        foreground.value = false
                    }
                    Lifecycle.Event.ON_DESTROY -> {
                        foreground.value = false
                    }
                    else -> {
                        refresh()
                    }
                }
            }

        refresh()
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    return foreground
}
