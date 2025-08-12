#include <jni.h>
#include <string>
#include "quickjs/quickjs.h"
#include <vector>
#include "quickjs/cutils.h"


#include <queue>

#include <pthread.h>
#include <unistd.h>

#include <android/log.h>
#define LOG_TAG "TEST"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG,LOG_TAG,__VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

#include <linux/input.h>


#if INTPTR_MAX >= INT64_MAX
#define JS_PTR64
#define JS_PTR64_DEF(a) a
#else
#define JS_PTR64_DEF(a)
#endif

#ifndef JS_PTR64
#define JS_NAN_BOXING
#endif


const int TYPE_NULL = 0;
const int TYPE_UNKNOWN = 0;
const int TYPE_INTEGER = 1;
const int TYPE_INT_32_ARRAY = 1;
const int TYPE_DOUBLE = 2;
const int TYPE_FLOAT_64_ARRAY = 2;
const int TYPE_BOOLEAN = 3;
const int TYPE_STRING = 4;
const int TYPE_JS_ARRAY = 5;
const int TYPE_JS_OBJECT = 6;
const int TYPE_JS_FUNCTION = 7;
const int TYPE_JS_EXCEPTION = 8;
const int TYPE_BYTE = 9;
const int TYPE_INT_8_ARRAY = 9;
const int TYPE_JS_ARRAY_BUFFER = 10;
const int TYPE_UNSIGNED_INT_8_ARRAY = 11;
const int TYPE_UNSIGNED_INT_8_CLAMPED_ARRAY = 12;
const int TYPE_INT_16_ARRAY = 13;
const int TYPE_UNSIGNED_INT_16_ARRAY = 14;
const int TYPE_UNSIGNED_INT_32_ARRAY = 15;
const int TYPE_FLOAT_32_ARRAY = 16;
const int TYPE_UNDEFINED = 99;

jclass integerCls = nullptr;
jclass longCls = nullptr;
jclass doubleCls = nullptr;
jclass booleanCls = nullptr;
jclass stringCls = nullptr;
jclass objectCls = nullptr;
jclass listCls = nullptr;
jclass mapClass = nullptr;

jmethodID integerInitMethodID = nullptr;
jmethodID longInitMethodID = nullptr;
jmethodID doubleInitMethodID = nullptr;
jmethodID booleanInitMethodID = nullptr;

jmethodID intValueMethodID = nullptr;
jmethodID longValueMethodID = nullptr;
jmethodID doubleValueMethodID = nullptr;
jmethodID booleanValueMethodID = nullptr;


jclass quickJSCls = nullptr;
jmethodID callJavaCallbackMethodID = nullptr;
jmethodID createJSValueMethodID = nullptr;
jmethodID getModuleScriptMethodID = nullptr;
jmethodID convertModuleNameMethodID = nullptr;

jclass jsValueCls = nullptr;
jfieldID js_value_tag_id;
jfieldID js_value_u_int32_id;
jfieldID js_value_u_float64_id;
jfieldID js_value_u_ptr_id;

std::queue<JSValue> unhandledRejections;

void initES6Module(JSRuntime *rt);

bool JS_Equals(JSValue v1, JSValue v2) {
#if defined(JS_NAN_BOXING)
    return v1 == v2;
#else
    return v1.tag == v2.tag && v1.u.int32 == v2.u.int32 && v1.u.float64 == v2.u.float64 && v1.u.ptr == v2.u.ptr;
#endif
}

JSValue TO_JS_VALUE(JNIEnv *env, jobject object_handle) {
    jlong tag = env->GetLongField(object_handle, js_value_tag_id);
#if defined(JS_NAN_BOXING)
    return tag;
#else
    JSValue value;
    value.tag = tag;
    value.u.int32 = env->GetIntField(object_handle, js_value_u_int32_id);
    value.u.float64 = env->GetDoubleField(object_handle, js_value_u_float64_id);
    value.u.ptr = (void *) env->GetLongField(object_handle, js_value_u_ptr_id);
    return value;
#endif
}

jobject TO_JAVA_OBJECT(JNIEnv *env, JSContext *ctx, JSValue value) {
    int type = TYPE_UNKNOWN;
    if (JS_IsUndefined(value)) {
        type = TYPE_UNDEFINED;
    } else if (JS_IsArray(ctx, value)) {
        type = TYPE_JS_ARRAY;
    } else if (JS_IsFunction(ctx, value)) {
        type = TYPE_JS_FUNCTION;
    } else if (JS_IsObject(value)) {
        type = TYPE_JS_OBJECT;
    } else if (JS_IsException(value) || JS_IsError(ctx, value)) {
        type = TYPE_JS_EXCEPTION;
    }
#if defined(JS_NAN_BOXING)
    return env->CallStaticObjectMethod(quickJSCls,
                                       createJSValueMethodID,
                                       (jlong) ctx,
                                       type,
                                       (jlong) value,
                                       0,
                                       0.0,
                                       (jlong) 0);
#else
    return env->CallStaticObjectMethod(quickJSCls,
                                       createJSValueMethodID,
                                       (jlong) ctx,
                                       type,
                                       JS_VALUE_GET_TAG(value),
                                       JS_VALUE_GET_INT(value),
                                       (jdouble) JS_VALUE_GET_FLOAT64(value),
                                       (jlong) JS_VALUE_GET_PTR(value));
#endif
}

JSValue JobjectToJSValue(JNIEnv *env, JSContext *ctx, jobject value) {
    if (value == nullptr) {
        return JS_NULL;
    } else if (env->IsInstanceOf(value, integerCls)) {
        return JS_NewInt32(ctx, env->CallIntMethod(value, intValueMethodID));
    } else if (env->IsInstanceOf(value, longCls)) {
        return JS_NewInt64(ctx, env->CallLongMethod(value, longValueMethodID));
    } else if (env->IsInstanceOf(value, doubleCls)) {
        return JS_NewFloat64(ctx, env->CallDoubleMethod(value, doubleValueMethodID));
    } else if (env->IsInstanceOf(value, booleanCls)) {
        return JS_NewBool(ctx, env->CallBooleanMethod(value, booleanValueMethodID));
    } else if (env->IsInstanceOf(value, stringCls)) {
        const char* chars = env->GetStringUTFChars((jstring) value, nullptr);
        JSValue jsStr = JS_NewString(ctx, chars);
        env->ReleaseStringUTFChars((jstring) value, chars);
        return jsStr;
    } else if (env->IsInstanceOf(value, jsValueCls)) {
        JSValue newValue = JS_DupValue(ctx, TO_JS_VALUE(env, value));
        return newValue;
    }
    return JS_UNDEFINED;
}


int GetObjectType(JSContext *ctx, JSValue result) {

    if (JS_IsArray(ctx, result)) {
        return TYPE_JS_ARRAY;
    } else if (JS_IsFunction(ctx, result)) {
        return TYPE_JS_FUNCTION;
    } else if (JS_IsObject(result)) {
        return TYPE_JS_OBJECT;
    } else if (JS_IsString(result)) {
        return TYPE_STRING;
    } else if (JS_IsBool(result)) {
        return TYPE_BOOLEAN;
    } else if (JS_IsBigInt(ctx, result)) {
        return TYPE_INTEGER;
    } else if (JS_IsNull(result)) {
        return TYPE_NULL;
    } else if (JS_IsUndefined(result)) {
        return TYPE_UNDEFINED;
    } else if (JS_IsNumber(result)) {
        int tag = JS_VALUE_GET_TAG(result);
        if (tag == JS_TAG_INT) {
            return TYPE_INTEGER;
        }
        return TYPE_DOUBLE;
    }
    return TYPE_UNKNOWN;
}

jobject To_JObject(JNIEnv *env, jlong context_ptr, int expected_type, JSValue result) {
    auto *ctx = reinterpret_cast<JSContext *>(context_ptr);

    if (expected_type == TYPE_UNKNOWN) {
        expected_type = GetObjectType(ctx, result);
    }
    if (JS_IsUndefined(result)) {
        expected_type = TYPE_UNDEFINED;
    } else if (JS_IsNull(result)) {
        expected_type = TYPE_NULL;
    }
    switch (expected_type) {
        case TYPE_NULL:
            return nullptr;
        case TYPE_INTEGER:
            return env->NewObject(integerCls, integerInitMethodID, JS_VALUE_GET_INT(result));
        case TYPE_DOUBLE:
            double pres;
            JS_ToFloat64(ctx, &pres, result);
            return env->NewObject(doubleCls, doubleInitMethodID, pres);
        case TYPE_BOOLEAN:
            return env->NewObject(booleanCls, booleanInitMethodID, JS_VALUE_GET_BOOL(result));
        case TYPE_STRING:
            return env->NewStringUTF(JS_ToCString(ctx, result));
        case TYPE_JS_ARRAY:
        case TYPE_JS_OBJECT:
        case TYPE_JS_FUNCTION:
        case TYPE_UNDEFINED:
            return TO_JAVA_OBJECT(env, ctx, result);
        default:
            return nullptr;
    }
    return nullptr;
}

void tryToTriggerOnError(JSContext *ctx, JSValueConst *error) {
    JSValue global = JS_GetGlobalObject(ctx);
    JSValue onerror = JS_GetPropertyStr(ctx, global, "onError");
    if (JS_IsNull(onerror)) {
        onerror = JS_GetPropertyStr(ctx, global, "onerror");
    }
    if (JS_IsNull(onerror)) {
        return;
    }
    JS_Call(ctx, onerror, global, 1, error);
    JS_FreeValue(ctx, onerror);
    JS_FreeValue(ctx, global);
}

std::string getJSErrorStr(JSContext *ctx, JSValueConst error) {
    JSValue val;
    bool is_error;
    is_error = JS_IsError(ctx, error);
    std::string jsException;
    if (is_error) {
        tryToTriggerOnError(ctx, &error);
        JSValue  message = JS_GetPropertyStr(ctx, error, "message");
        const char *msg_str = JS_ToCString(ctx, message);

        jsException += msg_str;
        JS_FreeCString(ctx, msg_str);
        JS_FreeValue(ctx, message);

        val = JS_GetPropertyStr(ctx, error, "stack");
        if (!JS_IsUndefined(val)) {
            jsException += "\n";
            const char *stack_str = JS_ToCString(ctx, val);
            jsException += stack_str;

            JS_FreeCString(ctx, stack_str);
        }
        JS_FreeValue(ctx, val);
    } else {
        const char *error_str = JS_ToCString(ctx, error);
        jsException += error_str;
        JS_FreeCString(ctx, error_str);
    }

    return jsException;
}

std::string getJSErrorStr(JSContext *ctx) {
    JSValue error = JS_GetException(ctx);
    std::string error_str = getJSErrorStr(ctx, error);
    JS_FreeValue(ctx, error);
    return error_str;
}

void throwJSException(JNIEnv *env, const char *msg) {
    if (env->ExceptionCheck()) return;

    jclass e = env->FindClass("com/quickjs/QuickJSException");
    auto init = env->GetMethodID(e, "<init>", "(Ljava/lang/String;Ljava/lang/String;)V");
    jstring ret = env->NewStringUTF(msg);
    jstring name = env->NewStringUTF("C");
    auto t = (jthrowable)env->NewObject(e, init, name, ret);
    env->Throw(t);
    env->DeleteLocalRef(e);
}

void throwJSException(JNIEnv *env, JSContext *ctx) {
    std::string error = getJSErrorStr(ctx);
    throwJSException(env, error.c_str());
}


JavaVM *jvm;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *) {
    jvm = vm;
    JNIEnv *env;
    jint onLoad_err = -1;
    if (vm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        return onLoad_err;
    }
    if (env == nullptr) {
        return onLoad_err;
    }

    integerCls = (jclass) env->NewGlobalRef((env)->FindClass("java/lang/Integer"));
    longCls = (jclass) env->NewGlobalRef((env)->FindClass("java/lang/Long"));
    doubleCls = (jclass) env->NewGlobalRef((env)->FindClass("java/lang/Double"));
    booleanCls = (jclass) env->NewGlobalRef((env)->FindClass("java/lang/Boolean"));
    stringCls = (jclass) env->NewGlobalRef((env)->FindClass("java/lang/String"));
    objectCls = (jclass) env->NewGlobalRef((env)->FindClass("java/lang/Object"));
    quickJSCls = (jclass) env->NewGlobalRef((env)->FindClass("com/quickjs/QuickJS"));
    listCls = (jclass) env->NewGlobalRef((env)->FindClass("java/util/List"));
    mapClass = (jclass) env->NewGlobalRef((env)->FindClass("java/util/HashMap"));

    integerInitMethodID = env->GetMethodID(integerCls, "<init>", "(I)V");
    longInitMethodID = env->GetMethodID(longCls, "<init>", "(J)V");
    doubleInitMethodID = env->GetMethodID(doubleCls, "<init>", "(D)V");
    booleanInitMethodID = env->GetMethodID(booleanCls, "<init>", "(Z)V");

    callJavaCallbackMethodID = env->GetStaticMethodID(quickJSCls, "callJavaCallback",
                                                      "(JILcom/quickjs/JSValue;[Ljava/lang/Object;)Ljava/lang/Object;");

    createJSValueMethodID = env->GetStaticMethodID(quickJSCls, "createJSValue",
                                                   "(JIJIDJ)Lcom/quickjs/JSValue;");
    getModuleScriptMethodID = env->GetStaticMethodID(quickJSCls, "getModuleScript",
                                                     "(JLjava/lang/String;)Ljava/lang/String;");
    convertModuleNameMethodID = env->GetStaticMethodID(quickJSCls, "convertModuleName",
                                                       "(JLjava/lang/String;Ljava/lang/String;)Ljava/lang/String;");

    intValueMethodID = env->GetMethodID(integerCls, "intValue", "()I");
    longValueMethodID = env->GetMethodID(longCls, "longValue", "()J");
    doubleValueMethodID = env->GetMethodID(doubleCls, "doubleValue", "()D");
    booleanValueMethodID = env->GetMethodID(booleanCls, "booleanValue", "()Z");


    jsValueCls = (jclass) env->NewGlobalRef((env)->FindClass("com/quickjs/JSValue"));
    js_value_tag_id = env->GetFieldID(jsValueCls, "tag", "J");
    js_value_u_int32_id = env->GetFieldID(jsValueCls, "uInt32", "I");
    js_value_u_float64_id = env->GetFieldID(jsValueCls, "uFloat64", "D");
    js_value_u_ptr_id = env->GetFieldID(jsValueCls, "uPtr", "J");
    return JNI_VERSION_1_6;
}

int GetArrayLength(JSContext *ctx, JSValue this_obj) {
    JSValue lenValue = JS_GetPropertyStr(ctx, this_obj, "length");
    return JS_VALUE_GET_INT(lenValue);
}

void promiseRejectionTracker(JSContext *ctx, JSValueConst promise,
                             JSValueConst reason, BOOL is_handled, void *opaque) {
    auto _unhandledRejections = static_cast<std::queue<JSValue> *>(opaque);
    if (!is_handled) {
        _unhandledRejections->push(JS_DupValue(ctx, reason));
    } else {
        if (!_unhandledRejections->empty()) {
            JSValue rej = _unhandledRejections->front();
            JS_FreeValue(ctx, rej);
            _unhandledRejections->pop();
        }
    }
}

bool throwIfUnhandledRejections(JNIEnv *env, JSContext *ctx) {
    std::string error;
    while (!unhandledRejections.empty()) {
        JSValue reason = unhandledRejections.front();
        error += getJSErrorStr(ctx, reason);
        error += "\n";
        JS_FreeValue(ctx, reason);
        unhandledRejections.pop();
    }
    if (!error.empty()) {
        error = "UnhandledPromiseRejectionException: " + error;
        throwJSException(env, error.c_str());
        return true;
    }
    return false;
}


bool executePendingJobLoop(JNIEnv *env, JSRuntime *rt, JSContext *ctx) {
    if (env->ExceptionCheck()) {
        return false;
    }

    JSContext *ctx1;
    bool success = true;
    int err;

    for(;;) {
        err = JS_ExecutePendingJob(rt, &ctx1);
        if (err <= 0) {
            if (err < 0) {
                success = false;
                std::string error = getJSErrorStr(ctx);
                LOGI("executePendingJobLoop LL: %s", error.c_str());
                throwJSException(env, error.c_str());
                break;
            }
            break;
        }
    }
    if (success && throwIfUnhandledRejections(env, ctx)) {
        success = false;
    }
    return success;
}


JSValue JavaToJSValue(JSContext* ctx, JNIEnv* env, jobject value) {
    if (value == nullptr) {
        return JS_NULL;
    }

    // Boolean
    if (env->IsInstanceOf(value, booleanCls)) {
        jmethodID booleanValue = env->GetMethodID(booleanCls, "booleanValue", "()Z");
        jboolean b = env->CallBooleanMethod(value, booleanValue);
        return JS_NewBool(ctx, b);
    }

    // Integer
    if (env->IsInstanceOf(value, integerCls)) {
        jmethodID intValue = env->GetMethodID(integerCls, "intValue", "()I");
        jint i = env->CallIntMethod(value, intValue);
        return JS_NewInt32(ctx, i);
    }

    // Double
    if (env->IsInstanceOf(value, doubleCls)) {
        jmethodID doubleValue = env->GetMethodID(doubleCls, "doubleValue", "()D");
        jdouble d = env->CallDoubleMethod(value, doubleValue);
        return JS_NewFloat64(ctx, d);
    }

    // String

    if (env->IsInstanceOf(value, stringCls)) {
        const char* str = env->GetStringUTFChars((jstring)value, nullptr);
        JSValue jsStr = JS_NewString(ctx, str);
        env->ReleaseStringUTFChars((jstring)value, str);
        return jsStr;
    }

    // List (java.util.List)

    if (env->IsInstanceOf(value, listCls)) {
        JSValue arr = JS_NewArray(ctx);
        jmethodID sizeMethod = env->GetMethodID(listCls, "size", "()I");
        jmethodID getMethod = env->GetMethodID(listCls, "get", "(I)Ljava/lang/Object;");
        jint size = env->CallIntMethod(value, sizeMethod);
        for (jint i = 0; i < size; i++) {
            jobject elem = env->CallObjectMethod(value, getMethod, i);
            JSValue jsElem = JavaToJSValue(ctx, env, elem);
            JS_SetPropertyUint32(ctx, arr, i, jsElem);
            env->DeleteLocalRef(elem);
        }
        return arr;
    }

    // Map (java.util.Map)
    if (env->IsInstanceOf(value, mapClass)) {
        JSValue obj = JS_NewObject(ctx);
        jmethodID entrySetMethod = env->GetMethodID(mapClass, "entrySet", "()Ljava/util/Set;");
        jobject entrySet = env->CallObjectMethod(value, entrySetMethod);

        jclass setClass = env->FindClass("java/util/Set");
        jmethodID iteratorMethod = env->GetMethodID(setClass, "iterator", "()Ljava/util/Iterator;");
        jobject iterator = env->CallObjectMethod(entrySet, iteratorMethod);

        jclass iteratorClass = env->FindClass("java/util/Iterator");
        jmethodID hasNextMethod = env->GetMethodID(iteratorClass, "hasNext", "()Z");
        jmethodID nextMethod = env->GetMethodID(iteratorClass, "next", "()Ljava/lang/Object;");

        jclass entryClass = env->FindClass("java/util/Map$Entry");
        jmethodID getKeyMethod = env->GetMethodID(entryClass, "getKey", "()Ljava/lang/Object;");
        jmethodID getValueMethod = env->GetMethodID(entryClass, "getValue", "()Ljava/lang/Object;");

        while (env->CallBooleanMethod(iterator, hasNextMethod)) {
            jobject entry = env->CallObjectMethod(iterator, nextMethod);
            jobject key = env->CallObjectMethod(entry, getKeyMethod);
            jobject val = env->CallObjectMethod(entry, getValueMethod);

            // 只处理 String key，为简化
            if (env->IsInstanceOf(key, stringCls)) {
                const char* keyStr = env->GetStringUTFChars((jstring)key, nullptr);
                JSValue jsVal = JavaToJSValue(ctx, env, val);
                JS_SetPropertyStr(ctx, obj, keyStr, jsVal);
                env->ReleaseStringUTFChars((jstring)key, keyStr);
            }
            env->DeleteLocalRef(key);
            env->DeleteLocalRef(val);
            env->DeleteLocalRef(entry);
        }
        env->DeleteLocalRef(iterator);
        env->DeleteLocalRef(entrySet);
        return obj;
    }

    // 其它类型可扩展
    if (env->IsInstanceOf(value, objectCls)) {
        return TO_JS_VALUE(env, value);
    }
    return JS_ThrowTypeError(ctx, "TODO: not instance of object");
}


jobject JSValueToJava(JSContext* ctx, JNIEnv* env, JSValue value) {
    if (JS_IsNull(value) || JS_IsUndefined(value)) {
        return nullptr;
    }

    // Boolean
    if (JS_IsBool(value)) {
        jmethodID ctor = env->GetMethodID(booleanCls, "<init>", "(Z)V");
        jboolean b = JS_ToBool(ctx, value);
        return env->NewObject(booleanCls, ctor, b);
    }

    // Number（优先转 Long，若溢出可转 Double）
    if (JS_IsNumber(value)) {

        if (JS_VALUE_GET_TAG(value) == JS_TAG_FLOAT64) {
            double d;
            JS_ToFloat64(ctx, &d, value);
            jmethodID ctor = env->GetMethodID(doubleCls, "<init>", "(D)V");
            return env->NewObject(doubleCls, ctor, (jdouble)d);
        } else {
            int64_t l;
            JS_ToInt64(ctx, &l, value);
            jmethodID ctor = env->GetMethodID(longCls, "<init>", "(J)V");
            return env->NewObject(longCls, ctor, (jlong)l);
        }
    }

    // String
    if (JS_IsString(value)) {
        const char* str = JS_ToCString(ctx, value);
        jobject jstr = env->NewStringUTF(str);
        JS_FreeCString(ctx, str);
        return jstr;
    }
    // 其它类型返回 null

    return TO_JAVA_OBJECT(env, ctx, value);
}


extern "C"
JNIEXPORT jlong JNICALL
Java_com_quickjs_QuickJSNativeImpl_createRuntime(JNIEnv *env, jclass clazz) {
    JSRuntime *runtime = JS_NewRuntime();
    initES6Module(runtime);
    return reinterpret_cast<jlong>(runtime);
}
extern "C"
JNIEXPORT jlong JNICALL
Java_com_quickjs_QuickJSNativeImpl_createContext(JNIEnv *env, jobject clzz, jlong runtime_ptr) {

    auto *runtime = reinterpret_cast<JSRuntime *>(runtime_ptr);
    auto *ctx = JS_NewContext(runtime);
    return reinterpret_cast<jlong>(ctx);
}
extern "C"
JNIEXPORT void JNICALL
Java_com_quickjs_QuickJSNativeImpl_releaseRuntime(JNIEnv *env, jobject clazz, jlong runtime_ptr) {
    auto *runtime = reinterpret_cast<JSRuntime *>(runtime_ptr);
    JS_FreeRuntime(runtime);
}extern "C"
JNIEXPORT void JNICALL
Java_com_quickjs_QuickJSNativeImpl_releaseContext(JNIEnv *env, jobject clazz, jlong context_ptr) {
    auto *ctx = reinterpret_cast<JSContext *>(context_ptr);
    JS_FreeContext(ctx);
}
extern "C"
JNIEXPORT jobject JNICALL
Java_com_quickjs_QuickJSNativeImpl_executeScript(JNIEnv *env, jobject clazz,jlong context_ptr,
                                                   jint expected_type,
                                                   jstring source, jstring file_name,jint eval_flags) {
    if (source == nullptr) {
        return nullptr;
    }
    auto *ctx = reinterpret_cast<JSContext *>(context_ptr);

    JSRuntime *rt = JS_GetRuntime(ctx);
    const char *file_name_;
    if (file_name == nullptr) {
        file_name_ = "";
    } else {
        file_name_ = env->GetStringUTFChars(file_name, JNI_FALSE);
    }
    const char *source_ = env->GetStringUTFChars(source, JNI_FALSE);
    const int source_length = env->GetStringUTFLength(source);
    JSValue val = JS_Eval(ctx, source_, (size_t) source_length, file_name_, eval_flags);
    if(JS_IsException(val)) {
        std::string error = getJSErrorStr(ctx);
        LOGE("executeScript: %s", error.c_str());
//        env->ThrowNew(env->FindClass("com/quickjs/QuickJSException"), error.c_str());
        return TO_JAVA_OBJECT(env, ctx, JS_Throw(ctx, val));
    }
    if (!executePendingJobLoop(env, rt, ctx)) {
        LOGE("executeScript: exce job error");
        return TO_JAVA_OBJECT(env, ctx, JS_Throw(ctx, val));
    }
    env->ReleaseStringUTFChars(source, source_);
    env->ReleaseStringUTFChars(file_name, file_name_);

    jobject result = To_JObject(env, context_ptr, expected_type, val);
    return result;
}

extern "C"
JNIEXPORT jobject JNICALL
Java_com_quickjs_QuickJSNativeImpl_getGlobalObject(JNIEnv *env, jobject clazz, jlong context_ptr) {
    auto *ctx = reinterpret_cast<JSContext *>(context_ptr);
    JSValue global_obj = JS_GetGlobalObject(ctx);
    return TO_JAVA_OBJECT(env, ctx, global_obj);
}


extern "C"
JNIEXPORT jobject JNICALL
Java_com_quickjs_QuickJSNativeImpl_initNewJSObject(JNIEnv *env, jobject clazz, jlong context_ptr) {
    auto *ctx = reinterpret_cast<JSContext *>(context_ptr);
    JSValue jsValue = JS_NewObject(ctx);
    return TO_JAVA_OBJECT(env, ctx, jsValue);
}
extern "C"
JNIEXPORT jobject JNICALL
Java_com_quickjs_QuickJSNativeImpl_initNewJSArray(JNIEnv *env, jobject clazz, jlong context_ptr) {
    auto *ctx = reinterpret_cast<JSContext *>(context_ptr);
    JSValue jsValue = JS_NewArray(ctx);
    return TO_JAVA_OBJECT(env, ctx, jsValue);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_quickjs_QuickJSNativeImpl_releasePtr(JNIEnv *env, jobject clazz, jlong context_ptr,
                                                jlong tag,
                                                jint u_int32, jdouble u_float64, jlong u_ptr) {

    JSValue value;
#if defined(JS_NAN_BOXING)
    value = tag;
#else
    value.tag = tag;
    value.u.int32 = u_int32;
    value.u.float64 = u_float64;
    value.u.ptr = (void *) u_ptr;
#endif
    auto *ctx = reinterpret_cast<JSContext *>(context_ptr);
    JS_FreeValue(ctx, value);
}

extern "C"
JNIEXPORT jobject JNICALL
Java_com_quickjs_QuickJSNativeImpl_get(JNIEnv *env, jobject clazz, jlong context_ptr,
                                         int expected_type,
                                         jobject object_handle, jstring key) {
    const char *key_ = env->GetStringUTFChars(key, nullptr);
    auto *ctx = reinterpret_cast<JSContext *>(context_ptr);
    JSValue this_obj = TO_JS_VALUE(env, object_handle);
    JSValue result = JS_GetPropertyStr(ctx, this_obj, key_);
    jobject tmp = To_JObject(env, context_ptr, expected_type, result);
//    JS_FreeValue(ctx, result);
    return tmp;
}
extern "C"
JNIEXPORT jobject JNICALL
Java_com_quickjs_QuickJSNativeImpl_getValue(JNIEnv *env, jobject clazz, jlong context_ptr,
                                              jobject object_handle, jstring key) {
    const char *key_ = env->GetStringUTFChars(key, nullptr);
    auto *ctx = reinterpret_cast<JSContext *>(context_ptr);
    JSValue this_obj = TO_JS_VALUE(env, object_handle);
    JSValue result = JS_GetPropertyStr(ctx, this_obj, key_);
    return TO_JAVA_OBJECT(env, ctx, result);
}



extern "C"
JNIEXPORT jobject JNICALL
Java_com_quickjs_QuickJSNativeImpl_arrayGet(JNIEnv *env, jobject clazz, jlong context_ptr,
                                              int expected_type,
                                              jobject object_handle, jint index) {
    auto *ctx = reinterpret_cast<JSContext *>(context_ptr);
    JSValue this_obj = TO_JS_VALUE(env, object_handle);
    JSValue result = JS_GetPropertyUint32(ctx, this_obj, index);
    jobject jo = To_JObject(env, context_ptr, expected_type, result);
//    JS_FreeValue(ctx, result);
    return jo;
}

extern "C"
JNIEXPORT jobject JNICALL
Java_com_quickjs_QuickJSNativeImpl_arrayGetValue(JNIEnv *env, jobject clazz, jlong context_ptr,
                                                   jobject object_handle, jint index) {
    auto *ctx = reinterpret_cast<JSContext *>(context_ptr);
    JSValue this_obj = TO_JS_VALUE(env, object_handle);
    JSValue result = JS_GetPropertyUint32(ctx, this_obj, index);
    return TO_JAVA_OBJECT(env, ctx, result);
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_quickjs_QuickJSNativeImpl_contains(JNIEnv *env, jobject clazz, jlong context_ptr,
                                              jobject object_handle, jstring key) {
    auto *ctx = reinterpret_cast<JSContext *>(context_ptr);
    JSValue this_obj = TO_JS_VALUE(env, object_handle);
    const char *key_ = env->GetStringUTFChars(key, nullptr);
    JSAtom atom = JS_NewAtom(ctx, key_);
    int result = JS_HasProperty(ctx, this_obj, atom);
    JS_FreeAtom(ctx, atom);
    return result;
}

extern "C"
JNIEXPORT jobjectArray JNICALL
Java_com_quickjs_QuickJSNativeImpl_getKeys(JNIEnv *env, jobject clazz, jlong context_ptr,
                                             jobject object_handle) {
    auto *ctx = reinterpret_cast<JSContext *>(context_ptr);
    JSValue this_obj = TO_JS_VALUE(env, object_handle);
    JSPropertyEnum *tab;
    uint32_t len;
    JS_GetOwnPropertyNames(ctx, &tab, &len, this_obj, JS_GPN_STRING_MASK | JS_GPN_ENUM_ONLY);
    jobjectArray stringArray = env->NewObjectArray(len, stringCls, nullptr);
    for (int i = 0; i < len; ++i) {
        jstring key = env->NewStringUTF(JS_AtomToCString(ctx, tab[i].atom));
        env->SetObjectArrayElement(stringArray, i, key);
    }
    return stringArray;
}

JSValue executeFunction(JNIEnv *env, jlong context_ptr, jobject object_handle, JSValue func,
                        jobjectArray args) {
    auto *ctx = reinterpret_cast<JSContext *>(context_ptr);
    JSValue this_obj = TO_JS_VALUE(env, object_handle);

    JSValue *argv = nullptr;
    int argc = 0;
    if (args != nullptr) {
        // old parameters_handle is JSValue
//        JSValue argArray = TO_JS_VALUE(env, parameters_handle);

        argc = env->GetArrayLength(args);
        argv = new JSValue[argc];
        for (int i = 0; i < argc; ++i) {
            jobject ele = env->GetObjectArrayElement(args, i);
            argv[i] = JavaToJSValue(ctx, env, ele);
        }
    }
    JSValue global = JS_GetGlobalObject(ctx);

    if (JS_Equals(this_obj, global)) {
        this_obj = JS_UNDEFINED;
    }
    JSValue result = JS_Call(ctx, func, this_obj, argc, argv);
//    JS_FreeValue(ctx, func);
    JS_FreeValue(ctx, global);
    if (argv != nullptr) {
        for (int i = 0; i < argc; ++i) {
            JS_FreeValue(ctx, argv[i]);
        }
    }
//    JS_FreeValue(ctx, result);
    return result;
}


extern "C"
JNIEXPORT jobject JNICALL
Java_com_quickjs_QuickJSNativeImpl_executeFunction2(JNIEnv *env, jobject clazz, jlong context_ptr,
                                                      jint expected_type, jobject object_handle,
                                                      jobject functionHandle,
                                                      jobjectArray args) {
    auto *ctx = reinterpret_cast<JSContext *>(context_ptr);
    JSValue func_obj = TO_JS_VALUE(env, functionHandle);
    JS_DupValue(ctx, func_obj);
    JSValue value = executeFunction(env, context_ptr, object_handle, func_obj, args);
    jobject result = To_JObject(env, context_ptr, expected_type, value);
    return result;
}


extern "C"
JNIEXPORT jobject JNICALL
Java_com_quickjs_QuickJSNativeImpl_executeFunction(JNIEnv *env, jobject clazz, jlong context_ptr,
                                                     jint expected_type, jobject object_handle,
                                                     jstring name, jobjectArray args) {
    auto *ctx = reinterpret_cast<JSContext *>(context_ptr);
    JSValue this_obj = TO_JS_VALUE(env, object_handle);
    JSValue func_obj = JS_GetPropertyStr(ctx, this_obj, env->GetStringUTFChars(name, nullptr));
    JSValue value = executeFunction(env, context_ptr, object_handle, func_obj, args);
    jobject result = To_JObject(env, context_ptr, expected_type, value);
    return result;
}

JSValue createJSException(JSContext *ctx, const char *errmsg) {
    JSValue err = JS_NewError(ctx);
    JS_DefinePropertyValueStr(ctx, err, "message", JS_NewString(ctx, errmsg), JS_PROP_WRITABLE|JS_PROP_CONFIGURABLE);
    LOGD("createJSException: %s", errmsg);
    return JS_Throw(ctx, err);
}

JSValue
callJavaCallback(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv, int magic, JSValue *func_data) {
    JNIEnv *env;
    jvm->GetEnv((void **) &env, JNI_VERSION_1_6);
    int callbackId = JS_VALUE_GET_INT(func_data[0]);
    bool void_method = JS_VALUE_GET_BOOL(func_data[1]);
    auto context_ptr = (jlong) ctx;

    jobjectArray args = env->NewObjectArray(argc, objectCls, nullptr);
    if (argv != nullptr) {
        for (int i = 0; i < argc; i++) {
            JSValue it = argv[i];
            JS_DupValue(ctx, it);
            jobject obj = JSValueToJava(ctx, env, it);
            env->SetObjectArrayElement(args, i, obj);
        }
    }
    jobject objectHandle = TO_JAVA_OBJECT(env, ctx, this_val);
//    jobject argsHandle = TO_JAVA_OBJECT(env, ctx, args);
    JSValue global = JS_GetGlobalObject(ctx);
    if (!JS_Equals(global, this_val)) {
        JS_DupValue(ctx, this_val);
    }
    JS_FreeValue(ctx, global);
    jobject result = env->CallStaticObjectMethod(quickJSCls, callJavaCallbackMethodID,
                                                 context_ptr,
                                                 callbackId,
                                                 objectHandle,
                                                 args
    );

    if (env->ExceptionCheck()) {
        // 获取异常对象
        jthrowable exc = env->ExceptionOccurred();
        env->ExceptionClear(); // 清除 JVM 异常状态

        // 获取异常类
        jclass excClass = env->GetObjectClass(exc);
        // 获取 toString 方法
        jmethodID toStringID = env->GetMethodID(excClass, "toString", "()Ljava/lang/String;");
        auto msgStr = (jstring)env->CallObjectMethod(exc, toStringID);

        // 获取字符串内容
        const char *errmsg = env->GetStringUTFChars(msgStr, nullptr);

        // 用异常信息抛出 JS 异常
        JSValue js_exc = createJSException(ctx, errmsg);

        // 释放本地引用
        env->ReleaseStringUTFChars(msgStr, errmsg);
        env->DeleteLocalRef(msgStr);
        env->DeleteLocalRef(excClass);
        env->DeleteLocalRef(exc);

        return js_exc;
    }

    JSValue value = JobjectToJSValue(env, ctx, result);

    if (env->IsInstanceOf(result, jsValueCls)) {
//        JS_FreeValue(ctx,value);
//        JS_DupValue(ctx, value);
    }
    return value;
}

JSValue newFunction(jlong context_ptr, int callbackId) {
    auto *ctx = reinterpret_cast<JSContext *>(context_ptr);
    JSValueConst func_data[2];
    func_data[0] = JS_NewInt32(ctx, callbackId);
    func_data[1] = JS_NewBool(ctx, 0);
    JSValue func = JS_NewCFunctionData(ctx, callJavaCallback, 1, 0, 2, func_data);
    return func;
}

extern "C"
JNIEXPORT jobject JNICALL
Java_com_quickjs_QuickJSNativeImpl_initNewJSFunction(JNIEnv *env,
                                                     jobject clazz,
                                                       jlong context_ptr,
                                                       jint callbackId) {
    auto *ctx = reinterpret_cast<JSContext *>(context_ptr);
    JSValue func = newFunction(context_ptr, callbackId);
    return TO_JAVA_OBJECT(env, ctx, func);
}
extern "C"
JNIEXPORT jobject JNICALL
Java_com_quickjs_QuickJSNativeImpl_registerJavaMethod(JNIEnv *env, jobject clazz,
                                                        jlong context_ptr,
                                                        jobject object_handle,
                                                        jstring function_name,
                                                        jint callbackId) {
    const char *name_ = env->GetStringUTFChars(function_name, nullptr);
    auto *ctx = reinterpret_cast<JSContext *>(context_ptr);
    JSValue func = newFunction(context_ptr, callbackId);
    JSValue this_obj = TO_JS_VALUE(env, object_handle);
    JS_SetPropertyStr(ctx, this_obj, name_, JS_DupValue(ctx, func));
    return TO_JAVA_OBJECT(env, ctx, func);
}


extern "C"
JNIEXPORT jint JNICALL
Java_com_quickjs_QuickJSNativeImpl_getObjectType(JNIEnv *env, jobject clazz, jlong context_ptr,
                                                   jobject object_handle) {
    auto *ctx = reinterpret_cast<JSContext *>(context_ptr);
    JSValue value = TO_JS_VALUE(env, object_handle);
    return GetObjectType(ctx, value);
}
extern "C"
JNIEXPORT void JNICALL
Java_com_quickjs_QuickJSNativeImpl_set(JNIEnv *env, jobject clazz, jlong context_ptr,
                                         jobject object_handle, jstring key, jobject value) {
    const char *key_ = env->GetStringUTFChars(key, nullptr);
    auto *ctx = reinterpret_cast<JSContext *>(context_ptr);
    JSValue this_obj = TO_JS_VALUE(env, object_handle);
    JS_SetPropertyStr(ctx, this_obj, key_, JobjectToJSValue(env, ctx, value));
    env->ReleaseStringUTFChars(key, key_);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_quickjs_QuickJSNativeImpl_arrayAdd(JNIEnv *env, jobject clazz, jlong context_ptr,
                                              jobject object_handle, jobject value) {
    auto *ctx = reinterpret_cast<JSContext *>(context_ptr);
    JSValue this_obj = TO_JS_VALUE(env, object_handle);
    int len = GetArrayLength(ctx, this_obj);
    JS_SetPropertyUint32(ctx, this_obj, len, JobjectToJSValue(env, ctx, value));
}
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_quickjs_QuickJSNativeImpl_isUndefined(JNIEnv *env, jobject clazz, jlong context_ptr,
                                                 jobject js_value) {
    JSValue value = TO_JS_VALUE(env, js_value);
    return JS_IsUndefined(value);
}

extern "C"
JNIEXPORT jobject JNICALL
Java_com_quickjs_QuickJSNativeImpl_undefined(JNIEnv *env, jobject clazz, jlong context_ptr) {
    auto *ctx = reinterpret_cast<JSContext *>(context_ptr);
    return TO_JAVA_OBJECT(env, ctx, JS_UNDEFINED);
}

extern "C"
JNIEXPORT jobject JNICALL
Java_com_quickjs_QuickJSNativeImpl_exception(JNIEnv *env, jobject clazz, jlong context_ptr) {
    auto *ctx = reinterpret_cast<JSContext *>(context_ptr);
    return TO_JAVA_OBJECT(env, ctx, JS_EXCEPTION);
}

extern "C"
JNIEXPORT jobjectArray JNICALL
Java_com_quickjs_QuickJSNativeImpl_getException(JNIEnv *env, jobject clazz, jlong context_ptr) {
    auto *ctx = reinterpret_cast<JSContext *>(context_ptr);
    JSValue exc = JS_GetException(ctx);
    if (!JS_IsError(ctx, exc)) {
        return nullptr;
    }

    JSValue func = JS_GetPropertyStr(ctx, exc, "toString");
    JSValue nameValue = JS_GetPropertyStr(ctx, exc, "name");
    JSValue stackValue = JS_GetPropertyStr(ctx, exc, "stack");
    JSValue titleValue = JS_Call(ctx, func, exc, 0, nullptr);
    JS_FreeValue(ctx, func);

    std::vector<const char *> messages;
    messages.push_back(JS_ToCString(ctx, nameValue));
    messages.push_back(JS_ToCString(ctx, titleValue));
    while (!JS_IsUndefined(stackValue)) {
        messages.push_back(JS_ToCString(ctx, stackValue));
        JS_FreeValue(ctx, stackValue);
        stackValue = JS_GetPropertyStr(ctx, stackValue, "stack");
    }
    JS_FreeValue(ctx, exc);

    jobjectArray stringArray = env->NewObjectArray(messages.size(), stringCls, nullptr);
    for (int i = 0; i < messages.size(); ++i) {
        jstring str = env->NewStringUTF(messages[i]);
        env->SetObjectArrayElement(stringArray, i, str);
    }
    return stringArray;
}

const char *GetModuleScript(JSContext *ctx, const char *module_name, int *scriptLen) {
    JNIEnv *env;
    jvm->GetEnv((void **) &env, JNI_VERSION_1_6);
    jobject result = env->CallStaticObjectMethod(quickJSCls, getModuleScriptMethodID, (jlong) ctx,
                                                 env->NewStringUTF(module_name));
    if (result == nullptr) {
        return nullptr;
    }
    *scriptLen = env->GetStringUTFLength((jstring) result);
    return env->GetStringUTFChars((jstring) result, nullptr);
}

JSModuleDef *ES6JSModuleLoaderFunc(JSContext *ctx, const char *module_name, void *opaque) {
    int scriptLen;
    void *m;

    // 读取模块脚本内容
    const char *script = GetModuleScript(ctx, module_name, &scriptLen);
    if (script == nullptr) {
        return nullptr;
    }

    JSValue func_val = JS_Eval(ctx, script, scriptLen, module_name,
                                 JS_EVAL_TYPE_MODULE | JS_EVAL_FLAG_COMPILE_ONLY);
    m = JS_VALUE_GET_PTR(func_val);
    JS_FreeValue(ctx, func_val);
    return (JSModuleDef *) m;
}

char *ES6JSModuleNormalizeFunc(JSContext *ctx,
                             const char *module_base_name,
                             const char *module_name, void *opaque) {

    JNIEnv *env;
    jvm->GetEnv((void **) &env, JNI_VERSION_1_6);

    jobject result = env->CallStaticObjectMethod(quickJSCls, convertModuleNameMethodID, (jlong) ctx,
                                                 env->NewStringUTF(module_base_name),
                                                 env->NewStringUTF(module_name));
    if (result == nullptr) {
        LOGW("_JSModuleNormalizeFunc: result is null");
        return nullptr;
    }

    char * s = (char *) env->GetStringUTFChars((jstring) result, nullptr);
    return s;
}

void jobListener(JSRuntime *rt, void *opaque) {
//    LOGD("新job加入队列！");
    JSContext *ctx;

    while (JS_IsJobPending(rt)) {
        int ret = JS_ExecutePendingJob(rt, &ctx);
        if (ret < 0) {
            std::string error = getJSErrorStr(ctx);
            LOGI("executePendingJobLoop LL: %s", error.c_str());
            break;
        }
    }
}

void initES6Module(JSRuntime *rt) {
    JS_SetModuleLoaderFunc(rt, ES6JSModuleNormalizeFunc, ES6JSModuleLoaderFunc, nullptr);
    JS_SetHostPromiseRejectionTracker(rt, promiseRejectionTracker, &unhandledRejections);
    JS_SetJobListAddListener(rt, jobListener, nullptr);
}

//JSValue js_worker_constructor(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv){
//
//}
JSValue js_worker_constructor(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv,
                              int me) {
    JNIEnv *env;
    jvm->GetEnv((void **) &env, JNI_VERSION_1_6);
    auto context_ptr = (jlong) ctx;
    int callbackId = JS_VALUE_GET_INT(JS_GetPropertyStr(ctx, this_val, "java_caller_id"));

    jobjectArray args = env->NewObjectArray(argc, objectCls, nullptr);

    if (argv != nullptr) {
        for (int i = 0; i < argc; ++i) {
            JSValue it = argv[i];
            jobject obj = JSValueToJava(ctx, env, it);
            env->SetObjectArrayElement(args, i, obj);
        }
    }

    jobject objectHandle = TO_JAVA_OBJECT(env, ctx, this_val);
//    jobject argsHandle = TO_JAVA_OBJECT(env, ctx, args);
    JSValue global = JS_GetGlobalObject(ctx);
    if (!JS_Equals(global, this_val)) {
        JS_DupValue(ctx, this_val);
    }
    JS_FreeValue(ctx, global);
    jobject result = env->CallStaticObjectMethod(quickJSCls, callJavaCallbackMethodID,
                                                 context_ptr,
                                                 callbackId,
                                                 objectHandle,
                                                 args
    );
    JSValue value = JobjectToJSValue(env, ctx, result);
    return value;
}

void newWorker(JSContext *ctx, int callbackId) {
//    JSClassDef workerClassDef = new JSClassDef(.);
//    JSClassID workerJSClassID;
//    JS_NewClassID(&workerJSClassID);
//    JS_NewClass(rt,workerJSClassID,);
//    JS_SetModuleLoaderFunc(rt, _JSModuleNormalizeFunc, _JSModuleLoaderFunc, nullptr);
//    obj = JS_NewCFunction2(ctx, js_worker_constructor, "Worker", 1,
//                           JS_CFUNC_constructor, 0);

}

extern "C"
JNIEXPORT jobject JNICALL
Java_com_quickjs_QuickJSNativeImpl_newClass(JNIEnv *env, jobject thiz, jlong context_ptr,
                                              jint java_caller_id) {
    auto *ctx = reinterpret_cast<JSContext *>(context_ptr);
    JSValue func = JS_NewCFunctionMagic(ctx, js_worker_constructor, "Worker", 1,
                                        JS_CFUNC_constructor, java_caller_id);
    JS_SetPropertyStr(ctx, func, "java_caller_id", JS_NewInt32(ctx, java_caller_id));
    return TO_JAVA_OBJECT(env, ctx, func);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_quickjs_QuickJSNativeImpl_stringify(JNIEnv *env, jobject thiz, jlong context_ptr, jlong js_object) {
    auto *ctx = reinterpret_cast<JSContext *>(context_ptr);
    JSValue obj = JS_JSONStringify(ctx, JS_MKPTR(JS_TAG_OBJECT, reinterpret_cast<void *>(js_object)), JS_UNDEFINED, JS_UNDEFINED);
    const auto str = JS_ToCString(ctx, obj);
    jstring result = env->NewStringUTF(str);
    JS_FreeCString(ctx, str);
    return result;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_quickjs_QuickJSNativeImpl_toString(JNIEnv *env, jobject thiz, jlong context_ptr, jlong js_object) {
    auto *ctx = reinterpret_cast<JSContext *>(context_ptr);
    JSValue obj = JS_MKPTR(JS_TAG_OBJECT, reinterpret_cast<void *>(js_object));
    const char* str = JS_ToCString(ctx, obj);
    jstring result = env->NewStringUTF(str);
    JS_FreeCString(ctx, str);
    JS_FreeValue(ctx, obj);
    return result;
}

extern "C"
JNIEXPORT jobject JNICALL
Java_com_quickjs_QuickJSNativeImpl_parseJSON(JNIEnv *env, jobject thiz, jlong context_ptr, jstring json) {

    auto *ctx = reinterpret_cast<JSContext *>(context_ptr);
    const char *c_json = env->GetStringUTFChars(json, JNI_FALSE);

    auto jsonObj = JS_ParseJSON(ctx, c_json, strlen(c_json), "parseJSON.js");
    if (JS_IsException(jsonObj)) {
        JS_ThrowSyntaxError(ctx, "");
        return nullptr;
    }
    jobject result = To_JObject(env, context_ptr, JS_TAG_OBJECT, jsonObj);
    env->ReleaseStringUTFChars(json, c_json);
    return result;
}


extern "C"
JNIEXPORT void JNICALL
Java_com_quickjs_QuickJSNativeImpl_executePendingJobs(JNIEnv *env, jobject thiz,
                                                      jlong context_ptr) {
    auto *ctx = reinterpret_cast<JSContext *>(context_ptr);

    JSRuntime *rt = JS_GetRuntime(ctx);
    executePendingJobLoop(env, rt, ctx);
}
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_quickjs_QuickJSNativeImpl_isJobPending(JNIEnv *env, jobject thiz, jlong context_ptr) {
    auto *ctx = reinterpret_cast<JSContext *>(context_ptr);

    JSRuntime *rt = JS_GetRuntime(ctx);
    int ok = JS_IsJobPending(rt);
    return ok > 0;
}
extern "C"
JNIEXPORT jobject JNICALL
Java_com_quickjs_QuickJSNativeImpl_getPrototype(JNIEnv *env, jobject thiz, jlong context_ptr,
                                                jobject value) {
    auto *ctx = reinterpret_cast<JSContext *>(context_ptr);

    JSValue jsValue = TO_JS_VALUE(env, value);
    JSValue proto = JS_GetPrototype(ctx, jsValue);
    JS_FreeValue(ctx, jsValue);


    return TO_JAVA_OBJECT(env, ctx, proto);
}
extern "C"
JNIEXPORT jobject JNICALL
Java_com_quickjs_QuickJSNativeImpl_getObject(JNIEnv *env, jobject thiz, jlong context_ptr,
                                             jobject value) {
    auto *ctx = reinterpret_cast<JSContext *>(context_ptr);
    JSValue val = TO_JS_VALUE(env, value);
    jobject result = TO_JAVA_OBJECT(env, ctx, val);

    JS_FreeValue(ctx, val);
    return result;
}
extern "C"
JNIEXPORT jstring JNICALL
Java_com_quickjs_QuickJSNativeImpl_toJSString(JNIEnv *env, jobject thiz, jlong context_ptr, jobject value) {
    auto *ctx = reinterpret_cast<JSContext *>(context_ptr);
    JSValue val = TO_JS_VALUE(env, value);
    JSValue str = JS_ToString(ctx, val);
    const char *obj_str = JS_ToCString(ctx, str);
    jstring result = env->NewStringUTF(obj_str);
    JS_FreeCString(ctx, obj_str);
    JS_FreeValue(ctx, str);
    JS_FreeValue(ctx, val);
    return result;
}
extern "C"
JNIEXPORT void JNICALL
Java_com_quickjs_QuickJSNativeImpl_setPrototype(JNIEnv *env, jobject thiz, jlong context_ptr,
                                                jobject obj, jobject prototype) {
    auto *ctx = reinterpret_cast<JSContext *>(context_ptr);
    JSValue jsObj = TO_JS_VALUE(env, obj);
    JSValue jsPrototype = TO_JS_VALUE(env, prototype);
    JS_SetPrototype(ctx, jsObj, jsPrototype);
}
extern "C"
JNIEXPORT void JNICALL
Java_com_quickjs_QuickJSNativeImpl_setConstructor(JNIEnv *env, jobject thiz, jlong context_ptr,
                                                jobject obj, jobject constructor) {
    auto *ctx = reinterpret_cast<JSContext *>(context_ptr);
    JSValue jsObj = TO_JS_VALUE(env, obj);
    JSValue jsConstructor = TO_JS_VALUE(env, constructor);
    JS_SetConstructor(ctx, jsObj, jsConstructor);
}

extern "C"
JNIEXPORT jobject JNICALL
Java_com_quickjs_QuickJSNativeImpl_newError(JNIEnv *env, jobject thiz, jlong context_ptr,
                                            jstring message) {
    auto *ctx = reinterpret_cast<JSContext *>(context_ptr);
    const char *c_message = env->GetStringUTFChars(message, JNI_FALSE);
    JSValue jsError = JS_ThrowTypeError(ctx, "%s", c_message);
    env->ReleaseStringUTFChars(message, c_message);
    return TO_JAVA_OBJECT(env, ctx, jsError);
}
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_quickjs_QuickJSNativeImpl_isError(JNIEnv *env, jobject thiz, jlong context_ptr,
                                           jobject value) {
    auto *ctx = reinterpret_cast<JSContext *>(context_ptr);
    JSValue jsValue = TO_JS_VALUE(env, value);
    bool isError = JS_IsError(ctx, jsValue);
    return isError;
}