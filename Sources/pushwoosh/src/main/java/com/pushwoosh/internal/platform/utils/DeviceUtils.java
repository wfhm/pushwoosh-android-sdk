/*
 *
 * Copyright (c) 2017. Pushwoosh Inc. (http://www.pushwoosh.com)
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * (i) the original and/or modified Software should be used exclusively to work with Pushwoosh services,
 *
 * (ii) the above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.pushwoosh.internal.platform.utils;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.PowerManager;
import android.os.StatFs;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import com.pushwoosh.PushwooshPlatform;
import com.pushwoosh.PushwooshSharedDataProvider;
import com.pushwoosh.internal.platform.AndroidPlatformModule;
import com.pushwoosh.internal.utils.PWLog;
import com.pushwoosh.repository.RegistrationPrefs;
import com.pushwoosh.repository.RepositoryModule;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

public class DeviceUtils {
    private static final int GET_UUID_TASK_TIMEOUT = 10000;
    private static DeviceUUID DEVICE_SERIAL_UUID = new DeviceSerialUUID();
    private static DeviceUUID DEVICE_OLD_HWID = new DeviceOldUUID();
    private static DeviceUUID DEVICE_TELEPHONY_UUID = new DeviceTelephonyUUID();

    private static DeviceUUID DEVICE_RANDOM_UUID = new DeviceRandomUUID();
    private static DeviceUUID DEVICE_SHARED_UUID = new DeviceSharedUUID();

    static {
        initHWIDGenerators();
    }

    static void initHWIDGenerators() {
        DEVICE_SERIAL_UUID = new DeviceSerialUUID();
        DEVICE_OLD_HWID = new DeviceOldUUID();
        DEVICE_TELEPHONY_UUID = new DeviceTelephonyUUID();

        DEVICE_RANDOM_UUID = new DeviceRandomUUID();
        DEVICE_SHARED_UUID = new DeviceSharedUUID();

        DEVICE_OLD_HWID.setFallback(DEVICE_TELEPHONY_UUID);
        DEVICE_TELEPHONY_UUID.setFallback(DEVICE_SERIAL_UUID);
        DEVICE_SERIAL_UUID.setFallback(DEVICE_RANDOM_UUID);

        DEVICE_SHARED_UUID.setFallback(DEVICE_RANDOM_UUID);
    }

    public static String getDeviceUUID() {
        return DEVICE_SHARED_UUID.getUUID();
    }

    public static void getDeviceUUID(@NonNull OnGetHwidListener onGetHwidListener) {
        DEVICE_SHARED_UUID.getUUID(onGetHwidListener);
    }

    @Nullable public static String getDeviceUUIDOrNull() {
        return RepositoryModule.getRegistrationPreferences().deviceId().get();
    }

    public static String getDeviceUUIDOld() {
        return DEVICE_OLD_HWID.getUUID();
    }

    @SuppressLint("InlinedApi")
    public static boolean isTablet() {
        int xlargeBit = Configuration.SCREENLAYOUT_SIZE_XLARGE;
        Configuration config = AndroidPlatformModule.getResourceProvider().getConfiguration();
        return config != null && (config.screenLayout & xlargeBit) == xlargeBit;
    }

    public static float getBatteryLevel() {
        try {
            Intent batteryIntent = AndroidPlatformModule.getReceiverProvider().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (batteryIntent == null) {
                return -1.f;
            }

            int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

            if (level == -1 || scale == -1) {
                return -1.f;
            }

            return ((float) level / (float) scale) * 100.0f;
        } catch (Exception e) {
            // just in case
        }

        return -1.f;
    }

    public static long getAvailableInternalSpaceMB() {
        try {
            StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
            long blockSize = stat.getBlockSize();
            long availableBlocks = stat.getAvailableBlocks();
            return (availableBlocks * blockSize) / (1024 * 1024);
        } catch (Exception e) {
            // just in case
        }

        return -1;
    }

    public static long getTotalInternalSpaceMB() {
        try {
            StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
            long blockSize = stat.getBlockSize();
            long totalBlocks = stat.getBlockCount();
            return (totalBlocks * blockSize) / (1024 * 1024);
        } catch (Exception e) {
            // just in case
        }

        return -1;
    }

    public static long getAvailableExternalSpaceMB() {
        try {
            StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());
            long blockSize = stat.getBlockSize();
            long availableBlocks = stat.getAvailableBlocks();
            return (availableBlocks * blockSize) / (1024 * 1024);
        } catch (Exception e) {
            // just in case
        }

        return -1;
    }

    public static long getTotalExternalSpaceMB() {
        try {
            StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());
            long blockSize = stat.getBlockSize();
            long totalBlocks = stat.getBlockCount();
            return (totalBlocks * blockSize) / (1024 * 1024);
        } catch (Exception e) {
            // just in case
        }

        return -1;
    }

    public static int getScreenWidth() {
        DisplayMetrics displaymetrics = new DisplayMetrics();
        WindowManager windowManager = AndroidPlatformModule.getManagerProvider().getWindowManager();
        if (windowManager == null) {
            return 0;
        }
        windowManager.getDefaultDisplay().getMetrics(displaymetrics);
        return displaymetrics.widthPixels;
    }

    public static int getScreenHeight() {
        DisplayMetrics displaymetrics = new DisplayMetrics();
        WindowManager windowManager = AndroidPlatformModule.getManagerProvider().getWindowManager();
        if (windowManager == null) {
            return 0;
        }
        windowManager.getDefaultDisplay().getMetrics(displaymetrics);
        return displaymetrics.heightPixels;
    }

    @Nullable
    public static String getInstaller() {
        return AndroidPlatformModule.getAppInfoProvider().getInstallerPackageName();
    }

    public static String getDeviceName() {
        String manufacturer = Build.MANUFACTURER;
        String model = Build.MODEL;
        if (model.startsWith(manufacturer)) {
            return capitalize(model);
        } else {
            return capitalize(manufacturer) + " " + model;
        }
    }

    public static boolean isAppOnForeground() {
        KeyguardManager kgMgr = AndroidPlatformModule.getManagerProvider().getKeyguardManager();
        if (kgMgr == null) {
            return false;
        }
        boolean lockScreenIsShowing = kgMgr.inKeyguardRestrictedInputMode();
        if (lockScreenIsShowing) {
            return false;
        }

        PowerManager powerManager = AndroidPlatformModule.getManagerProvider().getPowerManager();
        if (powerManager == null) {
            return false;
        }
        //noinspection deprecation
        boolean isScreenAwake = (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT_WATCH ? powerManager.isScreenOn() : powerManager.isInteractive());

        if (!isScreenAwake) {
            return false;
        }

        ActivityManager activityManager = AndroidPlatformModule.getManagerProvider().getActivityManager();
        List<ActivityManager.RunningAppProcessInfo> appProcesses = activityManager == null ? null : activityManager.getRunningAppProcesses();
        if (appProcesses == null) {
            return false;
        }

        final String packageName = AndroidPlatformModule.getAppInfoProvider().getPackageName();
        for (ActivityManager.RunningAppProcessInfo appProcess : appProcesses) {
            if (appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND && appProcess.processName.equals(packageName)) {
                return true;
            }
        }

        return false;
    }

    public static String getGooglePlayServicesVersion() {
        try {
            PackageInfo pInfo = AndroidPlatformModule.getApplicationContext().getPackageManager().getPackageInfo("com.google.android.gms", 0);
            return pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "not_installed";
        } catch (NullPointerException e) {
            return "undefined";
        }
    }

    public static String getFirmware() {
        return Build.DISPLAY;
    }

    public static boolean isDeviceRooted() {
        return checkRootMethod1() || checkRootMethod2();
    }

    private static boolean checkRootMethod1() {
        Process process = null;
        try {
            process = new ProcessBuilder("/system/xbin/which", "su").start();
            BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
            return in.readLine() != null;
        } catch (Throwable t) {
            return false;
        } finally {
            if (process != null) process.destroy();
        }
    }

    private static boolean checkRootMethod2() {
        for (String pathDir : System.getenv("PATH").split(":")){
            if (new File(pathDir, "su").exists()) {
                return true;
            }
        }
        return false;
    }

    private static String capitalize(String s) {
        if (s == null || s.length() == 0) {
            return "";
        }
        char first = s.charAt(0);
        if (Character.isUpperCase(first)) {
            return s;
        } else {
            return Character.toUpperCase(first) + s.substring(1);
        }
    }

    public abstract static class DeviceUUID {
        private static List<String> sWrongAndroidDevices = new ArrayList<>();

        String cachedUUID = null;
        DeviceUUID mFallback;

        DeviceUUID() {
            sWrongAndroidDevices.add("9774d56d682e549c");
            sWrongAndroidDevices.add("1234567");
            sWrongAndroidDevices.add("abcdef");
            sWrongAndroidDevices.add("dead00beef");
            sWrongAndroidDevices.add("unknown");
        }

        void setFallback(DeviceUUID fallback) {
            mFallback = fallback;
        }

        private boolean isBadUUID(String uuid) {
            //noinspection SimplifiableIfStatement
            if (TextUtils.isEmpty(uuid) || TextUtils.isEmpty(uuid.replace('0', ' ').replace('-', ' ').trim())) {
                return true;
            }

            return sWrongAndroidDevices.contains(uuid.toLowerCase());

        }

        public String getUUID() {
            synchronized (this) {
                if (cachedUUID != null)
                    return cachedUUID;
                String uuid = tryGetUUID();
                if (isBadUUID(uuid) && mFallback != null) {
                    return mFallback.getUUID();
                }

                // unfortunately we cannot change hwids for existing devices yet
                // return UUID.nameUUIDFromBytes(uuid.getBytes()).toString();

                return cachedUUID = uuid;
            }
        }

        public void getUUID(OnGetHwidListener onGetHwidListener) {
            synchronized (this) {
                if (cachedUUID != null) {
                    onGetHwidListener.onGetHwid(cachedUUID);
                    return;
                }
                tryGetUUID(uuid -> {
                    if (isBadUUID(uuid) && mFallback != null) {
                        onGetHwidListener.onGetHwid(mFallback.getUUID());
                        return;
                    }
                    cachedUUID = uuid;
                    onGetHwidListener.onGetHwid(cachedUUID);
                });
            }
        }

        protected abstract String tryGetUUID();

        protected void tryGetUUID(TryGetUuidCallback callback) {
            callback.onGetUuid(tryGetUUID());
        }

        protected interface TryGetUuidCallback {
            void onGetUuid(String uuid);
        }
    }

    private static class DeviceSerialUUID extends DeviceUUID {
        protected String tryGetUUID() {
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                return "";
            } else {
                @SuppressLint("HardwareIds")
                String uuid = android.os.Build.SERIAL;
                if (TextUtils.equals(Build.UNKNOWN, uuid)) {
                    return "";
                } else {
                    return uuid;
                }
            }


        }
    }

    private static class DeviceOldUUID extends DeviceUUID {
        protected String tryGetUUID() {
            return AndroidPlatformModule.getAppInfoProvider().getDeviceSecurityUUID();
        }
    }

    private static class DeviceTelephonyUUID extends DeviceUUID {
        @SuppressLint({"MissingPermission", "HardwareIds"})
        protected String tryGetUUID() {
            String uuid = "";
            try {
                TelephonyManager telephonyManager = AndroidPlatformModule.getManagerProvider().getTelephonyManager();
                if (telephonyManager != null) {
                    uuid = telephonyManager.getDeviceId();
                }
            } catch (RuntimeException e) {
                PWLog.error("DeviceTelephonyUUID", e);
                // fallback
            }

            return uuid;
        }
    }

    private static class DeviceRandomUUID extends DeviceUUID {
        protected String tryGetUUID() {
            RegistrationPrefs registrationPrefs = RepositoryModule.getRegistrationPreferences();
            String deviceId = registrationPrefs.deviceId().get();
            if (!TextUtils.isEmpty(deviceId)) {
                return deviceId;
            }

            deviceId = UUID.randomUUID().toString();
            registrationPrefs.deviceId().set(deviceId);
            return deviceId;
        }
    }

    private static class DeviceSharedUUID extends DeviceUUID {
        private CountDownLatch countDownLatch = new CountDownLatch(1);
        private TryGetSharedUuidTask tryGetSharedUuidTask;

        protected String tryGetUUID() {
            RegistrationPrefs registrationPrefs = RepositoryModule.getRegistrationPreferences();

            if (!isTryGetSharedUuidTaskRunning()) {
                tryGetUUID(null);
            }
            try {
                 countDownLatch.await();
                 return registrationPrefs.deviceId().get();
            } catch (InterruptedException e) {
                return null;
            }
        }

        @Override
        protected void tryGetUUID(TryGetUuidCallback callback) {
            if (isTryGetSharedUuidTaskRunning()) {
                if (callback != null) {
                    tryGetSharedUuidTask.addCallback(callback::onGetUuid);
                }
                return;
            }

            Handler stopTaskHandler = new Handler();
            TryGetSharedUuidCallback uuidCallback = (uuid) -> {
                stopTaskHandler.removeCallbacksAndMessages(null);
                if (callback != null)
                    callback.onGetUuid(uuid);
                countDownLatch.countDown();
                tryGetSharedUuidTask = null;
            };
            try {
                RegistrationPrefs registrationPrefs = RepositoryModule.getRegistrationPreferences();
                String deviceId = registrationPrefs.deviceId().get();
                if (!TextUtils.isEmpty(deviceId)) {
                    uuidCallback.onGetSharedUuid(deviceId);
                    return;
                }
                tryGetSharedUuidTask = new TryGetSharedUuidTask();
                tryGetSharedUuidTask.addCallback(uuidCallback);

                stopTaskHandler.postDelayed(() -> {
                    if (tryGetSharedUuidTask != null && tryGetSharedUuidTask.getStatus() != AsyncTask.Status.FINISHED) {
                        tryGetSharedUuidTask.cancel(true);
                    }
                }, GET_UUID_TASK_TIMEOUT);
                tryGetSharedUuidTask.execute();
            }
            catch (Exception e) {
                e.printStackTrace();
                uuidCallback.onGetSharedUuid(null);
            }
        }

        private boolean isTryGetSharedUuidTaskRunning() {
            return tryGetSharedUuidTask != null && tryGetSharedUuidTask.getStatus() != AsyncTask.Status.FINISHED;
        }

        @NonNull
        private static List<ProviderInfo> getVendorProviderInfos(List<ProviderInfo> providerInfos) {
            String[] trustedPackageNames = PushwooshPlatform.getInstance().getConfig().getTrustedPackageNames();

            List<ProviderInfo> vendorProviderInfos = new ArrayList<>();
            for (ProviderInfo providerInfo : providerInfos) {
                for (String trustedPackageName : trustedPackageNames) {
                    if (!TextUtils.isEmpty(providerInfo.packageName)
                            && TextUtils.equals(providerInfo.packageName, trustedPackageName)) {
                        vendorProviderInfos.add(providerInfo);
                    }
                }
            }
            return vendorProviderInfos;
        }

        private static String getUUIDFromForeignInstance() {
            String result = null;
            try {
                Context context = AndroidPlatformModule.getApplicationContext();
                if (context == null)
                    return null;
                PackageManager pm = context.getPackageManager();
                List<ProviderInfo> providerInfos = getVendorProviderInfos(pm.queryContentProviders(null, 0, 0));
                if (providerInfos.size() == 0)
                    return null;
                String currentProviderAuthority = context.getPackageName() + "." + PushwooshSharedDataProvider.class.getSimpleName();
                for (ProviderInfo pi : providerInfos) {
                    if (pi.authority.endsWith("." + PushwooshSharedDataProvider.class.getSimpleName())
                            && !pi.authority.equals(currentProviderAuthority)) {
                        ContentResolver resolver = context.getContentResolver();
                        Cursor cursor = resolver.query(Uri.parse("content://" + pi.authority + "/" + PushwooshSharedDataProvider.HWID_PATH),
                                null, null, null, GeneralUtils.md5(context.getPackageName()));
                        if (cursor != null) {
                            if (cursor.getColumnCount() > 0 && cursor.getColumnName(0).equals(PushwooshSharedDataProvider.HWID_COLUMN_NAME)) {
                                if (cursor.moveToFirst()) {
                                    String hwid = cursor.getString(0);
                                    if (!TextUtils.isEmpty(hwid)) {
                                        result = hwid;
                                        cursor.close();
                                        break;
                                    }
                                }
                            }
                            cursor.close();
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return result;
        }

        private static class TryGetSharedUuidTask extends AsyncTask<Void, Void, String> {
            private final List<TryGetSharedUuidCallback> callbacks = new ArrayList<>();

            void addCallback(TryGetSharedUuidCallback callback) {
                synchronized (callbacks) {
                    callbacks.add(callback);
                }
            }

            @Override
            protected String doInBackground(Void... voids) {
                return getUUIDFromForeignInstance();
            }

            private void onFinished(String uuid) {
                if (TextUtils.isEmpty(uuid)) {
                    uuid = UUID.randomUUID().toString();
                }
                RepositoryModule.getRegistrationPreferences().deviceId().set(uuid);
                List<TryGetSharedUuidCallback> runCallbacks;
                synchronized (callbacks) {
                    runCallbacks = new ArrayList<>(callbacks);
                }
                for (TryGetSharedUuidCallback callback : runCallbacks) {
                    callback.onGetSharedUuid(uuid);
                }
            }

            @Override
            protected void onPostExecute(String uuid) {
                super.onPostExecute(uuid);
                onFinished(uuid);
            }

            @Override
            protected void onCancelled() {
                super.onCancelled();
                onFinished(null);
            }
        }

        private interface TryGetSharedUuidCallback {
            void onGetSharedUuid(String uuid);
        }
    }
}
