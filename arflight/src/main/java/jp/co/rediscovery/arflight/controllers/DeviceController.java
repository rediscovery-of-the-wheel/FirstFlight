package jp.co.rediscovery.arflight.controllers;
/*
 * By downloading, copying, installing or using the software you agree to this license.
 * If you do not agree to this license, do not download, install,
 * copy or use the software.
 *
 *
 *                           License Agreement
 *                        (3-clause BSD License)
 *
 * Copyright (C) 2015-2018, saki t_saki@serenegiant.com
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 *   * Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *
 *   * Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *
 *   * Neither the names of the copyright holders nor the names of the contributors
 *     may be used to endorse or promote products derived from this software
 *     without specific prior written permission.
 *
 * This software is provided by the copyright holders and contributors "as is" and
 * any express or implied warranties, including, but not limited to, the implied
 * warranties of merchantability and fitness for a particular purpose are disclaimed.
 * In no event shall copyright holders or contributors be liable for any direct,
 * indirect, incidental, special, exemplary, or consequential damages
 * (including, but not limited to, procurement of substitute goods or services;
 * loss of use, data, or profits; or business interruption) however caused
 * and on any theory of liability, whether in contract, strict liability,
 * or tort (including negligence or otherwise) arising in any way out of
 * the use of this software, even if advised of the possibility of such damage.
 */

import android.content.Context;
import android.os.Handler;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.util.Log;

import com.parrot.arsdk.arcommands.ARCOMMANDS_COMMON_ACCESSORY_CONFIG_ACCESSORY_ENUM;
import com.parrot.arsdk.arcommands.ARCOMMANDS_COMMON_ANIMATIONS_STARTANIMATION_ANIM_ENUM;
import com.parrot.arsdk.arcommands.ARCOMMANDS_COMMON_ANIMATIONS_STOPANIMATION_ANIM_ENUM;
import com.parrot.arsdk.arcommands.ARCOMMANDS_COMMON_CHARGER_SETMAXCHARGERATE_RATE_ENUM;
import com.parrot.arsdk.arcommands.ARCOMMANDS_COMMON_COMMONSTATE_SENSORSSTATESLISTCHANGED_SENSORNAME_ENUM;
import com.parrot.arsdk.arcommands.ARCOMMANDS_COMMON_MAVLINK_START_TYPE_ENUM;
import com.parrot.arsdk.arcontroller.ARCONTROLLER_DEVICE_STATE_ENUM;
import com.parrot.arsdk.arcontroller.ARCONTROLLER_DICTIONARY_KEY_ENUM;
import com.parrot.arsdk.arcontroller.ARCONTROLLER_ERROR_ENUM;
import com.parrot.arsdk.arcontroller.ARControllerArgumentDictionary;
import com.parrot.arsdk.arcontroller.ARControllerDictionary;
import com.parrot.arsdk.arcontroller.ARControllerException;
import com.parrot.arsdk.arcontroller.ARDeviceController;
import com.parrot.arsdk.arcontroller.ARDeviceControllerListener;
import com.parrot.arsdk.arcontroller.ARFeatureCommon;
import com.parrot.arsdk.ardiscovery.ARDISCOVERY_PRODUCT_ENUM;
import com.parrot.arsdk.ardiscovery.ARDiscoveryDevice;
import com.parrot.arsdk.ardiscovery.ARDiscoveryDeviceBLEService;
import com.parrot.arsdk.ardiscovery.ARDiscoveryDeviceNetService;
import com.parrot.arsdk.ardiscovery.ARDiscoveryDeviceService;
import com.parrot.arsdk.ardiscovery.ARDiscoveryException;
import com.parrot.arsdk.ardiscovery.ARDiscoveryService;

import java.lang.ref.WeakReference;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import jp.co.rediscovery.arflight.CommonStatus;
import jp.co.rediscovery.arflight.DeviceConnectionListener;
import jp.co.rediscovery.arflight.DroneStatus;
import jp.co.rediscovery.arflight.IDeviceController;
import jp.co.rediscovery.arflight.IFlightController;
import jp.co.rediscovery.arflight.attribute.AttributeDevice;
import com.serenegiant.utils.HandlerThreadHandler;

/** Parrotのデバイスとの通信を処理するための基本クラス */
public abstract class DeviceController implements IDeviceController {
	private static final boolean DEBUG = false;	// FIXME 実働時はfalseにすること
	private String TAG = "DeviceController:" + getClass().getSimpleName();

	private final WeakReference<Context> mWeakContext;
	protected LocalBroadcastManager mLocalBroadcastManager;
	private final ARDiscoveryDeviceService mDiscoveredDevice;
	protected ARDeviceController mARDeviceController;
	protected Handler mAsyncHandler;
	protected long mAsyncThreadId;

	/** 接続待ちのためのセマフォ */
	private final Semaphore connectSent = new Semaphore(0);
	protected volatile boolean mRequestConnect;
	/** 切断待ちのためのセマフォ */
	private final Semaphore disconnectSent = new Semaphore(0);
	protected volatile boolean mRequestDisconnect;

	protected final Object mStateSync = new Object();
	private int mState = STATE_STOPPED;
	protected AttributeDevice mInfo;
	protected CommonStatus mStatus;
	protected ARCONTROLLER_DEVICE_STATE_ENUM mDeviceState = ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_STOPPED;
	protected ARCONTROLLER_DEVICE_STATE_ENUM mExtensionState = ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_STOPPED;
	/** コールバックリスナー */
	private final List<DeviceConnectionListener> mConnectionListeners = new ArrayList<DeviceConnectionListener>();

	/**
	 * コンストラクタ
	 * @param context
	 * @param discoveredDevice デバイス探索サービスから取得したARDiscoveryDeviceServiceインスタンス
	 */
	public DeviceController(final Context context, final ARDiscoveryDeviceService discoveredDevice) {
		if (DEBUG) Log.v(TAG, "コンストラクタ:");
		mWeakContext = new WeakReference<Context>(context);
		mLocalBroadcastManager = LocalBroadcastManager.getInstance(context);
		mDiscoveredDevice = discoveredDevice;
		mAsyncHandler = HandlerThreadHandler.createHandler(TAG);
		mAsyncThreadId = mAsyncHandler.getLooper().getThread().getId();
	}

	/**
	 * デストラクタ, プログラムから呼んじゃだめよぉ
	 * @throws Throwable
	 */
	@Override
	public void finalize() throws Throwable {
		if (DEBUG) Log.v (TAG, "finalize:");
		release();
		super.finalize();
	}

	@Override
	public synchronized void release() {
		if (DEBUG) Log.v(TAG, "release:");
		stop();
		mLocalBroadcastManager = null;
		if (mAsyncHandler != null) {
			mAsyncHandler.removeCallbacks(null, null);
			try {
				mAsyncHandler.getLooper().quit();
			} catch (final Exception e) {
			}
			mAsyncHandler = null;
		}
		if (DEBUG) Log.v(TAG, "release:終了");
	}

	public Context getContext() {
		return mWeakContext.get();
	}

	protected synchronized void queueEvent(final Runnable task, final long delayMs) {
		if ((task != null) && (mAsyncHandler != null)) {
			mAsyncHandler.removeCallbacks(task);
			if (delayMs > 0) {
				mAsyncHandler.postDelayed(task, delayMs);
			} else if (mAsyncHandler.getLooper().getThread().getId() == mAsyncThreadId) {
				try {
					task.run();
				} catch (final Exception e) {
					Log.w(TAG, e);
				}
			} else {
				mAsyncHandler.post(task);
			}
		}
	}

	protected synchronized void removeEvent(final Runnable task) {
		if ((task != null) && (mAsyncHandler != null)) {
			mAsyncHandler.removeCallbacks(task);
		}
	}

//================================================================================
// コールバック関係
//================================================================================
	@Override
	public void addListener(final DeviceConnectionListener listener) {
		if (DEBUG) Log.v(TAG, "addListener:" + listener);
		synchronized (mConnectionListeners) {
			mConnectionListeners.add(listener);
			callOnUpdateBattery(getBattery());
			callOnAlarmStateChangedUpdate(mStatus.getAlarm());
		}
	}

	@Override
	public void removeListener(final DeviceConnectionListener listener) {
		if (DEBUG) Log.v(TAG, "removeListener:" + listener);
		synchronized (mConnectionListeners) {
			mConnectionListeners.remove(listener);
		}
	}

	/**
	 * 接続時のコールバックを呼び出す
	 */
	protected void callOnConnect() {
		if (DEBUG) Log.v(TAG, "callOnConnect:");
		synchronized (mConnectionListeners) {
			for (final DeviceConnectionListener listener: mConnectionListeners) {
				if (listener != null) {
					try {
						listener.onConnect(this);
					} catch (final Exception e) {
						if (DEBUG) Log.w(TAG, e);
					}
				}
			}
		}
	}

	/**
	 * 切断時のコールバックを呼び出す
	 */
	protected void callOnDisconnect() {
		if (DEBUG) Log.v(TAG, "callOnDisconnect:");
		synchronized (mConnectionListeners) {
			for (final DeviceConnectionListener listener: mConnectionListeners) {
				if (listener != null) {
					try {
						listener.onDisconnect(this);
					} catch (final Exception e) {
						if (DEBUG) Log.w(TAG, e);
					}
				}
			}
		}
	}

	/**
	 * 異常状態変更コールバックを呼び出す
	 * @param alarm
	 */
	protected void callOnAlarmStateChangedUpdate(final int alarm) {
		if (DEBUG) Log.v(TAG, "callOnAlarmStateChangedUpdate:" + alarm);
		synchronized (mConnectionListeners) {
			for (final DeviceConnectionListener listener: mConnectionListeners) {
				if (listener != null) {
					try {
						listener.onAlarmStateChangedUpdate(this, alarm);
					} catch (final Exception e) {
						if (DEBUG) Log.w(TAG, e);
					}
				}
			}
		}
	}

	/**
	 * バッテリー残量変更コールバックを呼び出す
	 * @param percent
	 */
	protected void callOnUpdateBattery(final int percent) {
		if (DEBUG) Log.v(TAG, "callOnUpdateBattery:" + percent);
		synchronized (mConnectionListeners) {
			for (final DeviceConnectionListener listener: mConnectionListeners) {
				if (listener != null) {
					try {
						listener.onUpdateBattery(this, percent);
					} catch (final Exception e) {
						if (DEBUG) Log.w(TAG, e);
					}
				}
			}
		}
	}

	/**
	 * WiFi信号強度更新コールバックを呼び出す
	 * @param rssi
	 */
	protected void callOnUpdateWifiSignal(final int rssi) {
		synchronized (mConnectionListeners) {
			for (final DeviceConnectionListener listener: mConnectionListeners) {
				if (listener != null) {
					try {
						listener.onUpdateWiFiSignal(this, rssi);
					} catch (final Exception e) {
						if (DEBUG) Log.w(TAG, e);
					}
				}
			}
		}
	}
//================================================================================

	/**
	 * デバイス名を取得, これは変更可能な値
	 * @return
	 */
	@Override
	public String getName() {
		final ARDiscoveryDeviceService device_service = getDeviceService();
		return device_service != null ? device_service.getName() : null;
	}

	/**
	 * 製品名を取得, これは製品名なので変更不可
	 * @return
	 */
	@Override
	public String getProductName() {
		final ARDiscoveryDeviceService device_service = getDeviceService();
		return device_service != null ? ARDiscoveryService.getProductName(ARDiscoveryService.getProductFromProductID(device_service.getProductID())) : null;
	}

	/**
	 * 製品IDを取得
	 * @return
	 */
	@Override
	public int getProductId() {
		final ARDiscoveryDeviceService device_service = getDeviceService();
		return device_service != null ? device_service.getProductID() : ARDISCOVERY_PRODUCT_ENUM.eARDISCOVERY_PRODUCT_UNKNOWN_ENUM_VALUE.getValue();
	}

	/**
	 * 製品IDを列挙型として取得
	 * @return
	 */
	public ARDISCOVERY_PRODUCT_ENUM getProduct() {
		return ARDiscoveryService.getProductFromProductID(getProductId());
	}

	@Override
	public ARDiscoveryDeviceService getDeviceService() {
		return mDiscoveredDevice;
	}

	@Override
	public String getSoftwareVersion() {
		return mInfo.productSoftware();
	}

	@Override
	public String getHardwareVersion() {
		return mInfo.productHardware();
	}

	@Override
	public String getSerial() {
		return mInfo.getSerial();
	}

	/**
	 * デバイスの異常状態をセット
	 * @param alarm
	 */
	protected void setAlarm(final int alarm) {
		mStatus.setAlarm(alarm);
	}

	public int getAlarm() {
		return mStatus.getAlarm();
	}

	@Override
	public int getBattery() {
		return mStatus.getBattery();
	}

	/**
	 * バッテリー残量をセット
	 * @param percent
	 */
	protected void setBattery(final int percent) {
		mStatus.setBattery(percent);
		callOnUpdateBattery(percent);
	}

	@Override
	public int getWiFiSignal() {
		return mStatus.getWiFiSignal();
	}

	/**
	 * WiDi接続時の信号強度をセット
	 * @param rssi
	 */
	protected void setWiFiSignal(final int rssi) {
		mStatus.setWiFiSignal(rssi);
		callOnUpdateWifiSignal(rssi);
	}

	/**
	 * 接続ステータスを取得
	 * @return IDeviceController#STATE_XXX定数
	 */
	@Override
	public int getState() {
		synchronized (mStateSync) {
			return mState;
		}
	}

	/**
	 * 接続開始処理
	 * @return
	 */
	@Override
	public boolean start() {
		if (DEBUG) Log.v(TAG, "start:");

		synchronized (mStateSync) {
			if (mState != STATE_STOPPED) return false;
			mState = STATE_STARTING;
		}
		mRequestDisconnect = false;
		setAlarm(DroneStatus.ALARM_NON);

		boolean failed = startNetwork();
		ARCONTROLLER_ERROR_ENUM error = ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_ERROR;

		if (!failed && (mARDeviceController != null)
			&& (ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_STOPPED.equals(mDeviceState))) {

			mRequestConnect = true;
			try {
				if (DEBUG) Log.v(TAG, "start:ARDeviceController#start");
				error = mARDeviceController.start();
				failed = (error != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK);
				if (!failed) {
					if (DEBUG) Log.v(TAG, "start:connectSent待機");
					connectSent.acquire();
					if (ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING.equals(getDeviceState())) {
						synchronized (mStateSync) {
							mState = STATE_STARTED;
						}
						onStarted();
						callOnConnect();
					} else {
						Log.w(TAG, "connectSent#acquireを抜けたのにRUNNINGになってない");
						failed = true;
					}
				}
			} catch (final InterruptedException e) {
				failed = true;
			} finally {
				mRequestConnect = false;
			}
		} else {
			failed = true;
		}
		if (failed) {
			Log.w(TAG, "failed to start ARController:err=" + error);
			try {
				if (mARDeviceController != null) {
					mARDeviceController.dispose();
					mARDeviceController = null;
				}
			} catch (final Exception e) {
				Log.w(TAG, e);
			}
			synchronized (mStateSync) {
				mState = STATE_STOPPED;
				mDeviceState = ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_STOPPED;
				mExtensionState = ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_STOPPED;
			}
			setAlarm(DroneStatus.ALARM_DISCONNECTED);
		}
		if (DEBUG) Log.v(TAG, "start:終了");

		return failed;
	}

	/** 接続処理をキャンセル要求 */
	@Override
	public void cancelStart() {
		if (DEBUG) Log.v(TAG, "cancelStart:");
		if (!mRequestDisconnect && mRequestConnect) {
			mRequestDisconnect = true;
			try {
				internal_cancel_start();
				if (mARDeviceController != null) {
					mARDeviceController.stop();
					mARDeviceController = null;
				}
				if (DEBUG) Log.v(TAG, "cancelStart:disconnectSent待機");
				disconnectSent.tryAcquire(3000, TimeUnit.MILLISECONDS);	// とりあえず最大3秒待機
			} catch (final InterruptedException e) {
				// ignore
			} finally {
				mRequestDisconnect = false;
			}
			onStopped();
		}
		if (DEBUG) Log.v(TAG, "cancelStart:終了");
	}

	/**
	 * デバイスへの接続開始の実際の処理
	 * @return
	 */
	protected synchronized boolean startNetwork() {
		if (DEBUG) Log.v(TAG, "startNetwork:");
		boolean failed = false;
		ARDiscoveryDevice discovery_device;
		try {
			discovery_device = new ARDiscoveryDevice();

			final Object device = mDiscoveredDevice.getDevice();
			if (device instanceof ARDiscoveryDeviceNetService) {
				if (DEBUG) Log.v(TAG, "startNetwork:ARDiscoveryDeviceNetService");
				final ARDiscoveryDeviceNetService netDeviceService = (ARDiscoveryDeviceNetService)device;
				discovery_device.initWifi(getProduct(), netDeviceService.getName(), netDeviceService.getIp(), netDeviceService.getPort());
			} else if (device instanceof ARDiscoveryDeviceBLEService) {
				if (DEBUG) Log.v(TAG, "startNetwork:ARDiscoveryDeviceBLEService");
				final ARDiscoveryDeviceBLEService bleDeviceService = (ARDiscoveryDeviceBLEService) device;
				discovery_device.initBLE(getProduct(), getContext().getApplicationContext(), bleDeviceService.getBluetoothDevice());
			}
		} catch (final ARDiscoveryException e) {
			Log.e(TAG, "err=" + e.getError(), e);
			discovery_device = null;
			failed = true;
		}
		if (discovery_device != null) {
			if (DEBUG) Log.v(TAG, "startNetwork:ARDeviceController生成");
			ARDeviceController deviceController = null;
			try {
				deviceController = new ARDeviceController(discovery_device);
				deviceController.addListener(mDeviceControllerListener);
			} catch (final ARControllerException e) {
				Log.e(TAG, "err=" + e.getError(), e);
				failed = true;
				if (deviceController != null) {
					try {
						deviceController.dispose();
					} catch (final Exception e2) {
						Log.w(TAG, e2);
					}
					deviceController = null;
				}
			}
			mARDeviceController = deviceController;
		} else {
			Log.w(TAG, "startNetwork:ARDiscoveryDeviceを初期化出来なかった");
		}
		if (DEBUG) Log.v(TAG, "startNetwork:終了,failed=" + failed);
		return failed;
	}

	/** 接続中断の追加処理 */
	protected void internal_cancel_start() {
		if (DEBUG) Log.v(TAG, "internal_cancel_start:");
	}

	/** DeviceControllerがstartした時の処理(mARDeviceControllerは有効, onConnectを呼び出す直前) */
	protected void onStarted() {
		if (DEBUG) Log.v(TAG, "onStarted:");
		// only with RollingSpider in version 1.97 : date and time must be sent to permit a reconnection
		// NewAPIだといらんのかもしれんけど念の為に
		final Date currentDate = new Date(System.currentTimeMillis());
		sendDate(currentDate);
		sendTime(currentDate);
		if (DEBUG) Log.v(TAG, "onStarted:終了");
	}

	/** 切断処理 */
	@Override
	public final synchronized void stop() {
		if (DEBUG) Log.v(TAG, "stop:");

		synchronized (mStateSync) {
			if ((mState == STATE_STOPPED) || (mState != STATE_STARTED)) return;
			mState = STATE_STOPPING;
		}

		onBeforeStop();

		if (!mRequestDisconnect && (mARDeviceController != null)
			&& !ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_STOPPED.equals(mDeviceState)) {
			mRequestDisconnect = true;
			try {
				if (DEBUG) Log.v(TAG, "stop:ARDeviceController#stop");
				final ARCONTROLLER_ERROR_ENUM error = mARDeviceController.stop();
				final boolean failed = (error != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK);
				if (!failed) {
					if (DEBUG) Log.v(TAG, "stop:disconnectSent待機");
					disconnectSent.acquire();
				} else {
					Log.w(TAG, "failed to stop ARController:err=" + error);
				}
			} catch (final InterruptedException e) {
				// ignore
			} finally {
				mRequestDisconnect = false;
			}
			onStopped();
		}

		// ネットワーク接続をクリーンアップ
		stopNetwork();

		synchronized (mStateSync) {
			mState = STATE_STOPPED;
			mDeviceState = ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_STOPPED;
			mExtensionState = ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_STOPPED;
		}
		if (DEBUG) Log.v(TAG, "stop:終了");
	}

	/** 切断前の処理(接続中ならまだmARDeviceControllerは有効) */
	protected void onBeforeStop() {
		if (DEBUG) Log.v(TAG, "onBeforeStop:");
	}

	/** 切断後の追加処理(接続中でなければ呼び出されない) */
	protected void onStopped() {
		if (DEBUG) Log.v(TAG, "onStopped:");
		// セマフォをリセット
		connectSent.release();
		for ( ; connectSent.tryAcquire() ;) {}
		disconnectSent.release();
		for ( ; disconnectSent.tryAcquire() ;) {}
	}

	/** 切断処理(mARDeviceControllerは既にstopしているので無効) */
	protected void stopNetwork() {
		if (DEBUG) Log.v(TAG, "stopNetwork:");
		if (mARDeviceController != null) {
			mARDeviceController.dispose();
			mARDeviceController = null;
		}
	}

	/**
	 * デバイスと接続しているかどうか
	 * スカイコントローラー経由の場合はスカイコントローラーとの接続状態
	 * @return
	 */
	@Override
	public boolean isStarted() {
		synchronized (mStateSync) {
			return (mARDeviceController != null)
				&& ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING.equals(getDeviceState())
				&& ((mState == STATE_STARTED) || (mState == STATE_STARTING));	// これは無くていいかも
		}
	}

	/**
	 * デバイスと接続しているかどうか
	 * 直接接続の時は#isStartedと同じ
 	 * スカイコントローラー経由の場合はスカイコントローラーを経由してデバイスと接続しているかどうか
	 * @return
	 */
	@Override
	public boolean isConnected() {
		return isStarted();
	}

	/**
	 * デバイスとの接続ステータスを列挙型として取得
	 * スカイコントローラー経由の場合はスカイコントローラーとの接続状態
	 * @return
	 */
	protected ARCONTROLLER_DEVICE_STATE_ENUM getDeviceState() {
		synchronized (mStateSync) {
			return mDeviceState;
		}
	}

	/**
	 * スカイコントローラー経由で接続している時に、スカイコントローラーとデバイスの接続状態を列挙型として取得
	 * @return
	 */
	protected ARCONTROLLER_DEVICE_STATE_ENUM getExtensionDeviceState() {
		synchronized (mStateSync) {
			return mExtensionState;
		}
	}

	/**
	 * デバイスとの接続状態が変化した時およびデータを受け取った際のコールバックリスナー
	 * 直接DeviceControllerへARDeviceControllerListenerインターフェースを実装してもいいんだけど、
	 * このコールバックを呼び出してええのはこのクラスインスタンスが保持しとるARDeviceControllerインスタンスだけやのに
	 * Javaだとインターフェースの実装は常にpublicになってしまって外部の任意のクラス/コードから呼び出されてしまう危険があるので
	 * 一旦privateな匿名クラスインスタンスとして定義してprotectedなクラスメンバメソッドへdelegateする。
	 */
	private final ARDeviceControllerListener mDeviceControllerListener = new ARDeviceControllerListener() {
		/**
		 * デバイスとの接続状態が変化した時
		 * 実際の処理はDeviceController#onStateChangedへdelegate
		 * @param deviceController
		 * @param newState
		 * @param error
		 */
		@Override
		public void onStateChanged(final ARDeviceController deviceController,
								   final ARCONTROLLER_DEVICE_STATE_ENUM newState, final ARCONTROLLER_ERROR_ENUM error) {

			try {
				DeviceController.this.onStateChanged(deviceController, newState, error);
			} catch (final Exception e) {
				Log.w(TAG, e);
			}
        }

		/**
		 * スカイコントローラー経由で接続している際の、スカイコントローラーとデバイスの接続状態が変化した時
		 * 実際の処理はDeviceController#onExtensionStateChangedへdelegate
		 * @param deviceController
		 * @param newState
		 * @param product
		 * @param name
		 * @param error
		 */
		@Override
		public void onExtensionStateChanged(final ARDeviceController deviceController,
											final ARCONTROLLER_DEVICE_STATE_ENUM newState,
											final ARDISCOVERY_PRODUCT_ENUM product, final String name, final ARCONTROLLER_ERROR_ENUM error) {

			try {
				DeviceController.this.onExtensionStateChanged(deviceController, newState, product, name, error);
			} catch (final Exception e) {
				Log.w(TAG, e);
			}
		}

		/**
		 * デバイスからデータを受け取った
		 * 実際の処理はDeviceController#onCommandReceivedへdelegate
		 * @param deviceController
		 * @param commandKey
		 * @param elementDictionary
		 */
		@Override
		public void onCommandReceived(final ARDeviceController deviceController,
									  final ARCONTROLLER_DICTIONARY_KEY_ENUM commandKey, final ARControllerDictionary elementDictionary) {

			if (elementDictionary != null) {
				try {
					final ARControllerArgumentDictionary<Object> single = elementDictionary.get(ARControllerDictionary.ARCONTROLLER_DICTIONARY_SINGLE_KEY);
					DeviceController.this.onCommandReceived(deviceController, commandKey, single, elementDictionary);
				} catch (final Exception e) {
					Log.w(TAG, e);
				}
			}
		}
    };

	/**
	 * onStateChangedから呼び出される
	 * @param newState
	 */
	protected void setState(final ARCONTROLLER_DEVICE_STATE_ENUM newState) {
		synchronized (mStateSync) {
			mDeviceState = newState;
		}
	}

	/** mDeviceControllerListenerの下請け */
	protected void onStateChanged(final ARDeviceController deviceController,
								  final ARCONTROLLER_DEVICE_STATE_ENUM newState, final ARCONTROLLER_ERROR_ENUM error) {

		if (DEBUG) Log.v(TAG, "onStateChanged:state=" + newState + ",error=" + error);
		setState(newState);
		switch (newState) {
		case ARCONTROLLER_DEVICE_STATE_STOPPED: 	// (0, "device controller is stopped"),
			onDisconnect();
			break;
		case ARCONTROLLER_DEVICE_STATE_STARTING:	// (1, "device controller is starting"),
			break;
		case ARCONTROLLER_DEVICE_STATE_RUNNING:		// (2, "device controller is running"),
			onConnect();
			break;
		case ARCONTROLLER_DEVICE_STATE_PAUSED: 		// (3, "device controller is paused"),
			break;
		case ARCONTROLLER_DEVICE_STATE_STOPPING:	// (4, "device controller is stopping"),
			break;
		default:
			break;
		}
	}

	/**
	 * タブレット/スマホとデバイス(スカイコントローラーを含む)が接続した時
	 * onStateChangedの下請け, 大元から見たら孫請けやな
	 */
	protected void onConnect() {
		if (DEBUG) Log.d(TAG, "onConnect:");
		if (mRequestConnect) {
			connectSent.release();
		}
	}

	/**
	 * タブレット/スマホとデバイス(スカイコントローラーを含む)が切断された時
	 * onStateChangedの下請け, 大元から見たら孫請けやな
	 */
	protected void onDisconnect() {
		if (DEBUG) Log.d(TAG, "onDisconnect:");
		setAlarm(DroneStatus.ALARM_DISCONNECTED);
		if (mRequestConnect) {
			connectSent.release();
		}
		if (mRequestDisconnect) {
			disconnectSent.release();
		}
		callOnAlarmStateChangedUpdate(DroneStatus.ALARM_DISCONNECTED);
		callOnDisconnect();
	}

	/**
	 * onExtensionStateChangedから呼び出される
	 * @param newState
	 */
	protected void setExtensionState(final ARCONTROLLER_DEVICE_STATE_ENUM newState) {
		synchronized (mStateSync) {
			mExtensionState = newState;
		}
	}

	/** mDeviceControllerListenerの下請け */
	protected void onExtensionStateChanged(final ARDeviceController deviceController,
		final ARCONTROLLER_DEVICE_STATE_ENUM newState,
		final ARDISCOVERY_PRODUCT_ENUM product,
		final String name, final ARCONTROLLER_ERROR_ENUM error) {

		if (DEBUG) Log.v(TAG, "onExtensionStateChanged:state=" + newState + ",product=" + product + ",name=" + name + ",error=" + error);
		setExtensionState(newState);
		switch (newState) {
		case ARCONTROLLER_DEVICE_STATE_STOPPED: 	// (0, "device controller is stopped"),
			onExtensionDisconnect();
			break;
		case ARCONTROLLER_DEVICE_STATE_STARTING:	// (1, "device controller is starting"),
			break;
		case ARCONTROLLER_DEVICE_STATE_RUNNING:		// (2, "device controller is running"),
			onExtensionConnect();
			break;
		case ARCONTROLLER_DEVICE_STATE_PAUSED: 		// (3, "device controller is paused"),
			break;
		case ARCONTROLLER_DEVICE_STATE_STOPPING:	// (4, "device controller is stopping"),
			break;
		default:
			break;
		}
	}

	/**
	 * スカイコントローラーにデバイスが接続した時
	 * onExtensionStateChangedの下請け, 大元から見たら孫請けやな
	 */
	protected void onExtensionConnect() {
		if (DEBUG) Log.d(TAG, "onExtensionConnect:");
	}

	/**
	 * スカイコントローラーからデバイスが切断された時
	 * onExtensionStateChangedの下請け, 大元から見たら孫請けやな
	 */
	protected void onExtensionDisconnect() {
		if (DEBUG) Log.d(TAG, "onExtensionDisconnect:");
	}

	/**
	 * デバイスからのデータ受信時の処理
	 * mDeviceControllerListenerの下請け
	 */
	protected void onCommandReceived(final ARDeviceController deviceController,
		final ARCONTROLLER_DICTIONARY_KEY_ENUM commandKey,
		final ARControllerArgumentDictionary<Object> args,
		final ARControllerDictionary elementDictionary) {

		switch (commandKey) {
		case ARCONTROLLER_DICTIONARY_KEY_COMMON:	// (157, "Key used to define the feature <code>Common</code>"),
			if (DEBUG) Log.v(TAG, "COMMON:");
			break;
		case ARCONTROLLER_DICTIONARY_KEY_COMMON_NETWORKEVENT_DISCONNECTION:	// (158, "Key used to define the command <code>Disconnection</code> of class <code>NetworkEvent</code> in project <code>Common</code>"),
		{	// ネットワークから切断された時 FIXME 未実装
			if (DEBUG) Log.v(TAG, "NETWORKEVENT_DISCONNECTION:");
			break;
		}
		case ARCONTROLLER_DICTIONARY_KEY_COMMON_SETTINGSSTATE_ALLSETTINGSCHANGED:	// (159, "Key used to define the command <code>AllSettingsChanged</code> of class <code>SettingsState</code> in project <code>Common</code>"),
		{	// すべての設定を受信した時
			break;
		}
		case ARCONTROLLER_DICTIONARY_KEY_COMMON_SETTINGSSTATE_RESETCHANGED:	// (160, "Key used to define the command <code>ResetChanged</code> of class <code>SettingsState</code> in project <code>Common</code>"),
		{	// 設定がリセットされた時 FIXME 未実装
			if (DEBUG) Log.v(TAG, "SETTINGSSTATE_RESETCHANGED:");
			break;
		}
		case ARCONTROLLER_DICTIONARY_KEY_COMMON_SETTINGSSTATE_PRODUCTNAMECHANGED:	// (161, "Key used to define the command <code>ProductNameChanged</code> of class <code>SettingsState</code> in project <code>Common</code>"),
		{	// 製品名を受信した時
			final String name = (String)args.get(ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_SETTINGSSTATE_PRODUCTNAMECHANGED_NAME);
			mInfo.setProductName(name);
			break;
		}
		case ARCONTROLLER_DICTIONARY_KEY_COMMON_SETTINGSSTATE_PRODUCTVERSIONCHANGED:	// (162, "Key used to define the command <code>ProductVersionChanged</code> of class <code>SettingsState</code> in project <code>Common</code>"),
		{	// 製品バージョンを受信した時
			final String software = (String)args.get(ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_SETTINGSSTATE_PRODUCTVERSIONCHANGED_SOFTWARE);
			final String hardware = (String)args.get(ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_SETTINGSSTATE_PRODUCTVERSIONCHANGED_HARDWARE);
			mInfo.setProduct(software, hardware);
			break;
		}
		case ARCONTROLLER_DICTIONARY_KEY_COMMON_SETTINGSSTATE_PRODUCTSERIALHIGHCHANGED:	// (163, "Key used to define the command <code>ProductSerialHighChanged</code> of class <code>SettingsState</code> in project <code>Common</code>"),
		{	// シリアル番号の上位を受信した時
			final String high = (String)args.get(ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_SETTINGSSTATE_PRODUCTSERIALHIGHCHANGED_HIGH);
			mInfo.setSerialHigh(high);
			break;
		}
		case ARCONTROLLER_DICTIONARY_KEY_COMMON_SETTINGSSTATE_PRODUCTSERIALLOWCHANGED:	// (164, "Key used to define the command <code>ProductSerialLowChanged</code> of class <code>SettingsState</code> in project <code>Common</code>"),
		{	// シリアル番号の下位を受信した時
			final String low = (String)args.get(ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_SETTINGSSTATE_PRODUCTSERIALLOWCHANGED_LOW);
			mInfo.setSerialLow(low);
			break;
		}
		case ARCONTROLLER_DICTIONARY_KEY_COMMON_SETTINGSSTATE_COUNTRYCHANGED:	// (165, "Key used to define the command <code>CountryChanged</code> of class <code>SettingsState</code> in project <code>Common</code>"),
		{	// 国コードを受信した時
			final String code = (String)args.get(ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_SETTINGSSTATE_COUNTRYCHANGED_CODE);
			setCountryCode(code);
			break;
		}
		case ARCONTROLLER_DICTIONARY_KEY_COMMON_SETTINGSSTATE_AUTOCOUNTRYCHANGED:	// (166, "Key used to define the command <code>AutoCountryChanged</code> of class <code>SettingsState</code> in project <code>Common</code>"),
		{	// 自動国選択設定が変更された時
			final boolean auto = (Integer)args.get(ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_SETTINGSSTATE_AUTOCOUNTRYCHANGED_AUTOMATIC) != 0;
			setAutomaticCountry(auto);
			break;
		}
		case ARCONTROLLER_DICTIONARY_KEY_COMMON_COMMONSTATE_ALLSTATESCHANGED:	// (167, "Key used to define the command <code>AllStatesChanged</code> of class <code>CommonState</code> in project <code>Common</code>"),
		{	// 全てのステータスを受信した時
			break;
		}
		case ARCONTROLLER_DICTIONARY_KEY_COMMON_COMMONSTATE_BATTERYSTATECHANGED:	// (168, "Key used to define the command <code>BatteryStateChanged</code> of class <code>CommonState</code> in project <code>Common</code>"),
		{	// バッテリー残量が変化した時
			final int percent = (Integer) args.get(ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_COMMONSTATE_BATTERYSTATECHANGED_PERCENT);
			if (getBattery() != percent) {
				setBattery(percent);
			}
			break;
		}
		case ARCONTROLLER_DICTIONARY_KEY_COMMON_COMMONSTATE_MASSSTORAGESTATELISTCHANGED:	// (169, "Key used to define the command <code>MassStorageStateListChanged</code> of class <code>CommonState</code> in project <code>Common</code>"),
		{	// デバイス内のストレージ一覧が変化した時
			for (final ARControllerArgumentDictionary<Object> element: elementDictionary.values()) {
				final Object id_obj = element.get(ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_COMMONSTATE_MASSSTORAGESTATELISTCHANGED_MASS_STORAGE_ID);
				final Object name_obj = element.get(ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_COMMONSTATE_MASSSTORAGESTATELISTCHANGED_NAME);
				if ((id_obj != null) && (name_obj != null)) {
					onCommonStateMassStorageStateListChanged((Integer)id_obj, (String)name_obj);
				}
			}
			break;
		}
		case ARCONTROLLER_DICTIONARY_KEY_COMMON_COMMONSTATE_MASSSTORAGEINFOSTATELISTCHANGED:	// (170, "Key used to define the command <code>MassStorageInfoStateListChanged</code> of class <code>CommonState</code> in project <code>Common</code>"),
		{	// ストレージの状態が変化した時
			for (final ARControllerArgumentDictionary<Object> element: elementDictionary.values()) {
				final Object id_obj = element.get(ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_COMMONSTATE_MASSSTORAGEINFOSTATELISTCHANGED_MASS_STORAGE_ID);
				final Object size_obj = element.get(ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_COMMONSTATE_MASSSTORAGEINFOSTATELISTCHANGED_SIZE);
				final Object used_size_obj = element.get(ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_COMMONSTATE_MASSSTORAGEINFOSTATELISTCHANGED_USED_SIZE);
				final Object plugged_obj = element.get(ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_COMMONSTATE_MASSSTORAGEINFOSTATELISTCHANGED_PLUGGED);
				final Object full_obj = element.get(ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_COMMONSTATE_MASSSTORAGEINFOSTATELISTCHANGED_FULL);
				final Object internal_obj = element.get(ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_COMMONSTATE_MASSSTORAGEINFOSTATELISTCHANGED_INTERNAL);
				if ((id_obj != null) && (size_obj != null) && (used_size_obj != null)
					&& (plugged_obj != null) && (full_obj != null) && (internal_obj != null)) {
					final int mass_storage_id = (Integer)id_obj;
					final int size = (Integer)size_obj;
					final int used_size = (Integer)used_size_obj;
					final boolean plugged = (Integer)plugged_obj != 0;
					final boolean full = (Integer)full_obj != 0;
					final boolean internal = (Integer)internal_obj != 0;

					onCommonStateMassStorageInfoStateListChanged(mass_storage_id, size, used_size, plugged, full, internal);
				}
			}

			break;
		}
		case ARCONTROLLER_DICTIONARY_KEY_COMMON_COMMONSTATE_CURRENTDATECHANGED:	// (171, "Key used to define the command <code>CurrentDateChanged</code> of class <code>CommonState</code> in project <code>Common</code>"),
		{	// 日付が変更された時
			final String date = (String)args.get(ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_COMMONSTATE_CURRENTDATECHANGED_DATE);
			if (DEBUG) Log.v(TAG, "COMMONSTATE_CURRENTDATECHANGED:" + date);
			break;
		}
		case ARCONTROLLER_DICTIONARY_KEY_COMMON_COMMONSTATE_CURRENTTIMECHANGED:	// (172, "Key used to define the command <code>CurrentTimeChanged</code> of class <code>CommonState</code> in project <code>Common</code>"),
		{	// 時刻が変更された時
			final String time = (String)args.get(ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_COMMONSTATE_CURRENTTIMECHANGED_TIME);
			if (DEBUG) Log.v(TAG, "COMMONSTATE_CURRENTTIMECHANGED:" + time);
			break;
		}
		case ARCONTROLLER_DICTIONARY_KEY_COMMON_COMMONSTATE_MASSSTORAGEINFOREMAININGLISTCHANGED:	// (173, "Key used to define the command <code>MassStorageInfoRemainingListChanged</code> of class <code>CommonState</code> in project <code>Common</code>"),
		{	// ストレージの空き容量が変化した時
			final long free_space = (Long)args.get(ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_COMMONSTATE_MASSSTORAGEINFOREMAININGLISTCHANGED_FREE_SPACE);
   			final long rec_time = (Long)args.get(ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_COMMONSTATE_MASSSTORAGEINFOREMAININGLISTCHANGED_REC_TIME);
			final long photo = (Long)args.get(ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_COMMONSTATE_MASSSTORAGEINFOREMAININGLISTCHANGED_PHOTO_REMAINING);
			if (DEBUG) Log.v(TAG, "COMMONSTATE_MASSSTORAGEINFOREMAININGLISTCHANGED:free_space="
				+ free_space + ",rec_time=" + rec_time + ",photo=" + photo);
			break;
		}
		case ARCONTROLLER_DICTIONARY_KEY_COMMON_COMMONSTATE_WIFISIGNALCHANGED:	// (174, "Key used to define the command <code>WifiSignalChanged</code> of class <code>CommonState</code> in project <code>Common</code>"),
		{	// WiFiの信号強度が変化した時
			final int rssi = (Integer) args.get(ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_COMMONSTATE_WIFISIGNALCHANGED_RSSI);
			if (getWiFiSignal() != rssi) {
				setWiFiSignal(rssi);
			}
			break;
		}
		case ARCONTROLLER_DICTIONARY_KEY_COMMON_COMMONSTATE_SENSORSSTATESLISTCHANGED:	// (175, "Key used to define the command <code>SensorsStatesListChanged</code> of class <code>CommonState</code> in project <code>Common</code>"),
		{	// センサー状態リストが変化した時
			for (final ARControllerArgumentDictionary<Object> element: elementDictionary.values()) {
				final Object sensor_obj = element.get(ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_COMMONSTATE_SENSORSSTATESLISTCHANGED_SENSORNAME);
				final Object state_obj = element.get(ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_COMMONSTATE_SENSORSSTATESLISTCHANGED_SENSORSTATE);
				if ((sensor_obj != null) && (state_obj != null)) {
					final ARCOMMANDS_COMMON_COMMONSTATE_SENSORSSTATESLISTCHANGED_SENSORNAME_ENUM sensor
						= ARCOMMANDS_COMMON_COMMONSTATE_SENSORSSTATESLISTCHANGED_SENSORNAME_ENUM.getFromValue((Integer)sensor_obj);
					final int state = (Integer)state_obj;

					switch (sensor.getValue()) {
					case IFlightController.SENSOR_IMU: // 0
					case IFlightController.SENSOR_BAROMETER:	// 1
					case IFlightController.SENSOR_ULTRASOUND: // 2
					case IFlightController.SENSOR_GPS: // 3
					case IFlightController.SENSOR_MAGNETOMETER: // 4
					case IFlightController.SENSOR_VERTICAL_CAMERA: // 5
						break;
					}
//					if (DEBUG) Log.v(TAG, String.format("SensorsStatesListChangedUpdate:%d=%d", sensor.getValue(), state));
				}
			}
			break;
		}
		case ARCONTROLLER_DICTIONARY_KEY_COMMON_COMMONSTATE_PRODUCTMODEL:	// (176, "Key used to define the command <code>ProductModel</code> of class <code>CommonState</code> in project <code>Common</code>"),
		{	// 製品のモデルを受信した時
			final int _model = (Integer)args.get(ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_COMMONSTATE_PRODUCTMODEL_MODEL);
			if (DEBUG) Log.v(TAG, "COMMONSTATE_PRODUCTMODEL:model=" + _model);
			break;
		}
		case ARCONTROLLER_DICTIONARY_KEY_COMMON_COMMONSTATE_COUNTRYLISTKNOWN:	// (177, "Key used to define the command <code>CountryListKnown</code> of class <code>CommonState</code> in project <code>Common</code>"),
		{	// 指定可能な国コードリストを取得した時
			final String knownCountries = (String)args.get(ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_COMMONSTATE_COUNTRYLISTKNOWN_COUNTRYCODES);
			if (DEBUG) Log.v(TAG, "COMMONSTATE_COUNTRYLISTKNOWN:knownCountries=" + knownCountries);
			break;
		}
		case ARCONTROLLER_DICTIONARY_KEY_COMMON_OVERHEATSTATE_OVERHEATCHANGED:	// (178, "Key used to define the command <code>OverHeatChanged</code> of class <code>OverHeatState</code> in project <code>Common</code>"),
		{	// オーバーヒート状態が変化した時 FIXME 未実装
			if (DEBUG) Log.v(TAG, "OVERHEATSTATE_OVERHEATCHANGED:");
			break;
		}
		case ARCONTROLLER_DICTIONARY_KEY_COMMON_OVERHEATSTATE_OVERHEATREGULATIONCHANGED:	// (179, "Key used to define the command <code>OverHeatRegulationChanged</code> of class <code>OverHeatState</code> in project <code>Common</code>"),
		{	//  オーバーヒート時の冷却方法設定が変更された時 FIXME 未実装
			if (DEBUG) Log.v(TAG, "OVERHEATSTATE_OVERHEATREGULATIONCHANGED:");
//			public static String ARCONTROLLER_DICTIONARY_KEY_COMMON_OVERHEATSTATE_OVERHEATREGULATIONCHANGED_REGULATIONTYPE = ""; /**< Key of the argument </code>regulationType</code> of class <code>OverHeatState</code> in feature <code>Common</code> */
			break;
		}
		case ARCONTROLLER_DICTIONARY_KEY_COMMON_WIFISETTINGSSTATE_OUTDOORSETTINGSCHANGED:	// (180, "Key used to define the command <code>OutdoorSettingsChanged</code> of class <code>WifiSettingsState</code> in project <code>Common</code>"),
		{	//  WiFiの室内/室外モードが変更された時
			final boolean outdoor = (Integer)args.get(ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_WIFISETTINGSSTATE_OUTDOORSETTINGSCHANGED_OUTDOOR) != 0;
			onOutdoorSettingChanged(outdoor);
			break;
		}
		case ARCONTROLLER_DICTIONARY_KEY_COMMON_MAVLINKSTATE_MAVLINKFILEPLAYINGSTATECHANGED:	// (181, "Key used to define the command <code>MavlinkFilePlayingStateChanged</code> of class <code>MavlinkState</code> in project <code>Common</code>"),
		{	// FIXME 未実装
			if (DEBUG) Log.v(TAG, "MAVLINKSTATE_MAVLINKFILEPLAYINGSTATECHANGED:");
//			public static String ARCONTROLLER_DICTIONARY_KEY_COMMON_MAVLINKSTATE_MAVLINKFILEPLAYINGSTATECHANGED_STATE = ""; /**< Key of the argument </code>state</code> of class <code>MavlinkState</code> in feature <code>Common</code> */
//			public static String ARCONTROLLER_DICTIONARY_KEY_COMMON_MAVLINKSTATE_MAVLINKFILEPLAYINGSTATECHANGED_FILEPATH = ""; /**< Key of the argument </code>filepath</code> of class <code>MavlinkState</code> in feature <code>Common</code> */
//			public static String ARCONTROLLER_DICTIONARY_KEY_COMMON_MAVLINKSTATE_MAVLINKFILEPLAYINGSTATECHANGED_TYPE = ""; /**< Key of the argument </code>type</code> of class <code>MavlinkState</code> in feature <code>Common</code> */
			break;
		}
		case ARCONTROLLER_DICTIONARY_KEY_COMMON_MAVLINKSTATE_MAVLINKPLAYERRORSTATECHANGED:	// (182, "Key used to define the command <code>MavlinkPlayErrorStateChanged</code> of class <code>MavlinkState</code> in project <code>Common</code>"),
		{	// FIXME 未実装
			if (DEBUG) Log.v(TAG, "MAVLINKSTATE_MAVLINKPLAYERRORSTATECHANGED:");
//			public static String ARCONTROLLER_DICTIONARY_KEY_COMMON_MAVLINKSTATE_MAVLINKPLAYERRORSTATECHANGED_ERROR = ""; /**< Key of the argument </code>error</code> of class <code>MavlinkState</code> in feature <code>Common</code> */
			break;
		}
		case ARCONTROLLER_DICTIONARY_KEY_COMMON_CALIBRATIONSTATE_MAGNETOCALIBRATIONSTATECHANGED:	// (183, "Key used to define the command <code>MagnetoCalibrationStateChanged</code> of class <code>CalibrationState</code> in project <code>Common</code>"),
		{	// キャリブレーションの状態が変わった時の通知
			final boolean xAxisCalibration = (Integer)args.get(ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_CALIBRATIONSTATE_MAGNETOCALIBRATIONSTATECHANGED_XAXISCALIBRATION) == 1;
			final boolean yAxisCalibration = (Integer)args.get(ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_CALIBRATIONSTATE_MAGNETOCALIBRATIONSTATECHANGED_YAXISCALIBRATION) == 1;
			final boolean zAxisCalibration = (Integer)args.get(ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_CALIBRATIONSTATE_MAGNETOCALIBRATIONSTATECHANGED_ZAXISCALIBRATION) == 1;
			final boolean calibrationFailed = (Integer)args.get(ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_CALIBRATIONSTATE_MAGNETOCALIBRATIONSTATECHANGED_CALIBRATIONFAILED) == 1;
			mStatus.updateCalibrationState(xAxisCalibration, yAxisCalibration, zAxisCalibration, calibrationFailed);
			callOnCalibrationRequiredChanged(calibrationFailed);
			break;
		}
		case ARCONTROLLER_DICTIONARY_KEY_COMMON_CALIBRATIONSTATE_MAGNETOCALIBRATIONREQUIREDSTATE:	// (184, "Key used to define the command <code>MagnetoCalibrationRequiredState</code> of class <code>CalibrationState</code> in project <code>Common</code>"),
		{	// キャリブレーションが必要な時
			final boolean required = (Integer)args.get(ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_CALIBRATIONSTATE_MAGNETOCALIBRATIONREQUIREDSTATE_REQUIRED) != 0;
			callOnCalibrationRequiredChanged(required);
			break;
		}
		case ARCONTROLLER_DICTIONARY_KEY_COMMON_CALIBRATIONSTATE_MAGNETOCALIBRATIONAXISTOCALIBRATECHANGED:	// (185, "Key used to define the command <code>MagnetoCalibrationAxisToCalibrateChanged</code> of class <code>CalibrationState</code> in project <code>Common</code>"),
		{	// キャリブレーション中の軸が変更された時
			final int axis = (Integer)args.get(ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_CALIBRATIONSTATE_MAGNETOCALIBRATIONAXISTOCALIBRATECHANGED_AXIS);
			callOnCalibrationAxisChanged(axis);
			break;
		}
		case ARCONTROLLER_DICTIONARY_KEY_COMMON_CALIBRATIONSTATE_MAGNETOCALIBRATIONSTARTEDCHANGED:	// (186, "Key used to define the command <code>MagnetoCalibrationStartedChanged</code> of class <code>CalibrationState</code> in project <code>Common</code>"),
		{	// キャリブレーションを開始/終了した時
			final boolean is_started = (Integer)args.get(ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_CALIBRATIONSTATE_MAGNETOCALIBRATIONSTARTEDCHANGED_STARTED) != 0;
			callOnCalibrationStartStop(is_started);
			break;
		}
		case ARCONTROLLER_DICTIONARY_KEY_COMMON_CAMERASETTINGSSTATE_CAMERASETTINGSCHANGED:	// (187, "Key used to define the command <code>CameraSettingsChanged</code> of class <code>CameraSettingsState</code> in project <code>Common</code>"),
		{	// FIXME 未実装
			if (DEBUG) Log.v(TAG, "CAMERASETTINGSSTATE_CAMERASETTINGSCHANGED:");
//			public static String ARCONTROLLER_DICTIONARY_KEY_COMMON_CAMERASETTINGSSTATE_CAMERASETTINGSCHANGED_FOV = ""; /**< Key of the argument </code>fov</code> of class <code>CameraSettingsState</code> in feature <code>Common</code> */
//			public static String ARCONTROLLER_DICTIONARY_KEY_COMMON_CAMERASETTINGSSTATE_CAMERASETTINGSCHANGED_PANMAX = ""; /**< Key of the argument </code>panMax</code> of class <code>CameraSettingsState</code> in feature <code>Common</code> */
//			public static String ARCONTROLLER_DICTIONARY_KEY_COMMON_CAMERASETTINGSSTATE_CAMERASETTINGSCHANGED_PANMIN = ""; /**< Key of the argument </code>panMin</code> of class <code>CameraSettingsState</code> in feature <code>Common</code> */
//			public static String ARCONTROLLER_DICTIONARY_KEY_COMMON_CAMERASETTINGSSTATE_CAMERASETTINGSCHANGED_TILTMAX = ""; /**< Key of the argument </code>tiltMax</code> of class <code>CameraSettingsState</code> in feature <code>Common</code> */
//			public static String ARCONTROLLER_DICTIONARY_KEY_COMMON_CAMERASETTINGSSTATE_CAMERASETTINGSCHANGED_TILTMIN = ""; /**< Key of the argument </code>tiltMin</code> of class <code>CameraSettingsState</code> in feature <code>Common</code> */
			break;
		}
		case ARCONTROLLER_DICTIONARY_KEY_COMMON_FLIGHTPLANSTATE_AVAILABILITYSTATECHANGED:	// (188, "Key used to define the command <code>AvailabilityStateChanged</code> of class <code>FlightPlanState</code> in project <code>Common</code>"),
		{	// FIXME 未実装
			if (DEBUG) Log.v(TAG, "FLIGHTPLANSTATE_AVAILABILITYSTATECHANGED:");
//			public static String ARCONTROLLER_DICTIONARY_KEY_COMMON_FLIGHTPLANSTATE_AVAILABILITYSTATECHANGED_AVAILABILITYSTATE = ""; /**< Key of the argument </code>AvailabilityState</code> of class <code>FlightPlanState</code> in feature <code>Common</code> */
			break;
		}
		case ARCONTROLLER_DICTIONARY_KEY_COMMON_FLIGHTPLANSTATE_COMPONENTSTATELISTCHANGED:	// (189, "Key used to define the command <code>ComponentStateListChanged</code> of class <code>FlightPlanState</code> in project <code>Common</code>"),
		{	// FIXME 未実装
			if (DEBUG) Log.v(TAG, "FLIGHTPLANSTATE_COMPONENTSTATELISTCHANGED:");
//			public static String ARCONTROLLER_DICTIONARY_KEY_COMMON_FLIGHTPLANSTATE_COMPONENTSTATELISTCHANGED_COMPONENT = ""; /**< Key of the argument </code>component</code> of class <code>FlightPlanState</code> in feature <code>Common</code> */
//			public static String ARCONTROLLER_DICTIONARY_KEY_COMMON_FLIGHTPLANSTATE_COMPONENTSTATELISTCHANGED_STATE = ""; /**< Key of the argument </code>State</code> of class <code>FlightPlanState</code> in feature <code>Common</code> */
			break;
		}
		case ARCONTROLLER_DICTIONARY_KEY_COMMON_FLIGHTPLANEVENT_STARTINGERROREVENT:	// (190, "Key used to define the command <code>StartingErrorEvent</code> of class <code>FlightPlanEvent</code> in project <code>Common</code>"),
		{	// FIXME 未実装
			if (DEBUG) Log.v(TAG, "FLIGHTPLANEVENT_STARTINGERROREVENT:");
			break;
		}
		case ARCONTROLLER_DICTIONARY_KEY_COMMON_FLIGHTPLANEVENT_SPEEDBRIDLEEVENT:	// (191, "Key used to define the command <code>SpeedBridleEvent</code> of class <code>FlightPlanEvent</code> in project <code>Common</code>"),
		{	// FIXME 未実装
			if (DEBUG) Log.v(TAG, "FLIGHTPLANEVENT_SPEEDBRIDLEEVENT:");
			break;
		}
		case ARCONTROLLER_DICTIONARY_KEY_COMMON_ARLIBSVERSIONSSTATE_CONTROLLERLIBARCOMMANDSVERSION:	// (192, "Key used to define the command <code>ControllerLibARCommandsVersion</code> of class <code>ARLibsVersionsState</code> in project <code>Common</code>"),
		{	// FIXME 未実装
			if (DEBUG) Log.v(TAG, "ARLIBSVERSIONSSTATE_CONTROLLERLIBARCOMMANDSVERSION:");
			break;
		}
		case ARCONTROLLER_DICTIONARY_KEY_COMMON_ARLIBSVERSIONSSTATE_SKYCONTROLLERLIBARCOMMANDSVERSION:	// (193, "Key used to define the command <code>SkyControllerLibARCommandsVersion</code> of class <code>ARLibsVersionsState</code> in project <code>Common</code>"),
		{	// FIXME 未実装
			if (DEBUG) Log.v(TAG, "ARLIBSVERSIONSSTATE_SKYCONTROLLERLIBARCOMMANDSVERSION:");
			break;
		}
		case ARCONTROLLER_DICTIONARY_KEY_COMMON_ARLIBSVERSIONSSTATE_DEVICELIBARCOMMANDSVERSION:	// (194, "Key used to define the command <code>DeviceLibARCommandsVersion</code> of class <code>ARLibsVersionsState</code> in project <code>Common</code>"),
		{	// FIXME 未実装
			if (DEBUG) Log.v(TAG, "ARLIBSVERSIONSSTATE_DEVICELIBARCOMMANDSVERSION:");
			break;
		}
		case ARCONTROLLER_DICTIONARY_KEY_COMMON_AUDIOSTATE_AUDIOSTREAMINGRUNNING:	// (195, "Key used to define the command <code>AudioStreamingRunning</code> of class <code>AudioState</code> in project <code>Common</code>"),
		{	// FIXME 未実装
			if (DEBUG) Log.v(TAG, "AUDIOSTATE_AUDIOSTREAMINGRUNNING:");
			break;
		}
		case ARCONTROLLER_DICTIONARY_KEY_COMMON_HEADLIGHTSSTATE_INTENSITYCHANGED:	// (196, "Key used to define the command <code>IntensityChanged</code> of class <code>HeadlightsState</code> in project <code>Common</code>"),
		{	// FIXME 未実装
			if (DEBUG) Log.v(TAG, "HEADLIGHTSSTATE_INTENSITYCHANGED:");
			break;
		}
		case ARCONTROLLER_DICTIONARY_KEY_COMMON_ANIMATIONSSTATE_LIST:	// (197, "Key used to define the command <code>List</code> of class <code>AnimationsState</code> in project <code>Common</code>"),
		{	// FIXME 未実装
			if (DEBUG) Log.v(TAG, "ANIMATIONSSTATE_LIST:");
			break;
		}
		case ARCONTROLLER_DICTIONARY_KEY_COMMON_ACCESSORYSTATE_SUPPORTEDACCESSORIESLISTCHANGED:	// (198, "Key used to define the command <code>SupportedAccessoriesListChanged</code> of class <code>AccessoryState</code> in project <code>Common</code>"),
		{	// FIXME 未実装
			if (DEBUG) Log.v(TAG, "ACCESSORYSTATE_SUPPORTEDACCESSORIESLISTCHANGED:");
			break;
		}
		case ARCONTROLLER_DICTIONARY_KEY_COMMON_ACCESSORYSTATE_ACCESSORYCONFIGCHANGED:	// (199, "Key used to define the command <code>AccessoryConfigChanged</code> of class <code>AccessoryState</code> in project <code>Common</code>"),
		{	// FIXME 未実装
			if (DEBUG) Log.v(TAG, "ACCESSORYSTATE_ACCESSORYCONFIGCHANGED:");
			break;
		}
		case ARCONTROLLER_DICTIONARY_KEY_COMMON_ACCESSORYSTATE_ACCESSORYCONFIGMODIFICATIONENABLED:	// (200, "Key used to define the command <code>AccessoryConfigModificationEnabled</code> of class <code>AccessoryState</code> in project <code>Common</code>"),
		{	// FIXME 未実装
			if (DEBUG) Log.v(TAG, "ACCESSORYSTATE_ACCESSORYCONFIGMODIFICATIONENABLED:");
			break;
		}
		case ARCONTROLLER_DICTIONARY_KEY_COMMON_CHARGERSTATE_MAXCHARGERATECHANGED:	// (201, "Key used to define the command <code>MaxChargeRateChanged</code> of class <code>ChargerState</code> in project <code>Common</code>"),
		{	// FIXME 未実装
			if (DEBUG) Log.v(TAG, "CHARGERSTATE_MAXCHARGERATECHANGED:");
			break;
		}
		case ARCONTROLLER_DICTIONARY_KEY_COMMON_CHARGERSTATE_CURRENTCHARGESTATECHANGED:	// (202, "Key used to define the command <code>CurrentChargeStateChanged</code> of class <code>ChargerState</code> in project <code>Common</code>"),
		{	// FIXME 未実装
			if (DEBUG) Log.v(TAG, "CHARGERSTATE_CURRENTCHARGESTATECHANGED:");
			break;
		}
		case ARCONTROLLER_DICTIONARY_KEY_COMMON_CHARGERSTATE_LASTCHARGERATECHANGED:	// (203, "Key used to define the command <code>LastChargeRateChanged</code> of class <code>ChargerState</code> in project <code>Common</code>"),
		{	// FIXME 未実装
			if (DEBUG) Log.v(TAG, "CHARGERSTATE_LASTCHARGERATECHANGED:");
			break;
		}
		case ARCONTROLLER_DICTIONARY_KEY_COMMON_CHARGERSTATE_CHARGINGINFO:	// (204, "Key used to define the command <code>ChargingInfo</code> of class <code>ChargerState</code> in project <code>Common</code>"),
		{	// FIXME 未実装
			if (DEBUG) Log.v(TAG, "CHARGERSTATE_CHARGINGINFO:");
			break;
		}
		case ARCONTROLLER_DICTIONARY_KEY_COMMON_RUNSTATE_RUNIDCHANGED:	// (205, "Key used to define the command <code>RunIdChanged</code> of class <code>RunState</code> in project <code>Common</code>"),
		{
			if (DEBUG) Log.v(TAG, "RUNSTATE_RUNIDCHANGE:");
			final String runID = (String) args.get(ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_RUNSTATE_RUNIDCHANGED_RUNID);
			break;
		}
//		case ARCONTROLLER_DICTIONARY_KEY_COMMONDEBUG:	// (206, "Key used to define the feature <code>CommonDebug</code>"),
//		{	// ARSDK3.9.2で削除された
//			if (DEBUG) Log.v(TAG, "COMMONDEBUG:");
//			break;
//		}
//		case ARCONTROLLER_DICTIONARY_KEY_COMMONDEBUG_STATSEVENT_SENDPACKET:	// (207, "Key used to define the command <code>SendPacket</code> of class <code>StatsEvent</code> in project <code>CommonDebug</code>"),
//		{	// ARSDK3.9.2で削除された
//			if (DEBUG) Log.v(TAG, "COMMONDEBUG_STATSEVENT_SENDPACKET:");
//			break;
//		}
//		case ARCONTROLLER_DICTIONARY_KEY_COMMONDEBUG_DEBUGSETTINGSSTATE_INFO:	// (208, "Key used to define the command <code>Info</code> of class <code>DebugSettingsState</code> in project <code>CommonDebug</code>"),
//		{	// ARSDK3.9.2で削除された
//			if (DEBUG) Log.v(TAG, "COMMONDEBUG_DEBUGSETTINGSSTATE_INFO:");
//			break;
//		}
//		case ARCONTROLLER_DICTIONARY_KEY_COMMONDEBUG_DEBUGSETTINGSSTATE_LISTCHANGED:	// (209, "Key used to define the command <code>ListChanged</code> of class <code>DebugSettingsState</code> in project <code>CommonDebug</code>"),
//		{	// ARSDK3.9.2で削除された
//			if (DEBUG) Log.v(TAG, "COMMONDEBUG_DEBUGSETTINGSSTATE_LISTCHANGED:");
//			break;
//		}
		}
	}

	/**
	 * 国コード設定を保存
	 * @param code
	 */
	protected abstract void setCountryCode(final String code);

	/**
	 * 自動国選択設定を保存
	 * @param auto
	 */
	protected abstract void setAutomaticCountry(final boolean auto);

	/**
	 * 磁気センサーのキャリブレーを開始/停止した時のコールバック呼び出し用ヘルパーメソッド
	 * @param is_start
	 */
	protected abstract void callOnCalibrationStartStop(final boolean is_start);

	/**
	 * 磁気センサーのキャリブレーションが必要かどうかが更新された時のコールバック呼び出し用ヘルパーメソッド
	 * @param failed
	 */
	protected abstract void callOnCalibrationRequiredChanged(final boolean failed);

	/**
	 * 磁気センサーのキャリブレーション中の軸が変化した時のコールバック呼び出し用ヘルパーメソッド
	 * @param axis 0:x, 1:y, z:2, 3:none
	 */
	protected abstract void callOnCalibrationAxisChanged(final int axis);

	/**
	 * 静止画撮影ステータスが変化した時のコールバック呼び出し用のヘルパーメソッド
	 * @param state
	 */
	protected abstract void callOnStillCaptureStateChanged(final int state);

	/**
	 * 動画撮影ステータスが変化した時のコールバック呼び出し用のヘルパーメソッド
	 * @param state
	 */
	protected abstract void callOnVideoRecordingStateChanged(final int state);

	/**
	 * デバイス内のストレージの状態が変化した時のコールバック呼び出し用ヘルパーメソッド
	 * @param mass_storage_id
	 * @param size
	 * @param used_size
	 * @param plugged
	 * @param full
	 * @param internal
	 */
	protected abstract void callOnUpdateStorageState(final int mass_storage_id, final int size, final int used_size, final boolean plugged, final boolean full, final boolean internal);

	/**
	 * 室内モード/屋外モードが変わった時
	 * @param outdoor
	 */
	protected abstract void onOutdoorSettingChanged(final boolean outdoor);

	/**
	 * デバイス内のストレージ一覧が変化した時
	 * @param mass_storage_id
	 * @param name
	 */
	protected void onCommonStateMassStorageStateListChanged(
		final int mass_storage_id, final String name) {

		((DroneStatus)mStatus).setMassStorage(mass_storage_id, name);
	}

	/**
	 * デバイス内のストレージの状態が変化した時
	 * @param mass_storage_id
	 * @param size
	 * @param used_size
	 * @param plugged
	 * @param full
	 * @param internal
	 */
	protected void onCommonStateMassStorageInfoStateListChanged(
		final int mass_storage_id, final int size, final int used_size,
		final boolean plugged, final boolean full, final boolean internal) {

		callOnUpdateStorageState(mass_storage_id, size, used_size, plugged, full, internal);
	}

//********************************************************************************
// データ送受信関係
//********************************************************************************
	private static final SimpleDateFormat formattedDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
	private static final SimpleDateFormat formattedTime = new SimpleDateFormat("'T'HHmmssZZZ", Locale.getDefault());

	public boolean sendNetworkDisconnect() {
		if (DEBUG) Log.v(TAG, "sendNetworkDisconnect:");
		ARCONTROLLER_ERROR_ENUM result = ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_ERROR;
		if (isConnected()) {
			result = mARDeviceController.getFeatureCommon().sendNetworkDisconnect();
		}
		if (result != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK) {
			Log.e(TAG, "#sendNetworkDisconnect failed:" + result);
		}
		return result != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK;
	}

	@Override
	public boolean sendDate(final Date currentDate) {
		if (DEBUG) Log.v(TAG, "sendDate:");
		ARCONTROLLER_ERROR_ENUM result = ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_ERROR;
		if (isStarted()) {
			result = mARDeviceController.getFeatureCommon().sendCommonCurrentDate(formattedDate.format(currentDate));
		}
		if (result != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK) {
			Log.e(TAG, "#sendDate failed:" + result);
		}
		return result != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK;
	}

	@Override
	public boolean sendTime(final Date currentTime) {
		if (DEBUG) Log.v(TAG, "sendTime:");
		ARCONTROLLER_ERROR_ENUM result = ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_ERROR;
		if (isStarted()) {
			result = mARDeviceController.getFeatureCommon().sendCommonCurrentTime(formattedTime.format(currentTime));
		}
		if (result != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK) {
			Log.e(TAG, "#sendTime failed:" + result);
		}
		return result != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK;
	}

	@Override
	public boolean requestAllSettings() {
		if (DEBUG) Log.v(TAG, "requestAllSettings:");
		ARCONTROLLER_ERROR_ENUM result = ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_ERROR;
		if (isStarted()) {
			result = mARDeviceController.getFeatureCommon().sendSettingsAllSettings();
		}
		if (result != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK) {
			Log.e(TAG, "#requestAllSettings failed:" + result);
		}
		return result != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK;
	}

	@Override
	public boolean requestAllStates() {
		if (DEBUG) Log.v(TAG, "requestAllStates:");
		ARCONTROLLER_ERROR_ENUM result = ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_ERROR;
		if (isStarted()) {
			result = mARDeviceController.getFeatureCommon().sendCommonAllStates();
		}
		if (result != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK) {
			Log.e(TAG, "#requestAllStates failed:" + result);
		}
		return result != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK;
	}

	@Override
	public boolean sendSettingsReset() {
		if (DEBUG) Log.v(TAG, "sendSettingsReset:");
		ARCONTROLLER_ERROR_ENUM result = ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_ERROR;
		if (isStarted()) {
			result = mARDeviceController.getFeatureCommon().sendSettingsReset();
		}
		if (result != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK) {
			Log.e(TAG, "#sendSettingsReset failed:" + result);
		}
		return result != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK;
	}

	@Override
	public boolean sendCommonReboot() {
		if (DEBUG) Log.v(TAG, "sendCommonReboot:");
		ARCONTROLLER_ERROR_ENUM result = ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_ERROR;
		if (isStarted()) {
			result = mARDeviceController.getFeatureCommon().sendCommonReboot();
		}
		if (result != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK) {
			Log.e(TAG, "#sendCommonReboot failed:" + result);
		}
		return result != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK;
	}

	@Override
	public boolean sendSettingsProductName(final String name) {
		if (DEBUG) Log.v(TAG, "sendSettingsProductName:");
		ARCONTROLLER_ERROR_ENUM result = ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_ERROR;
		if (isStarted()) {
			result = mARDeviceController.getFeatureCommon().sendSettingsProductName(name);
		}
		if (result != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK) {
			Log.e(TAG, "#sendSettingsProductName failed:" + result);
		}
		return result != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK;
	}

	public boolean sendOverHeatSwitchOff() {
		if (DEBUG) Log.v(TAG, "sendOverHeatSwitchOff:");
		ARCONTROLLER_ERROR_ENUM result = ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_ERROR;
		if (isStarted()) {
			result = mARDeviceController.getFeatureCommon().sendOverHeatSwitchOff();
		}
		if (result != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK) {
			Log.e(TAG, "#sendOverHeatSwitchOff failed:" + result);
		}
		return result != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK;
	}

	public boolean sendOverHeatVentilate() {
		if (DEBUG) Log.v(TAG, "sendOverHeatVentilate:");
		ARCONTROLLER_ERROR_ENUM result = ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_ERROR;
		if (isStarted()) {
			result = mARDeviceController.getFeatureCommon().sendOverHeatVentilate();
		}
		if (result != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK) {
			Log.e(TAG, "#sendOverHeatVentilate failed:" + result);
		}
		return result != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK;
	}

	public boolean sendControllerIsPiloting(final boolean piloting) {
		if (DEBUG) Log.v(TAG, "sendControllerIsPiloting:");
		ARCONTROLLER_ERROR_ENUM result = ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_ERROR;
		if (isStarted()) {
			result = mARDeviceController.getFeatureCommon().sendControllerIsPiloting(piloting ? (byte)1 : (byte)0);
		}
		if (result != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK) {
			Log.e(TAG, "#sendControllerIsPiloting failed:" + result);
		}
		return result != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK;
	}

	public boolean sendCountryCode(final String code) {
		if (DEBUG) Log.v(TAG, "sendCountryCode:");
		ARCONTROLLER_ERROR_ENUM result = ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_ERROR;
		if (isStarted()) {
			result = mARDeviceController.getFeatureCommon().sendSettingsCountry(code);
		}
		if (result != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK) {
			Log.e(TAG, "#setCountryCode failed:" + result);
		}
		return result != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK;
	}

	public boolean sendAutomaticCountry(final boolean auto) {
		if (DEBUG) Log.v(TAG, "sendAutomaticCountry:");
		ARCONTROLLER_ERROR_ENUM result = ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_ERROR;
		if (isStarted()) {
			result = mARDeviceController.getFeatureCommon().sendSettingsAutoCountry(auto ? (byte)1 : (byte)0);
		}
		if (result != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK) {
			Log.e(TAG, "#setAutomaticCountry failed:" + result);
		}
		return result != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK;
	}

	public boolean sendSettingsOutdoor(final boolean outdoor) {
		if (DEBUG) Log.v(TAG, "sendSettingsOutdoor:");
		ARCONTROLLER_ERROR_ENUM result = ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_ERROR;
		if (isStarted()) {
			result = mARDeviceController.getFeatureCommon().sendWifiSettingsOutdoorSetting(outdoor ? (byte)1 : (byte)0);
		}
		if (result != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK) {
			Log.e(TAG, "#sendSettingsOutdoor failed:" + result);
		}
		return result != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK;
	}

	/**
	 * Mavlinkを使った自動飛行の開始要求
	 * @param filepath
	 * @param isFlightPlan
	 * @return
	 */
	public boolean sendMavlinkStart(final String filepath, final boolean isFlightPlan) {
		if (DEBUG) Log.v(TAG, "sendMavlinkStart:");
		ARCONTROLLER_ERROR_ENUM result = ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_ERROR;
		if (isStarted()) {
			result = mARDeviceController.getFeatureCommon().sendMavlinkStart(filepath,
				isFlightPlan ? ARCOMMANDS_COMMON_MAVLINK_START_TYPE_ENUM.ARCOMMANDS_COMMON_MAVLINK_START_TYPE_FLIGHTPLAN
					: ARCOMMANDS_COMMON_MAVLINK_START_TYPE_ENUM.ARCOMMANDS_COMMON_MAVLINK_START_TYPE_MAPMYHOUSE
			);
		}
		if (result != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK) {
			Log.e(TAG, "#sendMavlinkStart failed:" + result);
		}
		return result != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK;
	}

	/**
	 * Mavlinkを使った自動飛行の一時停止要求
	 * @return
	 */
	public boolean sendMavlinkPause() {
		if (DEBUG) Log.v(TAG, "sendMavlinkPause:");
		ARCONTROLLER_ERROR_ENUM result = ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_ERROR;
		if (isStarted()) {
			result = mARDeviceController.getFeatureCommon().sendMavlinkPause();
		}
		if (result != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK) {
			Log.e(TAG, "#sendMavlinkPause failed:" + result);
		}
		return result != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK;
	}

	/**
	 * Mavlinkを使った自動飛行の中断・終了要求
	 * @return
	 */
	public boolean sendMavlinkStop() {
		if (DEBUG) Log.v(TAG, "sendMavlinkStop:");
		ARCONTROLLER_ERROR_ENUM result = ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_ERROR;
		if (isStarted()) {
			result = mARDeviceController.getFeatureCommon().sendMavlinkStop();
		}
		if (result != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK) {
			Log.e(TAG, "#sendMavlinkStop failed:" + result);
		}
		return result != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK;
	}

	/**
	 * 磁気センサーのキャリブレーション開始要求
	 * @param start
	 * @return
	 */
	public boolean startCalibration(final boolean start) {
		if (DEBUG) Log.v (TAG, "startCalibration:");
		ARCONTROLLER_ERROR_ENUM result = ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_ERROR;
		if (isConnected()) {
			result = mARDeviceController.getFeatureCommon().sendCalibrationMagnetoCalibration(start ? (byte)1 : (byte)0);
		}
		if (result != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK) {
			Log.e(TAG, "#startCalibration failed:" + result);
		}
		return result != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK;
	}

	/**
	 * Bluetooth接続ではなくてかつGPSを持ってないデバイス(ぶっちゃけ今のところJumpingSumo)に対してコントローラーの現在位置を送信する
	 * 静止画・動画撮影時のジオタグに使うらしい。
	 * なんでBluetooth接続のデバイス(ミニドローン)に送っちゃいかんねん?ジオタグに使うんやったら一緒やん。
	 * @param latitude
	 * @param longitude
	 * @return
	 */
	public boolean sendGPSControllerPositionForRun(final double latitude, final double longitude) {
		if (DEBUG) Log.v(TAG, "sendGPSControllerPositionForRun:");
		ARCONTROLLER_ERROR_ENUM result = ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_ERROR;
		if (isStarted()) {
			result = mARDeviceController.getFeatureCommon().sendGPSControllerPositionForRun(latitude, longitude);
		}
		if (result != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK) {
			Log.e(TAG, "#sendGPSControllerPositionForRun failed:" + result);
		}
		return result != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK;
	}

	/**
	 * JumpingSumo Evo向けにコントローラーからの音声取得/音声送信が可能かどうかを送信する
	 * @param ready ビット0: 受信可, ビット1: 送信可
	 * @return
	 */
	public boolean sendAudioControllerReadyForStreaming(final int ready) {
		if (DEBUG) Log.v(TAG, "sendAudioControllerReadyForStreaming:");
		ARCONTROLLER_ERROR_ENUM result = ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_ERROR;
		if (isStarted()) {
			result = mARDeviceController.getFeatureCommon().sendAudioControllerReadyForStreaming((byte)(ready & 0x03));
		}
		if (result != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK) {
			Log.e(TAG, "#sendAudioControllerReadyForStreaming failed:" + result);
		}
		return result != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK;
	}

	/**
	 * ヘッドライトの明かりを調整
	 * @param left [0,255]
	 * @param right [0,255]
	 * @return
	 */
	public boolean sendHeadlightsIntensity(final byte left, final byte right) {
		if (DEBUG) Log.v(TAG, "sendHeadlightsIntensity:");
		ARCONTROLLER_ERROR_ENUM result = ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_ERROR;
		if (isStarted()) {
			result = mARDeviceController.getFeatureCommon().sendHeadlightsIntensity(left, right);
		}
		if (result != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK) {
			Log.e(TAG, "#sendHeadlightsIntensity failed:" + result);
		}
		return result != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK;
	}

	/**
	 * アニメーション動作開始要求
	 * Jumping Sumo, Jumping Sumo Evo Race, Jumping Sumo Evo Brick, Airborne Night, Airborne Cargo
	 * @param anim
	 * @return
	 */
	public boolean sendAnimationsStartAnimation(final int anim) {
		if (DEBUG) Log.v(TAG, "sendAnimationsStartAnimation:");
		ARCONTROLLER_ERROR_ENUM result = ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_ERROR;
		if (isStarted()) {
			final ARCOMMANDS_COMMON_ANIMATIONS_STARTANIMATION_ANIM_ENUM _anim
				= ARCOMMANDS_COMMON_ANIMATIONS_STARTANIMATION_ANIM_ENUM.getFromValue(anim);
			result = mARDeviceController.getFeatureCommon().sendAnimationsStartAnimation(_anim);
		}
		if (result != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK) {
			Log.e(TAG, "#sendAnimationsStartAnimation failed:" + result);
		}
		return result != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK;
	}

	/**
	 * アニメーション動作停止要求
	 * Jumping Sumo, Jumping Sumo Evo Race, Jumping Sumo Evo Brick, Airborne Night, Airborne Cargo
	 * @param anim
	 * @return
	 */
	public boolean sendAnimationsStopAnimation(final int anim) {
		if (DEBUG) Log.v(TAG, "sendAnimationsStopAnimation:");
		ARCONTROLLER_ERROR_ENUM result = ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_ERROR;
		if (isStarted()) {
			final ARCOMMANDS_COMMON_ANIMATIONS_STOPANIMATION_ANIM_ENUM _anim
				= ARCOMMANDS_COMMON_ANIMATIONS_STOPANIMATION_ANIM_ENUM.getFromValue(anim);
			result = mARDeviceController.getFeatureCommon().sendAnimationsStopAnimation(_anim);
		}
		if (result != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK) {
			Log.e(TAG, "#sendAnimationsStopAnimation failed:" + result);
		}
		return result != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK;
	}

	/**
	 * 実行要求中の全てのアニメーション動作を停止させる
	 * Jumping Sumo, Jumping Sumo Evo Race, Jumping Sumo Evo Brick, Airborne Night, Airborne Cargo
	 * @param anim
	 * @return
	 */
	public boolean sendAnimationsStopAllAnimations(final int anim) {
		if (DEBUG) Log.v(TAG, "sendAnimationsStopAllAnimations:");
		ARCONTROLLER_ERROR_ENUM result = ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_ERROR;
		if (isStarted()) {
			result = mARDeviceController.getFeatureCommon().sendAnimationsStopAllAnimations();
		}
		if (result != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK) {
			Log.e(TAG, "#sendAnimationsStopAllAnimations failed:" + result);
		}
		return result != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK;
	}

	/**
	 * ハル(ガード)の種類を設定する
	 * ミニドローンの場合にはARFeatureMiniDrone#sendSpeedSettingsWheelsちゅう設定も別途あるし
	 * Bebop/Bebop2の場合にはARFeatureARDrone3#sendSpeedSettingsHullProtectionちゅうのもあるから注意や
	 * @param accessory 0:無し, 1:標準の車輪型, 2:外周を囲うタイプ, 3:ハル, 4:ハイドロフォイル
	 * @return
	 */
	public boolean sendAccessoryConfig(final int accessory) {
		if (DEBUG) Log.v(TAG, "sendAccessoryConfig:");
		ARCONTROLLER_ERROR_ENUM result = ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_ERROR;
		if (isStarted()) {
			ARCOMMANDS_COMMON_ACCESSORY_CONFIG_ACCESSORY_ENUM _accessory
				= ARCOMMANDS_COMMON_ACCESSORY_CONFIG_ACCESSORY_ENUM.getFromValue(accessory);
			result = mARDeviceController.getFeatureCommon().sendAccessoryConfig(_accessory);
		}
		if (result != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK) {
			Log.e(TAG, "#sendAccessoryConfig failed:" + result);
		}
		return result != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK;
	}

	/**
	 * 充電速度を設定
	 * 急速充電すると電池寿命が短くなる可能性が高いからよう考えて設定するんやで
	 * Bebop/Bebop2/スカイコントローラーの場合は本体では充電できへんから実装されてない
	 * @param rate 0:最大512mA(通常のUSB1/1.1/2.0規格用), 1, 2:高速充電用, 1より2の方が速い
	 * @return
	 */
	public boolean sendChargerSetMaxChargeRate(final int rate) {
		if (DEBUG) Log.v(TAG, "sendChargerSetMaxChargeRate:");
		ARCONTROLLER_ERROR_ENUM result = ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_ERROR;
		if (isStarted()) {
			ARCOMMANDS_COMMON_CHARGER_SETMAXCHARGERATE_RATE_ENUM _rate
				= ARCOMMANDS_COMMON_CHARGER_SETMAXCHARGERATE_RATE_ENUM.getFromValue(rate);
			result = mARDeviceController.getFeatureCommon().sendChargerSetMaxChargeRate(_rate);
		}
		if (result != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK) {
			Log.e(TAG, "#sendChargerSetMaxChargeRate failed:" + result);
		}
		return result != ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK;
	}

}
