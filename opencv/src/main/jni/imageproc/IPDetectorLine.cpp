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

#if 1	// デバッグ情報を出さない時は1
	#ifndef LOG_NDEBUG
		#define	LOG_NDEBUG		// LOGV/LOGD/MARKを出力しない時
	#endif
	#undef USE_LOGALL			// 指定したLOGxだけを出力
#else
//	#define USE_LOGALL
	#define USE_LOGD
	#undef LOG_NDEBUG
	#undef NDEBUG
#endif

#include "utilbase.h"

#include "IPDetectorLine.h"

static double HU_MOMENTS[] = {
	3.673166e-01,1.071715e-01,1.763543e-04,9.209628e-05,1.173179e-08,3.007932e-05,-3.488433e-10
};


// 検出したオブジェクトの優先度の判定
// 第1引数が第2引数よりも小さい(=前にある=優先度が高い)時に真(正)を返す
static bool comp_priority(const DetectRec_t *left, const DetectRec_t *right) {
	// 類似性(小さい方, 曲線だと大きくなってしまう)
	const bool b2 = left->analogous < right->analogous;
	// 近似輪郭と実輪郭の面積比(小さい方, 曲線だと大きくなってしまう)
	const bool b3 = left->area_rate < right->area_rate;
	// アスペクト比の比較(大きい方)
	const bool b4 = left->aspect > right->aspect;
	// 長さの比較(大きい方)
	const bool b5 = left->length > right->length;
	return
		(b5 && b4)					// 長くてアスペクト比が大きい
		|| (b5 && b4 && b3 && b2)	// 長くてアスペクト比が大きくて面積比が小さくて類似性が良い
		|| (b5 && b4 && b3)			// 長くてアスペクト比が大きくて面積比が小さい
		|| (b4 && b3 && b2)			// アスペクト比が大きくて面積比が小さくて類似性が良い
		|| (b4 && b3)				// アスペクト比が大きくて面積比が小さい
		|| (b4 && b2)				// アスペクト比が大きくて類似性が良い
		|| (b5 && b3 && b2)			// 長くて面積比が小さくて類似性が良い
		|| (b5 && b3)				// 長くて面積比が小さい
		|| (b5 && b2)				// 長くて類似性が良い
		|| (b3 && b2)				// 面積比が小さくて類似性良い
		|| (b5)						// 長い
		|| (b4)						// アスペクト比が大きい
		|| (b3)						// 面積比が小さくい
		|| (b2);					// 類似性が良い
}

//********************************************************************************
//********************************************************************************
IPDetectorLine::IPDetectorLine() : IPDetector() {
	ENTER();

	EXIT();
}

IPDetectorLine::~IPDetectorLine() {
	ENTER();

	EXIT();
}

int IPDetectorLine::detect(
	cv::Mat &src,						// 解析画像
	std::vector<DetectRec_t> &contours,	// 近似輪郭
	std::vector<const DetectRec_t *> &possibles,	// ワーク用
	cv::Mat &result_frame,				// 結果書き込み用Mat
	DetectRec_t &result,				// 結果
	const DetectParam_t &param) {		// パラメータ

	ENTER();

	double hu_moments[8];
	possibles.clear();

	// 検出した輪郭の数分ループする
	for (auto iter = contours.begin(); iter != contours.end(); iter++) {
		DetectRec_t *rec = &(*iter);		// 輪郭レコード
		// アスペクト比が正方形に近いものはスキップ
		if ((rec->aspect < param.mMinLineAspect) && (rec->area_rate > 1.2f)) continue;
		if (param.show_detects) {
			cv::polylines(result_frame, rec->contour, true, COLOR_ORANGE, 2);
		}

		// 最小矩形と元輪郭の面積比が大き過ぎる場合スキップ
		if ((rec->area_rate > 1.2f) && (rec->contour.size() > 6)) continue;
		if (param.show_detects) {
			cv::polylines(result_frame, rec->contour, true, COLOR_ACUA, 2);
		}
		// 輪郭のHu momentを計算
		cv::HuMoments(rec->moments, hu_moments);
		// 基準値と比較, メソッド1は時々一致しない, メソッド2,3だとほとんど一致しない, 完全一致なら0が返る
		const float analogous = (float)compHuMoments(HU_MOMENTS, hu_moments, 1);
		// ラインの可能性が高い輪郭を追加
		rec->analogous = analogous;
		possibles.push_back(rec);
		if (param.show_detects) {
			cv::polylines(result_frame, rec->contour, true, COLOR_BLUE, 2);
		}
	}
	// 優先度の最も高いものを選択する
	if (possibles.size() > 0) {
		if (possibles.size() > 1) {
			// 優先度の降順にソートする
			std::sort(possibles.begin(), possibles.end(), comp_priority);
		}
		result = *(*possibles.begin());	// 先頭=優先度が最高
		result.type = TYPE_LINE;
		result.curvature = result.ex = result.ey = 0.0f;
	} else {
		result.type = TYPE_NON;
	}
	possibles.clear();

	RETURN(0, int);
}
