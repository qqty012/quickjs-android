package com.quickjs.plugin

import android.util.SparseArray
import com.quickjs.JSContext
import com.quickjs.JSFunction
import com.quickjs.JSValue
import com.quickjs.Plugin

class TimerPlugin : Plugin() {
    private val intervalList = SparseArray<Runnable?>(10)
    private val timeoutList = SparseArray<Runnable?>(10)

    private fun getIntervalListIsNullOfIndex(): Int {
        var index = 0
        while (intervalList[index] != null) {
            index++
        }
        return index
    }

    private fun getTimeoutListIsNullOfIndex(): Int {
        var index = 0
        while (timeoutList[index] != null) {
            index++
        }
        return index
    }

    private fun setTimeout(context: JSContext) {

        context.registerJavaMethod( "setTimeout") { receiver, args ->
            if (args.size < 2) throw IllegalArgumentException("setTimeout needs a callback and a delay")
            val callback = args[0]
            val delay = args[1]
            val params = arrayListOf<Any?>()
            if (args.size > 2) {
                for (i in 2 until args.size) {
                    val x = args[i] as JSValue
                    when (x.getType()) {
                        JSValue.TYPE.STRING -> params.add(args[i])
                        JSValue.TYPE.INTEGER -> params.add(args[i])
                        JSValue.TYPE.DOUBLE -> params.add(args[i])
                        JSValue.TYPE.BOOLEAN -> params.add(args[i])
                        else -> params.add(args[i])
                    }
                }
            }
            if (callback is JSFunction) {
                val delayer = Runnable {
                    callback.call(null, *params.toArray())
                    callback.close()
                }
                val key = getTimeoutListIsNullOfIndex()
                timeoutList.put(key, delayer)
                context.quickJS.native.handler.postDelayed(delayer, delay as Long)
                return@registerJavaMethod key
            }
            return@registerJavaMethod null
        }
    }


    override fun setup(context: JSContext) {
        setTimeout(context)
        clearTimeout(context)

        setInterval(context)
        clearInterval(context)
    }

    private fun clearInterval(context: JSContext) {
        context.registerJavaMethod( "clearInterval") { receiver, args ->
            if (args.isEmpty()) return@registerJavaMethod null
            val id = args[0] as? Long ?: return@registerJavaMethod null
            intervalList[id.toInt()]?.let {
                context.quickJS.native.handler.removeCallbacks(it)
            }
//            intervalList[id.toInt()] = null
        }
    }

    private fun clearTimeout(context: JSContext) {
        context.registerJavaMethod( "clearTimeout") { receiver, args ->
            if (args.isEmpty()) return@registerJavaMethod null
            val id = args[0] as? Int ?: return@registerJavaMethod null
            timeoutList[id]?.let { context.quickJS.native.handler.removeCallbacks(it) }
//            timeoutList[id] = null
        }
    }

    private fun setInterval(context: JSContext) {
        context.registerJavaMethod( "setInterval", { receiver, args ->
            if (args.size < 2) throw IllegalArgumentException("setInterval needs a callback and a delay")
            val callback = args[0] as? JSFunction
            val delay = args[1] as? Long
            if (callback !is JSFunction || delay !is Long) return@registerJavaMethod null
            val task = object : Runnable {
                override fun run() {
                    callback.call(null)
                    context.quickJS.native.handler.postDelayed(this, delay)
                }
            }
            val key = getIntervalListIsNullOfIndex()
            intervalList.put(key, task)
            context.quickJS.native.handler.postDelayed(task, delay)
            return@registerJavaMethod key
        })
    }

    override fun close(context: JSContext) {
        intervalList.clear()
        timeoutList.clear()
    }
}
