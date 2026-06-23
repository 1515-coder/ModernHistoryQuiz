package com.lzq.shigangquiz;

public final class NativeQuiz {
    static {
        System.loadLibrary("quizcore");
    }

    private NativeQuiz() {}

    public static native void nativeInit(String storageDirectory);
    public static native String nativeGetQuestionsJson();
    public static native String nativeSubmit(String questionId, String selectedAnswers);
    public static native String nativeGetStatsJson();
    public static native String nativeGetWrongIdsJson();
    public static native boolean nativeRemoveWrong(String questionId);
    public static native boolean nativeReset();
}
