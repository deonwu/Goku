package org.goku.video.odip;

/**
 * 协议里面用到的，摄像头状态信息。登录成功开始更新。
 * @author deon
 *
 */
public class ClientStatus {
	/**
	 * 正在实时监控.
	 */
	public boolean realPlaying = false;
	//public int status = 0;
	
	public int channelCount = 0; //通道数， ack_login W10
	
	/**
	 * 视频编码方式 ack_login W11
	 * 8 - MPEG4, 
	 * 9 - H.264
	 */
	public int videoType = 0;

	/**
	 * 设备类型 ack_login W12
	 */
	public int devType = 0;

	/**
	 * 设备类型 ack_login W16-W19
	 */	
	public int sessionId = 0;

	/**
	 * 最后一次有读操作时间。用于TimeOut管理。
	 */
	public long lastActiveTime = 0;
	/**
	 * 最后一次写操作时间。
	 */
	public long lastActionTime = 0;

	/**
	 * 视频制式 ack_login W28
	 */	
	public int devMode = 0;
	
	public String videoTypeName(){
		String name = "N/A";
		switch(this.videoType){
			case 8: name = "MPEG4";break;
			case 9: name = "H.264";break;
		}
		return this.videoType + ":" + name;
	}
	
	public String devTypeName(){
		String name = "N/A";
		switch(this.devType){
			case 0: name = "视豪";break;
			case 1: name = "视通";break;
			case 3: name = "视新";break;
		}
		return this.devType + ":" + name;
	}
	
	public String devModeName(){
		String name = "N/A";
		switch(this.devMode){
			case 0: name = "PAL";break;
			case 1: name = "NTSC";break;
		}
		return this.devMode + ":" + name;
	}
	
}