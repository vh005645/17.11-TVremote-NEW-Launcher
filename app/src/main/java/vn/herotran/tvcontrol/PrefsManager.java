package vn.herotran.tvcontrol;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Lưu trữ IP TV, số phút giới hạn, và Cấu hình Firebase Cloud trong SharedPreferences.
 */
public class PrefsManager {
    private static final String PREF_NAME = "PhoneControlPrefs";
    private static final String KEY_IP = "tv_ip";
    private static final String KEY_MINUTES = "limit_minutes";
    private static final String KEY_FIREBASE_URL = "firebase_url";
    private static final String KEY_FIREBASE_SECRET = "firebase_secret";
    private static final String KEY_GEMINI_API_KEY = "gemini_api_key";
    private static final String KEY_TELEGRAM_ENABLED = "telegram_enabled";
    private static final String KEY_TELEGRAM_BOT_TOKEN = "telegram_bot_token";
    private static final String KEY_TELEGRAM_CHAT_ID = "telegram_chat_id";
    private static final String KEY_WEATHER_CITY = "weather_sync_city";
    private static final String KEY_WEATHER_LAT = "weather_sync_lat";
    private static final String KEY_WEATHER_LON = "weather_sync_lon";
    private static final String KEY_TV_RECEIVER_ENABLED = "tv_receiver_enabled";
    private static final String KEY_LAST_COMMAND_TS = "last_command_ts";

    private final SharedPreferences prefs;

    public PrefsManager(Context ctx) {
        prefs = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void saveTvIp(String ip) {
        prefs.edit().putString(KEY_IP, ip).apply();
    }

    public String getTvIp() {
        return prefs.getString(KEY_IP, "192.168.1.50");
    }

    public void saveLimitMinutes(String minutes) {
        prefs.edit().putString(KEY_MINUTES, minutes).apply();
    }

    public String getLimitMinutes() {
        return prefs.getString(KEY_MINUTES, "30");
    }

    public void saveFirebaseUrl(String url) {
        prefs.edit().putString(KEY_FIREBASE_URL, url != null ? url.trim() : "").apply();
    }

    public String getFirebaseUrl() {
        return prefs.getString(KEY_FIREBASE_URL, AppConfig.DEFAULT_FIREBASE_URL);
    }

    public void saveFirebaseSecret(String secret) {
        prefs.edit().putString(KEY_FIREBASE_SECRET, secret != null ? secret.trim() : "").apply();
    }

    public String getFirebaseSecret() {
        return prefs.getString(KEY_FIREBASE_SECRET, AppConfig.DEFAULT_FIREBASE_SECRET);
    }

    public void saveGeminiApiKey(String apiKey) {
        prefs.edit().putString(KEY_GEMINI_API_KEY, apiKey != null ? apiKey.trim() : "").apply();
    }

    public String getGeminiApiKey() {
        return prefs.getString(KEY_GEMINI_API_KEY, "");
    }

    public void saveTelegramConfig(boolean enabled, String botToken, String chatId) {
        prefs.edit()
                .putBoolean(KEY_TELEGRAM_ENABLED, enabled)
                .putString(KEY_TELEGRAM_BOT_TOKEN, botToken != null ? botToken.trim() : "")
                .putString(KEY_TELEGRAM_CHAT_ID, chatId != null ? chatId.trim() : "")
                .apply();
    }

    public boolean isTelegramEnabled() {
        return prefs.getBoolean(KEY_TELEGRAM_ENABLED, false);
    }

    public String getTelegramBotToken() {
        return prefs.getString(KEY_TELEGRAM_BOT_TOKEN, "");
    }

    public String getTelegramChatId() {
        return prefs.getString(KEY_TELEGRAM_CHAT_ID, "");
    }

    public void saveWeatherSync(String city, String lat, String lon) {
        prefs.edit()
                .putString(KEY_WEATHER_CITY, city != null ? city.trim() : "")
                .putString(KEY_WEATHER_LAT, lat != null ? lat.trim() : "")
                .putString(KEY_WEATHER_LON, lon != null ? lon.trim() : "")
                .apply();
    }

    public String getWeatherCity() {
        return prefs.getString(KEY_WEATHER_CITY, "");
    }

    public String getWeatherLat() {
        return prefs.getString(KEY_WEATHER_LAT, "");
    }

    public void setTvReceiverEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_TV_RECEIVER_ENABLED, enabled).apply();
    }

    public boolean isTvReceiverEnabled() {
        return prefs.getBoolean(KEY_TV_RECEIVER_ENABLED, false);
    }

    public void saveLastCommandTimestamp(long ts) {
        prefs.edit().putLong(KEY_LAST_COMMAND_TS, ts).apply();
    }

    public long getLastCommandTimestamp() {
        return prefs.getLong(KEY_LAST_COMMAND_TS, 0);
    }

    public String getWeatherLon() {
        return prefs.getString(KEY_WEATHER_LON, "");
    }
}
