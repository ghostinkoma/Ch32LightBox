package com.lightbox.studio.config;

import com.lightbox.studio.common.Settings;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * config.h 編集GUI（スキーマ駆動）。メニューから起動され、
 * 値の行だけを差し替えて保存する（コメント/enum定義は保全）。
 *
 * <p>起動引数: [config.hのパス]（省略時は settings.properties の project.dir/config.h）。</p>
 */
public final class ConfigEditor extends JFrame {

    /** コンボの項目（raw値 + 表示ラベル）。 */
    private static final class Item {
        final String raw, label;
        Item(String raw, String label) { this.raw = raw; this.label = label; }
        @Override public String toString() { return label; }
    }

    private final List<ConfigField> schema = ConfigSchema.fields();
    private final Map<String, JSpinner> ints = new LinkedHashMap<>();
    private final Map<String, JCheckBox> bools = new LinkedHashMap<>();
    private final Map<String, JComboBox<Item>> combos = new LinkedHashMap<>();
    private ColorField colorField;              // WS_MAX_COLOR
    private final PinMapPanel pinMap = new PinMapPanel();   // ピン中心の割当UI(SVG準拠)

    private File configPath;
    private ConfigFile cf;
    private boolean loading = false;             // loadWidgets 中はリスナ由来の再計算/適用を抑止
    private final JTextField pathField = new JTextField();
    private final JTextArea issues = new JTextArea(6, 40);
    private final JLabel estimateLabel = new JLabel(" ");   // CR2032 電池寿命の目安(ライブ)

    public ConfigEditor(File initialPath) {
        super("LightBox config.h エディタ");
        this.configPath = initialPath;
        buildUi();
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(720, 780);
        setLocationRelativeTo(null);
        if (configPath != null && configPath.isFile()) reload();
        else appendIssue("config.h の場所を指定して［再読込］してください: " + configPath);
    }

    private void buildUi() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(new EmptyBorder(8, 8, 8, 8));

        // 上: パス
        JPanel top = new JPanel(new BorderLayout(6, 0));
        top.add(new JLabel("config.h:"), BorderLayout.WEST);
        pathField.setText(configPath == null ? "" : configPath.getAbsolutePath());
        top.add(pathField, BorderLayout.CENTER);
        JPanel topBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JButton browse = new JButton("参照…");
        browse.addActionListener(e -> onBrowse());
        JButton reload = new JButton("再読込");
        reload.addActionListener(e -> { configPath = new File(pathField.getText().trim()); reload(); });
        topBtns.add(browse); topBtns.add(reload);
        top.add(topBtns, BorderLayout.EAST);
        root.add(top, BorderLayout.NORTH);

        // 中央: セクション別フォーム
        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        // 先頭に「ピン割り当て機能」(ピン中心UI)を配置。変更で電池目安も再計算。
        pinMap.setOnChange(this::updateEstimate);
        form.add(pinMap);
        form.add(Box.createVerticalStrut(4));
        String currentSection = null;
        JPanel sectionPanel = null;
        int row = 0;
        for (ConfigField f : schema) {
            if (!f.section.equals(currentSection)) {
                currentSection = f.section;
                sectionPanel = new JPanel(new GridBagLayout());
                sectionPanel.setBorder(new TitledBorder(currentSection));
                form.add(sectionPanel);
                form.add(Box.createVerticalStrut(4));
                row = 0;
            }
            addFieldRow(sectionPanel, row++, f);
        }
        JScrollPane sc = new JScrollPane(form);
        sc.getVerticalScrollBar().setUnitIncrement(16);
        root.add(sc, BorderLayout.CENTER);

        // 下: 操作 + 検証結果
        JPanel bottom = new JPanel(new BorderLayout(6, 6));
        estimateLabel.setForeground(new Color(0, 110, 0));
        bottom.add(estimateLabel, BorderLayout.NORTH);

        issues.setEditable(false);
        issues.setLineWrap(true);
        issues.setWrapStyleWord(true);
        bottom.add(new JScrollPane(issues), BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        JButton validate = new JButton("検証");
        validate.addActionListener(e -> runValidate());
        JButton save = new JButton("保存");
        save.addActionListener(e -> onSave());
        JButton close = new JButton("閉じる");
        close.addActionListener(e -> dispose());
        actions.add(validate); actions.add(save); actions.add(close);
        bottom.add(actions, BorderLayout.SOUTH);

        root.add(bottom, BorderLayout.SOUTH);
        setContentPane(root);
        attachEstimateListeners();
    }

    /** 関連ウィジェット変更時に電池寿命の目安をライブ再計算。 */
    private void attachEstimateListeners() {
        javax.swing.event.ChangeListener cl = e -> updateEstimate();
        java.awt.event.ActionListener al = e -> updateEstimate();
        for (JSpinner sp : ints.values()) sp.addChangeListener(cl);
        for (JCheckBox cb : bools.values()) cb.addActionListener(al);
        for (JComboBox<Item> cx : combos.values()) cx.addActionListener(al);
    }

    private void updateEstimate() {
        if (loading || cf == null) { return; }   // ロード中は適用しない(値の取り違え防止)
        try { applyWidgets(); } catch (RuntimeException ex) { return; }
        String s = ConfigSchema.batteryEstimate(cf);
        estimateLabel.setText(s == null ? "（電池目安: 常夜灯=②単色LED のとき表示）" : s);
    }

    private void addFieldRow(JPanel panel, int row, ConfigField f) {
        GridBagConstraints lc = gbc(0, row); lc.weightx = 0; lc.anchor = GridBagConstraints.WEST;
        GridBagConstraints ec = gbc(1, row); ec.weightx = 1; ec.fill = GridBagConstraints.HORIZONTAL;
        GridBagConstraints hc = gbc(2, row); hc.weightx = 2; hc.fill = GridBagConstraints.HORIZONTAL;

        JLabel label = new JLabel(f.label + "  (" + f.key + ")");
        panel.add(label, lc);

        JComponent editor = createEditor(f);
        panel.add(editor, ec);

        String help = f.help + (f.unit.isEmpty() ? "" : (f.help.isEmpty() ? "" : " ") + "[" + f.unit + "]");
        JLabel helpLabel = new JLabel(help.isEmpty() ? "" : "— " + help);
        helpLabel.setForeground(new Color(90, 90, 90));
        panel.add(helpLabel, hc);

        if (!f.help.isEmpty()) { editor.setToolTipText(f.help); label.setToolTipText(f.help); }
    }

    private JComponent createEditor(ConfigField f) {
        switch (f.type) {
            case INT: {
                long init = clamp(0, f.min, f.max);
                SpinnerNumberModel m = new SpinnerNumberModel(
                        (Number) init, Long.valueOf(f.min), Long.valueOf(f.max), Long.valueOf(1));
                JSpinner sp = new JSpinner(m);
                ints.put(f.key, sp);
                return sp;
            }
            case BOOL: {
                JCheckBox cb = new JCheckBox();
                bools.put(f.key, cb);
                return cb;
            }
            case ENUM: case PORT: case PIN: {
                JComboBox<Item> cbx = new JComboBox<>();
                for (Map.Entry<String, String> en : f.options.entrySet())
                    cbx.addItem(new Item(en.getKey(), en.getValue()));
                combos.put(f.key, cbx);
                if ("WS_ORDER".equals(f.key)) cbx.addActionListener(e -> { if (colorField != null) colorField.setDigits(currentColorDigits()); });
                return cbx;
            }
            case COLOR: {
                colorField = new ColorField();
                return colorField;
            }
            default:
                return new JLabel("(未対応)");
        }
    }

    private int currentColorDigits() {
        JComboBox<Item> c = combos.get("WS_ORDER");
        String raw = (c != null && c.getSelectedItem() != null) ? ((Item) c.getSelectedItem()).raw : "WS_ORDER_GRB";
        return ConfigSchema.colorHexDigits(raw);
    }

    // ---- 読み込み ----

    private void reload() {
        try {
            if (configPath == null || !configPath.isFile()) {
                appendIssue("ファイルがありません: " + configPath);
                return;
            }
            cf = ConfigFile.load(configPath.toPath());
            loadWidgets();
            pathField.setText(configPath.getAbsolutePath());
            issues.setText("読み込みました: " + configPath.getAbsolutePath() + "\n");
            auditRanges();
            updateEstimate();
        } catch (IOException ex) {
            appendIssue("読み込み失敗: " + ex.getMessage());
        }
    }

    private void loadWidgets() {
        loading = true;                 // ロード中はウィジェット変更リスナ(目安再計算=applyWidgets)を抑止
        try {
        for (ConfigField f : schema) {
            if (!cf.has(f.key)) continue; // config.h に無いキーはスキップ
            switch (f.type) {
                case INT:
                    ints.get(f.key).setValue(clamp(cf.getLong(f.key, f.min), f.min, f.max));
                    break;
                case BOOL:
                    bools.get(f.key).setSelected(cf.getLong(f.key, 0) == 1);
                    break;
                case ENUM: case PORT: case PIN:
                    selectRaw(combos.get(f.key), cf.getSymbol(f.key, ""));
                    break;
                case COLOR:
                    colorField.setDigits(currentColorDigits());
                    colorField.setValue(cf.getLong(f.key, 0));
                    break;
            }
        }
        colorField.setDigits(currentColorDigits());
        pinMap.load(cf);                 // ピン割当をファイルの *_PIN 群から復元
        } finally {
            loading = false;
        }
    }

    /** 読み込んだファイルの INT 値が範囲外だったら（=クランプ表示された）警告する。 */
    private void auditRanges() {
        StringBuilder sb = new StringBuilder();
        for (ConfigField f : schema) {
            if (f.type != ConfigField.Type.INT || !cf.has(f.key)) continue;
            long v = cf.getLong(f.key, f.min);
            if (v < f.min || v > f.max)
                sb.append("⚠ ").append(f.key).append(" のファイル値 ").append(v)
                  .append(" は範囲 ").append(f.min).append("..").append(f.max)
                  .append(" 外です。表示は ").append(Math.max(f.min, Math.min(f.max, v)))
                  .append(" にクランプしました（保存で修正されます）\n");
        }
        if (sb.length() > 0) issues.append("\n【範囲外の既存値】\n" + sb);
    }

    private static void selectRaw(JComboBox<Item> box, String raw) {
        for (int i = 0; i < box.getItemCount(); i++) {
            if (box.getItemAt(i).raw.equals(raw)) { box.setSelectedIndex(i); return; }
        }
        // 該当なし: 先頭のまま（未知トークンは保存時に上書きされる点に注意）
    }

    // ---- 適用（widget -> ConfigFile） ----

    private void applyWidgets() {
        for (ConfigField f : schema) {
            if (!cf.has(f.key)) continue;
            switch (f.type) {
                case INT:
                    cf.setLong(f.key, ((Number) ints.get(f.key).getValue()).longValue());
                    break;
                case BOOL:
                    cf.setLong(f.key, bools.get(f.key).isSelected() ? 1 : 0);
                    break;
                case ENUM: case PORT: case PIN: {
                    Item it = (Item) combos.get(f.key).getSelectedItem();
                    if (it != null) cf.setSymbol(f.key, it.raw);
                    break;
                }
                case COLOR:
                    cf.setColor(f.key, colorField.getValue(), currentColorDigits());
                    break;
            }
        }
        pinMap.apply(cf);                // ピン割当を *_PIN 群へ書き戻す
    }

    // ---- 検証・保存 ----

    private boolean runValidate() {
        if (cf == null) { appendIssue("先に config.h を読み込んでください"); return false; }
        applyWidgets();
        List<ConfigSchema.Issue> list = new java.util.ArrayList<>(pinMap.issues(cf)); // ピン割当UIの検証を先頭に
        list.addAll(ConfigSchema.validate(cf));
        StringBuilder sb = new StringBuilder();
        int errors = 0, warns = 0;
        for (ConfigSchema.Issue is : list) {
            sb.append(is.error ? "【エラー】" : "【警告】").append(is.message).append('\n');
            if (is.error) errors++; else warns++;
        }
        if (list.isEmpty()) sb.append("問題なし。保存できます。\n");
        else sb.insert(0, "エラー " + errors + " / 警告 " + warns + "\n");
        issues.setText(sb.toString());
        updateEstimate();
        return errors == 0;
    }

    private void onSave() {
        if (cf == null) { appendIssue("先に config.h を読み込んでください"); return; }
        boolean ok = runValidate();
        if (!ok) {
            JOptionPane.showMessageDialog(this, "エラーがあります。修正してください。",
                    "保存不可", JOptionPane.ERROR_MESSAGE);
            return;
        }
        // 警告があれば確認
        List<ConfigSchema.Issue> list = ConfigSchema.validate(cf);
        boolean hasWarn = list.stream().anyMatch(i -> !i.error);
        if (hasWarn) {
            int r = JOptionPane.showConfirmDialog(this, "警告があります。このまま保存しますか？",
                    "確認", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (r != JOptionPane.YES_OPTION) return;
        }
        try {
            // バックアップ
            Path bak = configPath.toPath().resolveSibling(configPath.getName() + ".bak");
            Files.write(bak, String.join("\n", Files.readAllLines(configPath.toPath(), StandardCharsets.UTF_8))
                    .concat("\n").getBytes(StandardCharsets.UTF_8));
            cf.save(configPath.toPath());
            appendIssue("保存しました: " + configPath.getAbsolutePath() + "  (バックアップ: " + bak.getFileName() + ")");
            JOptionPane.showMessageDialog(this, "保存しました。\nメニューの［コンパイル］で反映されます。",
                    "完了", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            appendIssue("保存失敗: " + ex.getMessage());
            JOptionPane.showMessageDialog(this, "保存に失敗しました: " + ex.getMessage(),
                    "エラー", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onBrowse() {
        JFileChooser fc = new JFileChooser();
        if (configPath != null && configPath.getParentFile() != null) fc.setCurrentDirectory(configPath.getParentFile());
        fc.setDialogTitle("config.h を選択");
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            configPath = fc.getSelectedFile();
            pathField.setText(configPath.getAbsolutePath());
            reload();
        }
    }

    private void appendIssue(String s) { issues.append(s + "\n"); }

    private static GridBagConstraints gbc(int x, int y) {
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = x; g.gridy = y; g.insets = new Insets(2, 4, 2, 4);
        g.anchor = GridBagConstraints.WEST;
        return g;
    }

    private static long clamp(long v, long lo, long hi) { return Math.max(lo, Math.min(hi, v)); }

    // ---- 色入力（0xRRGGBB / 0xRRGGBBWW） ----
    private static final class ColorField extends JPanel {
        private int digits = 6;
        private long value = 0;
        private final JTextField hex = new JTextField(10);
        private final JPanel swatch = new JPanel();
        private final JButton pick = new JButton("RGB…");
        private final JSpinner white = new JSpinner(new SpinnerNumberModel(0, 0, 255, 1));
        private final JLabel wLabel = new JLabel("W:");

        ColorField() {
            super(new FlowLayout(FlowLayout.LEFT, 4, 0));
            swatch.setPreferredSize(new Dimension(20, 20));
            swatch.setBorder(BorderFactory.createLineBorder(Color.GRAY));
            add(hex); add(swatch); add(pick); add(wLabel); add(white);
            pick.addActionListener(e -> onPick());
            hex.addActionListener(e -> onHexEdited());
            hex.addFocusListener(new java.awt.event.FocusAdapter() {
                @Override public void focusLost(java.awt.event.FocusEvent e) { onHexEdited(); }
            });
            white.addChangeListener(e -> { recomputeFromParts(); });
        }

        void setDigits(int d) {
            this.digits = d;
            boolean rgbw = d == 8;
            wLabel.setVisible(rgbw); white.setVisible(rgbw);
            render();
        }

        void setValue(long v) { this.value = v; render(); }
        long getValue() { return value; }

        private void onPick() {
            int r = (int) ((value >> (digits == 8 ? 24 : 16)) & 0xFF);
            int g = (int) ((value >> (digits == 8 ? 16 : 8)) & 0xFF);
            int b = (int) ((value >> (digits == 8 ? 8 : 0)) & 0xFF);
            Color c = JColorChooser.showDialog(this, "RGB を選択", new Color(r, g, b));
            if (c == null) return;
            if (digits == 8) {
                int w = (int) (value & 0xFF);
                value = ((long) c.getRed() << 24) | ((long) c.getGreen() << 16) | ((long) c.getBlue() << 8) | w;
            } else {
                value = ((long) c.getRed() << 16) | ((long) c.getGreen() << 8) | c.getBlue();
            }
            render();
        }

        private void recomputeFromParts() {
            if (digits == 8) {
                int w = ((Number) white.getValue()).intValue();
                value = (value & 0xFFFFFF00L) | (w & 0xFF);
                render();
            }
        }

        private void onHexEdited() {
            try {
                String t = hex.getText().trim();
                if (t.startsWith("0x") || t.startsWith("0X")) t = t.substring(2);
                if (t.endsWith("u") || t.endsWith("U")) t = t.substring(0, t.length() - 1);
                long v = Long.parseLong(t, 16);
                value = v;
                render();
            } catch (NumberFormatException ex) {
                render(); // 不正入力は元に戻す
            }
        }

        private void render() {
            String h = Long.toHexString(value & 0xFFFFFFFFL);
            while (h.length() < digits) h = "0" + h;
            if (h.length() > digits) h = h.substring(h.length() - digits);
            hex.setText("0x" + h);
            int r, g, b;
            if (digits == 8) {
                r = (int) ((value >> 24) & 0xFF); g = (int) ((value >> 16) & 0xFF); b = (int) ((value >> 8) & 0xFF);
                white.setValue((int) (value & 0xFF));
            } else {
                r = (int) ((value >> 16) & 0xFF); g = (int) ((value >> 8) & 0xFF); b = (int) (value & 0xFF);
            }
            swatch.setBackground(new Color(r, g, b));
        }
    }

    // ---- エントリポイント ----
    public static void main(String[] args) {
        File path;
        if (args.length > 0) {
            path = new File(args[0]);
        } else {
            Settings st = Settings.beside(ConfigEditor.class);
            path = new File(st.projectDir(), "config.h");
        }
        final File p = path;
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new ConfigEditor(p).setVisible(true));
    }
}
