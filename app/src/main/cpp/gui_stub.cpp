#include "gui.hpp"
#include <deque>
#include <mutex>
#include <algorithm>

namespace GUI {
static u8 pads[2] = {0, 0};
static std::deque<int16_t> audioQueue;
static std::mutex mutex;

u8 get_joypad_state(int n) {
    if (n < 0 || n > 1) return 0;
    std::lock_guard<std::mutex> lock(mutex);
    return pads[n];
}

void set_joypad_state(int n, u8 state) {
    if (n < 0 || n > 1) return;
    std::lock_guard<std::mutex> lock(mutex);
    pads[n] = state;
}

void new_frame(u32*) {}

void new_samples(const blip_sample_t* samples, size_t count) {
    if (!samples || count == 0) return;
    std::lock_guard<std::mutex> lock(mutex);
    const size_t maxQueue = 44100 * 2;
    for (size_t i = 0; i < count; ++i) {
        if (audioQueue.size() >= maxQueue) audioQueue.pop_front();
        audioQueue.push_back((int16_t)std::clamp<int>(samples[i], -32768, 32767));
    }
}

size_t take_samples(int16_t* out, size_t maxCount) {
    if (!out || maxCount == 0) return 0;
    std::lock_guard<std::mutex> lock(mutex);
    size_t n = std::min(maxCount, audioQueue.size());
    for (size_t i = 0; i < n; ++i) {
        out[i] = audioQueue.front();
        audioQueue.pop_front();
    }
    return n;
}

void clear_samples() {
    std::lock_guard<std::mutex> lock(mutex);
    audioQueue.clear();
}

bool is_fast_forward() { return false; }
}
