package com.lightbox.studio.common;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 進捗コールバック付きの HTTP(S) ダウンローダ（標準API のみ）。
 * GitHub リリースのリダイレクト（github.com → objects.githubusercontent.com）に追従する。
 */
public final class Downloader {

    /** 進捗通知。total<0 は不明。 */
    public interface Progress { void update(long readBytes, long totalBytes); }

    private Downloader() {}

    /**
     * url を dest へ保存。進捗を p に通知。戻り値はダウンロードした総バイト数。
     */
    public static long download(String url, Path dest, Progress p) throws IOException {
        String current = url;
        for (int redirect = 0; redirect < 6; redirect++) {
            HttpURLConnection conn = (HttpURLConnection) new URL(current).openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(60000);
            conn.setRequestProperty("User-Agent", "LightBoxStudio-Setup");
            conn.setRequestProperty("Accept", "*/*");
            int code = conn.getResponseCode();
            if (code / 100 == 3) { // リダイレクト
                String loc = conn.getHeaderField("Location");
                conn.disconnect();
                if (loc == null) throw new IOException("リダイレクト先が空です (" + code + ")");
                current = loc.startsWith("http") ? loc : new URL(new URL(current), loc).toString();
                continue;
            }
            if (code / 100 != 2) {
                conn.disconnect();
                throw new IOException("HTTP " + code + " : " + current);
            }
            long total = conn.getContentLengthLong();
            Path parent = dest.getParent();
            if (parent != null) Files.createDirectories(parent);
            long read = 0;
            byte[] buf = new byte[64 * 1024];
            try (InputStream in = conn.getInputStream();
                 OutputStream out = Files.newOutputStream(dest)) {
                int n;
                long lastNotify = 0;
                if (p != null) p.update(0, total);
                while ((n = in.read(buf)) >= 0) {
                    out.write(buf, 0, n);
                    read += n;
                    if (p != null && (read - lastNotify >= 256 * 1024)) { p.update(read, total); lastNotify = read; }
                }
                if (p != null) p.update(read, total);
            } finally {
                conn.disconnect();
            }
            return read;
        }
        throw new IOException("リダイレクトが多すぎます: " + url);
    }
}
