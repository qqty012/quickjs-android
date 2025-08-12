package com.quickjs.classes

import com.quickjs.JSClass
import com.quickjs.JSContext
import com.quickjs.JSValue

class Buffer(context: JSContext) : JSClass(context, "Buffer") {

    override fun newConstructor(obj: JSValue, vararg args: Any?): JSValue? {
        if (args.isEmpty()) {
            throw IllegalArgumentException("Buffer constructor must have at least 1 argument")
        }
        return null
    }

}