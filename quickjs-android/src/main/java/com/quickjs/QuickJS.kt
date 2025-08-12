package com.quickjs

import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import androidx.annotation.Keep
import java.io.Closeable
import java.io.File
import java.util.Collections

class QuickJS private constructor(
        val runtimePtr: Long,
        handlerThread: HandlerThread?
) : Closeable {
    val native: EventQueue = EventQueue(this, handlerThread)

    val home = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "nodejs")

    @Volatile
    var released: Boolean = false

    companion object {
        val sContextMap: MutableMap<Long, JSContext> = Collections.synchronizedMap(HashMap())

        private var sId = 0

        fun createRuntime(): QuickJS {
            return QuickJS(QuickJSNativeImpl.createRuntime(), null)
        }

        fun createRuntimeWithEventQueue(): QuickJS {
            val lock = Object()
            val objects = arrayOfNulls<Any>(2)
            val handlerThread = HandlerThread("QuickJS-" + (sId++))
            handlerThread.start()
            Handler(handlerThread.looper).post {
                objects[0] = QuickJS(QuickJSNativeImpl.createRuntime(), handlerThread)
                synchronized(lock) {
                    objects[1] = true
                    lock.notify()
                }
            }
            synchronized(lock) {
                if (objects[1] == null) {
                    try {
                        lock.wait()
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                }
            }
            return objects[0] as QuickJS
        }

        @Keep
        @JvmStatic
        fun callJavaCallback(
            contextPtr: Long,
            javaCallerId: Int,
            objectHandle: JSValue,
            args: Array<Any>,
        ): Any? {
            val context = sContextMap[contextPtr] ?: return null
            val methodDescriptor = context.functionRegistry[javaCallerId] ?: return null
            val receiver = objectHandle as? JSObject
            return methodDescriptor.callback?.invoke(receiver, args)
        }

        @Keep
        @JvmStatic
        fun createJSValue(contextPtr: Long, type: Int, tag: Long, uInt32: Int, uFloat64: Double, uPtr: Long): JSValue {
            val context = sContextMap[contextPtr]!!
            return when (type) {
                JSValue.TYPE_JS_FUNCTION -> JSFunction(context, tag, uInt32, uFloat64, uPtr)
                JSValue.TYPE_JS_ARRAY -> JSArray(context, tag, uInt32, uFloat64, uPtr)
                JSValue.TYPE_JS_OBJECT -> JSObject(context, tag, uInt32, uFloat64, uPtr)
                JSValue.TYPE_JS_EXCEPTION -> JSException(context, tag, uInt32, uFloat64, uPtr)
                JSValue.TYPE_UNDEFINED -> JSObject.Undefined(context, tag, uInt32, uFloat64, uPtr)
                else -> JSValue(context, tag, uInt32, uFloat64, uPtr)
            }
        }

        @Keep
        @JvmStatic
        fun getModuleScript(contextPtr: Long, moduleName: String): String? {
            val context = sContextMap[contextPtr] ?: return null
            return if (context is Module) context.getModuleScript(moduleName) else null
        }

        @Keep
        @JvmStatic
        fun convertModuleName(
            contextPtr: Long,
            moduleBaseName: String,
            moduleName: String
        ): String? {
            val context = sContextMap[contextPtr] ?: return null
            return if (context is Module) context.convertModuleName(
                moduleBaseName,
                moduleName
            ) else null
        }

        fun executeFunction(
            context: JSContext,
            objectHandle: JSValue,
            name: String,
            parameters: Array<out Any?>
        ): Any? {
            return context.native.executeFunction(
                context.contextPtr,
                JSValue.TYPE_UNKNOWN,
                objectHandle,
                name,
                parameters
            )
        }

        fun checkException(context: JSContext) {
            val result = context.native.getException(context.contextPtr) ?: return
            val message = StringBuilder().apply {
                append(result[1]).append('\n')
                for (i in 2 until result.size) {
                    append(result[i])
                }
            }
            throw QuickJSException(result[0], message.toString())
        }

        const val JS_EVAL_TYPE_GLOBAL = 0
        const val JS_EVAL_TYPE_MODULE = 1
        const val JS_EVAL_TYPE_MASK = 3
        const val JS_EVAL_FLAG_STRICT = (1 shl 3)
        const val JS_EVAL_FLAG_STRIP = (1 shl 4)
        const val JS_EVAL_FLAG_COMPILE_ONLY = (1 shl 5)
        const val JS_EVAL_FLAG_BACKTRACE_BARRIER = (1 shl 6)

        init {
            System.loadLibrary("quickjs")
            System.loadLibrary("quickjs-android")
        }
    }

    fun postEventQueue(event: Runnable) {
//        native.postVoid(event, false)
    }

    fun createContext(): JSContext {
        return JSContext(this, native.createContext(runtimePtr))
    }

    override fun close() {
        if (released) return
        val values = sContextMap.values.filter { it.quickJS == this@QuickJS }
        values.forEach { it.close() }
        native.releaseRuntime(runtimePtr)
        released = true
    }

    fun checkReleased() {
        if (released) throw Error("Runtime disposed error")
    }
    fun isReleased(): Boolean = released

    class MethodDescriptor {
        var callback: JavaCallback? = null
    }
}