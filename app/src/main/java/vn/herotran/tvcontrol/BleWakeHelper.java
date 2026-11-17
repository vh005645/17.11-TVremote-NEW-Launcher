package vn.herotran.tvcontrol;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;

/**
 * BleWakeHelper - Phat song BLE de danh thuc TV Xiaomi / Redmi.
 *
 * Nguyen tac: Nhieu TV Xiaomi / Redmi de che do Standby van lang nghe
 * Bluetooth Low Energy tu remote. Khi dien thoai phat 1 goi quang ba BLE
 * (advertising) giong remote, TV se tinh giac de nhan them lenh WOL / LAN / Cloud.
 *
 * Day la co che "best-effort", bo sung cho Wake-on-LAN trong LanHelper.wakeUpTv().
 * Neu thiet bi khong co Bluetooth, chua cap quyen, hoac TV khong ho tro danh thuc
 * qua BLE thi ham nay tu thoat em, khong crash, va WOL + LAN + Cloud van chay.
 *
 * An toan tren Android 12+ (API 31+): kiem tra BLUETOOTH_ADVERTISE /
 * BLUETOOTH_CONNECT truoc khi goi bat cu API Bluetooth nao, bat het
 * SecurityException.
 */
public class BleWakeHelper {
    private static final String TAG = "BleWakeHelper";

    // Xiaomi Inc. - Company ID theo Bluetooth SIG (0x038F = 911)
    private static final int XIAOMI_COMPANY_ID = 0x038F;

    // Service UUID dung cho goi quang ba wake. Khong phai UUID ket noi,
    // chi de TV nhan dien day la goi tu remote / dien thoai dieu khien.
    private static final ParcelUuid WAKE_SERVICE_UUID =
            ParcelUuid.fromString("0000FEA0-0000-1000-8000-00805F9B34FB");

    // Phat trong 6 giay roi tu tat de tiet kiem pin.
    private static final long ADVERTISE_DURATION_MS = 6000;

    private static BluetoothLeAdvertiser sAdvertiser;
    private static AdvertiseCallback sCallback;
    private static final Handler sHandler = new Handler(Looper.getMainLooper());
    private static boolean sAdvertising = false;

    private BleWakeHelper() {}

    public static void sendBleWake(final Context ctx) {
        if (ctx == null) return;
        final Context appCtx;
        try {
            appCtx = ctx.getApplicationContext();
        } catch (Throwable t) {
            return;
        }
        if (appCtx == null) return;

        // Neu dang phat thi khong phat chong len nhau.
        if (sAdvertising) return;

        try {
            // 1. Quyen Android 12+
            if (Build.VERSION.SDK_INT >= 31) {
                if (appCtx.checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
                        != PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, "Thieu quyen BLUETOOTH_ADVERTISE, bo qua BLE wake");
                    return;
                }
                if (appCtx.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                        != PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, "Thieu quyen BLUETOOTH_CONNECT, bo qua BLE wake");
                    return;
                }
            }

            // 2. Lay BluetoothAdapter
            BluetoothManager btManager =
                    (BluetoothManager) appCtx.getSystemService(Context.BLUETOOTH_SERVICE);
            if (btManager == null) return;
            BluetoothAdapter adapter = btManager.getAdapter();
            if (adapter == null || !adapter.isEnabled()) {
                Log.w(TAG, "Bluetooth tat hoac khong co, bo qua BLE wake");
                return;
            }

            // 3. Lay Advertiser
            BluetoothLeAdvertiser advertiser = adapter.getBluetoothLeAdvertiser();
            if (advertiser == null) {
                Log.w(TAG, "Thiet bi khong ho tro BLE Advertising");
                return;
            }

            // 4. Cau hinh phat: manh nhat + nhanh nhat de TV bat duoc ngay
            AdvertiseSettings settings = new AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                    .setConnectable(false)
                    .setTimeout(0)
                    .build();

            // Payload: Company ID Xiaomi + 4 byte wake: 0x01 (power) + 3 byte ngau nhien
            // de moi lan phat khac nhau, tranh TV loc trung goi cu.
            byte[] payload = new byte[]{
                    0x01, // wake / power
                    (byte) (System.currentTimeMillis() & 0xFF),
                    (byte) ((System.currentTimeMillis() >> 8) & 0xFF),
                    0x00
            };

            AdvertiseData data = new AdvertiseData.Builder()
                    .setIncludeDeviceName(false)
                    .setIncludeTxPowerLevel(false)
                    .addServiceUuid(WAKE_SERVICE_UUID)
                    .addManufacturerData(XIAOMI_COMPANY_ID, payload)
                    .build();

            sCallback = new AdvertiseCallback() {
                @Override
                public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                    super.onStartSuccess(settingsInEffect);
                    Log.i(TAG, "Da phat BLE wake");
                }

                @Override
                public void onStartFailure(int errorCode) {
                    super.onStartFailure(errorCode);
                    Log.w(TAG, "BLE advertise failed: " + errorCode);
                    sAdvertising = false;
                    sAdvertiser = null;
                    sCallback = null;
                }
            };

            sAdvertiser = advertiser;
            sAdvertising = true;
            advertiser.startAdvertising(settings, data, sCallback);

            // 5. Tu tat sau 6 giay
            sHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    stopAdvertising();
                }
            }, ADVERTISE_DURATION_MS);

        } catch (SecurityException se) {
            Log.w(TAG, "BLE wake bi chan boi quyen: " + se.getMessage());
            sAdvertising = false;
        } catch (Throwable t) {
            Log.w(TAG, "BLE wake loi: " + t.getMessage());
            sAdvertising = false;
        }
    }

    public static void stopAdvertising() {
        try {
            if (sAdvertiser != null && sCallback != null && sAdvertising) {
                sAdvertiser.stopAdvertising(sCallback);
            }
        } catch (Throwable ignored) {
        } finally {
            sAdvertising = false;
            sAdvertiser = null;
            sCallback = null;
        }
    }
}
