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

#ifndef FLIGHTDEMO_IPDETECTOR_H
#define FLIGHTDEMO_IPDETECTOR_H

#include <stdlib.h>
#include <algorithm>
#include <iomanip>

#include "IPBase.h"

class IPDetector : virtual public IPBase {
private:
	int mWidth, mHeight;
protected:
	inline const int width() const { return mWidth; };
	inline const int height() const { return mHeight; };
public:
	IPDetector();
	virtual ~IPDetector();
	void resize(const int &width, const int &height);
	virtual int detect(cv::Mat &src, std::vector<DetectRec_t> &contours, std::vector<const DetectRec_t *> &possibles_work,
		cv::Mat &result_frame, DetectRec_t &possible, const DetectParam_t &param) = 0;
};
#endif //FLIGHTDEMO_IPDETECTOR_H
