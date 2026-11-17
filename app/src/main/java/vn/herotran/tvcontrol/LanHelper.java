package vn.herotran.tvcontrol;

import android.content.Context;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Giao tiếp mạng nội bộ (LAN) với TV qua Socket TCP & UDP Discovery trên port 8999.
 * 100% Java 8 thuần, không phụ thuộc ADB, không cần quyền đặc biệt.
 */
public class LanHelper {
    private static final String TAG = "LanHelper";
    public static final int PORT = 8999;
    /** Cong MOI cua TvReceiverService trong chinh APK RemoteTV tren TV. Launcher giu 8999, Receiver giu 9000. */
    public static final int PORT_TV_RECEIVER = 9000;

    public interface Callback {
        void onSuccess(String result);
        void onError(String error);
    }

    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * Gửi lệnh trực tiếp qua mạng LAN tới TV.
     */
    public static void sendCommand(final String ip, final String action, final int minutes, final Callback callback) {
        String json = "{\"action\":\"" + action + "\",\"minutes\":" + minutes + "}\n";
        sendJsonLine(ip, json, "Không thể kết nối tới TV tại " + ip + ": ", callback);
    }

    // ================= MỚI: lệnh mở rộng (giữ nguyên sendCommand cũ) =================

    public interface ScreenshotCallback {
        void onScreenshot(String base64);
        void onError(String error);
    }

    /**
     * Gửi lệnh kèm JSONObject tuỳ ý (message, ...) qua LAN. Dùng JSONObject để
     * escape đúng tiếng Việt / dấu ngoặc / xuống dòng trong tin nhắn.
     */
    public static void sendCommandWithJson(final String ip, final JSONObject obj, final Callback callback) {
        if (obj == null) {
            postError(callback, "Lệnh rỗng");
            return;
        }
        // Chi dung cho LENH MOI -> gui ca 8999 (launcher) + 9000 (Receiver). Lenh cu dung sendCommand() rieng.
        sendNewCommandBothPorts(ip, obj, callback);
    }

    // ================= TV RECEIVER: gui lenh moi di ca 2 cong =================
    /**
     * Gui lenh moi (take_screenshot, unlock_limit, lock_screen, show_message, ...)
     * di CA 2 cong:
     *  - 8999: launcher cu (neu sau nay launcher duoc cap nhat thi no cung chay).
     *  - 9000: TvReceiverService trong APK RemoteTV tren TV (chay lenh moi ngay bay gio).
     * Thanh cong neu 1 trong 2 tra OK. Gui song song, khong doi lan nhau.
     * LENH CU (set_limit, power_*, ...) GIU NGUYEN chi gui 8999 de tranh xu ly trung.
     */
    public static void sendNewCommandBothPorts(final String ip, final JSONObject obj, final Callback callback) {
        if (obj == null) { postError(callback, "Lệnh rỗng"); return; }
        final String jsonLine = obj.toString() + "\n";
        final java.util.concurrent.atomic.AtomicInteger done = new java.util.concurrent.atomic.AtomicInteger(0);
        final java.util.concurrent.atomic.AtomicBoolean anyOk = new java.util.concurrent.atomic.AtomicBoolean(false);
        final String[] lastErr = new String[1];
        Callback one = new Callback() {
            @Override public void onSuccess(String r) {
                anyOk.set(true);
                if (done.incrementAndGet() == 2) postSuccess(callback, "OK");
            }
            @Override public void onError(String e) {
                lastErr[0] = e;
                if (done.incrementAndGet() == 2) {
                    if (anyOk.get()) postSuccess(callback, "OK");
                    else postError(callback, lastErr[0] != null ? lastErr[0] : "Không kết nối được TV");
                }
            }
        };
        // Gui ca 2, neu 1 cong tra OK ngay thi van doi cong kia xong moi tra (de dam bao ca 2 deu nhan)
        // Nhung de DT khong cho lau, neu 1 cong OK + 1 cong loi nhanh thi ket qua van OK.
        sendJsonLineToPort(ip, PORT, jsonLine, "", one);
        sendJsonLineToPort(ip, PORT_TV_RECEIVER, jsonLine, "", one);
    }

    /** Gui 1 dong JSON toi 1 cong cu the. */
    public static void sendJsonLineToPort(final String ip, final int port, final String jsonLine, final String errPrefix, final Callback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Socket socket = null;
                try {
                    socket = new Socket();
                    socket.connect(new InetSocketAddress(ip, port), 2500);
                    socket.setSoTimeout(3000);
                    OutputStream out = socket.getOutputStream();
                    out.write(jsonLine.getBytes("UTF-8"));
                    out.flush();
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
                    String reply = in.readLine();
                    if (reply != null && ("OK".equals(reply.trim()) || reply.trim().startsWith("{"))) {
                        postSuccess(callback, "OK");
                    } else {
                        postError(callback, "Phản hồi: " + reply);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "LAN send port " + port + " error: " + e.getMessage());
                    postError(callback, errPrefix + e.getMessage());
                } finally {
                    if (socket != null) { try { socket.close(); } catch (Exception ignored) {} }
                }
            }
        }).start();
    }

    /** Chup anh: thu 9000 (Receiver moi) truoc, that bai thi thu 8999 (launcher neu co ho tro). */
    public static void requestScreenshotBothPorts(final String ip, final ScreenshotCallback callback) {
        requestScreenshotOnPort(ip, PORT_TV_RECEIVER, new ScreenshotCallback() {
            @Override public void onScreenshot(String b64) { callback.onScreenshot(b64); }
            @Override public void onError(String err9000) {
                requestScreenshotOnPort(ip, PORT, new ScreenshotCallback() {
                    @Override public void onScreenshot(String b64) { callback.onScreenshot(b64); }
                    @Override public void onError(String err8999) { callback.onError(err9000 + " | 8999: " + err8999); }
                });
            }
        });
    }

    private static void requestScreenshotOnPort(final String ip, final int port, final ScreenshotCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Socket socket = null;
                try {
                    socket = new Socket();
                    socket.connect(new InetSocketAddress(ip, port), 2500);
                    socket.setSoTimeout(20000);
                    OutputStream out = socket.getOutputStream();
                    JSONObject req = new JSONObject();
                    req.put("action", "take_screenshot");
                    req.put("minutes", 0);
                    out.write((req.toString() + "\n").getBytes("UTF-8"));
                    out.flush();
                    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                    java.io.InputStream in = socket.getInputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) != -1) { baos.write(buf, 0, n); }
                    String reply = new String(baos.toByteArray(), "UTF-8").trim();
                    if (reply.isEmpty()) { postScreenshotError(callback, "TV không trả về ảnh"); return; }
                    int jsonStart = reply.lastIndexOf('{');
                    if (jsonStart > 0) reply = reply.substring(jsonStart);
                    JSONObject json = new JSONObject(reply);
                    String b64 = null;
                    if (json.has("image_base64")) b64 = json.optString("image_base64", null);
                    else if (json.has("screenshot_base64")) b64 = json.optString("screenshot_base64", null);
                    else if (json.has("image")) b64 = json.optString("image", null);
                    if (b64 == null || b64.isEmpty()) {
                        postScreenshotError(callback, "TV không trả về ảnh");
                        return;
                    }
                    postScreenshotSuccess(callback, b64);
                } catch (Exception e) {
                    Log.w(TAG, "LAN screenshot port " + port + " error: " + e.getMessage());
                    postScreenshotError(callback, e.getMessage() != null ? e.getMessage() : "Lỗi kết nối");
                } finally {
                    if (socket != null) { try { socket.close(); } catch (Exception ignored) {} }
                }
            }
        }).start();
    }

    /** Gửi tin nhắn lên TV qua LAN. TV hiển thị: nền tối 50%, chữ trắng. */
    public static void sendTvMessage(final String ip, final String message, final Callback callback) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("action", "show_message");
            obj.put("minutes", 0);
            obj.put("message", message);
            sendCommandWithJson(ip, obj, callback);
        } catch (Exception e) {
            postError(callback, e.getMessage());
        }
    }

    /** Xoá / ẩn tin nhắn đang hiện trên TV qua LAN. */
    public static void clearTvMessage(final String ip, final Callback callback) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("action", "hide_message");
            obj.put("minutes", 0);
            sendCommandWithJson(ip, obj, callback);
        } catch (Exception e) {
            postError(callback, e.getMessage());
        }
    }

    /** Khoá màn hình TV qua LAN: màn hình đen, hiện chữ "Tắt TV ngay !". */
    public static void lockScreen(final String ip, final Callback callback) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("action", "lock_screen");
            obj.put("minutes", 0);
            obj.put("message", "Tắt TV ngay !");
            sendCommandWithJson(ip, obj, callback);
        } catch (Exception e) {
            postError(callback, e.getMessage());
        }
    }

    /** Mở khoá màn hình TV qua LAN. */
    public static void unlockScreen(final String ip, final Callback callback) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("action", "unlock_screen");
            obj.put("minutes", 0);
            sendCommandWithJson(ip, obj, callback);
        } catch (Exception e) {
            postError(callback, e.getMessage());
        }
    }

    /** Mở khoá giới hạn thời gian qua LAN: huỷ giới hạn, TV xem không giới hạn. */
    public static void unlockLimit(final String ip, final Callback callback) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("action", "unlock_limit");
            obj.put("minutes", 0);
            sendCommandWithJson(ip, obj, callback);
        } catch (Exception e) {
            postError(callback, e.getMessage());
        }
    }

    /**
     * Yêu cầu TV chụp ảnh màn hình qua LAN.
     * Gửi {"action":"take_screenshot"}.
     * TV trả về 1 dòng JSON (có thể rất dài): {"status":"OK","image_base64":"/9j/..."}
     * hoặc {"status":"OK","screenshot_base64":"..."} rồi đóng kết nối.
     * Hàm này đọc TOÀN BỘ phản hồi (không chỉ 1 dòng "OK" như lệnh thường)
     * nên ảnh lớn vẫn nhận đủ. Timeout 20s.
     */
    public static void requestScreenshot(final String ip, final ScreenshotCallback callback) {
        // Giu ten cu cho tuong thich, nhung thuc te thu 9000 truoc roi moi 8999
        requestScreenshotBothPorts(ip, callback);
    }


    private static void postScreenshotSuccess(final ScreenshotCallback cb, final String b64) {
        if (cb != null) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    cb.onScreenshot(b64);
                }
            });
        }
    }

    private static void postScreenshotError(final ScreenshotCallback cb, final String err) {
        if (cb != null) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    cb.onError(err);
                }
            });
        }
    }

    /**
     * Đồng bộ cấu hình Firebase URL & Secret sang TV qua mạng LAN để lưu vào /sdcard/com.ts.launcher/firebase_config.json.
     */
    public static void syncCloudConfig(final String ip, final String url, final String secret, final Callback callback) {
        String json = "{\"action\":\"sync_cloud_config\",\"firebase_url\":\"" + url + "\",\"firebase_secret\":\"" + secret + "\"}\n";
        sendJsonLine(ip, json, "", callback);
    }

    /**
     * Đồng bộ Gemini API Key sang TV qua mạng LAN để lưu vào /sdcard/com.ts.launcher/gemini_config.json.
     */
    public static void syncGeminiConfig(final String ip, final String apiKey, final String model, final Callback callback) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("action", "sync_gemini_config");
            obj.put("gemini_api_key", apiKey != null ? apiKey.trim() : "");
            if (model != null && !model.trim().isEmpty()) {
                obj.put("gemini_model", model.trim());
            }
            sendJsonLine(ip, obj.toString() + "\n", "", callback);
        } catch (Exception e) {
            postError(callback, e.getMessage());
        }
    }

    /**
     * Đồng bộ cấu hình Telegram Bot sang TV qua mạng LAN để lưu vào /sdcard/com.ts.launcher/telegram_config.json.
     */
    public static void syncTelegramConfig(final String ip, final boolean enabled, final String botToken, final String chatId, final Callback callback) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("action", "sync_telegram_config");
            obj.put("telegram_enabled", enabled);
            obj.put("telegram_bot_token", botToken != null ? botToken.trim() : "");
            obj.put("telegram_chat_id", chatId != null ? chatId.trim() : "");
            sendJsonLine(ip, obj.toString() + "\n", "", callback);
        } catch (Exception e) {
            postError(callback, e.getMessage());
        }
    }

    /**
     * Đồng bộ vị trí thời tiết (Cấp Xã, Phường, Thị trấn, Quận/Huyện, Tỉnh/TP) sang TV qua LAN.
     * Nếu auto = true -> TV sẽ chuyển về chế độ tự động quét theo địa chỉ IP.
     */
    public static void syncWeatherLocation(final String ip, final boolean auto, final String city, final String lat, final String lon, final Callback callback) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("action", "sync_weather_location");
            obj.put("auto", auto);
            if (!auto) {
                obj.put("city", city != null ? city.trim() : "");
                obj.put("lat", lat != null ? lat.trim() : "");
                obj.put("lon", lon != null ? lon.trim() : "");
            }
            sendJsonLine(ip, obj.toString() + "\n", "", callback);
        } catch (Exception e) {
            postError(callback, e.getMessage());
        }
    }

    private static void sendJsonLine(final String ip, final String jsonLine, final String errPrefix, final Callback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Socket socket = null;
                try {
                    socket = new Socket();
                    socket.connect(new InetSocketAddress(ip, PORT), 2500);
                    socket.setSoTimeout(3000);

                    OutputStream out = socket.getOutputStream();
                    out.write(jsonLine.getBytes("UTF-8"));
                    out.flush();

                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
                    String reply = in.readLine();
                    if ("OK".equals(reply != null ? reply.trim() : "")) {
                        postSuccess(callback, "OK");
                    } else {
                        postError(callback, "Phản hồi: " + reply);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "LAN send error: " + e.getMessage());
                    postError(callback, errPrefix + e.getMessage());
                } finally {
                    if (socket != null) {
                        try { socket.close(); } catch (Exception ignored) {}
                    }
                }
            }
        }).start();
    }

    /**
     * Tự động quét và tìm IP của TV trong mạng LAN bằng UDP Broadcast.
     */
    public static void discoverTv(final Context ctx, final Callback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                DatagramSocket socket = null;
                WifiManager.MulticastLock multicastLock = null;
                try {
                    if (ctx != null) {
                        try {
                            WifiManager wm = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                            if (wm != null) {
                                multicastLock = wm.createMulticastLock("tvcontrol:discover_lock");
                                multicastLock.setReferenceCounted(true);
                                multicastLock.acquire();
                            }
                        } catch (Exception ignored) {}
                    }

                    socket = new DatagramSocket();
                    socket.setBroadcast(true);
                    socket.setSoTimeout(2000); // 2s timeout

                    byte[] sendData = "DISCOVER_TV".getBytes("UTF-8");

                    // Gửi tới 255.255.255.255
                    DatagramPacket packet = new DatagramPacket(sendData, sendData.length, InetAddress.getByName("255.255.255.255"), PORT);
                    socket.send(packet);

                    // Đồng thời gửi tới broadcast của subnet nếu có
                    InetAddress broadcastAddr = getBroadcastAddress(ctx);
                    if (broadcastAddr != null) {
                        DatagramPacket subnetPacket = new DatagramPacket(sendData, sendData.length, broadcastAddr, PORT);
                        socket.send(subnetPacket);
                    }

                    byte[] recvBuf = new byte[256];
                    DatagramPacket recvPacket = new DatagramPacket(recvBuf, recvBuf.length);
                    socket.receive(recvPacket);

                    String response = new String(recvPacket.getData(), 0, recvPacket.getLength(), "UTF-8");
                    if ("TV_HERO_LAUNCHER".equals(response.trim())) {
                        String tvIp = recvPacket.getAddress().getHostAddress();
                        postSuccess(callback, tvIp);
                        return;
                    }

                    postError(callback, "Không tìm thấy TV");
                } catch (Exception e) {
                    postError(callback, "Không tìm thấy TV trên mạng Wi-Fi");
                } finally {
                    if (multicastLock != null && multicastLock.isHeld()) {
                        try { multicastLock.release(); } catch (Exception ignored) {}
                    }
                    if (socket != null) {
                        try { socket.close(); } catch (Exception ignored) {}
                    }
                }
            }
        }).start();
    }

    private static InetAddress getBroadcastAddress(Context ctx) {
        try {
            WifiManager wifi = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifi == null) return null;
            DhcpInfo dhcp = wifi.getDhcpInfo();
            if (dhcp == null) return null;

            int broadcast = (dhcp.ipAddress & dhcp.netmask) | ~dhcp.netmask;
            byte[] quads = new byte[4];
            for (int k = 0; k < 4; k++) {
                quads[k] = (byte) ((broadcast >> (k * 8)) & 0xFF);
            }
            return InetAddress.getByAddress(quads);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Gửi Magic Packet Wake-on-LAN để đánh thức TV từ chế độ Standby/Sleep qua mạng LAN.
     * Hỗ trợ phát tới cả card mạng LAN (eth0) và Wi-Fi (wlan0).
     */
    public static void wakeUpTv(final Context ctx) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                String[] macList = new String[]{
                    "78:53:33:DD:06:A3", // TV Ethernet (dây mạng LAN)
                    "FC:70:2E:9A:63:34"  // TV Wi-Fi
                };

                for (String mac : macList) {
                    sendWakeOnLanInternal(ctx, mac);
                }
            }
        }).start();
    }

    private static void sendWakeOnLanInternal(Context ctx, String macAddress) {
        if (macAddress == null || macAddress.trim().isEmpty()) return;
        WifiManager.MulticastLock multicastLock = null;
        try {
            if (ctx != null) {
                try {
                    WifiManager wm = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                    if (wm != null) {
                        multicastLock = wm.createMulticastLock("tvcontrol:wol_lock");
                        multicastLock.setReferenceCounted(true);
                        multicastLock.acquire();
                    }
                } catch (Exception ignored) {}
            }

            String cleanMac = macAddress.replaceAll("[:\\-]", "").trim();
            if (cleanMac.length() != 12) return;
            byte[] macBytes = new byte[6];
            for (int i = 0; i < 6; i++) {
                macBytes[i] = (byte) Integer.parseInt(cleanMac.substring(i * 2, i * 2 + 2), 16);
            }
            byte[] bytes = new byte[6 + 16 * macBytes.length];
            for (int i = 0; i < 6; i++) {
                bytes[i] = (byte) 0xff;
            }
            for (int i = 6; i < bytes.length; i += macBytes.length) {
                System.arraycopy(macBytes, 0, bytes, i, macBytes.length);
            }

            DatagramSocket socket = new DatagramSocket();
            socket.setBroadcast(true);

            InetAddress broadcastGlobal = InetAddress.getByName("255.255.255.255");
            InetAddress subnetBroadcast = ctx != null ? getBroadcastAddress(ctx) : null;
            InetAddress directTvIp = null;
            try {
                directTvIp = InetAddress.getByName("192.168.18.235");
            } catch (Exception ignored) {}

            // Gửi 3 lần qua cả broadcast và direct IP để đảm bảo xuyên mọi Switch / Router Wi-Fi
            for (int attempt = 0; attempt < 3; attempt++) {
                // Port 9
                DatagramPacket p9 = new DatagramPacket(bytes, bytes.length, broadcastGlobal, 9);
                socket.send(p9);
                if (subnetBroadcast != null) {
                    DatagramPacket pSub9 = new DatagramPacket(bytes, bytes.length, subnetBroadcast, 9);
                    socket.send(pSub9);
                }
                if (directTvIp != null) {
                    DatagramPacket pDirect9 = new DatagramPacket(bytes, bytes.length, directTvIp, 9);
                    socket.send(pDirect9);
                }

                // Port 7
                DatagramPacket p7 = new DatagramPacket(bytes, bytes.length, broadcastGlobal, 7);
                socket.send(p7);
                if (subnetBroadcast != null) {
                    DatagramPacket pSub7 = new DatagramPacket(bytes, bytes.length, subnetBroadcast, 7);
                    socket.send(pSub7);
                }
                if (directTvIp != null) {
                    DatagramPacket pDirect7 = new DatagramPacket(bytes, bytes.length, directTvIp, 7);
                    socket.send(pDirect7);
                }

                try { Thread.sleep(60); } catch (InterruptedException ignored) {}
            }

            socket.close();
            Log.i(TAG, "Sent WOL Magic Packets to " + macAddress);
        } catch (Exception e) {
            Log.w(TAG, "WOL send error: " + e.getMessage());
        } finally {
            if (multicastLock != null && multicastLock.isHeld()) {
                try {
                    multicastLock.release();
                } catch (Exception ignored) {}
            }
        }
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

