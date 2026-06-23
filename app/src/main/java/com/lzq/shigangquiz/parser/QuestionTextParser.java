package com.lzq.shigangquiz.parser;

import com.lzq.shigangquiz.model.Option;
import com.lzq.shigangquiz.model.Question;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class QuestionTextParser {
    private static final Pattern BLOCK_START = Pattern.compile("(?m)(?=^\\s*\\d+\\s*[.、．)]\\s*)");
    private static final Pattern LEADING_NUMBER = Pattern.compile("^\\s*\\d+\\s*[.、．)]\\s*");
    private static final Pattern OPTION = Pattern.compile("^\\s*([A-Ha-h])\\s*[.、．:：)）]\\s*(.+)$");
    private static final Pattern ANSWER = Pattern.compile("^\\s*(?:【?答案】?|答案)\\s*[:：]?\\s*(.+)$");
    private static final Pattern EXPLANATION = Pattern.compile("^\\s*(?:【?解析】?|解析)\\s*[:：]?\\s*(.*)$");
    private static final Pattern CATEGORY = Pattern.compile("^\\s*(?:【?分类】?|分类)\\s*[:：]?\\s*(.*)$");

    private QuestionTextParser() {}

    public static ParseResult parse(String source) {
        ParseResult result = new ParseResult();
        String normalized = normalize(source);
        if (normalized.trim().isEmpty()) {
            result.errors.add("没有可解析的文字");
            return result;
        }

        String[] blocks = BLOCK_START.split(normalized);
        if (blocks.length == 0) blocks = new String[]{normalized};
        int blockIndex = 0;
        for (String rawBlock : blocks) {
            String block = rawBlock.trim();
            if (block.isEmpty()) continue;
            blockIndex++;
            try {
                Question question = parseBlock(block, blockIndex);
                if (question != null) result.questions.add(question);
            } catch (IllegalArgumentException error) {
                result.errors.add("第 " + blockIndex + " 个题块：" + error.getMessage());
            }
        }
        return result;
    }

    private static Question parseBlock(String block, int number) {
        String[] lines = block.split("\\n");
        Question question = new Question();
        question.number = number;
        question.bank = "user";
        question.category = "用户题库";
        question.type = "single";
        question.explanation = "";

        StringBuilder stem = new StringBuilder();
        StringBuilder explanation = new StringBuilder();
        OptionBuilder currentOption = null;
        boolean inExplanation = false;
        boolean sawAnswer = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            if (i == 0) line = LEADING_NUMBER.matcher(line).replaceFirst("").trim();

            Matcher categoryMatcher = CATEGORY.matcher(line);
            if (categoryMatcher.matches()) {
                String category = categoryMatcher.group(1).trim();
                if (!category.isEmpty()) question.category = category;
                continue;
            }

            Matcher optionMatcher = OPTION.matcher(line);
            if (optionMatcher.matches() && !sawAnswer && !inExplanation) {
                if (currentOption != null) question.options.add(currentOption.build());
                currentOption = new OptionBuilder(
                        optionMatcher.group(1).toUpperCase(Locale.ROOT),
                        optionMatcher.group(2).trim()
                );
                continue;
            }

            Matcher answerMatcher = ANSWER.matcher(line);
            if (answerMatcher.matches()) {
                if (currentOption != null) {
                    question.options.add(currentOption.build());
                    currentOption = null;
                }
                addAnswers(question, answerMatcher.group(1));
                sawAnswer = true;
                inExplanation = false;
                continue;
            }

            Matcher explanationMatcher = EXPLANATION.matcher(line);
            if (explanationMatcher.matches()) {
                if (currentOption != null) {
                    question.options.add(currentOption.build());
                    currentOption = null;
                }
                String first = explanationMatcher.group(1).trim();
                if (!first.isEmpty()) explanation.append(first);
                inExplanation = true;
                continue;
            }

            if (inExplanation) {
                if (explanation.length() > 0) explanation.append('\n');
                explanation.append(line);
            } else if (currentOption != null) {
                currentOption.append(line);
            } else if (!sawAnswer) {
                if (stem.length() > 0) stem.append('\n');
                stem.append(line);
            }
        }

        if (currentOption != null) question.options.add(currentOption.build());
        question.question = stem.toString().trim();
        question.explanation = explanation.toString().trim();
        question.type = question.answers.size() > 1 ? "multiple" : "single";

        if (question.question.isEmpty()) throw new IllegalArgumentException("缺少题干");
        if (question.options.size() < 2) throw new IllegalArgumentException("没有识别到至少两个选项");
        if (question.answers.isEmpty()) throw new IllegalArgumentException("没有识别到答案");
        return question;
    }

    private static void addAnswers(Question question, String raw) {
        String upper = raw.toUpperCase(Locale.ROOT);
        for (int i = 0; i < upper.length(); i++) {
            char c = upper.charAt(i);
            if (c >= 'A' && c <= 'H') question.answers.add(String.valueOf(c));
        }
    }

    private static String normalize(String source) {
        if (source == null) return "";
        return source.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\u00A0', ' ')
                .replace("Ａ", "A").replace("Ｂ", "B").replace("Ｃ", "C").replace("Ｄ", "D")
                .replace("Ｅ", "E").replace("Ｆ", "F").replace("Ｇ", "G").replace("Ｈ", "H");
    }

    public static final class ParseResult {
        public final List<Question> questions = new ArrayList<>();
        public final List<String> errors = new ArrayList<>();

        public String summary() {
            String text = "识别到 " + questions.size() + " 道题";
            if (!errors.isEmpty()) text += "，另有 " + errors.size() + " 个题块未识别";
            return text;
        }
    }

    private static final class OptionBuilder {
        private final String label;
        private final StringBuilder text;

        OptionBuilder(String label, String text) {
            this.label = label;
            this.text = new StringBuilder(text);
        }

        void append(String line) {
            if (text.length() > 0) text.append(' ');
            text.append(line);
        }

        Option build() {
            return new Option(label, text.toString());
        }
    }
}
