package com.lightbox.studio.common;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * ZIP 展開（標準API のみ）。zip-slip を防止し、エントリ数ベースで進捗通知する。
 */
public final class Zip {

    public interface Progress { void update(int doneEntries, int totalEntries, String name); }

    private Zip() {}

    /** zip 内のエントリ総数を数える（進捗表示用）。 */
    public static int countEntries(Path zip) throws IOException {
        int n = 0;
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip))) {
            while (zis.getNextEntry() != null) { n++; zis.closeEntry(); }
        }
        return n;
    }

    /**
     * zip を destDir 直下へ展開。戻り値は「zip直下の唯一のトップフォルダ名」（無ければ null）。
     */
    public static String extract(Path zip, Path destDir, Progress p) throws IOException {
        Files.createDirectories(destDir);
        Path destCanon = destDir.toAbsolutePath().normalize();
        int total = countEntries(zip);
        int done = 0;
        String topLevel = null;
        boolean topLevelUnique = true;

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry e;
            byte[] buf = new byte[64 * 1024];
            while ((e = zis.getNextEntry()) != null) {
                String name = e.getName();
                Path out = destCanon.resolve(name).normalize();
                if (!out.startsWith(destCanon)) throw new IOException("不正なZIPエントリ(zip-slip): " + name);

                // トップフォルダ名の推定
                String first = name.replace('\\', '/');
                int slash = first.indexOf('/');
                String top = slash >= 0 ? first.substring(0, slash) : first;
                if (topLevel == null) topLevel = top;
                else if (!topLevel.equals(top)) topLevelUnique = false;

                if (e.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    if (out.getParent() != null) Files.createDirectories(out.getParent());
                    copy(zis, out, buf); // ストリームは閉じない(closeEntryで次エントリへ)
                }
                done++;
                if (p != null) p.update(done, total, name);
                zis.closeEntry();
            }
        }
        return topLevelUnique ? topLevel : null;
    }

    private static void copy(ZipInputStream zis, Path out, byte[] buf) throws IOException {
        try (java.io.OutputStream os = Files.newOutputStream(out)) {
            int n;
            while ((n = zis.read(buf)) >= 0) os.write(buf, 0, n);
        }
    }
}
