// PermKeeper Zygisk module: injects the Java watcher into system_server.
#include <jni.h>
#include <android/log.h>
#include <cstring>

#include "zygisk.hpp"
#include "dex_data.h"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "PermKeeperZ", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "PermKeeperZ", __VA_ARGS__)

static JavaVM *g_vm = nullptr;

static void load_and_install(JNIEnv *env) {
    if (env == nullptr) {
        LOGE("env null");
        return;
    }
    jobject bb = env->NewDirectByteBuffer((void *) dex_data, (jlong) dex_data_len);
    if (bb == nullptr) {
        LOGE("NewDirectByteBuffer failed");
        return;
    }
    jclass clClass = env->FindClass("java/lang/ClassLoader");
    if (clClass == nullptr) {
        LOGE("ClassLoader not found");
        env->ExceptionClear();
        return;
    }
    jmethodID getSystem = env->GetStaticMethodID(
            clClass, "getSystemClassLoader", "()Ljava/lang/ClassLoader;");
    jobject parent = env->CallStaticObjectMethod(clClass, getSystem);

    jclass inMem = env->FindClass("dalvik/system/InMemoryDexClassLoader");
    if (inMem == nullptr) {
        LOGE("InMemoryDexClassLoader not found");
        env->ExceptionClear();
        return;
    }
    jmethodID ctor = env->GetMethodID(
            inMem, "<init>", "(Ljava/nio/ByteBuffer;Ljava/lang/ClassLoader;)V");
    jobject loader = env->NewObject(inMem, ctor, bb, parent);
    if (loader == nullptr) {
        LOGE("InMemoryDexClassLoader ctor failed");
        env->ExceptionClear();
        return;
    }
    jmethodID loadClass = env->GetMethodID(
            clClass, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");
    jstring name = env->NewStringUTF("com.permkeeper.z.SystemHook");
    jclass hook = (jclass) env->CallObjectMethod(loader, loadClass, name);
    if (hook == nullptr) {
        LOGE("SystemHook class not found");
        env->ExceptionClear();
        return;
    }
    jmethodID install = env->GetStaticMethodID(hook, "install", "()V");
    if (install == nullptr) {
        LOGE("install() not found");
        env->ExceptionClear();
        return;
    }
    env->CallStaticVoidMethod(hook, install);
    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
    }
    LOGI("SystemHook.install() invoked");
}

class PermKeeperModule : public zygisk::ModuleBase {
public:
    void onLoad(zygisk::Api *api, JNIEnv *env) override {
        api_ = api;
        env_ = env;
        env->GetJavaVM(&g_vm);
    }

    void postServerSpecialize(const zygisk::ServerSpecializeArgs *) override {
        // Runs inside system_server after specialization.
        JNIEnv *env = env_;
        if (env == nullptr && g_vm != nullptr) {
            g_vm->GetEnv((void **) &env, JNI_VERSION_1_6);
        }
        LOGI("postServerSpecialize: injecting");
        load_and_install(env);
    }

private:
    zygisk::Api *api_ = nullptr;
    JNIEnv *env_ = nullptr;
};

REGISTER_ZYGISK_MODULE(PermKeeperModule)
