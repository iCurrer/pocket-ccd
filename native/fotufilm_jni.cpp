// Fotufilm 引擎的 JNI 边界。
//
// 这一层只做三件事：解析 FSWP pack、校验缓冲区、把参数原样转交给上游
// `FotufilmHalideAndroid.cpp` 导出的 C 接口。它**不做任何色彩处理**。
//
// 色彩约定（来自上游源码，改之前先读 FotufilmConfigLayout.h）：
//   - 输入：display-linear RGB，三个 float32 平面分离存放。
//   - 输出：display-linear RGB。只有 ENCODE_OUT 变体才做输出变换，而 Android
//     AOT 的 5 个 kernel 都不是 encode 变体（见 FOTUFILM_CONFIG_OUTPUT_MATRIX
//     的注释："Other variants return display-linear light"），所以 sRGB 编码
//     必须由宿主完成。
//   - 平面尺寸必须严格等于 pack 头里的 width/height：pack 里的空间参数是
//     "毫米 × 该帧 pixels-per-mm"，换尺寸就得换 pack。
//
// 内存约定：调用方传 direct ByteBuffer，position 必须为 0。这里直接拿地址，
// 不做拷贝 —— 一张 12MP 的 float 平面是 144MB，拷贝一次就是一次 GC 事故。

#include <jni.h>

#include <android/log.h>

#include <algorithm>
#include <cmath>
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <memory>
#include <string>
#include <vector>

#include "FotufilmHalide.h"
#include "FotufilmResolvedFrameParams.h"

// 需要的 C 入口都由 FotufilmHalide.h 在 extern "C" 里声明好了，这里不再重复声明 ——
// 重复声明一旦哪天与上游签名不一致，报错会指向这个文件而不是真正的改动点。

namespace {

constexpr const char *kLogTag = "FotufilmJNI";

// 与上游 FotufilmHalideAndroid.cpp 的 kLutDimension / kLutValueCount 保持一致。
constexpr int32_t kLutDimension = 33;
constexpr int32_t kLutValueCount = kLutDimension * kLutDimension * kLutDimension * 4;

// FSWP 头是 10 个 int32（小端），随后依次是 config / exposure / film / paper。
constexpr size_t kHeaderInts = 10;
constexpr size_t kHeaderBytes = kHeaderInts * sizeof(int32_t);

// Vulkan 侧把 LUT 绑到这个长度；上游 parity 的 fixture.h 读完后也是补零到这里，
// 这里照做，保证与已验证的路径逐字节一致。
constexpr int32_t kLutBindingBound = 147456;

// 单帧尺寸上界。没有这个守卫，一个被篡改的包头就能让我们申请 200GB。
constexpr int32_t kMaxDimension = 20000;

enum : int32_t {
    kOk = 0,
    kInvalidArgument = -1,
    kBadPack = -2,
    kBufferTooSmall = -3,
    kKernelFailed = -4,
};

// 单线程一次调用用得上就够了；JNI 调用是同步的，不需要跨线程可见性。
thread_local std::string g_lastError;

int32_t fail(int32_t code, const std::string &message) {
    g_lastError = message;
    __android_log_print(ANDROID_LOG_ERROR, kLogTag, "%s (code %d)", message.c_str(), code);
    return code;
}

struct Pack {
    int32_t width = 0;
    int32_t height = 0;
    int32_t mask = 0;
    uint32_t seed = 0;
    std::vector<float> config;
    std::vector<float> exposure;
    std::vector<float> film;
    std::vector<float> paper;
};

int32_t parsePack(const uint8_t *data, size_t size, Pack &out) {
    if (size < kHeaderBytes) {
        return fail(kBadPack, "pack 比 40 字节的头部还短");
    }
    int32_t header[kHeaderInts];
    std::memcpy(header, data, kHeaderBytes);

    if (std::memcmp(header, "FSWP", 4) != 0) {
        return fail(kBadPack, "pack 缺少 FSWP magic");
    }
    if (header[1] != 1 && header[1] != 2) {
        return fail(kBadPack, "不支持的 FSWP 版本 " + std::to_string(header[1]));
    }
    if (header[6] != int32_t(FOTUFILM_FRAME_CONFIGURATION_COUNT)) {
        return fail(kBadPack, "config 长度 " + std::to_string(header[6]) + " 与编译期常量 "
                              + std::to_string(int32_t(FOTUFILM_FRAME_CONFIGURATION_COUNT))
                              + " 不一致，说明 pack 与引擎版本不匹配");
    }
    if (header[7] != kLutDimension) {
        return fail(kBadPack, "LUT 维度 " + std::to_string(header[7]) + " 不是 33");
    }
    if (header[8] != kLutValueCount) {
        return fail(kBadPack, "LUT 值数量 " + std::to_string(header[8]) + " 不是 "
                              + std::to_string(kLutValueCount));
    }
    if (header[2] <= 0 || header[3] <= 0
        || header[2] > kMaxDimension || header[3] > kMaxDimension) {
        return fail(kBadPack, "帧尺寸不合理 " + std::to_string(header[2]) + "x"
                              + std::to_string(header[3]));
    }

    const size_t configFloats = size_t(header[6]);
    const size_t lutFloats = size_t(header[8]);
    const size_t expected = kHeaderBytes + (configFloats + 3 * lutFloats) * sizeof(float);
    if (size < expected) {
        return fail(kBadPack, "pack 被截断：头部声明 " + std::to_string(expected)
                              + " 字节，实际只有 " + std::to_string(size));
    }

    const uint8_t *cursor = data + kHeaderBytes;
    auto take = [&](std::vector<float> &destination, size_t count, bool padToBindingBound) {
        destination.resize(padToBindingBound ? size_t(kLutBindingBound) : count);
        std::memcpy(destination.data(), cursor, count * sizeof(float));
        if (padToBindingBound && destination.size() > count) {
            std::fill(destination.begin() + static_cast<std::ptrdiff_t>(count),
                      destination.end(), 0.0f);
        }
        cursor += count * sizeof(float);
    };
    take(out.config, configFloats, false);
    take(out.exposure, lutFloats, true);
    take(out.film, lutFloats, true);
    take(out.paper, lutFloats, true);

    out.width = header[2];
    out.height = header[3];
    out.mask = header[4];
    out.seed = uint32_t(header[5]);
    return kOk;
}

/// 取一个 direct ByteBuffer 的基址，并要求容量足够。
int32_t planeAddress(JNIEnv *env, jobject buffer, size_t floats, const char *name,
                     const float **address) {
    if (buffer == nullptr) {
        return fail(kInvalidArgument, std::string(name) + " 是 null");
    }
    if (env->GetDirectBufferAddress(buffer) == nullptr) {
        return fail(kInvalidArgument,
                    std::string(name) + " 不是 direct Buffer；这里不拷贝，必须是 direct");
    }
    const jlong capacity = env->GetDirectBufferCapacity(buffer);
    if (capacity < jlong(floats * sizeof(float))) {
        return fail(kBufferTooSmall,
                    std::string(name) + " 容量 " + std::to_string(capacity) + " 字节，需要 "
                        + std::to_string(floats * sizeof(float)));
    }
    *address = static_cast<const float *>(env->GetDirectBufferAddress(buffer));
    return kOk;
}

int32_t writePlane(JNIEnv *env, jobject buffer, size_t floats, const char *name,
                   float **address) {
    const float *base = nullptr;
    const int32_t status = planeAddress(env, buffer, floats, name, &base);
    if (status != kOk) {
        return status;
    }
    *address = const_cast<float *>(base);
    return kOk;
}

Pack *packFromHandle(jlong handle) { return reinterpret_cast<Pack *>(handle); }

/// 一次走完 develop + print。全帧调用等价于 output 与 input 同尺寸、origin 为 0
/// 的 strip 调用，所以这里不区分两条路径，避免两份几何校验逻辑漂移。
int32_t processFrame(JNIEnv *env, jlong handle,
                     jobject input_r, jobject input_g, jobject input_b,
                     jobject output_r, jobject output_g, jobject output_b,
                     int32_t inputWidth, int32_t inputHeight,
                     int32_t outputWidth, int32_t outputHeight,
                     int32_t originX, int32_t originY,
                     int32_t interiorTop, int32_t interiorHeight,
                     int32_t featureMask, int32_t seed) {
    Pack *pack = packFromHandle(handle);
    if (pack == nullptr) {
        return fail(kInvalidArgument, "pack handle 无效（0 或已释放）");
    }
    if (inputWidth <= 0 || inputHeight <= 0) {
        return fail(kInvalidArgument, "输入尺寸必须为正");
    }
    if (outputWidth < inputWidth || outputHeight < inputHeight) {
        return fail(kInvalidArgument, "输出画布小于输入块");
    }
    if (originX < 0 || originY < 0 || originX > outputWidth - inputWidth
        || originY > outputHeight - inputHeight) {
        return fail(kInvalidArgument, "origin 超出输出画布");
    }
    if (interiorTop < 0 || interiorHeight <= 0
        || interiorTop + interiorHeight > inputHeight) {
        return fail(kInvalidArgument, "interior 行区间超出输入块");
    }

    const size_t inputFloats = size_t(inputWidth) * size_t(inputHeight);
    const size_t outputFloats = size_t(outputWidth) * size_t(outputHeight);

    const float *in_r = nullptr;
    const float *in_g = nullptr;
    const float *in_b = nullptr;
    float *out_r = nullptr;
    float *out_g = nullptr;
    float *out_b = nullptr;

    int32_t status = planeAddress(env, input_r, inputFloats, "inputR", &in_r);
    if (status == kOk) status = planeAddress(env, input_g, inputFloats, "inputG", &in_g);
    if (status == kOk) status = planeAddress(env, input_b, inputFloats, "inputB", &in_b);
    if (status == kOk) status = writePlane(env, output_r, outputFloats, "outputR", &out_r);
    if (status == kOk) status = writePlane(env, output_g, outputFloats, "outputG", &out_g);
    if (status == kOk) status = writePlane(env, output_b, outputFloats, "outputB", &out_b);
    if (status != kOk) {
        return status;
    }

    // 负值表示"用 pack 里记录的"，正常 mask 与 seed 都不会是负数。
    const int32_t mask = featureMask < 0 ? pack->mask : featureMask;
    const uint32_t resolvedSeed = seed < 0 ? pack->seed : uint32_t(seed);

    const int32_t error = fotufilm_halide_process_strip(
        in_r, in_g, in_b, out_r, out_g, out_b,
        inputWidth, inputHeight, outputWidth, outputHeight,
        originX, originY, interiorTop, interiorHeight,
        pack->config.data(), pack->exposure.data(), pack->film.data(), pack->paper.data(),
        kLutDimension, mask, resolvedSeed);
    if (error != 0) {
        return fail(kKernelFailed, "Halide kernel 返回 " + std::to_string(error));
    }
    return kOk;
}

}  // namespace

extern "C" {

JNIEXPORT jint JNICALL
Java_com_pocketccd_prototype_fotufilm_FotufilmNative_nativeAbiVersion(JNIEnv *, jobject) {
    // 1：nativeProcess / nativeProcessStrip / nativeMaxHalo / nativePackInfo
    return 1;
}

JNIEXPORT jboolean JNICALL
Java_com_pocketccd_prototype_fotufilm_FotufilmNative_nativeAvailable(JNIEnv *, jobject) {
    return fotufilm_halide_available() == 1 ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jlong JNICALL
Java_com_pocketccd_prototype_fotufilm_FotufilmNative_nativeOpenPack(JNIEnv *env, jobject,
                                                                   jbyteArray bytes) {
    if (bytes == nullptr) {
        fail(kInvalidArgument, "pack 字节为 null");
        return 0;
    }
    const jsize length = env->GetArrayLength(bytes);
    if (length <= 0) {
        fail(kBadPack, "pack 为空");
        return 0;
    }
    // 注意：这里必须写 static_cast。写成 `raw(size_t(length))` 会被 C++ 按
    // "类型 (参数名)" 解析成函数声明 `vector<uint8_t> raw(size_t length)`，
    // 于是 raw 变成函数类型，后面的 raw.data()/raw.size() 全部编译失败
    // （报 "member reference base type ... is not a structure or union"）。
    std::vector<uint8_t> raw(static_cast<size_t>(length));
    env->GetByteArrayRegion(bytes, 0, length, reinterpret_cast<jbyte *>(raw.data()));
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        fail(kInvalidArgument, "读取 pack 字节失败");
        return 0;
    }

    auto pack = std::make_unique<Pack>();
    if (parsePack(raw.data(), raw.size(), *pack) != kOk) {
        return 0;
    }
    return reinterpret_cast<jlong>(pack.release());
}

JNIEXPORT void JNICALL
Java_com_pocketccd_prototype_fotufilm_FotufilmNative_nativeReleasePack(JNIEnv *, jobject,
                                                                      jlong handle) {
    delete packFromHandle(handle);
}

/// [width, height, featureMask, seed]。seed 以有符号 int 传出，Kotlin 侧用
/// toUInt() 还原 —— 12MP 下这 4 个 int 的开销可以忽略。
JNIEXPORT jintArray JNICALL
Java_com_pocketccd_prototype_fotufilm_FotufilmNative_nativePackInfo(JNIEnv *env, jobject,
                                                                   jlong handle) {
    Pack *pack = packFromHandle(handle);
    if (pack == nullptr) {
        fail(kInvalidArgument, "pack handle 无效");
        return nullptr;
    }
    const jint values[4] = {pack->width, pack->height, pack->mask, jint(pack->seed)};
    jintArray result = env->NewIntArray(4);
    if (result != nullptr) {
        env->SetIntArrayRegion(result, 0, 4, values);
    }
    return result;
}

/**
 * 该 pack 在配置下实际会散开的像素半径上界。
 *
 * strip 分块处理必须按这个值加 halo，否则每条带子的接缝会出现扩散/晕光断裂。
 * 这里用与 kernel 相同的 ResolvedFrameParams 推导，避免宿主侧重算一套公式而漂移。
 */
JNIEXPORT jint JNICALL
Java_com_pocketccd_prototype_fotufilm_FotufilmNative_nativeMaxHalo(JNIEnv *, jobject,
                                                                  jlong handle) {
    Pack *pack = packFromHandle(handle);
    if (pack == nullptr) {
        fail(kInvalidArgument, "pack handle 无效");
        return -1;
    }
    const fotufilm::ResolvedFrameParams resolved(
        pack->config.data(), pack->width, pack->height, pack->seed,
        (pack->mask & FOTUFILM_FRAME_REVERSAL) != 0, 0, 0);
    int32_t maximum = 0;
    const int32_t radii[] = {
        resolved.mtf_radius_0, resolved.mtf_radius_1, resolved.mtf_radius_2,
        resolved.mtf_luma_radius,
        resolved.coupler_radius, resolved.adjacency_radius,
        resolved.adjacency_secondary_radius, resolved.fringe_radius,
        resolved.grain_radius, resolved.mottle_radius, resolved.print_mtf_radius,
        resolved.halation_radius_0, resolved.halation_radius_1, resolved.halation_radius_2,
    };
    for (int32_t radius : radii) {
        maximum = std::max(maximum, radius);
    }
    // diffusion 的 ResolvedFrameParams 只暴露 stride 化之后的半径，但输入侧真正要留的
    // 是原始像素半径，所以直接从 config 读。
    for (int32_t offset = FOTUFILM_CONFIG_DIFFUSION_RADIUS;
         offset < FOTUFILM_CONFIG_DIFFUSION_RADIUS + 3; ++offset) {
        maximum = std::max(maximum, int32_t(std::max(0.0f, pack->config[size_t(offset)])));
    }
    return jint(maximum);
}

JNIEXPORT jint JNICALL
Java_com_pocketccd_prototype_fotufilm_FotufilmNative_nativeProcess(
    JNIEnv *env, jobject, jlong handle,
    jobject input_r, jobject input_g, jobject input_b,
    jobject output_r, jobject output_g, jobject output_b,
    jint width, jint height, jint featureMask, jint seed) {
    return processFrame(env, handle, input_r, input_g, input_b,
                        output_r, output_g, output_b,
                        width, height, width, height, 0, 0, 0, height,
                        featureMask, seed);
}

/**
 * 行带状分块。输入块位于全帧的 (0, originY)，块内 [interiorTop, interiorTop+interiorHeight)
 * 这一段才是本带真正要输出到 outputHeight 画布上的行。
 *
 * 调用方负责让 inputHeight = interiorHeight + 上下 halo，并把 halo 之外的输入行读成
 * 边缘延拓 —— kernel 会真的去读这些像素。
 */
JNIEXPORT jint JNICALL
Java_com_pocketccd_prototype_fotufilm_FotufilmNative_nativeProcessStrip(
    JNIEnv *env, jobject, jlong handle,
    jobject input_r, jobject input_g, jobject input_b,
    jobject output_r, jobject output_g, jobject output_b,
    jint inputWidth, jint inputHeight, jint outputWidth, jint outputHeight,
    jint originX, jint originY, jint interiorTop, jint interiorHeight,
    jint featureMask, jint seed) {
    return processFrame(env, handle, input_r, input_g, input_b,
                        output_r, output_g, output_b,
                        inputWidth, inputHeight, outputWidth, outputHeight,
                        originX, originY, interiorTop, interiorHeight,
                        featureMask, seed);
}

/// 最近一次失败的原因；成功调用不会清零，便于在 catch 之外排查。
JNIEXPORT jstring JNICALL
Java_com_pocketccd_prototype_fotufilm_FotufilmNative_nativeLastError(JNIEnv *env, jobject) {
    if (g_lastError.empty()) {
        return nullptr;
    }
    return env->NewStringUTF(g_lastError.c_str());
}

}  // extern "C"
