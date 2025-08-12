package com.quickjs

class JSException(context: JSContext, tag: Long, uInt32: Int, uFloat64: Double, uPtr: Long) : JSObject(context, tag, uInt32, uFloat64, uPtr) {

    val name: String
        get() = this["name"].toString()

    val message: String
        get() = this["message"].toString()

    val stack: String
        get() = this["stack"].toString()

    override fun toString(): String {
        return "$name：$message\n$stack"
    }
}