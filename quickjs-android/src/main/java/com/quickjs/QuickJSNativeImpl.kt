package com.quickjs

class QuickJSNativeImpl : QuickJSNative {



    companion object {
        @JvmStatic
        external fun createRuntime(): Long
    }

    external override fun releaseRuntime(runtimePtr: Long)

    external override fun createContext(runtimePtr: Long): Long

    external override fun releaseContext(contextPtr: Long)

    external override fun executeScript(
        contextPtr: Long,
        expectedType: Int,
        source: String,
        fileName: String,
        evalFlags: Int
    ): Any?

    external override fun getGlobalObject(contextPtr: Long): JSObject

    external override fun set(
        contextPtr: Long,
        objectHandle: JSValue,
        key: String,
        value: Any?
    )

    external override fun get(
        contextPtr: Long,
        expectedType: Int,
        objectHandle: JSValue,
        key: String
    ): Any?

    external override fun arrayGet(
        contextPtr: Long,
        expectedType: Int,
        objectHandle: JSValue,
        index: Int
    ): Any?

    external override fun arrayAdd(
        contextPtr: Long,
        objectHandle: JSValue,
        value: Any?
    )

    external override fun executeFunction(
        contextPtr: Long,
        expectedType: Int,
        objectHandle: JSValue,
        name: String,
        parametersHandle: Array<out Any?>
    ): Any?

    external override fun executeFunction2(
        contextPtr: Long,
        expectedType: Int,
        objectHandle: JSValue,
        functionHandle: JSValue,
        parametersHandle: Array<out Any?>
    ): Any?

    external override fun initNewJSObject(contextPtr: Long): JSObject

    external override fun initNewJSArray(contextPtr: Long): JSArray

    external override fun initNewJSFunction(
        contextPtr: Long,
        javaCallerId: Int
    ): JSFunction

    external override fun releasePtr(
        contextPtr: Long,
        tag: Long,
        uInt32: Int,
        uFloat64: Double,
        uPtr: Long
    )

    external override fun registerJavaMethod(
        contextPtr: Long,
        objectHandle: JSValue,
        jsFunctionName: String,
        javaCallerId: Int,
    ): JSFunction

    external override fun getObjectType(
        contextPtr: Long,
        objectHandle: JSValue
    ): Int

    external override fun contains(
        contextPtr: Long,
        objectHandle: JSValue,
        key: String
    ): Boolean

    external override fun getKeys(
        contextPtr: Long,
        objectHandle: JSValue
    ): Array<String>

    external override fun isUndefined(
        contextPtr: Long,
        value: JSValue
    ): Boolean

    external override fun undefined(contextPtr: Long): JSValue

    external override fun getValue(
        contextPtr: Long,
        obj: JSObject,
        key: String
    ): JSValue

    external override fun arrayGetValue(
        contextPtr: Long,
        array: JSArray,
        index: Int
    ): JSValue?

    external override fun getException(contextPtr: Long): Array<String>?

    external override fun newClass(
        contextPtr: Long,
        javaCallerId: Int
    ): JSFunction

    external override fun toJSString(contextPtr: Long, value: JSValue): String?

    // 查看原型
    external override fun getPrototype(contextPtr: Long, obj: JSObject): JSObject

    // 设置原型
    external override fun setPrototype(contextPtr: Long, obj: JSObject, prototype: JSObject)

    external override fun newError(contextPtr: Long, message: String): JSObject

    external override fun isError(contextPtr: Long, value: JSValue): Boolean
}
