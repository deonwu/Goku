package org.goku.video.encoder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.goku.video.odip.VideoDestination;

public class FFMpegVideoEncoder implements VideoDestination{
	
	/**
	 * 	默认使用,输出的数据流格式。默认为mp4.
	 */ 
	public String output = "mp4";
	
	private Log log = null;
	private Process process = null;
	//private ReadableByteChannel inVideoStream = null;
	private boolean isClosed = false;
	private long lastOutputStream = System.currentTimeMillis();
	
	/**
	 * 管道的错误输出流。
	 */
	//private ReadableByteChannel errStream = null;
	private WritableByteChannel outVideoStream = null;
	
	private ReentrantLock writePipeLock = new ReentrantLock();
	
	/**
	 * 视频通道.
	 */
	private int channel;
	//private ThreadPoolExecutor executor = null;
	private Collection<VideoDestination> destList = Collections.synchronizedCollection(new ArrayList<VideoDestination>());
	
	public FFMpegVideoEncoder(String uuid, int channel){
		this(uuid, channel, null);
	}
	
	
	public FFMpegVideoEncoder(String uuid, int channel, String output){
		this.channel = channel;
		if(output != null){
			this.output = output;
		}
		log = LogFactory.getLog("encoder." + uuid + "." + channel + "." + output);
	}
	
	/**
	 * @param selector
	 */
	public void start(Collection<Pollable> pool, String shell){
		String[] exec = new String[]{
				shell,
				"-f", "h264", "-i", "pipe:",
				"-f", this.output, "pipe:"
				};
		String cmds = "";
		for(String c:exec) cmds += c + " ";
		try {
			log.debug("Start video encoder:" + cmds);
			process = Runtime.getRuntime().exec(exec);
		} catch (IOException e) {
			log.error("Command:" + cmds + ", Error:" + e.toString(), e);
		}
		if(process != null){
			//inVideoStream = Channels.newChannel(process.getInputStream());
			outVideoStream = Channels.newChannel(process.getOutputStream());
			pool.add(new OutputVideoReader(process.getInputStream()));
			pool.add(new VideoErrorReader(process.getErrorStream()));
		}
		//process.
	}
	
	public void addVideoDestination(VideoDestination dest){
		synchronized(this.destList){
			if(!this.destList.contains(dest)){
				this.destList.add(dest);
			}
		}
	}

	@Override
	public boolean accept(int sourceType, int channel) {
		return this.channel == channel;
	}

	
	/**
	 * 将接收到的视频流写到FFMpeg管道。可能回导致输入阻塞，因为不能读到管道的空闲Buffer的大小。
	 * 如果已经有一个线程处于写阻塞状态，丢弃当前的视频包。
	 */
	@Override
	public void write(ByteBuffer data, int type, int channel)
			throws IOException {
		log.debug("write video stream to ffmpeg, size:" + data.remaining());
		if(outVideoStream != null){
			if(writePipeLock.tryLock()){
				outVideoStream.write(data);
				writePipeLock.unlock();
			}else {
				log.warn("Drop the video stream, because the ffmpeg pipe is blocking.");
			}
		}
	}
	
	public void closeEncoderWhenIdle(){
		/**
		 * 主要检测是否有太长时间没有输出任何转换后的视频流了。
		 * 有可能是ffmpeg进程出错，也有可能是所有客户端都关闭。无论如何太长时间没有输出视频流，就关闭当前encoder.
		 */
		long idleTime = System.currentTimeMillis() - this.lastOutputStream; 
		if(idleTime > 1000 * 60){
			log.debug("The ffmpeg encoder is idle long time, time out:" + idleTime);
			close();
		}
	}

	@Override
	public void close() {
		this.isClosed = true;
		if(process != null){
			this.process.destroy();			
			this.process = null;
		}
		log.debug("the ffmpeg video encoder is closed.");
	}


	@Override
	public boolean isClosed() {
		if(this.process != null){
			try{
				//如果没有结束回抛出 IllegalThreadStateException 异常。
				int err = this.process.exitValue();
				log.debug("the ffmpeg process existed, error code:" + err);
				this.isClosed = true;
				return true;
			}catch(IllegalThreadStateException e){
				return false;
			}
		}else {
			return true;
		}
	}
	
	public String toString(){
		return String.format("CH:%s, FFMpeg->%s", this.channel, this.output);
	}
	
	class OutputVideoReader implements Pollable{
		private ReentrantLock rLock = new ReentrantLock();
		public InputStream vedioStream = null;
		public byte[] buffer = new byte[64 * 1024];		
		public OutputVideoReader(InputStream in){
			this.vedioStream = in;
		}
		
		@Override
		public void run() {
			if(rLock.tryLock()){
				try{
					int len = this.vedioStream.read(buffer);
					log.debug("Read ffmpeg output stream, size:" + len);
					ByteBuffer byteBuffer = ByteBuffer.allocate(len);
					byteBuffer.put(buffer, 0, len);
					byteBuffer.flip();
					synchronized(destList){
						VideoDestination dest = null;
						for(Iterator<VideoDestination> iter = destList.iterator();
						iter.hasNext();
						){
							dest = iter.next();
							if(dest.isClosed()){
								iter.remove();
								continue;
							}
							try{
								log.debug("output clinet:" + dest.toString());
								dest.write(byteBuffer.duplicate(), 0, channel);
								//避免Encoder被关闭,只有成功的输出视频数据，才不会关闭Encoder.
								lastOutputStream = System.currentTimeMillis();
							}catch(IOException e){
								log.debug("Output video error, Error:" + e.toString());
								dest.close();
								iter.remove();
							}
						}
					}
				}catch(Throwable e){
					log.error(e.toString());
				}
				rLock.unlock();
			}
		}
		
		/**
		 * 没有其他线程在读，并且有数据可以读。
		 */
		@Override
		public boolean isReady() {
			try {
				closeEncoderWhenIdle();
				boolean isOtherThread = true;
				if(rLock.tryLock()){
					//如果可以得到锁，表示没有其他线程在操作。
					isOtherThread = false;
					rLock.unlock();
				}
				return !isOtherThread && vedioStream.available() > 0;
			} catch (IOException e) {
				log.error(e.toString());
			}
			return false;
		}

		@Override
		public boolean isClosed() {return isClosed;}
	}
	
	class VideoErrorReader implements Pollable{
		//public BufferedReader reader = null;
		private ReentrantLock rLock = new ReentrantLock();
		private InputStream in = null;
		
		public VideoErrorReader(InputStream in){
			//reader = new BufferedReader(new InputStreamReader(in));
			this.in = in;
		}
		
		@Override
		public void run() {
			if(rLock.tryLock()){
				//String output = "";
				byte[] buffer = new byte[1024];
				try{
					log.error("----read error-------len:" + in.available());
					int len = in.read(buffer);
					if(len > 0){
						log.error(new String(buffer, 0, len));
					}
				}catch(Exception e){
					log.error(e.toString(), e);
				}
				rLock.unlock();
			}
		}

		@Override
		public boolean isReady() {
			try {
				//return reader.ready();
				return in.available() > 0;
			} catch (IOException e) {
				log.error(e.toString());
			}
			return false;
		}

		@Override
		public boolean isClosed() {return isClosed;}
	}
}
