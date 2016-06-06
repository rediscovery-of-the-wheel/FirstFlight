package jp.co.rediscovery.arflight;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Checkable;
import android.widget.ImageView;
import android.widget.TextView;

import com.parrot.arsdk.ardiscovery.ARDISCOVERY_PRODUCT_ENUM;
import com.parrot.arsdk.ardiscovery.ARDiscoveryService;

import java.util.ArrayList;

/** スカイコントローラー経由でのデバイス探索画面で検出したデバイス一覧を表示するためのAdapterクラス */
public class ARDeviceInfoAdapter extends ArrayAdapter<DeviceInfo> {

	private final LayoutInflater mInflater;
	private final int mLayoutId;

	/**
	 * コンストラクタ
	 * @param context
	 * @param resource デバイス3Dモデル表示用のレイアウトリソースID, 表示できる項目のidはtitle(TextView), state(TextView), thumbnail(ImageView)
	 */
	public ARDeviceInfoAdapter(final Context context, final int resource) {
		super(context, resource, new ArrayList<DeviceInfo>());
		mInflater = LayoutInflater.from(context);
		mLayoutId = resource;
	}

	@Override
	public View getView(final int position, final View convertView, final ViewGroup parent) {
		View rootView = convertView;
		if (rootView == null) {
			rootView = mInflater.inflate(mLayoutId, parent, false);
		}
		ViewHolder holder = (ViewHolder)rootView.getTag(R.id.ardeviceserviceadapter);
		if (holder == null) {
			holder = new ViewHolder();
			holder.title = (TextView)rootView.findViewById(R.id.title);
			holder.state = (TextView)rootView.findViewById(R.id.state);
			holder.thumbnail = (ImageView)rootView.findViewById(R.id.thumbnail);
		}
		final DeviceInfo info = getItem(position);
		if (holder.title != null) {
			holder.title.setText(info.name());
		}
		if (holder.state != null) {
			// 接続状態の更新処理
			if (rootView instanceof Checkable) {
				holder.state.setText(((Checkable)rootView).isChecked() ? "選択中" : "---");
			}
		}
		if (holder.thumbnail != null) {
			// デバイスアイコンの更新処理。今は変更なし
			final ARDISCOVERY_PRODUCT_ENUM product = ARDiscoveryService.getProductFromProductID(info.productId());
			switch (product) {
			case ARDISCOVERY_PRODUCT_ARDRONE:	// Bebop
			case ARDISCOVERY_PRODUCT_BEBOP_2:	// Bebop2
			case ARDISCOVERY_PRODUCT_MINIDRONE:	// RollingSpider
			case ARDISCOVERY_PRODUCT_MINIDRONE_EVO_LIGHT:
			case ARDISCOVERY_PRODUCT_MINIDRONE_EVO_BRICK:
			case ARDISCOVERY_PRODUCT_MINIDRONE_EVO_HYDROFOIL: // ハイドロフォイルもいる?
			case ARDISCOVERY_PRODUCT_SKYCONTROLLER:	// SkyController
//				holder.thumbnail.setImageResource(デバイスアイコンリソースID);
				break;
			}
		}
		return rootView;
	}

	/**
	 * 指定した位置の検出デバイス名を取得
	 * @param position
	 * @return
	 */
	public String getItemName(final int position) {
		final DeviceInfo info = getItem(position);
		return info != null ? info.name() : null;
	}

	/** 生成した各項目用のViewの再利用をするためのholderクラス　*/
	private static final class ViewHolder {
		TextView title;
		TextView state;
		ImageView thumbnail;
	}
}
