package com.lzq.shigangquiz;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.radiobutton.MaterialRadioButton;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

public class MainActivity extends AppCompatActivity {
    private final List<Question> allQuestions = new ArrayList<>();
    private final List<Question> sessionQuestions = new ArrayList<>();
    private final Set<String> selectedAnswers = new TreeSet<>();
    private final Set<String> wrongIds = new HashSet<>();
    private final List<CompoundButton> optionViews = new ArrayList<>();

    private Spinner modeSpinner;
    private Spinner categorySpinner;
    private Spinner typeSpinner;
    private TextView answeredStat;
    private TextView accuracyStat;
    private TextView wrongStat;
    private TextView progressText;
    private TextView typeBadge;
    private TextView questionText;
    private LinearLayout optionsContainer;
    private MaterialCardView resultCard;
    private TextView resultTitle;
    private TextView answerText;
    private TextView explanationText;
    private MaterialButton previousButton;
    private MaterialButton submitButton;
    private MaterialButton nextButton;
    private MaterialButton masteredButton;
    private MaterialButton resetButton;

    private int currentIndex = 0;
    private boolean submitted = false;
    private boolean spinnerReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindViews();
        NativeQuiz.nativeInit(getFilesDir().getAbsolutePath());

        try {
            loadQuestionsFromNative();
            configureFilters();
            configureActions();
            refreshWrongIds();
            rebuildSession();
            refreshStats();
        } catch (Exception error) {
            showFatalError(error);
        }
    }

    private void bindViews() {
        modeSpinner = findViewById(R.id.modeSpinner);
        categorySpinner = findViewById(R.id.categorySpinner);
        typeSpinner = findViewById(R.id.typeSpinner);
        answeredStat = findViewById(R.id.answeredStat);
        accuracyStat = findViewById(R.id.accuracyStat);
        wrongStat = findViewById(R.id.wrongStat);
        progressText = findViewById(R.id.progressText);
        typeBadge = findViewById(R.id.typeBadge);
        questionText = findViewById(R.id.questionText);
        optionsContainer = findViewById(R.id.optionsContainer);
        resultCard = findViewById(R.id.resultCard);
        resultTitle = findViewById(R.id.resultTitle);
        answerText = findViewById(R.id.answerText);
        explanationText = findViewById(R.id.explanationText);
        previousButton = findViewById(R.id.previousButton);
        submitButton = findViewById(R.id.submitButton);
        nextButton = findViewById(R.id.nextButton);
        masteredButton = findViewById(R.id.masteredButton);
        resetButton = findViewById(R.id.resetButton);
    }

    private void loadQuestionsFromNative() throws JSONException {
        JSONObject bank = new JSONObject(NativeQuiz.nativeGetQuestionsJson());
        JSONArray array = bank.getJSONArray("questions");
        allQuestions.clear();

        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.getJSONObject(i);
            Question q = new Question();
            q.id = item.getString("id");
            q.number = item.optInt("number", i + 1);
            q.type = item.getString("type");
            q.category = item.optString("category", "未分类");
            q.question = item.getString("question");

            JSONArray options = item.getJSONArray("options");
            for (int j = 0; j < options.length(); j++) {
                JSONObject optionObject = options.getJSONObject(j);
                q.options.add(new Option(
                        optionObject.getString("label"),
                        optionObject.getString("text")
                ));
            }
            allQuestions.add(q);
        }
    }

    private void configureFilters() {
        ArrayAdapter<String> modeAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                Arrays.asList("顺序练习", "随机练习", "错题练习")
        );
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        modeSpinner.setAdapter(modeAdapter);

        Set<String> categories = new LinkedHashSet<>();
        for (Question q : allQuestions) categories.add(q.category);
        List<String> categoryItems = new ArrayList<>();
        categoryItems.add("全部专题");
        categoryItems.addAll(categories);
        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                categoryItems
        );
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(categoryAdapter);

        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                Arrays.asList("全部题型", "单选题", "多选题")
        );
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        typeSpinner.setAdapter(typeAdapter);

        AdapterView.OnItemSelectedListener listener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (spinnerReady) rebuildSession();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        };
        modeSpinner.setOnItemSelectedListener(listener);
        categorySpinner.setOnItemSelectedListener(listener);
        typeSpinner.setOnItemSelectedListener(listener);
        spinnerReady = true;
    }

    private void configureActions() {
        previousButton.setOnClickListener(v -> {
            if (sessionQuestions.isEmpty()) return;
            currentIndex = (currentIndex - 1 + sessionQuestions.size()) % sessionQuestions.size();
            renderCurrentQuestion();
        });

        nextButton.setOnClickListener(v -> {
            if (sessionQuestions.isEmpty()) return;
            currentIndex = (currentIndex + 1) % sessionQuestions.size();
            renderCurrentQuestion();
        });

        submitButton.setOnClickListener(v -> submitCurrentAnswer());
        masteredButton.setOnClickListener(v -> markCurrentAsMastered());
        resetButton.setOnClickListener(v -> confirmReset());
    }

    private void rebuildSession() {
        refreshWrongIds();
        sessionQuestions.clear();

        String mode = String.valueOf(modeSpinner.getSelectedItem());
        String category = String.valueOf(categorySpinner.getSelectedItem());
        String type = String.valueOf(typeSpinner.getSelectedItem());

        for (Question q : allQuestions) {
            if (!"全部专题".equals(category) && !category.equals(q.category)) continue;
            if ("单选题".equals(type) && !"single".equals(q.type)) continue;
            if ("多选题".equals(type) && !"multiple".equals(q.type)) continue;
            if ("错题练习".equals(mode) && !wrongIds.contains(q.id)) continue;
            sessionQuestions.add(q);
        }

        if ("随机练习".equals(mode)) Collections.shuffle(sessionQuestions);
        else sessionQuestions.sort(Comparator.comparingInt(item -> item.number));

        currentIndex = 0;
        if (sessionQuestions.isEmpty()) {
            showEmptyState();
        } else {
            renderCurrentQuestion();
        }
    }

    private void showEmptyState() {
        submitted = false;
        selectedAnswers.clear();
        progressText.setText("0 / 0");
        typeBadge.setText("暂无题目");
        questionText.setText("当前筛选条件下没有题目。错题练习为空时，先在普通模式中答几道题即可。");
        optionsContainer.removeAllViews();
        resultCard.setVisibility(View.GONE);
        masteredButton.setVisibility(View.GONE);
        submitButton.setEnabled(false);
        previousButton.setEnabled(false);
        nextButton.setEnabled(false);
    }

    private Question currentQuestion() {
        if (sessionQuestions.isEmpty()) return null;
        return sessionQuestions.get(currentIndex);
    }

    private void renderCurrentQuestion() {
        Question q = currentQuestion();
        if (q == null) {
            showEmptyState();
            return;
        }

        submitted = false;
        selectedAnswers.clear();
        optionViews.clear();
        resultCard.setVisibility(View.GONE);
        optionsContainer.removeAllViews();

        progressText.setText(String.format(
                Locale.getDefault(),
                "%d / %d · 第 %d 题",
                currentIndex + 1,
                sessionQuestions.size(),
                q.number
        ));
        typeBadge.setText("multiple".equals(q.type) ? "多选题" : "单选题");
        questionText.setText(q.question);

        for (Option option : q.options) {
            CompoundButton button;
            if ("multiple".equals(q.type)) {
                button = new MaterialCheckBox(this);
            } else {
                button = new MaterialRadioButton(this);
            }

            button.setText(option.label + ".  " + option.text);
            button.setTag(option.label);
            button.setTextSize(16f);
            button.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            button.setGravity(android.view.Gravity.CENTER_VERTICAL);
            button.setPadding(dp(14), dp(13), dp(14), dp(13));
            button.setBackgroundResource(R.drawable.option_background);
            button.setButtonTintList(ContextCompat.getColorStateList(this, R.color.option_tint));

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            params.bottomMargin = dp(10);
            button.setLayoutParams(params);

            button.setOnCheckedChangeListener((compoundButton, checked) -> {
                if (submitted) return;
                String label = String.valueOf(compoundButton.getTag());

                if ("single".equals(q.type) && checked) {
                    for (CompoundButton other : optionViews) {
                        if (other != compoundButton) {
                            other.setOnCheckedChangeListener(null);
                            other.setChecked(false);
                            attachSelectionListener(other, q);
                        }
                    }
                    selectedAnswers.clear();
                    selectedAnswers.add(label);
                } else if ("multiple".equals(q.type)) {
                    if (checked) selectedAnswers.add(label);
                    else selectedAnswers.remove(label);
                }
                submitButton.setEnabled(!selectedAnswers.isEmpty());
            });

            optionViews.add(button);
            optionsContainer.addView(button);
        }

        submitButton.setText("提交答案");
        submitButton.setEnabled(false);
        previousButton.setEnabled(true);
        nextButton.setEnabled(true);
        masteredButton.setVisibility(wrongIds.contains(q.id) ? View.VISIBLE : View.GONE);
    }

    private void attachSelectionListener(CompoundButton button, Question q) {
        button.setOnCheckedChangeListener((compoundButton, checked) -> {
            if (submitted) return;
            String label = String.valueOf(compoundButton.getTag());
            if ("single".equals(q.type) && checked) {
                for (CompoundButton other : optionViews) {
                    if (other != compoundButton) {
                        other.setOnCheckedChangeListener(null);
                        other.setChecked(false);
                        attachSelectionListener(other, q);
                    }
                }
                selectedAnswers.clear();
                selectedAnswers.add(label);
            } else if ("multiple".equals(q.type)) {
                if (checked) selectedAnswers.add(label);
                else selectedAnswers.remove(label);
            }
            submitButton.setEnabled(!selectedAnswers.isEmpty());
        });
    }

    private void submitCurrentAnswer() {
        Question q = currentQuestion();
        if (q == null || submitted) return;
        if (selectedAnswers.isEmpty()) {
            Toast.makeText(this, "请先选择答案", Toast.LENGTH_SHORT).show();
            return;
        }

        submitButton.setEnabled(false);
        submitButton.setText("提交中…");

        try {
            StringBuilder selectedBuilder = new StringBuilder();
            for (String label : selectedAnswers) selectedBuilder.append(label);
            String selected = selectedBuilder.toString();
            JSONObject result = new JSONObject(NativeQuiz.nativeSubmit(q.id, selected));
            boolean correct = result.getBoolean("correct");
            String answer = result.getString("answer");
            String explanation = result.optString("explanation", "暂无解析。");

            submitted = true;
            submitButton.setText("已提交");
            showResult(correct, selected, answer, explanation);
            highlightOptions(selected, answer);
            refreshWrongIds();
            refreshStats();
            masteredButton.setVisibility(wrongIds.contains(q.id) ? View.VISIBLE : View.GONE);
        } catch (JSONException error) {
            submitButton.setEnabled(true);
            submitButton.setText("提交答案");
            Toast.makeText(this, "提交失败：" + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showResult(boolean correct, String selected, String answer, String explanation) {
        resultCard.setVisibility(View.VISIBLE);
        resultTitle.setText(correct ? "回答正确" : "回答错误");
        resultTitle.setTextColor(ContextCompat.getColor(
                this,
                correct ? R.color.success : R.color.danger
        ));
        answerText.setText("你的答案：" + selected + "    正确答案：" + answer);
        explanationText.setText(explanation == null || explanation.trim().isEmpty() ? "暂无解析。" : explanation);
    }

    private void highlightOptions(String selected, String answer) {
        Set<Character> correctSet = new HashSet<>();
        for (char c : answer.toCharArray()) correctSet.add(c);
        Set<Character> selectedSet = new HashSet<>();
        for (char c : selected.toCharArray()) selectedSet.add(c);

        for (CompoundButton button : optionViews) {
            String labelString = String.valueOf(button.getTag());
            char label = labelString.charAt(0);
            button.setEnabled(false);
            if (correctSet.contains(label)) {
                button.setTextColor(ContextCompat.getColor(this, R.color.success));
                button.setBackgroundResource(R.drawable.option_correct_background);
            } else if (selectedSet.contains(label)) {
                button.setTextColor(ContextCompat.getColor(this, R.color.danger));
                button.setBackgroundResource(R.drawable.option_wrong_background);
            }
        }
    }

    private void refreshStats() {
        try {
            JSONObject stats = new JSONObject(NativeQuiz.nativeGetStatsJson());
            answeredStat.setText(String.valueOf(stats.optInt("answered", 0)));
            accuracyStat.setText(String.format(
                    Locale.getDefault(),
                    "%.1f%%",
                    stats.optDouble("accuracy", 0.0)
            ));
            wrongStat.setText(String.valueOf(stats.optInt("active_wrong", 0)));
        } catch (JSONException error) {
            Toast.makeText(this, "统计读取失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void refreshWrongIds() {
        wrongIds.clear();
        try {
            JSONObject data = new JSONObject(NativeQuiz.nativeGetWrongIdsJson());
            JSONArray ids = data.getJSONArray("ids");
            for (int i = 0; i < ids.length(); i++) wrongIds.add(ids.getString(i));
        } catch (JSONException error) {
            Toast.makeText(this, "错题库读取失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void markCurrentAsMastered() {
        Question q = currentQuestion();
        if (q == null) return;
        if (NativeQuiz.nativeRemoveWrong(q.id)) {
            refreshWrongIds();
            refreshStats();
            masteredButton.setVisibility(View.GONE);
            Toast.makeText(this, "已移出错题库", Toast.LENGTH_SHORT).show();
            if ("错题练习".equals(String.valueOf(modeSpinner.getSelectedItem()))) {
                rebuildSession();
            }
        }
    }

    private void confirmReset() {
        new AlertDialog.Builder(this)
                .setTitle("清空学习记录")
                .setMessage("将清空答题统计和错题库，但不会删除内置题库。")
                .setNegativeButton("取消", null)
                .setPositiveButton("清空", (dialog, which) -> {
                    NativeQuiz.nativeReset();
                    refreshWrongIds();
                    refreshStats();
                    rebuildSession();
                    Toast.makeText(this, "学习记录已清空", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void showFatalError(Exception error) {
        new AlertDialog.Builder(this)
                .setTitle("题库加载失败")
                .setMessage(error.getMessage())
                .setCancelable(false)
                .setPositiveButton("退出", (dialog, which) -> finish())
                .show();
    }

    private static final class Option {
        final String label;
        final String text;

        Option(String label, String text) {
            this.label = label;
            this.text = text;
        }
    }

    private static final class Question {
        String id;
        int number;
        String type;
        String category;
        String question;
        final List<Option> options = new ArrayList<>();
    }
}
