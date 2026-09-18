package com.lightbox.studio.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * config.h の 1 マクロを表すメタデータ（型・範囲・説明・依存）。
 *
 * <p>スキーマ駆動でGUIを自動生成するための定義。値そのものは
 * {@link ConfigFile} が保持し、本クラスは「どう見せ・どう検証するか」を持つ。</p>
 */
public final class ConfigField {

    public enum Type {
        INT,     // 整数（min/max）
        BOOL,    // 0/1
        ENUM,    // 記号定数の中から1つ（TEMP_SOURCE, WS_ORDER 等）
        PIN,     // ピン名（PA1, PC4 …）
        PORT,    // GPIOポート（GPIOA/GPIOC/GPIOD）
        COLOR    // 0xRRGGBB / 0xRRGGBBWW
    }

    public final String key;      // #define 名
    public final String section;  // グループ見出し
    public final String label;    // 表示名（日本語）
    public final Type type;
    public final long min;        // INT の下限
    public final long max;        // INT の上限
    public final String unit;     // 単位表示（ms, % 等, 無ければ ""）
    public final String help;     // ツールチップ/説明
    public final boolean advanced; // 上級者向け（既定は折りたたみ/ロック）
    /** ENUM/PORT: raw値(トークン) -> 表示ラベル。 */
    public final Map<String, String> options;

    private ConfigField(Builder b) {
        this.key = b.key;
        this.section = b.section;
        this.label = b.label;
        this.type = b.type;
        this.min = b.min;
        this.max = b.max;
        this.unit = b.unit;
        this.help = b.help;
        this.advanced = b.advanced;
        this.options = b.options;
    }

    public static Builder of(String key, Type type) { return new Builder(key, type); }

    public static final class Builder {
        private final String key;
        private final Type type;
        private String section = "";
        private String label = "";
        private long min = 0, max = Integer.MAX_VALUE;
        private String unit = "";
        private String help = "";
        private boolean advanced = false;
        private Map<String, String> options = new LinkedHashMap<>();

        Builder(String key, Type type) { this.key = key; this.type = type; }

        public Builder section(String s) { this.section = s; return this; }
        public Builder label(String s) { this.label = s; return this; }
        public Builder range(long lo, long hi) { this.min = lo; this.max = hi; return this; }
        public Builder unit(String u) { this.unit = u; return this; }
        public Builder help(String h) { this.help = h; return this; }
        public Builder advanced() { this.advanced = true; return this; }
        public Builder option(String raw, String label) { this.options.put(raw, label); return this; }
        public ConfigField build() { return new ConfigField(this); }
    }
}
