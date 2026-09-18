package com.lightbox.studio.flash;

import com.lightbox.studio.common.ProcRunner;
import com.lightbox.studio.common.Settings;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 書込処理の共通ロジック。設定の minichlink で対象を書き込む。
 * FlasherMain（③書込のみ）と BuilderMain（②+③）の双方から使う＝
 * ch32fun 同梱 minichlink（環境により init 失敗）ではなく、設定した実績 minichlink を必ず使う。
 */
public final class FlashOp {

    private FlashOp() {}

    /** 1つの minichlink が固まっていると判断するまでの待ち時間。実際の書込は数秒で終わる。 */
    private static final long ATTEMPT_TIMEOUT_MS = 25000;

    /** @return 0=成功。PC内に複数の minichlink があり得るため、候補を順に試して最初の成功を採用。 */
    public static int run(Settings st, PrintStream out) {
        File projectDir = st.projectDir();
        String artifact = st.get(Settings.FLASH_ARTIFACT, "lightbox.bin");
        File art = new File(artifact);
        if (!art.isAbsolute() && projectDir != null) art = new File(projectDir, artifact);

        out.println("=== LightBox 書込 (WCH-LinkE / minichlink) ===");
        out.println("書込対象   : " + art.getAbsolutePath());

        if (!art.isFile()) {
            out.println("[エラー] 書込対象がありません。先に［コンパイル］で lightbox.bin を生成してください。");
            return 2;
        }

        List<String> candidates = orderedMinichlinks(st);
        if (candidates.isEmpty()) {
            out.println("[エラー] minichlink が設定されていません。［⚙設定］で minichlink.path を指定してください。");
            return 2;
        }
        out.println("minichlink 候補 " + candidates.size() + " 件を順に試します（各 " + (ATTEMPT_TIMEOUT_MS / 1000) + "秒でタイムアウト）");

        int lastCode = 3;
        for (int i = 0; i < candidates.size(); i++) {
            String mc = candidates.get(i);
            out.println();
            out.println("── 試行 " + (i + 1) + "/" + candidates.size() + " : " + mc);
            List<String> cmd = new ArrayList<>();
            cmd.add(mc);
            cmd.add("-w");
            cmd.add(art.getAbsolutePath());
            cmd.add("flash");
            cmd.add("-b");
            out.println("> " + ProcRunner.display(cmd));
            out.println("----------------------------------------");
            try {
                int code = ProcRunner.runTimed(cmd, projectDir, null, out::println, ATTEMPT_TIMEOUT_MS);
                out.println("----------------------------------------");
                if (code == 0) {
                    out.println("[成功] 書込完了。基板が起動します。 (minichlink: " + mc + ")");
                    // 成功した minichlink をユーザープロファイルに記録（tools/ を消しても以後最優先で使う）
                    Settings.writeGlobal(Settings.MINICHLINK, mc);
                    return 0;
                } else if (code == ProcRunner.TIMEOUT) {
                    out.println("[タイムアウト] この minichlink は応答しません（書込ハング）。次の候補を試します。");
                } else {
                    out.println("[失敗] 終了コード " + code + "。次の候補を試します。");
                }
                lastCode = code;
            } catch (Exception e) {
                out.println("[例外] 実行できません: " + e.getMessage());
                lastCode = 3;
            }
        }

        out.println();
        out.println("[失敗] すべての minichlink 候補で書込できませんでした。症状別の対処:");
        out.println("  ・'Could not initialize' → WCH-LinkE の WinUSBドライバ未設定。Zadig で");
        out.println("     WCH-Link インターフェースを WinUSB に割当（初回1回）。");
        out.println("  ・'Found WCH Link' 後に停止(ハング) → minichlinkビルドとLinkEファームの相性。");
        out.println("     ［🔧書込ツール選択］で別のビルドを選ぶ（成功したものは自動記憶される）。");
        out.println("  ・'nothing connected' → SWIO(PD1)配線/ターゲット電源/GND共通を確認。");
        return lastCode == ProcRunner.TIMEOUT ? 124 : lastCode;
    }

    /**
     * 試行順の minichlink リスト。順序:
     *  ①ローカル設定 primary → ②過去に成功した実績(ユーザープロファイル) → ③自動検出候補。
     * これにより tools/ を消して再導入(ローカル設定が消滅)しても、実績ビルドが最優先で試される。
     */
    private static List<String> orderedMinichlinks(Settings st) {
        Set<String> ordered = new LinkedHashSet<>();
        String primary = st.get(Settings.MINICHLINK);
        if (!primary.isEmpty()) ordered.add(primary);
        String proven = Settings.readGlobal(Settings.MINICHLINK); // 過去に書込成功した実績
        if (!proven.isEmpty()) ordered.add(proven);
        // ダウンロードした ch32fun 同梱の minichlink（自己完結。PC内にminichlinkが無い初心者向け）
        String ch32fun = st.get(Settings.CH32FUN);
        if (!ch32fun.isEmpty()) {
            File cf = new File(ch32fun);
            if (cf.getParentFile() != null) {
                File bundled = new File(cf.getParentFile(), "minichlink/minichlink.exe");
                if (bundled.isFile()) ordered.add(bundled.getAbsolutePath());
            }
        }
        for (String c : st.get(Settings.MINICHLINK_CANDIDATES).split(";")) {
            c = c.trim();
            if (!c.isEmpty()) ordered.add(c);
        }
        List<String> out = new ArrayList<>();
        for (String c : ordered) {
            if (new File(c).isFile()) out.add(c);
        }
        if (out.isEmpty()) out.add(primary.isEmpty() ? "minichlink" : primary);
        return out;
    }
}
