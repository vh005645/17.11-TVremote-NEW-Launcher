package vn.herotran.tvcontrol;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;

/**
 * Chup anh man hinh tren TV.
 * Thu tu uu tien:
 *  1) Lenh shell "screencap -p <file>" (thuong chay duoc tren TV / box neu app co quyen shell
 *     hoac launcher chay cung UID). Day la cach don gian + chup duoc TOAN man hinh,
 *     ke ca video dang phat, khong can MediaProjection xin quyen tay.
 *  2) Neu that bai -> tra null, ben DT se bao "TV khong chup duoc anh".
 *
 * Anh tra ve: JPEG quality 70, chieu dai nhat ~1280px, Base64 NO_WRAP,
 * de duoi ~500KB, DT giai ma nhanh.
 */
public class TvScreenshotHelper {
    private static final String TAG = "TvScreenshot";
    private static final String TMP_PATH = "/sdcard/com.ts.launcher/tv_shot_tmp.png";

    public static String captureBase64() {
        try {
            // Dam bao thu muc ton tai
            File dir = new File("/sdcard/com.ts.launcher");
            if (!dir.exists()) dir.mkdirs();
            File tmp = new File(TMP_PATH);
            if (tmp.exists()) tmp.delete();

            // screencap -p <file>
            Process p = Runtime.getRuntime().exec(new String[]{"screencap", "-p", TMP_PATH});
            int exit = p.waitFor();
            // Cho file ghi xong
            for (int i = 0; i < 10 && !tmp.exists(); i++) {
                try { Thread.sleep(100); } catch (Exception ignored) {}
            }
            if (exit != 0 && !tmp.exists()) {
                Log.w(TAG, "screencap exit=" + exit + " no file");
                return null;
            }
            if (!tmp.exists() || tmp.length() == 0) return null;

            byte[] pngBytes;
            FileInputStream fis = new FileInputStream(tmp);
            try {
                pngBytes = new byte[(int) tmp.length()];
                int read = 0;
                while (read < pngBytes.length) {
                    int n = fis.read(pngBytes, read, pngBytes.length - read);
                    if (n < 0) break;
                    read += n;
                }
            } finally {
                try { fis.close(); } catch (Exception ignored) {}
                try { tmp.delete(); } catch (Exception ignored) {}
            }

            Bitmap bmp = BitmapFactory.decodeByteArray(pngBytes, 0, pngBytes.length);
            if (bmp == null) return null;
            // Giam co neu qua lon
            int maxSide = Math.max(bmp.getWidth(), bmp.getHeight());
            Bitmap out = bmp;
            if (maxSide > 1280) {
                float scale = 1280f / maxSide;
                int w = Math.max(1, (int) (bmp.getWidth() * scale));
                int h = Math.max(1, (int) (bmp.getHeight() * scale));
                out = Bitmap.createScaledBitmap(bmp, w, h, true);
                if (out != bmp) bmp.recycle();
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            out.compress(Bitmap.CompressFormat.JPEG, 70, baos);
            if (out != bmp) out.recycle();
            byte[] jpg = baos.toByteArray();
            return Base64.encodeToString(jpg, Base64.NO_WRAP);
        } catch (Exception e) {
            Log.w(TAG, "capture fail: " + e.getMessage());
            return null;
        }
    }
}
