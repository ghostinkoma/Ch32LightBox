package com.lightbox.studio.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * config.h を「行単位で」読み込み、{@code #define KEY VALUE} の VALUE トークンだけを
 * 差し替える編集器。コメント・enum定義行・空行・構造は一切変更しない（全文再生成しない）。
 *
 * <p>これにより設計解説コメントや記号定数（TEMP_SOURCE_DIE 等）が保全され、
 * 差分は「実際に変えた値の行」だけになる。</p>
 */
public final class ConfigFile {

    /** {@code   #define KEY   VALUE   /* comment *​/} を分解。値は空白を含まない単一トークン想定。 */
    private static final Pattern DEFINE = Pattern.compile(
            "^(\\s*#\\s*define\\s+)(\\w+)(\\s+)(\\S+)(.*)$");

    /** パース済みの値トークン（再出力のためのヒントを保持）。 */
    static final class Val {
        int lineIndex;
        String prefix; // 行頭〜値の直前（#define KEY と空白まで）
        String rest;   // 値の後ろ（コメント等）
        String rawToken;
        // 数値ヒント
        boolean numeric;
        int radix = 10;
        String suffix = ""; // u/U/l/L の並び
        boolean paren = false; // (-300) のような括弧付き
        long value;         // numeric のとき有効
    }

    private final List<String> lines = new ArrayList<>();
    private final Map<String, Val> defines = new LinkedHashMap<>();

    public static ConfigFile load(Path path) throws IOException {
        ConfigFile cf = new ConfigFile();
        List<String> raw = Files.readAllLines(path, StandardCharsets.UTF_8);
        cf.lines.addAll(raw);
        for (int i = 0; i < raw.size(); i++) {
            Matcher m = DEFINE.matcher(raw.get(i));
            if (!m.matches()) continue;
            String key = m.group(2);
            Val v = new Val();
            v.lineIndex = i;
            v.prefix = m.group(1) + m.group(2) + m.group(3);
            v.rest = m.group(5);
            v.rawToken = m.group(4);
            parseNumeric(v);
            // 同名が複数あっても最初を採用（本config.hは一意）
            cf.defines.putIfAbsent(key, v);
        }
        return cf;
    }

    private static void parseNumeric(Val v) {
        String t = v.rawToken.trim();
        if (t.startsWith("(") && t.endsWith(")")) {
            v.paren = true;
            t = t.substring(1, t.length() - 1).trim();
        }
        // 末尾の u/U/l/L を取り出す
        int end = t.length();
        while (end > 0 && "uUlL".indexOf(t.charAt(end - 1)) >= 0) end--;
        String core = t.substring(0, end);
        v.suffix = t.substring(end);
        String digits = core;
        boolean neg = false;
        if (digits.startsWith("+")) digits = digits.substring(1);
        else if (digits.startsWith("-")) { neg = true; digits = digits.substring(1); }
        int radix = 10;
        if (digits.startsWith("0x") || digits.startsWith("0X")) {
            radix = 16;
            digits = digits.substring(2);
        }
        boolean allDigits = !digits.isEmpty();
        for (int i = 0; i < digits.length(); i++) {
            if (Character.digit(digits.charAt(i), radix) < 0) { allDigits = false; break; }
        }
        if (allDigits) {
            try {
                long val = Long.parseLong(digits, radix);
                v.value = neg ? -val : val;
                v.radix = radix;
                v.numeric = true;
            } catch (NumberFormatException ignored) {
                v.numeric = false;
            }
        } else {
            v.numeric = false; // 記号定数（TEMP_SOURCE_EXTERNAL 等）やピン名
        }
    }

    // ---- 参照 ----

    public boolean has(String key) { return defines.containsKey(key); }

    public java.util.Set<String> keys() { return defines.keySet(); }

    /** 生トークン（未知の型や記号定数の取得に）。無ければ null。 */
    public String rawToken(String key) {
        Val v = defines.get(key);
        return v == null ? null : v.rawToken;
    }

    /** 数値を取得。数値でない/無ければ def。 */
    public long getLong(String key, long def) {
        Val v = defines.get(key);
        return (v != null && v.numeric) ? v.value : def;
    }

    /** 記号/ピン等のシンボル値（生トークンそのまま）。 */
    public String getSymbol(String key, String def) {
        Val v = defines.get(key);
        return v == null ? def : v.rawToken;
    }

    // ---- 変更 ----

    /** 数値を設定（元の u/L サフィックスと括弧付き負数の様式を維持）。 */
    public void setLong(String key, long value) {
        Val v = require(key);
        String core;
        if (v.radix == 16) {
            core = "0x" + Long.toHexString(value); // 幅指定は setColor で別途
        } else {
            core = Long.toString(value);
        }
        String token;
        if (value < 0) {
            // 負数は括弧付き（(-300) 様式）。サフィックスは付けない。
            token = "(" + core + ")";
        } else {
            token = core + v.suffix;
        }
        applyToken(v, token);
        v.value = value; v.numeric = true; v.paren = value < 0;
    }

    /** 色を 0x + 指定桁hex + サフィックスで設定。 */
    public void setColor(String key, long value, int hexDigits) {
        Val v = require(key);
        String hex = Long.toHexString(value & 0xFFFFFFFFL);
        while (hex.length() < hexDigits) hex = "0" + hex;
        if (hex.length() > hexDigits) hex = hex.substring(hex.length() - hexDigits);
        String suffix = v.suffix.isEmpty() ? "u" : v.suffix; // 元がu無しでもuを付けておく（符号なし色）
        applyToken(v, "0x" + hex + suffix);
        v.value = value; v.numeric = true; v.radix = 16;
    }

    /** 記号/ピン等をそのまま設定。 */
    public void setSymbol(String key, String token) {
        Val v = require(key);
        applyToken(v, token);
        v.numeric = false;
    }

    private void applyToken(Val v, String newToken) {
        v.rawToken = newToken;
        lines.set(v.lineIndex, v.prefix + newToken + v.rest);
    }

    private Val require(String key) {
        Val v = defines.get(key);
        if (v == null) throw new IllegalArgumentException("config.h に #define " + key + " がありません");
        return v;
    }

    public List<String> lines() { return lines; }

    public void save(Path path) throws IOException {
        // 末尾改行を維持しつつ書き出し
        String text = String.join("\n", lines);
        if (!text.endsWith("\n")) text = text + "\n";
        Files.write(path, text.getBytes(StandardCharsets.UTF_8));
    }
}
