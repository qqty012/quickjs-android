package com.quickjs;

abstract class Plugin {
    abstract fun setup(context:JSContext)

    abstract fun close(context:JSContext)
}
