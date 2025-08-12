package com.quickjs.classes

import com.quickjs.JSClass
import com.quickjs.JSContext
import com.quickjs.JSFunction
import com.quickjs.JSObject
import com.quickjs.JSValue

class Url(ctx: JSContext): JSClass(ctx, "URL") {

    companion object {
        private val objectUrlMap = mutableMapOf<String, JSValue>()
    }

    init {
        clazz.set("createObjectURL", JSFunction(context, { _, args ->
            val blob = args.getOrNull(0)
                ?: throw IllegalArgumentException("Blob required")
            val id = "blob:${System.currentTimeMillis()}:${blob.hashCode()}"
            objectUrlMap[id] = blob as JSValue
            return@JSFunction id
        }))
        clazz.set("revokeObjectURL", JSFunction(context) { _, args ->
            val url = args.getOrNull(0)?.toString()
            if (url != null) {
                objectUrlMap.remove(url)
            }
        })
        clazz.set("parse", JSFunction(context) { obj, args ->
            return@JSFunction parse(JSObject(ctx), args)
        })

        this.set("URL", clazz)
    }

    fun parse(obj: JSValue, vararg args: Any?): JSValue {
        if (args.isEmpty()) {
            return context.newError("Failed to construct 'URL': 1 argument required, but only 0 present.")
        }
        val url = args[0] as String
        val base = if (args.size > 1) args[1] as String else ""

        // 解析URL
        val u = try {
            if (base.isNotEmpty()) java.net.URL(java.net.URL(base), url)
            else java.net.URL(url)
        } catch (e: Exception) {
            return context.newError("Invalid URL $e")
        }

        // 给 obj 添加属性
        if (obj is JSObject) {
            obj.set("href", u.toString())
            obj.set("protocol", u.protocol + ":")
            obj.set("host", u.host + if (u.port != -1) ":${u.port}" else "")
            obj.set("hostname", u.host)
            obj.set("port", if (u.port != -1) u.port.toString() else "")
            obj.set("pathname", u.path)
            obj.set("search", u.query?.let { "?$it" } ?: "")
            obj.set("hash", u.ref?.let { "#$it" } ?: "")
            obj.set("origin", "${u.protocol}://${u.host}${if (u.port != -1) ":${u.port}" else ""}")
            // 可继续扩展 username/password/searchParams 等
            u.userInfo?.let {
                val parts = it.split(":")
                obj.set("username", parts[0])
                obj.set("password", parts.getOrNull(1) ?: "")
            } ?: {
                obj.set("username", "")
                obj.set("password", "")
            }
            // 处理 searchParams
            val searchParams = u.query?.split("&")?.associate {
                val parts = it.split("=")
                parts[0] to parts.getOrNull(1)
            }?: emptyMap()
            val searchParamsObj = JSObject(context)
            searchParams.forEach { (key, value) ->
                searchParamsObj.set(key, value?: "")
            }
        }
        return obj
    }

    override fun newConstructor(obj: JSValue, vararg args: Any?): JSValue? {
        return parse(obj, *args)
    }

}