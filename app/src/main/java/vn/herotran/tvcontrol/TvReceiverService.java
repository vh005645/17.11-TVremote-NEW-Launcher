package vn.herotran.tvcontrol;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

/**
 * Receiver chay tren TV (trong chinh APK RemoteTV).
 *
 * 1) KHONG tranh cong 8999 voi launcher. Nghe cong MOI 9000 (TCP) + poll Firebase.
 * 2) Chi xu ly LENH MOI: take_screenshot, unlock_limit, lock_screen, unlock_screen,
 *    show_message, hide_message. LENH CU de launcher xu ly.
 *    - Ngoai le: neu launcher KHONG chay (khong ket noi duoc 127.0.0.1:8999),
 *      Receiver tu xu ly CA lenh cu (standalone) de TV chi cai RemoteTV van chay duoc.
 * 3) unlock_limit: ngoai viec xoa timer cua minh, con forward stop_limit ve 127.0.0.1:8999
 *    de launcher cung huy dem gio cua no. Neu khong lam buoc nay, launcher van tat TV
 *    theo gioi han cu du DT da bam "Mo khoa".
 * 4) sync_*: ghi vao /sdcard/com.ts.launcher/*.json + luu vao Prefs, de launcher + Receiver
 *    cung hieu cau hinh moi.
 */
public class TvReceiverService extends Service {
    private static final String TAG = "TvReceiver";
    public static final String ACTION_START = "vn.herotran.tvcontrol.START_TV_RECEIVER";
    public static final String ACTION_STOP = "vn.herotran.tvcontrol.STOP_TV_RECEIVER";

    private volatile boolean running = false;
    private Thread tcpThread;
    private Thread firebaseThread;
    private ServerSocket serverSocket;
    private Handler mainHandler;
    private PrefsManager prefs;

    // Dem gio gioi han cua RIENG Receiver (dung khi standalone, hoac lam backup)
    private Handler limitHandler;
    private Runnable limitRunnable;
    private volatile boolean limitActive = false;
    private volatile long limitEndsAt = 0;

    // Chong xu ly trung lenh Firebase (launcher + Receiver cung poll 1 node)
    private volatile long lastFirebaseTs = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        mainHandler = new Handler(Looper.getMainLooper());
        limitHandler = new Handler(Looper.getMainLooper());
        prefs = new PrefsManager(this);
        lastFirebaseTs = prefs.getLastCommandTimestamp();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        startForegroundIfNeeded();
        if (!running) {
            running = true;
            startTcpServer();
            startFirebasePolling();
            Log.i(TAG, "TvReceiver started (TCP 9000 + Firebase poll)");
        }
        return START_STICKY;
    }

    private void startForegroundIfNeeded() {
        try {
            String chId = "tv_receiver";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel ch = new NotificationChannel(chId, "TV Receiver", NotificationManager.IMPORTANCE_LOW);
                NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null) nm.createNotificationChannel(ch);
            }
            Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? new Notification.Builder(this, chId)
                    : new Notification.Builder(this);
            b.setContentTitle("RemoteTV - Dang nhan lenh tren TV");
            b.setContentText("Nhan lenh tu dien thoai qua LAN 9000 + Cloud");
            b.setSmallIcon(android.R.drawable.stat_sys_data_bluetooth);
            android.app.Notification notif = b.build();
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(101, notif, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(101, notif);
            }
        } catch (Exception e) {
            Log.w(TAG, "foreground fail: " + e.getMessage());
        }
    }

    // ---------------- TCP 9000 ----------------
    private void startTcpServer() {
        tcpThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    serverSocket = new ServerSocket(LanHelper.PORT_TV_RECEIVER);
                    serverSocket.setSoTimeout(1000);
                    while (running) {
                        try {
                            Socket client = serverSocket.accept();
                            handleTcpClient(client);
                        } catch (SocketTimeoutException ignored) {
                            // timeout de kiem tra running
                        } catch (Exception e) {
                            if (running) Log.w(TAG, "accept fail: " + e.getMessage());
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "TCP 9000 bind fail (co the da co service khac giu cong): " + e.getMessage());
                }
            }
        });
        tcpThread.start();
    }

    private void handleTcpClient(final Socket client) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    client.setSoTimeout(8000);
                    BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream(), "UTF-8"));
                    OutputStream out = client.getOutputStream();
                    StringBuilder sb = new StringBuilder();
                    String line;
                    // Doc 1 dong JSON (DT gui 1 dong + \n). Neu DT gui nhieu dong, doc het toi khi het dong.
                    while ((line = in.readLine()) != null) {
                        sb.append(line);
                        if (line.trim().endsWith("}")) break;
                    }
                    String jsonStr = sb.toString().trim();
                    if (jsonStr.isEmpty()) {
                        out.write("OK\n".getBytes("UTF-8"));
                        out.flush();
                        return;
                    }
                    // DT co the gui kieu cu "DISCOVER_TV" (UDP, khong phai TCP) -> bo qua
                    JSONObject cmd = new JSONObject(jsonStr);
                    String action = cmd.optString("action", "");
                    String reply = handleCommand(cmd);
                    if ("take_screenshot".equals(action)) {
                        // Tra JSON chua anh, khong tra "OK" don
                        out.write((reply + "\n").getBytes("UTF-8"));
                    } else {
                        out.write("OK\n".getBytes("UTF-8"));
                    }
                    out.flush();
                } catch (Exception e) {
                    Log.w(TAG, "tcp client fail: " + e.getMessage());
                } finally {
                    try { client.close(); } catch (Exception ignored) {}
                }
            }
        }).start();
    }

    // ---------------- Firebase polling ----------------
    private void startFirebasePolling() {
        firebaseThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (running) {
                    try {
                        // Chi poll khi co cau hinh Cloud (tren TV, cau hinh duoc dong bo tu DT)
                        String url = prefs.getFirebaseUrl();
                        String secret = prefs.getFirebaseSecret();
                        if (url != null && !url.isEmpty() && secret != null && !secret.isEmpty()) {
                            // Dung callback dong bo bang CountDownLatch de doi ket qua trong thread nay
                            final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
                            final String[] holder = new String[1];
                            FirebaseHelper.getCommand(TvReceiverService.this, new FirebaseHelper.Callback() {
                                @Override public void onSuccess(String r) { holder[0] = r; latch.countDown(); }
                                @Override public void onError(String e) { latch.countDown(); }
                            });
                            latch.await(6, java.util.concurrent.TimeUnit.SECONDS);
                            String resp = holder[0];
                            if (resp != null && !resp.trim().isEmpty() && !"null".equals(resp.trim())) {
                                JSONObject cmd = new JSONObject(resp);
                                long ts = cmd.optLong("timestamp", 0);
                                if (ts > 0 && ts > lastFirebaseTs) {
                                    lastFirebaseTs = ts;
                                    prefs.saveLastCommandTimestamp(ts);
                                    handleCommand(cmd);
                                } else if (ts == 0) {
                                    // Lenh cu khong co timestamp: chi xu ly 1 lan neu action khac lenh truoc
                                    // (de an toan, bo qua - DT luon gui kem timestamp)
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "firebase poll fail: " + e.getMessage());
                    }
                    try { Thread.sleep(2000); } catch (Exception ignored) {}
                }
            }
        });
        firebaseThread.start();
    }

    // ---------------- Dispatcher ----------------
    /**
     * Tra ve reply cho LAN. Voi take_screenshot tra JSON chua anh, cac lenh khac tra "OK".
     * Ham nay chay tren thread phu, chi goi UI qua mainHandler / startActivity.
     */
    private String handleCommand(JSONObject cmd) {
        try {
            String action = cmd.optString("action", "");
            if (action.isEmpty()) return "OK";
            Log.i(TAG, "handleCommand: " + action + " : " + cmd.toString());

            // --- Sync cau hinh: luon xu ly (ca companion + standalone) ---
            if ("sync_cloud_config".equals(action)) {
                String url = cmd.optString("firebase_url", "");
                String secret = cmd.optString("firebase_secret", "");
                if (!url.isEmpty()) prefs.saveFirebaseUrl(url);
                if (!secret.isEmpty()) prefs.saveFirebaseSecret(secret);
                JSONObject f = new JSONObject();
                f.put("firebase_url", url);
                f.put("firebase_secret", secret);
                TvModeHelper.writeLauncherFile("firebase_config.json", f.toString());
                return "OK";
            }
            if ("sync_gemini_config".equals(action)) {
                prefs.saveGeminiApiKey(cmd.optString("gemini_api_key", ""));
                JSONObject f = new JSONObject();
                f.put("gemini_api_key", cmd.optString("gemini_api_key", ""));
                if (cmd.has("gemini_model")) f.put("gemini_model", cmd.optString("gemini_model", ""));
                TvModeHelper.writeLauncherFile("gemini_config.json", f.toString());
                return "OK";
            }
            if ("sync_telegram_config".equals(action)) {
                prefs.saveTelegramConfig(cmd.optBoolean("telegram_enabled", false),
                        cmd.optString("telegram_bot_token", ""), cmd.optString("telegram_chat_id", ""));
                JSONObject f = new JSONObject();
                f.put("telegram_enabled", cmd.optBoolean("telegram_enabled", false));
                f.put("telegram_bot_token", cmd.optString("telegram_bot_token", ""));
                f.put("telegram_chat_id", cmd.optString("telegram_chat_id", ""));
                TvModeHelper.writeLauncherFile("telegram_config.json", f.toString());
                return "OK";
            }
            if ("sync_weather_location".equals(action)) {
                if (cmd.optBoolean("auto", false)) {
                    prefs.saveWeatherSync("", "", "");
                } else {
                    prefs.saveWeatherSync(cmd.optString("city", ""), cmd.optString("lat", ""), cmd.optString("lon", ""));
                }
                TvModeHelper.writeLauncherFile("weather_config.json", cmd.toString());
                return "OK";
            }

            // --- LENH MOI: luon xu ly ---
            if ("take_screenshot".equals(action)) {
                String b64 = TvScreenshotHelper.captureBase64();
                if (b64 == null) {
                    JSONObject err = new JSONObject();
                    err.put("status", "ERROR");
                    err.put("error", "screencap failed");
                    // Neu la lenh Cloud, khong co kenh tra truc tiep -> khong ghi screenshot cu
                    return err.toString();
                }
                // Neu la lenh Cloud, day anh len Cloud luon de DT poll ve
                // (Nhan biet: lenh Cloud co timestamp, lenh LAN cung co the co - nhung day len Cloud
                //  khong hai gi, DT LAN se lay qua reply truc tiep)
                // Chi day khi co cau hinh Cloud
                try {
                    FirebaseHelper.putScreenshot(TvReceiverService.this, b64, System.currentTimeMillis(), null);
                } catch (Exception ignored) {}
                JSONObject ok = new JSONObject();
                ok.put("status", "OK");
                ok.put("image_base64", b64);
                return ok.toString();
            }
            if ("unlock_limit".equals(action)) {
                clearOwnLimit();
                // QUAN TRONG: bao launcher cung huy dem gio cua no
                forwardStopLimitToLauncher();
                // Ghi file de launcher (neu sau nay doc file) biet da mo khoa
                try {
                    JSONObject f = new JSONObject();
                    f.put("action", "unlock_limit");
                    f.put("timestamp", System.currentTimeMillis());
                    TvModeHelper.writeLauncherFile("unlock_limit.json", f.toString());
                } catch (Exception ignored) {}
                // Neu dang hien overlay het gio (do Receiver tu khoa), mo luon
                hideOverlay();
                return "OK";
            }
            if ("lock_screen".equals(action)) {
                String msg = cmd.optString("message", "Tắt TV ngay !");
                if (msg.isEmpty()) msg = "Tắt TV ngay !";
                showOverlay("lock", msg);
                return "OK";
            }
            if ("unlock_screen".equals(action)) {
                hideOverlay();
                return "OK";
            }
            if ("show_message".equals(action)) {
                String msg = cmd.optString("message", "");
                showOverlay("message", msg);
                return "OK";
            }
            if ("hide_message".equals(action)) {
                hideOverlay();
                return "OK";
            }

            // --- LENH CU ---
            boolean launcherAlive = TvModeHelper.isLauncherListening();
            if (launcherAlive) {
                // Companion: de launcher xu ly lenh cu, Receiver khong lam gi them
                // (tranh tat TV 2 lan, tang/giam am 2 lan, ...)
                Log.i(TAG, "Launcher alive, skip old action: " + action);
                return "OK";
            }
            // Standalone: TV chi cai RemoteTV, khong co launcher -> tu xu ly lenh cu
            return handleOldActionStandalone(action, cmd);
        } catch (Exception e) {
            Log.w(TAG, "handleCommand fail: " + e.getMessage());
            return "OK";
        }
    }

    private String handleOldActionStandalone(String action, JSONObject cmd) {
        try {
            if ("set_limit".equals(action)) {
                int minutes = cmd.optInt("minutes", 0);
                startOwnLimit(minutes);
                return "OK";
            }
            if ("stop_limit".equals(action)) {
                clearOwnLimit();
                return "OK";
            }
            if ("power_off".equals(action)) {
                // Khong co quyen he thong de tat nguon that -> khoa man hinh + ve Home (an toan)
                showOverlay("lock", "Tắt TV ngay !");
                goHome();
                return "OK";
            }
            if ("power_on".equals(action)) {
                hideOverlay();
                return "OK";
            }
            if ("go_home".equals(action)) {
                hideOverlay();
                goHome();
                return "OK";
            }
            if ("go_back".equals(action)) {
                hideOverlay();
                // Gui phim BACK bang input (can shell)
                try { Runtime.getRuntime().exec(new String[]{"input", "keyevent", "4"}); } catch (Exception ignored) {}
                return "OK";
            }
            if ("volume_up".equals(action) || "volume_down".equals(action)) {
                try {
                    AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                    if (am != null) {
                        am.adjustVolume("volume_up".equals(action)
                                ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER,
                                AudioManager.FLAG_SHOW_UI);
                    }
                } catch (Exception ignored) {}
                return "OK";
            }
        } catch (Exception e) {
            Log.w(TAG, "old action fail: " + e.getMessage());
        }
        return "OK";
    }

    // ---------------- Limit cua Receiver ----------------
    private void startOwnLimit(final int minutes) {
        clearOwnLimit();
        if (minutes <= 0) return;
        limitActive = true;
        limitEndsAt = System.currentTimeMillis() + minutes * 60L * 1000L;
        limitRunnable = new Runnable() {
            @Override
            public void run() {
                if (!limitActive) return;
                if (System.currentTimeMillis() >= limitEndsAt) {
                    limitActive = false;
                    // Het gio: khoa man hinh (giong launcher)
                    showOverlay("lock", "Tắt TV ngay !");
                } else {
                    limitHandler.postDelayed(this, 5000);
                }
            }
        };
        limitHandler.postDelayed(limitRunnable, 5000);
        Log.i(TAG, "Own limit started: " + minutes + " min");
    }

    private void clearOwnLimit() {
        limitActive = false;
        limitEndsAt = 0;
        if (limitHandler != null && limitRunnable != null) {
            try { limitHandler.removeCallbacks(limitRunnable); } catch (Exception ignored) {}
        }
        prefs.saveLimitMinutes("0");
    }

    /** Gui stop_limit ve launcher tren localhost:8999 de launcher huy dem gio cua no. */
    private void forwardStopLimitToLauncher() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    java.net.Socket s = new java.net.Socket();
                    s.connect(new java.net.InetSocketAddress("127.0.0.1", LanHelper.PORT), 800);
                    s.setSoTimeout(2000);
                    String json = "{\"action\":\"stop_limit\",\"minutes\":0}\n";
                    s.getOutputStream().write(json.getBytes("UTF-8"));
                    s.getOutputStream().flush();
                    // Doc reply (neu co) roi dong, khong quan tam ket qua
                    try {
                        java.io.BufferedReader br = new java.io.BufferedReader(
                                new java.io.InputStreamReader(s.getInputStream(), "UTF-8"));
                        br.readLine();
                    } catch (Exception ignored) {}
                    s.close();
                    Log.i(TAG, "Forwarded stop_limit to launcher 8999");
                } catch (Exception e) {
                    Log.i(TAG, "No launcher on 8999 to forward stop_limit: " + e.getMessage());
                }
            }
        }).start();
    }

    // ---------------- Overlay ----------------
    private void showOverlay(final String mode, final String message) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    Intent it = new Intent(TvReceiverService.this, TvOverlayActivity.class);
                    it.putExtra(TvOverlayActivity.EXTRA_MODE, mode);
                    it.putExtra(TvOverlayActivity.EXTRA_MESSAGE, message);
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TOP
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(it);
                } catch (Exception e) {
                    Log.w(TAG, "showOverlay fail: " + e.getMessage());
                }
            }
        });
    }

    private void hideOverlay() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    Intent it = new Intent(TvOverlayActivity.ACTION_HIDE_OVERLAY);
                    sendBroadcast(it);
                } catch (Exception ignored) {}
            }
        });
    }

    private void goHome() {
        try {
            Intent it = new Intent(Intent.ACTION_MAIN);
            it.addCategory(Intent.CATEGORY_HOME);
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(it);
        } catch (Exception e) {
            Log.w(TAG, "goHome fail: " + e.getMessage());
        }
    }

    @Override
    public void onDestroy() {
        running = false;
        clearOwnLimit();
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignored) {}
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    // Tien ich cho MainActivity / BootReceiver
    public static void start(Context ctx) {
        Intent it = new Intent(ctx, TvReceiverService.class);
        it.setAction(ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try { ctx.startForegroundService(it); } catch (Exception e) { ctx.startService(it); }
        } else {
            ctx.startService(it);
        }
    }

    public static void stop(Context ctx) {
        Intent it = new Intent(ctx, TvReceiverService.class);
        it.setAction(ACTION_STOP);
        ctx.startService(it);
    }
}
