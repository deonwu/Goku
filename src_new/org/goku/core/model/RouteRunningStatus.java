package org.goku.core.model;

import java.util.Date;

/**
 * 系统运行状态。
 * @author deon
 */
public class RouteRunningStatus {
	private Object lock = new Object();
	public Date statusTime = new Date(System.currentTimeMillis());
	/**
	 * 接受数据量
	 */
	public long receiveData = 0;
	/**
	 * 发送数据量
	 */	
	public long sendData = 0;
	
	/**
	 * 正在传视频的终端个数。
	 */
	public long activeVideo = 0;
	
	/**
	 * 正成功连接的终端个数
	 */
	public long connectVideo = 0;
	/**
	 * 总的终端个数
	 */
	public long allVideo = 0;	
	
	/**
	 * 命令处理次数
	 */
	public long clientRequestCount = 0;
	
	public long lastReadTime = System.currentTimeMillis();
	private long readSize = 0;
	public double readKbs = 0;
	
	
	public boolean receiveData(long size){
		synchronized(lock){
			this.receiveData += size;
		}
		this.readSize += size;
		if(System.currentTimeMillis() - lastReadTime > 1000){
			readKbs = readSize * 1.0 / (System.currentTimeMillis() - lastReadTime);
			readSize = 0;
			lastReadTime = System.currentTimeMillis();
			return true;
		}else {		
			return false;
		}
	}
	public void sendData(long size){
		synchronized(lock){
			this.receiveData += size;
		}		
	}
	public void clientRequestCount(long size){
		synchronized(lock){
			this.clientRequestCount += size;
		}
	}	
	public void cleanData(){
		synchronized(lock){
			this.receiveData = 0;
			this.sendData = 0;
			//this.clientRequestCount = 0;
		}
	}
}
