package com.quickjs.plugin

import com.quickjs.JSContext
import com.quickjs.Plugin
import com.quickjs.classes.Url

class URLPlugin: Plugin() {
    override fun setup(context: JSContext) {

        val url = Url(context)
        context.global["URL"] = url["URL"]

    }
    override fun close(context: JSContext) {

    }
}