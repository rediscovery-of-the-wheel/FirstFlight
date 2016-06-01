package jp.co.rediscovery.arflight;

public interface IVideoStreamController {
	public static final int DEFAULT_VIDEO_FRAGMENT_SIZE = 1000;
	public static final int DEFAULT_VIDEO_FRAGMENT_MAXIMUM_NUMBER = 128;
	public static final int VIDEO_RECEIVE_TIMEOUT_MS = 500;

	public void setVideoStream(final IVideoStream video_stream);
	public boolean isVideoStreamingEnabled();
	public boolean enableVideoStreaming(final boolean enable);
}
