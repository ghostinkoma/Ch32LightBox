package com.lightbox.studio.build;

import com.lightbox.studio.common.ProcRunner;
import com.lightbox.studio.common.Settings;

import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * コンパイル実行（ヘッドレス／バックグラウンドJAR）。
 *
 * <p>settings.properties を読み、{@code make lightbox.bin CH32FUN=<...>} を実行して
 * 出力を標準出力へ流す。メニューGUIが子プロセスとして起動しログを拾う。</p>
 *
 * <p>引数: なし=ビルドのみ / {@code --flash}=ビルド後に書込まで（make のデフォルトターゲット）。</p>
 */
public final class BuilderMain {

    public static void main(String[] args) {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        boolean withFlash = false;
        for (String a : args) if ("--flash".equals(a)) withFlash = true;

        Settings st = Settings.beside(BuilderMain.class);
        File projectDir = st.projectDir();
        String ch32fun = st.get(Settings.CH32FUN);
        String makePath = st.get(Settings.MAKE, "make");
        String gccBin   = st.get(Settings.GCC_BIN);

        out.println("=== LightBox コンパイル ===");
        out.println("project.dir : " + safe(projectDir));
        out.println("CH32FUN     : " + (ch32fun.isEmpty() ? "(未設定: Makefile既定)" : ch32fun));
        out.println("make        : " + makePath);
        out.println("gcc bin     : " + (gccBin.isEmpty() ? "(PATH)" : gccBin));

        if (projectDir == null || !new File(projectDir, "Makefile").isFile()) {
            out.println("[エラー] project.dir に Makefile がありません。メニューの［設定］で source/ を指定してください。");
            System.exit(2);
            return;
        }

        List<String> cmd = new ArrayList<>();
        cmd.add(makePath);
        cmd.add("lightbox.bin");   // 常にビルドのみ（書込は下で設定の minichlink を使う）
        // CH32FUN はフォワードスラッシュで渡す。xpack make は同梱 sh 経由でレシピを実行し、
        // バックスラッシュ入り絶対パスの `>` リダイレクトが sh のエスケープで壊れるため。
        if (!ch32fun.isEmpty()) cmd.add("CH32FUN=" + ch32fun.replace('\\', '/'));

        // gcc の bin と、（make がフルパス指定なら）make の bin を PATH 先頭へ
        String extraPath = gccBin;
        File makeFile = new File(makePath);
        if (makeFile.isAbsolute() && makeFile.getParentFile() != null) {
            String makeDir = makeFile.getParentFile().getAbsolutePath();
            extraPath = extraPath.isEmpty() ? makeDir : (extraPath + File.pathSeparator + makeDir);
        }

        out.println("> " + ProcRunner.display(cmd));
        out.println("----------------------------------------");
        try {
            int code = ProcRunner.run(cmd, projectDir, extraPath, out::println);
            out.println("----------------------------------------");
            File bin = new File(projectDir, "lightbox.bin");
            if (code == 0 && bin.isFile()) {
                out.println("[成功] " + bin.getName() + " (" + bin.length() + " bytes) を生成しました");
            } else if (code == 0) {
                out.println("[完了] 終了コード 0");
            } else {
                out.println("[失敗] 終了コード " + code
                        + " — ツールチェーン(riscv-none-elf-gcc/make)や CH32FUN パスを確認してください");
                System.exit(code);
                return;
            }
            // --flash: ビルド成功後、設定した minichlink で書込（ch32fun同梱minichlinkは使わない）
            if (withFlash) {
                out.println();
                int fcode = com.lightbox.studio.flash.FlashOp.run(st, out);
                System.exit(fcode);
                return;
            }
            System.exit(code);
        } catch (Exception e) {
            out.println("[例外] 実行できません: " + e.getMessage());
            out.println("       make が見つからない場合は［設定］で make のパスを指定してください。");
            System.exit(3);
        }
    }

    private static String safe(File f) { return f == null ? "(null)" : f.getAbsolutePath(); }
}
