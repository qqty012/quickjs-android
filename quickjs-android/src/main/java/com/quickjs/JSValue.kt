package com.quickjs

import java.io.Closeable


open class JSValue: Closeable {
    @Volatile
    var released: Boolean = false

    companion object {
        const val TYPE_NULL = 0
        const val TYPE_UNKNOWN = 0
        const val TYPE_INTEGER = 1
        const val TYPE_DOUBLE = 2
        const val TYPE_BOOLEAN = 3
        const val TYPE_STRING = 4
        const val TYPE_JS_ARRAY = 5
        const val TYPE_JS_OBJECT = 6
        const val TYPE_JS_FUNCTION = 7
        const val TYPE_JS_EXCEPTION = 8
        const val TYPE_UNDEFINED = 99

        private val typeMap = mapOf(
            TYPE_UNDEFINED to TYPE.UNDEFINED,
            TYPE_INTEGER to TYPE.INTEGER,
            TYPE_DOUBLE to TYPE.DOUBLE,
            TYPE_BOOLEAN to TYPE.BOOLEAN,
            TYPE_STRING to TYPE.STRING,
            TYPE_JS_ARRAY to TYPE.JS_ARRAY,
            TYPE_JS_FUNCTION to TYPE.JS_FUNCTION,
            TYPE_JS_OBJECT to TYPE.JS_OBJECT
        )

        fun checkType(value: Any?, expectedType: TYPE): Any? {
            return when (expectedType) {
                TYPE.INTEGER -> value as? Int
                TYPE.DOUBLE -> value as? Double
                TYPE.BOOLEAN -> value as? Boolean
                TYPE.STRING -> value as? String
                TYPE.JS_ARRAY -> value as? JSArray
                TYPE.JS_OBJECT -> value as? JSObject
                else -> value
            }
        }

        fun undefined(context: JSContext): JSValue {
            return context.native.undefined(context.contextPtr)
        }

        fun NULL(): JSValue? = null
    }

    enum class TYPE(val value: Int) {
        NULL(TYPE_NULL),
        UNKNOWN(TYPE_UNKNOWN),
        UNDEFINED(TYPE_UNDEFINED),
        INTEGER(TYPE_INTEGER),
        DOUBLE(TYPE_DOUBLE),
        BOOLEAN(TYPE_BOOLEAN),
        STRING(TYPE_STRING),
        JS_ARRAY(TYPE_JS_ARRAY),
        JS_OBJECT(TYPE_JS_OBJECT),
        JS_FUNCTION(TYPE_JS_FUNCTION)
    }

    val context: JSContext
    @JvmField
    val tag: Long
    @JvmField
    val uInt32: Int
    @JvmField
    val uFloat64: Double
    @JvmField
    val uPtr: Long
    val quickJS: QuickJS

    constructor(context: JSContext, tag: Long, uInt32: Int, uFloat64: Double, uPtr: Long) {
        this.context = context
        this.tag = tag
        this.uInt32 = uInt32
        this.uFloat64 = uFloat64
        this.uPtr = uPtr
        quickJS = context.quickJS
    }

    constructor(context: JSContext, value: JSValue) : this(context, value.tag, value.uInt32, value.uFloat64, value.uPtr) {
        value.released = true
        context.let {
            it.removeObjRef(value)
            it.addObjRef(this)
            it.checkReleased()
        }
    }

    protected fun checkReleased() {
        if (context.isReleased()) {
            throw IllegalStateException("JSContext 已释放")
        }
    }

    protected fun checkRuntime(value: JSValue) {
        if (value.context != context) {
            throw IllegalArgumentException("JSValue 不属于当前 JSContext")
        }
    }

    fun getContextPtr(): Long = context.contextPtr

    override fun close() {
        if (released) return
        released = true
        context.releaseObjRef(this, false)
    }


    fun isUndefined(): Boolean =
        context.native.isUndefined(getContextPtr(), this)

    fun getType(): TYPE {
        context.checkReleased()
        return typeMap[context.native.getObjectType(getContextPtr(), this)] ?: TYPE.UNKNOWN
    }

    override fun equals(other: Any?): Boolean =
        other is JSValue && other.tag == this.tag


    protected fun getNative(): QuickJSNative = context.native

//    fun postEventQueue(event: Runnable) = getQuickJS().postEventQueue(event)

    override fun toString(): String {
        return context.toJSString(this)
    }

    override fun hashCode(): Int {
        var result = context.hashCode()
        result = 31 * result + tag.hashCode()
        result = 31 * result + uInt32
        result = 31 * result + uFloat64.hashCode()
        result = 31 * result + uPtr.hashCode()
        result = 31 * result + released.hashCode()
        return result
    }
}