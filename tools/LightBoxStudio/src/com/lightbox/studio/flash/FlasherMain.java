package com.lightbox.studio.flash;

import com.lightbox.studio.common.Settings;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * 書込実行（ヘッドレス／バックグラウンドJAR）。
 *
 * <p>WCH-LinkE + minichlink で CH32V003 へ書き込む（設定した minichlink を直接使用）。
 * make/ツールチェーン不要で書込だけ可能。前提: WCH-LinkE の WinUSB ドライバ導入済み。</p>
 */
public final class FlasherMain {

    public static void main(String[] args) {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        Settings st = Settings.beside(FlasherMain.class);
        int code = FlashOp.run(st, out);
        System.exit(code);
    }
}
