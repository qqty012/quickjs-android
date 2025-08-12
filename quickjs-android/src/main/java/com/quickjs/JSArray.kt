package com.quickjs

import org.json.JSONArray
import org.json.JSONObject

class JSArray : JSObject, Collection<Any?> {

    constructor(context: JSContext) : super(
        context,
        context.native.initNewJSArray(context.contextPtr)
    )

    constructor(context: JSContext, tag: Long, uInt32: Int, uFloat64: Double, uPtr: Long)
            : super(context, tag, uInt32, uFloat64, uPtr)

    constructor(context: JSContext, jsonArray: JSONArray) : this(context) {
        append(this, jsonArray)
    }

    companion object {
        fun append(jsArray: JSArray, jsonArray: JSONArray?) {
            if (jsonArray == null) return
            for (i in 0 until jsonArray.length()) {
                when (val obj = jsonArray.opt(i)) {
                    is String -> jsArray.push(obj)
                    is Int -> jsArray.push(obj)
                    is Boolean -> jsArray.push(obj)
                    is Number -> jsArray.push(obj.toDouble())
                    is JSONObject -> jsArray.push(JSObject(jsArray.context, obj))
                    is JSONArray -> jsArray.push(JSArray(jsArray.context, obj))
                }
            }
        }
    }

    fun get(index: Int): Any? = get(TYPE.UNKNOWN, index)

    fun get(expectedType: TYPE?, index: Int): Any? {
        context.checkReleased()
        val type = expectedType ?: TYPE.UNKNOWN
        val obj = getNative().arrayGet(getContextPtr(), type.value, this, index)
        return checkType(obj, type)
    }

    private fun pushObject(value: Any?): JSArray {
        context.checkReleased()
        getNative().arrayAdd(getContextPtr(), this, value)
        return this
    }

    fun getInteger(index: Int): Int =
        (get(TYPE.INTEGER, index) as? Int) ?: 0

    fun getBoolean(index: Int): Boolean =
        (get(TYPE.BOOLEAN, index) as? Boolean) ?: false

    fun getDouble(index: Int): Double =
        (get(TYPE.DOUBLE, index) as? Double) ?: 0.0

    fun getString(index: Int): String? =
        get(TYPE.STRING, index) as? String

    fun getObject(index: Int): JSObject? =
        get(TYPE.JS_OBJECT, index) as? JSObject

    fun getArray(index: Int): JSArray? =
        get(TYPE.JS_ARRAY, index) as? JSArray

    fun getType(index: Int): TYPE {
        context.checkReleased()
        val value = context.native.arrayGetValue(getContextPtr(), this, index)
        return value?.getType() ?: TYPE.NULL
    }

    fun push(value: Int): JSArray = pushObject(value)
    fun push(value: Double): JSArray = pushObject(value)
    fun push(value: String?): JSArray = pushObject(value)
    fun push(value: Boolean): JSArray = pushObject(value)
    fun push(value: JSValue?): JSArray {
        context.checkRuntime(value)
        return pushObject(value)
    }

    fun length(): Int = getInteger("length")

    fun toJSONArray(): JSONArray {
        val jsonArray = JSONArray()
        for (i in 0 until length()) {
            val obj = get(i)
            when (obj) {
                is Undefined, is JSFunction -> {}
                is Number, is String, is Boolean -> jsonArray.put(obj)
                is JSArray -> jsonArray.put(obj.toJSONArray())
                is JSObject -> jsonArray.put(obj.toJSONObject())
            }
        }
        return jsonArray
    }

    override fun toString(): String {
        val result = ArrayList<String>()
        for (obj in this) {
            result.add(obj?.toString() ?: "null")
        }
        return result.toString()
    }

    // -------- Collection<Any?> 实现 --------

    override val size: Int
        get() = length()

    override fun isEmpty(): Boolean = size == 0

    override fun iterator(): Iterator<Any?> = object : Iterator<Any?> {
        private var index = 0
        override fun hasNext(): Boolean = index < size
        override fun next(): Any? {
            if (index >= size) throw NoSuchElementException()
            return get(index++)
        }
    }

    override fun contains(element: @UnsafeVariance Any?): Boolean {
        for (i in 0 until size) {
            if (get(i) == element) return true
        }
        return false
    }

    override fun containsAll(elements: Collection<@UnsafeVariance Any?>): Boolean {
        for (e in elements) {
            if (!contains(e)) return false
        }
        return true
    }
}