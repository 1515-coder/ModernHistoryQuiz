#pragma once

#include <string>

namespace quiz {

void initialize(const std::string& storage_directory);
std::string questions_json();
std::string submit(const std::string& question_id, const std::string& selected);
std::string stats_json();
std::string wrong_ids_json();
bool remove_wrong(const std::string& question_id);
bool reset();

}  // namespace quiz
