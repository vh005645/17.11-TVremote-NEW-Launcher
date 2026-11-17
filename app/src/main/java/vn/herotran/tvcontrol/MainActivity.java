package vn.herotran.tvcontrol;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private Button btnToggleSettings;
    private LinearLayout cardCloudConfig;
    private EditText etFirebaseUrl, etFirebaseSecret, etGeminiApiKey, etTelegramBotToken, etTelegramChatId;
    private CheckBox cbTelegramEnabled;
    private Button btnSaveCloudConfig, btnSyncGeminiKey, btnSyncTelegramConfig;

    private EditText etWeatherCity, etWeatherLat, etWeatherLon;
    private Button btnGetGpsLocation, btnGeocodeSearch, btnSyncWeatherToTv, btnResetWeatherAuto;

    private RadioGroup rgMode;
    private RadioButton rbCloud, rbLan;
    private LinearLayout cardLanConfig;
    private EditText etIp;
    private EditText etMinutes;
    private Button btnScan;
    private Button btnSetLimit;
    private Button btnStopTime;
    private Button btnUnlockLimit;
    private TextView tvLimitStatus;

    // TV MODE: khi APK nay chay tren TV, bat Receiver de nhan lenh moi
    private TextView tvTvModeStatus;
    private Button btnStartTvReceiver;
    private Button btnStopTvReceiver;

    // MỚI (1): Chụp ảnh màn hình TV
    private ImageView ivScreenshot;
    private TextView tvScreenshotStatus;
    private Button btnTakeScreenshot;
    private Button btnClearScreenshot;

    // MỚI (4): Gửi tin nhắn lên TV
    private EditText etTvMessage;
    private TextView tvMessageCount;
    private TextView tvMessagePreview;
    private Button btnSendTvMessage;
    private Button btnClearTvMessage;

    // MỚI (3): Khoá màn hình TV
    private Button btnLockScreen;
    private Button btnUnlockScreen;

    private static final int MAX_TV_MESSAGE_LENGTH = 500;
    private static final String LOCK_SCREEN_MESSAGE = "Tắt TV ngay !";
    private Button btnPowerOff;
    private Button btnPowerOn;
    private Button btnHome;
    private Button btnBack;
    private Button btnVolDown;
    private Button btnVolUp;
    private TextView tvStatus;

    private PrefsManager prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Điều khiển TV REDMI/XIAOMI");
        setupCenteredActionBar();
        setContentView(R.layout.activity_main);

        prefs = new PrefsManager(this);

        initViews();
        loadSavedData();
        setupListeners();
        checkCloudStatus();
        checkBluetoothPermissions();
        // Neu dang chay tren TV: tu dong bat Receiver (de DT dieu khien duoc ngay, khong can bam tay)
        // Neu tren DT: khong tu bat, chi khi nguoi dung bam nut trong card TV MODE.
        if (TvModeHelper.isTvDevice(this) || prefs.isTvReceiverEnabled()) {
            updateTvModeStatus(true);
            TvReceiverService.start(this);
        } else {
            updateTvModeStatus(false);
        }
    }

    private void setupCenteredActionBar() {
        android.app.ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(false);
            actionBar.setDisplayShowHomeEnabled(false);
            actionBar.setDisplayHomeAsUpEnabled(false);
            actionBar.setDisplayShowCustomEnabled(true);

            TextView tvTitle = new TextView(this);
            tvTitle.setText("Điều khiển TV REDMI/XIAOMI");
            tvTitle.setTextColor(0xFFFFFFFF);
            tvTitle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18);
            tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
            tvTitle.setGravity(android.view.Gravity.CENTER);

            android.app.ActionBar.LayoutParams lp = new android.app.ActionBar.LayoutParams(
                    android.app.ActionBar.LayoutParams.MATCH_PARENT,
                    android.app.ActionBar.LayoutParams.MATCH_PARENT,
                    android.view.Gravity.CENTER);
            actionBar.setCustomView(tvTitle, lp);

            try {
                android.view.ViewParent parent = tvTitle.getParent();
                if (parent instanceof android.view.ViewGroup) {
                    android.view.ViewGroup vg = (android.view.ViewGroup) parent;
                    vg.setPadding(0, 0, 0, 0);
                    java.lang.reflect.Method m = vg.getClass().getMethod("setContentInsetsAbsolute", int.class, int.class);
                    m.invoke(vg, 0, 0);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private void checkBluetoothPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_ADVERTISE) != android.content.pm.PackageManager.PERMISSION_GRANTED
                    || checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                        android.Manifest.permission.BLUETOOTH_ADVERTISE,
                        android.Manifest.permission.BLUETOOTH_CONNECT,
                        android.Manifest.permission.BLUETOOTH_SCAN
                }, 101);
            }
        }
    }

    private void initViews() {
        btnToggleSettings = findViewById(R.id.btnToggleSettings);
        cardCloudConfig = findViewById(R.id.cardCloudConfig);
        etFirebaseUrl = findViewById(R.id.etFirebaseUrl);
        etFirebaseSecret = findViewById(R.id.etFirebaseSecret);
        btnSaveCloudConfig = findViewById(R.id.btnSaveCloudConfig);
        etGeminiApiKey = findViewById(R.id.etGeminiApiKey);
        btnSyncGeminiKey = findViewById(R.id.btnSyncGeminiKey);
        cbTelegramEnabled = findViewById(R.id.cbTelegramEnabled);
        etTelegramBotToken = findViewById(R.id.etTelegramBotToken);
        etTelegramChatId = findViewById(R.id.etTelegramChatId);
        btnSyncTelegramConfig = findViewById(R.id.btnSyncTelegramConfig);

        etWeatherCity = findViewById(R.id.etWeatherCity);
        etWeatherLat = findViewById(R.id.etWeatherLat);
        etWeatherLon = findViewById(R.id.etWeatherLon);
        btnGetGpsLocation = findViewById(R.id.btnGetGpsLocation);
        btnGeocodeSearch = findViewById(R.id.btnGeocodeSearch);
        btnSyncWeatherToTv = findViewById(R.id.btnSyncWeatherToTv);
        btnResetWeatherAuto = findViewById(R.id.btnResetWeatherAuto);

        rgMode = findViewById(R.id.rgMode);
        rbCloud = findViewById(R.id.rbCloud);
        rbLan = findViewById(R.id.rbLan);
        cardLanConfig = findViewById(R.id.cardLanConfig);

        etIp = findViewById(R.id.etIp);
        etMinutes = findViewById(R.id.etMinutes);
        btnScan = findViewById(R.id.btnScan);
        btnSetLimit = findViewById(R.id.btnSetLimit);
        btnStopTime = findViewById(R.id.btnStopTime);
        btnUnlockLimit = findViewById(R.id.btnUnlockLimit);
        tvLimitStatus = findViewById(R.id.tvLimitStatus);
        tvTvModeStatus = findViewById(R.id.tvTvModeStatus);
        btnStartTvReceiver = findViewById(R.id.btnStartTvReceiver);
        btnStopTvReceiver = findViewById(R.id.btnStopTvReceiver);

        ivScreenshot = findViewById(R.id.ivScreenshot);
        tvScreenshotStatus = findViewById(R.id.tvScreenshotStatus);
        btnTakeScreenshot = findViewById(R.id.btnTakeScreenshot);
        btnClearScreenshot = findViewById(R.id.btnClearScreenshot);

        etTvMessage = findViewById(R.id.etTvMessage);
        tvMessageCount = findViewById(R.id.tvMessageCount);
        tvMessagePreview = findViewById(R.id.tvMessagePreview);
        btnSendTvMessage = findViewById(R.id.btnSendTvMessage);
        btnClearTvMessage = findViewById(R.id.btnClearTvMessage);

        btnLockScreen = findViewById(R.id.btnLockScreen);
        btnUnlockScreen = findViewById(R.id.btnUnlockScreen);
        btnPowerOff = findViewById(R.id.btnPowerOff);
        btnPowerOn = findViewById(R.id.btnPowerOn);
        btnHome = findViewById(R.id.btnHome);
        btnBack = findViewById(R.id.btnBack);
        btnVolDown = findViewById(R.id.btnVolDown);
        btnVolUp = findViewById(R.id.btnVolUp);
        tvStatus = findViewById(R.id.tvStatus);
    }

    private void loadSavedData() {
        String curUrl = prefs.getFirebaseUrl();
        String curSecret = prefs.getFirebaseSecret();
        etFirebaseUrl.setText(curUrl);
        etFirebaseSecret.setText(curSecret);
        etGeminiApiKey.setText(prefs.getGeminiApiKey());
        cbTelegramEnabled.setChecked(prefs.isTelegramEnabled());
        etTelegramBotToken.setText(prefs.getTelegramBotToken());
        etTelegramChatId.setText(prefs.getTelegramChatId());

        etWeatherCity.setText(prefs.getWeatherCity());
        etWeatherLat.setText(prefs.getWeatherLat());
        etWeatherLon.setText(prefs.getWeatherLon());

        if (TextUtils.isEmpty(curUrl) || TextUtils.isEmpty(curSecret)) {
            cardCloudConfig.setVisibility(View.VISIBLE);
            updateStatus("⚠️ Vui lòng cấu hình Firebase URL và Secret Key");
        }

        String savedIp = prefs.getTvIp();
        if (!TextUtils.isEmpty(savedIp)) {
            etIp.setText(savedIp);
        } else {
            etIp.setText("192.168.1.50");
        }

        String savedMinutes = prefs.getLimitMinutes();
        if (!TextUtils.isEmpty(savedMinutes)) {
            etMinutes.setText(savedMinutes);
        } else {
            etMinutes.setText("30");
        }
    }

    private boolean isCloudMode() {
        return rbCloud != null && rbCloud.isChecked();
    }

    private void setupListeners() {
        // TV MODE: Bat / Dung Receiver tren TV (APK nay cai ca DT + TV)
        if (btnStartTvReceiver != null) {
            btnStartTvReceiver.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    prefs.setTvReceiverEnabled(true);
                    TvReceiverService.start(MainActivity.this);
                    updateTvModeStatus(true);
                    updateStatus("📺 Đã bật nhận lệnh trên TV (LAN 9000 + Cloud)");
                    Toast.makeText(MainActivity.this, "Đã bật nhận lệnh trên TV", Toast.LENGTH_SHORT).show();
                }
            });
        }
        if (btnStopTvReceiver != null) {
            btnStopTvReceiver.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    prefs.setTvReceiverEnabled(false);
                    TvReceiverService.stop(MainActivity.this);
                    updateTvModeStatus(false);
                    updateStatus("📺 Đã dừng nhận lệnh trên TV");
                    Toast.makeText(MainActivity.this, "Đã dừng nhận lệnh trên TV", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // Nút Ẩn / Hiện Cài đặt Cloud
        btnToggleSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (cardCloudConfig.getVisibility() == View.VISIBLE) {
                    cardCloudConfig.setVisibility(View.GONE);
                } else {
                    cardCloudConfig.setVisibility(View.VISIBLE);
                }
            }
        });

        // Nút Lưu cấu hình Cloud
        btnSaveCloudConfig.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String url = etFirebaseUrl.getText().toString().trim();
                final String secret = etFirebaseSecret.getText().toString().trim();

                if (TextUtils.isEmpty(url)) {
                    Toast.makeText(MainActivity.this, "Vui lòng nhập Firebase URL", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (TextUtils.isEmpty(secret)) {
                    Toast.makeText(MainActivity.this, "Vui lòng nhập Database Secret Key", Toast.LENGTH_SHORT).show();
                    return;
                }

                btnSaveCloudConfig.setEnabled(false);
                updateStatus("🔍 Đang quét tìm TV trong mạng Wi-Fi...");
                Toast.makeText(MainActivity.this, "Đang quét TV để đồng bộ cấu hình...", Toast.LENGTH_SHORT).show();

                discoverTvOrFallback(new IpTask() {
                    @Override
                    public void onTargetIp(String targetIp) {
                        doPushConfigToTv(targetIp, url, secret);
                    }

                    @Override
                    public void onNoIp() {
                        btnSaveCloudConfig.setEnabled(true);
                    }
                });
            }
        });

        // Nút Lưu & Đẩy Gemini API Key sang TV
        btnSyncGeminiKey.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String geminiKey = etGeminiApiKey.getText().toString().trim();
                if (TextUtils.isEmpty(geminiKey)) {
                    Toast.makeText(MainActivity.this, "Vui lòng nhập Gemini API Key", Toast.LENGTH_SHORT).show();
                    etGeminiApiKey.requestFocus();
                    return;
                }

                btnSyncGeminiKey.setEnabled(false);
                updateStatus("🔍 Đang quét tìm TV trong mạng Wi-Fi...");
                Toast.makeText(MainActivity.this, "Đang quét TV để lưu Gemini API Key...", Toast.LENGTH_SHORT).show();

                discoverTvOrFallback(new IpTask() {
                    @Override
                    public void onTargetIp(String targetIp) {
                        doPushGeminiKeyToTv(targetIp, geminiKey);
                    }

                    @Override
                    public void onNoIp() {
                        btnSyncGeminiKey.setEnabled(true);
                    }
                });
            }
        });

        // Nút Lưu & Đẩy cấu hình Telegram sang TV
        btnSyncTelegramConfig.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final boolean enabled = cbTelegramEnabled.isChecked();
                final String botToken = etTelegramBotToken.getText().toString().trim();
                final String chatId = etTelegramChatId.getText().toString().trim();

                if (enabled && (TextUtils.isEmpty(botToken) || TextUtils.isEmpty(chatId))) {
                    Toast.makeText(MainActivity.this, "Vui lòng nhập đủ Bot Token và Chat ID khi bật Telegram", Toast.LENGTH_SHORT).show();
                    if (TextUtils.isEmpty(botToken)) etTelegramBotToken.requestFocus();
                    else etTelegramChatId.requestFocus();
                    return;
                }

                btnSyncTelegramConfig.setEnabled(false);
                updateStatus("🔍 Đang quét tìm TV trong mạng Wi-Fi...");
                Toast.makeText(MainActivity.this, "Đang quét TV để lưu cấu hình Telegram...", Toast.LENGTH_SHORT).show();

                discoverTvOrFallback(new IpTask() {
                    @Override
                    public void onTargetIp(String targetIp) {
                        doPushTelegramConfigToTv(targetIp, enabled, botToken, chatId);
                    }

                    @Override
                    public void onNoIp() {
                        btnSyncTelegramConfig.setEnabled(true);
                    }
                });
            }
        });

        // Nút Lấy GPS từ điện thoại
        btnGetGpsLocation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleGetPhoneLocation();
            }
        });

        // Nút Tìm toạ độ theo địa danh nhập
        btnGeocodeSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleGeocodeSearch();
            }
        });

        // Nút Đồng bộ vị trí thời tiết sang TV
        btnSyncWeatherToTv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String city = etWeatherCity.getText().toString().trim();
                final String lat = etWeatherLat.getText().toString().trim();
                final String lon = etWeatherLon.getText().toString().trim();

                if (TextUtils.isEmpty(lat) || TextUtils.isEmpty(lon)) {
                    Toast.makeText(MainActivity.this, "Vui lòng bấm 'Lấy GPS từ ĐT' hoặc 'Tìm toạ độ' trước khi đồng bộ!", Toast.LENGTH_LONG).show();
                    return;
                }
                if (TextUtils.isEmpty(city)) {
                    Toast.makeText(MainActivity.this, "Vui lòng nhập tên Xã, Phường hoặc Tỉnh/TP!", Toast.LENGTH_SHORT).show();
                    etWeatherCity.requestFocus();
                    return;
                }

                btnSyncWeatherToTv.setEnabled(false);
                updateStatus("🔍 Đang tìm kết nối TV...");
                discoverTvOrFallback(new IpTask() {
                    @Override
                    public void onTargetIp(String targetIp) {
                        doPushWeatherSyncToTv(targetIp, false, city, lat, lon);
                    }

                    @Override
                    public void onNoIp() {
                        btnSyncWeatherToTv.setEnabled(true);
                    }
                });
            }
        });

        // Nút Quay về tự động (IP)
        btnResetWeatherAuto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                btnResetWeatherAuto.setEnabled(false);
                updateStatus("🔍 Đang tìm kết nối TV...");
                discoverTvOrFallback(new IpTask() {
                    @Override
                    public void onTargetIp(String targetIp) {
                        doPushWeatherSyncToTv(targetIp, true, "", "", "");
                    }

                    @Override
                    public void onNoIp() {
                        btnResetWeatherAuto.setEnabled(true);
                    }
                });
            }
        });

        // Chuyển đổi chế độ Cloud / LAN
        rgMode.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.rbCloud) {
                    cardLanConfig.setVisibility(View.GONE);
                    updateStatus("🌐 Chế độ Cloud Internet: Điều khiển từ xa qua 4G/Wifi");
                } else {
                    cardLanConfig.setVisibility(View.VISIBLE);
                    updateStatus("📶 Chế độ LAN: Yêu cầu cùng Wi-Fi với TV");
                }
            }
        });

        // 1. Quét IP TV (chế độ LAN)
        btnScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateStatus("🔍 Đang quét TV trong mạng Wi-Fi...");
                Toast.makeText(MainActivity.this, "Đang quét tìm TV...", Toast.LENGTH_SHORT).show();
                LanHelper.discoverTv(MainActivity.this, new LanHelper.Callback() {
                    @Override
                    public void onSuccess(String foundIp) {
                        etIp.setText(foundIp);
                        prefs.saveTvIp(foundIp);
                        String msg = "✅ Đã tìm thấy TV: " + foundIp;
                        updateStatus(msg);
                        Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(String error) {
                        updateStatus("⚠️ Chưa tìm thấy TV tự động, bạn có thể tự nhập IP");
                        Toast.makeText(MainActivity.this, "Không tìm thấy TV tự động. Hãy nhập IP tay.", Toast.LENGTH_LONG).show();
                    }
                });
            }
        });

        // 2. Cài đặt giới hạn thời gian
        btnSetLimit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String minsStr = etMinutes.getText().toString().trim();
                if (TextUtils.isEmpty(minsStr)) {
                    Toast.makeText(MainActivity.this, "Vui lòng nhập số phút giới hạn", Toast.LENGTH_SHORT).show();
                    etMinutes.requestFocus();
                    return;
                }

                final int minutes;
                try {
                    minutes = Integer.parseInt(minsStr);
                    if (minutes <= 0) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    Toast.makeText(MainActivity.this, "Số phút phải là số nguyên dương", Toast.LENGTH_SHORT).show();
                    return;
                }

                prefs.saveLimitMinutes(minsStr);

                if (isCloudMode()) {
                    updateStatus("⏳ Đang gửi lệnh giới hạn " + minutes + " phút qua Cloud...");
                    FirebaseHelper.sendCommand(MainActivity.this, "set_limit", minutes, new FirebaseHelper.Callback() {
                        @Override
                        public void onSuccess(String response) {
                            String msg = "✅ Đã cài đặt giới hạn " + minutes + " phút cho TV qua Internet!";
                            updateLimitStatus("Trạng thái: Đang giới hạn " + minutes + " phút");
                            updateStatus(msg);
                            Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                        }

                        @Override
                        public void onError(String error) {
                            String msg = "❌ Lỗi: " + error;
                            updateStatus(msg);
                            Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                        }
                    });
                } else {
                    String ip = etIp.getText().toString().trim();
                    if (TextUtils.isEmpty(ip)) {
                        Toast.makeText(MainActivity.this, "Vui lòng nhập hoặc Quét IP TV trước", Toast.LENGTH_SHORT).show();
                        etIp.requestFocus();
                        return;
                    }
                    prefs.saveTvIp(ip);
                    updateStatus("⏳ Đang gửi lệnh giới hạn " + minutes + " phút qua mạng LAN...");
                    LanHelper.sendCommand(ip, "set_limit", minutes, new LanHelper.Callback() {
                        @Override
                        public void onSuccess(String result) {
                            String msg = "✅ [LAN] Đã cài đặt giới hạn " + minutes + " phút cho TV!";
                            updateLimitStatus("Trạng thái: Đang giới hạn " + minutes + " phút");
                            updateStatus(msg);
                            Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                        }

                        @Override
                        public void onError(String error) {
                            updateStatus("❌ [LAN] Lỗi: " + error);
                            Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        });

        // 2b. Hủy giới hạn thời gian (Stop Time)
        btnStopTime.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isCloudMode()) {
                    updateStatus("⏳ Đang gửi lệnh HỦY hẹn giờ qua Cloud...");
                    FirebaseHelper.sendCommand(MainActivity.this, "stop_limit", 0, new FirebaseHelper.Callback() {
                        @Override
                        public void onSuccess(String response) {
                            String msg = "✅ Đã dừng đếm giờ cho TV!";
                            updateLimitStatus("Trạng thái: Đã dừng đếm giờ");
                            updateStatus(msg);
                            Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                        }

                        @Override
                        public void onError(String error) {
                            String msg = "❌ Lỗi: " + error;
                            updateStatus(msg);
                            Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                        }
                    });
                } else {
                    String ip = etIp.getText().toString().trim();
                    if (TextUtils.isEmpty(ip)) {
                        ip = prefs.getTvIp();
                    }
                    if (TextUtils.isEmpty(ip)) {
                        ip = "192.168.18.235";
                    }
                    prefs.saveTvIp(ip);
                    updateStatus("⏳ Đang gửi lệnh HỦY hẹn giờ qua mạng LAN...");
                    LanHelper.sendCommand(ip, "stop_limit", 0, new LanHelper.Callback() {
                        @Override
                        public void onSuccess(String result) {
                            String msg = "✅ [LAN] Đã dừng đếm giờ cho TV!";
                            updateLimitStatus("Trạng thái: Đã dừng đếm giờ");
                            updateStatus(msg);
                            Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                        }

                        @Override
                        public void onError(String error) {
                            updateStatus("❌ [LAN] Lỗi: " + error);
                            Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        });

        // 3. Tắt TV từ xa
        btnPowerOff.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isCloudMode()) {
                    updateStatus("⏳ Đang gửi lệnh Tắt TV qua Cloud...");
                    FirebaseHelper.sendCommand(MainActivity.this, "power_off", 0, new FirebaseHelper.Callback() {
                        @Override
                        public void onSuccess(String response) {
                            String msg = "✅ Đã gửi lệnh TẮT TV qua Internet!";
                            updateStatus(msg);
                            Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onError(String error) {
                            String msg = "❌ Lỗi: " + error;
                            updateStatus(msg);
                            Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                        }
                    });
                } else {
                    String ip = etIp.getText().toString().trim();
                    if (TextUtils.isEmpty(ip)) {
                        Toast.makeText(MainActivity.this, "Vui lòng nhập IP TV trước", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    prefs.saveTvIp(ip);
                    LanHelper.sendCommand(ip, "power_off", 0, new LanHelper.Callback() {
                        @Override
                        public void onSuccess(String result) {
                            String msg = "✅ [LAN] Đã gửi lệnh TẮT TV!";
                            updateStatus(msg);
                            Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onError(String error) {
                            updateStatus("❌ [LAN] Lỗi: " + error);
                            Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        });

        // 3b. Mở TV từ xa
        btnPowerOn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 1. Phát sóng Bluetooth Xiaomi Remote (BLE) & Wake-on-LAN Magic Packet để đánh thức phần cứng TV
                BleWakeHelper.sendBleWake(MainActivity.this);
                LanHelper.wakeUpTv(MainActivity.this);

                if (isCloudMode()) {
                    updateStatus("⚡ Đang phát tín hiệu MỞ TV (BLE Remote + WOL + Cloud)...");
                    Toast.makeText(MainActivity.this, "⚡ Đang phát tín hiệu MỞ TV (BLE + WOL)...", Toast.LENGTH_SHORT).show();
                    FirebaseHelper.sendCommand(MainActivity.this, "power_on", 0, new FirebaseHelper.Callback() {
                        @Override
                        public void onSuccess(String response) {
                            String msg = "✅ Đã gửi tín hiệu MỞ TV qua Internet!";
                            updateStatus(msg);
                        }

                        @Override
                        public void onError(String error) {
                            updateStatus("⚡ Đã phát sóng Bluetooth & WOL đánh thức TV");
                        }
                    });
                } else {
                    String ip = etIp.getText().toString().trim();
                    if (TextUtils.isEmpty(ip)) {
                        ip = prefs.getTvIp();
                    }
                    if (TextUtils.isEmpty(ip)) {
                        ip = "192.168.18.235";
                    }
                    prefs.saveTvIp(ip);
                    updateStatus("⚡ Đang phát Bluetooth BLE + Wake-on-LAN mở TV...");
                    Toast.makeText(MainActivity.this, "⚡ Đang mở TV (Bluetooth + WOL)...", Toast.LENGTH_SHORT).show();

                    final String finalIp = ip;
                    // 1. Thử gửi ngay TCP power_on (nếu TV đang kết nối LAN thì bật sáng màn hình tức thì!)
                    LanHelper.sendCommand(finalIp, "power_on", 0, new LanHelper.Callback() {
                        @Override
                        public void onSuccess(String result) {
                            updateStatus("✅ [LAN] TV đã bật sáng tức thì!");
                        }

                        @Override
                        public void onError(String error) {
                            // Bỏ qua nếu socket chưa kết nối được, bước 2 sau 1.5s sẽ retry sau khi WOL thức dậy
                        }
                    });

                    // 2. Sau 1.5s khi TV nhận WOL và kết nối lại mạng (nếu đang ở trạng thái ngủ sâu), gửi tiếp TCP power_on
                    v.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            LanHelper.sendCommand(finalIp, "power_on", 0, new LanHelper.Callback() {
                                @Override
                                public void onSuccess(String result) {
                                    updateStatus("✅ [LAN] TV đã thức dậy thành công!");
                                }

                                @Override
                                public void onError(String error) {
                                    updateStatus("⚡ Đã phát gói tin Wake-on-LAN tới TV");
                                }
                            });
                        }
                    }, 1500);
                }
            }
        });

        // 4. Nút HOME & BACK
        btnHome.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendSimpleAction("go_home", "Về màn hình HOME");
            }
        });

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendSimpleAction("go_back", "Quay lại (BACK)");
            }
        });

        // 5. Giảm âm lượng
        btnVolDown.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendSimpleAction("volume_down", "Giảm âm lượng (-)");
            }
        });

        // 6. Tăng âm lượng
        btnVolUp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendSimpleAction("volume_up", "Tăng âm lượng (+)");
            }
        });

    // ================= MỚI: 4 chức năng =================

        // (2) Mở khoá giới hạn thời gian: huỷ giới hạn, TV xem không giới hạn
        btnUnlockLimit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleUnlockLimit();
            }
        });

        // (1) Chụp ảnh màn hình TV
        btnTakeScreenshot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleTakeScreenshot();
            }
        });

        btnClearScreenshot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ivScreenshot != null) ivScreenshot.setImageBitmap(null);
                if (tvScreenshotStatus != null) tvScreenshotStatus.setText("Đã xoá ảnh. Bấm “Chụp ảnh” để chụp lại.");
                updateStatus("🗑️ Đã xoá ảnh chụp màn hình trên điện thoại");
            }
        });

        // (4) Gửi tin nhắn lên TV - đếm ký tự + xem trước (nền tối 50%, chữ trắng)
        etTvMessage.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                int len = s == null ? 0 : s.length();
                if (tvMessageCount != null) tvMessageCount.setText(len + " / " + MAX_TV_MESSAGE_LENGTH);
                if (tvMessagePreview != null) {
                    String preview = s == null ? "" : s.toString().trim();
                    tvMessagePreview.setText(preview.isEmpty() ? "Xem trước: —" : "Xem trước: " + preview);
                }
            }
        });

        btnSendTvMessage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleSendTvMessage();
            }
        });

        btnClearTvMessage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleClearTvMessage();
            }
        });

        // (3) Khoá / Mở khoá màn hình TV
        btnLockScreen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleLockScreen();
            }
        });

        btnUnlockScreen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleUnlockScreen();
            }
        });
    }

    private void updateLimitStatus(String text) {
        if (tvLimitStatus != null) tvLimitStatus.setText(text);
    }

    // (2) Mở khoá giới hạn: gửi lệnh "unlock_limit" (khác với "stop_limit" là chỉ dừng đếm).
    // TV nhận lệnh này phải xoá hẳn giới hạn đã lưu + huỷ đếm ngược -> xem không giới hạn.
    private void handleUnlockLimit() {
        if (isCloudMode()) {
            updateStatus("⏳ Đang gửi lệnh MỞ KHOÁ giới hạn qua Cloud...");
            FirebaseHelper.unlockLimit(MainActivity.this, new FirebaseHelper.Callback() {
                @Override
                public void onSuccess(String response) {
                    String msg = "✅ Đã MỞ KHOÁ giới hạn! TV xem không giới hạn.";
                    updateStatus(msg);
                    updateLimitStatus("Trạng thái: Không giới hạn - xem thoải mái");
                    Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                }
                @Override
                public void onError(String error) {
                    String msg = "❌ Lỗi: " + error;
                    updateStatus(msg);
                    Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                }
            });
        } else {
            String ip = etIp.getText().toString().trim();
            if (TextUtils.isEmpty(ip)) ip = prefs.getTvIp();
            if (TextUtils.isEmpty(ip)) {
                Toast.makeText(MainActivity.this, "Vui lòng nhập hoặc Quét IP TV trước", Toast.LENGTH_SHORT).show();
                etIp.requestFocus();
                return;
            }
            prefs.saveTvIp(ip);
            updateStatus("⏳ Đang gửi lệnh MỞ KHOÁ giới hạn qua mạng LAN...");
            LanHelper.unlockLimit(ip, new LanHelper.Callback() {
                @Override
                public void onSuccess(String result) {
                    String msg = "✅ [LAN] Đã MỞ KHOÁ giới hạn! TV xem không giới hạn.";
                    updateStatus(msg);
                    updateLimitStatus("Trạng thái: Không giới hạn - xem thoải mái");
                    Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                }
                @Override
                public void onError(String error) {
                    updateStatus("❌ [LAN] Lỗi: " + error);
                    Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    // (1) Chụp ảnh màn hình TV
    private void handleTakeScreenshot() {
        btnTakeScreenshot.setEnabled(false);
        if (tvScreenshotStatus != null) tvScreenshotStatus.setText("Đang chụp ảnh màn hình TV...");
        if (isCloudMode()) {
            updateStatus("⏳ Đang yêu cầu TV chụp ảnh màn hình qua Cloud...");
            // Cloud là 1 chiều: gửi lệnh, rồi poll node screenshot.json mà TV đẩy lên
            final long requestTime = System.currentTimeMillis();
            FirebaseHelper.sendCommand(MainActivity.this, "take_screenshot", 0, new FirebaseHelper.Callback() {
                @Override
                public void onSuccess(String response) {
                    pollScreenshotFromCloud(requestTime, 0);
                }
                @Override
                public void onError(String error) {
                    btnTakeScreenshot.setEnabled(true);
                    String msg = "❌ Lỗi: " + error;
                    updateStatus(msg);
                    if (tvScreenshotStatus != null) tvScreenshotStatus.setText(msg);
                    Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                }
            });
        } else {
            String ip = etIp.getText().toString().trim();
            if (TextUtils.isEmpty(ip)) ip = prefs.getTvIp();
            if (TextUtils.isEmpty(ip)) {
                btnTakeScreenshot.setEnabled(true);
                Toast.makeText(MainActivity.this, "Vui lòng nhập hoặc Quét IP TV trước", Toast.LENGTH_SHORT).show();
                etIp.requestFocus();
                return;
            }
            prefs.saveTvIp(ip);
            updateStatus("⏳ Đang chụp ảnh màn hình TV qua mạng LAN...");
            LanHelper.requestScreenshot(ip, new LanHelper.ScreenshotCallback() {
                @Override
                public void onScreenshot(String base64) {
                    btnTakeScreenshot.setEnabled(true);
                    decodeAndShowScreenshot(base64);
                }
                @Override
                public void onError(String error) {
                    btnTakeScreenshot.setEnabled(true);
                    String msg = "❌ Không chụp được ảnh: " + error;
                    updateStatus(msg);
                    if (tvScreenshotStatus != null) tvScreenshotStatus.setText(msg);
                    Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    // Poll Cloud tối đa 8 lần, mỗi lần cách 2s. Chỉ nhận ảnh mới hơn thời điểm bấm chụp.
    private void pollScreenshotFromCloud(final long requestTime, final int attempt) {
        if (attempt >= 8) {
            btnTakeScreenshot.setEnabled(true);
            String msg = "❌ TV chưa gửi ảnh về. Hãy chắc chắn app TV đã cập nhật tính năng chụp ảnh màn hình.";
            updateStatus(msg);
            if (tvScreenshotStatus != null) tvScreenshotStatus.setText(msg);
            Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
            return;
        }
        FirebaseHelper.getScreenshot(MainActivity.this, new FirebaseHelper.Callback() {
            @Override
            public void onSuccess(String response) {
                try {
                    if (TextUtils.isEmpty(response) || "null".equals(response.trim())) throw new Exception("empty");
                    JSONObject json = new JSONObject(response);
                    long ts = json.optLong("timestamp", 0);
                    String b64 = json.optString("image_base64", "");
                    if (b64.isEmpty()) b64 = json.optString("screenshot_base64", "");
                    // Ảnh cũ (TV chụp từ lần trước) thì bỏ qua, poll tiếp
                    if (b64.isEmpty() || (ts != 0 && ts < requestTime - 2000)) throw new Exception("stale");
                    btnTakeScreenshot.setEnabled(true);
                    decodeAndShowScreenshot(b64);
                } catch (Exception e) {
                    // Chưa có ảnh mới -> đợi 2s rồi poll lại
                    tvScreenshotStatus.postDelayed(new Runnable() {
                        @Override public void run() { pollScreenshotFromCloud(requestTime, attempt + 1); }
                    }, 2000);
                }
            }
            @Override
            public void onError(String error) {
                tvScreenshotStatus.postDelayed(new Runnable() {
                    @Override public void run() { pollScreenshotFromCloud(requestTime, attempt + 1); }
                }, 2000);
            }
        });
    }

    // Giải mã Base64 -> Bitmap, giảm cỡ nếu ảnh quá lớn để khỏi tràn RAM, rồi hiển thị
    private void decodeAndShowScreenshot(String base64) {
        try {
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
            // Giới hạn chiều dài nhất ~1280px cho hiển thị trên điện thoại
            int sample = 1;
            while (Math.max(opts.outWidth, opts.outHeight) / sample > 1280) sample *= 2;
            opts.inJustDecodeBounds = false;
            opts.inSampleSize = sample;
            Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
            if (bmp == null) throw new Exception("decode null");
            if (ivScreenshot != null) ivScreenshot.setImageBitmap(bmp);
            String msg = "✅ Đã chụp ảnh màn hình TV!";
            updateStatus(msg);
            if (tvScreenshotStatus != null) {
                tvScreenshotStatus.setText("Đã chụp lúc " + new java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new java.util.Date()));
            }
            Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            String msg = "❌ Ảnh TV gửi về bị lỗi, không hiển thị được.";
            updateStatus(msg);
            if (tvScreenshotStatus != null) tvScreenshotStatus.setText(msg);
            Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
        }
    }

    // (4) Gửi tin nhắn lên TV: nền tối 50%, chữ trắng, tối đa 500 ký tự
    private void handleSendTvMessage() {
        String message = etTvMessage.getText().toString().trim();
        if (TextUtils.isEmpty(message)) {
            Toast.makeText(this, "Vui lòng nhập tin nhắn muốn hiện lên TV", Toast.LENGTH_SHORT).show();
            etTvMessage.requestFocus();
            return;
        }
        if (message.length() > MAX_TV_MESSAGE_LENGTH) {
            message = message.substring(0, MAX_TV_MESSAGE_LENGTH);
            etTvMessage.setText(message);
            Toast.makeText(this, "Tin nhắn đã cắt còn " + MAX_TV_MESSAGE_LENGTH + " ký tự", Toast.LENGTH_SHORT).show();
        }
        final String finalMessage = message;
        if (isCloudMode()) {
            updateStatus("⏳ Đang gửi tin nhắn lên TV qua Cloud...");
            FirebaseHelper.sendTvMessage(MainActivity.this, finalMessage, new FirebaseHelper.Callback() {
                @Override
                public void onSuccess(String response) {
                    String msg = "✅ Đã gửi tin nhắn lên TV!";
                    updateStatus(msg);
                    Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                }
                @Override
                public void onError(String error) {
                    String msg = "❌ Lỗi: " + error;
                    updateStatus(msg);
                    Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                }
            });
        } else {
            String ip = etIp.getText().toString().trim();
            if (TextUtils.isEmpty(ip)) ip = prefs.getTvIp();
            if (TextUtils.isEmpty(ip)) {
                Toast.makeText(MainActivity.this, "Vui lòng nhập hoặc Quét IP TV trước", Toast.LENGTH_SHORT).show();
                etIp.requestFocus();
                return;
            }
            prefs.saveTvIp(ip);
            updateStatus("⏳ Đang gửi tin nhắn lên TV qua mạng LAN...");
            LanHelper.sendTvMessage(ip, finalMessage, new LanHelper.Callback() {
                @Override
                public void onSuccess(String result) {
                    String msg = "✅ [LAN] Đã gửi tin nhắn lên TV!";
                    updateStatus(msg);
                    Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                }
                @Override
                public void onError(String error) {
                    updateStatus("❌ [LAN] Lỗi: " + error);
                    Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private void handleClearTvMessage() {
        // Xoá ô nhập trên điện thoại + gửi lệnh ẩn tin nhắn trên TV
        etTvMessage.setText("");
        if (isCloudMode()) {
            FirebaseHelper.clearTvMessage(MainActivity.this, new FirebaseHelper.Callback() {
                @Override public void onSuccess(String r) { updateStatus("✅ Đã xoá tin nhắn trên TV"); }
                @Override public void onError(String e) { updateStatus("❌ Lỗi: " + e); }
            });
        } else {
            String ip = etIp.getText().toString().trim();
            if (TextUtils.isEmpty(ip)) ip = prefs.getTvIp();
            if (!TextUtils.isEmpty(ip)) {
                LanHelper.clearTvMessage(ip, new LanHelper.Callback() {
                    @Override public void onSuccess(String r) { updateStatus("✅ [LAN] Đã xoá tin nhắn trên TV"); }
                    @Override public void onError(String e) { updateStatus("❌ [LAN] " + e); }
                });
            }
        }
        Toast.makeText(this, "Đã xoá tin nhắn", Toast.LENGTH_SHORT).show();
    }

    // (3) Khoá màn hình TV: màn hình đen, hiện chữ "Tắt TV ngay !"
    private void handleLockScreen() {
        sendActionWithMessage("lock_screen", LOCK_SCREEN_MESSAGE, "Đã khoá màn hình TV!");
    }

    private void handleUnlockScreen() {
        sendActionWithMessage("unlock_screen", "", "Đã mở khoá màn hình TV!");
    }

    // Gửi lệnh kèm message, dùng chung cho khoá / mở khoá màn hình (Cloud + LAN)
    private void sendActionWithMessage(final String action, final String message, final String successLabel) {
        if (isCloudMode()) {
            updateStatus("⏳ Đang gửi lệnh qua Cloud...");
            try {
                JSONObject extra = new JSONObject();
                if (!TextUtils.isEmpty(message)) extra.put("message", message);
                FirebaseHelper.sendCommandWithData(MainActivity.this, action, extra, new FirebaseHelper.Callback() {
                    @Override
                    public void onSuccess(String response) {
                        updateStatus("✅ " + successLabel);
                        Toast.makeText(MainActivity.this, successLabel, Toast.LENGTH_SHORT).show();
                    }
                    @Override
                    public void onError(String error) {
                        String msg = "❌ Lỗi: " + error;
                        updateStatus(msg);
                        Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        } else {
            String ip = etIp.getText().toString().trim();
            if (TextUtils.isEmpty(ip)) ip = prefs.getTvIp();
            if (TextUtils.isEmpty(ip)) {
                Toast.makeText(MainActivity.this, "Vui lòng nhập hoặc Quét IP TV trước", Toast.LENGTH_SHORT).show();
                etIp.requestFocus();
                return;
            }
            prefs.saveTvIp(ip);
            try {
                JSONObject obj = new JSONObject();
                obj.put("action", action);
                obj.put("minutes", 0);
                if (!TextUtils.isEmpty(message)) obj.put("message", message);
                LanHelper.sendCommandWithJson(ip, obj, new LanHelper.Callback() {
                    @Override
                    public void onSuccess(String result) {
                        updateStatus("✅ [LAN] " + successLabel);
                        Toast.makeText(MainActivity.this, successLabel, Toast.LENGTH_SHORT).show();
                    }
                    @Override
                    public void onError(String error) {
                        updateStatus("❌ [LAN] Lỗi: " + error);
                        Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void sendSimpleAction(final String action, final String label) {
        if (isCloudMode()) {
            FirebaseHelper.sendCommand(MainActivity.this, action, 0, new FirebaseHelper.Callback() {
                @Override
                public void onSuccess(String response) {
                    updateStatus("✅ Đã gửi lệnh: " + label);
                    Toast.makeText(MainActivity.this, label, Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(String error) {
                    updateStatus("❌ Lỗi: " + error);
                }
            });
        } else {
            String ip = etIp.getText().toString().trim();
            if (TextUtils.isEmpty(ip)) {
                ip = prefs.getTvIp();
            }
            if (TextUtils.isEmpty(ip)) return;
            LanHelper.sendCommand(ip, action, 0, new LanHelper.Callback() {
                @Override
                public void onSuccess(String result) {
                    updateStatus("✅ [LAN] " + label);
                }

                @Override
                public void onError(String error) {
                    updateStatus("❌ [LAN] " + error);
                }
            });
        }
    }

    private void checkCloudStatus() {
        FirebaseHelper.getTvStatus(this, new FirebaseHelper.Callback() {
            @Override
            public void onSuccess(String response) {
                if (response != null && response.contains("\"online\":true")) {
                    updateStatus("🟢 TV đang Online trên Cloud");
                } else {
                    updateStatus("🌐 Cloud sẵn sàng (TV chưa kết nối)");
                }
            }

            @Override
            public void onError(String error) {
                updateStatus("🌐 Sẵn sàng gửi lệnh qua Cloud");
            }
        });
    }

    private interface IpTask {
        void onTargetIp(String targetIp);
        void onNoIp();
    }

    private void discoverTvOrFallback(final IpTask task) {
        LanHelper.discoverTv(MainActivity.this, new LanHelper.Callback() {
            @Override
            public void onSuccess(final String foundIp) {
                etIp.setText(foundIp);
                prefs.saveTvIp(foundIp);
                task.onTargetIp(foundIp);
            }

            @Override
            public void onError(String error) {
                String fallbackIp = etIp.getText().toString().trim();
                if (TextUtils.isEmpty(fallbackIp)) {
                    fallbackIp = prefs.getTvIp();
                }
                if (!TextUtils.isEmpty(fallbackIp)) {
                    updateStatus("⏳ Đang thử kết nối trực tiếp TV tại " + fallbackIp + "...");
                    task.onTargetIp(fallbackIp);
                } else {
                    String msg = "❌ Không tìm thấy TV trong mạng Wi-Fi! Hãy đảm bảo TV đang bật và cùng mạng Wi-Fi.";
                    updateStatus(msg);
                    Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                    task.onNoIp();
                }
            }
        });
    }

    private void doPushConfigToTv(final String targetIp, final String url, final String secret) {
        updateStatus("⏳ Đang đẩy file cấu hình sang TV (" + targetIp + ")...");
        LanHelper.syncCloudConfig(targetIp, url, secret, new LanHelper.Callback() {
            @Override
            public void onSuccess(String result) {
                // CHỈ KHI TV NHẬN THÀNH CÔNG MỚI LƯU CẤU HÌNH VÀO PHONE
                prefs.saveFirebaseUrl(url);
                prefs.saveFirebaseSecret(secret);
                btnSaveCloudConfig.setEnabled(true);
                cardCloudConfig.setVisibility(View.GONE);
                checkCloudStatus();

                String msg = "✅ Cấu hình thành công! Đã đẩy file JSON vào TV (" + targetIp + ")!";
                updateStatus(msg);
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(String error) {
                btnSaveCloudConfig.setEnabled(true);
                String msg = "❌ Thất bại: Không thể đẩy file vào TV (" + error + "). Hãy kiểm tra TV!";
                updateStatus(msg);
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void doPushGeminiKeyToTv(final String targetIp, final String geminiKey) {
        updateStatus("⏳ Đang đẩy Gemini API Key sang TV (" + targetIp + ")...");
        LanHelper.syncGeminiConfig(targetIp, geminiKey, null, new LanHelper.Callback() {
            @Override
            public void onSuccess(String result) {
                prefs.saveGeminiApiKey(geminiKey);
                btnSyncGeminiKey.setEnabled(true);
                cardCloudConfig.setVisibility(View.GONE);

                String msg = "✅ Đã lưu & đẩy Gemini API Key vào TV (" + targetIp + ")!";
                updateStatus(msg);
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(String error) {
                btnSyncGeminiKey.setEnabled(true);
                String msg = "❌ Thất bại khi đẩy Gemini API Key sang TV (" + error + ")!";
                updateStatus(msg);
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void doPushTelegramConfigToTv(final String targetIp, final boolean enabled, final String botToken, final String chatId) {
        updateStatus("⏳ Đang đẩy cấu hình Telegram sang TV (" + targetIp + ")...");
        LanHelper.syncTelegramConfig(targetIp, enabled, botToken, chatId, new LanHelper.Callback() {
            @Override
            public void onSuccess(String result) {
                prefs.saveTelegramConfig(enabled, botToken, chatId);
                btnSyncTelegramConfig.setEnabled(true);
                cardCloudConfig.setVisibility(View.GONE);

                String msg = "✅ Đã lưu & đẩy cấu hình Telegram sang TV (" + targetIp + ")!";
                updateStatus(msg);
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(String error) {
                btnSyncTelegramConfig.setEnabled(true);
                String msg = "❌ Thất bại khi đẩy cấu hình Telegram sang TV (" + error + ")!";
                updateStatus(msg);
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    private static final int REQ_LOCATION = 1001;

    private void handleGetPhoneLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, REQ_LOCATION);
            return;
        }

        final LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) {
            Toast.makeText(this, "Không khởi tạo được LocationManager", Toast.LENGTH_SHORT).show();
            return;
        }

        updateStatus("📍 Đang lấy vị trí GPS từ điện thoại...");
        Location bestLoc = null;
        try {
            Location gps = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location net = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (gps != null && net != null) {
                bestLoc = gps.getTime() > net.getTime() ? gps : net;
            } else if (gps != null) {
                bestLoc = gps;
            } else {
                bestLoc = net;
            }
        } catch (SecurityException ignored) {
        }

        if (bestLoc != null) {
            applyLocation(bestLoc);
        } else {
            try {
                LocationListener oneShot = new LocationListener() {
                    @Override
                    public void onLocationChanged(Location location) {
                        applyLocation(location);
                        try {
                            lm.removeUpdates(this);
                        } catch (Exception ignored) {}
                    }
                    @Override
                    public void onStatusChanged(String provider, int status, Bundle extras) {}
                    @Override
                    public void onProviderEnabled(String provider) {}
                    @Override
                    public void onProviderDisabled(String provider) {}
                };
                if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    lm.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, oneShot, null);
                } else if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    lm.requestSingleUpdate(LocationManager.GPS_PROVIDER, oneShot, null);
                } else {
                    Toast.makeText(this, "Vui lòng bật Định vị (GPS) trên điện thoại!", Toast.LENGTH_LONG).show();
                }
            } catch (Exception e) {
                Toast.makeText(this, "Chưa lấy được toạ độ: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void applyLocation(Location loc) {
        if (loc == null) return;
        final double lat = loc.getLatitude();
        final double lon = loc.getLongitude();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                etWeatherLat.setText(String.format(Locale.US, "%.4f", lat));
                etWeatherLon.setText(String.format(Locale.US, "%.4f", lon));
            }
        });

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Geocoder gcd = new Geocoder(MainActivity.this, new Locale("vi", "VN"));
                    List<Address> addresses = gcd.getFromLocation(lat, lon, 1);
                    if (addresses != null && !addresses.isEmpty()) {
                        Address a = addresses.get(0);
                        final String name = formatAddressName(a);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (!TextUtils.isEmpty(name)) {
                                    etWeatherCity.setText(name);
                                    updateStatus("📍 Đã định vị: " + name);
                                    Toast.makeText(MainActivity.this, "Đã định vị: " + name, Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                    }
                } catch (Exception ignored) {}
            }
        }).start();
    }

    private String formatAddressName(Address a) {
        if (a == null) return "";
        String ward = a.getSubLocality();
        String district = a.getSubAdminArea();
        String province = a.getAdminArea();

        StringBuilder sb = new StringBuilder();
        if (!TextUtils.isEmpty(ward)) {
            sb.append(ward);
        }
        if (!TextUtils.isEmpty(district)) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(district);
        } else if (!TextUtils.isEmpty(province)) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(province);
        }
        if (sb.length() == 0 && !TextUtils.isEmpty(province)) {
            sb.append(province);
        }
        return sb.toString();
    }

    private void handleGeocodeSearch() {
        final String input = etWeatherCity.getText().toString().trim();
        if (TextUtils.isEmpty(input)) {
            Toast.makeText(this, "Vui lòng nhập tên Xã, Phường hoặc Quận, Tỉnh để tìm toạ độ!", Toast.LENGTH_SHORT).show();
            etWeatherCity.requestFocus();
            return;
        }

        btnGeocodeSearch.setEnabled(false);
        updateStatus("🔍 Đang tìm toạ độ cho: " + input + "...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Geocoder gcd = new Geocoder(MainActivity.this, new Locale("vi", "VN"));
                    List<Address> list = gcd.getFromLocationName(input, 1);
                    if (list != null && !list.isEmpty()) {
                        final Address a = list.get(0);
                        final double lat = a.getLatitude();
                        final double lon = a.getLongitude();
                        final String resolvedName = formatAddressName(a);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                btnGeocodeSearch.setEnabled(true);
                                etWeatherLat.setText(String.format(Locale.US, "%.4f", lat));
                                etWeatherLon.setText(String.format(Locale.US, "%.4f", lon));
                                if (!TextUtils.isEmpty(resolvedName) && TextUtils.isEmpty(input)) {
                                    etWeatherCity.setText(resolvedName);
                                }
                                String msg = "✅ Đã tìm thấy toạ độ: " + String.format(Locale.US, "%.4f, %.4f", lat, lon);
                                updateStatus(msg);
                                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                            }
                        });
                    } else {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                btnGeocodeSearch.setEnabled(true);
                                updateStatus("❌ Không tìm thấy toạ độ cho: " + input);
                                Toast.makeText(MainActivity.this, "Không tìm thấy toạ độ. Thử thêm tên Quận/Huyện, Tỉnh!", Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            btnGeocodeSearch.setEnabled(true);
                            updateStatus("❌ Lỗi tìm toạ độ: " + e.getMessage());
                            Toast.makeText(MainActivity.this, "Lỗi tìm kiếm: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void doPushWeatherSyncToTv(final String targetIp, final boolean auto, final String city, final String lat, final String lon) {
        updateStatus(auto ? ("⏳ Đang chuyển TV (" + targetIp + ") về tự động quét IP...") : ("⏳ Đang đồng bộ vị trí sang TV (" + targetIp + ")..."));
        LanHelper.syncWeatherLocation(targetIp, auto, city, lat, lon, new LanHelper.Callback() {
            @Override
            public void onSuccess(String result) {
                btnSyncWeatherToTv.setEnabled(true);
                btnResetWeatherAuto.setEnabled(true);
                if (!auto) {
                    prefs.saveWeatherSync(city, lat, lon);
                }
                cardCloudConfig.setVisibility(View.GONE);
                String msg = auto ? "✅ TV đã chuyển sang tự động quét vị trí theo IP!" : ("✅ Đã đồng bộ vị trí '" + city + "' sang TV!");
                updateStatus(msg);
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(String error) {
                btnSyncWeatherToTv.setEnabled(true);
                btnResetWeatherAuto.setEnabled(true);
                String msg = "❌ Thất bại khi đồng bộ thời tiết sang TV (" + error + ")!";
                updateStatus(msg);
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION) {
            if (grantResults != null && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                handleGetPhoneLocation();
            } else {
                Toast.makeText(this, "Cần cấp quyền vị trí để lấy toạ độ GPS của điện thoại!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void updateTvModeStatus(boolean running) {
        if (tvTvModeStatus == null) return;
        if (running) {
            if (TvModeHelper.isTvDevice(this)) {
                tvTvModeStatus.setText("Trạng thái: Đang chạy trên TV - Đang nhận lệnh (LAN 9000 + Cloud). Launcher vẫn xử lý lệnh cũ trên 8999.");
            } else {
                tvTvModeStatus.setText("Trạng thái: Đang nhận lệnh (chế độ test trên điện thoại). Trên TV thật, Receiver tự bật khi mở app.");
            }
        } else {
            if (TvModeHelper.isTvDevice(this)) {
                tvTvModeStatus.setText("Trạng thái: Đang chạy trên TV nhưng Receiver đang TẮT - Điện thoại sẽ không gửi được lệnh mới. Bấm “Bật nhận lệnh”.");
            } else {
                tvTvModeStatus.setText("Trên điện thoại: không cần bật. Trên TV: bấm “Bật nhận lệnh”.");
            }
        }
    }

    private void updateStatus(String text) {
        if (tvStatus != null) {
            tvStatus.setText(text);
        }
    }
}
