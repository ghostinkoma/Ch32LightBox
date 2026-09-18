package com.lightbox.studio.setup;

import java.io.File;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 期限・深さ制限付きのファイル探索（minichlink 等を「ユーザーに探させず」内部で見つける用）。
 * システムフォルダはスキップして時間を抑える。
 */
public final class FileScan {

    private FileScan() {}

    private static final String[] SKIP = {
        "windows", "$recycle.bin", "system volume information", "programdata",
        "$sysreset", "recovery", "perflogs", "node_modules", ".git"
    };

    /**
     * fileName に一致するファイルを、roots 配下から深さ maxDepth・期限 deadlineMs 内で探す。
     * excludeDir 配下（自分のツールフォルダ等）は探索しない。
     * @return 最初に見つかったファイル、無ければ null
     */
    public static File find(String fileName, File[] roots, int maxDepth, long deadlineMs, File excludeDir) {
        java.util.List<File> all = findAll(fileName, roots, maxDepth, deadlineMs, excludeDir, 1);
        return all.isEmpty() ? null : all.get(0);
    }

    /**
     * fileName に一致するファイルを最大 limit 件まで集める（重複パスは除く）。
     */
    public static java.util.List<File> findAll(String fileName, File[] roots, int maxDepth,
                                               long deadlineMs, File excludeDir, int limit) {
        java.util.List<File> out = new java.util.ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        String excl = canon(excludeDir);
        // ドライブごとに時間を配分（大きな C:\Users で使い切って D: 未走査になるのを防ぐ）
        int nRoots = 0;
        for (File r : roots) if (r != null && r.isDirectory()) nRoots++;
        long perRoot = nRoots > 0 ? Math.max(4000, deadlineMs / nRoots) : deadlineMs;
        for (File root : roots) {
            if (root == null || !root.isDirectory()) continue;
            long rootEnd = System.currentTimeMillis() + perRoot;
            Deque<File> stack = new ArrayDeque<>();
            Deque<Integer> depth = new ArrayDeque<>();
            stack.push(root); depth.push(0);
            while (!stack.isEmpty()) {
                if (System.currentTimeMillis() > rootEnd || out.size() >= limit) break;
                File dir = stack.pop();
                int d = depth.pop();
                if (excl != null && canon(dir).startsWith(excl)) continue; // 自分のフォルダは除外
                File[] kids = dir.listFiles();
                if (kids == null) continue;
                for (File k : kids) {
                    if (k.isFile()) {
                        if (k.getName().equalsIgnoreCase(fileName)) {
                            String c = canon(k);
                            if (seen.add(c)) { out.add(k); if (out.size() >= limit) return out; }
                        }
                    } else if (k.isDirectory() && d < maxDepth && !skip(k.getName())) {
                        stack.push(k); depth.push(d + 1);
                    }
                }
            }
            if (out.size() >= limit) break;
        }
        return out;
    }

    private static String canon(File f) {
        if (f == null) return null;
        try { return f.getCanonicalPath(); } catch (Exception e) { return f.getAbsolutePath(); }
    }

    private static boolean skip(String name) {
        String n = name.toLowerCase();
        for (String s : SKIP) if (n.equals(s)) return true;
        return false;
    }

    /** 探索の起点（ユーザーホーム→各ドライブルート。canonicalで重複除去）。 */
    public static File[] defaultRoots() {
        java.util.LinkedHashMap<String, File> roots = new java.util.LinkedHashMap<>();
        String home = System.getProperty("user.home");
        if (home != null) roots.put(canon(new File(home)), new File(home));
        File[] fsRoots = File.listRoots();
        if (fsRoots != null) for (File r : fsRoots) {
            String c = canon(r);
            if (!roots.containsKey(c)) roots.put(c, r);
        }
        return roots.values().toArray(new File[0]);
    }
}
