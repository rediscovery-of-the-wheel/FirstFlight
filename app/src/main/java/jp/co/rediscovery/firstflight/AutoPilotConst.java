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

public class AutoPilotConst {
// ライントレース
	// デバイス側のカメラ設定
	public static final String KEY_CAMERA_WHITE_BLANCE = "KEY_CAMERA_WHITE_BLANCE";
	public static final int DEFAULT_CAMERA_WHITE_BLANCE = 2;	// 晴天
	public static final String KEY_CAMERA_EXPOSURE = "KEY_CAMERA_EXPOSURE";
	public static final float DEFAULT_CAMERA_EXPOSURE = 0.0f;
	public static final String KEY_CAMERA_SATURATION = "KEY_CAMERA_SATURATION";
	public static final float DEFAULT_CAMERA_SATURATION = 0.0f;
	public static final String KEY_CAMERA_PAN = "KEY_CAMERA_PAN";	// 左右
	public static final int DEFAULT_CAMERA_PAN =  0;
	public static final String KEY_CAMERA_TILT = "KEY_CAMERA_TILT";	// 上下
	public static final int DEFAULT_CAMERA_TILT = -100;

	public static final String KEY_PREF_NAME_AUTOPILOT = "KEY_PREF_NAME_AUTOPILOT";
	public static final String KEY_AUTOPILOT_MODE = "KEY_AUTOPILOT_MODE";
	public static final String KEY_EXPOSURE = "KEY_EXPOSURE";
	public static final float DEFAULT_EXPOSURE = 0.0f;
	public static final String KEY_SATURATION = "KEY_SATURATION";
	public static final float DEFAULT_SATURATION = 0.0f;
	public static final String KEY_BRIGHTNESS = "KEY_BRIGHTNESS";
	public static final float DEFAULT_BRIGHTNESS = 0.0f;
	public static final String KEY_BINARIZE_THRESHOLD = "KEY_BINARIZE_THRESHOLD";
	public static final float DEFAULT_BINARIZE_THRESHOLD = 0.25f;

	public static final String KEY_AREA_LIMIT_MIN = "KEY_AREA_LIMIT_MIN";
	public static final float DEFAULT_AREA_LIMIT_MIN = 500.0f;
	public static final String KEY_ASPECT_LIMIT_MIN = "KEY_ASPECT_LIMIT_MIN";
	public static final float DEFAULT_ASPECT_LIMIT_MIN = 2.0f;
	public static final String KEY_AREA_ERR_LIMIT1 = "KEY_AREA_ERR_LIMIT1";
	public static final float DEFAULT_AREA_ERR_LIMIT1 = 1.5f;
	public static final String KEY_AREA_ERR_LIMIT2 = "KEY_AREA_ERR_LIMIT2";
	public static final float DEFAULT_AREA_ERR_LIMIT2 = 1.65f;
	// 色抽出設定
	public static final String KEY_ENABLE_EXTRACTION = "KEY_ENABLE_EXTRACTION";
	public static final boolean DEFAULT_ENABLE_EXTRACTION = true;
//	public static final String KEY_ENABLE_NATIVE_EXTRACTION = "KEY_ENABLE_NATIVE_EXTRACTION";
	public static final String KEY_EXTRACT_H = "KEY_ENABLE_EXTRACT_H";
	public static final float DEFAULT_EXTRACT_H = 0.5f;
	public static final String KEY_EXTRACT_S = "KEY_ENABLE_EXTRACT_S";
	public static final float DEFAULT_EXTRACT_S = 0.196f;
	public static final String KEY_EXTRACT_V = "KEY_ENABLE_EXTRACT_V";
	public static final float DEFAULT_EXTRACT_V = 0.7353f;
	public static final String KEY_EXTRACT_RANGE_H = "KEY_ENABLE_EXTRACT_RANGE_H";
	public static final float DEFAULT_EXTRACT_RANGE_H = 0.5f;
	public static final String KEY_EXTRACT_RANGE_S = "KEY_ENABLE_EXTRACT_RANGE_S";
	public static final float DEFAULT_EXTRACT_RANGE_S = 0.098f;
	public static final String KEY_EXTRACT_RANGE_V = "KEY_ENABLE_EXTRACT_RANGE_V";
	public static final float DEFAULT_EXTRACT_RANGE_V = 0.265f;

	// 自動トレース設定
	public static final String KEY_TRACE_ATTITUDE_YAW = "KEY_TRACE_FLIGHT_ATTITUDE_YAW";
	public static final float DEFAULT_TRACE_ATTITUDE_YAW = 0.0f;
	public static final String KEY_TRACE_SPEED = "KEY_TRACE_FLIGHT_SPEED";
	public static final float DEFAULT_TRACE_SPEED = 100.0f;
	public static final String KEY_TRACE_ALTITUDE_ENABLED = "KEY_TRACE_ALTITUDE_ENABLED";
	public static final boolean DEFAULT_TRACE_ALTITUDE_ENABLED = false;
	public static final String KEY_TRACE_ALTITUDE = "KEY_TRACE_ALTITUDE";
	public static final float DEFAULT_TRACE_ALTITUDE = 0.6f;
	public static final String KEY_TRACE_DIR_REVERSE_BIAS = "KEY_TRACE_FLIGHT_DIR_REVERSE_BIAS";
	public static final float DEFAULT_TRACE_DIR_REVERSE_BIAS = 0.3f;
	public static final String KEY_TRACE_MOVING_AVE_TAP = "KEY_TRACE_MOVING_AVE_NOTCH";
	public static final int DEFAULT_TRACE_MOVING_AVE_TAP = 5;
	public static final String KEY_TRACE_DECAY_RATE = "KEY_TRACE_DECAY_RATE";
	public static final float DEFAULT_TRACE_DECAY_RATE = 0.0f;
	public static final String KEY_TRACE_SENSITIVITY = "KEY_TRACE_SENSITIVITY";
	public static final float DEFAULT_TRACE_SENSITIVITY = 50.0f;
}
