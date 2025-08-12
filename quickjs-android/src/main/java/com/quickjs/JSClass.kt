package com.quickjs

abstract class JSClass(context: JSContext, name: String): JSObject(context) {
    val clazz: JSFunction = this.registerClass(name, object : JavaConstructorCallback {
        override fun invoke(p1: JSObject, p2: Array<out Any?>): JSValue? {
           return newConstructor(p1, *p2)
        }
    })

    abstract fun newConstructor(obj: JSValue, vararg args: Any?): JSValue?
    fun get(): JSValue {
        return clazz
    }
}