#include "cartridge.hpp"
#include "cpu.hpp"
#include "ppu.hpp"
#include "apu.hpp"
#include "mappers/mapper0.hpp"
#include "mappers/mapper4.hpp"
#include <cstdio>
#include <string>

namespace Cartridge {
Mapper* mapper = nullptr;
std::string currentRomPath;
u8 currentMapperId = 0;

template <bool wr> u8 access(u16 addr, u8 v) {
    if (!mapper) return v;
    return wr ? mapper->write(addr, v) : mapper->read(addr);
}
template u8 access<0>(u16, u8); template u8 access<1>(u16, u8);

template <bool wr> u8 chr_access(u16 addr, u8 v) {
    if (!mapper) return v;
    if (!wr) { mapper->ppu_read_hook(addr); return mapper->chr_read(addr); }
    return mapper->chr_write(addr, v);
}
template u8 chr_access<0>(u16, u8); template u8 chr_access<1>(u16, u8);

void signal_scanline(int scanline) { if (mapper) mapper->signal_scanline(scanline); }
void ppu_write_hook(u16 addr, u8 v) { if (mapper) mapper->ppu_write_hook(addr, v); }

void load(const char* fileName) {
    FILE* f = fopen(fileName, "rb");
    if (!f) return;
    fseek(f, 0, SEEK_END);
    long size = ftell(f);
    fseek(f, 0, SEEK_SET);
    if (size < 16) { fclose(f); return; }
    u8* rom = new u8[(size_t)size];
    if (fread(rom, 1, (size_t)size, f) != (size_t)size) { fclose(f); delete[] rom; return; }
    fclose(f);
    if (rom[0] != 'N' || rom[1] != 'E' || rom[2] != 'S' || rom[3] != 0x1A) { delete[] rom; return; }
    const int mapperNum = (rom[6] >> 4) | (rom[7] & 0xF0);
    if (mapper) { delete mapper; mapper = nullptr; }
    currentMapperId = (u8)mapperNum;
    switch (mapperNum) {
        case 0: mapper = new Mapper0(rom); break;
        case 4: mapper = new Mapper4(rom); break;
        default: delete[] rom; currentMapperId = 0xFF; return;
    }
    PPU::reset();
    APU::reset();
    CPU::power();
    currentRomPath = fileName;
}

void reset() { if (!currentRomPath.empty()) load(currentRomPath.c_str()); }
bool loaded() { return mapper != nullptr; }
bool check_mapper_irq(int elapsed) { return mapper ? mapper->check_irq(elapsed) : false; }
bool handles_expansion_addr(u16 addr) { return mapper ? mapper->handles_expansion_addr(addr) : false; }
void run_mapper_audio(int elapsed) { if (mapper && mapper->has_audio()) mapper->run_audio(elapsed); }
void end_mapper_audio_frame(int elapsed) { if (mapper && mapper->has_audio()) mapper->end_audio_frame(elapsed); }
std::string get_rom_path() { return currentRomPath; }
u8 get_mapper_id() { return currentMapperId; }
Mapper* get_mapper() { return mapper; }
}
