package org.goku.socket;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;

public class FileReplayController {
	private SocketClient client = null;
	private FileChannel channel = null;
	private MappedByteBuffer buffer = null;
	private int fileSize = 0;
	private int frameSize = 1024 * 16;
	private File path = null;
	
	public FileReplayController(SocketClient client, File path){
		this.client = client;
		fileSize = (int)path.length();
		this.path = path;
	}
	
	public boolean openFile() throws IOException{
		if(path.isFile()){
			channel = new FileInputStream(path).getChannel();
			buffer = channel.map(MapMode.READ_ONLY, 0, fileSize);
			return true;
		}else {
			return false;
		}
	}
	
	public void nextFrame() throws IOException{
		if(!client.writeBusy()){
			int nextPos = buffer.position();
			nextPos = Math.min(nextPos + frameSize, fileSize);
			buffer.limit(nextPos);
			client.write(buffer, false);
			if(nextPos >= fileSize){
				this.close();
			}
		}
	}

	public void seekPos(int pos, boolean relative){
		if (relative) pos = buffer.position() + pos;
		pos = Math.min(pos, fileSize);
		if(pos < 0)pos = 0;
		
		int nextPos = Math.min(pos + frameSize, fileSize);
		buffer.limit(nextPos);
		buffer.position(pos);
	}
	
	public void seekLast(int pos){
		pos = fileSize - pos;
		if(pos < 0)pos = 0;
		buffer.position(pos);
	}
	
	public void close() throws IOException{
		//等1分钟写队列，
		for(int i = 0; i < 60; i++){
			if(client.writeQueue.size() > 0){
				synchronized(client.writeQueue){
					try {
						client.writeQueue.wait(1000);
					} catch (InterruptedException e) {
					}
				}
			}else {
				break;
			}
		}
		
		if(channel != null){
			channel.close();
		}
		if(client != null){
			client.replay = null;
			client.close();
		}
	}
}
