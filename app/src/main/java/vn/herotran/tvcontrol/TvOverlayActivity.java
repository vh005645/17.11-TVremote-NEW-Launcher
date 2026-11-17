package vn.herotran.tvcontrol;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * Man hinh phu tren TV cho 2 tinh nang moi:
 *  - LOCK (lock_screen): nen DEN TUYET DOI #FF000000, chu "Tat TV ngay !" trang, to, giua.
 *    Chan BACK / chan thoat, chi tat khi nhan unlock_screen / power_on / go_home.
 *  - MESSAGE (show_message): nen DEN 50% #80000000, chu TRANG, giua, nhin xuyen thau.
 *    Tat khi nhan hide_message / go_home / go_back.
 *
 * Dung Activity thay vi WindowManager overlay de KHONG can quyen SYSTEM_ALERT_WINDOW
 * tren TV (nhieu TV khong cap quyen nay cho app thuong). Service chi can startActivity.
 */
public class TvOverlayActivity extends Activity {
    public static final String EXTRA_MODE = "mode"; // "lock" | "message"
    public static final String EXTRA_MESSAGE = "message";
    public static final String ACTION_HIDE_OVERLAY = "vn.herotran.tvcontrol.HIDE_OVERLAY";
    public static final String ACTION_UPDATE_MESSAGE = "vn.herotran.tvcontrol.UPDATE_MESSAGE";

    private String mode = "message";
    private TextView tvText;
    private final BroadcastReceiver hideReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) return;
            if (ACTION_HIDE_OVERLAY.equals(intent.getAction())) {
                finish();
            } else if (ACTION_UPDATE_MESSAGE.equals(intent.getAction())) {
                String m = intent.getStringExtra(EXTRA_MESSAGE);
                if (m != null && tvText != null) {
                    if ("lock".equals(mode)) tvText.setText("Tắt TV ngay !");
                    else tvText.setText(m);
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // De Activity hien len tren ca khi TV dang o man hinh khac
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        mode = getIntent() != null ? getIntent().getStringExtra(EXTRA_MODE) : "message";
        if (mode == null) mode = "message";
        String msg = getIntent() != null ? getIntent().getStringExtra(EXTRA_MESSAGE) : null;

        FrameLayout root = new FrameLayout(this);
        if ("lock".equals(mode)) {
            root.setBackgroundColor(Color.BLACK); // #FF000000
        } else {
            root.setBackgroundColor(0x80000000); // Den 50%
        }
        tvText = new TextView(this);
        tvText.setTextColor(Color.WHITE);
        tvText.setGravity(Gravity.CENTER);
        tvText.setPadding(48, 48, 48, 48);
        if ("lock".equals(mode)) {
            tvText.setText("Tắt TV ngay !");
            tvText.setTextSize(56);
            tvText.setTypeface(null, android.graphics.Typeface.BOLD);
        } else {
            tvText.setText(msg == null || msg.isEmpty() ? "" : msg);
            tvText.setTextSize(32);
        }
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        lp.gravity = Gravity.CENTER;
        root.addView(tvText, lp);
        // Cham vao man hinh tin nhan thi tat (tien cho nguoi dung TV), con man hinh khoa thi KHONG cho tat bang cham
        if (!"lock".equals(mode)) {
            root.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { finish(); }
            });
        }
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter f = new IntentFilter();
        f.addAction(ACTION_HIDE_OVERLAY);
        f.addAction(ACTION_UPDATE_MESSAGE);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(hideReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(hideReceiver, f);
        }
    }

    @Override
    protected void onPause() {
        try { unregisterReceiver(hideReceiver); } catch (Exception ignored) {}
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        if ("lock".equals(mode)) {
            // Khoa: khong cho BACK thoat, phai mo khoa tu DT
            return;
        }
        super.onBackPressed();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ("lock".equals(mode)) {
            // Chan het phim (tru HOME - he thong se xu ly, Service cung nghe go_home de mo khoa)
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                    || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_MENU) {
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }
}
