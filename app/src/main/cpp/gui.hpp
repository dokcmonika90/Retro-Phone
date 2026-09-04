#include "common.hpp"
#include <Blip_Buffer.h>
#include <cstddef>
#include <cstdint>

namespace GUI {
u8 get_joypad_state(int n);
void set_joypad_state(int n, u8 state);
void new_frame(u32* pixels);
void new_samples(const blip_sample_t* samples, size_t count);
size_t take_samples(int16_t* out, size_t maxCount);
void clear_samples();
bool is_fast_forward();
}
