package com.lightbox.studio.common;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * settings.properties への読み書き（全JAR共有）。
 *
 * <p>各ツール（menu / config / build / flash）はこの1ファイルを介して
 * プロジェクトパスやツールチェーンの場所を共有する。既定では
 * 実行JARと同じフォルダの settings.properties を使う。</p>
 */
public final class Settings {

    /** プロパティキー。 */
    public static final String PROJECT_DIR   = "project.dir";   // config.h / Makefile がある source/ 相当
    public static final String CH32FUN       = "ch32fun.dir";   // ch32fun.mk があるフォルダ
    public static final String MAKE          = "make.path";     // make 実行ファイル（空=PATH上のmake）
    public static final String GCC_BIN       = "gcc.bin.dir";   // riscv-none-elf-gcc のある bin フォルダ（空=PATH）
    public static final String MINICHLINK    = "minichlink.path"; // minichlink 実行ファイル
    public static final String MINICHLINK_CANDIDATES = "minichlink.candidates"; // ';'区切りの代替minichlink
    public static final String FLASH_ARTIFACT= "flash.artifact";  // 書込対象（既定 lightbox.bin）

    private final File file;
    private final Properties props = new Properties();

    public Settings(File file) {
        this.file = file;
        load();
    }

    /** 実行中JARの隣（見つからなければカレント）にある settings.properties を使う。 */
    public static Settings beside(Class<?> anchor) {
        File dir = jarDir(anchor);
        return new Settings(new File(dir, "settings.properties"));
    }

    /** anchor クラスを含むJAR（またはクラスフォルダ）のあるディレクトリ。 */
    public static File jarDir(Class<?> anchor) {
        try {
            File loc = new File(anchor.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            return loc.isFile() ? loc.getParentFile() : loc; // JARなら親、クラスフォルダならそれ自身
        } catch (Exception e) {
            return new File(".").getAbsoluteFile();
        }
    }

    public File file() { return file; }

    public synchronized void load() {
        props.clear();
        if (file.isFile()) {
            try (FileInputStream in = new FileInputStream(file)) {
                props.load(in);
            } catch (IOException e) {
                // 破損等は無視して空扱い（初回起動時など）
            }
        }
    }

    public synchronized void save() {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory()) parent.mkdirs();
            try (FileOutputStream out = new FileOutputStream(file)) {
                props.store(out, "LightBox Studio settings");
            }
        } catch (IOException e) {
            throw new RuntimeException("設定を保存できません: " + file + " (" + e.getMessage() + ")", e);
        }
    }

    public String get(String key, String def) {
        String v = props.getProperty(key);
        return (v == null || v.isEmpty()) ? def : v;
    }

    public String get(String key) { return get(key, ""); }

    public void set(String key, String value) {
        if (value == null) value = "";
        props.setProperty(key, value);
    }

    // ---- ユーザープロファイル側の恒久ストア（tools/ を消しても残る） ----

    /** ~/.lightboxstudio/settings.properties （クリーン再導入でも消えない実績の保存先）。 */
    public static File userGlobalFile() {
        return new File(new File(System.getProperty("user.home", "."), ".lightboxstudio"), "settings.properties");
    }

    /** グローバルストアから1件読む（無ければ ""）。 */
    public static String readGlobal(String key) {
        File f = userGlobalFile();
        if (!f.isFile()) return "";
        Properties p = new Properties();
        try (FileInputStream in = new FileInputStream(f)) { p.load(in); }
        catch (IOException e) { return ""; }
        String v = p.getProperty(key);
        return v == null ? "" : v;
    }

    /** グローバルストアへ1件書く（マージ）。失敗は無視（環境依存）。 */
    public static void writeGlobal(String key, String value) {
        File f = userGlobalFile();
        Properties p = new Properties();
        if (f.isFile()) {
            try (FileInputStream in = new FileInputStream(f)) { p.load(in); } catch (IOException ignored) {}
        }
        p.setProperty(key, value == null ? "" : value);
        try {
            File dir = f.getParentFile();
            if (dir != null && !dir.isDirectory()) dir.mkdirs();
            try (FileOutputStream out = new FileOutputStream(f)) { p.store(out, "LightBox Studio global"); }
        } catch (IOException ignored) {}
    }

    /**
     * project.dir を解決。未設定なら実行JARの位置から上位へ辿って
     * {@code source/config.h}（または {@code config.h}+{@code Makefile}）を探す。
     */
    public File projectDir() {
        String p = get(PROJECT_DIR);
        if (!p.isEmpty()) return new File(p);
        File found = autodetectProjectDir();
        if (found != null) return found;
        return new File(jarDir(Settings.class), "../../source");
    }

    /** JAR位置から上へ最大8階層、source/config.h または config.h+Makefile を探す。 */
    public static File autodetectProjectDir() {
        File dir = jarDir(Settings.class);
        for (int i = 0; i < 8 && dir != null; i++) {
            File srcCfg = new File(dir, "source/config.h");
            if (srcCfg.isFile()) return new File(dir, "source");
            if (new File(dir, "config.h").isFile() && new File(dir, "Makefile").isFile()) return dir;
            dir = dir.getParentFile();
        }
        return null;
    }
}
