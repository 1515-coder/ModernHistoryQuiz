package com.lzq.shigangquiz;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.lzq.shigangquiz.data.QuestionRepository;
import com.lzq.shigangquiz.parser.QuestionTextParser;

import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class QuestionImportActivity extends AppCompatActivity {
    private QuestionRepository repository;
    private TextInputEditText inputText;
    private TextView statusText;

    private final ActivityResultLauncher<String[]> openJsonLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            this::importJsonUri
    );

    private final ActivityResultLauncher<String> exportJsonLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/json"),
            this::exportJsonUri
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_question_import);
        configureSystemInsets();

        repository = new QuestionRepository(this);
        inputText = findViewById(R.id.importInput);
        statusText = findViewById(R.id.importStatus);

        findViewById(R.id.importBackButton).setOnClickListener(v -> finish());
        findViewById(R.id.parseTextButton).setOnClickListener(v -> parseText());
        findViewById(R.id.pasteJsonButton).setOnClickListener(v -> importPastedJson());
        findViewById(R.id.openJsonButton).setOnClickListener(v ->
                openJsonLauncher.launch(new String[]{"application/json", "text/plain"}));
        findViewById(R.id.exportJsonButton).setOnClickListener(v ->
                exportJsonLauncher.launch("modern_history_user_questions.json"));
        findViewById(R.id.clearUserBankButton).setOnClickListener(v -> confirmClear());

        try {
            repository.load();
            updateStatus("用户题库现有 " + repository.getUserQuestions().size() + " 道题");
        } catch (Exception error) {
            updateStatus("题库加载失败：" + error.getMessage());
            disableEditing();
        }
    }

    private void configureSystemInsets() {
        View root = findViewById(R.id.importRoot);
        final int left = root.getPaddingLeft();
        final int top = root.getPaddingTop();
        final int right = root.getPaddingRight();
        final int bottom = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout()
            );
            view.setPadding(
                    left + insets.left,
                    top + insets.top,
                    right + insets.right,
                    bottom + insets.bottom
            );
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    private void parseText() {
        String source = textValue();
        QuestionTextParser.ParseResult parsed = QuestionTextParser.parse(source);
        if (parsed.questions.isEmpty()) {
            String message = parsed.errors.isEmpty() ? "没有识别到题目" : parsed.errors.get(0);
            updateStatus(message);
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("确认加入用户题库")
                .setMessage(parsed.summary() + "\n\n第一题：\n" + parsed.questions.get(0).question)
                .setNegativeButton("取消", null)
                .setPositiveButton("加入", (dialog, which) -> {
                    try {
                        QuestionRepository.ImportResult result = repository.addQuestions(parsed.questions);
                        updateStatus(result.summary());
                        setResult(Activity.RESULT_OK);
                    } catch (Exception error) {
                        showError("写入失败", error);
                    }
                })
                .show();
    }

    private void importPastedJson() {
        try {
            QuestionRepository.ImportResult result = repository.importJson(textValue());
            updateStatus(result.summary());
            setResult(Activity.RESULT_OK);
        } catch (Exception error) {
            showError("JSON 导入失败", error);
        }
    }

    private void importJsonUri(Uri uri) {
        if (uri == null) return;
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IOException("无法读取所选文件");
            String text = readAll(input);
            QuestionRepository.ImportResult result = repository.importJson(text);
            updateStatus(result.summary());
            setResult(Activity.RESULT_OK);
        } catch (Exception error) {
            showError("JSON 文件导入失败", error);
        }
    }

    private void exportJsonUri(Uri uri) {
        if (uri == null) return;
        try (OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
            if (output == null) throw new IOException("无法创建导出文件");
            output.write(repository.exportUserBank().getBytes(StandardCharsets.UTF_8));
            output.flush();
            updateStatus("用户题库已导出");
        } catch (Exception error) {
            showError("导出失败", error);
        }
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle("清空用户题库")
                .setMessage("将删除应用内的全部用户题目。建议先导出 JSON 备份。")
                .setNegativeButton("取消", null)
                .setPositiveButton("清空", (dialog, which) -> {
                    try {
                        repository.clearUserBank();
                        updateStatus("用户题库已清空");
                        setResult(Activity.RESULT_OK);
                    } catch (Exception error) {
                        showError("清空失败", error);
                    }
                })
                .show();
    }

    private String textValue() {
        return inputText.getText() == null ? "" : inputText.getText().toString();
    }

    private void updateStatus(String message) {
        statusText.setText(message);
    }

    private void disableEditing() {
        int[] ids = {
                R.id.parseTextButton,
                R.id.pasteJsonButton,
                R.id.openJsonButton,
                R.id.exportJsonButton,
                R.id.clearUserBankButton
        };
        for (int id : ids) findViewById(id).setEnabled(false);
    }

    private void showError(String title, Exception error) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage())
                .setPositiveButton("确定", null)
                .show();
    }

    private static String readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        return output.toString(StandardCharsets.UTF_8.name());
    }
}
