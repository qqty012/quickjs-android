package com.quickjs

open class JSFunction : JSObject {

    constructor(context: JSContext, callback: JavaCallback) : this(context, callback, false)

    private constructor(context: JSContext, callback: JavaCallback, isVoid: Boolean) :
            super(context, context.native.initNewJSFunction(context.contextPtr, callback.hashCode())) {
        context.registerCallback(callback)
    }

    internal constructor(context: JSContext, tag: Long, uInt32: Int, uFloat64: Double, uPtr: Long) :
            super(context, tag, uInt32, uFloat64, uPtr)

    open fun call(type: TYPE, receiver: JSObject? = null, vararg parameters: Any?): Any? {
        context.checkReleased()
        parameters.filterIsInstance<JSValue>().forEach { context.checkRuntime(it) }
        val actualReceiver = receiver ?: undefined(context)
        val result = getNative().executeFunction2(context.contextPtr, type.value, actualReceiver, this, parameters)
        QuickJS.checkException(context)
        return checkType(result, type)
    }

    fun call(receiver: JSObject? = null, vararg parameters: Any?): Any? {
        return call(TYPE.UNKNOWN, receiver, *parameters)
    }

    override fun toString(): String {
        val result = context.native.toJSString(context.contextPtr, this) ?: return "undefined"

        return if (result.trim().startsWith("function")) {
            result.replace("function", "ƒ")
        } else {
            "ƒ $result"
        }
    }
}