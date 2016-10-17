package jp.co.rediscovery.firstflight;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Checkable;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.Toast;

import com.parrot.arsdk.ardiscovery.ARDISCOVERY_PRODUCT_ENUM;
import com.parrot.arsdk.ardiscovery.ARDiscoveryDeviceService;
import com.parrot.arsdk.ardiscovery.ARDiscoveryService;

import java.util.List;

import jp.co.rediscovery.arflight.ARDeviceServiceAdapter;
import jp.co.rediscovery.arflight.ManagerFragment;

/***
 * デバイス探索画面用Fragment
 **/
public class ConnectionFragment extends BaseFragment {
	private static final boolean DEBUG = false;	// FIXME 実働時はfalseにすること
	private static String TAG = ConnectionFragment.class.getSimpleName();

	public static ConnectionFragment newInstance() {
		return new ConnectionFragment();
	}

	/*** 操縦画面等へ遷移するためのアイコン */
	protected ImageButton mDownloadBtn, mPilotBtn, mGalleyBrn, mAutoBtn;
	protected ListView mDeviceListView;

	public ConnectionFragment() {
		super();
		// Required empty public constructor
	}

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
//		if (DEBUG) Log.v(TAG, "onCreateView:");
		loadArguments(savedInstanceState);
		final View rootView = inflater.inflate(R.layout.fragment_connection, container, false);
		initView(rootView);
		return rootView;
	}

	@Override
	public void onResume() {
		super.onResume();
		if (DEBUG) Log.d(TAG, "onResume:");
		if (checkPermissionLocation()) {
			final ManagerFragment manager = ManagerFragment.getInstance(getActivity());
			manager.addCallback(mManagerCallback);
			manager.startDiscovery();
		}
		updateButtons(false);
	}

	@Override
	public void onPause() {
		if (DEBUG) Log.d(TAG, "onPause:");

		updateButtons(false);
		final ManagerFragment manager = ManagerFragment.getInstance(getActivity());
		if (manager != null) {
			manager.stopDiscovery();
			manager.removeCallback(mManagerCallback);
		}
		super.onPause();
	}

	/**
	 * Viewを初期化
	 * @param rootView
	 */
	private void initView(final View rootView) {

		final ARDeviceServiceAdapter adapter = new ARDeviceServiceAdapter(getActivity(), R.layout.list_item_deviceservice);

		mDeviceListView = (ListView)rootView.findViewById(R.id.list);
		final View empty_view = rootView.findViewById(R.id.empty_view);
		mDeviceListView.setEmptyView(empty_view);
		mDeviceListView.setAdapter(adapter);
		mDeviceListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

		mDownloadBtn = (ImageButton)rootView.findViewById(R.id.download_button);
		mDownloadBtn.setOnClickListener(mOnClickListener);
		mDownloadBtn.setOnLongClickListener(mOnLongClickListener);

		mPilotBtn = (ImageButton)rootView.findViewById(R.id.pilot_button);
		mPilotBtn.setOnClickListener(mOnClickListener);
		mPilotBtn.setOnLongClickListener(mOnLongClickListener);

		mGalleyBrn = (ImageButton)rootView.findViewById(R.id.gallery_button);
		mGalleyBrn.setOnClickListener(mOnClickListener);
		mGalleyBrn.setOnLongClickListener(mOnLongClickListener);

		mAutoBtn = (ImageButton)rootView.findViewById(R.id.auto_button);
		mAutoBtn.setOnClickListener(mOnClickListener);
		mAutoBtn.setOnLongClickListener(mOnLongClickListener);
	}

	private void updateButtons(final boolean visible) {
		final Activity activity = getActivity();
		if ((activity != null) && !activity.isFinishing()) {
			getActivity().runOnUiThread(new Runnable() {
				@Override
				public void run() {
					updateButtonsOnUiThread(visible);
				}
			});
		}
	}

	protected void updateButtonsOnUiThread(final boolean visible) {
		if (!visible) {
			try {
				final ARDeviceServiceAdapter adapter = (ARDeviceServiceAdapter)mDeviceListView.getAdapter();
				adapter.clear();
			} catch (final Exception e) {
				Log.w(TAG, e);
			}
		}
		final int visibility = visible ? View.VISIBLE : View.INVISIBLE;
		mDownloadBtn.setVisibility(visibility);
		mPilotBtn.setVisibility(visibility);
		mAutoBtn.setVisibility(visibility);
	}

	/**
	 * 検出したデバイスのリストが更新された時のコールバック
	 */
	private ManagerFragment.ManagerCallback mManagerCallback = new ManagerFragment.ManagerCallback() {
		@Override
		public void onServicesDevicesListUpdated(final List<ARDiscoveryDeviceService> devices) {
			if (DEBUG) Log.v(TAG, "onServicesDevicesListUpdated:");
			final ARDeviceServiceAdapter adapter = (ARDeviceServiceAdapter) mDeviceListView.getAdapter();
			adapter.clear();
			for (final ARDiscoveryDeviceService service : devices) {
				if (DEBUG) Log.d(TAG, "service :  " + service);
				final ARDISCOVERY_PRODUCT_ENUM product = ARDiscoveryService.getProductFromProductID(service.getProductID());
				switch (product) {
				case ARDISCOVERY_PRODUCT_ARDRONE:	// Bebop
				case ARDISCOVERY_PRODUCT_BEBOP_2:	// bebop2
					adapter.add(service);
					break;
				case ARDISCOVERY_PRODUCT_JS:		// JumpingSumo
				case ARDISCOVERY_PRODUCT_JS_EVO_LIGHT:
				case ARDISCOVERY_PRODUCT_JS_EVO_RACE:
					// FIXME JumpingSumoは未実装
					break;
				case ARDISCOVERY_PRODUCT_MINIDRONE:	// RollingSpider
				case ARDISCOVERY_PRODUCT_MINIDRONE_EVO_LIGHT:
				case ARDISCOVERY_PRODUCT_MINIDRONE_EVO_BRICK:
	//			case ARDISCOVERY_PRODUCT_MINIDRONE_EVO_HYDROFOIL: // ハイドロフォイルもいる?
					adapter.add(service);
					break;
				case ARDISCOVERY_PRODUCT_SKYCONTROLLER:	// SkyController
					adapter.add(service);
					break;
				case ARDISCOVERY_PRODUCT_NSNETSERVICE:
					break;
				}
/*				// ブルートゥース接続の時だけ追加する
				if (service.getDevice() instanceof ARDiscoveryDeviceBLEService) {
					adapter.add(service.getName());
				} */
			}
			adapter.notifyDataSetChanged();
			mDeviceListView.setItemChecked(0, true);	// 先頭を選択
			updateButtons(adapter.getCount() > 0);
		}
	};

	private void clearCheck(final ViewGroup parent) {
		final int n = parent.getChildCount();
		for (int i = 0; i < n; i++) {
			final View v = parent.getChildAt(i);
			if (v instanceof Checkable) {
				((Checkable) v).setChecked(false);
			}
		}
	}

	private final View.OnClickListener mOnClickListener = new View.OnClickListener() {
		@Override
		public void onClick(final View view) {
			ConnectionFragment.this.onClick(view, mDeviceListView.getCheckedItemPosition());
		}
	};

	protected void onClick(final View view, final int position) {
		Fragment fragment = null;
		switch (view.getId()) {
		case R.id.pilot_button:
			if (checkPermissionLocation()) {
				fragment = getFragment(position, true);
			}
			break;
		case R.id.download_button:
			if (checkPermissionWriteExternalStorage()) {
				fragment = getFragment(position, false);
			}
			break;
		case R.id.gallery_button:
			if (checkPermissionWriteExternalStorage()) {
				fragment = GalleyFragment.newInstance();
			}
			break;
		case R.id.auto_button:
		{
			if (checkPermissionLocation()) {
//				final ManagerFragment manager = ManagerFragment.getInstance(getActivity());
				final ARDeviceServiceAdapter adapter = (ARDeviceServiceAdapter)mDeviceListView.getAdapter();
//				final String itemValue = adapter.getItemName(position);
//				final ARDiscoveryDeviceService device = manager.getDevice(itemValue);
				final ARDiscoveryDeviceService device = adapter.getItem(position);
				if (device != null) {
					// 製品名を取得
					final ARDISCOVERY_PRODUCT_ENUM product = ARDiscoveryService.getProductFromProductID(device.getProductID());

					switch (product) {
					case ARDISCOVERY_PRODUCT_ARDRONE:	// Bebop
						fragment = AutoPilotFragment.newInstance(device, null, "bebop", AutoPilotFragment.MODE_TRACE);
						break;
					case ARDISCOVERY_PRODUCT_BEBOP_2:	// Bebop2
						fragment = AutoPilotFragment.newInstance(device, null, "bebop2", AutoPilotFragment.MODE_TRACE);
						break;
					case ARDISCOVERY_PRODUCT_SKYCONTROLLER:	// SkyControllerNewAPI
						fragment = newBridgetFragment(device);
						break;
					default:
						Toast.makeText(getActivity(), R.string.unsupported_product, Toast.LENGTH_SHORT).show();
						break;
					}
				}
			}
			break;
		}
		}
		replace(fragment);
	}

	private final View.OnLongClickListener mOnLongClickListener = new View.OnLongClickListener() {
		@Override
		public boolean onLongClick(final View view) {
			if (mPilotBtn.getVisibility() != View.VISIBLE) return false;
			return ConnectionFragment.this.onLongClick(view, mDeviceListView.getCheckedItemPosition());
		}
	};

	protected boolean onLongClick(final View view, final int position) {
		return false;
	}

	protected Fragment getFragment(final int position, final boolean isPiloting) {
//		final ManagerFragment manager = ManagerFragment.getInstance(getActivity());
		final ARDeviceServiceAdapter adapter = (ARDeviceServiceAdapter)mDeviceListView.getAdapter();
//		final String itemValue = adapter.getItemName(position);
//		final ARDiscoveryDeviceService device = manager.getDevice(itemValue);
		final ARDiscoveryDeviceService device = adapter.getItem(position);
		Fragment fragment = null;
		if (device != null) {
			// 製品名を取得
			final ARDISCOVERY_PRODUCT_ENUM product = ARDiscoveryService.getProductFromProductID(device.getProductID());

			switch (product) {
			case ARDISCOVERY_PRODUCT_ARDRONE:	// Bebop
			case ARDISCOVERY_PRODUCT_BEBOP_2:	// Bebop2
				fragment = isPiloting ? PilotFragment.newInstance(device, null) : MediaFragment.newInstance(device, null);
				break;
			case ARDISCOVERY_PRODUCT_JS:        // JumpingSumo
				//FIXME JumpingSumoは未実装
				break;
			case ARDISCOVERY_PRODUCT_MINIDRONE:	// RollingSpider
			case ARDISCOVERY_PRODUCT_MINIDRONE_EVO_LIGHT:
			case ARDISCOVERY_PRODUCT_MINIDRONE_EVO_BRICK:
//			case ARDISCOVERY_PRODUCT_MINIDRONE_EVO_HYDROFOIL: // ハイドロフォイルもいる?
				fragment = isPiloting ? PilotFragment.newInstance(device, null) : MediaFragment.newInstance(device, null);
				break;
			case ARDISCOVERY_PRODUCT_SKYCONTROLLER:	// SkyController
				fragment = newBridgetFragment(device);
				break;
			}
		}
		return fragment;
	}

	protected BridgeFragment newBridgetFragment(final ARDiscoveryDeviceService device) {
		return BridgeFragment.newInstance(device);
	}

}
