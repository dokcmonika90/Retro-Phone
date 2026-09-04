cmake_minimum_required(VERSION 3.22.1)
project(retro_recompiler)

add_library(retro_recompiler SHARED native.cpp)
target_compile_features(retro_recompiler PRIVATE cxx_std_20)
target_compile_options(retro_recompiler PRIVATE -O3 -ffast-math)
find_library(log-lib log)
target_link_libraries(retro_recompiler ${log-lib})
