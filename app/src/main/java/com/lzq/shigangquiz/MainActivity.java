package com.lzq.shigangquiz;

import android.graphics.Rect;
import android.os.Build;
import com.lzq.shigangquiz.ui.SwipeDrawerLayout;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.lzq.shigangquiz.data.FavoriteStore;
import com.lzq.shigangquiz.data.QuestionRepository;
import com.lzq.shigangquiz.data.SessionState;
import com.lzq.shigangquiz.data.SessionStore;
import com.lzq.shigangquiz.data.StudyStateStore;
import com.lzq.shigangquiz.model.Option;
import com.lzq.shigangquiz.model.Question;

import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

public final class MainActivity extends AppCompatActivity {
    private final List<Question> allQuestions = new ArrayList<>();
    private final List<Question> sessionQuestions = new ArrayList<>();
    private final Set<String> selectedAnswers = new TreeSet<>();
    private final List<CompoundButton> optionViews = new ArrayList<>();
    private final Handler stateHandler = new Handler(Looper.getMainLooper());

    private QuestionRepository questionRepository;
    private FavoriteStore favoriteStore;
    private StudyStateStore studyStateStore;
    private SessionStore sessionStore;

    private DrawerLayout drawerLayout;
    private LinearLayout bankOptions;
    private LinearLayout modeOptions;
    private LinearLayout categoryOptions;
    private LinearLayout typeOptions;
    private LinearLayout toolOptions;
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
    private MaterialButton favoriteButton;

    private String bankFilter = QuestionRepository.BANK_ALL;
    private String modeFilter = "sequence";
    private String categoryFilter = "全部专题";
    private String typeFilter = "all";
    private int currentIndex;
    private boolean submitted;
    private boolean internalSelectionChange;

    private final Runnable saveStateRunnable = this::flushSessionStateQuietly;

    private final ActivityResultLauncher<Intent> manageQuestionsLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    loadData(false);
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);

        questionRepository = new QuestionRepository(this);
        favoriteStore = new FavoriteStore(this);
        studyStateStore = new StudyStateStore(this);
        sessionStore = new SessionStore(this);

        bindViews();
        configureSystemInsets();
        configureActions();
        configureExpandableSections();
        loadData(true);
        stateHandler.postDelayed(saveStateRunnable, 800L);
    }

    private void bindViews() {
        drawerLayout = findViewById(R.id.drawerLayout);
        bankOptions = findViewById(R.id.bankOptions);
        modeOptions = findViewById(R.id.modeOptions);
        categoryOptions = findViewById(R.id.categoryOptions);
        typeOptions = findViewById(R.id.typeOptions);
        toolOptions = findViewById(R.id.toolOptions);
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
        favoriteButton = findViewById(R.id.favoriteButton);
    }

    private void configureSystemInsets() {
        View topBar = findViewById(R.id.topBar);
        View bottomBar = findViewById(R.id.bottomBar);
        View drawerPanel = findViewById(R.id.drawerPanel);

        final int topLeft = topBar.getPaddingLeft();
        final int topTop = topBar.getPaddingTop();
        final int topRight = topBar.getPaddingRight();
        final int topBottom = topBar.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(topBar, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.statusBars()
                            | WindowInsetsCompat.Type.displayCutout()
            );
            view.setPadding(topLeft, topTop + insets.top, topRight, topBottom);
            return windowInsets;
        });

        final int bottomLeft = bottomBar.getPaddingLeft();
        final int bottomTop = bottomBar.getPaddingTop();
        final int bottomRight = bottomBar.getPaddingRight();
        final int bottomBottom = bottomBar.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(bottomBar, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars());
            view.setPadding(bottomLeft, bottomTop, bottomRight, bottomBottom + insets.bottom);
            return windowInsets;
        });

        final int drawerLeft = drawerPanel.getPaddingLeft();
        final int drawerTop = drawerPanel.getPaddingTop();
        final int drawerRight = drawerPanel.getPaddingRight();
        final int drawerBottom = drawerPanel.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(drawerPanel, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout()
            );
            view.setPadding(
                    drawerLeft,
                    drawerTop + insets.top,
                    drawerRight,
                    drawerBottom + insets.bottom
            );
            return windowInsets;
        });

        ViewCompat.requestApplyInsets(drawerLayout);
    }

    private void configureActions() {
        drawerLayout.setDrawerLockMode(
                DrawerLayout.LOCK_MODE_UNLOCKED,
                GravityCompat.START
        );
        findViewById(R.id.menuButton).setOnClickListener(v ->
                drawerLayout.openDrawer(GravityCompat.START));
        findViewById(R.id.closeDrawerButton).setOnClickListener(v ->
                drawerLayout.closeDrawer(GravityCompat.START));
        findViewById(R.id.manageQuestionsButton).setOnClickListener(v -> {
            drawerLayout.closeDrawer(GravityCompat.START);
            manageQuestionsLauncher.launch(new Intent(this, QuestionImportActivity.class));
        });
        findViewById(R.id.resetButton).setOnClickListener(v -> confirmReset());

        previousButton.setOnClickListener(v -> moveQuestion(-1));
        nextButton.setOnClickListener(v -> moveQuestion(1));
        submitButton.setOnClickListener(v -> submitCurrentAnswer());
        masteredButton.setOnClickListener(v -> markCurrentAsMastered());
        favoriteButton.setOnClickListener(v -> toggleFavorite());
    }

    private void configureExpandableSections() {
        setupExpandable(R.id.bankHeader, bankOptions, true);
        setupExpandable(R.id.modeHeader, modeOptions, true);
        setupExpandable(R.id.categoryHeader, categoryOptions, false);
        setupExpandable(R.id.typeHeader, typeOptions, false);
        setupExpandable(R.id.toolHeader, toolOptions, true);
    }

    private void setupExpandable(int headerId, LinearLayout content, boolean expanded) {
        MaterialButton header = findViewById(headerId);
        content.setVisibility(expanded ? View.VISIBLE : View.GONE);
        updateHeaderArrow(header, expanded);
        header.setOnClickListener(v -> {
            boolean show = content.getVisibility() != View.VISIBLE;
            content.setVisibility(show ? View.VISIBLE : View.GONE);
            updateHeaderArrow(header, show);
        });
    }

    private void updateHeaderArrow(MaterialButton header, boolean expanded) {
        Object title = header.getTag();
        String base = title == null ? header.getText().toString().replace(" ▾", "").replace(" ▸", "") : title.toString();
        header.setTag(base);
        header.setText(base + (expanded ? " ▾" : " ▸"));
    }

    private void loadData(boolean restoreSession) {
        try {
            questionRepository.load();
            favoriteStore.load();
            studyStateStore.load();
            allQuestions.clear();
            allQuestions.addAll(questionRepository.getAllQuestions());

            SessionState restored = null;
            if (restoreSession) {
                restored = sessionStore.load();
                bankFilter = restored.bank;
                modeFilter = restored.mode;
                categoryFilter = restored.category;
                typeFilter = restored.type;
            }

            ensureValidFilters();
            rebuildSidebarChoices();
            rebuildSession(restored);
            refreshStats();
        } catch (Exception error) {
            showFatalError(error);
        }
    }

    private void ensureValidFilters() {
        if (!QuestionRepository.BANK_ALL.equals(bankFilter)
                && !QuestionRepository.BANK_BUILTIN.equals(bankFilter)
                && !QuestionRepository.BANK_USER.equals(bankFilter)
                && !QuestionRepository.BANK_FAVORITES.equals(bankFilter)) {
            bankFilter = QuestionRepository.BANK_ALL;
        }
        if (!"sequence".equals(modeFilter) && !"random".equals(modeFilter) && !"wrong".equals(modeFilter)) {
            modeFilter = "sequence";
        }
        if (!"all".equals(typeFilter) && !"single".equals(typeFilter) && !"multiple".equals(typeFilter)) {
            typeFilter = "all";
        }
        Set<String> categories = availableCategories();
        if (!"全部专题".equals(categoryFilter) && !categories.contains(categoryFilter)) {
            categoryFilter = "全部专题";
        }
    }

    private void rebuildSidebarChoices() {
        populateChoices(bankOptions, new Choice[]{
                new Choice(QuestionRepository.BANK_ALL, "全部题库"),
                new Choice(QuestionRepository.BANK_BUILTIN, "内置题库"),
                new Choice(QuestionRepository.BANK_USER, "用户题库"),
                new Choice(QuestionRepository.BANK_FAVORITES, "收藏题目")
        }, bankFilter, key -> {
            bankFilter = key;
            categoryFilter = "全部专题";
            ensureValidFilters();
            rebuildSidebarChoices();
            rebuildSession(null);
            drawerLayout.closeDrawer(GravityCompat.START);
        });

        populateChoices(modeOptions, new Choice[]{
                new Choice("sequence", "顺序练习"),
                new Choice("random", "随机练习"),
                new Choice("wrong", "错题练习")
        }, modeFilter, key -> {
            modeFilter = key;
            rebuildSidebarChoices();
            rebuildSession(null);
            drawerLayout.closeDrawer(GravityCompat.START);
        });

        List<Choice> categoryChoices = new ArrayList<>();
        categoryChoices.add(new Choice("全部专题", "全部专题"));
        for (String category : availableCategories()) categoryChoices.add(new Choice(category, category));
        populateChoices(categoryOptions, categoryChoices.toArray(new Choice[0]), categoryFilter, key -> {
            categoryFilter = key;
            rebuildSidebarChoices();
            rebuildSession(null);
            drawerLayout.closeDrawer(GravityCompat.START);
        });

        populateChoices(typeOptions, new Choice[]{
                new Choice("all", "全部题型"),
                new Choice("single", "单选题"),
                new Choice("multiple", "多选题")
        }, typeFilter, key -> {
            typeFilter = key;
            rebuildSidebarChoices();
            rebuildSession(null);
            drawerLayout.closeDrawer(GravityCompat.START);
        });

    }

    private void populateChoices(LinearLayout container, Choice[] choices, String selectedKey, ChoiceListener listener) {
        container.removeAllViews();
        for (Choice choice : choices) {
            MaterialButton button = new MaterialButton(this, null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle);
            button.setText(choice.label);
            button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            button.setCheckable(true);
            button.setChecked(choice.key.equals(selectedKey));
            button.setCornerRadius(dp(12));
            button.setInsetTop(0);
            button.setInsetBottom(0);
            button.setMinHeight(44);
            button.setOnClickListener(v -> listener.onSelected(choice.key));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(44)
            );
            params.topMargin = dp(5);
            button.setLayoutParams(params);
            container.addView(button);
        }
    }

    private Set<String> availableCategories() {
        Set<String> result = new LinkedHashSet<>();
        Set<String> favorites = favoriteStore == null ? Collections.emptySet() : favoriteStore.snapshot();
        for (Question question : allQuestions) {
            if (QuestionRepository.BANK_BUILTIN.equals(bankFilter)
                    && !QuestionRepository.BANK_BUILTIN.equals(question.bank)) continue;
            if (QuestionRepository.BANK_USER.equals(bankFilter)
                    && !QuestionRepository.BANK_USER.equals(question.bank)) continue;
            if (QuestionRepository.BANK_FAVORITES.equals(bankFilter)
                    && !favorites.contains(question.id)) continue;
            result.add(question.category);
        }
        return result;
    }

    private void rebuildSession(SessionState restored) {
        sessionQuestions.clear();
        Set<String> wrongIds = studyStateStore.getWrongIds();
        Set<String> favoriteIds = favoriteStore.snapshot();

        for (Question question : allQuestions) {
            if (QuestionRepository.BANK_BUILTIN.equals(bankFilter)
                    && !QuestionRepository.BANK_BUILTIN.equals(question.bank)) continue;
            if (QuestionRepository.BANK_USER.equals(bankFilter)
                    && !QuestionRepository.BANK_USER.equals(question.bank)) continue;
            if (QuestionRepository.BANK_FAVORITES.equals(bankFilter)
                    && !favoriteIds.contains(question.id)) continue;
            if (!"全部专题".equals(categoryFilter) && !categoryFilter.equals(question.category)) continue;
            if ("single".equals(typeFilter) && !"single".equals(question.type)) continue;
            if ("multiple".equals(typeFilter) && !"multiple".equals(question.type)) continue;
            if ("wrong".equals(modeFilter) && !wrongIds.contains(question.id)) continue;
            sessionQuestions.add(question);
        }

        if ("random".equals(modeFilter)) {
            Collections.shuffle(sessionQuestions);
        } else {
            sessionQuestions.sort(Comparator
                    .comparing((Question q) -> QuestionRepository.BANK_BUILTIN.equals(q.bank) ? 0 : 1)
                    .thenComparingInt(q -> q.number));
        }

        currentIndex = 0;
        if (restored != null && !sessionQuestions.isEmpty()) {
            int byId = findQuestionIndex(restored.questionId);
            currentIndex = byId >= 0 ? byId : Math.min(restored.index, sessionQuestions.size() - 1);
        }

        if (sessionQuestions.isEmpty()) showEmptyState();
        else renderCurrentQuestion();
        scheduleSessionSave();
    }

    private int findQuestionIndex(String id) {
        if (id == null || id.isEmpty()) return -1;
        for (int i = 0; i < sessionQuestions.size(); i++) {
            if (id.equals(sessionQuestions.get(i).id)) return i;
        }
        return -1;
    }

    private void showEmptyState() {
        submitted = false;
        selectedAnswers.clear();
        progressText.setText("0 / 0");
        typeBadge.setText("暂无题目");
        questionText.setText(emptyStateMessage());
        optionsContainer.removeAllViews();
        resultCard.setVisibility(View.GONE);
        masteredButton.setVisibility(View.GONE);
        favoriteButton.setEnabled(false);
        favoriteButton.setText("☆ 收藏");
        submitButton.setEnabled(false);
        previousButton.setEnabled(false);
        nextButton.setEnabled(false);
    }

    private String emptyStateMessage() {
        if (QuestionRepository.BANK_USER.equals(bankFilter)) return "用户题库还没有题目。请从左侧菜单进入“题库导入与编辑”。";
        if (QuestionRepository.BANK_FAVORITES.equals(bankFilter)) return "收藏库为空。可在刷题时点击右上角的收藏按钮。";
        if ("wrong".equals(modeFilter)) return "当前筛选条件下没有错题。";
        return "当前筛选条件下没有题目。";
    }

    private Question currentQuestion() {
        if (sessionQuestions.isEmpty() || currentIndex < 0 || currentIndex >= sessionQuestions.size()) return null;
        return sessionQuestions.get(currentIndex);
    }

    private void renderCurrentQuestion() {
        Question question = currentQuestion();
        if (question == null) {
            showEmptyState();
            return;
        }

        submitted = false;
        selectedAnswers.clear();
        optionViews.clear();
        resultCard.setVisibility(View.GONE);
        optionsContainer.removeAllViews();

        String bankName = QuestionRepository.BANK_USER.equals(question.bank) ? "用户" : "内置";
        progressText.setText(String.format(
                Locale.getDefault(),
                "%d / %d · %s第 %d 题",
                currentIndex + 1,
                sessionQuestions.size(),
                bankName,
                question.number
        ));
        typeBadge.setText("multiple".equals(question.type) ? "多选题" : "单选题");
        questionText.setText(question.question);
        favoriteButton.setEnabled(true);
        updateFavoriteButton(question);

        for (Option option : question.options) {
            CompoundButton button = "multiple".equals(question.type)
                    ? new MaterialCheckBox(this)
                    : new MaterialRadioButton(this);
            button.setText(option.label + ".  " + option.text);
            button.setTag(option.label);
            button.setTextSize(16f);
            button.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            button.setGravity(Gravity.CENTER_VERTICAL);
            button.setPadding(dp(14), dp(13), dp(14), dp(13));
            button.setBackgroundResource(R.drawable.option_background);
            button.setButtonTintList(ContextCompat.getColorStateList(this, R.color.option_tint));

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            params.bottomMargin = dp(10);
            button.setLayoutParams(params);
            button.setOnCheckedChangeListener((view, checked) -> handleSelection(question, view, checked));
            optionViews.add(button);
            optionsContainer.addView(button);
        }

        submitButton.setText("提交答案");
        submitButton.setEnabled(false);
        previousButton.setEnabled(true);
        nextButton.setEnabled(true);
        masteredButton.setVisibility(studyStateStore.getWrongIds().contains(question.id) ? View.VISIBLE : View.GONE);
    }

    private void handleSelection(Question question, CompoundButton changed, boolean checked) {
        if (submitted || internalSelectionChange) return;
        String label = String.valueOf(changed.getTag());
        if ("single".equals(question.type)) {
            if (checked) {
                internalSelectionChange = true;
                for (CompoundButton other : optionViews) {
                    if (other != changed) other.setChecked(false);
                }
                internalSelectionChange = false;
                selectedAnswers.clear();
                selectedAnswers.add(label);
            } else {
                selectedAnswers.remove(label);
            }
        } else {
            if (checked) selectedAnswers.add(label);
            else selectedAnswers.remove(label);
        }
        submitButton.setEnabled(!selectedAnswers.isEmpty());
    }

    private void submitCurrentAnswer() {
        Question question = currentQuestion();
        if (question == null || submitted) return;
        if (selectedAnswers.isEmpty()) {
            Toast.makeText(this, "请先选择答案", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean correct = new TreeSet<>(question.answers).equals(new TreeSet<>(selectedAnswers));
        String selected = joinAnswers(selectedAnswers);
        String answer = question.answerText();
        try {
            studyStateStore.recordAnswer(question.id, correct);
            submitted = true;
            submitButton.setEnabled(false);
            submitButton.setText("已提交");
            showResult(correct, selected, answer, question.explanation);
            highlightOptions(selectedAnswers, question.answers);
            refreshStats();
            masteredButton.setVisibility(studyStateStore.getWrongIds().contains(question.id)
                    ? View.VISIBLE : View.GONE);
        } catch (Exception error) {
            Toast.makeText(this, "学习记录写入失败：" + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showResult(boolean correct, String selected, String answer, String explanation) {
        resultCard.setVisibility(View.VISIBLE);
        resultTitle.setText(correct ? "回答正确" : "回答错误");
        resultTitle.setTextColor(ContextCompat.getColor(this, correct ? R.color.success : R.color.danger));
        answerText.setText("你的答案：" + selected + "    正确答案：" + answer);
        explanationText.setText(explanation == null || explanation.trim().isEmpty() ? "暂无解析。" : explanation);
    }

    private void highlightOptions(Set<String> selected, Set<String> correct) {
        for (CompoundButton button : optionViews) {
            String label = String.valueOf(button.getTag());
            button.setEnabled(false);
            if (correct.contains(label)) {
                button.setTextColor(ContextCompat.getColor(this, R.color.success));
                button.setBackgroundResource(R.drawable.option_correct_background);
            } else if (selected.contains(label)) {
                button.setTextColor(ContextCompat.getColor(this, R.color.danger));
                button.setBackgroundResource(R.drawable.option_wrong_background);
            }
        }
    }

    private void moveQuestion(int delta) {
        if (sessionQuestions.isEmpty()) return;
        currentIndex = (currentIndex + delta + sessionQuestions.size()) % sessionQuestions.size();
        renderCurrentQuestion();
        scheduleSessionSave();
    }

    private void toggleFavorite() {
        Question question = currentQuestion();
        if (question == null) return;
        try {
            boolean favorite = favoriteStore.toggle(question.id);
            updateFavoriteButton(question);
            Toast.makeText(this, favorite ? "已加入收藏" : "已取消收藏", Toast.LENGTH_SHORT).show();
            rebuildSidebarChoices();
            if (QuestionRepository.BANK_FAVORITES.equals(bankFilter) && !favorite) {
                rebuildSession(null);
            }
        } catch (Exception error) {
            Toast.makeText(this, "收藏写入失败：" + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void updateFavoriteButton(Question question) {
        favoriteButton.setText(favoriteStore.contains(question.id) ? "★ 已收藏" : "☆ 收藏");
    }

    private void markCurrentAsMastered() {
        Question question = currentQuestion();
        if (question == null) return;
        try {
            studyStateStore.removeWrong(question.id);
            refreshStats();
            masteredButton.setVisibility(View.GONE);
            Toast.makeText(this, "已移出错题库", Toast.LENGTH_SHORT).show();
            if ("wrong".equals(modeFilter)) rebuildSession(null);
        } catch (Exception error) {
            Toast.makeText(this, "错题记录写入失败：" + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void confirmReset() {
        drawerLayout.closeDrawer(GravityCompat.START);
        new AlertDialog.Builder(this)
                .setTitle("清空学习记录")
                .setMessage("将清空答题次数、正确率和错题记录。用户题库与收藏不会被删除。")
                .setNegativeButton("取消", null)
                .setPositiveButton("清空", (dialog, which) -> {
                    try {
                        studyStateStore.reset();
                        refreshStats();
                        rebuildSession(null);
                        Toast.makeText(this, "学习记录已清空", Toast.LENGTH_SHORT).show();
                    } catch (Exception error) {
                        Toast.makeText(this, "清空失败：" + error.getMessage(), Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }

    private void refreshStats() {
        answeredStat.setText(String.valueOf(studyStateStore.getAnswered()));
        accuracyStat.setText(String.format(Locale.getDefault(), "%.1f%%", studyStateStore.getAccuracy()));
        wrongStat.setText(String.valueOf(studyStateStore.getWrongCount()));
    }

    private void scheduleSessionSave() {
        stateHandler.removeCallbacks(saveStateRunnable);
        stateHandler.postDelayed(saveStateRunnable, 500L);
    }

    private void flushSessionStateQuietly() {
        try {
            SessionState state = new SessionState();
            state.bank = bankFilter;
            state.mode = modeFilter;
            state.category = categoryFilter;
            state.type = typeFilter;
            state.index = currentIndex;
            Question question = currentQuestion();
            state.questionId = question == null ? "" : question.id;
            sessionStore.save(state);
        } catch (Exception ignored) {
            // Position persistence is best effort; a later navigation or onPause will retry.
        }
    }

    @Override
    protected void onPause() {
        stateHandler.removeCallbacks(saveStateRunnable);
        flushSessionStateQuietly();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        stateHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    private String bankLabel() {
        if (QuestionRepository.BANK_BUILTIN.equals(bankFilter)) return "内置题库";
        if (QuestionRepository.BANK_USER.equals(bankFilter)) return "用户题库";
        if (QuestionRepository.BANK_FAVORITES.equals(bankFilter)) return "收藏题目";
        return "全部题库";
    }

    private String modeLabel() {
        if ("random".equals(modeFilter)) return "随机练习";
        if ("wrong".equals(modeFilter)) return "错题练习";
        return "顺序练习";
    }

    private String typeLabel() {
        if ("single".equals(typeFilter)) return "单选题";
        if ("multiple".equals(typeFilter)) return "多选题";
        return "全部题型";
    }

    private static String joinAnswers(Set<String> answers) {
        StringBuilder builder = new StringBuilder();
        for (String answer : new TreeSet<>(answers)) builder.append(answer);
        return builder.toString();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void showFatalError(Exception error) {
        new AlertDialog.Builder(this)
                .setTitle("题库加载失败")
                .setMessage(error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage())
                .setCancelable(false)
                .setPositiveButton("退出", (dialog, which) -> finish())
                .show();
    }

    private interface ChoiceListener {
        void onSelected(String key);
    }

    private static final class Choice {
        final String key;
        final String label;

        Choice(String key, String label) {
            this.key = key;
            this.label = label;
        }
    }
}
