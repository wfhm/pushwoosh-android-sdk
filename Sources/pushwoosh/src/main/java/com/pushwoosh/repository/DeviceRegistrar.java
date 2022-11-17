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

//
//  DeviceRegistrar.java
//
// Pushwoosh Push Notifications SDK
// www.pushwoosh.com
//
// MIT Licensed

package com.pushwoosh.repository;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.TextUtils;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;

import com.pushwoosh.RegisterForPushNotificationsResultData;
import com.pushwoosh.internal.event.EventBus;
import com.pushwoosh.internal.network.NetworkModule;
import com.pushwoosh.internal.network.RequestManager;
import com.pushwoosh.internal.platform.AndroidPlatformModule;
import com.pushwoosh.internal.utils.PWLog;
import com.pushwoosh.internal.utils.TiramisuApiHelper;
import com.pushwoosh.notification.event.DeregistrationErrorEvent;
import com.pushwoosh.notification.event.DeregistrationSuccessEvent;
import com.pushwoosh.notification.event.RegistrationErrorEvent;
import com.pushwoosh.notification.event.RegistrationSuccessEvent;

import java.util.Calendar;
import java.util.Date;

/**
 * Register/unregister with the App server.
 * TODO: to improve testablity need to make methods of this class non-static
 */
public class DeviceRegistrar {
	private static final String TAG = "DeviceRegistrar";
	private static final int COOLDOWN_MINUTES = 10;
	
	public static void registerWithServer(final String deviceRegistrationID) {
		PWLog.debug(TAG, "Registering for pushes...");
		RegistrationPrefs registrationPrefs = RepositoryModule.getRegistrationPreferences();

		RegisterDeviceRequest request = new RegisterDeviceRequest(deviceRegistrationID);
		RequestManager requestManager = NetworkModule.getRequestManager();
		if (requestManager == null) {
			EventBus.sendEvent(new RegistrationErrorEvent("Request manager is null"));
			return;
		}
		requestManager.sendRequest(request, result -> {
			if (result.isSuccess()) {
				registrationPrefs.registeredOnServer().set(true);

				EventBus.sendEvent(new RegistrationSuccessEvent(new RegisterForPushNotificationsResultData(deviceRegistrationID,areNotificationsEnabled())));
				registrationPrefs.lastPushRegistration().set(new Date().getTime());
				PWLog.info(TAG, "Registered for push notifications: " + deviceRegistrationID);
			} else {
				String errorDescription = result.getException() == null ? "" : result.getException().getMessage();
				if (TextUtils.isEmpty(errorDescription)) {
					errorDescription = "Pushwoosh registration error";
				}

				PWLog.error(TAG, "Registration error: " + errorDescription);
				EventBus.sendEvent(new RegistrationErrorEvent(errorDescription));
			}
		});
	}

	public static void unregisterWithServer(final String deviceRegistrationID) {
		unregisterWithServer(deviceRegistrationID, null);
	}

	public static void unregisterWithServer(final String deviceRegistrationId, String baseUrl) {
		PWLog.debug(TAG, "Unregistering for pushes...");

		RegistrationPrefs registrationPrefs = RepositoryModule.getRegistrationPreferences();
		registrationPrefs.registeredOnServer().set(false);

		UnregisterDeviceRequest request = new UnregisterDeviceRequest();
		RequestManager requestManager = NetworkModule.getRequestManager();
		if (requestManager == null) {
			EventBus.sendEvent(new DeregistrationErrorEvent("Request manager is null"));
			return;
		}
		requestManager.sendRequest(request, baseUrl, result -> {
			if (result.isSuccess()) {
				PWLog.info(TAG, "Unregistered for pushes: " + deviceRegistrationId);

				EventBus.sendEvent(new DeregistrationSuccessEvent(deviceRegistrationId));
				registrationPrefs.lastPushRegistration().set(0);
			} else {
				String errorDescription = result.getException() == null ? "" : result.getException().getMessage();
				if (TextUtils.isEmpty(errorDescription)) {
					errorDescription = "Pushwoosh unregistration error";
				}

				PWLog.error(TAG, "Unregistration error: " + errorDescription);
				EventBus.sendEvent(new DeregistrationErrorEvent(errorDescription));
			}
		});
	}

	public void updateRegistration() {
		RegistrationPrefs registrationPrefs = RepositoryModule.getRegistrationPreferences();
		final String regId = registrationPrefs.pushToken().get();
		if (regId != null && !regId.equals("")) {
			//if we need to re-register on Pushwoosh because of Pushwoosh App Id change
			boolean forceRegister = registrationPrefs.forceRegister().get();
			registrationPrefs.forceRegister().set(false);
			if (forceRegister || neededToRequestPushwooshServer()) {
				registerWithServer(regId);
			}
		}
	}

	private static boolean neededToRequestPushwooshServer() {
		RegistrationPrefs registrationPrefs = RepositoryModule.getRegistrationPreferences();
		Calendar nowTime = Calendar.getInstance();
		Calendar tenMinutesBefore = Calendar.getInstance();
		tenMinutesBefore.add(Calendar.MINUTE, -COOLDOWN_MINUTES);

		Calendar lastPushWooshRegistrationTime = Calendar.getInstance();
		lastPushWooshRegistrationTime.setTime(new Date(registrationPrefs.lastPushRegistration().get()));

		if (tenMinutesBefore.before(lastPushWooshRegistrationTime) && lastPushWooshRegistrationTime.before(nowTime)) {
			// tenMinutesBefore <= lastPushWooshRegistrationTime <= nowTime
			return false;
		}
		return true;
	}

	private static boolean areNotificationsEnabled() {
		try {
			Context context = AndroidPlatformModule.getApplicationContext();
			if (Build.VERSION.SDK_INT >= 33 || TiramisuApiHelper.getReleaseOrCodeName().equals("Tiramisu")) {
				return ActivityCompat.checkSelfPermission(context,
						TiramisuApiHelper.PERMISSION_POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
			} else {
				return NotificationManagerCompat.from(context).areNotificationsEnabled();
			}
		} catch (Exception e) {
			PWLog.exception(e);
			return true; // fall back to default behavior
		}
	}
}
