package jp.co.rediscovery.arflight;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Locale;

import static com.parrot.arsdk.arcommands.ARCOMMANDS_SKYCONTROLLER_DEVICESTATE_CONNEXIONCHANGED_STATUS_ENUM.ARCOMMANDS_SKYCONTROLLER_DEVICESTATE_CONNEXIONCHANGED_STATUS_CONNECTED;
import static com.parrot.arsdk.arcommands.ARCOMMANDS_SKYCONTROLLER_DEVICESTATE_CONNEXIONCHANGED_STATUS_ENUM.ARCOMMANDS_SKYCONTROLLER_DEVICESTATE_CONNEXIONCHANGED_STATUS_CONNECTING;
import static com.parrot.arsdk.arcommands.ARCOMMANDS_SKYCONTROLLER_DEVICESTATE_CONNEXIONCHANGED_STATUS_ENUM.ARCOMMANDS_SKYCONTROLLER_DEVICESTATE_CONNEXIONCHANGED_STATUS_DISCONNECTING;
import static com.parrot.arsdk.arcommands.ARCOMMANDS_SKYCONTROLLER_DEVICESTATE_CONNEXIONCHANGED_STATUS_ENUM.ARCOMMANDS_SKYCONTROLLER_DEVICESTATE_CONNEXIONCHANGED_STATUS_NOTCONNECTED;

/** デバイス状態の保持用 */
public class DeviceInfo implements Parcelable {
	public static final int CONNECT_STATE_DISCONNECT = ARCOMMANDS_SKYCONTROLLER_DEVICESTATE_CONNEXIONCHANGED_STATUS_NOTCONNECTED.getValue();	// 0
	public static final int CONNECT_STATE_CONNECTING = ARCOMMANDS_SKYCONTROLLER_DEVICESTATE_CONNEXIONCHANGED_STATUS_CONNECTING.getValue();		// 1
	public static final int CONNECT_STATE_CONNECTED = ARCOMMANDS_SKYCONTROLLER_DEVICESTATE_CONNEXIONCHANGED_STATUS_CONNECTED.getValue();		// 2
	public static final int CONNECT_STATE_DISCONNECTing = ARCOMMANDS_SKYCONTROLLER_DEVICESTATE_CONNEXIONCHANGED_STATUS_DISCONNECTING.getValue();// 3

	public static final Creator<DeviceInfo> CREATOR = new Creator<DeviceInfo>() {
		@Override
		public DeviceInfo createFromParcel(final Parcel in) {
			return new DeviceInfo(in);
		}

		@Override
		public DeviceInfo[] newArray(final int size) {
			return new DeviceInfo[size];
		}
	};

	private final Object mSync = new Object();
	private final String mName;
	private final int mProductId;
	private int connectionState;

	/** コンストラクタ */
	public DeviceInfo(final String name, final int product_id) {
		mName = name;
		mProductId = product_id;
		connectionState = CONNECT_STATE_DISCONNECT;
	}

	/** コピーコンストラクタ */
	public DeviceInfo(final DeviceInfo other) {
		mName = other.mName;
		mProductId = other.mProductId;
		synchronized (other.mSync) {
			connectionState = other.connectionState;
		}
	}

	/** Parcelからの生成用コンストラクタ */
	protected DeviceInfo(final Parcel in) {
		mName = in.readString();
		mProductId = in.readInt();
		connectionState = in.readInt();
	}

	/**
	 * デバイス名を取得
	 * @return
	 */
	public String name() {
		return mName;
	}

	/**
	 * 製品IDを取得
	 * @return
	 */
	public int productId() {
		return mProductId;
	}

	/**
	 * 接続状態をセット
	 * @param connection_state
	 */
	public void connectionState(final int connection_state) {
		synchronized (mSync) {
			if (connectionState != connection_state) {
				connectionState = connection_state;
			}
		}
	}

	/**
	 * 接続状態を取得
	 * @return
	 */
	public int connectionState() {
		synchronized (mSync) {
			return connectionState;
		}
	}

	/**
	 * デバイスと接続しているかどうか
	 * @return
	 */
	public boolean isConnected() {
		synchronized (mSync) {
			return (connectionState == CONNECT_STATE_CONNECTING) || (connectionState == CONNECT_STATE_CONNECTED);
		}
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(final Parcel dest, final int flags) {
		dest.writeString(mName);
		dest.writeInt(mProductId);
		synchronized (mSync) {
			dest.writeInt(connectionState);
		}
	}

	@Override
	public String toString() {
		return String.format(Locale.US, "DeviceInfo(%s,id=%d,state=%d)", mName, mProductId, connectionState);
	}

	@Override
	public boolean equals(final Object other) {
		if (other instanceof DeviceInfo) {
			return (((mName == null) && (((DeviceInfo)other).mName == null))
					|| ((mName != null) && mName.equals(((DeviceInfo)other).mName)))
				&& (mProductId == ((DeviceInfo)other).mProductId)
				&& (connectionState == ((DeviceInfo)other).connectionState);
		} else {
			return super.equals(other);
		}
	}
}
