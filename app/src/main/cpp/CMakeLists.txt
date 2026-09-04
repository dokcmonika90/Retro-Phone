cmake_minimum_required(VERSION 3.22.1)
project(retro_phone)
add_library(retro_core SHARED native.cpp)
target_compile_features(retro_core PRIVATE cxx_std_20)
