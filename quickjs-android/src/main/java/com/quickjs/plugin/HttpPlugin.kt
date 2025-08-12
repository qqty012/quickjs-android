package com.quickjs.plugin

import com.quickjs.JSArray
import com.quickjs.JSContext
import com.quickjs.JSFunction
import com.quickjs.JSObject
import com.quickjs.JSValue.Companion.NULL
import com.quickjs.Plugin
import com.quickjs.PromiseHelper
import java.net.HttpURLConnection
import java.net.URL
import kotlin.collections.component1
import kotlin.collections.component2

class HttpPlugin: Plugin() {
    override fun setup(context: JSContext) {
        val http = context.global.addJavascriptInterface(HttpPlugin(), "http")
        http.set("get", get(context))
        http.set("post", post(context))
    }

    override fun close(context: JSContext) {
    }

    fun get(context: JSContext): JSFunction {
        return JSFunction(context, { _, args ->
            val url = args[0] as? String ?: throw NullPointerException("url is null")
            var headers: Map<String, String>? = null
            var callback: JSFunction? = null

            if (args.size == 2) {

                if (args[1] is JSFunction) {
                    // 只有一个参数，认为是回调函数
                    callback = args[1] as JSFunction
                } else if (args[1] is JSObject) {
                    // 第二个参数是 headers 对象
                    val headersObj = args[1] as JSObject
                    headers = headersObj.getKeys().associateWith { headersObj.get(it)!!.toString() }
                }
            } else if (args.size >= 3) {
                val headersObj = args[1]
                headers = if (headersObj is JSObject) {
                    headersObj.getKeys().associateWith { headersObj.get(headersObj.getKeys().toString())!!.toString() }
                } else {
                    null
                }
                callback = args[2] as JSFunction
            }


            (URL(url).openConnection() as HttpURLConnection).apply {
                headers?.forEach { (key, value) ->
                    setRequestProperty(key, value)
                }
            }.let { connection ->
                return@JSFunction handleCallbackOrPromise(context, callback) {
                    connection.connect()
                    val code = connection.responseCode
                    val message = connection.responseMessage
                    val result = if (code < 400) connection.inputStream.bufferedReader()
                        .use { it.readText() } else message
                    val header = connection.headerFields.let {
                        JSObject(context).apply {
                            it.forEach { (key, value) ->
                                if (key != null) set(key, value?.joinToString(";") ?: "null")
                            }
                        }
                    }
                    return@handleCallbackOrPromise JSObject(context).apply {
                        set("httpCode", code)
                        set("message", message)
                        set("headers", header)
                        set("body", result)
                    }
                }
            }
        })
    }

    fun post(context: JSContext): JSFunction {
        return JSFunction(context, { _, args ->
            val url = args[0] as? String ?: throw NullPointerException("url is null")
            val data = args[1] as? String
            var headers: Map<String, String>? = null
            var callback: JSFunction? = null

            if (args.size == 3) {
                if (args[2] is JSFunction) {
                    callback = args[2] as JSFunction
                } else if (args[2] is JSObject) {
                    val headersObj = args[2] as JSObject
                    headers = headersObj.getKeys().associateWith { headersObj.get(it)!!.toString() }
                }
            } else if (args.size >= 4) {
                val headersObj = args[2]
                headers = if (headersObj is JSObject) {
                    headersObj.getKeys().associateWith { headersObj.get(it)!!.toString() }
                } else {
                    null
                }
                callback = args[3] as JSFunction
            }

            (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                headers?.forEach { (key, value) ->
                    setRequestProperty(key, value)
                }
                if (data != null) outputStream.write(data.toByteArray())
            }.let { connection ->
                return@JSFunction handleCallbackOrPromise(context, callback) {
                    connection.connect()
                    val code = connection.responseCode
                    val message = connection.responseMessage
                    val result = if (code < 400) connection.inputStream.bufferedReader()
                        .use { it.readText() } else message
                    val header = connection.headerFields.let {
                        JSObject(context).apply {
                            it.forEach { (key, value) ->
                                if (key != null) set(key, value?.joinToString(";") ?: "null")
                            }
                        }
                    }
                    return@handleCallbackOrPromise JSObject(context).apply {
                        set("httpCode", code)
                        set("message", message)
                        set("headers", header)
                        set("body", result)
                    }
                }
            }
        })
    }

    private fun handleCallbackOrPromise(
        ctx: JSContext,
        callback: JSFunction?,
        block: () -> Any
    ): Any? {
        val handler = ctx.quickJS.native.handler
        return if (callback != null) {
            Thread {
                try {
                    val result = block()
                    handler.post {
                        callback.call(null, result)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    handler.post {
                        callback.call(null, JSObject(ctx).apply {
                            set("code" , -1)
                            set("message", e.message ?: "")
                            set("error", e.fillInStackTrace().toString())
                            set("body", NULL())
                        })
                    }
                }
            }.start()
            NULL()
        } else {
            PromiseHelper(ctx).create { resolve, reject ->
                Thread {
                    try {
                        val result = block()
                        handler.post { resolve.call(null, result) }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        handler.post {
                            reject.call(null, JSArray(ctx).apply {
                                JSObject(ctx).apply {
                                    set("code" , -1)
                                    set("message", e.message ?: "")
                                    set("error", e.fillInStackTrace().toString())
                                    set("body", NULL())
                                }
                            })
                        }
                    }
                }.start()
            }
        }
    }
}