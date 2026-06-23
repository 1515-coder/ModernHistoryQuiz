#pragma once

#include <cstddef>

struct EmbeddedQuestion {
    const char* id;
    const char* answer;
    const char* explanation;
    const char* public_json;
};

extern const EmbeddedQuestion kQuestions[];
extern const std::size_t kQuestionCount;
