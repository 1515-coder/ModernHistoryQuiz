#include "quiz_engine.h"

#include "questions_data.h"

#include <algorithm>
#include <cctype>
#include <filesystem>
#include <fstream>
#include <map>
#include <mutex>
#include <regex>
#include <sstream>
#include <string>
#include <vector>

namespace quiz {
namespace {

struct ProgressRecord {
    int attempts = 0;
    int correct = 0;
    int wrong = 0;
    bool active_wrong = false;
};

std::map<std::string, ProgressRecord> g_records;
std::filesystem::path g_state_path;
std::mutex g_mutex;

std::string escape_json(const std::string& value) {
    std::ostringstream out;
    for (unsigned char ch : value) {
        switch (ch) {
            case '"': out << "\\\""; break;
            case '\\': out << "\\\\"; break;
            case '\b': out << "\\b"; break;
            case '\f': out << "\\f"; break;
            case '\n': out << "\\n"; break;
            case '\r': out << "\\r"; break;
            case '\t': out << "\\t"; break;
            default:
                if (ch < 0x20) {
                    const char* hex = "0123456789abcdef";
                    out << "\\u00" << hex[(ch >> 4) & 0xF] << hex[ch & 0xF];
                } else {
                    out << static_cast<char>(ch);
                }
        }
    }
    return out.str();
}

std::string normalize_answer(std::string value) {
    std::string result;
    for (unsigned char ch : value) {
        char upper = static_cast<char>(std::toupper(ch));
        if (upper >= 'A' && upper <= 'F') result.push_back(upper);
    }
    std::sort(result.begin(), result.end());
    result.erase(std::unique(result.begin(), result.end()), result.end());
    return result;
}

const EmbeddedQuestion* find_question(const std::string& id) {
    for (std::size_t i = 0; i < kQuestionCount; ++i) {
        if (id == kQuestions[i].id) return &kQuestions[i];
    }
    return nullptr;
}

void save_state_locked() {
    if (g_state_path.empty()) return;
    std::filesystem::create_directories(g_state_path.parent_path());
    const auto temp = g_state_path.string() + ".tmp";
    std::ofstream output(temp, std::ios::binary | std::ios::trunc);
    output << "{\"version\":1,\"records\":[";
    bool first = true;
    for (const auto& [id, record] : g_records) {
        if (!first) output << ',';
        first = false;
        output << "{\"id\":\"" << escape_json(id)
               << "\",\"attempts\":" << record.attempts
               << ",\"correct\":" << record.correct
               << ",\"wrong\":" << record.wrong
               << ",\"active_wrong\":" << (record.active_wrong ? "true" : "false")
               << '}';
    }
    output << "]}";
    output.close();
    std::error_code error;
    std::filesystem::rename(temp, g_state_path, error);
    if (error) {
        std::filesystem::remove(g_state_path, error);
        std::filesystem::rename(temp, g_state_path, error);
    }
}

void load_state_locked() {
    g_records.clear();
    if (g_state_path.empty() || !std::filesystem::exists(g_state_path)) return;

    std::ifstream input(g_state_path, std::ios::binary);
    std::stringstream buffer;
    buffer << input.rdbuf();
    const std::string data = buffer.str();

    const std::regex pattern(
        R"REGEX(\{"id":"([^"]+)","attempts":([0-9]+),"correct":([0-9]+),"wrong":([0-9]+),"active_wrong":(true|false)\})REGEX"
    );
    for (auto it = std::sregex_iterator(data.begin(), data.end(), pattern);
         it != std::sregex_iterator(); ++it) {
        ProgressRecord record;
        record.attempts = std::stoi((*it)[2].str());
        record.correct = std::stoi((*it)[3].str());
        record.wrong = std::stoi((*it)[4].str());
        record.active_wrong = (*it)[5].str() == "true";
        g_records[(*it)[1].str()] = record;
    }
}

}  // namespace

void initialize(const std::string& storage_directory) {
    std::lock_guard<std::mutex> lock(g_mutex);
    g_state_path = std::filesystem::path(storage_directory) / "quiz_state.json";
    load_state_locked();
}

std::string questions_json() {
    std::ostringstream output;
    output << "{\"count\":" << kQuestionCount << ",\"questions\":[";
    for (std::size_t i = 0; i < kQuestionCount; ++i) {
        if (i > 0) output << ',';
        output << kQuestions[i].public_json;
    }
    output << "]}";
    return output.str();
}

std::string submit(const std::string& question_id, const std::string& selected) {
    std::lock_guard<std::mutex> lock(g_mutex);
    const EmbeddedQuestion* question = find_question(question_id);
    if (question == nullptr) {
        return "{\"ok\":false,\"error\":\"题目不存在\"}";
    }

    const std::string normalized_selected = normalize_answer(selected);
    const std::string correct_answer = normalize_answer(question->answer);
    const bool correct = !normalized_selected.empty() && normalized_selected == correct_answer;

    auto& record = g_records[question_id];
    record.attempts += 1;
    if (correct) {
        record.correct += 1;
    } else {
        record.wrong += 1;
        record.active_wrong = true;
    }
    save_state_locked();

    std::ostringstream output;
    output << "{\"ok\":true,\"correct\":" << (correct ? "true" : "false")
           << ",\"selected\":\"" << escape_json(normalized_selected)
           << "\",\"answer\":\"" << escape_json(correct_answer)
           << "\",\"explanation\":\"" << escape_json(question->explanation)
           << "\"}";
    return output.str();
}

std::string stats_json() {
    std::lock_guard<std::mutex> lock(g_mutex);
    int attempts = 0;
    int correct = 0;
    int active_wrong = 0;
    for (const auto& [id, record] : g_records) {
        (void)id;
        attempts += record.attempts;
        correct += record.correct;
        if (record.active_wrong) active_wrong += 1;
    }
    const double accuracy = attempts == 0 ? 0.0 : static_cast<double>(correct) * 100.0 / attempts;
    std::ostringstream output;
    output.setf(std::ios::fixed);
    output.precision(1);
    output << "{\"answered\":" << g_records.size()
           << ",\"attempts\":" << attempts
           << ",\"correct\":" << correct
           << ",\"accuracy\":" << accuracy
           << ",\"active_wrong\":" << active_wrong << '}';
    return output.str();
}

std::string wrong_ids_json() {
    std::lock_guard<std::mutex> lock(g_mutex);
    std::ostringstream output;
    output << "{\"ids\":[";
    bool first = true;
    for (const auto& [id, record] : g_records) {
        if (!record.active_wrong) continue;
        if (!first) output << ',';
        first = false;
        output << '"' << escape_json(id) << '"';
    }
    output << "]}";
    return output.str();
}

bool remove_wrong(const std::string& question_id) {
    std::lock_guard<std::mutex> lock(g_mutex);
    auto it = g_records.find(question_id);
    if (it == g_records.end()) return false;
    it->second.active_wrong = false;
    save_state_locked();
    return true;
}

bool reset() {
    std::lock_guard<std::mutex> lock(g_mutex);
    g_records.clear();
    save_state_locked();
    return true;
}

}  // namespace quiz
