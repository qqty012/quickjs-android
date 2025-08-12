package com.quickjs

import android.webkit.JavascriptInterface
import org.json.JSONArray
import org.json.JSONObject
import java.lang.reflect.Method
import java.util.ArrayList
import java.util.Collection

open class JSObject : JSValue {

    constructor(context: JSContext) : super(context, context.native.initNewJSObject(context.contextPtr))

    constructor(context: JSContext, jsonObject: JSONObject) : this(context) {
        jsonObject.appendTo(this)
    }
    constructor(context: JSContext, map: Map<String, Any?>) : this(context) {
        map.forEach { (key, value) ->
            when (value) {
                is Int -> set(key, value)
                is Double -> set(key, value)
                is Boolean -> set(key, value)
                is String -> set(key, value)
                is JSValue -> set(key, value)
                is Array<*>,
                is ArrayList<*> -> set(key, buildArray(context, value as Collection<*>))
                else -> {
                    println(value?.javaClass)
                }
            }
        }
    }

    constructor(context: JSContext, value: JSValue) : super(context, value)

    constructor(context: JSContext, tag: Long, uInt32: Int, uFloat64: Double, uPtr: Long) : super(context, tag, uInt32, uFloat64, uPtr)

    companion object {
        private fun JSONObject.appendTo(jsObject: JSObject) {
            keys().forEach { key ->
                when (val value = opt(key)) {
                    is String -> jsObject.set(key, value)
                    is Int -> jsObject.set(key, value)
                    is Boolean -> jsObject.set(key, value)
                    is Number -> jsObject.set(key, value.toDouble())
                    is JSONObject -> jsObject.set(key, JSObject(jsObject.context, value))
                    is JSONArray -> jsObject.set(key, JSArray(jsObject.context, value))
                }
            }
        }

        private fun buildArray(context: JSContext, collection: java.util.Collection<*>): JSArray {
            val array = JSArray(context)
            collection.forEach {
                when (it) {
                    is Int -> array.push(it)
                    is Double -> array.push(it)
                    is Boolean -> array.push(it)
                    is String -> array.push(it)
                    is JSValue -> array.push(it)
                    else -> (null as? JSValue?)?.let { array.push(it) }
                }
            }
            return array
        }

        private fun Method.getParameters(args: JSArray): Array<Any?> {
            return genericParameterTypes.mapIndexed { index, type ->
                when (type) {
                    Int::class.javaPrimitiveType, Int::class.javaObjectType -> args.getInteger(index)
                    Double::class.javaPrimitiveType, Double::class.javaObjectType -> args.getDouble(index)
                    Boolean::class.javaPrimitiveType, Boolean::class.javaObjectType -> args.getBoolean(index)
                    String::class.java -> args.getString(index)
                    JSArray::class.java -> args.getArray(index)
                    JSObject::class.java, JSFunction::class.java -> args.getObject(index)
                    else -> throw RuntimeException("Type error")
                }
            }.toTypedArray()
        }
    }

    protected open fun setObject(key: String, value: Any?): JSObject {
        context.checkReleased()
        context.native.set(getContextPtr(), this, key, value)
        return this
    }

    operator fun get(key: String): Any? {
        if (key == "prototype") {
            return getPrototype()
        }
        return get(TYPE.UNKNOWN, key)
    }

    open fun get(expectedType: TYPE?, key: String): Any? {
        context.checkReleased()
        val type = expectedType ?: TYPE.UNKNOWN
        val obj = context.native.get(getContextPtr(), type.value, this, key)
        return checkType(obj, type)
    }

    open fun set(key: String, value: Int): JSObject = setObject(key, value)
    open fun set(key: String, value: Double): JSObject = setObject(key, value)
    open fun set(key: String, value: String): JSObject = setObject(key, value)
    open fun set(key: String, value: Boolean): JSObject = setObject(key, value)
    open fun set(key: String, value: JSValue?): JSObject {
        context.checkRuntime(value)
        return setObject(key, value)
    }

    operator fun set(key: String, value: Any?) {
        if (key == "prototype") {
            setPrototype(value as JSObject)
        }
        setObject(key, value)
    }

    fun getInteger(key: String): Int = get(TYPE.INTEGER, key) as Int
    fun getBoolean(key: String): Boolean = get(TYPE.BOOLEAN, key) as Boolean
    fun getDouble(key: String): Double = get(TYPE.DOUBLE, key) as Double
    fun getString(key: String): String? = get(TYPE.STRING, key) as String?
    fun getArray(key: String): JSArray = get(TYPE.JS_ARRAY, key) as JSArray
    fun getObject(key: String): JSObject = get(TYPE.JS_OBJECT, key) as JSObject

    fun getType(key: String): TYPE {
        val value = context.let {
            getNative().getValue(it.contextPtr, this@JSObject, key)
        }
        return value?.getType() ?: TYPE.NULL
    }

    open fun registerJavaMethod(jsFunctionName: String, callback: JavaCallback): JSFunction {
        context.checkReleased()
        val functionHandle = getNative().registerJavaMethod(getContextPtr(), this, jsFunctionName, callback.hashCode())
        context.registerCallback(callback)
        return functionHandle
    }

    /*open fun registerJavaMethod(jsFunctionName: String, callback: JavaVoidCallback): JSFunction {
        context.checkReleased()
        val functionHandle = getNative().registerJavaMethod(getContextPtr(), this, jsFunctionName, callback.hashCode(), true)
        context.registerCallback(callback)
        return functionHandle
    }*/

    open fun registerClass(className: String, javaConstructorCallback: JavaConstructorCallback): JSFunction {
        val callback = object : JavaCallback {
            override fun invoke(
                receiver: JSObject?,
                args: Array<out Any?>
            ): Any? {
                val thisObj = JSObject(receiver?.context ?: context)
                return javaConstructorCallback.invoke(thisObj, args)
            }
        }

        val functionHandle = getNative().newClass(getContextPtr(), callback.hashCode())
        context.registerCallback(callback)
        set(className, functionHandle)
        return functionHandle
    }

    open fun executeFunction(name: String, vararg parameters: Any): Any? =
        executeFunction(TYPE.UNKNOWN, name, parameters)
    fun executeIntegerFunction(name: String, vararg parameters: Any): Int = executeFunction(TYPE.INTEGER, name, parameters) as Int
    fun executeDoubleFunction(name: String, vararg parameters: Any): Double = executeFunction(TYPE.DOUBLE, name, parameters) as Double
    fun executeBooleanFunction(name: String, vararg parameters: Any): Boolean = executeFunction(TYPE.BOOLEAN, name, parameters) as Boolean
    fun executeStringFunction(name: String, vararg parameters: Any): String = executeFunction(TYPE.STRING, name, parameters) as String
    fun executeArrayFunction(name: String, vararg parameters: Any): JSArray = executeFunction(TYPE.JS_ARRAY, name, parameters) as JSArray
    fun executeObjectFunction(name: String, vararg parameters: Any): JSObject = executeFunction(TYPE.JS_OBJECT, name, parameters) as JSObject
    fun executeVoidFunction(name: String, vararg parameters: Any) {
        executeFunction(TYPE.NULL, name, parameters)
    }

    fun executeFunction2(name: String, vararg parameters: Any?): Any? {
        context.checkReleased()
        return QuickJS.executeFunction(context, this, name, parameters)
    }

    fun contains(key: String): Boolean {
        context.checkReleased()
        return getNative().contains(getContextPtr(), this, key)
    }

    fun getKeys(): Array<String> {
        context.checkReleased()
        return getNative().getKeys(getContextPtr(), this)
    }

    protected open fun executeFunction(expectedType: TYPE, name: String, vararg parameters: Any?): Any? {
        context.checkReleased()
        parameters.filterIsInstance<JSValue>().forEach { context.checkRuntime(it) }
        val obj = getNative().executeFunction(getContextPtr(), expectedType.value, this, name, parameters)
        QuickJS.checkException(context)
        return checkType(obj, expectedType)
    }

    fun appendJavascriptInterface(obj: Any) {
        appendJavascriptInterface(this, obj)
    }

    fun addJavascriptInterface(obj: Any, interfaceName: String): JSObject {
        context.checkReleased()
        val jsObject = JSObject(context)
        appendJavascriptInterface(jsObject, obj)
        set(interfaceName, jsObject)
        return jsObject
    }

    private fun appendJavascriptInterface(jsObject: JSObject, obj: Any) {
        val methods = obj.javaClass.methods
        for (method in methods) {
            if (method.getAnnotation(JavascriptInterface::class.java) == null) {
                continue
            }
            val functionName = method.name
            if (method.returnType == Void.TYPE) {
                jsObject.registerJavaMethod(functionName) { _, args ->
                    try {
                        method.invoke(obj, *getParameters(method, args))
                    } catch (e: Exception) {
                        e.printStackTrace()
                        throw java.lang.RuntimeException(e)
                    }
                }
            } else {
                jsObject.registerJavaMethod(functionName,  { _, args ->
                    return@registerJavaMethod method.invoke(
                        obj,
                        *getParameters(method, args)
                    )
                })
            }
        }
    }


    private fun getParameters(method: Method, vararg args: Any): Array<Any?> {
        val objects = arrayOfNulls<Any>(args.size)
        val types = method.genericParameterTypes
        for (i in objects.indices) {
            val type = types[i]
            if (type === Int::class.javaPrimitiveType || type === Int::class.java) {
                objects[i] = args[i] as Int
            } else if (type === Double::class.javaPrimitiveType || type === Double::class.java) {
                objects[i] = args[i] as Double
            } else if (type === Boolean::class.javaPrimitiveType || type === Boolean::class.java) {
                objects[i] = args[i] as Boolean
            } else if (type === String::class.java) {
                objects[i] = args[i] as String
            } else if (type === JSArray::class.java) {
                objects[i] = args[i] as JSArray
            } else if (type === JSObject::class.java || type === JSFunction::class.java) {
                objects[i] = args[i] as JSObject
            } else if (type == Array::class.java) {
                objects[i] = args[i]
            } else {
                throw java.lang.RuntimeException("Type error")
            }
        }
        return objects
    }

    fun asArrayOrNull(): List<Any?>? {
        val length = this.get("length") as? Number ?: return null
        return (0 until length.toInt()).map { this.get(it.toString()) }
    }

    fun toJSONObject(): JSONObject {
        val jsonObject = JSONObject()
        getKeys().forEach { key ->
            when (val value = get(key)) {
                is Number, is String, is Boolean -> jsonObject.put(key, value)
                is JSArray -> jsonObject.put(key, value.toJSONArray())
                is JSObject -> jsonObject.put(key, value.toJSONObject())
            }
        }
        return jsonObject
    }

    fun getPrototype(): JSObject {
        return getNative().getPrototype(getContextPtr(), this)
    }

    fun setPrototype(prototype: JSObject) {
        getNative().setPrototype(getContextPtr(), this, prototype)
    }

    private fun isError(): Boolean {
        return context.native.isError(getContextPtr(), this)
    }

    override fun toString(): String {
        if (isError()) {
            return "${get("name")}: ${get("message")}\n${get("stack")}"
        }
        return getKeys().associateWith { key -> get(key)?.toString() ?: "null" }.toString()
    }

    open class Undefined(context: JSContext, tag: Long, uInt32: Int, uFloat64: Double, uPtr: Long) :
        JSObject(context, tag, uInt32, uFloat64, uPtr) {

        init {
            released = true
        }

        override fun setObject(key: String, value: Any?): JSObject {
            throw UnsupportedOperationException()
        }

        override fun get(expectedType: TYPE?, key: String): Any? {
            throw UnsupportedOperationException()
        }

        override fun registerJavaMethod(
            jsFunctionName: String,
            callback: JavaCallback
        ): JSFunction {
            throw UnsupportedOperationException()
        }

        override fun executeFunction(expectedType: TYPE, name: String, vararg parameters: Any?): Any? {
            throw UnsupportedOperationException()
        }

        override fun hashCode(): Int = TYPE_UNDEFINED

        override fun toString(): String = "undefined"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            if (!super.equals(other)) return false
            return true
        }
    }
}