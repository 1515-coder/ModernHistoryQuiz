cmake_minimum_required(VERSION 3.22.1)
project(quizcore LANGUAGES CXX)

add_library(
    quizcore
    SHARED
    native_bridge.cpp
    quiz_engine.cpp
    questions_data.cpp
)

target_compile_features(quizcore PRIVATE cxx_std_17)
target_compile_options(quizcore PRIVATE -Wall -Wextra)
