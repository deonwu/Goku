package org.goku.core.model;

import org.goku.video.odip.VideoChannel;

/**
 * 描述一个摄像头信息。
 * @author deon
 *
 */
public class MonitorChannel {
	public static final int MAIN_VIDEO = 0;
	
	public boolean videoStatus[] = new boolean[5];
	public int id;
	public String name;
	public VideoChannel videoChannel = null;
	//public boolean inVideo = false;
	
	public boolean videoPlaying(int mode){
		return videoStatus[mode];
	}
	
	public void videoStatus(int mode, boolean playing){
		videoStatus[mode] = playing;
	}
}
