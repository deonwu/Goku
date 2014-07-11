package org.goku.video;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.goku.video.encoder.FFMpegVideoEncoder;
import org.goku.video.encoder.Pollable;
import org.goku.video.odip.MonitorClient;
import org.goku.video.odip.VideoDestination;

/**
 * 视频格式编码服务，使用ffmpeg程序把实时监控视频流，转换为不同格式和大小的视频流。
 * 使用pipe方式，把H264视频流输入到ffmpeg进程，让后读取输出流。
 * 
 * @author deon
 */
public class VideoEncodingService implements Runnable{
	private Log log = null;
	private Collection<Pollable> pool = Collections.synchronizedCollection(new ArrayList<Pollable>());
	private boolean isClosed = false;
	private ThreadPoolExecutor threadPool = null; 
	private Map<String, FFMpegVideoEncoder> encoderPool = new HashMap<String, FFMpegVideoEncoder>();
	private String ffMpegPath = null;
	private long lastCheck = System.currentTimeMillis();
	
	public VideoEncodingService(ThreadPoolExecutor threadPool, String ffMpegPath){
		log = LogFactory.getLog("ffmpeg");
		this.threadPool = threadPool;
		this.ffMpegPath = ffMpegPath;
	}
	
	/**
	 * 在客户端上注册一个输出格式。
	 * @param client 转换的视频设备。
	 * @param ch -- 需要注册的视频通道。
	 * @param dest 数据流输出目地。
	 * @param format 输出编码格式 -- ogg, flv, mp4 等.
	 */
	public synchronized void registerVideoOutput(MonitorClient client, int ch, VideoDestination dest, String format){
		String key = client.info.uuid + "_" + ch + "_" + format;
		FFMpegVideoEncoder encoder = encoderPool.get(key);
		if(encoder == null){
			log.debug("Create new ffmpeg video encoder:" + key);
			encoder = new FFMpegVideoEncoder(client.info.uuid, ch, format);
			encoder.start(pool, ffMpegPath);
			encoderPool.put(key, encoder);
			client.route.addDestination(encoder);
		}else {
			log.debug("Get video encoder:" + key);
		}
		encoder.addVideoDestination(dest);
	}

	@Override
	public void run() {
		log.info("Starting a video encoding service...");
		while(!isClosed){
			synchronized(pool){
				Pollable o = null; 
				for(Iterator<Pollable> iter = pool.iterator();
				iter.hasNext();){
					o = iter.next();
					if(o.isClosed()){
						iter.remove();
						//可能有什么缓冲什么，还可以读一下。
						threadPool.execute(o);
						continue;
					}
					if(o.isReady()){
						threadPool.execute(o);
					}
				}
				this.cleanUpStopEncoder();
			}
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
			}
		}
		log.info("Video encoding service is stopped.");
	}
	
	private void cleanUpStopEncoder(){
		if(System.currentTimeMillis() - this.lastCheck < 1000) return;
		FFMpegVideoEncoder encoder = null;
		for(String key: encoderPool.keySet().toArray(new String[0])){
			encoder = encoderPool.get(key);
			if(encoder.isClosed()){
				encoderPool.remove(key);
				log.debug("Remove stopped ffmpeg process, " + key);
			}
		}
		this.lastCheck = System.currentTimeMillis();
	}
}
