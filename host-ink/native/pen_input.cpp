#include <jni.h>
#include <atomic>
#include <algorithm>
#include <cmath>
#include <chrono>
#include <cstdint>

namespace {
std::atomic<bool> stopped{false};
struct Sink {
    JNIEnv* env; jobject owner; jmethodID method;
    Sink(JNIEnv* e, jobject o) : env(e), owner(o), method(e->GetMethodID(e->GetObjectClass(o), "onNativeSample", "(IJJIIIIZ)V")) {}
    void emit(int action, int64_t pointer, int64_t at, int x, int y, int pressure, int tilt, bool eraser) {
        if (!method || env->ExceptionCheck()) { stopped = true; return; }
        env->CallVoidMethod(owner, method, jint(action), jlong(pointer), jlong(at), jint(x), jint(y), jint(pressure), jint(tilt), jboolean(eraser));
        if (env->ExceptionCheck()) stopped = true;
    }
};
int64_t now_ms() { return std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now().time_since_epoch()).count(); }
void unavailable(JNIEnv* env, const char* message) {
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), message);
}
}
extern "C" JNIEXPORT void JNICALL Java_com_halo_host_ink_DesktopPenSource_prepareNative(JNIEnv*, jobject) { stopped = false; }

#ifdef _WIN32
#include <windows.h>
#include <windowsx.h>
namespace {
std::atomic<DWORD> window_thread{0};
LRESULT CALLBACK pen_window(HWND window, UINT message, WPARAM w, LPARAM l) {
    auto* sink = reinterpret_cast<Sink*>(GetWindowLongPtr(window, GWLP_USERDATA));
    if (message == WM_NCCREATE) {
        sink = static_cast<Sink*>(reinterpret_cast<CREATESTRUCT*>(l)->lpCreateParams);
        SetWindowLongPtr(window, GWLP_USERDATA, reinterpret_cast<LONG_PTR>(sink));
    }
    if (sink && (message == WM_POINTERDOWN || message == WM_POINTERUPDATE || message == WM_POINTERUP)) {
        POINTER_PEN_INFO info{};
        if (!GetPointerPenInfo(GET_POINTERID_WPARAM(w), &info)) return 0; // Ignore mouse/touch.
        RECT rect{}; GetClientRect(window, &rect);
        POINT point = info.pointerInfo.ptPixelLocation; ScreenToClient(window, &point);
        if (rect.right <= 0 || rect.bottom <= 0) return 0;
        int action = message == WM_POINTERDOWN ? 1 : message == WM_POINTERUP ? 3 : 2;
        if (info.pointerInfo.pointerFlags & POINTER_FLAG_CANCELED) action = 4;
        const int pressure = info.penMask & PEN_MASK_PRESSURE ? std::clamp(int(info.pressure) * 1000 / 1024, 0, 1000) : -1;
        int tilt = -1;
        if ((info.penMask & (PEN_MASK_TILT_X | PEN_MASK_TILT_Y)) == (PEN_MASK_TILT_X | PEN_MASK_TILT_Y)) {
            constexpr double rad = 3.141592653589793 / 180.0;
            tilt = int(std::atan(std::hypot(std::tan(std::clamp(int(info.tiltX), -89, 89) * rad),
                std::tan(std::clamp(int(info.tiltY), -89, 89) * rad))) * 1000);
        }
        sink->emit(action, info.pointerInfo.pointerId, now_ms(),
            std::clamp(point.x * 1000 / rect.right, 0L, 1000L), std::clamp(point.y * 1000 / rect.bottom, 0L, 1000L),
            pressure, tilt, (info.penFlags & (PEN_FLAG_ERASER | PEN_FLAG_INVERTED | PEN_FLAG_BARREL)) != 0);
        return 0;
    }
    if (sink && (message == WM_KILLFOCUS || message == WM_POINTERCAPTURECHANGED)) sink->emit(4, 0, now_ms(), 0, 0, -1, -1, false);
    if (message == WM_CLOSE) { DestroyWindow(window); return 0; }
    if (message == WM_DESTROY) { PostQuitMessage(0); return 0; }
    return DefWindowProc(window, message, w, l);
}
}
extern "C" JNIEXPORT void JNICALL Java_com_halo_host_ink_DesktopPenSource_readWindows(JNIEnv* env, jobject owner) {
    Sink sink(env, owner);
    const auto instance = GetModuleHandle(nullptr);
    WNDCLASS cls{}; cls.lpfnWndProc = pen_window; cls.hInstance = instance; cls.lpszClassName = L"HaloInkPenInputV1";
    cls.hCursor = LoadCursor(nullptr, IDC_CROSS);
    RegisterClass(&cls);
    HWND window = CreateWindow(cls.lpszClassName, L"halo - drawing input", WS_OVERLAPPEDWINDOW | WS_VISIBLE,
        CW_USEDEFAULT, CW_USEDEFAULT, 980, 700, nullptr, nullptr, instance, &sink);
    if (!window) { unavailable(env, "Pen window unavailable"); return; }
    window_thread = GetCurrentThreadId();
    MSG message{};
    while (!stopped && GetMessage(&message, nullptr, 0, 0) > 0) { TranslateMessage(&message); DispatchMessage(&message); }
    DestroyWindow(window); window_thread = 0;
}
extern "C" JNIEXPORT void JNICALL Java_com_halo_host_ink_DesktopPenSource_readLinux(JNIEnv* env, jobject, jstring) { unavailable(env, "Linux pen input unavailable on this host"); }
extern "C" JNIEXPORT void JNICALL Java_com_halo_host_ink_DesktopPenSource_stopNative(JNIEnv*, jobject) {
    stopped = true; const DWORD thread = window_thread.load(); if (thread) PostThreadMessage(thread, WM_QUIT, 0, 0);
}
#elif defined(__linux__)
#include <linux/input.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <poll.h>
#include <unistd.h>
#include <cstring>
namespace {
bool bit(const unsigned char* data, unsigned code) { return (data[code / 8] & (1U << (code % 8))) != 0; }
int normalized(int value, const input_absinfo& axis) {
    if (axis.maximum <= axis.minimum) return -1;
    return int(std::clamp((int64_t(value) - axis.minimum) * 1000 / (int64_t(axis.maximum) - axis.minimum), int64_t(0), int64_t(1000)));
}
}
extern "C" JNIEXPORT void JNICALL Java_com_halo_host_ink_DesktopPenSource_readLinux(JNIEnv* env, jobject owner, jstring selected) {
    const char* path = env->GetStringUTFChars(selected, nullptr);
    if (!path) return;
    const int fd = open(path, O_RDONLY | O_NONBLOCK | O_CLOEXEC); env->ReleaseStringUTFChars(selected, path);
    if (fd < 0) { unavailable(env, "Selected pen input unavailable; check local device access"); return; }
    struct stat status{}; unsigned char keys[(KEY_MAX + 8) / 8]{};
    input_absinfo x{}, y{}, pressure{};
    bool valid = fstat(fd, &status) == 0 && S_ISCHR(status.st_mode) &&
        ioctl(fd, EVIOCGBIT(EV_KEY, sizeof(keys)), keys) >= 0 &&
        (bit(keys, BTN_TOOL_PEN) || bit(keys, BTN_TOOL_RUBBER)) &&
        ioctl(fd, EVIOCGABS(ABS_X), &x) == 0 && ioctl(fd, EVIOCGABS(ABS_Y), &y) == 0 &&
        x.maximum > x.minimum && y.maximum > y.minimum;
    const bool has_pressure = ioctl(fd, EVIOCGABS(ABS_PRESSURE), &pressure) == 0 && pressure.maximum > pressure.minimum;
    if (!valid) { close(fd); unavailable(env, "Selected input is not a supported absolute pen"); return; }
    Sink sink(env, owner);
    int px = x.value, py = y.value, pp = pressure.value;
    unsigned char initial_state[(KEY_MAX + 8) / 8]{};
    if (ioctl(fd, EVIOCGKEY(sizeof(initial_state)), initial_state) < 0) { close(fd); unavailable(env, "Pen state unavailable"); return; }
    bool pen = bit(initial_state, BTN_TOOL_PEN), rubber = bit(initial_state, BTN_TOOL_RUBBER), touch = bit(initial_state, BTN_TOUCH);
    bool barrel = bit(initial_state, BTN_STYLUS), active = false, dropped = false, suppress = touch;
    pollfd poller{fd, POLLIN, 0};
    while (!stopped) {
        const int ready = poll(&poller, 1, 250);
        if (ready < 0 || (poller.revents & (POLLERR | POLLHUP | POLLNVAL))) break;
        if (!ready) continue;
        input_event event{};
        if (read(fd, &event, sizeof(event)) != sizeof(event)) continue;
        if (event.type == EV_SYN && event.code == SYN_DROPPED) {
            sink.emit(4, 0, now_ms(), 0, 0, -1, -1, false); active = false; dropped = true; suppress = true; continue;
        }
        if (dropped) {
            if (event.type == EV_SYN && event.code == SYN_REPORT) {
                unsigned char state[(KEY_MAX + 8) / 8]{};
                if (ioctl(fd, EVIOCGKEY(sizeof(state)), state) < 0 || ioctl(fd, EVIOCGABS(ABS_X), &x) < 0 || ioctl(fd, EVIOCGABS(ABS_Y), &y) < 0) break;
                pen = bit(state, BTN_TOOL_PEN); rubber = bit(state, BTN_TOOL_RUBBER); touch = bit(state, BTN_TOUCH); barrel = bit(state, BTN_STYLUS);
                px = x.value; py = y.value;
                if (has_pressure && ioctl(fd, EVIOCGABS(ABS_PRESSURE), &pressure) == 0) pp = pressure.value;
                dropped = false; suppress = touch;
            }
            continue;
        }
        if (event.type == EV_ABS) {
            if (event.code == ABS_X) px = event.value; if (event.code == ABS_Y) py = event.value;
            if (event.code == ABS_PRESSURE) pp = event.value;
        }
        if (event.type == EV_KEY && event.value != 2) {
            if (event.code == BTN_TOOL_PEN) pen = event.value != 0;
            if (event.code == BTN_TOOL_RUBBER) rubber = event.value != 0;
            if (event.code == BTN_TOUCH) touch = event.value != 0;
            if (event.code == BTN_STYLUS) barrel = event.value != 0;
        }
        if (event.type == EV_SYN && event.code == SYN_REPORT) {
            if (suppress) { if (!touch) suppress = false; continue; }
            const bool contact = (pen || rubber) && touch;
            const int action = contact ? (active ? 2 : 1) : (active ? 3 : 0);
            if (action) sink.emit(action, 0, now_ms(), normalized(px, x), normalized(py, y), has_pressure ? normalized(pp, pressure) : -1, -1, rubber || barrel);
            active = contact;
        }
    }
    sink.emit(4, 0, now_ms(), 0, 0, -1, -1, false); close(fd);
}
extern "C" JNIEXPORT void JNICALL Java_com_halo_host_ink_DesktopPenSource_readWindows(JNIEnv* env, jobject) { unavailable(env, "Windows pen input unavailable on this host"); }
extern "C" JNIEXPORT void JNICALL Java_com_halo_host_ink_DesktopPenSource_stopNative(JNIEnv*, jobject) { stopped = true; }
#else
extern "C" JNIEXPORT void JNICALL Java_com_halo_host_ink_DesktopPenSource_readWindows(JNIEnv* env, jobject) { unavailable(env, "Pen host unavailable"); }
extern "C" JNIEXPORT void JNICALL Java_com_halo_host_ink_DesktopPenSource_readLinux(JNIEnv* env, jobject, jstring) { unavailable(env, "Pen host unavailable"); }
extern "C" JNIEXPORT void JNICALL Java_com_halo_host_ink_DesktopPenSource_stopNative(JNIEnv*, jobject) {}
#endif
