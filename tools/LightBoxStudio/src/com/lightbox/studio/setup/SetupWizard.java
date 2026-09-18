package com.lightbox.studio.setup;

import com.lightbox.studio.common.Downloader;
import com.lightbox.studio.common.Settings;
import com.lightbox.studio.common.Zip;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 初回セットアップ・ウィザード（プログレスバー付き）。
 * ツールチェーンを toolchain/ へダウンロード＆展開し、settings.properties を自動記入する。
 * 「インストール済みパスを探す」手間をユーザーに掛けず、自己完結させる。
 */
public final class SetupWizard extends JFrame {

    private final File appDir = Settings.jarDir(SetupWizard.class);
    private final Settings settings = Settings.beside(SetupWizard.class);
    private final Manifest manifest = Manifest.load(appDir);
    private final File toolchainRoot = new File(appDir, "toolchain");

    private final File runtimeRoot = new File(appDir, "runtime");
    private String minichlinkCandidates = ""; // ';'区切りの代替minichlinkパス
    private final JProgressBar overall = new JProgressBar(0, 100);
    private final JProgressBar current = new JProgressBar(0, 100);
    private final JCheckBox jreCheck = new JCheckBox(
            "ポータブルJRE も取得（システム非依存にする・runtime/ にのみ展開＝サンドボックス, 約44MB）");
    private final JLabel step = new JLabel("［開始］を押すとツールチェーンの取得を始めます。");
    private final JTextArea log = new JTextArea();
    private JScrollPane logScroll;
    private final JButton detailBtn = new JButton("詳細 ▼");
    private final JButton start = new JButton("開始");
    private final JButton close = new JButton("閉じる");

    private static final int H_COMPACT = 250;
    private static final int H_EXPANDED = 520;

    public SetupWizard() {
        super("LightBox Studio — 初回セットアップ");
        buildUi();
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(680, H_COMPACT);   // 既定はプログレスのみ（ログは詳細トグルで表示）
        setLocationRelativeTo(null);
    }

    private void buildUi() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel top = new JPanel(new GridLayout(0, 1, 0, 4));
        JLabel title = new JLabel("ツールチェーンを取得します（約360MBのGCCを含むため時間がかかります）");
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        top.add(title);
        top.add(step);
        jreCheck.setSelected(true); // サンドボックス方針: 既定でポータブルJREも取得
        top.add(jreCheck);
        top.add(labeled("全体", overall));
        top.add(labeled("現在", current));
        overall.setStringPainted(true);
        current.setStringPainted(true);
        root.add(top, BorderLayout.NORTH);

        log.setEditable(false);
        log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        logScroll = new JScrollPane(log);
        logScroll.setVisible(false); // 既定はログ非表示（詳細トグルで表示）
        root.add(logScroll, BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout());
        detailBtn.addActionListener(e -> toggleDetail());
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        left.add(detailBtn);
        south.add(left, BorderLayout.WEST);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        start.addActionListener(e -> onStart());
        close.addActionListener(e -> dispose());
        right.add(start); right.add(close);
        south.add(right, BorderLayout.EAST);
        root.add(south, BorderLayout.SOUTH);

        setContentPane(root);
    }

    /** ログの表示/非表示をトグルし、ウィンドウ高さも合わせて変える。 */
    private void toggleDetail() {
        boolean show = !logScroll.isVisible();
        logScroll.setVisible(show);
        detailBtn.setText(show ? "詳細 ▲" : "詳細 ▼");
        setSize(getWidth(), show ? H_EXPANDED : H_COMPACT);
        revalidate();
        repaint();
    }

    private JPanel labeled(String text, JComponent c) {
        JPanel p = new JPanel(new BorderLayout(6, 0));
        JLabel l = new JLabel(text); l.setPreferredSize(new Dimension(40, 20));
        p.add(l, BorderLayout.WEST); p.add(c, BorderLayout.CENTER);
        return p;
    }

    private void onStart() {
        start.setEnabled(false);
        close.setEnabled(false);
        new SwingWorker<Boolean, String>() {
            @Override protected Boolean doInBackground() {
                try { runSetup(); return true; }
                catch (Exception ex) { logln("[失敗] " + ex.getMessage()); return false; }
            }
            @Override protected void done() {
                start.setEnabled(true);
                close.setEnabled(true);
                boolean ok;
                try { ok = get(); } catch (Exception e) { ok = false; }
                if (ok) {
                    setStep("完了しました。このウィンドウを閉じてメニューから ①②③ を実行できます。");
                    JOptionPane.showMessageDialog(SetupWizard.this,
                            "セットアップ完了。\n設定は自動保存されました。", "完了", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    setStep("エラーで中断しました。ログを確認してください。");
                }
            }
        }.execute();
    }

    // ---- 本体 ----

    private void runSetup() throws IOException {
        Files.createDirectories(toolchainRoot.toPath());
        settings.load(); // 既存設定（実績minichlink等）を反映してから判断
        List<Manifest.Comp> comps = manifest.components();
        boolean wantJre = jreCheck.isSelected();
        int n = comps.size();
        int steps = n + (wantJre ? 1 : 0);

        String gccBin = "", makeExe = "", ch32funDir = "", minichlinkExe = "";

        for (int i = 0; i < n; i++) {
            Manifest.Comp c = comps.get(i);
            final int idx = i;
            final int stepsF = steps;
            setStep("(" + (i + 1) + "/" + steps + ") " + c.label);
            setOverall(idx, steps, 0);

            if ("minichlink".equals(c.name) && c.url.isEmpty()) {
                // ch32fun 同梱の prebuilt minichlink（既にダウンロード済み＝self-contained）を最優先で使う
                File bundled = null;
                if (!ch32funDir.isEmpty()) {
                    File cf = new File(ch32funDir);
                    if (cf.getParentFile() != null)
                        bundled = new File(cf.getParentFile(), "minichlink/minichlink.exe");
                }
                minichlinkExe = provisionMinichlink(settings.get(Settings.MINICHLINK), bundled);
                setOverall(idx, steps, 1);
                continue;
            }

            // 既に展開済みなら再ダウンロードしない（再セットアップを高速化）
            File existing = resolveExisting(c);
            if (existing != null) {
                logln(c.label + ": 既存を使用（ダウンロード省略）: " + existing.getAbsolutePath());
                switch (c.name) {
                    case "gcc":     gccBin = existing.getAbsolutePath(); break;
                    case "make":    makeExe = new File(existing, manifest.makeExe()).getAbsolutePath(); break;
                    case "ch32fun": ch32funDir = existing.getAbsolutePath(); break;
                    default: break;
                }
                setOverall(idx, steps, 1);
                continue;
            }

            // ダウンロード
            Path zip = new File(toolchainRoot, c.name + ".zip").toPath();
            logln("DL: " + c.url);
            Downloader.download(c.url, zip, (read, tbytes) -> {
                int pct = tbytes > 0 ? (int) (read * 100 / tbytes) : -1;
                setCurrent(pct, "ダウンロード " + human(read) + (tbytes > 0 ? " / " + human(tbytes) : ""));
                setOverall(idx, stepsF, tbytes > 0 ? (read / (double) tbytes) * 0.5 : 0.0);
            });

            // 展開
            logln("展開: " + zip.getFileName());
            String top = Zip.extract(zip, toolchainRoot.toPath(), (done, tot, name) -> {
                int pct = tot > 0 ? (done * 100 / tot) : -1;
                setCurrent(pct, "展開 " + done + "/" + tot);
                setOverall(idx, stepsF, 0.5 + (tot > 0 ? (done / (double) tot) * 0.5 : 0.0));
            });
            Files.deleteIfExists(zip);

            File base = new File(toolchainRoot, top == null ? "" : top);
            File target = c.tail.isEmpty() ? base : new File(base, c.tail);
            logln("  -> " + target.getAbsolutePath());
            switch (c.name) {
                case "gcc":     gccBin = target.getAbsolutePath(); break;
                case "make":    makeExe = new File(target, manifest.makeExe()).getAbsolutePath(); break;
                case "ch32fun": ch32funDir = target.getAbsolutePath(); break;
                default: break;
            }
            setOverall(idx, steps, 1);
        }

        // 任意: ポータブルJRE（runtime/ にのみ展開。システムへは一切インストールしない）
        String javaHome = "";
        if (wantJre) {
            javaHome = provisionJre(n, steps);
        }

        // 設定を保存
        setStep("設定を保存中…");
        settings.load();
        if (!gccBin.isEmpty())        settings.set(Settings.GCC_BIN, gccBin);
        if (!makeExe.isEmpty())       settings.set(Settings.MAKE, makeExe);
        if (!ch32funDir.isEmpty())    settings.set(Settings.CH32FUN, ch32funDir);
        if (!minichlinkExe.isEmpty()) settings.set(Settings.MINICHLINK, minichlinkExe);
        settings.set(Settings.MINICHLINK_CANDIDATES, minichlinkCandidates);
        File pj = Settings.autodetectProjectDir();
        if (pj != null && settings.get(Settings.PROJECT_DIR).isEmpty())
            settings.set(Settings.PROJECT_DIR, pj.getAbsolutePath());
        settings.save();
        logln("[保存] " + settings.file().getAbsolutePath());
        logln("  gcc.bin.dir   = " + gccBin);
        logln("  make.path     = " + makeExe);
        logln("  ch32fun.dir   = " + ch32funDir);
        logln("  minichlink    = " + (minichlinkExe.isEmpty() ? "(未取得: 書込は別途minichlinkが必要)" : minichlinkExe));
        if (!javaHome.isEmpty()) logln("  runtime(JRE)  = " + javaHome + "  ← 起動スクリプトが自動使用（システム非依存）");
        setOverall(steps, steps, 0);
        setCurrent(100, "完了");
    }

    /** ポータブルJRE を runtime/ へ展開（システムへは非インストール＝サンドボックス）。 */
    private String provisionJre(int idx, int steps) throws IOException {
        final int stepsF = steps;
        setStep("(" + steps + "/" + steps + ") ポータブルJRE（runtime/ に展開・サンドボックス）");
        Files.createDirectories(runtimeRoot.toPath());
        String url = manifest.jreUrl();
        Path zip = new File(runtimeRoot, "jre.zip").toPath();
        logln("DL(JRE): " + url);
        Downloader.download(url, zip, (read, tbytes) -> {
            int pct = tbytes > 0 ? (int) (read * 100 / tbytes) : -1;
            setCurrent(pct, "ダウンロード " + human(read) + (tbytes > 0 ? " / " + human(tbytes) : ""));
            setOverall(idx, stepsF, tbytes > 0 ? (read / (double) tbytes) * 0.5 : 0.0);
        });
        String top = Zip.extract(zip, runtimeRoot.toPath(), (done, tot, name) -> {
            int pct = tot > 0 ? (done * 100 / tot) : -1;
            setCurrent(pct, "展開 " + done + "/" + tot);
            setOverall(idx, stepsF, 0.5 + (tot > 0 ? (done / (double) tot) * 0.5 : 0.0));
        });
        Files.deleteIfExists(zip);
        File javaw = new File(new File(new File(runtimeRoot, top == null ? "" : top), "bin"), "javaw.exe");
        logln("  JRE -> " + (top == null ? runtimeRoot : new File(runtimeRoot, top)).getAbsolutePath());
        return javaw.getParentFile().getParentFile().getAbsolutePath();
    }

    /**
     * minichlink を調達。ソースビルドは不要（ch32fun が Windows版 prebuilt minichlink.exe +
     * libusb-1.0.dll を同梱）。優先順:
     *   ①既存ローカル設定 → ②過去の成功実績(global) → ③ch32fun同梱(self-contained) → ④PC内検出。
     * 候補は minichlinkCandidates（';'区切り）に格納。
     * @return 主として使う minichlink パス（無ければ ""）
     */
    private String provisionMinichlink(String existing, File bundled) {
        setCurrent(-1, "書込ツール(minichlink)を準備中…");
        // 自己完結（サンドボックス）: PC全体を走査しない。
        //   ①既存ローカル設定 → ②成功実績(記憶) → ③ch32fun同梱(dist内) のみ。
        java.util.LinkedHashSet<String> cand = new java.util.LinkedHashSet<>();
        if (existing != null && !existing.isEmpty() && new File(existing).isFile()) {
            cand.add(existing);
            logln("  minichlink: 既存の設定を最優先に維持: " + existing);
        }
        String proven = Settings.readGlobal(Settings.MINICHLINK);
        if (!proven.isEmpty() && new File(proven).isFile()) {
            cand.add(proven);
            logln("  minichlink: 過去の成功実績を優先（記憶）: " + proven);
        }
        if (bundled != null && bundled.isFile()) {
            cand.add(bundled.getAbsolutePath());
            logln("  minichlink: ch32fun 同梱の prebuilt を使用（self-contained・ソースビルド不要）: "
                    + bundled.getAbsolutePath());
        } else {
            logln("  minichlink: ch32fun 同梱 minichlink が見つかりません（ch32fun展開を確認）");
        }
        if (cand.isEmpty())
            logln("  minichlink: 未確定。メニューの［🔧書込ツール選択］で動く minichlink.exe を指定してください。");

        minichlinkCandidates = String.join(";", cand);
        return cand.isEmpty() ? "" : cand.iterator().next();
    }

    /** toolchain/ 内に既に展開済みの対象があれば返す（再DL省略用）。無ければ null。 */
    private File resolveExisting(Manifest.Comp c) {
        File[] subs = toolchainRoot.listFiles(File::isDirectory);
        if (subs == null) return null;
        for (File d : subs) {
            File target = c.tail.isEmpty() ? d : new File(d, c.tail);
            if (isValidTarget(c.name, target)) return target;
        }
        return null;
    }

    private boolean isValidTarget(String name, File target) {
        switch (name) {
            case "gcc":
                return new File(target, "riscv-none-elf-gcc.exe").isFile()
                    || new File(target, "riscv-none-elf-gcc").isFile();
            case "make":
                return new File(target, manifest.makeExe()).isFile();
            case "ch32fun":
                return new File(target, "ch32fun.mk").isFile();
            default:
                return false;
        }
    }

    // ---- UI 更新（スレッド安全） ----

    private void setStep(String s) { SwingUtilities.invokeLater(() -> step.setText(s)); }
    private void logln(String s) {
        SwingUtilities.invokeLater(() -> { log.append(s + "\n"); log.setCaretPosition(log.getDocument().getLength()); });
    }
    private void setCurrent(int pct, String text) {
        SwingUtilities.invokeLater(() -> {
            if (pct < 0) { current.setIndeterminate(true); current.setString(text); }
            else { current.setIndeterminate(false); current.setValue(pct); current.setString(text + "  " + pct + "%"); }
        });
    }
    private void setOverall(int compIndex, int compCount, double subFraction) {
        int pct = (int) ((compIndex + Math.max(0, Math.min(1, subFraction))) / compCount * 100);
        SwingUtilities.invokeLater(() -> { overall.setValue(pct); overall.setString(pct + "%"); });
    }

    private static String human(long b) {
        if (b < 1024) return b + "B";
        if (b < 1024 * 1024) return String.format("%.1fKB", b / 1024.0);
        return String.format("%.1fMB", b / (1024.0 * 1024.0));
    }

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new SetupWizard().setVisible(true));
    }
}
