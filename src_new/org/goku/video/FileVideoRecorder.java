package org.goku.video;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Queue;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.goku.video.odip.VideoDestination;

public class FileVideoRecorder implements VideoDestination{
	private Log log = LogFactory.getLog("recorder.file");
	
	private File path = null;
	private FileOutputStream os = null;
	private int channel = 0;
	private int type = 0;
	private Queue<ByteBuffer> writeQueue = new ArrayDeque<ByteBuffer>(100); 
	private Thread writer = null;
	
	//private 
	public FileVideoRecorder(final File path, int type, int channel){
		this.path = path;
		this.type = type;
		this.channel = channel;
		writer = new Thread(){
			public void run(){
				try {
					os = new FileOutputStream(path);
					while(os != null){
						ByteBuffer data = writeQueue.poll();
						if(data == null){
							synchronized(writeQueue){
								writeQueue.wait();
							}
							continue;
						}else {
							os.getChannel().write(data);
						}
					}
				} catch (FileNotFoundException e) {
					log.error(e.toString(), e);
				} catch (InterruptedException e) {
					log.error(e.toString(), e);
				} catch (IOException e) {
					log.error(e.toString(), e);
				}
			}
		};
		writer.start();
	}

	@Override
	public boolean accept(int sourceType, int channel) {
		return this.type == sourceType;
	}

	@Override
	public void write(ByteBuffer data, int type, int channel) throws IOException {
		if(this.type == type && this.channel == channel){
			this.writeQueue.offer(data);
			synchronized(this.writeQueue){
				this.writeQueue.notifyAll();
			}
		}
	}
	
	public boolean isClosed(){
		return os == null;
	}

	@Override
	public void close() {
		if(os != null){
			try{
				os.close();
			}catch(IOException e){				
			}
			os = null;
		}
		synchronized(this.writeQueue){
			this.writeQueue.notifyAll();
		}		
	}

}
