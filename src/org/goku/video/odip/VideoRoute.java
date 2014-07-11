package org.goku.video.odip;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.ThreadPoolExecutor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Video 路由器。负责转发Video数据。
 * @author deon
 *
 */
public class VideoRoute {
	private Log log = null;
	private ThreadPoolExecutor executor = null;
	private Collection<VideoDestination> destList = Collections.synchronizedCollection(new ArrayList<VideoDestination>());
	private MonitorClient client = null;
	
	public VideoRoute(ThreadPoolExecutor executor){
		this.executor = executor;
	}
	
	/**
	 * 使用Client的logger, 规范log输出。
	 * @param log
	 */
	public void setLogger(Log log){
		this.log = log;
	}
	
	public void start(MonitorClient client){
		this.client = client;
	}
	
	/**
	 * 处理缓存数据，读取到byte[]后，转发到不同的目的对象。复制到byte[]是为了避免，
	 * 目标节点数据处理太慢，导致数据还没有处理，就被源数据覆盖。
	 * 
	 * 如果写目的出现异常，关闭目的，并从转发列表中删除。
	 * @param source
	 * @param sourceType -- 源数据类型， 用于处理双码流情况。
	 * @param channel -- 视通通道号
	 */	
	public void route(ByteBuffer source, int sourceType, int channel){
		final ByteBuffer buffer = ByteBuffer.allocate(source.remaining());
		buffer.put(source);
		buffer.flip();
		long et = 0, st = 0;
		int acceptCount = 0;
		synchronized(destList){
			VideoDestination dest = null;
			for(Iterator<VideoDestination> iter = destList.iterator(); iter.hasNext();){
				dest = iter.next();
				if(dest.isClosed()){
					iter.remove();
				}else if(dest.accept(sourceType, channel)){
					acceptCount++;
					try {
						st = System.currentTimeMillis();
						dest.write(buffer.duplicate(), sourceType, channel);
						et = System.currentTimeMillis() - st;
						if(et > 5){
							log.warn("Destination too slow, dest:" + dest.toString() + ", write time:" + et + "ms.");							
						}
					} catch (IOException e) {
						log.warn("Routting error, the destination will removed.:" + e.toString());
						iter.remove();
						dest.close();				
					}
				}
			}
		}
		//如果没有目地在接收视频数据，关闭在通道的数据。
		if(acceptCount <= 0){
			this.client.videoDestinationEmpty(channel, sourceType);
		}
	}
	
	public void end(){
		synchronized(destList){
			for(VideoDestination dest: destList){
				dest.close();
			}
			destList.clear();
		}
	}
	
	public void addDestination(VideoDestination dest){
		if(!this.destList.contains(dest)){
			this.destList.add(dest);
			log.debug("Add video destination, dest=" + dest.toString());
		}
	}
	
	public void removeDestination(VideoDestination dest){
		if(this.destList.contains(dest)){
			this.destList.remove(dest);
			log.debug("Remove video destination, dest=" + dest.toString());
		}
	}
	
	public int destinationSize(){
		return this.destList.size();
	}
}
