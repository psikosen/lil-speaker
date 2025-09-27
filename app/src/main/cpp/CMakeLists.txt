cmake_minimum_required(VERSION 3.22.1)
project(lilspeaker_llama)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

include(FetchContent)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SHARED_LIB ON CACHE BOOL "" FORCE)
set(LLAMA_K_QUANTS ON CACHE BOOL "" FORCE)

FetchContent_Declare(
    llama_cpp
    GIT_REPOSITORY https://github.com/ggerganov/llama.cpp.git
    GIT_TAG master
)

FetchContent_MakeAvailable(llama_cpp)

add_library(lilspeaker-llama SHARED llama_bridge.cpp)

target_include_directories(lilspeaker-llama PRIVATE
    ${llama_cpp_SOURCE_DIR}
    ${llama_cpp_SOURCE_DIR}/include
)

target_link_libraries(lilspeaker-llama PRIVATE llama)

target_compile_definitions(lilspeaker-llama PRIVATE GGML_USE_K_QUANTS)
