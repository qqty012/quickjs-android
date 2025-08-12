package com.quickjs

import java.io.Closeable
import java.util.Collections
import java.util.HashMap
import java.util.HashSet
import java.util.LinkedList
import java.util.WeakHashMap

open class JSContext(
    val quickJS: QuickJS,
    val contextPtr: Long
): Closeable {

    private val plugins: MutableSet<Plugin> = Collections.synchronizedSet(HashSet())
    val refs: MutableMap<Int, JSValue> = Collections.synchronizedMap(WeakHashMap())
    private val releaseObjPtrPool: MutableList<Array<Any>> = Collections.synchronizedList(LinkedList())
    val functionRegistry: MutableMap<Int, QuickJS.MethodDescriptor> = Collections.synchronizedMap(HashMap())
    private var released: Boolean = false
    val native: QuickJSNative by lazy { quickJS.native }
    val global: JSObject by lazy { native.getGlobalObject(contextPtr) }

    init {
        QuickJS.sContextMap[contextPtr] = this
    }

    fun addObjRef(reference: JSValue) {
        if (reference.javaClass != JSContext::class.java) {
            refs[reference.hashCode()] = reference
        }
    }

    fun releaseObjRef(reference: JSValue, finalize: Boolean) {
        if (finalize) {
            releaseObjPtrPool.add(arrayOf(reference.tag, reference.uInt32, reference.uFloat64, reference.uPtr))
        } else {
            native.releasePtr(contextPtr, reference.tag, reference.uInt32, reference.uFloat64, reference.uPtr)
        }
        removeObjRef(reference)
    }

    private fun checkReleaseObjPtrPool() {
        while (releaseObjPtrPool.isNotEmpty()) {
            val ptr = releaseObjPtrPool[0]
            native.releasePtr(contextPtr, ptr[0] as Long, ptr[1] as Int, ptr[2] as Double, ptr[3] as Long)
            releaseObjPtrPool.removeAt(0)
        }
    }

    fun removeObjRef(reference: JSValue) {
        refs.remove(reference.hashCode())
    }

    override fun close() {
        if (released) return
        plugins.forEach { it.close(this@JSContext) }
        plugins.clear()
        functionRegistry.clear()
        refs.values.toTypedArray().forEach { it.close() }
        checkReleaseObjPtrPool()
        native.releaseContext(contextPtr)
        QuickJS.sContextMap.remove(contextPtr)
        released = true
    }

    open fun registerJavaMethod(jsFunctionName: String, callback: JavaCallback): JSFunction {
        checkReleased()
        val functionHandle = native.registerJavaMethod(contextPtr, global, jsFunctionName, callback.hashCode())
        registerCallback(callback)
        return functionHandle
    }

    /*open fun registerJavaMethod(jsFunctionName: String, callback: JavaVoidCallback): JSFunction {
        checkReleased()
        val functionHandle = native.registerJavaMethod(contextPtr, global, jsFunctionName, callback.hashCode(), true)
        registerCallback(callback)
        return functionHandle
    }*/

    private fun executeScript(expectedType: JSValue.TYPE, source: String, fileName: String?): Any? {
        val obj = native.executeScript(this.contextPtr, expectedType.value, source, fileName ?: "", QuickJS.JS_EVAL_TYPE_GLOBAL)
        QuickJS.checkException(this)
        return obj
    }

    fun executeScript(source: String, fileName: String): Any? =
        executeScript(JSValue.TYPE.UNKNOWN, source, fileName)

    fun executeScript(source: String, fileName: String, evalType: Int): Any? {
        val obj = native.executeScript(this.contextPtr, JSValue.TYPE.UNKNOWN.value, source, fileName, evalType)
        QuickJS.checkException(this)
        return obj
    }

    open fun executeModuleScript(source: String, fileName: String): Any? {
        val obj = native.executeScript(this.contextPtr, JSValue.TYPE.UNKNOWN.value, source, fileName, QuickJS.JS_EVAL_TYPE_MODULE)
        QuickJS.checkException(this)
        return obj
    }

    fun executeIntegerScript(source: String, fileName: String?): Int =
        executeScript(JSValue.TYPE.INTEGER, source, fileName) as Int

    fun executeDoubleScript(source: String, fileName: String?): Double =
        executeScript(JSValue.TYPE.DOUBLE, source, fileName) as Double

    fun executeBooleanScript(source: String, fileName: String?): Boolean =
        executeScript(JSValue.TYPE.BOOLEAN, source, fileName) as Boolean

    fun executeStringScript(source: String, fileName: String?): String =
        executeScript(JSValue.TYPE.STRING, source, fileName) as String

    fun executeVoidScript(source: String, fileName: String?) {
        executeScript(JSValue.TYPE.NULL, source, fileName)
    }

    fun executeArrayScript(source: String, fileName: String?): JSArray =
        executeScript(JSValue.TYPE.JS_ARRAY, source, fileName) as JSArray

    fun executeObjectScript(source: String, fileName: String?): JSObject =
        executeScript(JSValue.TYPE.JS_OBJECT, source, fileName) as JSObject

    fun isReleased(): Boolean =
        quickJS.released || released

    fun registerCallback(callback: JavaCallback) {
        val methodDescriptor = QuickJS.MethodDescriptor()
        methodDescriptor.callback = callback
        functionRegistry[callback.hashCode()] = methodDescriptor
    }

    /*fun registerCallback(callback: JavaVoidCallback) {
        val methodDescriptor = QuickJS.MethodDescriptor()
        methodDescriptor.voidCallback = callback
        functionRegistry[callback.hashCode()] = methodDescriptor
    }*/

    fun checkRuntime(value: JSValue?) {
        if (value != null && !value.isUndefined()) {
            val quickJS = value.context.quickJS
            if (quickJS.released || quickJS != this.quickJS) {
                throw Error("Invalid target runtime")
            }
        }
    }

    fun newError(message: String): JSObject {
        return native.newError(contextPtr, message)
    }

    fun addPlugin(plugin: Plugin) {
        checkReleased()
        if (plugins.contains(plugin)) return
        plugin.setup(this)
        plugins.add(plugin)
    }

    fun checkReleased() {
        checkReleaseObjPtrPool()
        if (isReleased()) throw Error("Context disposed error")
    }

    fun toJSString(obj: JSValue): String {
        return native.toJSString(contextPtr, obj) ?: "undefined"
    }

    fun getPrototype(obj: JSObject): JSObject {
        return native.getPrototype(contextPtr, obj)
    }

//    fun getNative(): QuickJSNative = quickJS.native

}