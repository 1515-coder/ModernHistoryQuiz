#include <jni.h>

#include <string>

#include "quiz_engine.h"

namespace {

std::string from_jstring(JNIEnv* env, jstring value) {
    if (value == nullptr) return {};
    const char* chars = env->GetStringUTFChars(value, nullptr);
    std::string result = chars == nullptr ? "" : chars;
    if (chars != nullptr) env->ReleaseStringUTFChars(value, chars);
    return result;
}

jstring to_jstring(JNIEnv* env, const std::string& value) {
    return env->NewStringUTF(value.c_str());
}

}  // namespace

extern "C" JNIEXPORT void JNICALL
Java_com_lzq_shigangquiz_NativeQuiz_nativeInit(
        JNIEnv* env, jclass, jstring storage_directory) {
    quiz::initialize(from_jstring(env, storage_directory));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_lzq_shigangquiz_NativeQuiz_nativeGetQuestionsJson(
        JNIEnv* env, jclass) {
    return to_jstring(env, quiz::questions_json());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_lzq_shigangquiz_NativeQuiz_nativeSubmit(
        JNIEnv* env, jclass, jstring question_id, jstring selected_answers) {
    return to_jstring(env, quiz::submit(
            from_jstring(env, question_id),
            from_jstring(env, selected_answers)
    ));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_lzq_shigangquiz_NativeQuiz_nativeGetStatsJson(
        JNIEnv* env, jclass) {
    return to_jstring(env, quiz::stats_json());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_lzq_shigangquiz_NativeQuiz_nativeGetWrongIdsJson(
        JNIEnv* env, jclass) {
    return to_jstring(env, quiz::wrong_ids_json());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_lzq_shigangquiz_NativeQuiz_nativeRemoveWrong(
        JNIEnv* env, jclass, jstring question_id) {
    return quiz::remove_wrong(from_jstring(env, question_id)) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_lzq_shigangquiz_NativeQuiz_nativeReset(
        JNIEnv*, jclass) {
    return quiz::reset() ? JNI_TRUE : JNI_FALSE;
}
