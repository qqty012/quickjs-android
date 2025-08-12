package com.quickjs

interface QuickJSNative {
    fun releaseRuntime(runtimePtr: Long)

    fun createContext(runtimePtr: Long): Long

    fun releaseContext(contextPtr: Long)

    fun executeScript(
            contextPtr: Long,
            expectedType: Int,
            source: String,
            fileName: String,
            evalFlags: Int
    ): Any?

    fun getGlobalObject(contextPtr: Long): JSObject

    fun set(contextPtr: Long, objectHandle: JSValue, key: String, value: Any?)

    fun get(contextPtr: Long, expectedType: Int, objectHandle: JSValue, key: String): Any?

    fun arrayGet(contextPtr: Long, expectedType: Int, objectHandle: JSValue, index: Int): Any?

    fun arrayAdd(contextPtr: Long, objectHandle: JSValue, value: Any?)

    fun executeFunction(
        contextPtr: Long,
        expectedType: Int,
        objectHandle: JSValue,
        name: String,
        parametersHandle: Array<out Any?>
    ): Any?

    fun executeFunction2(
        contextPtr: Long,
        expectedType: Int,
        objectHandle: JSValue,
        functionHandle: JSValue,
        parametersHandle: Array<out Any?>
    ): Any?

    fun initNewJSObject(contextPtr: Long): JSObject

    fun initNewJSArray(contextPtr: Long): JSArray

    fun initNewJSFunction(contextPtr: Long, javaCallerId: Int): JSFunction

    fun releasePtr(
            contextPtr: Long,
            tag: Long,
            uInt32: Int,
            uFloat64: Double,
            uPtr: Long
    )

    fun registerJavaMethod(
            contextPtr: Long,
            objectHandle: JSValue,
            jsFunctionName: String,
            javaCallerId: Int,
    ): JSFunction

    fun getObjectType(contextPtr: Long, objectHandle: JSValue): Int

    fun contains(contextPtr: Long, objectHandle: JSValue, key: String): Boolean

    fun getKeys(contextPtr: Long, objectHandle: JSValue): Array<String>

    fun isUndefined(contextPtr: Long, value: JSValue): Boolean

    fun undefined(contextPtr: Long): JSValue

    fun getValue(contextPtr: Long, obj: JSObject, key: String): JSValue

    fun arrayGetValue(contextPtr: Long, array: JSArray, index: Int): JSValue?

    fun getException(contextPtr: Long): Array<String>?

    fun newClass(contextPtr: Long, javaCallerId: Int): JSFunction

    fun toJSString(contextPtr: Long, value: JSValue): String?

    fun getPrototype(contextPtr: Long, obj: JSObject): JSObject

    fun setPrototype(contextPtr: Long, obj: JSObject, prototype: JSObject)

    fun newError(contextPtr: Long, message: String): JSObject

    fun isError(contextPtr: Long, value: JSValue): Boolean
}