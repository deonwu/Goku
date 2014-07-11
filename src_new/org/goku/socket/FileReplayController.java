package org.goku.socket;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;

public class FileReplayController {
	private SocketClient client = null;
	private FileChannel channel = null;
	private MappedByteBuffer buffer = null;
	private int fileSize = 0;
	private int frameSize = 1024 * 40;
	
	public FileReplayController(SocketClient client, File path) throws IOException{
		this.client = client;
		fileSize = (int)path.length();
		channel = new FileInputStream(path).getChannel();
		buffer = channel.map(MapMode.READ_ONLY, 0, fileSize);
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

	public void seekPos(int pos){
		pos = Math.min(pos, fileSize);
		buffer.position(pos);
	}
	
	public void close() throws IOException{
		if(channel != null){
			channel.close();
		}
		if(client != null){
			client.replay = null;
			client.close();
		}
	}
}
