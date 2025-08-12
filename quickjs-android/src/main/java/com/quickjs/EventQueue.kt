package com.quickjs

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log


class EventQueue(
    private val quickJS: QuickJS,
    private val handlerThread: HandlerThread?
) : QuickJSNative {
    private val quickJSNative: QuickJSNative = QuickJSNativeImpl()
    private val thread: Thread = Thread.currentThread()
    val handler: Handler = Handler(Looper.myLooper()!!)
    private val threadChecker: ThreadChecker = ThreadChecker(quickJS)

    fun interrupt() {
        handlerThread?.interrupt()
    }

    private fun <T> post(event: () -> T): T? {
        if (quickJS.isReleased() || handlerThread?.isInterrupted == true) {
            Log.e("QuickJS", "QuickJS is released")
            return null
        }
        if (Thread.currentThread() == thread) return event()

        val result = arrayOfNulls<Any>(2)
        val errors = arrayOfNulls<RuntimeException>(1)

        handler.post {
            try {
                result[0] = event()
            } catch (e: RuntimeException) {
                errors[0] = e
            }
            synchronized(result) {
                result[1] = true
                (result as Object).notifyAll() // 关键修正点
            }
        }

        synchronized(result) {
            try {
                if (result[1] == null) {
                    (result as Object).wait() // 关键修正点
                }
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }

        errors[0]?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return result[0] as? T
    }

    fun postVoid(event: () -> Unit) {
        postVoid(true, event)
    }

    fun postVoid(block: Boolean = true, event: () -> Unit) {
        if (quickJS.isReleased() || (handlerThread?.isInterrupted == true)) {
            Log.e("QuickJS", "QuickJS is released")
            return
        }
        if (Thread.currentThread() == thread) {
            event()
            return
        }
        val result = arrayOfNulls<Any>(2)
        val errors = arrayOfNulls<RuntimeException>(1)
        handler.post {
            try {
                if (!quickJS.isReleased()) {
                    event()
                }
            } catch (e: RuntimeException) {
                errors[0] = e
            }
            if (block) {
                synchronized(result) {
                    result[1] = true
                    (result as Object).notifyAll()
                }
            }
        }
        if (block) {
            synchronized(result) {
                try {
                    if (result[1] == null) {
                        (result as Object).wait()
                    }
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }
            errors[0]?.let { throw it }
        }
    }

    override fun releaseRuntime(runtimePtr: Long) {
        postVoid { quickJSNative.releaseRuntime(runtimePtr) }
    }

    override fun createContext(runtimePtr: Long): Long {
        return quickJSNative.createContext(runtimePtr)
    }

    override fun releaseContext(contextPtr: Long) {
        postVoid { quickJSNative.releaseContext(contextPtr) }
    }

    override fun executeScript(
        contextPtr: Long,
        expectedType: Int,
        source: String,
        fileName: String,
        evalFlags: Int
    ): Any? {
        return post { quickJSNative.executeScript(contextPtr, expectedType, source, fileName, evalFlags) }
    }


    override fun getGlobalObject(contextPtr: Long): JSObject {
        return post { quickJSNative.getGlobalObject(contextPtr) }!!
    }

    override fun set(contextPtr: Long, objectHandle: JSValue, key: String, value: Any?) {
        postVoid { quickJSNative.set(contextPtr, objectHandle, key, value) }
    }

    override fun get(contextPtr: Long, expectedType: Int, objectHandle: JSValue, key: String): Any? {
        return post { quickJSNative.get(contextPtr, expectedType, objectHandle, key) }
    }

    override fun arrayGet(contextPtr: Long, expectedType: Int, objectHandle: JSValue, index: Int): Any? {
        return post { quickJSNative.arrayGet(contextPtr, expectedType, objectHandle, index) }
    }

    override fun arrayAdd(contextPtr: Long, objectHandle: JSValue, value: Any?) {
        postVoid { quickJSNative.arrayAdd(contextPtr, objectHandle, value) }
    }

    override fun executeFunction(
        contextPtr: Long,
        expectedType: Int,
        objectHandle: JSValue,
        name: String,
        parametersHandle: Array<out Any?>
    ): Any? {
        return post { quickJSNative.executeFunction(contextPtr, expectedType, objectHandle, name, parametersHandle) }
    }

    override fun executeFunction2(
        contextPtr: Long,
        expectedType: Int,
        objectHandle: JSValue,
        functionHandle: JSValue,
        parametersHandle: Array<out Any?>
    ): Any? {
        return post { quickJSNative.executeFunction2(contextPtr, expectedType, objectHandle, functionHandle, parametersHandle) }
    }

    override fun initNewJSObject(contextPtr: Long): JSObject {
        return post { quickJSNative.initNewJSObject(contextPtr) }!!
    }

    override fun initNewJSArray(contextPtr: Long): JSArray {
        return post { quickJSNative.initNewJSArray(contextPtr) }!!
    }

    override fun initNewJSFunction(contextPtr: Long, javaCallerId: Int): JSFunction {
        return post { quickJSNative.initNewJSFunction(contextPtr, javaCallerId) }!!
    }

    override fun releasePtr(contextPtr: Long, tag: Long, uInt32: Int, uFloat64: Double, uPtr: Long) {
        postVoid { quickJSNative.releasePtr(contextPtr, tag, uInt32, uFloat64, uPtr) }
    }

    override fun registerJavaMethod(
        contextPtr: Long,
        objectHandle: JSValue,
        jsFunctionName: String,
        javaCallerId: Int
    ): JSFunction {
        return post { quickJSNative.registerJavaMethod(contextPtr, objectHandle, jsFunctionName, javaCallerId) }!!
    }

    override fun getObjectType(contextPtr: Long, objectHandle: JSValue): Int {
        return post { quickJSNative.getObjectType(contextPtr, objectHandle) }!!
    }

    override fun contains(contextPtr: Long, objectHandle: JSValue, key: String): Boolean {
        return post { quickJSNative.contains(contextPtr, objectHandle, key) }!!
    }

    override fun getKeys(contextPtr: Long, objectHandle: JSValue): Array<String> {
        return post { quickJSNative.getKeys(contextPtr, objectHandle) }!!
    }

    override fun isUndefined(contextPtr: Long, value: JSValue): Boolean {
        return post { quickJSNative.isUndefined(contextPtr, value) }!!
    }

    override fun undefined(contextPtr: Long): JSValue {
        return post { quickJSNative.undefined(contextPtr) }!!
    }

    override fun getValue(contextPtr: Long, obj: JSObject, key: String): JSValue {
        return post { quickJSNative.getValue(contextPtr, obj, key) }!!
    }

    override fun arrayGetValue(contextPtr: Long, array: JSArray, index: Int): JSValue? {
        return post { quickJSNative.arrayGetValue(contextPtr, array, index) }
    }

    override fun getException(contextPtr: Long): Array<String>? {
        return post { quickJSNative.getException(contextPtr) }
    }

    override fun newClass(contextPtr: Long, javaCallerId: Int): JSFunction {
        return post { quickJSNative.newClass(contextPtr, javaCallerId) }!!
    }

    override fun toJSString(contextPtr: Long, value: JSValue): String {
        return post { quickJSNative.toJSString(contextPtr, value) } ?: "undefind"
    }

    override fun getPrototype(contextPtr: Long, obj: JSObject): JSObject {
        return post { quickJSNative.getPrototype(contextPtr, obj) }!!
    }

    override fun setPrototype(contextPtr: Long, obj: JSObject, prototype: JSObject) {
        postVoid { quickJSNative.setPrototype(contextPtr, obj, prototype) }
    }

    override fun newError(contextPtr: Long, message: String): JSObject {
        return post { quickJSNative.newError(contextPtr, message) }!!
    }

    override fun isError(contextPtr: Long, value: JSValue): Boolean {
        return post { quickJSNative.isError(contextPtr, value) }!!
    }
}