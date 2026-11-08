package ru.vlad.unitprice;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;

/** Установка скачанного APK через PackageInstaller (системный диалог подтверждения). */
final class UpdateInstaller {

    private UpdateInstaller() {
    }

    static void install(Context context, File apk) throws Exception {
        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(context.getPackageName());

        int sessionId = installer.createSession(params);
        try (PackageInstaller.Session session = installer.openSession(sessionId)) {
            try (OutputStream out = session.openWrite("base.apk", 0, apk.length());
                 FileInputStream in = new FileInputStream(apk)) {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) > 0) {
                    out.write(buffer, 0, n);
                }
                session.fsync(out);
            }

            Intent result = new Intent(context, InstallResultReceiver.class);
            PendingIntent pending = PendingIntent.getBroadcast(
                    context, sessionId, result,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
            session.commit(pending.getIntentSender());
        }
    }
}
