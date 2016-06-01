package jp.co.rediscovery.arflight;

public interface DeviceConnectionListener {
	/**
	 * 接続した時のコールバック
	 * @param controller
	 */
	public void onConnect(final IDeviceController controller);
	/**
	 * 切断された時のコールバック
	 */
	public void onDisconnect(final IDeviceController controller);
	/**
	 * 電池残量が変化した時のコールバック
	 * @param controller
	 * @param percent
	 */
	public void onUpdateBattery(final IDeviceController controller, final int percent);

	/**
	 * WiFi信号強度が変化した時のコールバック
	 * @param controller
	 * @param rssi
	 */
	public void onUpdateWiFiSignal(final IDeviceController controller, final int rssi);
	/**
	 * 機器からの異常通知時のコールバック
	 * @param controller
	 * @param alarm_state
	 * 0: No alert, 1:User emergency alert, 2:Cut out alert, 3:Critical battery alert, 4:Low battery alert
	 */
	public void onAlarmStateChangedUpdate(final IDeviceController controller, final int alarm_state);
}
