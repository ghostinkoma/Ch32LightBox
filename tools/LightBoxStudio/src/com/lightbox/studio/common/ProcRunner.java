package com.lightbox.studio.common;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 外部プロセス（make / minichlink 等）を起動し、標準出力＋標準エラーを
 * 1行ずつコールバックへ流す小さなヘルパ。
 *
 * <p>「バックグラウンドJAR」（builder / flasher）はこれで実行結果を
 * 標準出力に流し、メニューGUIがその出力を子プロセス越しに拾う。</p>
 */
public final class ProcRunner {

    /** runTimed がタイムアウトで中断した時の戻り値。 */
    public static final int TIMEOUT = Integer.MIN_VALUE;

    private ProcRunner() {}

    private static void applyExtraPath(ProcessBuilder pb, String extraPath) {
        if (extraPath == null || extraPath.isEmpty()) return;
        java.util.Map<String, String> env = pb.environment();
        String key = "PATH";
        for (String k : env.keySet()) {
            if (k.equalsIgnoreCase("PATH")) { key = k; break; }
        }
        String cur = env.getOrDefault(key, "");
        env.put(key, extraPath + File.pathSeparator + cur);
    }

    /**
     * タイムアウト付き実行。timeoutMs 以内に終わらなければ、プロセス（と子孫）を強制停止し
     * {@link #TIMEOUT} を返す。ハングする minichlink 等を確実に打ち切るために使う。
     */
    public static int runTimed(List<String> command, File workDir, String extraPath,
                               Consumer<String> line, long timeoutMs)
            throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        if (workDir != null) pb.directory(workDir);
        pb.redirectErrorStream(true);
        applyExtraPath(pb, extraPath);

        Process p = pb.start();
        Thread reader = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String s;
                while ((s = br.readLine()) != null) line.accept(s);
            } catch (IOException ignored) {}
        }, "proc-reader");
        reader.setDaemon(true);
        reader.start();

        boolean finished = p.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
        if (!finished) {
            try { p.descendants().forEach(ProcessHandle::destroyForcibly); } catch (Throwable ignored) {}
            p.destroyForcibly();
            reader.join(2000);
            return TIMEOUT;
        }
        reader.join(2000);
        return p.exitValue();
    }

    /**
     * コマンドを実行して終了コードを返す。出力は line で受け取る。
     *
     * @param command  実行コマンド（プログラム＋引数）
     * @param workDir  作業ディレクトリ（null可）
     * @param extraPath PATH の先頭に足すディレクトリ（null/空可, gcc/make の場所など）
     * @param line     1行ごとのコールバック（stdout/stderr混在, 改行なし）
     */
    public static int run(List<String> command, File workDir, String extraPath,
                          Consumer<String> line) throws IOException, InterruptedException {
        return run(command, workDir, extraPath, line, null);
    }

    /**
     * procSink に起動直後の Process を渡す版（呼び出し側が中断できるように）。
     */
    public static int run(List<String> command, File workDir, String extraPath,
                          Consumer<String> line, Consumer<Process> procSink)
            throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        if (workDir != null) pb.directory(workDir);
        pb.redirectErrorStream(true);
        applyExtraPath(pb, extraPath);

        Process p = pb.start();
        if (procSink != null) procSink.accept(p);
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String s;
            while ((s = br.readLine()) != null) {
                line.accept(s);
            }
        }
        return p.waitFor();
    }

    /** コマンドを見やすい1行に整形（ログ表示用）。 */
    public static String display(List<String> command) {
        StringBuilder sb = new StringBuilder();
        for (String c : command) {
            if (sb.length() > 0) sb.append(' ');
            if (c.contains(" ")) sb.append('"').append(c).append('"');
            else sb.append(c);
        }
        return sb.toString();
    }
}
