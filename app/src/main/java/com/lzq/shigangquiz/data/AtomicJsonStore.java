package com.lzq.shigangquiz.data;

import android.content.Context;
import android.util.AtomicFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class AtomicJsonStore {
    private final Context context;

    public AtomicJsonStore(Context context) {
        this.context = context.getApplicationContext();
    }

    public String readAsset(String name) throws IOException {
        try (InputStream input = context.getAssets().open(name)) {
            return readAll(input);
        }
    }

    public String readFile(String name, String fallback) throws IOException {
        File file = new File(context.getFilesDir(), name);
        if (!file.exists()) return fallback;
        try (InputStream input = new FileInputStream(file)) {
            return readAll(input);
        }
    }

    public void writeFile(String name, String content) throws IOException {
        File file = new File(context.getFilesDir(), name);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.exists()) {
            throw new IOException("无法创建数据目录：" + parent.getAbsolutePath());
        }

        AtomicFile atomicFile = new AtomicFile(file);
        FileOutputStream output = null;
        try {
            output = atomicFile.startWrite();
            output.write(content.getBytes(StandardCharsets.UTF_8));
            output.flush();
            atomicFile.finishWrite(output);
        } catch (IOException error) {
            if (output != null) atomicFile.failWrite(output);
            throw error;
        }
    }

    private static String readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        return output.toString(StandardCharsets.UTF_8.name());
    }
}
