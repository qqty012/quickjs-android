package com.quickjs

class QuickJSException: RuntimeException {
    private var name: String? = null

    constructor(name: String, message: String): super("$name,$message") {
        this.name = name
    }

    constructor(message: String, cause: Throwable): super(message, cause)

    constructor(cause: Throwable): super(cause)

    fun getName(): String? {
        return name
    }
}
