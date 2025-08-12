package com.quickjs.plugin

import com.quickjs.JSArray
import com.quickjs.JSClass
import com.quickjs.JSContext
import com.quickjs.JSFunction
import com.quickjs.JSObject
import com.quickjs.JSValue
import com.quickjs.Plugin


class BufferPlugin : Plugin() {

    override fun setup(context: JSContext) {
        val buffer = context.global.addJavascriptInterface(this, "Buffer")
        buffer.set("from", from(context))
        buffer.set("alloc", alloc(context))
    }

    override fun close(context: JSContext) {
    }

    fun from(context: JSContext): JSFunction {
        return JSFunction(context, { _, args ->
            val input = args.getOrNull(0)
            val encoding =  when(args.getOrNull(1)?.toString() ?: "utf-8") {
                "utf-8" -> Charsets.UTF_8
                "utf-16le" -> Charsets.UTF_16LE
                "utf-16be" -> Charsets.UTF_16BE
                "ascii" -> Charsets.US_ASCII
                "latin1" -> Charsets.ISO_8859_1
                else -> Charsets.UTF_8
            }
            val bytes: ByteArray = when (input) {
                is String -> input.toByteArray(encoding)
                is JSArray -> {
                    val arr = ByteArray(input.length())
                    for (i in 0 until input.length()) {
                        arr[i] = (input.get(i) as Number).toByte()
                    }
                    arr
                }
                is JSObject -> {
                    // 支持传入 Array/TypedArray
                    input.asArrayOrNull()?.map { (it as Number).toByte() }?.toByteArray()
                        ?: ByteArray(0)
                }
                else -> ByteArray(0)
            }
            BufferObject(context, bytes)
        })
    }

    fun alloc(context: JSContext): JSFunction {
        return JSFunction(context, { _, args ->
            val len = (args.getOrNull(0) as? Number)?.toInt() ?: 0
            BufferObject(context, ByteArray(len))
        })
    }

    class BufferObject(context: JSContext, private val bytes: ByteArray) : JSClass(context, "Buffer") {
        init {
            set("length", bytes.size)
            set("toString", toStr(context))
            set("toJSON", toJson(context))
            set("readUInt8", readUInt8(context))
            set("write", write(context))
            // 可继续实现更多 Buffer 方法
        }

        fun toJson(context: JSContext): JSFunction {
            return JSFunction(context,  { _, _ ->
                JSObject(context, mapOf("type" to "Buffer", "data" to bytes.map { it.toInt() and 0xFF }))
            })
        }
        fun readUInt8(context: JSContext): JSFunction {
            return JSFunction(context,  { _, args ->
                val offset = (args.getOrNull(0) as? Number)?.toInt() ?: 0
                if (offset in bytes.indices) bytes[offset].toInt() and 0xff else throw IndexOutOfBoundsException()
            })
        }
        fun write(context: JSContext): JSFunction {
            return JSFunction(context,  { _, args ->
                val str = args.getOrNull(0)?.toString() ?: ""
                val offset = (args.getOrNull(1) as? Number)?.toInt() ?: 0
                val bytesToWrite = str.toByteArray()
                val len = minOf(bytesToWrite.size, bytes.size - offset)
                System.arraycopy(bytesToWrite, 0, bytes, offset, len)
                len
            })
        }

        fun toStr(context: JSContext): JSFunction {
            return JSFunction(context,  { _, args ->
                val encoding = args.getOrNull(0)?.toString() ?: "utf8"
                when (encoding.lowercase()) {
                    "utf8", "utf-8" -> bytes.toString(Charsets.UTF_8)
                    "hex" -> bytes.joinToString("") { "%02x".format(it) }
                    "base64" -> android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                    else -> throw IllegalArgumentException("Unsupported encoding: $encoding")
                }
            })
        }

        override fun newConstructor(obj: JSValue, vararg args: Any?): JSValue? {
            throw IllegalArgumentException("The first argument must be of type string or an instance of Buffer, ArrayBuffer, or Array or an Array-like Object. Received undefined")
        }
    }
}