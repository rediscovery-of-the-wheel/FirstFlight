package jp.co.rediscovery.firstflight;
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
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.os.Bundle;
import android.os.Process;

import androidx.annotation.NonNull;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import jp.co.rediscovery.arflight.DroneStatus;
import jp.co.rediscovery.arflight.ICameraController;
import jp.co.rediscovery.arflight.IDeviceController;
import jp.co.rediscovery.arflight.IFlightController;
import jp.co.rediscovery.arflight.ISkyController;
import jp.co.rediscovery.arflight.attribute.AttributeFloat;
import jp.co.rediscovery.opencv.ImageProcessor;

import com.serenegiant.math.Vector;
import com.serenegiant.utils.CpuMonitor;

import static jp.co.rediscovery.firstflight.AppConst.*;
import static jp.co.rediscovery.firstflight.AutoPilotConst.*;

/** 画像解析により飛行するための操縦画面Fragmentの基本クラス */
public abstract class BaseAutoPilotFragment extends BasePilotFragment {
//	private static final boolean DEBUG = false; // FIXME 実働時はfalseにすること
	private final String TAG = "BaseAutoPilotFragment:" + getClass().getSimpleName();

	public static final int MODE_TRACE = 0;
	public static final int MODE_TRACKING = 1;

	private final Object mCtrlSync = new Object();

	protected int mMode;

//	private ViewGroup mControllerFrame;		// 操作パネル全体

	// 上パネル
	private View mTopPanel;
	private TextView mBatteryLabel;			// バッテリー残量表示
	private ImageButton mFlatTrimBtn;		// フラットトリム
	private TextView mAlertMessage;			// 非常メッセージ
	private ImageButton mConfigShowBtn;		// 設定パネル表示ボタン
	private String mBatteryFmt;

	// 下パネル
//	private View mBottomPanel;
	private ImageButton mEmergencyBtn;		// 非常停止ボタン
	private ImageButton mTakeOnOffBtn;		// 離陸/着陸ボタン

	// 右サイドパネル
	private View mRightSidePanel;
	private ImageButton mCopilotBtn;		// コパイロットボタン
	private ImageButton mStillCaptureBtn;
	private ImageButton mVideoRecordingBtn;
	private ImageButton mTraceButton;

	/** 操縦に使用するボタン等の一括変更用。操作可・不可に応じてenable/disableを切り替える */
	private final List<View> mActionViews = new ArrayList<View>();

	protected SurfaceView mDetectView;
	protected ImageProcessor mImageProcessor;
	protected ControlTask mControlTask;
	protected TextView mTraceTv1, mTraceTv2, mTraceTv3;
	private TextView mCpuLoadTv;
	private TextView mFpsSrcTv, mFpsResultTv;

	// 設定
	protected String mPrefName;
	protected SharedPreferences mPref;

	private final CpuMonitor cpuMonitor = new CpuMonitor();

	public BaseAutoPilotFragment() {
		super();
		// デフォルトコンストラクタが必要
	}

	@Override
	public void onDetach() {
		mPref = null;
		super.onDetach();
	}

	@Override
	public void onResume() {
		super.onResume();
		runOnUiThread(mCPUMonitorTask, 1000);
		runOnUiThread(mFpsTask, 1000);
		clearAutoPilot();
	}

	@Override
	public void onPause() {
		removeFromUIThread(mFpsTask);
		removeFromUIThread(mCPUMonitorTask);
		clearAutoPilot();
		super.onPause();
	}

	@Override
	protected View internalCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState, final int layout_id) {
		Bundle args = savedInstanceState;
		if (args == null) {
			args = getArguments();
		}
		mPrefName = args.getString(KEY_PREF_NAME_AUTOPILOT, mPrefName);
		mMode = args.getInt(KEY_AUTOPILOT_MODE, mMode);
		mPref = getActivity().getSharedPreferences(mPrefName, 0);
		// パラメータの読み込み
		mCameraAutoWhiteBlance = getInt(mPref, KEY_CAMERA_WHITE_BLANCE, DEFAULT_CAMERA_WHITE_BLANCE);
		mCameraExposure = mPref.getFloat(KEY_CAMERA_EXPOSURE, DEFAULT_CAMERA_EXPOSURE);
		mCameraSaturation = mPref.getFloat(KEY_CAMERA_SATURATION, DEFAULT_CAMERA_SATURATION);
		mCameraPan = getInt(mPref, KEY_CAMERA_PAN, DEFAULT_CAMERA_PAN);
		mCameraTilt = getInt(mPref, KEY_CAMERA_TILT, DEFAULT_CAMERA_TILT);
		//
		mExposure = mPref.getFloat(KEY_EXPOSURE, DEFAULT_EXPOSURE);
		mSaturation = mPref.getFloat(KEY_SATURATION, DEFAULT_SATURATION);
		mBrightness = mPref.getFloat(KEY_BRIGHTNESS, DEFAULT_BRIGHTNESS);
		mBinarizeThreshold = mPref.getFloat(KEY_BINARIZE_THRESHOLD, DEFAULT_BINARIZE_THRESHOLD);
		//
		mExtractH = mPref.getFloat(KEY_EXTRACT_H, DEFAULT_EXTRACT_H);
		mExtractRangeH = mPref.getFloat(KEY_EXTRACT_RANGE_H, DEFAULT_EXTRACT_RANGE_H);
		mExtractS = mPref.getFloat(KEY_EXTRACT_S, DEFAULT_EXTRACT_S);
		mExtractRangeS = mPref.getFloat(KEY_EXTRACT_RANGE_S, DEFAULT_EXTRACT_RANGE_S);
		mExtractV = mPref.getFloat(KEY_EXTRACT_V, DEFAULT_EXTRACT_V);
		mExtractRangeV = mPref.getFloat(KEY_EXTRACT_RANGE_V, DEFAULT_EXTRACT_RANGE_V);
		//
		mEnableGLESExtraction = mPref.getBoolean(KEY_ENABLE_EXTRACTION, DEFAULT_ENABLE_EXTRACTION);
		//
		mAreaLimitMin = mPref.getFloat(KEY_AREA_LIMIT_MIN, DEFAULT_AREA_LIMIT_MIN);
		mAspectLimitMin = mPref.getFloat(KEY_ASPECT_LIMIT_MIN, DEFAULT_ASPECT_LIMIT_MIN);
		mAreaErrLimit1 = mPref.getFloat(KEY_AREA_ERR_LIMIT1, DEFAULT_AREA_ERR_LIMIT1);
		mAreaErrLimit2 = mPref.getFloat(KEY_AREA_ERR_LIMIT2, DEFAULT_AREA_ERR_LIMIT2);
		//
		mTraceAttitudeYaw = mPref.getFloat(KEY_TRACE_ATTITUDE_YAW, DEFAULT_TRACE_ATTITUDE_YAW);
		mTraceSpeed = mPref.getFloat(KEY_TRACE_SPEED, DEFAULT_TRACE_SPEED);
		mTraceAltitudeEnabled = mPref.getBoolean(KEY_TRACE_ALTITUDE_ENABLED, DEFAULT_TRACE_ALTITUDE_ENABLED);
		mTraceAltitude = Math.min(mPref.getFloat(KEY_TRACE_ALTITUDE, DEFAULT_TRACE_ALTITUDE), mFlightController.getMaxAltitude().current());
		mTraceDirectionalReverseBias = mPref.getFloat(KEY_TRACE_DIR_REVERSE_BIAS, DEFAULT_TRACE_DIR_REVERSE_BIAS);
		mTraceMovingAveTap = mPref.getInt(KEY_TRACE_MOVING_AVE_TAP, DEFAULT_TRACE_MOVING_AVE_TAP);
		mTraceDecayRate = mPref.getFloat(KEY_TRACE_DECAY_RATE, DEFAULT_TRACE_DECAY_RATE);
		mTraceSensitivity = mPref.getFloat(KEY_TRACE_SENSITIVITY, DEFAULT_TRACE_SENSITIVITY);

		// Viewの取得・初期化
		mActionViews.clear();

		final ViewGroup rootView = (ViewGroup) inflater.inflate(R.layout.fragment_pilot_auto, container, false);

		final ViewGroup controllerFrame = rootView.findViewById(R.id.controller_frame);
		controllerFrame.setOnClickListener(mOnClickListener);

// 上パネル
		mTopPanel = rootView.findViewById(R.id.top_panel);
		mTopPanel.setOnClickListener(mOnClickListener);
		mTopPanel.setOnLongClickListener(mOnLongClickListener);
		mActionViews.add(mTopPanel);
		// フラットトリムボタン
		mFlatTrimBtn = rootView.findViewById(R.id.flat_trim_btn);
		mFlatTrimBtn.setOnClickListener(mOnClickListener);
		mFlatTrimBtn.setOnLongClickListener(mOnLongClickListener);
		mActionViews.add(mFlatTrimBtn);
		// 設定表示ボタン
		mConfigShowBtn = rootView.findViewById(R.id.config_show_btn);
		mConfigShowBtn.setOnClickListener(mOnClickListener);
		//
		mBatteryLabel = rootView.findViewById(R.id.batteryLabel);
		mAlertMessage = rootView.findViewById(R.id.alert_message);
		mAlertMessage.setVisibility(View.INVISIBLE);

// 下パネル
		// 非常停止ボタン
//		mBottomPanel = rootView.findViewById(R.id.bottom_panel);
		mEmergencyBtn = rootView.findViewById(R.id.emergency_btn);
		mEmergencyBtn.setOnClickListener(mOnClickListener);
		// 離着陸指示ボタン
		mTakeOnOffBtn = rootView.findViewById(R.id.take_onoff_btn);
		mTakeOnOffBtn.setOnClickListener(mOnClickListener);
		mTakeOnOffBtn.setOnLongClickListener(mOnLongClickListener);
		mActionViews.add(mTakeOnOffBtn);

// 右サイドパネル
		mRightSidePanel = rootView.findViewById(R.id.right_side_panel);
		mActionViews.add(mRightSidePanel);

		// コパイロットボタン
		mCopilotBtn = rootView.findViewById(R.id.copilot_btn);
		mCopilotBtn.setOnClickListener(mOnClickListener);
		mCopilotBtn.setVisibility(mController instanceof ISkyController ? View.VISIBLE : View.GONE);

		// 静止画撮影
		mStillCaptureBtn = rootView.findViewById(R.id.still_capture_btn);
		mStillCaptureBtn.setOnClickListener(mOnClickListener);

		// 動画撮影
		mVideoRecordingBtn = rootView.findViewById(R.id.video_capture_btn);
		mVideoRecordingBtn.setOnClickListener(mOnClickListener);

		// トレース実行
		mTraceButton = rootView.findViewById(R.id.trace_btn);
		mTraceButton.setOnClickListener(mOnClickListener);
		mTraceButton.setOnLongClickListener(mOnLongClickListener);

		if (mController instanceof ICameraController) {
			((ICameraController)mController).setCameraControllerListener(null);
			((ICameraController)mController).sendCameraOrientation(0, 0);
		}

		mVideoView = rootView.findViewById(R.id.drone_view);
		mVideoView.setOnClickListener(mOnClickListener);

		mDetectView = rootView.findViewById(R.id.detect_view);
		mDetectView.setVisibility(View.VISIBLE);
//--------------------------------------------------------------------------------
		final ConfigPagerAdapter adapter = new ConfigPagerAdapter(inflater);
		final ViewPager pager = rootView.findViewById(R.id.pager);
		pager.setAdapter(adapter);
		//
		mTraceTv1 = rootView.findViewById(R.id.trace1_tv);
		mTraceTv2 = rootView.findViewById(R.id.trace2_tv);
		mTraceTv3 = rootView.findViewById(R.id.trace3_tv);
		//
		mCpuLoadTv = rootView.findViewById(R.id.cpu_load_textview);
		//
		mFpsSrcTv = rootView.findViewById(R.id.fps_src_textview);
		mFpsSrcTv.setText(null);
		mFpsResultTv = rootView.findViewById(R.id.fps_result_textview);
		mFpsResultTv.setText(null);

		return rootView;
	}

	private final View.OnClickListener mOnClickListener = new View.OnClickListener() {
		@Override
		public void onClick(final View view) {
			switch (view.getId()) {
			case R.id.flat_trim_btn:
				// フラットトリム
				if ((mFlightController != null) && (getState() == IFlightController.STATE_STARTED)) {
					mFlightController.requestFlatTrim();
				}
				break;
			case R.id.config_show_btn:
				// 設定パネル表示処理
				if (isStarted()) {
					if ((getState() & IFlightController.STATE_MASK_FLYING) == DroneStatus.STATE_FLYING_LANDED) {
						replace(ConfigFragment.newInstance(getDevice(), getDeviceInfo()));
					} else {
						landing();
					}
				}
				break;
			case R.id.trace_btn:
				// 自動操縦ボタン
				clearAutoPilot();	// 自動操縦解除
				break;
			case R.id.emergency_btn:
				// 非常停止指示ボタンの処理
				emergencyStop();
				clearAutoPilot();	// 自動操縦解除
				break;
			case R.id.copilot_btn:
				if ((mController instanceof ISkyController) && mController.isConnected()) {
					((ISkyController)mController).setCoPilotingSource(
						((ISkyController)mController).getCoPilotingSource() == 0 ? 1 : 0
					);
					updateButtons();
				}
				break;
			case R.id.take_onoff_btn:
				// 離陸指示/着陸指示ボタンの処理
				clearAutoPilot();	// 自動操縦解除
				if (!isFlying()) {
//					takeOff();
					Toast.makeText(getActivity(), R.string.notify_takeoff, Toast.LENGTH_SHORT).show();
				} else {
					landing();
				}
				updateButtons();
				break;
			case R.id.still_capture_btn:
				// 静止画撮影ボタンの処理
				if (getStillCaptureState() == DroneStatus.MEDIA_READY) {
					if (mFlightController != null) {
						mFlightController.requestTakePicture();
					}
				}
				break;
			case R.id.video_capture_btn:
				// 動画撮影ボタンの処理
				if (mController instanceof ICameraController) {
					mVideoRecording = !mVideoRecording;
					((ICameraController)mController).sendVideoRecording(mVideoRecording);
				}
				break;
//--------------------------------------------------------------------------------
			case R.id.top_panel:
				// 解析中画像の表示モードを変更
				if (mImageProcessor != null) {
					mImageProcessor.setResultFrameType((mImageProcessor.getResultFrameType() - 2) % 2 + 3);
				}
				break;
			case R.id.drone_view:
				break;
			case R.id.update_extraction_color_btn:
				queueEvent(new Runnable() {
					@Override
					public void run() {
						if (mImageProcessor != null) {
							extractColorChanged(mImageProcessor.requestUpdateExtractionColor());
						}
					}
				}, 0);
				break;
			case R.id.reset_extraction_color_btn:
				// 抽出色をリセット
				if (mImageProcessor != null) {
					extractColorChanged(mImageProcessor.resetExtractionColor());
				}
				break;
			}
		}
	};

	private final View.OnLongClickListener mOnLongClickListener = new View.OnLongClickListener() {
		@Override
		public boolean onLongClick(View view) {
			mVibrator.vibrate(50);
			switch (view.getId()) {
			case R.id.flat_trim_btn:
				clearAutoPilot();	// 自動操縦解除
				if ((mFlightController != null) && (getState() == IFlightController.STATE_STARTED)) {
					replace(CalibrationFragment.newInstance(getDevice()));
					return true;
				}
				break;
			case R.id.take_onoff_btn:
				// 離陸/着陸ボタンを長押しした時の処理
				clearAutoPilot();	// 自動操縦解除
				if (!isFlying()) {
					takeOff();
				} else {
					landing();
				}
				updateButtons();
				return true;
			case R.id.trace_btn:
				clearAutoPilot();	// 自動操縦解除
				if (!mAutoPilot) {
					synchronized (mParamSync) {
						mRequestAutoPilot = mReqUpdateParams = true;
					}
					if (!isFlying()) {
						// 飛行中でなければ離陸指示＆一定時間後に自動トレース開始
						takeOff();
						queueEvent(mAutoPilotOnTask, 500);
					} else {
						// 飛行中ならすぐに自動トレース開始
						mAutoPilot = true;
						if ((mController instanceof ISkyController) && mController.isConnected()) {
							// スマホ/タブレットで操縦する
							((ISkyController)mController).setCoPilotingSource(1);
						}
					}
				} else {
					mAutoPilot = mRequestAutoPilot = false;
				}
				updateButtons();
				return true;
			}
			return false;
		}
	};

	/** 自動操縦解除 */
	protected void clearAutoPilot() {
		mAutoPilot = mRequestAutoPilot = false;
		removeEvent(mAutoPilotOnTask);
		if ((mController instanceof ISkyController) && mController.isConnected()) {
			// スカイコントローラーのジョイスティックで操縦する
			((ISkyController)mController).setCoPilotingSource(0);
		}
		updateButtons();
	}

	private final Runnable mAutoPilotOnTask = new Runnable() {
		@Override
		public void run() {
			synchronized (mParamSync) {
				if ((mController instanceof ISkyController) && mController.isConnected()) {
					// スマホ/タブレットで操縦する
					((ISkyController)mController).setCoPilotingSource(1);
				}
				mAutoPilot = mReqUpdateParams = true;
			}
		}
	};

	private float originalExposure;
	private float originalSaturation;
	private int originalAutoWhiteBlance;
	@Override
	protected void onConnect(final IDeviceController controller) {
		super.onConnect(controller);
		if (controller instanceof ICameraController) {
			final ICameraController camera = (ICameraController)controller;
			originalExposure = camera.exposure();
			originalSaturation = camera.saturation();
			originalAutoWhiteBlance = camera.autoWhiteBalance();
			camera.sendCameraOrientation(mCameraTilt, mCameraPan);
			camera.sendExposure(mCameraExposure);
			camera.sendSaturation(mCameraSaturation);
			camera.sendAutoWhiteBalance(mCameraAutoWhiteBlance);
		}
		mTraceAltitude = Math.min(mPref.getFloat(KEY_TRACE_ALTITUDE, DEFAULT_TRACE_ALTITUDE), mFlightController.getMaxAltitude().current());
		synchronized (mParamSync) {
			mReqUpdateParams = true;
		}
	}

	@Override
	protected void onDisconnect(final IDeviceController controller) {
		clearAutoPilot();	// 自動操縦解除
		stopImageProcessor();
		if (controller instanceof ICameraController) {
			final ICameraController camera = (ICameraController)controller;
			camera.sendCameraOrientation(0, 0);
			camera.sendExposure(originalExposure);
			camera.sendSaturation(originalSaturation);
			camera.sendAutoWhiteBalance(originalAutoWhiteBlance);
		}
		super.onDisconnect(controller);
	}

	@Override
	protected void startVideoStreaming() {
		super.startVideoStreaming();
		try {
			startImageProcessor(512, 256);
		} catch (final Exception e) {
			Log.w(TAG, e);
		}
	}

	@Override
	protected void stopVideoStreaming() {
		clearAutoPilot();	// 自動操縦解除
		stopImageProcessor();
		super.stopVideoStreaming();
	}

	@Override
	protected void updateAlarmMessageOnUIThread(final int alarm) {
		switch (alarm) {
		case DroneStatus.ALARM_NON:					// No alert
			break;
		case DroneStatus.ALARM_USER_EMERGENCY:		// User emergency alert
			mAlertMessage.setText(R.string.alarm_user_emergency);
			break;
		case DroneStatus.ALARM_CUTOUT:				// Cut out alert
			mAlertMessage.setText(R.string.alarm_motor_cut_out);
			break;
		case DroneStatus.ALARM_BATTERY_CRITICAL:	// Critical battery alert
			mAlertMessage.setText(R.string.alarm_low_battery_critical);
			break;
		case DroneStatus.ALARM_BATTERY:				// Low battery alert
			mAlertMessage.setText(R.string.alarm_low_battery);
			break;
		case DroneStatus.ALARM_DISCONNECTED:		// 切断された
			mAlertMessage.setText(R.string.alarm_disconnected);
			break;
		default:
			Log.w(TAG, "unexpected alarm state:" + alarm);
			break;
		}
		mAlertMessage.setVisibility(alarm != 0 ? View.VISIBLE : View.INVISIBLE);
		if (alarm != DroneStatus.ALARM_NON) {
			clearAutoPilot();	// 自動操縦解除
		}
	}

	@Override
	protected void updateBatteryOnUIThread(final int battery) {
		final boolean isSkyController = mController instanceof ISkyController;
		if (mBatteryFmt == null) {
			mBatteryFmt = getString(isSkyController ? R.string.battery_skycontroller : R.string.battery);
		}
		if (battery >= 0) {
			mBatteryLabel.setText(isSkyController
				? String.format(mBatteryFmt, battery, ((ISkyController)mController).getBatterySkyController())
				: String.format(mBatteryFmt, battery));
		} else {
			mBatteryLabel.setText("---");
		}
	}

	@Override
	protected void updateButtons() {
		// 20ミリ秒以内に連続して呼ばれた時は最後のをのみ実行できるように遅延実行する
		removeFromUIThread(mUpdateButtonsTask);
		runOnUiThread(mUpdateButtonsTask, 20);
	}

	/**
	 *　ボタンの表示更新をUIスレッドで行うためのRunnable
	 */
	private final Runnable mUpdateButtonsTask = new Runnable() {
		@Override
		public void run() {
			final int state = getState();
			final int alarm_state = getAlarm();
			final int still_capture_state = getStillCaptureState();
			final int video_recording_state = getVideoRecordingState();
			final boolean is_connected = isStarted();
			final boolean can_fly = alarm_state == DroneStatus.ALARM_NON;
			final boolean can_config = can_fly && (state == IFlightController.STATE_STARTED);
			final boolean is_battery_alarm
				= (alarm_state == DroneStatus.ALARM_BATTERY) || (alarm_state == DroneStatus.ALARM_BATTERY_CRITICAL);

			// 上パネル
			mTopPanel.setEnabled(is_connected);
			mFlatTrimBtn.setEnabled(can_config);	// フラットトリム
			mBatteryLabel.setTextColor(is_battery_alarm ? 0xffff0000 : 0xff9400d3);
			mConfigShowBtn.setEnabled(can_config);
			mConfigShowBtn.setColorFilter(can_config ? 0 : DISABLE_COLOR);

			// 下パネル
			mEmergencyBtn.setEnabled(is_connected);	// 非常停止
			mCopilotBtn.setEnabled(is_connected);	// コパイロット
			mCopilotBtn.setColorFilter(
				(mController instanceof ISkyController)
				&& ((ISkyController)mController).getCoPilotingSource() == 0
					? 0 : 0xffff0000);

			// 離陸/着陸
			switch (state & IFlightController.STATE_MASK_FLYING) {
			case DroneStatus.STATE_FLYING_LANDED:		// 0x0000;		// FlyingState=0
				mVideoView.stopEngine();
			case DroneStatus.STATE_FLYING_LANDING:		// 0x0400;		// FlyingState=4
				mTakeOnOffBtn.setImageResource(R.mipmap.ic_takeoff);
				break;
			case DroneStatus.STATE_FLYING_TAKEOFF:		// 0x0100;		// FlyingState=1
			case DroneStatus.STATE_FLYING_HOVERING:		// 0x0200;		// FlyingState=2
			case DroneStatus.STATE_FLYING_FLYING:		// 0x0300;		// FlyingState=3
			case DroneStatus.STATE_FLYING_ROLLING:		// 0x0600;		// FlyingState=6
				mTakeOnOffBtn.setImageResource(R.mipmap.ic_landing);
				mVideoView.startEngine();
				break;
			case DroneStatus.STATE_FLYING_EMERGENCY:	// 0x0500;		// FlyingState=5
				mVideoView.stopEngine();
				break;
			}

			// 右サイドパネル
			mRightSidePanel.setEnabled(can_fly);

			mStillCaptureBtn.setEnabled(still_capture_state == DroneStatus.MEDIA_READY);
			setChildVisibility(mStillCaptureBtn, still_capture_state != DroneStatus.MEDIA_UNAVAILABLE ? View.VISIBLE : View.INVISIBLE);

			mVideoRecordingBtn.setEnabled((video_recording_state == DroneStatus.MEDIA_READY) || (video_recording_state == DroneStatus.MEDIA_BUSY));
			setChildVisibility(mStillCaptureBtn, video_recording_state != DroneStatus.MEDIA_UNAVAILABLE ? View.VISIBLE : View.INVISIBLE);
			mVideoRecordingBtn.setColorFilter(mVideoRecording ? 0x7fff0000 : 0);

			for (final View view: mActionViews) {
				view.setEnabled(can_fly);
				if (view instanceof ImageView) {
					((ImageView)view).setColorFilter(can_fly ? 0 : DISABLE_COLOR);
				}
			}
			if (!can_fly && (mAutoPilot || mRequestAutoPilot)) {
				clearAutoPilot();	// 自動操縦解除
			}
			mTraceButton.setEnabled(can_fly);
			mTraceButton.setColorFilter(!can_fly ? DISABLE_COLOR : ((mAutoPilot || mRequestAutoPilot) ? TOUCH_RESPONSE_COLOR : 0));
		}
	};

	protected void startImageProcessor(final int processing_width, final int processing_height) {
		mIsRunning = true;
		if (mControlTask == null) {
			mControlTask = new ControlTask(mFlightController);
			new Thread(mControlTask, "Ctrl").start();
		}
	}

	protected void stopImageProcessor() {
		synchronized (mQueue) {
			mIsRunning = false;
			mQueue.notifyAll();
		}
		if (mControlTask != null) {
			mControlTask.release();
			mControlTask = null;
		}
		clearAutoPilot();	// 自動操縦解除
		updateButtons();
	}

	/**
	 * TraceTaskが停止するときのコールバック
	 * @param isError
	 */
	protected void onStopAutoPilot(final boolean isError) {
		mVibrator.vibrate(100);
		clearAutoPilot();	// 自動操縦解除
		updateButtons();
		synchronized (mQueue) {
			mIsRunning = mAutoPilot = false;
			mQueue.clear();
			mPool.clear();
		}
		System.gc();
	}

	protected void addSurface(final int id, final Surface surface) {
		if ((mVideoStream != null) && (id != 0) && (surface != null)) {
			mVideoStream.addSurface(id, surface);
		}
	}

	protected void removeSurface(final int id) {
		if ((mVideoStream != null) && (id != 0)) {
			mVideoStream.removeSurface(id);
		}
	}

	/** 解析データキューの最大サイズ */
	private static final int MAX_QUEUE = 1;
	/** 解析データレコードの再利用のためのプール */
	private final List<LineRec> mPool = new ArrayList<LineRec>();
	/** 解析データキュー */
	private final List<LineRec> mQueue = new ArrayList<LineRec>();
	private volatile boolean mIsRunning;
	/** トレース飛行中 */
	private volatile boolean mAutoPilot, mRequestAutoPilot;
	/** パラメータ変更指示 */
	private boolean mReqUpdateParams;
	/** パラメータの排他制御用 */
	private final Object mParamSync = new Object();

	protected void prepareQueue() {
		synchronized (mQueue) {
			for (int i = 0; i < MAX_QUEUE; i++) {
				mPool.add(new LineRec());
			}
		}
	}

	protected LineRec waitLineRec() {
		LineRec result = null;
		synchronized (mQueue) {
			try {
				// 解析データ待ち
				mQueue.wait(500);
			} catch (final InterruptedException e) {
				// ignore
			}
			if (mQueue.size() > 0) {
				result = mQueue.remove(0);
			}
		}
		return result;
	}

	protected void recycleLineRec(final LineRec rec) {
		if (rec != null) {
			synchronized (mQueue) {
				mPool.add(rec);
			}
		}
	}

	protected static class PilotVector extends Vector {
		public float angle;

		public PilotVector() {
			super();
		}

		public PilotVector(final PilotVector other) {
			super(other);
			angle = other.angle;
		}

		public PilotVector(final Vector other) {
			super(other);
			angle = 0.0f;
		}

		public PilotVector set(final PilotVector src) {
			super.set(src);
			angle = src.angle;
			return this;
		}

		public PilotVector set(final Vector src) {
			super.set(src);
			return this;
		}

		public PilotVector clear(final float scaler) {
			super.clear(scaler);
			angle = scaler;
			return this;
		}
	}

	protected void setMove(final float roll, final float pitch, final float gaz, final float yaw, final float decay_rate) {
		synchronized (mCtrlSync) {
			if (mControlTask != null) {
				mControlTask.roll = roll;
				mControlTask.pitch = pitch;
				mControlTask.gaz = gaz;
				mControlTask.yaw = yaw;
				mControlTask.decayRate = decay_rate;
				mControlTask.requested = true;
			}
			mCtrlSync.notify();
		}
	}

	/** トレース飛行タスク抽象クラス */
	protected abstract class AbstractTraceTask implements Runnable {
		protected static final float EPS_CURVATURE = 1.0e-4f;
		protected static final float MAX_PILOT_ANGLE = 80.0f;	// 一度に修正するyaw角の最大絶対値
		protected static final float MIN_PILOT_ANGLE = 1.0f;	// 0とみなすyaw角のずれの絶対値

		protected final int WIDTH, HEIGHT;
		protected final int CX, CY;
		protected int mMovingAveTap = 0;
		protected final Vector mMovingAve = new Vector();			// オフセットの移動平均
		protected Vector[] mOffsets;
		protected int mOffsetIx;
		protected boolean altitudeControl = mTraceAltitudeEnabled;
		protected float flightAltitude = Math.min(mTraceAltitude, mFlightController.getMaxAltitude().current());
		protected String msg1, msg2;
		public AbstractTraceTask(final int processing_width, final int processing_height) {
			WIDTH = processing_width;
			HEIGHT = processing_height;
			CX = processing_width >>> 1;
			CY = processing_height >>> 1;
		}

		/**
		 * オフセットの移動平均計算用ワーク変数を生成
		 * @param notch
		 */
		protected void createMovingAve(final int notch) {
			if ((mOffsets == null) || (mOffsets.length != notch)) {
				final Vector[] temp = new Vector[notch];
				final int n = mOffsets != null ? mOffsets.length : 0;
				for (int i = 0; i < notch; i++) {
					if (i < n) {
						temp[i] = mOffsets[i];
					}
					if (temp[i] == null) {
						temp[i] = new Vector();
					}
				}
				mOffsets = temp;
				mMovingAveTap = notch;
				clearMovingAve();
			}
		}

		/***
		 * 移動平均計算用ワークをクリア
		 */
		protected void clearMovingAve() {
			mOffsetIx = -1;
			for (int i = 0; i < mMovingAveTap; i++) {
				mOffsets[i].clear(0.0f);
			}
			mMovingAve.clear(0.0f);
		}

		/**
		 * 移動平均を計算
		 * @param offset
		 * @return
		 */
		protected Vector updateMovingAve(final Vector offset) {
			mOffsetIx = (++mOffsetIx) % mMovingAveTap;
			mOffsets[mOffsetIx].set(offset);
			mMovingAve.clear(0.0f);
			for (int i = 0; i < mMovingAveTap; i++) {
				mMovingAve.add(mOffsets[i]);
			}
			mMovingAve.div((float)mMovingAveTap);
			return mMovingAve;
		}

		/** UIで設定値が変更された時の処理, 必要な物をコピーする */
		protected abstract void onUpdateParams();

		/**
		 * 飛行制御値の計算処理
		 * 解析データを受け取ると呼び出される
		 * 引数のLineRecはこのメソッドの呼び出し側でrecycleする
		 * @param rec
		 * @return 飛行制御値
		 */
		protected abstract PilotVector onCalc(final LineRec rec);

		@Override
		public void run() {
			prepareQueue();
			try {
				mIsRunning = mReqUpdateParams = true;
				mAutoPilot = false;
				int mLostCnt = 0;
				final PilotVector mPilotValue = new PilotVector();		// roll,pitch,gaz,yaw制御量
				final PilotVector mPrevPilotValue = new PilotVector();	// roll,pitch,gazの前回制御量
				long startTime = -1L, lostTime = -1L;
				float decayRate = mTraceDecayRate;												// 減衰率
				LineRec rec;
				for ( ; mIsRunning ; ) {
					synchronized (mParamSync) {
						if (mReqUpdateParams) {	// パラメータ変更指示?
							mReqUpdateParams = false;
							altitudeControl = mTraceAltitudeEnabled;
							flightAltitude = Math.min(mTraceAltitude, mFlightController.getMaxAltitude().current());
							if (flightAltitude < 0.5f) {
								flightAltitude = 0.5f;
							}
							decayRate = mTraceDecayRate;
							onUpdateParams();
						}
					}
					rec = waitLineRec();
					if (!mIsRunning) break;
					if (rec != null) {
						try {
							// 解析データを取得できた＼(^o^)／
							if (rec.type >= 0) {
								// ラインを検出した時
								lostTime = -1;
								// 下位クラスで制御量を計算したのをmPilotValueへセット
								mPilotValue.set(onCalc(rec));
							} else {
								// ラインを見失った時
								mLostCnt++;
								msg1 = null;
								mPilotValue.clear(0.0f);
								if (mAutoPilot) {
									if (lostTime < 0) {
										lostTime = System.currentTimeMillis();
										setMove(0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
										clearMovingAve();	// オフセットの移動平均をクリア
									}
									final long t = System.currentTimeMillis() - lostTime;
									if (t > 10000) {	// 10秒以上ラインを見失ったらライントレース解除
										onStopAutoPilot(true);
										mAutoPilot = false;
										startTime = -1L;
										mLostCnt = 0;
										setMove(0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
									}
								}
							}
							//--------------------------------------------------------------------------------
							// トレース飛行中なら制御コマンド送信
							//--------------------------------------------------------------------------------
							if (mAutoPilot) {
								if (startTime < 0) {
									startTime = System.currentTimeMillis();
									mLostCnt = 0;
								}
								if (!altitudeControl) {
									mPilotValue.z = 0.0f;
								}
								final boolean b = !altitudeControl || Math.abs(rec.linePos.z - flightAltitude) < 0.1f;	// 10センチ以内
								if (b || (System.currentTimeMillis() - startTime > 5000)) {
									// 制御コマンド送信
									setMove(mPilotValue.x, mPilotValue.y, mPilotValue.z, mPilotValue.angle, decayRate);
									// 今回の制御量を保存
									if ((lostTime < 0) || (System.currentTimeMillis() - lostTime < 50)) {	// ラインを見失っても50ミリ秒以内なら保持する
										mPrevPilotValue.set(mPilotValue);
									}
								} else {
									// 制御コマンド送信
									setMove(0.0f, 0.0f, mPilotValue.z, 0.0f, decayRate);
									mPrevPilotValue.set(0.0f, 0.0f, mPilotValue.z);
								}
							} else {
								startTime = -1L;
							}
							final String m3 = String.format(Locale.US, "p(%5.1f,%5.1f,%5.1f,%5.1f)", mPilotValue.x, mPilotValue.y, mPilotValue.z, mPilotValue.angle);
							final String m1 = msg1;
							final String m2 = msg2;
							runOnUiThread(new Runnable() {
								@Override
								public void run() {
									mTraceTv1.setText(m1);
									mTraceTv2.setText(m2);
									mTraceTv3.setTextColor(mAutoPilot ? Color.RED : Color.GREEN);
									mTraceTv3.setText(m3);
								}
							});
						} finally {
							recycleLineRec(rec);
						}
					}
				}	// for ( ; mIsRunning ; )
				try {
					setMove(0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
				} catch (final Exception e) {
					// ignore
				}
			} catch (final Exception e) {
				Log.w(TAG, e);
			}
			onStopAutoPilot(!mAutoPilot);
		}
	}

	private class ControlTask implements Runnable {
		private final IFlightController mController;
		private boolean requested;
		private float roll, pitch, gaz, yaw;
		private float decayRate;
		public ControlTask(final IFlightController controller) {
			mController = controller;
			requested = false;
		}

		public void release() {
			mIsRunning = false;
			synchronized (this) {
				this.notifyAll();
			}
		}

		@Override
		public void run() {
			float local_roll = 0.0f, local_pitch = 0.0f, local_gaz, local_yaw = 0.0f;
			// 少しスレッドの優先順位を上げる
			Process.setThreadPriority(Process. THREAD_PRIORITY_DISPLAY);	// -4
			for (; mIsRunning ; ) {
				synchronized (this) {
					try {
						wait(50);
					} catch (final InterruptedException e) {
						break;
					}
					if (!mIsRunning) break;
					if (mAutoPilot) {
						if (requested) {
							local_roll = roll;
							local_pitch = pitch;
							local_gaz = gaz;
							local_yaw = yaw;
						} else {
							local_roll *= decayRate;
							local_pitch *= decayRate;
							local_yaw *= decayRate;
							local_gaz = 0.0f;
						}
					} else {
						local_roll = local_pitch = local_gaz = local_yaw = 0.0f;
					}
					requested = false;
				}
				sendMove(local_roll, local_pitch, local_gaz, local_yaw);
			} // for (; mIsRunning ; )
			Process.setThreadPriority(Process. THREAD_PRIORITY_DEFAULT);	// 0
			mIsRunning = false;
			try {
				mController.setMove(0.0f, 0.0f, 0.0f, 0.0f);
			} catch (final Exception e) {
				// ignore
			}
			clearAutoPilot();
		}
	}

	protected class MyImageProcessorCallback implements ImageProcessor.ImageProcessorCallback {
		private final int width, height;
		private final Matrix matrix = new Matrix();
		private Bitmap mFrame;
		protected MyImageProcessorCallback(final int processing_width, final int processing_height) {
			width = processing_width;
			height = processing_height;
		}

		@Override
		public void onFrame(final ByteBuffer frame) {
			if (mDetectView != null) {
				final SurfaceHolder holder = mDetectView.getHolder();
				if ((holder == null) || (holder.getSurface() == null)) return;
				if (mFrame == null) {
					mFrame = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
					final float scaleX = mDetectView.getWidth() / (float)width;
					final float scaleY = mDetectView.getHeight() / (float)height;
					matrix.reset();
					matrix.postScale(scaleX, scaleY);
				}
				frame.clear();
				mFrame.copyPixelsFromBuffer(frame);
				final Canvas canvas = holder.lockCanvas();
				if (canvas != null) {
					try {
						canvas.drawBitmap(mFrame, matrix, null);
					} catch (final Exception e) {
						Log.w(TAG, e);
					} finally {
						holder.unlockCanvasAndPost(canvas);
					}
				}
			}
		}

		@Override
		public void onResult(final int type, final float[] result) {
			synchronized (mQueue) {
				if (!mIsRunning) return;
				LineRec rec = mPool.size() > 0 ? mPool.remove(0) : null;
				if (rec == null) {
					rec = new LineRec();
				}
				rec.type = type;
				// ライン最小矩形の中心座標(位置ベクトル,cv::RotatedRect#center)
				rec.linePos.set(result[0], result[1], mFlightController.getAltitude());
				// ラインの長さ(長軸長さ=length)
				rec.lineLen = result[2];
				// ライン幅(短軸長さ)
				rec.lineWidth = result[3];
				// ラインの方向(cv::RotatedRect#angle)
				rec.angle = result[4];
				// 最小矩形面積に対する輪郭面積の比
				rec.areaRate = result[5];
				// 楕円フィッティングの曲率
				rec.curvature = result[6];
				// 近似楕円の中心座標, curvature==0の時は無効(0,0)
				rec.ellipsePos.set(result[7], result[8], 0.0f);
				rec.ellipseA = result[9];		// 楕円の幅
				rec.ellipseB = result[10];		// 楕円の高さ
				rec.ellipseAngle = result[11];	// 楕円の回転角
				// 重心座標
				rec.center.set(result[12], result[13], 0.0f);
				// 概算処理時間
				rec.processingTimeMs = (long)(result[19]);
				// キュー内に最大数入っていたら先頭(一番古いもの)をプールに戻す(と言っても最新１つしか保持しないけど)
				for ( ; mQueue.size() > MAX_QUEUE ; ) {
					mPool.add(mQueue.remove(0));
				}
				// キューの最後に追加
				mQueue.add(rec);
				mQueue.notify();
			}
		}

	}

//================================================================================
// ここから下はパラメータ関係
//================================================================================
	private int getInt(final SharedPreferences pref, final String key, final int default_value) {
		int result = default_value;
		try {
			result = pref.getInt(key, default_value);
		} catch (final Exception e) {
			try {
				result = Integer.parseInt(pref.getString(key, Integer.toString(default_value)));
			} catch (final Exception e1) {
				// ignore
			}
		}
		return result;
	}

	private static class WhiteBlanceAdapter extends ArrayAdapter<String> {
		private final String[] values;
		public WhiteBlanceAdapter(final Context context) {
			super(context, android.R.layout.simple_dropdown_item_1line);
			values = context.getResources().getStringArray(R.array.trace_white_blance_value);
			final String[] entries = context.getResources().getStringArray(R.array.trace_white_blance_entries);
			addAll(entries);
		}

		@SuppressWarnings("deprecation")
		@NonNull
		@Override
		public View getView(final int position, final View convertView, @NonNull final ViewGroup parent) {
			final View rootView = super.getView(position, convertView, parent);
			changeColor(rootView, getContext().getResources().getColor(R.color.WHITE));
			return rootView;
		}

		private void changeColor(final View view, final int cl) {
			if (view instanceof TextView) {
				((TextView)view).setTextColor(cl);
			} else if (view instanceof ViewGroup) {
				final ViewGroup parent = (ViewGroup)view;
				final int n = parent.getChildCount();
				for (int i = 0; i < n; i++) {
					changeColor(parent.getChildAt(i), cl);
				}
			}
		}
	}

	private final AdapterView.OnItemSelectedListener mOnItemSelectedListener
		= new AdapterView.OnItemSelectedListener() {
		@Override
		public void onItemSelected(final AdapterView<?> parent, final View view, final int position, final long id) {
			switch (parent.getId()) {
			case R.id.camera_white_blance_spinner:
				final int white_blance = position - 1;
				if (mCameraAutoWhiteBlance != white_blance) {
					mCameraAutoWhiteBlance = white_blance;
					if (mController instanceof ICameraController) {
						((ICameraController)mController).sendAutoWhiteBalance(white_blance);
					}
					if (mPref != null) {
						mPref.edit().putInt(KEY_CAMERA_WHITE_BLANCE, white_blance).apply();
					}
				}
				break;
			}
		}

		@Override
		public void onNothingSelected(final AdapterView<?> parent) {
			switch (parent.getId()) {
			case R.id.camera_white_blance_spinner:
				if (mController instanceof ICameraController) {
					((ICameraController)mController).sendAutoWhiteBalance(0);
				}
				if (mPref != null) {
					mPref.edit().putInt(KEY_CAMERA_WHITE_BLANCE, 0).apply();
				}
				break;
			}
		}
	};

	private final CompoundButton.OnCheckedChangeListener mOnCheckedChangeListener
		= new CompoundButton.OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(final CompoundButton buttonView, final boolean isChecked) {
			switch (buttonView.getId()) {
			case R.id.use_extract_sw:
				if (mImageProcessor != null) {
					mEnableGLESExtraction = isChecked;
					mImageProcessor.enableExtraction(isChecked);
					mImageProcessor.setBinarizeThreshold(mBinarizeThreshold);
				}
				if (mPref != null) {
					mPref.edit().putBoolean(KEY_ENABLE_EXTRACTION, isChecked).apply();
				}
				break;
//			case R.id.use_fill_contour_sw:
//				if (mImageProcessor != null) {
//					mImageProcessor.setFillInnerContour(isChecked);
//				}
//				if (mPref != null) {
//					mPref.edit().putBoolean(KEY_FILL_INNER_CONTOUR, isChecked).apply();
//				}
//				break;
			case R.id.trace_flight_altitude_enable_switch:
				synchronized (mParamSync) {
					mTraceAltitudeEnabled = isChecked;
					mReqUpdateParams = true;
				}
				if (mPref != null) {
					mPref.edit().putBoolean(KEY_TRACE_ALTITUDE_ENABLED, mTraceAltitudeEnabled).apply();
				}
				break;
			}
		}
	};

	private final SeekBar.OnSeekBarChangeListener mOnSeekBarChangeListener
		= new SeekBar.OnSeekBarChangeListener() {
		@Override
		public void onProgressChanged(final SeekBar seekBar, final int progress, final boolean fromUser) {
			if (!fromUser) return;
			switch (seekBar.getId()) {
			case R.id.camera_exposure_seekbar:
				final float camera_exposure = progressToCameraExposure(progress);	// [0,3000] => [-1.5f, +1.5f]
				if (mCameraExposure != camera_exposure) {
					mCameraExposure = camera_exposure;
					if (mController instanceof ICameraController) {
						((ICameraController)mController).sendExposure(camera_exposure);
					}
					updateCameraExposure(camera_exposure);
				}
				break;
			case R.id.camera_saturation_seekbar:
				final float camera_saturation = (progress - 1000) / 10.0f;	// [0,2000] => [-100.0f, +100.0f]
				if (mCameraSaturation != camera_saturation) {
					mCameraSaturation = camera_saturation;
					if (mController instanceof ICameraController) {
						((ICameraController)mController).sendSaturation(camera_saturation);
					}
					updateCameraSaturation(camera_saturation);
				}
				break;
			case R.id.camera_pan_seekbar:
				final int camera_pan = progressToPanTilt(progress);
				if (mCameraPan != camera_pan) {
					mCameraPan = camera_pan;
					if (mController instanceof ICameraController) {
						((ICameraController)mController).sendCameraOrientation(mCameraTilt, camera_pan);
					}
					updateCameraPan(camera_pan);
				}
				break;
			case R.id.camera_tilt_seekbar:
				final int camera_tilt = progressToPanTilt(progress);
				if (mCameraTilt != camera_tilt) {
					mCameraTilt = camera_tilt;
					if (mController instanceof ICameraController) {
						((ICameraController)mController).sendCameraOrientation(camera_tilt, mCameraPan);
					}
					updateCameraTilt(camera_tilt);
				}
				break;
			case R.id.exposure_seekbar:
				final float exposure = progressToExposure(progress);	// [0,3000] => [-1.5f, +1.5f]
				if (mExposure != exposure) {
					mExposure = exposure;
					if (mImageProcessor != null) {
						mImageProcessor.setExposure(exposure);
					}
					updateExposure(exposure);
				}
				break;
			case R.id.saturation_seekbar:
				final float saturation = (progress - 100) / 100.0f;	// [0,200] => [-1.0f, +1.0f]
				if (mSaturation != saturation) {
					mSaturation = saturation;
					if (mImageProcessor != null) {
						mImageProcessor.setSaturation(saturation);
					}
					updateSaturation(saturation);
				}
				break;
			case R.id.brightness_seekbar:
				final float brightness = (progress - 100) / 100.0f;	// [0,200] => [-1.0f, +1.0f]
				if (mBrightness != brightness) {
					mBrightness = brightness;
					if (mImageProcessor != null) {
						mImageProcessor.setBrightness(brightness);
					}
					updateBrightness(brightness);
				}
				break;
			case R.id.binarize_threshold_seekbar:
				final float threshold = progress / 100.0f;
				if (mBinarizeThreshold != threshold) {
					mBinarizeThreshold = threshold;
					if (mImageProcessor != null) {
						mImageProcessor.setBinarizeThreshold(threshold);
					}
					updateBinarizeThreshold(threshold);
				}
				break;
			case R.id.extract_range_h_seekbar:
				final float range_h = progress / 100.0f;
				if (mExtractRangeH != range_h) {
					synchronized (mParamSync) {
						mExtractRangeH = range_h;
					}
					updateExtractRangeH(range_h);
					applyExtractRange(range_h, mExtractRangeS, mExtractRangeV);
				}
				break;
			case R.id.extract_range_s_seekbar:
				final float range_s = progress / 100.0f;
				if (mExtractRangeS != range_s) {
					synchronized (mParamSync) {
						mExtractRangeS = range_s;
					}
					updateExtractRangeS(range_s);
					applyExtractRange(mExtractRangeH, range_s, mExtractRangeV);
				}
				break;
			case R.id.extract_range_v_seekbar:
				final float range_v = progress / 100.0f;
				if (mExtractRangeV != range_v) {
					synchronized (mParamSync) {
						mExtractRangeV = range_v;
					}
					updateExtractRangeV(range_v);
					applyExtractRange(mExtractRangeH, mExtractRangeS, range_v);
				}
				break;
			case R.id.area_limit_min_seekbar:
				final float area_limit_min = progressToAreaLimitMin(progress);
				if (mAreaLimitMin != area_limit_min) {
					mAreaLimitMin = area_limit_min;
					if (mImageProcessor != null) {
						mImageProcessor.setAreaLimit(area_limit_min, AREA_LIMIT_MAX);
					}
					updateAreaLimitMin(area_limit_min);
				}
				break;
			case R.id.area_err_limit1_seekbar:
				final float area_err_limit1 = (progress / 100.0f) + 1.0f;
				if (mAreaErrLimit1 != area_err_limit1) {
					mAreaErrLimit1 = area_err_limit1;
					if (mImageProcessor != null) {
						mImageProcessor.setAreaErrLimit(area_err_limit1, mAreaErrLimit2);
					}
					updateAreaErrLimit1(area_err_limit1);
				}
				break;
			case R.id.area_err_limit2_seekbar:
				final float area_err_limit2 = (progress / 100.0f) + 1.0f;
				if (mAreaErrLimit2 != area_err_limit2) {
					mAreaErrLimit2 = area_err_limit2;
					if (mImageProcessor != null) {
						mImageProcessor.setAreaErrLimit(mAreaErrLimit1, area_err_limit2);
					}
					updateAreaErrLimit2(area_err_limit2);
				}
				break;
			case R.id.aspect_limit_min_seekbar:
				final float aspect = (progress / 10.0f) + 1.0f;
				if (mAspectLimitMin != aspect) {
					mAspectLimitMin = aspect;
					if (mImageProcessor != null) {
						mImageProcessor.setAspectLimit(aspect);
					}
					updateAspectLimitMin(aspect);
				}
				break;
			case R.id.max_altitude_seekbar:
				final float altitude = (int) (progress / 100f * (mMaxAltitude.max() - mMaxAltitude.min())) / 10f + mMaxAltitude.min();
				updateMaxAltitude(altitude);
				break;
			case R.id.max_tilt_seekbar:
				final float tilt = (int) (progress / 100f * (mMaxTilt.max() - mMaxTilt.min())) / 10f + mMaxTilt.min();
				updateMaxTilt(tilt);
				break;
			case R.id.max_vertical_speed_seekbar:
				final float vertical = (int) (progress / 100f * (mMaxVerticalSpeed.max() - mMaxVerticalSpeed.min())) / 10f + mMaxVerticalSpeed.min();
				updateMaxVerticalSpeed(vertical);
				break;
			case R.id.max_rotation_speed_seekbar:
				final float rotation = (int) (progress / 1000f * (mMaxRotationSpeed.max() - mMaxRotationSpeed.min())) + mMaxRotationSpeed.min();
				updateMaxRotationSpeed(rotation);
				break;
			case R.id.max_control_value_seekbar:	// -500〜+500
				final float max_control_value = progress - APP_SCALE_OFFSET;
				updateAutopilotMaxControlValue(max_control_value);
				break;
			case R.id.scale_seekbar_x:
				final float scale_x = (progress - APP_SCALE_OFFSET) / APP_SCALE_FACTOR;
				updateAutopilotScaleX(scale_x);
				break;
			case R.id.scale_seekbar_y:
				final float scale_y = (progress - APP_SCALE_OFFSET) / APP_SCALE_FACTOR;
				updateAutopilotScaleY(scale_y);
				break;
			case R.id.scale_seekbar_z:
				final float scale_z = (progress - APP_SCALE_OFFSET) / APP_SCALE_FACTOR;
				updateAutopilotScaleZ(scale_z);
				break;
			case R.id.scale_seekbar_r:
				final float scale_r = (progress - APP_SCALE_OFFSET) / APP_SCALE_FACTOR;
				updateAutopilotScaleR(scale_r);
				break;
			case R.id.trace_flight_attitude_yaw_seekbar:
				final float attitude_yaw = progress - 90;
				updateTraceAttitudeYaw(attitude_yaw);
				break;
			case R.id.trace_flight_speed_seekbar:
				final float speed = progress - 100;
				updateTraceSpeed(speed);
				break;
			case R.id.trace_flight_altitude_seekbar:
				final float trace_altitude = progress / 10.0f + 0.5f;
				updateTraceAltitude(trace_altitude);
				break;
			case R.id.trace_flight_reverse_bias_seekbar:
				final float bias = progress / 100.0f;
				updateTraceDirectionalReverseBias(bias);
				break;
			case R.id.trace_flight_moving_ave_tap_seekbar:
				updateTraceMovingAveTap(progress + 1);
				break;
			case R.id.trace_flight_decay_rate_seekbar:
				updateTraceDecayRate(progress / 1000.0f);
				break;
			case R.id.trace_flight_sensitivity_seekbar:
				updateTraceSensitivity(progress);
				break;
			}
		}

		@Override
		public void onStartTrackingTouch(final SeekBar seekBar) {
		}

		@Override
		public void onStopTrackingTouch(final SeekBar seekBar) {
			switch (seekBar.getId()) {
			case R.id.camera_exposure_seekbar:
				if (mPref != null) {
					mPref.edit().putFloat(KEY_CAMERA_EXPOSURE, mCameraExposure).apply();
				}
				break;
			case R.id.camera_saturation_seekbar:
				if (mPref != null) {
					mPref.edit().putFloat(KEY_CAMERA_SATURATION, mCameraSaturation).apply();
				}
				break;
			case R.id.camera_pan_seekbar:
				if (mPref != null) {
					mPref.edit().putInt(KEY_CAMERA_PAN, mCameraPan).apply();
				}
				break;
			case R.id.camera_tilt_seekbar:
				if (mPref != null) {
					mPref.edit().putInt(KEY_CAMERA_TILT, mCameraTilt).apply();
				}
				break;
			case R.id.exposure_seekbar:
				if (mPref != null) {
					mPref.edit().putFloat(KEY_EXPOSURE, mExposure).apply();
				}
				break;
			case R.id.saturation_seekbar:
				if (mPref != null) {
					mPref.edit().putFloat(KEY_SATURATION, mSaturation).apply();
				}
				break;
			case R.id.brightness_seekbar:
				if (mPref != null) {
					mPref.edit().putFloat(KEY_BRIGHTNESS, mBrightness).apply();
				}
				break;
			case R.id.binarize_threshold_seekbar:
				if (mPref != null) {
					mPref.edit().putFloat(KEY_BINARIZE_THRESHOLD, mBinarizeThreshold).apply();
				}
				break;
			case R.id.extract_range_h_seekbar:
				if (mPref != null) {
					mPref.edit().putFloat(KEY_EXTRACT_RANGE_H, mExtractRangeH).apply();
				}
				break;
			case R.id.extract_range_s_seekbar:
				if (mPref != null) {
					mPref.edit().putFloat(KEY_EXTRACT_RANGE_S, mExtractRangeS).apply();
				}
				break;
			case R.id.extract_range_v_seekbar:
				if (mPref != null) {
					mPref.edit().putFloat(KEY_EXTRACT_RANGE_V, mExtractRangeV).apply();
				}
				break;
			case R.id.area_limit_min_seekbar:
				if (mPref != null) {
					mPref.edit().putFloat(KEY_AREA_LIMIT_MIN, mAreaLimitMin).apply();
				}
				break;
			case R.id.area_err_limit1_seekbar:
				if (mPref != null) {
					mPref.edit().putFloat(KEY_AREA_ERR_LIMIT1, mAreaErrLimit1).apply();
				}
				break;
			case R.id.area_err_limit2_seekbar:
				if (mPref != null) {
					mPref.edit().putFloat(KEY_AREA_ERR_LIMIT2, mAreaErrLimit2).apply();
				}
				break;
			case R.id.aspect_limit_min_seekbar:
				if (mPref != null) {
					mPref.edit().putFloat(KEY_ASPECT_LIMIT_MIN, mAspectLimitMin).apply();
				}
				break;
			case R.id.max_altitude_seekbar:
				final float altitude = (int)(seekBar.getProgress() / 100f * (mMaxAltitude.max() - mMaxAltitude.min())) / 10f + mMaxAltitude.min();
				if (altitude != mMaxAltitude.current()) {
					mFlightController.setMaxAltitude(altitude);
				}
				break;
			case R.id.max_tilt_seekbar:
				final float tilt = (int)(seekBar.getProgress() / 100f * (mMaxTilt.max() - mMaxTilt.min())) / 10f + mMaxTilt.min();
				if (tilt != mMaxTilt.current()) {
					mFlightController.setMaxTilt(tilt);
				}
				break;
			case R.id.max_vertical_speed_seekbar:
				final float vertical = (int)(seekBar.getProgress() / 100f * (mMaxVerticalSpeed.max() - mMaxVerticalSpeed.min())) / 10f + mMaxVerticalSpeed.min();
				if (vertical != mMaxVerticalSpeed.current()) {
					mFlightController.setMaxVerticalSpeed(vertical);
				}
				break;
			case R.id.max_rotation_speed_seekbar:
				final float rotation = (int)(seekBar.getProgress() / 1000f * (mMaxRotationSpeed.max() - mMaxRotationSpeed.min())) + mMaxRotationSpeed.min();
				if (rotation != mMaxRotationSpeed.current()) {
					mFlightController.setMaxRotationSpeed(rotation);
				}
				break;
			// 自動操縦
			case R.id.max_control_value_seekbar:
				final float max_control_value = seekBar.getProgress() - APP_SCALE_OFFSET;
				if (max_control_value != mMaxControlValue) {
					synchronized (mParamSync) {
						mReqUpdateParams = true;
						mMaxControlValue = max_control_value;
					}
					mPref.edit().putFloat(APP_CONFIG_KEY_MAX_CONTROL_VALUE, max_control_value).apply();
				}
				break;
			case R.id.scale_seekbar_x:
				final float scale_x = (seekBar.getProgress() - APP_SCALE_OFFSET) / APP_SCALE_FACTOR;
				if (scale_x != mScaleX) {
					synchronized (mParamSync) {
						mReqUpdateParams = true;
						mScaleX = scale_x;
					}
					mPref.edit().putFloat(APP_CONFIG_KEY_SCALE_X, scale_x).apply();
				}
				break;
			case R.id.scale_seekbar_y:
				final float scale_y = (seekBar.getProgress() - APP_SCALE_OFFSET) / APP_SCALE_FACTOR;
				if (scale_y != mScaleY) {
					synchronized (mParamSync) {
						mReqUpdateParams = true;
						mScaleY = scale_y;
					}
					mPref.edit().putFloat(APP_CONFIG_KEY_SCALE_Y, scale_y).apply();
				}
				break;
			case R.id.scale_seekbar_z:
				final float scale_z = (seekBar.getProgress() - APP_SCALE_OFFSET) / APP_SCALE_FACTOR;
				if (scale_z != mScaleZ) {
					synchronized (mParamSync) {
						mReqUpdateParams = true;
						mScaleZ = scale_z;
					}
					mPref.edit().putFloat(APP_CONFIG_KEY_SCALE_Z, scale_z).apply();
				}
				break;
			case R.id.scale_seekbar_r:
				final float scale_r = (seekBar.getProgress() - APP_SCALE_OFFSET) / APP_SCALE_FACTOR;
				if (scale_r != mScaleR) {
					synchronized (mParamSync) {
						mReqUpdateParams = true;
						mScaleR = scale_r;
					}
					mPref.edit().putFloat(APP_CONFIG_KEY_SCALE_R, scale_r).apply();
				}
				break;
			case R.id.trace_flight_attitude_yaw_seekbar:
				final float attitude_yaw = seekBar.getProgress() - 90;
				if (attitude_yaw != mTraceAttitudeYaw) {
					synchronized (mParamSync) {
						mReqUpdateParams = true;
						mTraceAttitudeYaw = attitude_yaw;
					}
					mPref.edit().putFloat(KEY_TRACE_ATTITUDE_YAW, attitude_yaw).apply();
				}
				break;
			case R.id.trace_flight_speed_seekbar:
				final float speed = seekBar.getProgress() - 100;
				if (speed != mTraceSpeed) {
					synchronized (mParamSync) {
						mReqUpdateParams = true;
						mTraceSpeed = speed;
					}
					mPref.edit().putFloat(KEY_TRACE_SPEED, speed).apply();
				}
				break;
			case R.id.trace_flight_altitude_seekbar:
				final float trace_altitude = seekBar.getProgress() / 10.0f + 0.5f;
				if (trace_altitude != mTraceAltitude) {
					synchronized (mParamSync) {
						mReqUpdateParams = true;
						mTraceAltitude = trace_altitude;
					}
					mPref.edit().putFloat(KEY_TRACE_ALTITUDE, trace_altitude).apply();
				}
				break;
			case R.id.trace_flight_reverse_bias_seekbar:
				final float bias = seekBar.getProgress() / 100.0f;
				if (bias != mTraceDirectionalReverseBias) {
					synchronized (mParamSync) {
						mReqUpdateParams = true;
						mTraceDirectionalReverseBias = bias;
					}
					mPref.edit().putFloat(KEY_TRACE_DIR_REVERSE_BIAS, bias).apply();
				}
				break;
			case R.id.trace_flight_moving_ave_tap_seekbar:
				final int notch = seekBar.getProgress() + 1;
				if (notch != mTraceMovingAveTap) {
					synchronized (mParamSync) {
						mReqUpdateParams = true;
						mTraceMovingAveTap = notch;
					}
					mPref.edit().putInt(KEY_TRACE_MOVING_AVE_TAP, notch).apply();
				}
				break;
			case R.id.trace_flight_decay_rate_seekbar:
				final float decay_rate = seekBar.getProgress() / 1000.0f;
				if (decay_rate != mTraceDecayRate) {
					synchronized (mParamSync) {
						mReqUpdateParams = true;
						mTraceDecayRate = decay_rate;
					}
					mPref.edit().putFloat(KEY_TRACE_DECAY_RATE, decay_rate).apply();
				}
				break;
			case R.id.trace_flight_sensitivity_seekbar:
				final float sensitivity = seekBar.getProgress();
				if (sensitivity != mTraceSensitivity) {
					synchronized (mParamSync) {
						mReqUpdateParams = true;
						mTraceSensitivity = sensitivity;
					}
					mPref.edit().putFloat(KEY_TRACE_SENSITIVITY, sensitivity).apply();
				}
				break;
			}
		}
	};

//--------------------------------------------------------------------------------
	private String mCameraExposureFormat;
	private String mCameraSaturationFormat;
	private String mCameraPanFormat;
	private String mCameraTiltFormat;
	private TextView mCameraExposureLabel;
	private TextView mCameraSaturationLabel;
	private TextView mCameraPanLabel;
	private TextView mCameraTiltLabel;
	/** ホワイトバランス */
	protected int mCameraAutoWhiteBlance;
	/** 露出 */
	protected float mCameraExposure;
	/** 彩度 */
	protected float mCameraSaturation;
	/** パン */
	protected int mCameraPan;
	/** チルト */
	protected int mCameraTilt;

	private void initCamera(final View rootView) {
		mCameraExposureFormat = getString(R.string.trace_camera_exposure);
		mCameraSaturationFormat = getString(R.string.trace_camera_saturation);
		mCameraPanFormat = getString(R.string.trace_camera_pan);
		mCameraTiltFormat = getString(R.string.trace_camera_tilt);

		SeekBar sb;
		// ホワイトバランス
		mCameraAutoWhiteBlance = getInt(mPref, KEY_CAMERA_WHITE_BLANCE, DEFAULT_CAMERA_WHITE_BLANCE);
		final Spinner spinner = rootView.findViewById(R.id.camera_white_blance_spinner);
		spinner.setAdapter(new WhiteBlanceAdapter(getActivity()));
		spinner.setSelection(mCameraAutoWhiteBlance + 1);
		spinner.setOnItemSelectedListener(mOnItemSelectedListener);
		// 露出
		mCameraExposure = mPref.getFloat(KEY_CAMERA_EXPOSURE, DEFAULT_CAMERA_EXPOSURE);
		mCameraExposureLabel = rootView.findViewById(R.id.camera_exposure_textview);
		sb = rootView.findViewById(R.id.camera_exposure_seekbar);
		sb.setMax(3000);
		sb.setProgress(cameraExposureToProgress(mCameraExposure));	// [-1.5,+ 1.5] => [0, 3000]
		sb.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
		updateCameraExposure(mCameraExposure);
		// 彩度
		mCameraSaturation = mPref.getFloat(KEY_CAMERA_SATURATION, DEFAULT_CAMERA_SATURATION);
		mCameraSaturationLabel = rootView.findViewById(R.id.camera_saturation_textview);
		sb = rootView.findViewById(R.id.camera_saturation_seekbar);
		sb.setMax(2000);
		sb.setProgress((int)(mCameraSaturation * 10.0f) + 1000);	// [-100.0f, +100.0f] => [0, 2000]
		sb.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
		updateCameraSaturation(mCameraSaturation);
		// パン
		mCameraPan = getInt(mPref, KEY_CAMERA_PAN, DEFAULT_CAMERA_PAN);
		mCameraPanLabel = rootView.findViewById(R.id.camera_pan_textview);
		sb = rootView.findViewById(R.id.camera_pan_seekbar);
		sb.setMax(40);
		sb.setProgress(cameraPanTiltToProgress(mCameraPan));	// [-100,+100] => [0, 40]
		sb.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
		updateCameraPan(mCameraPan);
		// チルト
		mCameraTilt = getInt(mPref, KEY_CAMERA_TILT, DEFAULT_CAMERA_TILT);
		mCameraTiltLabel = rootView.findViewById(R.id.camera_tilt_textview);
		sb = rootView.findViewById(R.id.camera_tilt_seekbar);
		sb.setMax(40);
		sb.setProgress(cameraPanTiltToProgress(mCameraTilt));	// [-100,+100] => [0, 40]
		sb.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
		updateCameraTilt(mCameraTilt);
	}

	private void releaseCamera(final View rootView) {
		mCameraExposureLabel = null;
		mCameraSaturationLabel = null;
	}

	private int cameraExposureToProgress(final float exposure) {
		return (int)(Math.signum(exposure) * (Math.sqrt(Math.abs(exposure * 1500000)))) + 1500;
	}

	private float progressToCameraExposure(final int progress) {
		final int p = progress - 1500;
		return Math.signum(p) * (p * p / 1500000.0f);
	}

	private void updateCameraExposure(final float exposure) {
		if (mCameraExposureLabel != null) {
			mCameraExposureLabel.setText(String.format(mCameraExposureFormat, exposure));
		}
	}

	private void updateCameraSaturation(final float saturation) {
		if (mCameraSaturationLabel != null) {
			mCameraSaturationLabel.setText(String.format(mCameraSaturationFormat, saturation));
		}
	}

	private int cameraPanTiltToProgress(final int pantilt) {
		return (pantilt + 100) / 5;
	}

	private int progressToPanTilt(final int progress) {
		return progress * 5 - 100;
	}

	private void updateCameraPan(final int pan) {
		if (mCameraPanLabel != null) {
			mCameraPanLabel.setText(String.format(mCameraPanFormat, pan));
		}
	}

	private void updateCameraTilt(final int pan) {
		if (mCameraTiltLabel != null) {
			mCameraTiltLabel.setText(String.format(mCameraTiltFormat, pan));
		}
	}
//--------------------------------------------------------------------------------
	private String mExposureFormat;
	private String mSaturationFormat;
	private String mBrightnessFormat;
	private String mBinarizeThresholdFormat;
	private TextView mExposureLabel;
	private TextView mSaturationLabel;
	private TextView mBrightnessLabel;
	private TextView mBinarizeThresholdLabel;
	/** 露出 */
	protected float mExposure;
	/** 彩度 */
	protected float mSaturation;
	/** 明るさ */
	protected float mBrightness;
	/** 2値化閾値 */
	protected float mBinarizeThreshold;

	private void initPreprocess(final View rootView) {
		mExposureFormat = getString(R.string.trace_use_exposure);
		mSaturationFormat = getString(R.string.trace_use_saturation);
		mBrightnessFormat = getString(R.string.trace_use_brightness);
		mBinarizeThresholdFormat = getString(R.string.trace_binarize_threshold);

		SeekBar sb;
		Button btn;
		// 露出
		mExposure = mPref.getFloat(KEY_EXPOSURE, DEFAULT_EXPOSURE);
		mExposureLabel = rootView.findViewById(R.id.exposure_textview);
		sb = rootView.findViewById(R.id.exposure_seekbar);
		sb.setMax(6000);
		sb.setProgress(exposureToProgress(mExposure));	// [-3,+ 3] => [0, 6000]
		sb.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
		updateExposure(mExposure);
		// 彩度
		mSaturation = mPref.getFloat(KEY_SATURATION, DEFAULT_SATURATION);
		mSaturationLabel = rootView.findViewById(R.id.saturation_textview);
		sb = rootView.findViewById(R.id.saturation_seekbar);
		sb.setMax(200);
		sb.setProgress((int)(mSaturation * 100.0f) + 100);	// [-1.0f, +1.0f] => [0, 200]
		sb.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
		updateSaturation(mSaturation);
		// 明るさ
		mBrightness = mPref.getFloat(KEY_BRIGHTNESS, DEFAULT_BRIGHTNESS);
		mBrightnessLabel = rootView.findViewById(R.id.brightness_textview);
		sb = rootView.findViewById(R.id.brightness_seekbar);
		sb.setMax(200);
		sb.setProgress((int)(mBrightness * 100.0f) + 100);	// [-1.0f, +1.0f] => [0, 200]
		sb.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
		updateBrightness(mBrightness);
		// 二値化閾値
		mBinarizeThreshold = mPref.getFloat(KEY_BINARIZE_THRESHOLD, DEFAULT_BINARIZE_THRESHOLD);
		mBinarizeThresholdLabel = rootView.findViewById(R.id.binarize_threshold_textview);
		sb = rootView.findViewById(R.id.binarize_threshold_seekbar);
		sb.setMax(100);
		sb.setProgress((int)(mBinarizeThreshold * 100.0f));	// [0.0f, +1.0f] => [0, 100]
		sb.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
		updateBinarizeThreshold(mBinarizeThreshold);
	}

	private void releasePreprocess(final View rootView) {
		mExposureLabel = null;
		mSaturationLabel = null;
		mBrightnessLabel = null;
		mBinarizeThresholdLabel = null;
	}

	private int exposureToProgress(final float exposure) {
		return (int)(Math.signum(exposure) * (Math.sqrt(Math.abs(exposure * 3000000)))) + 3000;
	}

	private float progressToExposure(final int progress) {
		final int p = progress - 3000;
		return Math.signum(p) * (p * p / 3000000.0f);
	}

	private void updateExposure(final float exposure) {
		if (mExposureLabel != null) {
			mExposureLabel.setText(String.format(mExposureFormat, exposure));
		}
	}

	private void updateSaturation(final float saturation) {
		if (mSaturationLabel != null) {
			mSaturationLabel.setText(String.format(mSaturationFormat, saturation));
		}
	}

	private void updateBrightness(final float brightness) {
		if (mBrightnessLabel != null) {
			mBrightnessLabel.setText(String.format(mBrightnessFormat, brightness));
		}
	}

	private void updateBinarizeThreshold(final float threshold) {
		if (mBinarizeThresholdLabel != null) {
			mBinarizeThresholdLabel.setText(String.format(mBinarizeThresholdFormat, threshold));
		}
	}

//--------------------------------------------------------------------------------
	private String mExtractRangeHFormat;
	private String mExtractRangeSFormat;
	private String mExtractRangeVFormat;
	private TextView mExtractRangeHLabel;
	private TextView mExtractRangeSLabel;
	private TextView mExtractRangeVLabel;
	private SeekBar mExtractRangeHSeekbar;
	private SeekBar mExtractRangeSSeekbar;
	private SeekBar mExtractRangeVSeekbar;
	/** OpenGL|ESで色抽出を行うかどうか  */
	protected boolean mEnableGLESExtraction = false;
	// 抽出色
	protected float mExtractH;	// [0.0f, 1.0f] => [0, 180]
	protected float mExtractS;	// [0.0f, 1.0f] => [0, 255]
	protected float mExtractV;	// [0.0f, 1.0f] => [0, 255]
	// 抽出色範囲
	protected float mExtractRangeH;
	protected float mExtractRangeS;
	protected float mExtractRangeV;

	private void initColorExtraction(final View rootView) {
		Switch sw;
		Button btn;
		mExtractRangeHFormat = getString(R.string.trace_config_extract_range_h);
		mExtractRangeSFormat = getString(R.string.trace_config_extract_range_s);
		mExtractRangeVFormat = getString(R.string.trace_config_extract_range_v);

		// OpenGL|ESで色抽出を使うかどうか
		mEnableGLESExtraction = mPref.getBoolean(KEY_ENABLE_EXTRACTION, DEFAULT_ENABLE_EXTRACTION);
		sw = rootView.findViewById(R.id.use_extract_sw);
		sw.setChecked(mEnableGLESExtraction);
		sw.setOnCheckedChangeListener(mOnCheckedChangeListener);
		// 抽出色取得
		btn = rootView.findViewById(R.id.update_extraction_color_btn);
		btn.setOnClickListener(mOnClickListener);
		// 抽出色リセット
		btn = rootView.findViewById(R.id.reset_extraction_color_btn);
		btn.setOnClickListener(mOnClickListener);
		// 抽出色
		mExtractH = mPref.getFloat(KEY_EXTRACT_H, DEFAULT_EXTRACT_H);
		mExtractRangeH = mPref.getFloat(KEY_EXTRACT_RANGE_H, DEFAULT_EXTRACT_RANGE_H);
		mExtractS = mPref.getFloat(KEY_EXTRACT_S, DEFAULT_EXTRACT_S);
		mExtractRangeS = mPref.getFloat(KEY_EXTRACT_RANGE_S, DEFAULT_EXTRACT_RANGE_S);
		mExtractV = mPref.getFloat(KEY_EXTRACT_V, DEFAULT_EXTRACT_V);
		mExtractRangeV = mPref.getFloat(KEY_EXTRACT_RANGE_V, DEFAULT_EXTRACT_RANGE_V);
		mExtractRangeHLabel = rootView.findViewById(R.id.extract_range_h_textview);
		mExtractRangeHSeekbar = rootView.findViewById(R.id.extract_range_h_seekbar);
		mExtractRangeHSeekbar.setMax(100);
		mExtractRangeHSeekbar.setProgress((int)(mExtractRangeH * 100)); 	   // [0.0f, 1.0f] => [0.0f, 100f]
		mExtractRangeHSeekbar.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
		updateExtractRangeH(mExtractRangeH);
		mExtractRangeSLabel = rootView.findViewById(R.id.extract_range_s_textview);
		mExtractRangeSSeekbar = rootView.findViewById(R.id.extract_range_s_seekbar);
		mExtractRangeSSeekbar.setMax(100);
		mExtractRangeSSeekbar.setProgress((int)(mExtractRangeS * 100)); 	   // [0.0f, 1.0f] => [0.0f, 100f]
		mExtractRangeSSeekbar.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
		updateExtractRangeS(mExtractRangeS);
		mExtractRangeVLabel = rootView.findViewById(R.id.extract_range_v_textview);
		mExtractRangeVSeekbar = rootView.findViewById(R.id.extract_range_v_seekbar);
		mExtractRangeVSeekbar.setMax(100);
		mExtractRangeVSeekbar.setProgress((int)(mExtractRangeV * 100)); 	   // [0.0f, 1.0f] => [0.0f, 100f]
		mExtractRangeVSeekbar.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
		updateExtractRangeV(mExtractRangeV);
	}

	private void releaseColorExtraction(final View rootView) {
		mExtractRangeHLabel = null;
		mExtractRangeSLabel = null;
		mExtractRangeVLabel = null;
		mExtractRangeHSeekbar = null;
		mExtractRangeSSeekbar = null;
		mExtractRangeVSeekbar = null;
	}

	/**
	 * 「抽出色更新」,「抽出色選択」または「抽出色リセット」で抽出色が変更された時の処理
	 * この時は既にImageProcessor側は更新されている
	 * @param limit_hsv
	 */
	protected void extractColorChanged(final int[] limit_hsv) {
		final float dh = Math.abs(limit_hsv[0] - limit_hsv[1]) / 180.0f;
		final float h = (limit_hsv[0] + limit_hsv[0]) / 2.0f / 180.0f;
		final float ds = Math.abs(limit_hsv[2] - limit_hsv[3]) / 255.0f;
		final float s = (limit_hsv[2] + limit_hsv[3]) / 2.0f / 255.0f;
		final float dv = Math.abs(limit_hsv[4] - limit_hsv[5]) / 255.0f;
		final float v = (limit_hsv[4] + limit_hsv[5]) / 2.0f / 255.0f;
		synchronized (mParamSync) {
			mExtractH = h;
			mExtractRangeH = dh;
			mExtractS = s;
			mExtractRangeS = ds;
			mExtractV = v;
			mExtractRangeV = dv;
		}
		// プレファレンスに保存する
		mPref.edit().putFloat(KEY_EXTRACT_H, h)
			.putFloat(KEY_EXTRACT_RANGE_H, dh)
			.putFloat(KEY_EXTRACT_S, s)
			.putFloat(KEY_EXTRACT_RANGE_S, ds)
			.putFloat(KEY_EXTRACT_V, v)
			.putFloat(KEY_EXTRACT_RANGE_V, dv).apply();
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				// ラベル・Seekbarの表示更新
				updateExtractRangeH(dh);
				updateExtractRangeS(ds);
				updateExtractRangeV(dv);
			}
		});
	}

	/** 抽出色が変更された時ImageProcessorへ適用するためのメソッド */
	protected void applyExtract(final float h, final float s, final float v) {
		if (mImageProcessor != null) {
			final float h_min = ImageProcessor.sat(h - mExtractRangeH / 2.0f, 0.0f, 1.0f);
			final float h_max = ImageProcessor.sat(h + mExtractRangeH / 2.0f, 0.0f, 1.0f);
			final float s_min = ImageProcessor.sat(s - mExtractRangeS / 2.0f, 0.0f, 1.0f);
			final float s_max = ImageProcessor.sat(s + mExtractRangeS / 2.0f, 0.0f, 1.0f);
			final float v_min = ImageProcessor.sat(v - mExtractRangeV / 2.0f, 0.0f, 1.0f);
			final float v_max = ImageProcessor.sat(v + mExtractRangeV / 2.0f, 0.0f, 1.0f);
			mImageProcessor.setExtractionColor(
				(int)(h_min * 180.0f),
				(int)(h_max * 180.0f),
				(int)(s_min * 255.0f),
				(int)(s_max * 255.0f),
				(int)(v_min * 255.0f),
				(int)(v_max * 255.0f)
			);
		}
	}

	/** 抽出色範囲が変更された時にImageProcessorへ適用するためのメソッド */
	protected void applyExtractRange(final float h_range, final float s_range, final float v_range) {
		if (mImageProcessor != null) {
			final float h_min = ImageProcessor.sat(mExtractH - h_range / 2.0f, 0.0f, 1.0f);
			final float h_max = ImageProcessor.sat(mExtractH + h_range / 2.0f, 0.0f, 1.0f);
			final float s_min = ImageProcessor.sat(mExtractS - s_range / 2.0f, 0.0f, 1.0f);
			final float s_max = ImageProcessor.sat(mExtractS + s_range / 2.0f, 0.0f, 1.0f);
			final float v_min = ImageProcessor.sat(mExtractV - v_range / 2.0f, 0.0f, 1.0f);
			final float v_max = ImageProcessor.sat(mExtractV + v_range / 2.0f, 0.0f, 1.0f);
			mImageProcessor.setExtractionColor(
				(int)(h_min * 180.0f),
				(int)(h_max * 180.0f),
				(int)(s_min * 255.0f),
				(int)(s_max * 255.0f),
				(int)(v_min * 255.0f),
				(int)(v_max * 255.0f)
			);
		}
	}

	/** 抽出色範囲(H/色相のラベル,Seekbar更新) */
	private void updateExtractRangeH(final float range) {
		if (mExtractRangeHLabel != null) {
			mExtractRangeHLabel.setText(String.format(mExtractRangeHFormat, range));
		}
		if (mExtractRangeHSeekbar != null) {
			mExtractRangeHSeekbar.setProgress((int)(range * 100.0f));
		}
	}

	/** 抽出色範囲(S/彩度のラベル,Seekbar更新) */
	private void updateExtractRangeS(final float range) {
		if (mExtractRangeSLabel != null) {
			mExtractRangeSLabel.setText(String.format(mExtractRangeSFormat, range));
		}
		if (mExtractRangeSSeekbar != null) {
			mExtractRangeSSeekbar.setProgress((int)(range * 100.0f));
		}
	}

	/** 抽出色範囲(V/明度のラベル,Seekbar更新) */
	private void updateExtractRangeV(final float range) {
		if (mExtractRangeVLabel != null) {
			mExtractRangeVLabel.setText(String.format(mExtractRangeVFormat, range));
		}
		if (mExtractRangeVSeekbar != null) {
			mExtractRangeVSeekbar.setProgress((int)(range * 100.0f));
		}
	}

//--------------------------------------------------------------------------------
	private String mAreaLimitMinFormat;
	private String mAspectLimitMinFormat;
	private String mAreaErrLimit1Format;
	private String mAreaErrLimit2Format;
	private TextView mAreaLimitMinLabel;
	private TextView mAspectLimitMinLabel;
	private TextView mAreaErrLimit1Label;
	private TextView mAreaErrLimit2Label;
	/** 輪郭検出時の最小面積 */
	protected float mAreaLimitMin = DEFAULT_AREA_LIMIT_MIN;
	protected static final float AREA_LIMIT_MAX = 120000.0f;
	// ラインe検出時の最小アスペクト比
	protected float mAspectLimitMin = 3.0f;
	// 輪郭検出時の面積誤差1, 2
	protected float mAreaErrLimit1 = 1.25f;
	protected float mAreaErrLimit2 = 1.3f;

	private void initDetect(final View rootView) {
		Button btn;
		SeekBar sb;

		mAreaLimitMinFormat = getString(R.string.trace_config_detect_area_limit_min);
		mAspectLimitMinFormat = getString(R.string.trace_config_detect_aspect_limit_min);
		mAreaErrLimit1Format = getString(R.string.trace_config_detect_area_err_limit1);
		mAreaErrLimit2Format = getString(R.string.trace_config_detect_area_err_limit2);

		// 輪郭検出時の最小面積
		mAreaLimitMin = mPref.getFloat(KEY_AREA_LIMIT_MIN, DEFAULT_AREA_LIMIT_MIN);
		mAreaLimitMinLabel = rootView.findViewById(R.id.area_limit_min_textview);
		sb =rootView.findViewById(R.id.area_limit_min_seekbar);
		sb.setMax(9500);
		sb.setProgress(areaLimitMinToProgress(mAreaLimitMin - 500)); 	   // [0,10000] => [0, 10000]
		sb.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
		updateAreaLimitMin(mAreaLimitMin);
		// ライン検出時の面積誤差1
		mAreaErrLimit1 = mPref.getFloat(KEY_AREA_ERR_LIMIT1, DEFAULT_AREA_ERR_LIMIT1);
		mAreaErrLimit1Label = rootView.findViewById(R.id.area_err_limit1_textview);
		sb = rootView.findViewById(R.id.area_err_limit1_seekbar);
		sb.setMax(100);
		sb.setProgress((int)((mAreaErrLimit1 - 1.0f) * 100.0f)); 	   // [1,2] => [0, 100]
		sb.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
		updateAreaErrLimit1(mAreaErrLimit1);
		// ライン検出時の面積誤差2
		mAreaErrLimit2 = mPref.getFloat(KEY_AREA_ERR_LIMIT2, DEFAULT_AREA_ERR_LIMIT2);
		mAreaErrLimit2Label = rootView.findViewById(R.id.area_err_limit2_textview);
		sb = rootView.findViewById(R.id.area_err_limit2_seekbar);
		sb.setMax(100);
		sb.setProgress((int)((mAreaErrLimit2 - 1.0f) * 100.0f)); 	   // [1,2] => [0, 100]
		sb.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
		updateAreaErrLimit2(mAreaErrLimit2);
		// ライン検出時の最小アスペクト比
		mAspectLimitMin = mPref.getFloat(KEY_ASPECT_LIMIT_MIN, DEFAULT_ASPECT_LIMIT_MIN);
		mAspectLimitMinLabel = rootView.findViewById(R.id.aspect_limit_min_textview);
		sb =rootView.findViewById(R.id.aspect_limit_min_seekbar);
		sb.setMax(190);
		sb.setProgress((int)((mAspectLimitMin - 1.0f) * 10)); 	   // [1,20] => [0, 190]
		sb.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
		updateAspectLimitMin(mAspectLimitMin);
	}

	private void releaseDetect(final View rootView) {
		mAreaLimitMinLabel = null;
		mAspectLimitMinLabel = null;
		mAreaErrLimit1Label = null;
		mAreaErrLimit2Label = null;
	}

	private int areaLimitMinToProgress(final float area_limit_min) {
		return (int)(area_limit_min);
	}

	private float progressToAreaLimitMin(final int progress) {
		return progress + 500;
	}

	private void updateAreaLimitMin(final float area_limit_min) {
		if (mAreaLimitMinLabel != null) {
			mAreaLimitMinLabel.setText(String.format(mAreaLimitMinFormat, area_limit_min));
		}
	}

	private void updateAspectLimitMin(final float aspect) {
		if (mAspectLimitMinLabel != null) {
			mAspectLimitMinLabel.setText(String.format(mAspectLimitMinFormat, aspect));
		}
	}

	private void updateAreaErrLimit1(final float limit) {
		if (mAreaErrLimit1Label != null) {
			mAreaErrLimit1Label.setText(String.format(mAreaErrLimit1Format, limit));
		}
	}

	private void updateAreaErrLimit2(final float limit) {
		if (mAreaErrLimit2Label != null) {
			mAreaErrLimit2Label.setText(String.format(mAreaErrLimit2Format, limit));
		}
	}
//--------------------------------------------------------------------------------
	private TextView mTraceDirectionalReverseBiasLabel;
	private TextView mTraceMovingAveTapLabel;
	private TextView mTraceDecayRateLabel;
	private TextView mTraceSensitivityLabel;
	private String mTraceDirectionalReverseBiasFormat;
	private String mTraceMovingAveTapFormat;
	private String mTraceDecayRateFormat;
	private String mTraceSensitivityFormat;
	protected float mTraceDirectionalReverseBias = 0.3f;
	protected int mTraceMovingAveTap = DEFAULT_TRACE_MOVING_AVE_TAP;
	protected float mTraceDecayRate = DEFAULT_TRACE_DECAY_RATE;
	protected float mTraceSensitivity = DEFAULT_TRACE_SENSITIVITY;

	private void initAutoTrace(final View rootView) {
		SeekBar sb;
		//
		mTraceDirectionalReverseBiasFormat = getString(R.string.trace_config_trace_reverse_bias);
		mTraceMovingAveTapFormat = getString(R.string.trace_config_moving_ave_tap);
		mTraceDecayRateFormat = getString(R.string.trace_config_decay_rate);
		mTraceSensitivityFormat = getString(R.string.trace_config_trace_sensitivity);
		// 移動方向逆バイアス
		mTraceDirectionalReverseBias = mPref.getFloat(KEY_TRACE_DIR_REVERSE_BIAS, DEFAULT_TRACE_DIR_REVERSE_BIAS);
		mTraceDirectionalReverseBiasLabel = rootView.findViewById(R.id.trace_flight_reverse_bias_textview);
		sb = rootView.findViewById(R.id.trace_flight_reverse_bias_seekbar);
		sb.setMax(200);
		sb.setProgress((int)(mTraceDirectionalReverseBias * 100));	// [0.0f, 2.0f] => [0, 200]
		sb.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
		updateTraceDirectionalReverseBias(mTraceDirectionalReverseBias);
		// 移動平均タップ数
		mTraceMovingAveTap = mPref.getInt(KEY_TRACE_MOVING_AVE_TAP, DEFAULT_TRACE_MOVING_AVE_TAP);
		mTraceMovingAveTapLabel = rootView.findViewById(R.id.trace_flight_moving_ave_tap_textview);
		sb = rootView.findViewById(R.id.trace_flight_moving_ave_tap_seekbar);
		sb.setMax(19);
		sb.setProgress(mTraceMovingAveTap - 1);	// [1, 20] => [0, 19]
		sb.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
		updateTraceMovingAveTap(mTraceMovingAveTap);
		// 減衰率
		mTraceDecayRate = mPref.getFloat(KEY_TRACE_DECAY_RATE, DEFAULT_TRACE_DECAY_RATE);
		mTraceDecayRateLabel = rootView.findViewById(R.id.trace_flight_decay_rate_textview);
		sb = rootView.findViewById(R.id.trace_flight_decay_rate_seekbar);
		sb.setMax(1000);
		sb.setProgress((int)(mTraceDecayRate * 1000.0f));	// [0.000f, 1.000f] => [0, 1000]
		sb.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
		updateTraceDecayRate(mTraceDecayRate);
		// 制御感度
		mTraceSensitivity = mPref.getFloat(KEY_TRACE_SENSITIVITY, DEFAULT_TRACE_SENSITIVITY);
		mTraceSensitivityLabel = rootView.findViewById(R.id.trace_flight_sensitivity_textview);
		sb = rootView.findViewById(R.id.trace_flight_sensitivity_seekbar);
		sb.setMax(100);
		sb.setProgress((int)(mTraceSensitivity));	// [0f, 100f] => [0, 100]
		sb.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
		updateTraceSensitivity(mTraceSensitivity);
	}

	private void releaseAutoTrace(final View rootView) {
		mTraceAttitudeYawLabel = null;
		mTraceSpeedLabel = null;
		mTraceDirectionalReverseBiasLabel = null;
	}

	private void updateTraceDirectionalReverseBias(final float bias) {
		if (mTraceDirectionalReverseBiasLabel != null) {
			mTraceDirectionalReverseBiasLabel.setText(String.format(mTraceDirectionalReverseBiasFormat, bias));
		}
	}

	private void updateTraceMovingAveTap(final int notch) {
		if (mTraceMovingAveTapLabel != null) {
			mTraceMovingAveTapLabel.setText(String.format(mTraceMovingAveTapFormat, notch));
		}
	}

	private void updateTraceDecayRate(final float decay_rate) {
		if (mTraceDecayRateLabel != null) {
			mTraceDecayRateLabel.setText(String.format(mTraceDecayRateFormat, decay_rate));
		}
	}

	private void updateTraceSensitivity(final float sensitivity) {
		if (mTraceSensitivityLabel != null) {
			mTraceSensitivityLabel.setText(String.format(mTraceSensitivityFormat, sensitivity));
		}
	}

	//--------------------------------------------------------------------------------
	private TextView mTraceAttitudeYawLabel;
	private TextView mTraceSpeedLabel;
	private TextView mTraceAltitudeLabel;
	private String mTraceAttitudeYawFormat;
	private String mTraceSpeedFormat;
	private String mTraceAltitudeFormat;
	protected float mTraceAttitudeYaw = 0.0f;
	protected float mTraceSpeed = 100.0f;
	protected boolean mTraceAltitudeEnabled = true;
	protected float mTraceAltitude = 0.6f;

	private void initAutoTrace2(final View rootView) {
		SeekBar sb;
		Switch sw;
		//
		mTraceAttitudeYawFormat = getString(R.string.trace_config_trace_attitude_yaw);
		mTraceSpeedFormat = getString(R.string.trace_config_trace_speed);
		mTraceAltitudeFormat = getString(R.string.trace_config_trace_altitude);
		// 飛行姿勢(yaw)
		mTraceAttitudeYaw = mPref.getFloat(KEY_TRACE_ATTITUDE_YAW, DEFAULT_TRACE_ATTITUDE_YAW);
		mTraceAttitudeYawLabel = rootView.findViewById(R.id.trace_flight_attitude_yaw_textview);
		sb = rootView.findViewById(R.id.trace_flight_attitude_yaw_seekbar);
		sb.setMax(180);
		sb.setProgress((int)(mTraceAttitudeYaw + 90));	// [-90,+90] => [0, 180]
		sb.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
		updateTraceAttitudeYaw(mTraceAttitudeYaw);
		// 飛行速度
		mTraceSpeed = mPref.getFloat(KEY_TRACE_SPEED, DEFAULT_TRACE_SPEED);
		mTraceSpeedLabel = rootView.findViewById(R.id.trace_flight_speed_textview);
		sb = rootView.findViewById(R.id.trace_flight_speed_seekbar);
		sb.setMax(200);
		sb.setProgress((int)(mTraceSpeed + 100));	// [-100,+100] => [0, 200]
		sb.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
		updateTraceSpeed(mTraceSpeed);
		// 高度制御
		mTraceAltitudeEnabled = mPref.getBoolean(KEY_TRACE_ALTITUDE_ENABLED, DEFAULT_TRACE_ALTITUDE_ENABLED);
		sw = rootView.findViewById(R.id.trace_flight_altitude_enable_switch);
		sw.setChecked(mTraceAltitudeEnabled);
		sw.setOnCheckedChangeListener(mOnCheckedChangeListener);
		// 飛行高度
		mTraceAltitude = Math.min(mPref.getFloat(KEY_TRACE_ALTITUDE, DEFAULT_TRACE_ALTITUDE), mFlightController.getMaxAltitude().current());
		mTraceAltitudeLabel = rootView.findViewById(R.id.trace_flight_altitude_textview);
		sb = rootView.findViewById(R.id.trace_flight_altitude_seekbar);
		sb.setMax(45);
		sb.setProgress((int)((mTraceAltitude - 0.5f) * 10.0f));	// [0.5,+5.0] => [0, 45]
		sb.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
		updateTraceAltitude(mTraceAltitude);
	}

	private void releaseAutoTrace2(final View rootView) {
		mTraceAttitudeYawLabel = null;
		mTraceSpeedLabel = null;
		mTraceAltitudeLabel = null;
	}

	private void updateTraceAttitudeYaw(final float attitude_yaw) {
		if (mTraceAttitudeYawLabel != null) {
			mTraceAttitudeYawLabel.setText(String.format(mTraceAttitudeYawFormat, attitude_yaw));
		}
	}

	private void updateTraceSpeed(final float speed) {
		if (mTraceSpeedLabel != null) {
			mTraceSpeedLabel.setText(String.format(mTraceSpeedFormat, speed));
		}
	}

	private void updateTraceAltitude(final float altitude) {
		if (mTraceAltitudeLabel != null) {
			mTraceAltitudeLabel.setText(String.format(mTraceAltitudeFormat, altitude));
		}
	}

//--------------------------------------------------------------------------------
	private String mMaxAltitudeFormat;
	private String mMaxTiltFormat;
	private String mMaxVerticalSpeedFormat;
	private String mMaxRotationSpeedFormat;
	private TextView mMaxAltitudeLabel;
	private TextView mMaxTiltLabel;
	private TextView mMaxVerticalSpeedLabel;
	private TextView mMaxRotationSpeedLabel;
	private AttributeFloat mMaxAltitude;
	private AttributeFloat mMaxTilt;
	private AttributeFloat mMaxVerticalSpeed;
	private AttributeFloat mMaxRotationSpeed;

	/**
	 * 飛行設定画面の準備
	 * @param root
	 */
	private void initConfigFlight(final View root) {
		mMaxAltitudeFormat = getString(R.string.config_max_altitude);
		mMaxTiltFormat = getString(R.string.config_max_tilt);
		mMaxVerticalSpeedFormat = getString(R.string.config_max_vertical_speed);
		mMaxRotationSpeedFormat = getString(R.string.config_max_rotating_speed);
		// 最大高度設定
		mMaxAltitudeLabel = root.findViewById(R.id.max_altitude_textview);
		SeekBar seekbar = root.findViewById(R.id.max_altitude_seekbar);
		seekbar.setOnSeekBarChangeListener(null);
		seekbar.setMax(1000);
		mMaxAltitude = mFlightController.getMaxAltitude();
		try {
			seekbar.setProgress((int) ((mMaxAltitude.current() - mMaxAltitude.min()) / (mMaxAltitude.max() - mMaxAltitude.min()) * 1000));
		} catch (final Exception e) {
			seekbar.setProgress(0);
		}
		seekbar.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
		updateMaxAltitude(mMaxAltitude.current());
		// 最大傾斜設定
		// bebopは5-30度。最大時速約50km/hrからすると13.9m/s/30度≒0.46[m/s/度]
		mMaxTiltLabel = root.findViewById(R.id.max_tilt_textview);
		seekbar = root.findViewById(R.id.max_tilt_seekbar);
		seekbar.setOnSeekBarChangeListener(null);
		seekbar.setMax(1000);
		mMaxTilt = mFlightController.getMaxTilt();
		try {
			seekbar.setProgress((int) ((mMaxTilt.current() - mMaxTilt.min()) / (mMaxTilt.max() - mMaxTilt.min()) * 1000));
		} catch (final Exception e) {
			seekbar.setProgress(0);
		}
		seekbar.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
		updateMaxTilt(mMaxTilt.current());
		// 最大上昇/降下速度設定, Bebop2は6m/s
		mMaxVerticalSpeedLabel = root.findViewById(R.id.max_vertical_speed_textview);
		seekbar = root.findViewById(R.id.max_vertical_speed_seekbar);
		seekbar.setOnSeekBarChangeListener(null);
		seekbar.setMax(1000);
		mMaxVerticalSpeed = mFlightController.getMaxVerticalSpeed();
		try {
			seekbar.setProgress((int) ((mMaxVerticalSpeed.current() - mMaxVerticalSpeed.min()) / (mMaxVerticalSpeed.max() - mMaxVerticalSpeed.min()) * 1000));
		} catch (final Exception e) {
			seekbar.setProgress(0);
		}
		seekbar.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
		updateMaxVerticalSpeed(mMaxVerticalSpeed.current());
		// 最大回転速度
		mMaxRotationSpeedLabel = root.findViewById(R.id.max_rotation_speed_textview);
		seekbar = root.findViewById(R.id.max_rotation_speed_seekbar);
		seekbar.setOnSeekBarChangeListener(null);
		seekbar.setMax(1000);
		mMaxRotationSpeed = mFlightController.getMaxRotationSpeed();
		try {
			seekbar.setProgress((int) ((mMaxRotationSpeed.current() - mMaxRotationSpeed.min()) / (mMaxRotationSpeed.max() - mMaxRotationSpeed.min()) * 1000));
		} catch (final Exception e) {
			seekbar.setProgress(0);
		}
		seekbar.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
		updateMaxRotationSpeed(mMaxRotationSpeed.current());
	}

	private void releaseConfigFlight(final View rootView) {
		mMaxAltitudeLabel = null;
		mMaxTiltLabel = null;
		mMaxVerticalSpeedLabel = null;
		mMaxRotationSpeedLabel = null;
	}

	/**
	 * 最大高度設定値表示を更新
	 * @param max_altitude
	 */
	private void updateMaxAltitude(final float max_altitude) {
		if (mMaxAltitudeLabel != null) {
			mMaxAltitudeLabel.setText(String.format(mMaxAltitudeFormat, max_altitude));
		}
	}

	/**
	 * 最大傾斜設定表示を更新
	 * @param max_tilt
	 */
	private void updateMaxTilt(final float max_tilt) {
		if (mMaxTiltLabel != null) {
			mMaxTiltLabel.setText(String.format(mMaxTiltFormat, max_tilt));
		}
	}

	/**
	 * 最大上昇/降下速度設定表示を更新
	 * @param max_vertical_speed
	 */
	private void updateMaxVerticalSpeed(final float max_vertical_speed) {
		if (mMaxVerticalSpeedLabel != null) {
			mMaxVerticalSpeedLabel.setText(String.format(mMaxVerticalSpeedFormat, max_vertical_speed));
		}
	}

	/**
	 * 最大回転速度設定表示を更新
	 * @param max_rotation_speed
	 */
	private void updateMaxRotationSpeed(final float max_rotation_speed) {
		if (mMaxRotationSpeedLabel != null) {
			mMaxRotationSpeedLabel.setText(String.format(mMaxRotationSpeedFormat, max_rotation_speed));
		}
	}

//----------------------------------------------------------------------
	private TextView mAutopilotScaleXLabel;
	private TextView mAutopilotScaleYLabel;
	private TextView mAutopilotScaleZLabel;
	private TextView mAutopilotScaleRLabel;
	private TextView mAutopilotMaxControlValueLabel;
	private String mAutopilotScaleXFormat;
	private String mAutopilotScaleYFormat;
	private String mAutopilotScaleZFormat;
	private String mAutopilotScaleRFormat;
	private String mAutopilotMaxControlValueFormat;
	/**
	 * 自動操縦設定画面の準備
	 * @param root
	 */
	private void initConfigAutopilot(final View root) {
		mAutopilotScaleXFormat = getString(R.string.config_scale_x);
		mAutopilotScaleYFormat = getString(R.string.config_scale_y);
		mAutopilotScaleZFormat = getString(R.string.config_scale_z);
		mAutopilotScaleRFormat = getString(R.string.config_scale_r);
		mAutopilotMaxControlValueFormat = getString(R.string.config_control_max);
		// 最大制御値設定
		mAutopilotMaxControlValueLabel = root.findViewById(R.id.max_control_value_textview);
		SeekBar seekbar = root.findViewById(R.id.max_control_value_seekbar);
		seekbar.setOnSeekBarChangeListener(null);
		seekbar.setMax(1000);
		mMaxControlValue = mPref.getFloat(APP_CONFIG_KEY_MAX_CONTROL_VALUE, APP_CONFIG_DEFAULT_MAX_CONTROL_VALUE);
		try {
			seekbar.setProgress((int) (mMaxControlValue + APP_SCALE_OFFSET));
		} catch (final Exception e) {
			seekbar.setProgress(APP_SCALE_OFFSET);
		}
		seekbar.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
		updateAutopilotMaxControlValue(mMaxControlValue);
		// スケールX設定
		mAutopilotScaleXLabel = root.findViewById(R.id.scale_x_textview);
		seekbar = root.findViewById(R.id.scale_seekbar_x);
		seekbar.setOnSeekBarChangeListener(null);
		seekbar.setMax(1000);
		mScaleX = mPref.getFloat(APP_CONFIG_KEY_SCALE_X, APP_CONFIG_DEFAULT_SCALE_X);
		try {
			seekbar.setProgress((int) (mScaleX * APP_SCALE_FACTOR + APP_SCALE_OFFSET));
		} catch (final Exception e) {
			seekbar.setProgress(APP_SCALE_OFFSET);
		}
		seekbar.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
		updateAutopilotScaleX(mScaleX);
		// スケールY設定
		mAutopilotScaleYLabel = root.findViewById(R.id.scale_y_textview);
		seekbar = root.findViewById(R.id.scale_seekbar_y);
		seekbar.setOnSeekBarChangeListener(null);
		seekbar.setMax(1000);
		mScaleY = mPref.getFloat(APP_CONFIG_KEY_SCALE_Y, APP_CONFIG_DEFAULT_SCALE_Y);
		try {
			seekbar.setProgress((int) (mScaleY * APP_SCALE_FACTOR + APP_SCALE_OFFSET));
		} catch (final Exception e) {
			seekbar.setProgress(APP_SCALE_OFFSET);
		}
		seekbar.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
		updateAutopilotScaleY(mScaleY);
		// スケールZ設定
		mAutopilotScaleZLabel = root.findViewById(R.id.scale_z_textview);
		seekbar = root.findViewById(R.id.scale_seekbar_z);
		seekbar.setOnSeekBarChangeListener(null);
		seekbar.setMax(1000);
		mScaleZ = mPref.getFloat(APP_CONFIG_KEY_SCALE_Z, APP_CONFIG_DEFAULT_SCALE_Z);
		try {
			seekbar.setProgress((int) (mScaleZ * APP_SCALE_FACTOR + APP_SCALE_OFFSET));
		} catch (final Exception e) {
			seekbar.setProgress(APP_SCALE_OFFSET);
		}
		seekbar.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
		updateAutopilotScaleZ(mScaleZ);
		// スケールR設定
		mAutopilotScaleRLabel = root.findViewById(R.id.scale_r_textview);
		seekbar = root.findViewById(R.id.scale_seekbar_r);
		seekbar.setOnSeekBarChangeListener(null);
		seekbar.setMax(1000);
		mScaleR = mPref.getFloat(APP_CONFIG_KEY_SCALE_R, APP_CONFIG_DEFAULT_SCALE_R);
		try {
			seekbar.setProgress((int) (mScaleR * APP_SCALE_FACTOR + APP_SCALE_OFFSET));
		} catch (final Exception e) {
			seekbar.setProgress(APP_SCALE_OFFSET);
		}
		seekbar.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
		updateAutopilotScaleR(mScaleR);
	}

	private void releaseConfigAutopilot(final View root) {
		mAutopilotScaleXLabel = null;
		mAutopilotScaleYLabel = null;
		mAutopilotScaleZLabel = null;
		mAutopilotScaleRLabel = null;
		mAutopilotMaxControlValueLabel = null;
	}

	/**
	 * 自動操縦:最大制御設定値表示を更新
	 * @param max_control_value
	 */
	private void updateAutopilotMaxControlValue(final double max_control_value) {
		if (mAutopilotMaxControlValueLabel != null) {
			mAutopilotMaxControlValueLabel.setText(String.format(mAutopilotMaxControlValueFormat, max_control_value));
		}
	}

	/**
	 * 自動操縦:スケールZ設定表示を更新
	 * @param scale_x
	 */
	private void updateAutopilotScaleX(final double scale_x) {
		if (mAutopilotScaleXLabel != null) {
			mAutopilotScaleXLabel.setText(String.format(mAutopilotScaleXFormat, scale_x));
		}
	}

	/**
	 * 自動操縦:スケールY設定表示を更新
	 * @param scale_y
	 */
	private void updateAutopilotScaleY(final double scale_y) {
		if (mAutopilotScaleYLabel != null) {
			mAutopilotScaleYLabel.setText(String.format(mAutopilotScaleYFormat, scale_y));
		}
	}

	/**
	 * 自動操縦:スケールZ設定表示を更新
	 * @param scale_z
	 */
	private void updateAutopilotScaleZ(final double scale_z) {
		if (mAutopilotScaleZLabel != null) {
			mAutopilotScaleZLabel.setText(String.format(mAutopilotScaleZFormat, scale_z));
		}
	}

	/**
	 * 自動操縦:スケールR設定表示を更新
	 * @param scale_r
	 */
	private void updateAutopilotScaleR(final double scale_r) {
		if (mAutopilotScaleRLabel != null) {
			mAutopilotScaleRLabel.setText(String.format(mAutopilotScaleRFormat, scale_r));
		}
	}

//================================================================================
//================================================================================
	private static interface AdapterItemHandler {
		public void initialize(final BaseAutoPilotFragment parent, final View view);
		public void release(final BaseAutoPilotFragment parent, final View view);
	}

	private static final class PagerAdapterConfig {
		public final int title_id;
		public final int layout_id;
		public final AdapterItemHandler handler;

		public PagerAdapterConfig(final int _title_id, final int _layout_id, final AdapterItemHandler _handler) {
			title_id = _title_id;
			layout_id = _layout_id;
			handler = _handler;
		}
	}

	private static PagerAdapterConfig[] PAGER_CONFIG_TRACE;
	static {
		//
		PAGER_CONFIG_TRACE = new PagerAdapterConfig[8];
		PAGER_CONFIG_TRACE[0] = new PagerAdapterConfig(R.string.trace_config_title_camera, R.layout.trace_config_camera, new AdapterItemHandler() {
			@Override
			public void initialize(final BaseAutoPilotFragment parent, final View view) {
				parent.initCamera(view);
			}
			@Override
			public void release(final BaseAutoPilotFragment parent, final View view) {
				parent.releaseCamera(view);
			}
		});
		PAGER_CONFIG_TRACE[1] = new PagerAdapterConfig(R.string.trace_config_title_preprocess, R.layout.trace_config_preprocess, new AdapterItemHandler() {
			@Override
			public void initialize(final BaseAutoPilotFragment parent, final View view) {
				parent.initPreprocess(view);
			}
			@Override
			public void release(final BaseAutoPilotFragment parent, final View view) {
				parent.releasePreprocess(view);
			}
		});
		PAGER_CONFIG_TRACE[2] = new PagerAdapterConfig(R.string.trace_config_title_color_extract, R.layout.trace_config_color_extraction, new AdapterItemHandler() {
			@Override
			public void initialize(final BaseAutoPilotFragment parent, final View view) {
				parent.initColorExtraction(view);
			}
			@Override
			public void release(final BaseAutoPilotFragment parent, final View view) {
				parent.releaseColorExtraction(view);
			}
		});
		PAGER_CONFIG_TRACE[3] = new PagerAdapterConfig(R.string.trace_config_title_detect, R.layout.trace_config_detect, new AdapterItemHandler() {
			@Override
			public void initialize(final BaseAutoPilotFragment parent, final View view) {
				parent.initDetect(view);
			}
			@Override
			public void release(final BaseAutoPilotFragment parent, final View view) {
				parent.releaseDetect(view);
			}
		});
		PAGER_CONFIG_TRACE[4] = new PagerAdapterConfig(R.string.trace_config_title_auto_trace, R.layout.trace_config_auto_trace, new AdapterItemHandler() {
			@Override
			public void initialize(final BaseAutoPilotFragment parent, final View view) {
				parent.initAutoTrace(view);
			}
			@Override
			public void release(final BaseAutoPilotFragment parent, final View view) {
				parent.releaseAutoTrace(view);
			}
		});
		PAGER_CONFIG_TRACE[5] = new PagerAdapterConfig(R.string.trace_config_title_auto_trace2, R.layout.trace_config_auto_trace2, new AdapterItemHandler() {
			@Override
			public void initialize(final BaseAutoPilotFragment parent, final View view) {
				parent.initAutoTrace2(view);
			}
			@Override
			public void release(final BaseAutoPilotFragment parent, final View view) {
				parent.releaseAutoTrace2(view);
			}
		});
		PAGER_CONFIG_TRACE[6] = new PagerAdapterConfig(R.string.config_title_flight, R.layout.trace_config_flight, new AdapterItemHandler() {
			@Override
			public void initialize(final BaseAutoPilotFragment parent, final View view) {
				parent.initConfigFlight(view);
			}
			@Override
			public void release(final BaseAutoPilotFragment parent, final View view) {
				parent.releaseConfigFlight(view);
			}
		});
		PAGER_CONFIG_TRACE[7] = new PagerAdapterConfig(R.string.config_title_autopilot, R.layout.trace_config_autopilot, new AdapterItemHandler() {
			@Override
			public void initialize(final BaseAutoPilotFragment parent, final View view) {
				parent.initConfigAutopilot(view);
			}
			@Override
			public void release(final BaseAutoPilotFragment parent, final View view) {
				parent.releaseConfigAutopilot(view);
			}
		});
	}

	/**
	 * 設定画面の各ページ用のViewを提供するためのPagerAdapterクラス
	 */
	private class ConfigPagerAdapter extends PagerAdapter {
		private final LayoutInflater mInflater;
		private final PagerAdapterConfig[] mConfigs;
		public ConfigPagerAdapter(final LayoutInflater inflater) {
			super();
			mInflater = inflater;
			mConfigs = PAGER_CONFIG_TRACE;
		}

		@NonNull
		@Override
		public synchronized Object instantiateItem(@NonNull final ViewGroup container, final int position) {
			View view = null;
			final int n = mConfigs != null ? mConfigs.length : 0;
			if ((position >= 0) && (position < n)) {
				final PagerAdapterConfig config = mConfigs[position];
				view = mInflater.inflate(config.layout_id, container, false);
				config.handler.initialize(BaseAutoPilotFragment.this, view);
			}
			if (view != null) {
				container.addView(view);
			}
			return view;
		}

		@Override
		public synchronized void destroyItem(@NonNull final ViewGroup container, final int position, @NonNull final Object object) {
			if (object instanceof View) {
				container.removeView((View)object);
			}
		}

		@Override
		public int getCount() {
			return mConfigs != null ? mConfigs.length : 0;
		}

		@Override
		public boolean isViewFromObject(@NonNull final View view, @NonNull final Object object) {
			return view.equals(object);
		}

		@Override
		public CharSequence getPageTitle(final int position) {
			CharSequence result = null;
			final int n = mConfigs != null ? mConfigs.length : 0;
			if ((position >= 0) && (position < n)) {
				result = getString(mConfigs[position].title_id);
			}
			return result;
		}
	}

	private final StringBuilder sb = new StringBuilder();
	private final Runnable mCPUMonitorTask = new Runnable() {
		@Override
		public void run() {
			if (cpuMonitor.sampleCpuUtilization()) {
				sb.setLength(0);
				sb.append("CPU%: ")
					.append(cpuMonitor.getCpuCurrent()).append("/")
					.append(cpuMonitor.getCpuAvg3()).append("/")
					.append(cpuMonitor.getCpuAvgAll());
			}
			mCpuLoadTv.setText(sb.toString());
			runOnUiThread(this, 1000);
		}
	};

	private final Runnable mFpsTask = new Runnable() {
		@Override
		public void run() {
			if (mVideoStream != null) {
				mFpsSrcTv.setText(String.format(Locale.US, "%5.1f", mVideoStream.updateFps().getFps()));
			} else {
				mFpsSrcTv.setText(null);
			}
			if (mImageProcessor != null) {
				mImageProcessor.updateFps();
				mFpsResultTv.setText(String.format(Locale.US, "%5.1f", mImageProcessor.getFps()));
			} else {
				mFpsResultTv.setText(null);
			}
			runOnUiThread(this, 1000);
		}
	};
}
