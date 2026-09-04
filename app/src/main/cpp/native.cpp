#include <jni.h>
#include <android/log.h>
#include <cstdio>
#include <mutex>
#include <string>
#include <vector>
#include "cpu.hpp"
#include "ppu.hpp"
#include "cartridge.hpp"
#include "apu.hpp"
#include "gui.hpp"

#define LOG_TAG "RetroPhone"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace PPU { extern u32 pixels[256 * 240]; }

static std::mutex emuMutex;

static bool writeRomFile(JNIEnv* env, jbyteArray data) {
    if (!data) return false;
    const jsize size = env->GetArrayLength(data);
    if (size < 16) return false;
    std::vector<jbyte> bytes((size_t)size);
    env->GetByteArrayRegion(data, 0, size, bytes.data());
    const char* path = "/data/data/com.dokcmonika90.retrophone/cache/retrophone-current.nes";
    FILE* f = fopen(path, "wb");
    if (!f) { LOGE("Unable to create ROM cache file"); return false; }
    const size_t written = fwrite(bytes.data(), 1, (size_t)size, f);
    fclose(f);
    return written == (size_t)size;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_dokcmonika90_retrophone_MainActivity_nativeVersion(JNIEnv* env, jobject) {
    return env->NewStringUTF("Retro Phone — LaiNES cycle-accurate core");
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_dokcmonika90_retrophone_MainActivity_nativeLoad(JNIEnv* env, jobject, jbyteArray data) {
    std::lock_guard<std::mutex> lock(emuMutex);
    if (!writeRomFile(env, data)) return JNI_FALSE;
    const char* path = "/data/data/com.dokcmonika90.retrophone/cache/retrophone-current.nes";
    Cartridge::load(path);
    return Cartridge::loaded() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_dokcmonika90_retrophone_MainActivity_nativeReset(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(emuMutex);
    if (Cartridge::loaded()) Cartridge::reset();
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_dokcmonika90_retrophone_MainActivity_nativeFrame(JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> lock(emuMutex);
    jintArray out = env->NewIntArray(256 * 240);
    if (!out) return nullptr;
    std::vector<jint> pixels(256 * 240);
    for (size_t i = 0; i < pixels.size(); ++i)
        pixels[i] = (jint)(0xFF000000u | (PPU::pixels[i] & 0x00FFFFFFu));
    env->SetIntArrayRegion(out, 0, (jsize)pixels.size(), pixels.data());
    return out;
}

extern "C" JNIEXPORT void JNICALL
Java_com_dokcmonika90_retrophone_MainActivity_nativeRunFrame(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(emuMutex);
    if (Cartridge::loaded()) CPU::run_frame();
}

extern "C" JNIEXPORT void JNICALL
Java_com_dokcmonika90_retrophone_MainActivity_nativeButtons(JNIEnv*, jobject, jint mask) {
    u8 p = 0;
    if (mask & 16)  p |= 0x01;
    if (mask & 32)  p |= 0x02;
    if (mask & 64)  p |= 0x04;
    if (mask & 128) p |= 0x08;
    if (mask & 8)   p |= 0x10;
    if (mask & 4)   p |= 0x20;
    if (mask & 2)   p |= 0x40;
    if (mask & 1)   p |= 0x80;
    GUI::set_joypad_state(0, p);
}

extern "C" JNIEXPORT jshortArray JNICALL
Java_com_dokcmonika90_retrophone_MainActivity_nativeAudio(JNIEnv* env, jobject) {
    int16_t samples[2048];
    const size_t count = GUI::take_samples(samples, 2048);
    if (count == 0) return env->NewShortArray(0);
    jshortArray out = env->NewShortArray((jsize)count);
    if (!out) return nullptr;
    env->SetShortArrayRegion(out, 0, (jsize)count, samples);
    return out;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_dokcmonika90_retrophone_MainActivity_nativeIsLoaded(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(emuMutex);
    return Cartridge::loaded() ? JNI_TRUE : JNI_FALSE;
}
