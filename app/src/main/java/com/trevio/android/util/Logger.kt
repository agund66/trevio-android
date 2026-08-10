package com.trevio.android.util

import android.util.Log

object Logger {
    private const val DEFAULT_TAG = "Trevio"

    fun d(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        if (throwable != null) Log.d(tag, message, throwable) else Log.d(tag, message)
    }

    fun w(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        if (throwable != null) Log.w(tag, message, throwable) else Log.w(tag, message)
    }

    fun e(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
    }

    fun i(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        if (throwable != null) Log.i(tag, message, throwable) else Log.i(tag, message)
    }
}
