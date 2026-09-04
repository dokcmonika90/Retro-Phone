cmake_minimum_required(VERSION 3.22.1)
project(retro_phone)

set(LAINES_DIR ${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/LaiNES)

# LaiNES contains the mapper implementations that its core supports.
# Build every mapper source instead of compiling only MMC3/Mapper 4.
file(GLOB LainesMappers CONFIGURE_DEPENDS
    ${LAINES_DIR}/src/mappers/*.cpp
)

add_library(retro_recompiler SHARED
    native.cpp
    gui_stub.cpp
    retro_cartridge.cpp
    retro_apu.cpp
    ${LAINES_DIR}/src/cpu.cpp
    ${LAINES_DIR}/src/ppu.cpp
    ${LAINES_DIR}/src/mapper.cpp
    ${LAINES_DIR}/src/joypad.cpp
    ${LainesMappers}
    ${LAINES_DIR}/lib/Blip_Buffer.cpp
    ${LAINES_DIR}/lib/Multi_Buffer.cpp
    ${LAINES_DIR}/lib/Nes_Apu.cpp
    ${LAINES_DIR}/lib/Nes_Oscs.cpp
    ${LAINES_DIR}/lib/Nonlinear_Buffer.cpp
)

target_compile_features(retro_recompiler PRIVATE cxx_std_20)
target_compile_options(retro_recompiler PRIVATE -O3 -Wno-unused-value)

target_include_directories(retro_recompiler PRIVATE
    ${CMAKE_CURRENT_LIST_DIR}
    ${LAINES_DIR}/src/include
    ${LAINES_DIR}/lib/include
)

find_library(log-lib log)
target_link_libraries(retro_recompiler ${log-lib})
