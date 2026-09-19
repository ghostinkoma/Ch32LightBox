package com.lightbox.studio.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LightBox config.h の全編集項目スキーマと、相互制約バリデーション。
 * 定義の出典は documents/CONFIG_REFERENCE.md と source/config.h。
 */
public final class ConfigSchema {

    private ConfigSchema() {}

    /** 検証結果の1件。 */
    public static final class Issue {
        public final boolean error;   // true=エラー(書けない) / false=警告
        public final String message;
        public Issue(boolean error, String message) { this.error = error; this.message = message; }
    }

    /** SOP8 (J4M6) 実在ピン。firmware pins.h の LB_PIN_IS_SOP8 と一致。 */
    static final String[] PINS_ALL = {"PA1", "PA2", "PC1", "PC2", "PC4", "PD1"};
    /** PWM 正出力が可能なピン (PC2=TIM2_CH2 / PC1=TIM2_CH4 / PA1=TIM1_CH2 / PC4=TIM1_CH4)。 */
    static final String[] PINS_PWM = {"PC2", "PC1", "PA1", "PC4"};
    /** ADC 対応ピン (PA2=ch0 / PA1=ch1 / PC4=ch2)。外付け温度センサ用。 */
    static final String[] PINS_ADC = {"PA2", "PA1", "PC4"};
    /** SWIO(書込/printf)予約ピン。 */
    static final String SWIO_PIN = "PD1";

    public static List<ConfigField> fields() {
        List<ConfigField> f = new ArrayList<>();

        String S;
        // ---- ピン割当 ----
        S = "ピン割当 (HW結線固定・上級者向け)";
        f.add(pin("PWM_PIN", S, "本体LED PWM出力", PINS_PWM).advanced()
                .help("PWM可能ピンのみ。PC2=既定(TIM2_CH2,実機検証済)。他はコンパイル対応・実機未検証").build());
        f.add(pin("ENC_A_PIN", S, "エンコーダ A 相").advanced().help("内部プルアップ。HW結線と一致必須").build());
        f.add(pin("ENC_B_PIN", S, "エンコーダ B 相").advanced().help("内部プルアップ。HW結線と一致必須").build());
        f.add(pin("ENC_SW_PIN", S, "押しSW").advanced().help("内部プルアップ, 押下=Low").build());

        // ---- PWM ----
        S = "PWM (16bit TIM2)";
        f.add(ConfigField.of("PWM_TOP", ConfigField.Type.INT).section(S).label("分解能-1 (PWM_TOP)")
                .range(1, 65535).help("周波数=48MHz/(TOP+1)/(PSC+1)。上げると滑らか/低周波。既定4095").build());
        f.add(ConfigField.of("PWM_PSC", ConfigField.Type.INT).section(S).label("プリスケーラ (PWM_PSC)")
                .range(0, 65535).help("既定0").build());

        // ---- EMI ----
        S = "EMI対策 (PWM)";
        f.add(ConfigField.of("PWM_SLEW_MHZ", ConfigField.Type.ENUM).section(S).label("スルーレート")
                .option("2", "2 MHz (大電流LED推奨)").option("10", "10 MHz").option("30", "30 MHz")
                .help("低いほどエッジが鈍り高調波↓").build());
        f.add(bool("PWM_SPREAD_SPECTRUM", S, "スペクトラム拡散").help("1で周期を微小ディザ(明るさ不変)").build());
        f.add(ConfigField.of("PWM_SPREAD_RANGE", ConfigField.Type.INT).section(S).label("拡散振り幅(±count)")
                .range(1, 65535).help("PWM_TOP+RANGE≤65535 かつ RANGE<PWM_TOP 必須").build());
        f.add(ConfigField.of("PWM_SPREAD_PERIOD_MS", ConfigField.Type.INT).section(S).label("ディザ更新間隔")
                .range(1, 65535).unit("ms").build());

        // ---- 調光段 ----
        S = "調光段";
        f.add(ConfigField.of("LEVELS", ConfigField.Type.INT).section(S).label("段数 (0..LEVELS)")
                .range(1, 127).help("既定64。ファーム制約で1..127").build());
        f.add(ConfigField.of("LEVEL_INIT", ConfigField.Type.INT).section(S).label("起動レベル")
                .range(0, 127).help("STORE無効時のみ有効。LEVELS以下").build());

        // ---- エンコーダ ----
        S = "エンコーダ / チャタリング";
        f.add(bool("ENC_REVERSE", S, "CW/CCW反転").help("1で回転方向を反転").build());
        f.add(bool("ENC_HALF_STEP", S, "ハーフステップ").help("0=フルステップ(4遷移で1段)/1=ハーフ(2遷移で1段)").build());
        f.add(bool("ENC_ACCEL_ENABLE", S, "加速有効").build());
        f.add(ConfigField.of("ENC_ACCEL_FACTOR", ConfigField.Type.INT).section(S).label("加速倍率")
                .range(1, 20).help("速回し時の1クリックのステップ倍率").build());
        f.add(ConfigField.of("ENC_ACCEL_WINDOW_MS", ConfigField.Type.INT).section(S).label("加速判定窓")
                .range(1, 2000).unit("ms").build());
        f.add(ConfigField.of("ENC_REVERSAL_LOCK_MS", ConfigField.Type.INT).section(S).label("逆方向ロックアウト")
                .range(0, 1000).unit("ms").help("この時間内の逆方向はバウンス扱いで無視").build());

        // ---- スナップ ----
        S = "高速回しスナップ";
        f.add(ConfigField.of("SNAP_WINDOW_MS", ConfigField.Type.INT).section(S).label("判定窓")
                .range(1, 5000).unit("ms").build());
        f.add(ConfigField.of("SNAP_CLICKS", ConfigField.Type.INT).section(S).label("発火クリック数")
                .range(2, 255).help("2以上").build());

        // ---- 押しSW ----
        S = "押しSW";
        f.add(ConfigField.of("SW_DEBOUNCE_MS", ConfigField.Type.INT).section(S).label("デバウンス")
                .range(1, 1000).unit("ms").build());

        // ---- 動作オプション ----
        S = "動作オプション";
        f.add(bool("WAKE_ON_TURN", S, "回して自動点灯").build());
        f.add(bool("DEBUG_LOG", S, "SWD printfログ").help("★通常運用は0厳守(ブロッキングで取りこぼし要因)").build());

        // ---- ソフトスタート ----
        S = "ソフトスタート";
        f.add(ConfigField.of("SOFT_START_ON", ConfigField.Type.INT).section(S).label("点灯フェード")
                .range(0, 65535).unit("ms").help("0=即時").build());
        f.add(ConfigField.of("SOFT_START_OFF", ConfigField.Type.INT).section(S).label("消灯フェード")
                .range(0, 65535).unit("ms").help("0=即時").build());

        // ---- ストア ----
        S = "明るさ不揮発ストア";
        f.add(bool("STORE_ENABLE", S, "明るさ記憶").build());
        f.add(ConfigField.of("STORE_SIZE_BYTES", ConfigField.Type.INT).section(S).label("予約サイズ")
                .range(1024, 65536).unit("byte").help("1KBの倍数。2KB=1024レコード").build());
        f.add(ConfigField.of("STORE_COMMIT_MS", ConfigField.Type.INT).section(S).label("保存待ち")
                .range(0, 600000).unit("ms").build());

        // ---- 温度保護 ----
        S = "温度保護 (既定OFF)";
        f.add(bool("TEMP_PROTECT_ENABLE", S, "過熱保護有効").help("既定0。実測NTCで守る時のみ1").build());
        f.add(ConfigField.of("TEMP_SOURCE", ConfigField.Type.ENUM).section(S).label("温度源")
                .option("TEMP_SOURCE_DIE", "ダイ推定 (非推奨・誤遮断)")
                .option("TEMP_SOURCE_EXTERNAL", "外付けNTC実測 (推奨)").build());
        f.add(ConfigField.of("DIE_AMBIENT_C", ConfigField.Type.INT).section(S).label("[DIE]基準温度")
                .range(-40, 125).unit("℃").build());
        f.add(ConfigField.of("DIE_RISE_AT_FULL_C", ConfigField.Type.INT).section(S).label("[DIE]全開時上昇")
                .range(0, 125).unit("℃").build());
        f.add(ConfigField.of("DIE_TAU_MS", ConfigField.Type.INT).section(S).label("[DIE]熱時定数")
                .range(100, 600000).unit("ms").build());
        f.add(pin("TEMP_SENSE_PIN", S, "[EXT]センサピン(ADC対応)", PINS_ADC)
                .help("ADCチャネルは自動導出(PA2=0/PA1=1/PC4=2)").build());
        f.add(ConfigField.of("TEMP_CAL_T0_C", ConfigField.Type.INT).section(S).label("[EXT]校正基準温度")
                .range(-40, 125).unit("℃").build());
        f.add(ConfigField.of("TEMP_CAL_ADC0", ConfigField.Type.INT).section(S).label("[EXT]T0時の生ADC")
                .range(0, 1023).build());
        f.add(ConfigField.of("TEMP_CAL_SLOPE_X100", ConfigField.Type.INT).section(S).label("[EXT]傾き×100")
                .range(-100000, 100000).help("ADC/℃×100(符号付, 0不可)").build());
        f.add(ConfigField.of("WARN_TEMP_C", ConfigField.Type.INT).section(S).label("遮断閾値")
                .range(0, 150).unit("℃").build());
        f.add(ConfigField.of("WARN_TEMP_HYST_C", ConfigField.Type.INT).section(S).label("復帰ヒステリシス")
                .range(1, 100).unit("℃").help("この分下がると再開(<WARN)").build());
        f.add(ConfigField.of("WARN_TEMP_PERIOD_MS", ConfigField.Type.INT).section(S).label("サンプリング周期")
                .range(10, 10000).unit("ms").build());
        f.add(ConfigField.of("THERMAL_TRIP_N", ConfigField.Type.INT).section(S).label("スロットル発動回数")
                .range(1, 255).build());
        f.add(ConfigField.of("THERMAL_THROTTLE_PCT", ConfigField.Type.INT).section(S).label("スロットル量")
                .range(1, 100).unit("%").build());
        f.add(ConfigField.of("THERMAL_THROTTLE_MAX", ConfigField.Type.INT).section(S).label("スロットル上限")
                .range(1, 99).unit("%").help("<100").build());
        f.add(bool("WARN_LED_ENABLE", S, "警告灯").help("過熱中にWARN_LED_PINをHigh").build());
        f.add(pin("WARN_LED_PIN", S, "警告灯ピン").build());

        // ---- 常夜灯 ----
        S = "常夜灯 (WS2812/SK6812)";
        f.add(bool("NIGHTLIGHT_ENABLE", S, "常夜灯有効").build());
        f.add(pin("WS_DIN_PIN", S, "データ線GPIO", PINS_ALL)
                .help("port/番号は自動導出。任意のSOP8ピン可").build());
        f.add(ConfigField.of("WS_COUNT", ConfigField.Type.INT).section(S).label("LED個数")
                .range(1, 64).build());
        f.add(ConfigField.of("NIGHTLIGHT_PERIOD_MS", ConfigField.Type.INT).section(S).label("明↔暗周期")
                .range(2, 60000).unit("ms").build());
        f.add(ConfigField.of("NIGHTLIGHT_INTERVAL_MS", ConfigField.Type.INT).section(S).label("周期後オフ")
                .range(0, 60000).unit("ms").build());
        f.add(ConfigField.of("NIGHTLIGHT_REFRESH_MS", ConfigField.Type.INT).section(S).label("送信リフレッシュ")
                .range(5, 1000).unit("ms").build());
        f.add(ConfigField.of("NIGHTLIGHT_BOOT_TEST_MS", ConfigField.Type.INT).section(S).label("起動セルフテスト")
                .range(0, 60000).unit("ms").help("配線切り分け用。0=無効").build());
        f.add(ConfigField.of("WS_ORDER", ConfigField.Type.ENUM).section(S).label("バイト並び")
                .option("WS_ORDER_GRB", "GRB (一般的WS2812)")
                .option("WS_ORDER_RGB", "RGB")
                .option("WS_ORDER_GRBW", "GRBW (RGBW)")
                .option("WS_ORDER_RGBW", "RGBW (SK6812)").build());
        f.add(ConfigField.of("WS_MAX_COLOR", ConfigField.Type.COLOR).section(S).label("最大時の色")
                .help("RGB系=0xRRGGBB / RGBW系=0xRRGGBBWW。桁はWS_ORDERに連動").build());

        // ---- WDT ----
        S = "ウォッチドッグ";
        f.add(bool("WDT_ENABLE", S, "IWDG有効").build());
        f.add(ConfigField.of("WDT_TIMEOUT_MS", ConfigField.Type.INT).section(S).label("タイムアウト")
                .range(100, 8190).unit("ms").help("ファーム制約で100..8190").build());

        return f;
    }

    private static ConfigField.Builder pin(String key, String section, String label) {
        return pin(key, section, label, PINS_ALL);
    }

    /** 能力で絞ったピン選択肢を持つフィールド（PWMはPINS_PWM、ADCはPINS_ADC 等）。 */
    private static ConfigField.Builder pin(String key, String section, String label, String[] allowed) {
        ConfigField.Builder b = ConfigField.of(key, ConfigField.Type.PIN).section(section).label(label);
        for (String p : allowed) b.option(p, p);
        return b;
    }

    private static void addUse(List<String[]> list, String pin, String label) {
        if (pin != null && !pin.isEmpty()) list.add(new String[]{pin, label});
    }

    private static boolean contains(String[] arr, String v) {
        for (String s : arr) if (s.equals(v)) return true;
        return false;
    }

    private static ConfigField.Builder bool(String key, String section, String label) {
        return ConfigField.of(key, ConfigField.Type.BOOL).section(section).label(label);
    }

    /** WS_ORDER に応じた色のhex桁数（RGBW=8 / RGB=6）。 */
    public static int colorHexDigits(String wsOrder) {
        return ("WS_ORDER_GRBW".equals(wsOrder) || "WS_ORDER_RGBW".equals(wsOrder)) ? 8 : 6;
    }

    /**
     * 相互制約の検証。GUIが編集後の値を反映した ConfigFile を渡す。
     */
    public static List<Issue> validate(ConfigFile cf) {
        List<Issue> out = new ArrayList<>();

        long pwmTop = cf.getLong("PWM_TOP", 4095);
        long range = cf.getLong("PWM_SPREAD_RANGE", 0);
        // ファームは PWM_SPREAD_SPECTRUM の有無に関わらず無条件で _Static_assert する
        if (pwmTop + range > 65535)
            out.add(new Issue(true, "PWM: PWM_TOP+PWM_SPREAD_RANGE (" + (pwmTop + range) + ") が 65535 を超えています"));
        if (range >= pwmTop)
            out.add(new Issue(true, "PWM: PWM_SPREAD_RANGE (" + range + ") は PWM_TOP (" + pwmTop + ") 未満にしてください"));

        long storeSize = cf.getLong("STORE_SIZE_BYTES", 2048);
        if (storeSize % 1024 != 0)
            out.add(new Issue(true, "ストア: STORE_SIZE_BYTES (" + storeSize + ") は 1024 の倍数にしてください"));

        long levels = cf.getLong("LEVELS", 64);
        long levelInit = cf.getLong("LEVEL_INIT", 32);
        if (levelInit > levels)
            out.add(new Issue(true, "調光段: LEVEL_INIT (" + levelInit + ") が LEVELS (" + levels + ") を超えています"));

        long warn = cf.getLong("WARN_TEMP_C", 80);
        long hyst = cf.getLong("WARN_TEMP_HYST_C", 8);
        if (hyst >= warn)
            out.add(new Issue(true, "温度: WARN_TEMP_HYST_C (" + hyst + ") は WARN_TEMP_C (" + warn + ") 未満にしてください"));
        long thrMax = cf.getLong("THERMAL_THROTTLE_MAX", 80);
        if (thrMax >= 100)
            out.add(new Issue(true, "温度: THERMAL_THROTTLE_MAX (" + thrMax + ") は 100 未満にしてください"));
        long slope = cf.getLong("TEMP_CAL_SLOPE_X100", -300);
        if (slope == 0)
            out.add(new Issue(true, "温度: TEMP_CAL_SLOPE_X100 は 0 にできません"));
        long dieTau = cf.getLong("DIE_TAU_MS", 30000);
        long warnPeriod = cf.getLong("WARN_TEMP_PERIOD_MS", 200);
        if (dieTau < warnPeriod)
            out.add(new Issue(true, "温度: DIE_TAU_MS (" + dieTau + ") は WARN_TEMP_PERIOD_MS (" + warnPeriod + ") 以上にしてください"));

        // WS_MAX_COLOR の桁数と WS_ORDER の整合
        String wsOrder = cf.getSymbol("WS_ORDER", "WS_ORDER_GRB");
        long color = cf.getLong("WS_MAX_COLOR", 0);
        int digits = colorHexDigits(wsOrder);
        long maxColor = (digits == 8) ? 0xFFFFFFFFL : 0xFFFFFFL;
        if (color > maxColor)
            out.add(new Issue(true, "常夜灯: WS_MAX_COLOR が WS_ORDER(" + wsOrder + ") の桁(" + digits + "hex)に収まりません"));

        // ---- 全機能横断のピン割当検証（firmware pins.h と対応）: 衝突 / 能力 / 存在 / SWIO ----
        boolean tempOn = cf.getLong("TEMP_PROTECT_ENABLE", 0) == 1;
        boolean tempExt = "TEMP_SOURCE_EXTERNAL".equals(cf.getSymbol("TEMP_SOURCE", ""));

        // 有効な機能のみ (pin, ラベル) を収集
        List<String[]> pinUse = new ArrayList<>();
        addUse(pinUse, cf.getSymbol("PWM_PIN", ""), "PWM(PWM_PIN)");
        addUse(pinUse, cf.getSymbol("ENC_A_PIN", ""), "エンコーダA(ENC_A_PIN)");
        addUse(pinUse, cf.getSymbol("ENC_B_PIN", ""), "エンコーダB(ENC_B_PIN)");
        addUse(pinUse, cf.getSymbol("ENC_SW_PIN", ""), "押しSW(ENC_SW_PIN)");
        if (cf.getLong("NIGHTLIGHT_ENABLE", 0) == 1)
            addUse(pinUse, cf.getSymbol("WS_DIN_PIN", ""), "常夜灯(WS_DIN_PIN)");
        if (tempOn && tempExt)
            addUse(pinUse, cf.getSymbol("TEMP_SENSE_PIN", ""), "外付け温度(TEMP_SENSE_PIN)");
        if (cf.getLong("WARN_LED_ENABLE", 0) == 1)
            addUse(pinUse, cf.getSymbol("WARN_LED_PIN", ""), "警告灯(WARN_LED_PIN)");

        // 衝突: 同一ピンに2機能以上
        Map<String, List<String>> byPin = new LinkedHashMap<>();
        for (String[] u : pinUse) byPin.computeIfAbsent(u[0], k -> new ArrayList<>()).add(u[1]);
        for (Map.Entry<String, List<String>> e : byPin.entrySet())
            if (e.getValue().size() > 1)
                out.add(new Issue(true, "ピン衝突: " + e.getKey() + " に " + String.join(" / ", e.getValue()) + " が同時割当(1ピン1機能)"));

        // 能力: PWM_PIN は PWM 可能ピン / TEMP_SENSE_PIN(有効時) は ADC 対応ピン
        String pwmPin = cf.getSymbol("PWM_PIN", "");
        if (!pwmPin.isEmpty() && !contains(PINS_PWM, pwmPin))
            out.add(new Issue(true, "PWM_PIN (" + pwmPin + ") は PWM 可能ピンではありません: " + String.join("/", PINS_PWM)));
        if (tempOn && tempExt) {
            String tp = cf.getSymbol("TEMP_SENSE_PIN", "");
            if (!tp.isEmpty() && !contains(PINS_ADC, tp))
                out.add(new Issue(true, "TEMP_SENSE_PIN (" + tp + ") は ADC 対応ピンではありません: " + String.join("/", PINS_ADC)));
        }

        // 存在: すべての割当ピンは SOP8 実在ピン / SWIO(PD1) は警告
        for (String[] u : pinUse) {
            if (!contains(PINS_ALL, u[0]))
                out.add(new Issue(true, u[1] + " のピン " + u[0] + " は SOP8 実在ピンではありません: " + String.join("/", PINS_ALL)));
            if (SWIO_PIN.equals(u[0]))
                out.add(new Issue(false, u[1] + " が SWIO(" + SWIO_PIN + ")を使用。書込/debugprintf と競合します"));
        }

        // ---- 警告（エラーではない） ----
        if (cf.getLong("DEBUG_LOG", 0) == 1)
            out.add(new Issue(false, "DEBUG_LOG=1 は開発時のみ。通常運用は 0 推奨(printfブロッキングで取りこぼし要因)"));
        if (tempOn && "TEMP_SOURCE_DIE".equals(cf.getSymbol("TEMP_SOURCE", "")))
            out.add(new Issue(false, "TEMP_SOURCE=ダイ推定 は誤遮断のおそれ。実測NTC(EXTERNAL)推奨"));

        return out;
    }
}
