package vn.herotran.tvcontrol;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Tu dong chay Receiver khi TV khoi dong lai, de DT luon dieu khien duoc
 * ma khong can mo app RemoteTV tren TV bang tay.
 * Chi tu dong chay neu: la TV HOAC nguoi dung da bat "Che do TV" truoc do.
 */
public class TvBootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String a = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(a)
                && !"android.intent.action.QUICKBOOT_POWERON".equals(a)) return;
        PrefsManager prefs = new PrefsManager(context);
        boolean shouldRun = TvModeHelper.isTvDevice(context) || prefs.isTvReceiverEnabled();
        if (shouldRun) {
            TvReceiverService.start(context);
        }
    }
}
