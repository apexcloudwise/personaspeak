/*
 * Copyright (c) 2026 Pixel Perfect Studios
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.personaspeak.engine

import java.util.Locale

/**
 * One of the two narrow host interfaces (#124): the engine never touches
 * android.util.Log or ASK's Logger facade; the host installs its sink.
 * Call shapes mirror ASK's Logger (format string + varargs, throwable
 * variants) so the ported code reads as it did upstream.
 */
interface EngineLog {
    fun d(tag: String, msgFormat: String?, vararg args: Any?)

    fun i(tag: String, msgFormat: String?, vararg args: Any?)

    fun w(tag: String, msgFormat: String?, vararg args: Any?)

    fun w(tag: String, t: Throwable?, msgFormat: String?, vararg args: Any?)

    fun e(tag: String, msgFormat: String?, vararg args: Any?)

    fun e(tag: String, t: Throwable?, msgFormat: String?, vararg args: Any?)

    object Noop : EngineLog {
        override fun d(tag: String, msgFormat: String?, vararg args: Any?) {}
        override fun i(tag: String, msgFormat: String?, vararg args: Any?) {}
        override fun w(tag: String, msgFormat: String?, vararg args: Any?) {}
        override fun w(tag: String, t: Throwable?, msgFormat: String?, vararg args: Any?) {}
        override fun e(tag: String, msgFormat: String?, vararg args: Any?) {}
        override fun e(tag: String, t: Throwable?, msgFormat: String?, vararg args: Any?) {}
    }

    companion object {
        /** Hosts swap this; defaults to silent. */
        @Volatile
        var instance: EngineLog = Noop
    }
}

/** Logger-shaped facade matching ASK's Logger call sites (ported code uses this). */
object Log {
    @JvmStatic
    fun d(tag: String, msgFormat: String?, vararg args: Any?) =
        EngineLog.instance.d(tag, msgFormat, *args)

    @JvmStatic
    fun i(tag: String, msgFormat: String?, vararg args: Any?) =
        EngineLog.instance.i(tag, msgFormat, *args)

    @JvmStatic
    fun w(tag: String, msgFormat: String?, vararg args: Any?) =
        EngineLog.instance.w(tag, msgFormat, *args)

    @JvmStatic
    fun w(tag: String, t: Throwable?, msgFormat: String?, vararg args: Any?) =
        EngineLog.instance.w(tag, t, msgFormat, *args)

    @JvmStatic
    fun e(tag: String, msgFormat: String?, vararg args: Any?) =
        EngineLog.instance.e(tag, msgFormat, *args)

    @JvmStatic
    fun e(tag: String, t: Throwable?, msgFormat: String?, vararg args: Any?) =
        EngineLog.instance.e(tag, t, msgFormat, *args)
}
