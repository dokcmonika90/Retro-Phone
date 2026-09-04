#include "gui.hpp"
#include "cpu.hpp"
#include "apu.hpp"

namespace APU {
Nes_Apu apu;
Blip_Buffer buf;
static int last_irq_check_time = -1;
static blip_sample_t outBuf[4096];

static void irq_changed(void*) {}

void init() {
    static bool initialized = false;
    if (initialized) return;
    initialized = true;
    buf.sample_rate(44100);
    buf.clock_rate(1789773);
    apu.output(&buf);
    apu.dmc_reader(CPU::dmc_read);
    apu.irq_notifier(irq_changed);
}

void reset() {
    init();
    apu.reset();
    buf.clear();
    last_irq_check_time = -1;
    GUI::clear_samples();
}

template <bool write> u8 access(int elapsed, u16 addr, u8 v, bool is_put_cycle) {
    init();
    if (write) apu.write_register(elapsed, addr, v, is_put_cycle);
    else if (addr == 0x4015) {
        u8 status = apu.read_status(elapsed, is_put_cycle);
        v = (status & 0xDF) | (v & 0x20);
    }
    return v;
}
template u8 access<0>(int, u16, u8, bool);
template u8 access<1>(int, u16, u8, bool);

void run_frame(int elapsed) { init(); apu.end_frame(elapsed); last_irq_check_time = -1; }

void end_buffer_frame(int elapsed) {
    init();
    buf.end_frame(elapsed);
    while (buf.samples_avail() > 0) {
        int count = buf.samples_avail() > 4096 ? 4096 : buf.samples_avail();
        count = buf.read_samples(outBuf, count);
        if (count <= 0) break;
        GUI::new_samples(outBuf, (size_t)count);
    }
}

bool check_irq(int elapsed) {
    init();
    if (elapsed != last_irq_check_time) {
        apu.run_until(elapsed);
        last_irq_check_time = elapsed;
    }
    cpu_time_t t = apu.earliest_irq();
    return (t == Nes_Apu::irq_waiting) || (t != Nes_Apu::no_irq && t <= elapsed);
}

Blip_Buffer& get_buffer() { init(); return buf; }
Nes_Apu& get_apu() { init(); return apu; }
}
