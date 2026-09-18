package com.lightbox.studio.menu;

import com.lightbox.studio.common.ProcRunner;
import com.lightbox.studio.common.Settings;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * LightBox Studio メニュー（ランチャ＝キッカー）。
 *
 * <p>1本のGUIから config.h 編集・コンパイル・書込の各JARを子プロセスとして起動し、
 * その出力を下部ログへ集約表示する。設定（パス類）は settings.properties で共有。</p>
 */
public final class MenuApp extends JFrame {

    private final Settings settings = Settings.beside(MenuApp.class);
    private final File appDir = Settings.jarDir(MenuApp.class);

    private final JTextArea log = new JTextArea();
    private final JLabel status = new JLabel();
    private final List<JButton> actionButtons = new ArrayList<>();
    private final JButton cancelButton = new JButton("■ 中断");
    private volatile Process currentProc;

    public MenuApp() {
        super("LightBox Studio");
        buildUi();
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(760, 560);
        setLocationRelativeTo(null);
        refreshStatus();
        // 初回（未セットアップ）は起動時にセットアップ画面を自動で開く
        if (!isSetupDone()) SwingUtilities.invokeLater(this::launchSetup);
    }

    /** ツールチェーンが揃っているか（gcc.bin.dir が実在 かつ toolchain/ がある）。 */
    private boolean isSetupDone() {
        settings.load();
        String gcc = settings.get(Settings.GCC_BIN);
        return new File(appDir, "toolchain").isDirectory()
                && !gcc.isEmpty() && new File(gcc).isDirectory();
    }

    private void buildUi() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(new EmptyBorder(8, 8, 8, 8));

        // 上: タイトル + ボタン群
        JPanel north = new JPanel(new BorderLayout());
        JLabel title = new JLabel("LightBox Studio — CH32V003 ワンストップ (VS Code不要)");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        north.add(title, BorderLayout.NORTH);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        buttons.add(action("① config.h 編集", e -> launchConfig()));
        buttons.add(action("② コンパイル", e -> launchBuilder(false)));
        buttons.add(action("②+③ ビルド→書込", e -> launchBuilder(true)));
        buttons.add(action("③ 書込のみ", e -> launchFlasher()));
        JButton pickMc = new JButton("🔧 書込ツール選択");
        pickMc.setToolTipText("動作実績のある minichlink.exe を最優先に指定");
        pickMc.addActionListener(e -> chooseMinichlink());
        buttons.add(pickMc);
        JButton zadig = new JButton("🔌 ドライバ(Zadig)");
        zadig.setToolTipText("WCH-LinkE の WinUSB ドライバ設定ガイド（初回1回）");
        zadig.addActionListener(e -> openZadigGuide());
        buttons.add(zadig);
        JButton settingsBtn = new JButton("⚙ 設定");
        settingsBtn.addActionListener(e -> openSettings());
        buttons.add(settingsBtn);
        cancelButton.setToolTipText("実行中のビルド/書込を強制停止（ハング時）");
        cancelButton.setEnabled(false);
        cancelButton.addActionListener(e -> cancelCurrent());
        buttons.add(cancelButton);
        JButton clear = new JButton("ログ消去");
        clear.addActionListener(e -> log.setText(""));
        buttons.add(clear);
        north.add(buttons, BorderLayout.CENTER);
        root.add(north, BorderLayout.NORTH);

        // 中央: ログ
        log.setEditable(false);
        log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        root.add(new JScrollPane(log), BorderLayout.CENTER);

        // 下: ステータス
        status.setBorder(new EmptyBorder(4, 2, 0, 2));
        root.add(status, BorderLayout.SOUTH);

        setContentPane(root);
    }

    private JButton action(String text, java.awt.event.ActionListener l) {
        JButton b = new JButton(text);
        b.addActionListener(l);
        actionButtons.add(b);
        return b;
    }

    private void refreshStatus() {
        settings.load();
        File pj = settings.projectDir();
        boolean ok = pj != null && new File(pj, "config.h").isFile();
        boolean toolReady = !settings.get(Settings.GCC_BIN).isEmpty() || new File(appDir, "toolchain").isDirectory();
        String tool = toolReady ? "  ✓ ツールチェーン設定済" : "  ⚠ 未セットアップ（セットアップ画面で取得してください）";
        status.setText("project: " + (pj == null ? "(未設定)" : pj.getAbsolutePath())
                + (ok ? "  ✓ config.h" : "  ⚠ config.h未検出") + tool);
    }

    // ---- 子プロセス起動 ----

    private static String javaExe() {
        File bin = new File(System.getProperty("java.home"), "bin");
        File win = new File(bin, "java.exe");
        File nix = new File(bin, "java");
        if (win.isFile()) return win.getAbsolutePath();
        if (nix.isFile()) return nix.getAbsolutePath();
        return "java";
    }

    private File childJar(String name) { return new File(appDir, name); }

    private void launchSetup() {
        File jar = childJar("setup.jar");
        if (!checkJar(jar)) return;
        List<String> cmd = Arrays.asList(javaExe(), "-jar", jar.getAbsolutePath());
        appendLog("[起動] 初回セットアップ（別ウィンドウ）");
        runAsync(cmd, false);
    }

    private void launchConfig() {
        File jar = childJar("config-editor.jar");
        if (!checkJar(jar)) return;
        File cfg = new File(settings.projectDir(), "config.h");
        List<String> cmd = Arrays.asList(javaExe(), "-jar", jar.getAbsolutePath(), cfg.getAbsolutePath());
        appendLog("[起動] config.h 編集");
        // GUI子プロセス: ボタンは止めず、出力だけログへ
        runAsync(cmd, false);
    }

    private void launchBuilder(boolean withFlash) {
        File jar = childJar("builder.jar");
        if (!checkJar(jar)) return;
        List<String> cmd = new ArrayList<>(Arrays.asList(javaExe(), "-jar", jar.getAbsolutePath()));
        if (withFlash) cmd.add("--flash");
        runAsync(cmd, true, withFlash ? this::offerPickMinichlink : null);
    }

    private void launchFlasher() {
        File jar = childJar("flasher.jar");
        if (!checkJar(jar)) return;
        List<String> cmd = Arrays.asList(javaExe(), "-jar", jar.getAbsolutePath());
        runAsync(cmd, true, this::offerPickMinichlink);
    }

    private void chooseMinichlink() { chooseMinichlink(false); }

    /**
     * 書込に使う minichlink.exe を「ファイル選択」で指定（PC全体の走査はしない＝サンドボックス）。
     * 既定フォルダは ch32fun 同梱 minichlink の場所。選択で primary＋恒久記録し、任意で即書込。
     */
    private void chooseMinichlink(boolean autoRetry) {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("動く minichlink.exe を選択");
        File defDir = defaultMinichlinkDir();
        if (defDir != null) fc.setCurrentDirectory(defDir);
        String cur = settings.get(Settings.MINICHLINK);
        if (!cur.isEmpty() && new File(cur).isFile()) fc.setSelectedFile(new File(cur));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        String path = fc.getSelectedFile().getAbsolutePath();
        settings.load();
        settings.set(Settings.MINICHLINK, path);
        settings.save();
        Settings.writeGlobal(Settings.MINICHLINK, path); // 恒久記録（クリーン再導入でも最優先）
        appendLog("[設定] minichlink.path = " + path + " （最優先＋恒久記録）");
        if (autoRetry) launchFlasher();
    }

    /** ファイル選択の既定フォルダ: ①ch32fun同梱minichlink → ②現在の設定の場所 → ③dist/toolchain。 */
    private File defaultMinichlinkDir() {
        settings.load();
        String ch = settings.get(Settings.CH32FUN);
        if (!ch.isEmpty()) {
            File p = new File(ch).getParentFile();
            if (p != null) { File m = new File(p, "minichlink"); if (m.isDirectory()) return m; }
        }
        String cur = settings.get(Settings.MINICHLINK);
        if (!cur.isEmpty()) { File p = new File(cur).getParentFile(); if (p != null && p.isDirectory()) return p; }
        File tc = new File(appDir, "toolchain");
        return tc.isDirectory() ? tc : appDir;
    }

    /** 書込失敗時に、動く minichlink を選んで即再試行するか尋ねる。 */
    private void offerPickMinichlink() {
        int r = JOptionPane.showConfirmDialog(this,
                "書込に失敗しました。\n動く minichlink.exe を選び直して、すぐ再試行しますか？",
                "書込ツールを選択", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (r != JOptionPane.YES_OPTION) return;
        chooseMinichlink(true);
    }

    // ---- WCH-LinkE ドライバ設定ガイド（Zadig） ----

    private static final String ZADIG_URL = "https://zadig.akeo.ie/";

    /** Zadig（WinUSBドライバ割当）のガイドを表示。ダウンロードページ/Zadig起動の導線付き。 */
    private void openZadigGuide() {
        String steps =
            "WCH-LinkE で書き込むには、初回1回だけ WinUSB ドライバの割当が必要です（Zadig）。\n\n" +
            "手順:\n" +
            "  1. 下の［Zadigダウンロードページ］から zadig.exe を入手し、管理者として実行。\n" +
            "  2. WCH-LinkE を USB に接続。\n" +
            "  3. Zadig の Options → List All Devices にチェック。\n" +
            "  4. 上部リストから \"WCH-Link\"（CH32書込用インターフェース）を選ぶ。\n" +
            "  5. ドライバ欄で \"WinUSB\" を選び、[Replace Driver]（または Install Driver）。\n" +
            "  6. 完了後、本ツールで ［③ 書込のみ］ を再試行。\n\n" +
            "切り分け:\n" +
            "  ・'Could not initialize' → まさにこの設定が必要。\n" +
            "  ・'nothing connected'   → 配線(SWIO=PD1)/電源/GND共通を確認。\n" +
            "  ・'Found WCH Link'後にハング → ［🔧書込ツール選択］で別の minichlink を選ぶ。";

        File zadig = findZadig();
        java.util.List<String> opts = new java.util.ArrayList<>();
        opts.add("Zadigダウンロードページ");
        if (zadig != null) opts.add("Zadigを起動");
        opts.add("閉じる");

        int sel = JOptionPane.showOptionDialog(this, steps, "WCH-LinkE ドライバ設定（Zadig）",
                JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null,
                opts.toArray(), opts.get(0));
        if (sel < 0) return;
        String chosen = opts.get(sel);
        if (chosen.equals("Zadigダウンロードページ")) {
            openBrowser(ZADIG_URL);
        } else if (chosen.equals("Zadigを起動") && zadig != null) {
            try {
                new ProcessBuilder(zadig.getAbsolutePath()).start();
                appendLog("[Zadig] 起動: " + zadig.getAbsolutePath());
            } catch (Exception ex) {
                appendLog("[Zadig] 起動失敗: " + ex.getMessage());
            }
        }
    }

    /** toolchain/ 配下に zadig*.exe があれば返す（利用者が置けば「起動」ボタンが出る）。 */
    private File findZadig() {
        File tc = new File(appDir, "toolchain");
        File[] hits = tc.listFiles((d, name) -> name.toLowerCase().startsWith("zadig") && name.toLowerCase().endsWith(".exe"));
        if (hits != null && hits.length > 0) return hits[0];
        File z = new File(new File(tc, "zadig"), "zadig.exe");
        return z.isFile() ? z : null;
    }

    private void openBrowser(String url) {
        try {
            if (java.awt.Desktop.isDesktopSupported()
                    && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
                appendLog("[Zadig] ブラウザで開きました: " + url);
            } else {
                appendLog("[Zadig] ブラウザを開けません。手動で: " + url);
            }
        } catch (Exception ex) {
            appendLog("[Zadig] ブラウザを開けません（手動で " + url + "）: " + ex.getMessage());
        }
    }

    private boolean checkJar(File jar) {
        if (!jar.isFile()) {
            appendLog("[エラー] " + jar.getName() + " が見つかりません（" + appDir.getAbsolutePath()
                    + "）。build スクリプトでビルドしてください。");
            return false;
        }
        return true;
    }

    private void runAsync(List<String> cmd, boolean blockButtons) {
        runAsync(cmd, blockButtons, null);
    }

    /** 子プロセスをバックグラウンドスレッドで実行し、出力をログへ。onFail は blockButtons かつ非0終了時にEDTで実行。 */
    private void runAsync(List<String> cmd, boolean blockButtons, Runnable onFail) {
        if (blockButtons) { setButtonsEnabled(false); SwingUtilities.invokeLater(() -> cancelButton.setEnabled(true)); }
        appendLog("> " + ProcRunner.display(cmd));
        Thread t = new Thread(() -> {
            int code = -1;
            try {
                code = ProcRunner.run(cmd, settings.projectDir(), null, this::appendLog,
                        blockButtons ? (p -> currentProc = p) : null);
            } catch (Exception ex) {
                appendLog("[例外] " + ex.getMessage());
            } finally {
                final int c = code;
                if (blockButtons) currentProc = null;
                SwingUtilities.invokeLater(() -> {
                    if (blockButtons) {
                        setButtonsEnabled(true);
                        cancelButton.setEnabled(false);
                        appendLog("[終了] コード " + c);
                        if (c != 0 && onFail != null) onFail.run();
                    }
                });
            }
        }, "child-proc");
        t.setDaemon(true);
        t.start();
    }

    private void cancelCurrent() {
        Process p = currentProc;
        if (p != null && p.isAlive()) {
            appendLog("[中断] 実行中のプロセス(minichlink等の子も含む)を停止します…");
            // 子孫(builder/flasher が起動した make や minichlink)も含めて停止
            try { p.descendants().forEach(ProcessHandle::destroyForcibly); } catch (Throwable ignored) {}
            p.destroyForcibly();
        } else {
            appendLog("[中断] 実行中のプロセスはありません");
        }
    }

    private void setButtonsEnabled(boolean en) {
        for (JButton b : actionButtons) b.setEnabled(en);
    }

    private void appendLog(String s) {
        SwingUtilities.invokeLater(() -> {
            log.append(s + "\n");
            log.setCaretPosition(log.getDocument().getLength());
        });
    }

    // ---- 設定ダイアログ ----

    private void openSettings() {
        settings.load();
        JTextField project = new JTextField(settings.get(Settings.PROJECT_DIR), 32);
        JTextField ch32fun = new JTextField(settings.get(Settings.CH32FUN), 32);
        JTextField make = new JTextField(settings.get(Settings.MAKE), 32);
        JTextField gcc = new JTextField(settings.get(Settings.GCC_BIN), 32);
        JTextField mini = new JTextField(settings.get(Settings.MINICHLINK), 32);
        JTextField art = new JTextField(settings.get(Settings.FLASH_ARTIFACT, "lightbox.bin"), 32);

        JPanel p = new JPanel(new GridBagLayout());
        int r = 0;
        addSetting(p, r++, "project.dir (source/)", project, true, false);
        addSetting(p, r++, "ch32fun.dir (ch32fun.mk のフォルダ)", ch32fun, true, false);
        addSetting(p, r++, "make (空=PATH)", make, false, true);
        addSetting(p, r++, "gcc bin (riscv-none-elf-gcc, 空=PATH)", gcc, true, false);
        addSetting(p, r++, "minichlink (空=PATH)", mini, false, true);
        addSetting(p, r++, "書込対象 (既定 lightbox.bin)", art, false, false);

        JButton auto = new JButton("project.dir 自動検出");
        auto.addActionListener(e -> {
            File d = Settings.autodetectProjectDir();
            if (d != null) project.setText(d.getAbsolutePath());
            else JOptionPane.showMessageDialog(this, "source/config.h が見つかりませんでした。手動で指定してください。");
        });
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 1; gc.gridy = r; gc.anchor = GridBagConstraints.WEST; gc.insets = new Insets(4, 4, 4, 4);
        p.add(auto, gc);

        int res = JOptionPane.showConfirmDialog(this, p, "設定", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res == JOptionPane.OK_OPTION) {
            settings.set(Settings.PROJECT_DIR, project.getText().trim());
            settings.set(Settings.CH32FUN, ch32fun.getText().trim());
            settings.set(Settings.MAKE, make.getText().trim());
            settings.set(Settings.GCC_BIN, gcc.getText().trim());
            settings.set(Settings.MINICHLINK, mini.getText().trim());
            settings.set(Settings.FLASH_ARTIFACT, art.getText().trim());
            settings.save();
            appendLog("[設定] " + settings.file().getAbsolutePath() + " に保存しました");
            refreshStatus();
        }
    }

    private void addSetting(JPanel p, int row, String label, JTextField field, boolean dir, boolean fileOnly) {
        GridBagConstraints lc = new GridBagConstraints();
        lc.gridx = 0; lc.gridy = row; lc.anchor = GridBagConstraints.WEST; lc.insets = new Insets(3, 4, 3, 4);
        p.add(new JLabel(label), lc);
        GridBagConstraints fc = new GridBagConstraints();
        fc.gridx = 1; fc.gridy = row; fc.fill = GridBagConstraints.HORIZONTAL; fc.weightx = 1; fc.insets = new Insets(3, 4, 3, 4);
        p.add(field, fc);
        JButton br = new JButton("…");
        br.addActionListener(e -> {
            JFileChooser fchooser = new JFileChooser();
            fchooser.setFileSelectionMode(dir ? JFileChooser.DIRECTORIES_ONLY : JFileChooser.FILES_ONLY);
            String cur = field.getText().trim();
            if (!cur.isEmpty()) fchooser.setSelectedFile(new File(cur));
            if (fchooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
                field.setText(fchooser.getSelectedFile().getAbsolutePath());
        });
        GridBagConstraints bc = new GridBagConstraints();
        bc.gridx = 2; bc.gridy = row; bc.insets = new Insets(3, 4, 3, 4);
        p.add(br, bc);
    }

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new MenuApp().setVisible(true));
    }
}
