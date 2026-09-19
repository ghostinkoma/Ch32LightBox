package com.lightbox.studio.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * ConfigSchema.validate() の自己テスト（JUnit 非依存の素の main）。
 *
 * <p>base config.h を読み、値を差し替えた一時 config を作って検証結果を確認する:
 * (1) 既定=エラー無し (2) ピン衝突検出 (3) PWM能力違反 (4) ADC能力違反 (5) SWIO警告。</p>
 *
 * <p>使い方: {@code java -cp build/classes:test-classes com.lightbox.studio.config.ConfigSchemaCheck <base config.h>}</p>
 */
public final class ConfigSchemaCheck {

    private static int failures = 0;

    public static void main(String[] args) throws IOException {
        if (args.length < 1) { System.err.println("usage: ConfigSchemaCheck <config.h>"); System.exit(2); }
        Path base = Path.of(args[0]);
        List<String> baseLines = Files.readAllLines(base, StandardCharsets.UTF_8);

        // (1) 既定: エラー無し
        List<ConfigSchema.Issue> is0 = validateVariant(baseLines);
        check("既定構成はエラー無し", errorCount(is0) == 0, is0);

        // (2) 衝突: WS_DIN_PIN を ENC_B(PC1) と同じに
        List<ConfigSchema.Issue> is1 = validateVariant(replace(baseLines, "WS_DIN_PIN", "PC1"));
        check("WS_DIN_PIN=PC1(=ENC_B) でピン衝突を検出", hasError(is1, "ピン衝突") && hasError(is1, "PC1"), is1);

        // (3) PWM能力: PWM_PIN を非PWMピン(PA2)に
        List<ConfigSchema.Issue> is2 = validateVariant(replace(baseLines, "PWM_PIN", "PA2"));
        check("PWM_PIN=PA2 で PWM能力違反を検出", hasError(is2, "PWM_PIN") && hasError(is2, "PWM 可能"), is2);

        // (4) ADC能力: 外付け温度を有効化し 非ADCピン(PC1)に
        List<String> v3 = replace(replace(baseLines, "TEMP_PROTECT_ENABLE", "1"), "TEMP_SENSE_PIN", "PC1");
        List<ConfigSchema.Issue> is3 = validateVariant(v3);
        check("外付け温度=PC1 で ADC能力違反を検出", hasError(is3, "TEMP_SENSE_PIN") && hasError(is3, "ADC"), is3);

        // (5) SWIO警告: 警告灯を有効化し PD1 に(衝突なし=警告のみ)
        List<String> v4 = replace(replace(baseLines, "WARN_LED_ENABLE", "1"), "WARN_LED_PIN", "PD1");
        List<ConfigSchema.Issue> is4 = validateVariant(v4);
        check("警告灯=PD1 で SWIO 警告(エラーではない)", hasWarn(is4, "SWIO") && !hasError(is4, "PD1"), is4);

        System.out.println(failures == 0 ? "ALL PASS" : ("FAILURES=" + failures));
        System.exit(failures == 0 ? 0 : 1);
    }

    /** 「#define KEY ...」の値トークンを newVal に置換した行リストを返す。 */
    private static List<String> replace(List<String> lines, String key, String newVal) {
        List<String> out = new ArrayList<>(lines.size());
        for (String ln : lines) {
            String t = ln.replaceFirst("^(\\s*#\\s*define\\s+" + key + "\\s+)\\S+", "$1" + newVal);
            out.add(t);
        }
        return out;
    }

    private static List<ConfigSchema.Issue> validateVariant(List<String> lines) throws IOException {
        Path tmp = Files.createTempFile("cfgcheck", ".h");
        Files.write(tmp, String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
        ConfigFile cf = ConfigFile.load(tmp);
        Files.deleteIfExists(tmp);
        return ConfigSchema.validate(cf);
    }

    private static int errorCount(List<ConfigSchema.Issue> is) {
        int n = 0; for (ConfigSchema.Issue i : is) if (i.error) n++; return n;
    }
    private static boolean hasError(List<ConfigSchema.Issue> is, String sub) {
        for (ConfigSchema.Issue i : is) if (i.error && i.message.contains(sub)) return true; return false;
    }
    private static boolean hasWarn(List<ConfigSchema.Issue> is, String sub) {
        for (ConfigSchema.Issue i : is) if (!i.error && i.message.contains(sub)) return true; return false;
    }

    private static void check(String name, boolean ok, List<ConfigSchema.Issue> is) {
        System.out.println((ok ? "PASS " : "FAIL ") + name);
        if (!ok) {
            failures++;
            for (ConfigSchema.Issue i : is) System.out.println("      [" + (i.error ? "ERR " : "WARN") + "] " + i.message);
        }
    }
}
