package com.serenegiant.mediaeffect;
/*
 * Copyright (c) 2015 saki t_saki@serenegiant.com
 *
 * File name: MediaEffectStraighten.java
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
*/

import android.media.effect.EffectContext;
import android.media.effect.EffectFactory;

public class MediaEffectStraighten extends MediaEffect {
	/**
	 * コンストラクタ
	 * GLコンテキスト内で生成すること
	 *
	 * @param effect_context
	 * @param angle The angle of rotation. between -45 and +45.
	 */
	public MediaEffectStraighten(final EffectContext effect_context, final float angle) {
		super(effect_context, EffectFactory.EFFECT_STRAIGHTEN);
		setParameter(angle);
	}

	/**
	 * @param angle The angle of rotation. between -45 and +45.
	 * @return
	 */
	public MediaEffectStraighten setParameter(final float angle) {
		setParameter("angle", angle);
		return this;
	}
}
