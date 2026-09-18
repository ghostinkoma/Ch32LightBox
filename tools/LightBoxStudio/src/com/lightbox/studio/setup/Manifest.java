package com.lightbox.studio.setup;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * ダウンロードするツールチェーン部品の定義（既定値は埋め込み、
 * dist/toolchain.manifest.properties があれば上書き）。Windows x64 向け。
 */
public final class Manifest {

    /** 1部品。url が空なら「ダウンロードせず内部で調達」（minichlink=検出コピー）。 */
    public static final class Comp {
        public final String name;   // gcc / make / ch32fun / minichlink
        public final String url;    // ダウンロードURL（空可）
        public final String tail;   // 展開後トップフォルダからの相対（bin / ch32fun 等）
        public final String label;  // 表示名
        public Comp(String name, String url, String tail, String label) {
            this.name = name; this.url = url; this.tail = tail; this.label = label;
        }
    }

    // 既定URL（到達確認済み: 2026-09）
    private static final String DEF_GCC =
        "https://github.com/xpack-dev-tools/riscv-none-elf-gcc-xpack/releases/download/v14.2.0-3/xpack-riscv-none-elf-gcc-14.2.0-3-win32-x64.zip";
    private static final String DEF_MAKE =
        "https://github.com/xpack-dev-tools/windows-build-tools-xpack/releases/download/v4.4.1-3/xpack-windows-build-tools-4.4.1-3-win32-x64.zip";
    private static final String DEF_CH32FUN =
        "https://github.com/cnlohr/ch32fun/archive/refs/heads/master.zip";
    // ポータブルJRE（Adoptium Temurin 17 LTS, Windows x64, 約44MB, インストーラ不要のzip）
    private static final String DEF_JRE =
        "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.13%2B11/OpenJDK17U-jre_x64_windows_hotspot_17.0.13_11.zip";

    private final Properties p = new Properties();

    /** dist（JARの隣）にある manifest を読み込む（無ければ既定のみ）。 */
    public static Manifest load(File appDir) {
        Manifest m = new Manifest();
        File f = new File(appDir, "toolchain.manifest.properties");
        if (f.isFile()) {
            try (FileInputStream in = new FileInputStream(f)) { m.p.load(in); }
            catch (IOException ignored) {}
        }
        return m;
    }

    private String v(String key, String def) {
        String s = p.getProperty(key);
        return (s == null) ? def : s.trim();
    }

    public List<Comp> components() {
        List<Comp> list = new ArrayList<>();
        list.add(new Comp("gcc",      v("gcc.url", DEF_GCC),        v("gcc.tail", "bin"),      "RISC-V GCC (xpack riscv-none-elf)"));
        list.add(new Comp("make",     v("make.url", DEF_MAKE),      v("make.tail", "bin"),     "make (xpack windows-build-tools)"));
        list.add(new Comp("ch32fun",  v("ch32fun.url", DEF_CH32FUN),v("ch32fun.tail", "ch32fun"), "ch32fun SDK"));
        // minichlink: 既定URLなし → 内部で検出コピー（url指定があればDL）
        list.add(new Comp("minichlink", v("minichlink.url", ""),    v("minichlink.tail", ""),  "minichlink (書込ツール)"));
        return list;
    }

    /** make 実行ファイル名（tail 配下）。 */
    public String makeExe() { return v("make.exe", "make.exe"); }
    /** minichlink 実行ファイル名。 */
    public String minichlinkExe() { return v("minichlink.exe", "minichlink.exe"); }
    /** ポータブルJRE の zip URL。 */
    public String jreUrl() { return v("jre.url", DEF_JRE); }
}
