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

import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.GridView;

import com.serenegiant.mediastore.MediaStoreAdapter;
import com.serenegiant.mediastore.MediaStoreHelper;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

/** 端末内の静止画・動画一覧を表示するためのFragment */
public class GalleyFragment extends BaseFragment {
	private static final boolean DEBUG = false;	// FIXME 実働時はfalseにすること
	private static String TAG = GalleyFragment.class.getSimpleName();

	public static GalleyFragment newInstance() {
		GalleyFragment fragment = new GalleyFragment();
		return fragment;
	}

	private MediaStoreAdapter mMediaStoreAdapter;

	public GalleyFragment() {
		super();
		// デフォルトコンストラクタが必要
	}

	@Override
	public View onCreateView(@NonNull final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
		if (DEBUG) Log.v(TAG, "onCreateView:");
		final View rootView = inflater.inflate(R.layout.fragment_galley, container, false);
		initView(rootView);
		return rootView;
	}

	/**
	 * Viewを初期化
	 * @param rootView
	 */
	private void initView(final View rootView) {
		final GridView gridView = rootView.findViewById(R.id.media_gridview);
		mMediaStoreAdapter = new MediaStoreAdapter(getActivity(), R.layout.grid_item_media);
		gridView.setAdapter(mMediaStoreAdapter);
		gridView.setOnItemClickListener(mOnItemClickListener);
	}

	private final AdapterView.OnItemClickListener mOnItemClickListener = new AdapterView.OnItemClickListener() {
		@Override
		public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id) {
			switch (parent.getId()) {
			case R.id.media_gridview:
				doPlay(position, id);
				break;
			}
		}
	};

	private void doPlay(final int position, final long id) {
		final MediaStoreHelper.MediaInfo info = mMediaStoreAdapter.getMediaInfo(position);
		if (DEBUG) Log.v(TAG, "" + info);
		final Fragment fragment;
		switch (info.mediaType) {
		case MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE:
			// 静止画を選択した時
			fragment = PhotoFragment.newInstance(id);
			break;
		case MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO:
			// 動画を選択した時
			fragment = PlayerFragment.newInstance(info.data);
			break;
		default:
			fragment = null;
			break;
		}
		replace(fragment);
	}
}
