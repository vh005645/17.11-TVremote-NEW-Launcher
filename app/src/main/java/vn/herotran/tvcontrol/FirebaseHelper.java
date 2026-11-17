package vn.herotran.tvcontrol;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;

/**
 * Giao tiếp với Firebase Realtime Database qua REST API thuần Java (Zero-Dependency).
 * Cấu hình URL và Secret lấy động từ PrefsManager.
 */
public class FirebaseHelper {
    private static final String TAG = "FirebaseHelper";

    public interface Callback {
        void onSuccess(String response);
        void onError(String error);
    }

    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * Gửi lệnh điều khiển lên Cloud cho TV nhận.
     */
    public static void sendCommand(Context ctx, final String action, final int minutes, final Callback callback) {
        long timestamp = System.currentTimeMillis();
        String jsonPayload = "{\"action\":\"" + action + "\",\"minutes\":" + minutes + ",\"timestamp\":" + timestamp + "}";
        executeRequest(ctx, "PUT", "/tv_control/command.json", jsonPayload, 8000, "Chưa cấu hình Firebase URL hoặc Secret Key!", callback);
    }

    /**
     * Kiểm tra trạng thái TV từ Cloud.
     */
    public static void getTvStatus(Context ctx, final Callback callback) {
        executeRequest(ctx, "GET", "/tv_control/status.json", null, 5000, "Chưa cấu hình Cloud", callback);
    }

    // ================= MỚI: lệnh mở rộng (giữ nguyên sendCommand cũ) =================

    /**
     * Gửi lệnh kèm thêm dữ liệu tuỳ ý (message, ...). Dùng JSONObject để escape
     * đúng tiếng Việt, dấu ngoặc kép, xuống dòng trong tin nhắn.
     * Ví dụ: sendCommandWithData(ctx, "show_message", {"message":"..."}, cb)
     */
    public static void sendCommandWithData(Context ctx, final String action, final JSONObject extra, final Callback callback) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("action", action);
            obj.put("minutes", 0);
            obj.put("timestamp", System.currentTimeMillis());
            if (extra != null) {
                java.util.Iterator<String> keys = extra.keys();
                while (keys.hasNext()) {
                    String k = keys.next();
                    obj.put(k, extra.get(k));
                }
            }
            executeRequest(ctx, "PUT", "/tv_control/command.json", obj.toString(), 8000,
                    "Chưa cấu hình Firebase URL hoặc Secret Key!", callback);
        } catch (Exception e) {
            postError(callback, e.getMessage() != null ? e.getMessage() : "Lỗi tạo lệnh");
        }
    }

    /** Gửi tin nhắn lên TV qua Cloud. TV hiển thị: nền tối 50%, chữ trắng. */
    public static void sendTvMessage(Context ctx, final String message, final Callback callback) {
        try {
            JSONObject extra = new JSONObject();
            extra.put("message", message);
            sendCommandWithData(ctx, "show_message", extra, callback);
        } catch (Exception e) {
            postError(callback, e.getMessage());
        }
    }

    /** Xoá / ẩn tin nhắn đang hiện trên TV. */
    public static void clearTvMessage(Context ctx, final Callback callback) {
        sendCommandWithData(ctx, "hide_message", null, callback);
    }

    /** Khoá màn hình TV: màn hình đen, hiện chữ "Tắt TV ngay !". */
    public static void lockScreen(Context ctx, final Callback callback) {
        try {
            JSONObject extra = new JSONObject();
            extra.put("message", "Tắt TV ngay !");
            sendCommandWithData(ctx, "lock_screen", extra, callback);
        } catch (Exception e) {
            postError(callback, e.getMessage());
        }
    }

    /** Mở khoá màn hình TV. */
    public static void unlockScreen(Context ctx, final Callback callback) {
        sendCommandWithData(ctx, "unlock_screen", null, callback);
    }

    /** Mở khoá giới hạn thời gian: huỷ giới hạn, TV xem không giới hạn. */
    public static void unlockLimit(Context ctx, final Callback callback) {
        sendCommandWithData(ctx, "unlock_limit", null, callback);
    }

    /**
     * Lấy ảnh chụp màn hình TV mà TV đã đẩy lên Cloud.
     * TV (sau khi nhận lệnh take_screenshot) ghi vào /tv_control/screenshot.json:
     * {"image_base64":"/9j/...","timestamp":...}
     * Trả về chuỗi JSON thô, MainActivity tự parse + kiểm tra timestamp.
     */
    public static void getScreenshot(Context ctx, final Callback callback) {
        executeRequest(ctx, "GET", "/tv_control/screenshot.json", null, 5000, "Chưa cấu hình Cloud", callback);
    }

    private static void executeRequest(
            Context ctx,
            final String method,
            final String path,
            final String jsonPayload,
            final int timeoutMs,
            final String emptyCredMsg,
            final Callback callback) {
        PrefsManager prefs = new PrefsManager(ctx);
        final String baseUrl = cleanUrl(prefs.getFirebaseUrl());
        final String secret = prefs.getFirebaseSecret();

        if (TextUtils.isEmpty(baseUrl) || TextUtils.isEmpty(secret)) {
            postError(callback, emptyCredMsg);
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpsURLConnection conn = null;
                try {
                    URL url = new URL(baseUrl + path + "?auth=" + secret);
                    conn = (HttpsURLConnection) url.openConnection();
                    conn.setRequestMethod(method);
                    conn.setConnectTimeout(timeoutMs);
                    conn.setReadTimeout(timeoutMs);

                    if (jsonPayload != null) {
                        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                        conn.setDoOutput(true);
                        byte[] postData = jsonPayload.getBytes("UTF-8");
                        try (OutputStream os = conn.getOutputStream()) {
                            os.write(postData);
                            os.flush();
                        }
                    }

                    int responseCode = conn.getResponseCode();
                    if (responseCode >= 200 && responseCode < 300) {
                        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = in.readLine()) != null) {
                            response.append(line);
                        }
                        in.close();
                        postSuccess(callback, response.toString());
                    } else {
                        postError(callback, "HTTP error: " + responseCode);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Firebase request error (" + method + " " + path + ")", e);
                    postError(callback, e.getMessage() != null ? e.getMessage() : "Lỗi kết nối mạng");
                } finally {
                    if (conn != null) {
                        conn.disconnect();
                    }
                }
            }
        }).start();
    }

    private static String cleanUrl(String url) {
        if (url == null) return "";
        url = url.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    private static void postSuccess(final Callback cb, final String res) {
        if (cb != null) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    cb.onSuccess(res);
                }
            });
        }
    }

    // ================= TV RECEIVER: doc lenh + day anh =================

    /** Receiver tren TV poll node lenh nay (cung node DT gui len). */
    public static void getCommand(Context ctx, final Callback callback) {
        executeRequest(ctx, "GET", "/tv_control/command.json", null, 5000, "Chưa cấu hình Cloud", callback);
    }

    /**
     * TV day anh chup man hinh len Cloud de DT poll ve.
     * Body: {"image_base64":"...","timestamp":...}
     * callback co the null (Receiver goi fire-and-forget).
     */
    public static void putScreenshot(Context ctx, final String base64, final long timestamp, final Callback callback) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("image_base64", base64 != null ? base64 : "");
            obj.put("timestamp", timestamp);
            executeRequest(ctx, "PUT", "/tv_control/screenshot.json", obj.toString(), 15000,
                    "Chưa cấu hình Cloud", callback != null ? callback : new Callback() {
                        @Override public void onSuccess(String r) {}
                        @Override public void onError(String e) {}
                    });
        } catch (Exception e) {
            if (callback != null) postError(callback, e.getMessage());
        }
    }

    private static void postError(final Callback cb, final String err) {
        if (cb != null) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    cb.onError(err);
                }
            });
        }
    }
}
