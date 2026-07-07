package com.superdash.core.resources

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

/** Resolves string resources outside Compose (ViewModels, services, notifications),
 *  so those classes stay off the Android framework and are unit-testable with a fake. */
interface StringProvider {
    fun get(
        @StringRes id: Int,
    ): String

    fun get(
        @StringRes id: Int,
        vararg args: Any,
    ): String

    fun getQuantity(
        @PluralsRes id: Int,
        quantity: Int,
        vararg args: Any,
    ): String
}

class AndroidStringProvider(
    private val context: Context,
) : StringProvider {
    override fun get(id: Int): String = context.getString(id)

    override fun get(id: Int, vararg args: Any): String = context.getString(id, *args)

    override fun getQuantity(id: Int, quantity: Int, vararg args: Any): String =
        context.resources.getQuantityString(id, quantity, *args)
}
