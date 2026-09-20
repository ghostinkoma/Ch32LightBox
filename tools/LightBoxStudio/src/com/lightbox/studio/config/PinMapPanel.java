package com.lightbox.studio.config;

import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ピン中心の割当パネル（SVG準拠）。SOP8 の各割当可能ピンに「機能」を選ぶ方式。
 *
 * <p>従来の「機能→ピン」ではなく「ピン→機能」。1ピン=1機能が自然に保証されるため、
 * 2機能が同じピンを取り合う衝突（齟齬）を構造的に排除する。
 * UI の割当から config.h の *_PIN 群（PWM_PIN / ENC_*_PIN / WS_DIN_PIN=NL_LED_PIN /
 * TEMP_SENSE_PIN / WARN_LED_PIN）を逆算して書き込む。</p>
 *
 * <p>VSS(2)/VDD(4)/SWIO(8=PD1) は固定表示（編集不可）。上部チェックで本ブロック全体の
 * 編集可否を切替（☑=編集可 / ☐=ロック、既定=☑）。「デフォルト」で既定割当へ一括復帰。</p>
 */
public final class PinMapPanel extends JPanel {

    /** 機能。ラベルと書き込み先 config キー（NIGHT は WS_DIN_PIN と NL_LED_PIN の両方）。 */
    enum Func {
        UNUSED("未使用", null),
        PWM("PWM出力", "PWM_PIN"),
        ENC_A("エンコーダA", "ENC_A_PIN"),
        ENC_B("エンコーダB", "ENC_B_PIN"),
        ENC_SW("押しSW", "ENC_SW_PIN"),
        NIGHT("常夜灯", null),          // 特別: WS_DIN_PIN と NL_LED_PIN の両方へ
        TEMP("外付け温度", "TEMP_SENSE_PIN"),
        WARN("警告灯", "WARN_LED_PIN");
        final String label, key;
        Func(String l, String k) { label = l; key = k; }
        @Override public String toString() { return label; }
    }

    /** SOP8 の割当可能ピン（電源/SWIO を除く）。 */
    private static final String[] ASSIGN = {"PA1", "PA2", "PC1", "PC2", "PC4"};
    /** 必須機能（必ず1ピンに割当が必要）。 */
    private static final Func[] REQUIRED = {Func.PWM, Func.ENC_A, Func.ENC_B, Func.ENC_SW};

    private final Map<String, JComboBox<Func>> combos = new LinkedHashMap<>();
    private final JCheckBox enable = new JCheckBox("ピン割り当て機能を利用する（上級者向き）", true);
    private final JButton defaultBtn = new JButton("デフォルト");
    private Runnable onChange;

    public PinMapPanel() {
        super(new BorderLayout(6, 6));
        setBorder(new TitledBorder("ピン割り当て機能"));

        JPanel north = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        north.add(enable);
        add(north, BorderLayout.NORTH);

        add(buildMap(), BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        south.add(new JLabel("未割当/重複があると保存できません"));
        south.add(defaultBtn);
        add(south, BorderLayout.SOUTH);

        enable.addActionListener(e -> updateEnabled());
        defaultBtn.addActionListener(e -> { restoreDefault(); fireChange(); });
        updateEnabled();
    }

    public void setOnChange(Runnable r) { this.onChange = r; }
    private void fireChange() { if (onChange != null) onChange.run(); }

    // ---- レイアウト（左: pin1-4 / 中央: チップ / 右: pin8-5） ----

    private JComponent buildMap() {
        JPanel p = new JPanel(new GridBagLayout());
        // 左列
        rowLeft(p, 0, "1", "PA1", comboFor("PA1"));
        rowLeft(p, 1, "2", "VSS", fixed("VSS", false));
        rowLeft(p, 2, "3", "PA2", comboFor("PA2"));
        rowLeft(p, 3, "4", "VDD", fixed("VDD", true));
        // 中央チップ（4行ぶち抜き）
        JLabel chip = new JLabel("<html><div style='text-align:center'>CH32V003<br>SOP8</div></html>", SwingConstants.CENTER);
        chip.setBorder(new LineBorder(Color.BLACK, 2));
        chip.setPreferredSize(new Dimension(120, 140));
        GridBagConstraints cc = gbc(3, 0);
        cc.gridheight = 4; cc.fill = GridBagConstraints.BOTH; cc.insets = new Insets(4, 12, 4, 12);
        p.add(chip, cc);
        // 右列
        rowRight(p, 0, "8", "SWIO", fixed("SWIO", false));
        rowRight(p, 1, "7", "PC4", comboFor("PC4"));
        rowRight(p, 2, "6", "PC2", comboFor("PC2"));
        rowRight(p, 3, "5", "PC1", comboFor("PC1"));
        return p;
    }

    private JComboBox<Func> comboFor(String pin) {
        JComboBox<Func> c = new JComboBox<>();
        c.addItem(Func.UNUSED);
        if (contains(ConfigSchema.PINS_PWM, pin)) c.addItem(Func.PWM);   // PWM 可能ピンのみ
        c.addItem(Func.ENC_A); c.addItem(Func.ENC_B); c.addItem(Func.ENC_SW);
        c.addItem(Func.NIGHT);
        if (contains(ConfigSchema.PINS_ADC, pin)) c.addItem(Func.TEMP);  // ADC 対応ピンのみ
        c.addItem(Func.WARN);
        c.addActionListener(e -> fireChange());
        combos.put(pin, c);
        return c;
    }

    private JLabel fixed(String txt, boolean power) {
        JLabel l = new JLabel(txt);
        l.setForeground(power ? new Color(0xE7, 0x29, 0x20) : Color.DARK_GRAY);
        return l;
    }

    private void rowLeft(JPanel p, int row, String num, String name, JComponent ctrl) {
        GridBagConstraints c0 = gbc(0, row); c0.fill = GridBagConstraints.HORIZONTAL; c0.weightx = 1;
        p.add(ctrl, c0);
        p.add(new JLabel(num + " " + name), gbc(1, row));
    }

    private void rowRight(JPanel p, int row, String num, String name, JComponent ctrl) {
        p.add(new JLabel(name + " " + num), gbc(4, row));
        GridBagConstraints c5 = gbc(5, row); c5.fill = GridBagConstraints.HORIZONTAL; c5.weightx = 1;
        p.add(ctrl, c5);
    }

    private static GridBagConstraints gbc(int x, int y) {
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = x; g.gridy = y; g.insets = new Insets(3, 4, 3, 4); g.anchor = GridBagConstraints.WEST;
        return g;
    }

    private void updateEnabled() {
        boolean en = enable.isSelected();
        for (JComboBox<Func> c : combos.values()) c.setEnabled(en);
        defaultBtn.setEnabled(en);
    }

    // ---- 読み込み / 適用 / 検証 ----

    /** config.h の *_PIN 群から「各ピンの機能」を復元。 */
    public void load(ConfigFile cf) {
        String pwm = cf.getSymbol("PWM_PIN", ""),  a = cf.getSymbol("ENC_A_PIN", ""),
               b = cf.getSymbol("ENC_B_PIN", ""),  sw = cf.getSymbol("ENC_SW_PIN", ""),
               ws = cf.getSymbol("WS_DIN_PIN", ""), nl = cf.getSymbol("NL_LED_PIN", ""),
               temp = cf.getSymbol("TEMP_SENSE_PIN", ""), warn = cf.getSymbol("WARN_LED_PIN", "");
        boolean tempActive = cf.getLong("TEMP_PROTECT_ENABLE", 0) == 1
                && "TEMP_SOURCE_EXTERNAL".equals(cf.getSymbol("TEMP_SOURCE", ""));
        boolean warnActive = cf.getLong("WARN_LED_ENABLE", 0) == 1;
        for (String pin : ASSIGN) {
            Func f = Func.UNUSED;
            if (pin.equals(pwm)) f = Func.PWM;
            else if (pin.equals(a)) f = Func.ENC_A;
            else if (pin.equals(b)) f = Func.ENC_B;
            else if (pin.equals(sw)) f = Func.ENC_SW;
            else if (pin.equals(ws) || pin.equals(nl)) f = Func.NIGHT;   // 常夜灯を優先
            else if (tempActive && pin.equals(temp)) f = Func.TEMP;      // 温度は有効時のみ占有扱い
            else if (warnActive && pin.equals(warn)) f = Func.WARN;      // 警告灯は有効時のみ
            setCombo(pin, f);
        }
    }

    /** 各ピンの機能から config.h の *_PIN 群へ書き込む（一意割当のもののみ）。 */
    public void apply(ConfigFile cf) {
        Map<Func, String> first = new LinkedHashMap<>();
        Map<Func, Integer> count = new HashMap<>();
        for (String pin : ASSIGN) {
            Func f = (Func) combos.get(pin).getSelectedItem();
            if (f == null || f == Func.UNUSED) continue;
            count.merge(f, 1, Integer::sum);
            first.putIfAbsent(f, pin);
        }
        writeIf(cf, Func.PWM, first, count);
        writeIf(cf, Func.ENC_A, first, count);
        writeIf(cf, Func.ENC_B, first, count);
        writeIf(cf, Func.ENC_SW, first, count);
        writeIf(cf, Func.TEMP, first, count);
        writeIf(cf, Func.WARN, first, count);
        if (count.getOrDefault(Func.NIGHT, 0) == 1) {   // 常夜灯は両キーへ
            String p = first.get(Func.NIGHT);
            if (cf.has("WS_DIN_PIN")) cf.setSymbol("WS_DIN_PIN", p);
            if (cf.has("NL_LED_PIN")) cf.setSymbol("NL_LED_PIN", p);
        }
    }

    private void writeIf(ConfigFile cf, Func f, Map<Func, String> first, Map<Func, Integer> count) {
        if (f.key != null && count.getOrDefault(f, 0) == 1 && cf.has(f.key))
            cf.setSymbol(f.key, first.get(f));
    }

    /** 検証: 機能の重複割当 / 必須機能の未割当 / 有効な任意機能の未割当。 */
    public List<ConfigSchema.Issue> issues(ConfigFile cf) {
        List<ConfigSchema.Issue> out = new ArrayList<>();
        Map<Func, List<String>> byFunc = new LinkedHashMap<>();
        for (String pin : ASSIGN) {
            Func f = (Func) combos.get(pin).getSelectedItem();
            if (f != null && f != Func.UNUSED) byFunc.computeIfAbsent(f, k -> new ArrayList<>()).add(pin);
        }
        for (Map.Entry<Func, List<String>> e : byFunc.entrySet())
            if (e.getValue().size() > 1)
                out.add(new ConfigSchema.Issue(true, "ピン割当: 機能『" + e.getKey().label
                        + "』が複数ピン(" + String.join(",", e.getValue()) + ")に割当。1機能=1ピンに"));
        for (Func req : REQUIRED)
            if (!byFunc.containsKey(req))
                out.add(new ConfigSchema.Issue(true, "ピン割当: 必須機能『" + req.label + "』が未割当"));
        if (cf.getLong("NIGHTLIGHT_ENABLE", 0) == 1 && !byFunc.containsKey(Func.NIGHT))
            out.add(new ConfigSchema.Issue(true, "ピン割当: 常夜灯が有効ですがピン未割当"));
        if (cf.getLong("TEMP_PROTECT_ENABLE", 0) == 1
                && "TEMP_SOURCE_EXTERNAL".equals(cf.getSymbol("TEMP_SOURCE", ""))
                && !byFunc.containsKey(Func.TEMP))
            out.add(new ConfigSchema.Issue(true, "ピン割当: 外付け温度が有効ですがピン未割当"));
        if (cf.getLong("WARN_LED_ENABLE", 0) == 1 && !byFunc.containsKey(Func.WARN))
            out.add(new ConfigSchema.Issue(true, "ピン割当: 警告灯が有効ですがピン未割当"));
        return out;
    }

    /** 実機検証済みの既定割当へ一括復帰。 */
    public void restoreDefault() {
        setCombo("PA1", Func.ENC_A);
        setCombo("PA2", Func.ENC_SW);
        setCombo("PC1", Func.ENC_B);
        setCombo("PC2", Func.PWM);
        setCombo("PC4", Func.NIGHT);
    }

    private void setCombo(String pin, Func f) {
        JComboBox<Func> c = combos.get(pin);
        if (c == null) return;
        for (int i = 0; i < c.getItemCount(); i++)
            if (c.getItemAt(i) == f) { c.setSelectedIndex(i); return; }
        c.setSelectedItem(Func.UNUSED);   // そのピンに無い機能なら未使用
    }

    private static boolean contains(String[] arr, String v) {
        for (String s : arr) if (s.equals(v)) return true;
        return false;
    }
}
