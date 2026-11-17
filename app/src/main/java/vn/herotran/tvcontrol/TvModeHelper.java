package vn.herotran.tvcontrol;

import android.app.UiModeManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Nhan dien TV + Launcher.
 *
 * Kien truc that:
 *  - Cung 1 APK RemoteTV (vn.herotran.tvcontrol) cai ca tren DT va TV.
 *  - Tren TV, launcher com.ts.launcher dang GIU cong 8999 (TCP + UDP),
 *    nhan lenh cu (set_limit, stop_limit, power_*, go_home, volume_*, sync_*).
 *  - Vi vay Receiver moi trong APK nay KHONG duoc tranh cong 8999.
 *    No nghe cong MOI 9000 + poll Firebase, chi xu ly LENH MOI.
 *  - DT gui lenh moi di ca 2 cong 8999 + 9000, ben nao hieu thi chay.
 */
public class TvModeHelper {
    private static final String TAG = "TvModeHelper";
    public static final String LAUNCHER_PKG = "com.ts.launcher";
    public static final String LAUNCHER_DIR = "/sdcard/com.ts.launcher";

    /** TV that su? Dua tren UiMode + Leanback + kich thuoc man hinh. */
    public static boolean isTvDevice(Context ctx) {
        if (ctx == null) return false;
        try {
            UiModeManager um = (UiModeManager) ctx.getSystemService(Context.UI_MODE_SERVICE);
            if (um != null && um.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) return true;
        } catch (Exception ignored) {}
        try {
            PackageManager pm = ctx.getPackageManager();
            if (pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) return true;
            if (pm.hasSystemFeature("android.hardware.type.television")) return true;
        } catch (Exception ignored) {}
        return false;
    }

    /** Launcher co dang cai tren may nay khong? */
    public static boolean isLauncherInstalled(Context ctx) {
        if (ctx == null) return false;
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                ctx.getPackageManager().getPackageInfo(LAUNCHER_PKG, android.content.pm.PackageManager.PackageInfoFlags.of(0));
            } else {
                ctx.getPackageManager().getPackageInfo(LAUNCHER_PKG, 0);
            }
            return true;
        } catch (Exception e) {
            // Launcher co the la app he thong / ten goi khac, kiem tra them thu muc config
            return new File(LAUNCHER_DIR).exists();
        }
    }

    /**
     * Launcher co dang GIU cong 8999 tren localhost khong?
     * Neu co -> che do "dong hanh" (companion): Receiver chi xu ly lenh MOI,
     * lenh cu de launcher xu ly, tranh xu ly trung.
     * Neu khong -> che do "doc lap" (standalone): Receiver tu xu ly CA lenh cu + moi,
     * de truong hop TV chi cai RemoteTV, khong co launcher chay.
     * Ham nay chay tren thread phu, timeout ngan.
     */
    public static boolean isLauncherListening() {
        Socket s = null;
        try {
            s = new Socket();
            s.connect(new InetSocketAddress("127.0.0.1", LanHelper.PORT), 400);
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            if (s != null) try { s.close(); } catch (Exception ignored) {}
        }
    }

    /** Ghi file config cho launcher doc (de launcher + Receiver cung hieu cau hinh). */
    public static void writeLauncherFile(String fileName, String content) {
        try {
            File dir = new File(LAUNCHER_DIR);
            if (!dir.exists()) dir.mkdirs();
            File f = new File(dir, fileName);
            java.io.FileWriter w = new java.io.FileWriter(f, false);
            w.write(content);
            w.flush();
            w.close();
            Log.i(TAG, "Wrote launcher file: " + f.getAbsolutePath());
        } catch (Exception e) {
            Log.w(TAG, "writeLauncherFile fail: " + e.getMessage());
        }
    }
}
