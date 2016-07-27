//
// Created by saki on 16/03/01.
//

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

#include <jni.h>
#include <stdlib.h>
#include <algorithm>

#include "utilbase.h"
#include "common_utils.h"
#include "JNIHelp.h"
#include "Errors.h"

#include "ImageProcessor.h"

struct fields_t {
    jmethodID callFromNative;
    jmethodID arrayID;	// ByteBufferがdirectBufferでない時にJava側からbyte[]を取得するためのメソッドid
};
static fields_t fields;

using namespace android;

ImageProcessor::ImageProcessor(JNIEnv* env, jobject weak_thiz_obj, jclass clazz)
:	mWeakThiz(env->NewGlobalRef(weak_thiz_obj)),
	mClazz((jclass)env->NewGlobalRef(clazz)),
	mIsRunning(false)
{
	// 結果形式
	mParam.mResultFrameType = RESULT_FRAME_TYPE_DST_LINE;
	// 輪郭近似
#if 1
	mParam.mApproxType = APPROX_RELATIVE;
	mParam.mApproxFactor = 0.01;
#else
	mParam.mApproxType = APPROX_ABS;
	mParam.mApproxFactor = 10;
#endif
	// 検出輪郭の内部空隙を塗りつぶすかどうか
	mParam.mFillInnerContour = false;
	// 基準図形との類似性の最大値
	mParam.mMaxAnalogous = 200.0;
//	// 台形補正
//	mParam.mTrapeziumRate = 0.0;
	// 輪郭検出時の最小/最大面積
	mParam.mAreaLimitMin = 1000.0f;
	mParam.mAreaLimitMax = 120000.0f;
	// 輪郭検出時の面積誤差
	mParam.mAreaErrLimit1 = 1.25f;
	mParam.mAreaErrLimit2 = 1.3f;
	// ライン検出時の最小アスペクト比
	mParam.mMinLineAspect = 3.0f;
	mParam.changed = true;
}

ImageProcessor::~ImageProcessor() {
}

void ImageProcessor::release(JNIEnv *env) {
	ENTER();

	if (LIKELY(env)) {
		if (mWeakThiz) {
			env->DeleteGlobalRef(mWeakThiz);
			mWeakThiz = NULL;
		}
		if (mClazz) {
			env->DeleteGlobalRef(mClazz);
			mClazz = NULL;
		}
	}
	clearFrames();

	EXIT();
}

/**
 * プロセッシングスレッド開始
 * これはJava側の描画スレッド内から呼ばれる(EGLContextが有るのでOpenGL|ES関係の処理可)
 */
int ImageProcessor::start(const int &width, const int &height) {
	ENTER();
	int result = -1;

	if (!isRunning()) {
		mMutex.lock();
		{
			mLineDetector.resize(width, height);
			mCurveDetector.resize(width, height);
			initFrame(width, height);
			mIsRunning = true;
			result = pthread_create(&processor_thread, NULL, processor_thread_func, (void *)this);
		}
		mMutex.unlock();
	} else {
		LOGW("already running");
	}

	RETURN(result, int);
}

/**
 * プロセッシングスレッド終了
 * これはJava側の描画スレッド内から呼ばれる(EGLContextが有るのでOpenGL|ES関係の処理可)
 */
int ImageProcessor::stop() {
	ENTER();

	bool b = isRunning();
	if (LIKELY(b)) {
		// XXX releaseFrame内でgetFrame待ちが一旦解除されて抜けて
		// 再度getFrame待ちに入ってハングアップしてしまうので先にmIsRunningを落とさないとダメ
		mIsRunning = false;
		releaseFrame();
		mMutex.lock();
		{
			MARK("signal to processor thread");
			mSync.broadcast();
		}
		mMutex.unlock();
		MARK("プロセッサスレッド終了待ち");
		if (pthread_join(processor_thread, NULL) != EXIT_SUCCESS) {
			LOGW("terminate processor thread: pthread_join failed");
		}
	}
	clearFrames();

	RETURN(0, int);
}

void ImageProcessor::setResultFrameType(const int &result_frame_type) {
	ENTER();

	Mutex::Autolock lock(mMutex);

	mParam.mResultFrameType = result_frame_type % RESULT_FRAME_TYPE_MAX;
	mParam.changed = true;

	EXIT();
};

/** ライン検出時の面積の上下限をセット */
int ImageProcessor::setAreaLimit(const float &min, const float &max) {
	ENTER();

	if ((min != max) && ((mParam.mAreaLimitMin != min) || (mParam.mAreaLimitMax != max))) {
		mMutex.lock();
		{
			mParam.mAreaLimitMin = fmin(min, max);
			mParam.mAreaLimitMax = fmax(min, max);
			mParam.changed = true;
		}
		mMutex.unlock();
	}

	RETURN(0, int);
}

int ImageProcessor::setAspectLimit(const float &min) {
	ENTER();

	if ((min > 0.0f) && (mParam.mMinLineAspect != min)) {
		mMutex.lock();
		{
			mParam.mMinLineAspect = min;
			mParam.changed = true;
		}
		mMutex.unlock();
	}

	RETURN(0, int);
}

int ImageProcessor::setAreaErrLimit(const float &limit1, const float &limit2) {
	ENTER();

	if ((limit1 >= 1.0f) && (limit2 >= 1.0f) && ((mParam.mAreaErrLimit1 != limit1) || (mParam.mAreaErrLimit2 != limit2))) {
		mMutex.lock();
		{
			mParam.mAreaErrLimit1 = limit1;
			mParam.mAreaErrLimit2 = limit2;
			mParam.changed = true;
		}
		mMutex.unlock();
	}

	RETURN(0, int);
}

int ImageProcessor::setFillInnerContour(const bool &fill) {
	ENTER();

	if (mParam.mFillInnerContour != fill) {
		mMutex.lock();
		{
			mParam.mFillInnerContour = fill;
			mParam.changed = true;
		}
		mMutex.unlock();
	}
	RETURN(0, int);
}


/** プロセッシングスレッドの実行関数 */
/*private*/
void *ImageProcessor::processor_thread_func(void *vptr_args) {
	ENTER();

	ImageProcessor *processor = reinterpret_cast<ImageProcessor *>(vptr_args);
	if (LIKELY(processor)) {
		// Java側へアクセスできるようにするためにJavaVMへアタッチする
		JavaVM *vm = getVM();
		CHECK(vm);
		JNIEnv *env;
		vm->AttachCurrentThread(&env, NULL);
		CHECK(env);
		processor->do_process(env);
		LOGD("プロセッサループ終了, JavaVMからデタッチする");
		vm->DetachCurrentThread();
		LOGD("デタッチ終了");
	}

	PRE_EXIT();
	pthread_exit(NULL);
}

/** プロセッシングスレッドの実体 */
/*private*/
void ImageProcessor::do_process(JNIEnv *env) {
	ENTER();

	DetectRec_t line, curve, corner, *possible;
	cv::Mat src, bk_result, result;
	std::vector<std::vector< cv::Point>> contours;	// 輪郭データ
	std::vector<const DetectRec_t *> work;	// ワーク用
	std::vector<DetectRec_t> approxes;	// 近似輪郭
	DetectParam_t param;
	long last_queued_time_ms;

	for ( ; mIsRunning ; ) {
		// フレームデータの取得待ち
		cv::Mat frame = getFrame(last_queued_time_ms);
		if (UNLIKELY(!mIsRunning)) break;
		if (LIKELY(!frame.empty())) {
			try {
//================================================================================
// フラグ更新
				mMutex.lock();
				{
					if (UNLIKELY(mParam.changed)) {
						param.set(mParam);
						mParam.changed = false;
					}
				}
				mMutex.unlock();
//--------------------------------------------------------------------------------
// 前処理
				pre_process(frame, src, bk_result, param);
				if (UNLIKELY(!mIsRunning)) break;
//--------------------------------------------------------------------------------
// 輪郭の検出処理
// 最大で直線・円弧・コーナーの3つの処理が走るので近似輪郭検出と最低限のチェック(面積とか)は1回だけ先に済ましておく
				findPossibleContours(src, bk_result, contours, approxes, param);
				if (UNLIKELY(!mIsRunning)) break;
				{	// vectorの余分なメモリーを開放する
					std::vector<std::vector< cv::Point>> temp1;
					contours.swap(temp1);
				}
//--------------------------------------------------------------------------------
// 直線ラインの検出処理
				result = bk_result;	// 結果用画像を初期化
				mLineDetector.detect(src, approxes, work, result, line, param);
				if (UNLIKELY(!mIsRunning)) break;
// 円弧の検出処理
				mCurveDetector.detect(src, approxes, work, result, curve, param);
				if (UNLIKELY(!mIsRunning)) break;
//================================================================================
				{	// vectorの余分なメモリーを開放する
					std::vector<const DetectRec_t *> temp2;
					std::vector<DetectRec_t> temp3;
					work.swap(temp2);
					approxes.swap(temp3);
				}
				// 面積の大きい方を選択する FIXME 得点化してソート
				const float a = curve.type != TYPE_NON ? curve.area : 0.0f;
				const float b = line.type != TYPE_NON ? line.area : 0.0f;
				const float c = corner.type != TYPE_NON ? corner.area : 0.0f;
				possible = &curve;
				if (a < b) {
					if (b > c) {
						possible = &line;
					} else {
						possible = &corner;
					}
				}
				if (param.show_detects && (possible->type != TYPE_NON)) {
					// ラインとして検出した輪郭線を赤で描画する
					cv::polylines(result, possible->contour, true, COLOR_RED, 2);
#if 1
					// 映像中央から輪郭の最小矩形の中心に向かって線を引く
					cv::line(result, cv::Point(width() >> 1, height() >> 1), possible->area_rect.center, COLOR_RED, 8, 8);
#else
					// 映像中央から検出したオブジェクトの重心に向かって線を引く
					if (possible->moments.m00 != 0) {
						cv::line(result, cv::Point(width() >> 1, height() >> 1), possible->center, COLOR_RED, 8, 8);
					}
#endif
					if (possible->type == TYPE_CURVE) {
						cv::ellipse(result, possible->ellipse.center, possible->ellipse.size, possible->ellipse.angle, 0, 360, COLOR_RED);
					}
					if (possible->coeffs.size() > 0) {
					}
				}
				// Java側のコールバックメソッドを呼び出す
				callJavaCallback(env, *possible, result, last_queued_time_ms, param);
			} catch (cv::Exception e) {
				LOGE("do_process failed:%s", e.msg.c_str());
				continue;
			} catch (...) {
				LOGE("do_process unknown exception:");
				break;
			}
			recycle(frame);
		}
	}

	EXIT();
}

#define RESULT_NUM 20

/*private*/
int ImageProcessor::callJavaCallback(JNIEnv *env, DetectRec_t &detect_result, cv::Mat &result, const long &last_queued_time_ms, const DetectParam_t &param) {
	ENTER();

	float detected[RESULT_NUM];

	if (LIKELY(mIsRunning && fields.callFromNative && mClazz && mWeakThiz)) {
		// 解析結果を配列にセットする
		// ラインの最小矩形の中心座標(位置ベクトル,cv::RotatedRect#center)
		detected[0] = detect_result.area_rect.center.x;
		detected[1] = detect_result.area_rect.center.y;
		// ラインの長さ(長軸長さ=length)
		detected[2] = detect_result.length;
		// ライン幅(短軸長さ)
		detected[3] = detect_result.width;
		// ラインの方向(cv::RotatedRect#angle)
		// カメラ映像の真上を0としてラインが右(1,2,3時方向)に傾いてれば負,左(11,10,9時方向)に傾いていれば正
		// デバイスの向きは逆
		detected[4] = (detect_result.area_rect.size.width <= detect_result.area_rect.size.height ? 0.0f : -90.0f) - detect_result.area_rect.angle;
		// 最小矩形面積に対する輪郭面積の比
		detected[5] = detect_result.area_rate;
		// 楕円フィッティングの曲率
		detected[6] = detect_result.curvature;
		// 楕円の中心座標
		detected[7] = detect_result.ex;
		detected[8] = detect_result.ey;
		// 楕円の長軸/短軸半径
		detected[9] = detect_result.ellipse.size.width;
		detected[10] = detect_result.ellipse.size.height;
		// 楕円の傾き
		detected[11] = detect_result.ellipse.angle;
		// 重心位置
		detected[12] = detect_result.center.x;
		detected[13] = detect_result.center.y;
		// 概算処理時間
		detected[19] = (float)(getTimeMilliseconds() - last_queued_time_ms);
		//
		jfloatArray detected_array = env->NewFloatArray(RESULT_NUM);
		env->SetFloatArrayRegion(detected_array, 0, RESULT_NUM, detected);
		// 解析画像
		jobject buf_frame = param.needs_result ? env->NewDirectByteBuffer(result.data, result.total() * result.elemSize()) : NULL;
		// コールバックメソッドを呼び出す
		env->CallStaticVoidMethod(mClazz, fields.callFromNative, mWeakThiz, detect_result.type, buf_frame, detected_array);
		env->ExceptionClear();
		if (LIKELY(detected_array)) {
			env->DeleteLocalRef(detected_array);
		}
		if (buf_frame) {
			env->DeleteLocalRef(buf_frame);
		}
	}

	RETURN(0, int);
}

//********************************************************************************
//********************************************************************************
static void nativeClassInit(JNIEnv* env, jclass clazz) {
	ENTER();

	fields.callFromNative = env->GetStaticMethodID(clazz, "callFromNative",
         "(Ljava/lang/ref/WeakReference;ILjava/nio/ByteBuffer;[F)V");
	if (UNLIKELY(!fields.callFromNative)) {
		LOGW("can't find com.serenegiant.ImageProcessor#callFromNative");
	}
	env->ExceptionClear();
	// ByteBufferがdirectBufferでない時にJava側からbyte[]を取得するためのメソッドidを取得
    jclass byteBufClass = env->FindClass("java/nio/ByteBuffer");

	if (LIKELY(byteBufClass)) {
		fields.arrayID = env->GetMethodID(byteBufClass, "array", "()[B");
		if (!fields.arrayID) {
			LOGE("Can't find java/nio/ByteBuffer#array");
		}
	} else {
		LOGE("Can't find java/nio/ByteBuffer");
	}
	env->ExceptionClear();

	EXIT();
}

static ID_TYPE nativeCreate(JNIEnv *env, jobject thiz,
	jobject weak_thiz_obj) {

	ImageProcessor *processor = NULL;

	jclass clazz = env->GetObjectClass(thiz);
	if (LIKELY(clazz)) {
		processor = new ImageProcessor(env, weak_thiz_obj, clazz);
		setField_long(env, thiz, "mNativePtr", reinterpret_cast<ID_TYPE>(processor));
	} else {
		jniThrowRuntimeException(env, "can't find com.serenegiant.ImageProcessor");
	}

	RETURN(reinterpret_cast<ID_TYPE>(processor), ID_TYPE);
}

static void nativeRelease(JNIEnv *env, jobject thiz,
	ID_TYPE id_native) {

	ENTER();

	setField_long(env, thiz, "mNativePtr", 0);
	ImageProcessor *processor = reinterpret_cast<ImageProcessor *>(id_native);
	if (LIKELY(processor)) {
		// 終了処理
		processor->release(env);
		SAFE_DELETE(processor);
	}

	EXIT();
}

static jint nativeStart(JNIEnv *env, jobject thiz,
	ID_TYPE id_native, jint width, jint height) {

	ENTER();

	jint result = -1;
	ImageProcessor *processor = reinterpret_cast<ImageProcessor *>(id_native);
	if (LIKELY(processor)) {
		result = processor->start(width, height);
	}

	RETURN(result, jint);
}

static jint nativeStop(JNIEnv *env, jobject thiz,
	ID_TYPE id_native) {

	ENTER();

	jint result = -1;
	ImageProcessor *processor = reinterpret_cast<ImageProcessor *>(id_native);
	if (LIKELY(processor)) {
		result = processor->stop();
	}

	RETURN(result, jint);
}

static int nativeHandleFrame(JNIEnv *env, jobject thiz,
	ID_TYPE id_native, jint width, jint height, jint tex_name) {

	ENTER();

	jint result = -1;
	ImageProcessor *processor = reinterpret_cast<ImageProcessor *>(id_native);
	if (LIKELY(processor)) {
		// フレーム処理
		// こっちはNative側でglReadPixelsを呼んで画像を読み込んで処理する時
		result = processor->handleFrame(width, height, tex_name);
	}

	RETURN(result, jint);
}

static jint nativeSetResultFrameType(JNIEnv *env, jobject thiz,
	ID_TYPE id_native, jint result_frame_type) {

	ENTER();

	jint result = -1;
	ImageProcessor *processor = reinterpret_cast<ImageProcessor *>(id_native);
	if (LIKELY(processor)) {
		processor->setResultFrameType(result_frame_type);
		result = 0;
	}

	RETURN(result, jint);
}

static jint nativeGetResultFrameType(JNIEnv *env, jobject thiz,
	ID_TYPE id_native) {

	ENTER();

	jint result = 0;
	ImageProcessor *processor = reinterpret_cast<ImageProcessor *>(id_native);
	if (LIKELY(processor)) {
		result = processor->getResultFrameType();
	}

	RETURN(result, jint);
}

static jint nativeSetAreaLimit(JNIEnv *env, jobject thiz,
	ID_TYPE id_native, jfloat min, jfloat max) {

	ENTER();

	jint result = -1;
	ImageProcessor *processor = reinterpret_cast<ImageProcessor *>(id_native);
	if (LIKELY(processor)) {
		result = processor->setAreaLimit(min, max);
	}

	RETURN(result, jint);
}

static jint nativeSetAspectLimit(JNIEnv *env, jobject thiz,
	ID_TYPE id_native, jfloat min) {

	ENTER();

	jint result = -1;
	ImageProcessor *processor = reinterpret_cast<ImageProcessor *>(id_native);
	if (LIKELY(processor)) {
		result = processor->setAspectLimit(min);
	}

	RETURN(result, jint);
}

static jint nativeSetAreaErrLimit(JNIEnv *env, jobject thiz,
	ID_TYPE id_native, jfloat limit1, jfloat limit2) {

	ENTER();

	jint result = -1;
	ImageProcessor *processor = reinterpret_cast<ImageProcessor *>(id_native);
	if (LIKELY(processor)) {
		result = processor->setAreaErrLimit(limit1, limit2);
	}

	RETURN(result, jint);
}

//================================================================================
//================================================================================
static JNINativeMethod methods[] = {
	{ "nativeClassInit",			"()V",   (void*)nativeClassInit },
	{ "nativeCreate",				"(Ljava/lang/ref/WeakReference;)J", (void *) nativeCreate },
	{ "nativeRelease",				"(J)V", (void *) nativeRelease },
	{ "nativeStart",				"(JII)I", (void *) nativeStart },
	{ "nativeStop",					"(J)I", (void *) nativeStop },
	{ "nativeHandleFrame",			"(JIII)I", (void *) nativeHandleFrame },
	{ "nativeSetResultFrameType",	"(JI)I", (void *) nativeSetResultFrameType },
	{ "nativeGetResultFrameType",	"(J)I", (void *) nativeGetResultFrameType },
	{ "nativeSetAreaLimit",			"(JFF)I", (void *) nativeSetAreaLimit },
	{ "nativeSetAspectLimit",		"(JF)I", (void *) nativeSetAspectLimit },
	{ "nativeSetAreaErrLimit",		"(JFF)I", (void *) nativeSetAreaErrLimit },
};


int register_ImageProcessor(JNIEnv *env) {
	// ネイティブメソッドを登録
	if (registerNativeMethods(env,
		"jp/co/rediscovery/opencv/ImageProcessor",
		methods, NUM_ARRAY_ELEMENTS(methods)) < 0) {
		return -1;
	}
	return 0;
}

jint JNI_OnLoad(JavaVM *vm, void *reserved) {
#if LOCAL_DEBUG
    LOGD("JNI_OnLoad");
#endif

    JNIEnv *env;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }
    // register native methods
    int result = register_ImageProcessor(env);

	setVM(vm);

#if LOCAL_DEBUG
    LOGD("JNI_OnLoad:finished:result=%d", result);
#endif
    return JNI_VERSION_1_6;
}
