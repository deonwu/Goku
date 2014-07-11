package org.goku.video.odip;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;

import org.apache.commons.logging.LogFactory;
import org.goku.core.model.MonitorChannel;

public class DownloadChannel extends VideoChannel {
	public ByteBuffer buffer = ByteBuffer.allocate(1024 * 64);
	public ProtocolHeader protoHeader = new ProtocolHeader();
	protected long downSize = 0;
	
	private OutputStream os = null;
	protected WritableByteChannel writeChannel = null;
	protected RecordFileInfo file = null;
	
	public DownloadChannel(MonitorClient client,  RecordFileInfo file, Runnable startUp, OutputStream os){
		super(client, null, startUp);
		log = LogFactory.getLog("node." + client.info.uuid + ".down");
		writeChannel = Channels.newChannel(os);
		this.os = os;
		this.file = file;
	}
	
	protected synchronized void initVideoAuth(){
		this.handler.videoStreamAuth(this.file.channel, 2);
		this.notifyAll();
		//log.info("xxxxxx:");
	}
	
	/**
	 * 读Socket缓冲区并处理
	 * @param channel
	 * @throws IOException
	 */
	protected void read2(SocketChannel channel) throws IOException{
		if(protoHeader.cmd == 0){
			buffer.clear();
			buffer.limit(32);
			this.read(buffer);
			buffer.flip();			
			protoHeader.loadBuffer(buffer);
			log.warn(String.format("Process ODIP '0x%x'.", protoHeader.cmd));
			log.debug("Start downloading size:" + protoHeader.externalLength);
		}
		
		while(downSize < protoHeader.externalLength){
			buffer.clear();
			this.read(buffer);
			buffer.flip();
			if(!buffer.hasRemaining())break;
			
			while(buffer.hasRemaining()){
				downSize += this.writeChannel.write(buffer);
				if(!buffer.hasRemaining())break;
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
				}
			}
		}
		log.debug("downloading size:" + this.downSize);
		if(downSize >= protoHeader.externalLength){
			log.debug("download done");
			this.writeChannel.close();
			//this.writeChannel = null;
			this.reconnectSocketChannel();
		}else if(this.selectionKey == null){
			this.writeChannel.close();
			//this.writeChannel = null;
			log.debug("download error at offset " + downSize);			
		}
		if(!this.writeChannel.isOpen()){
			synchronized(this.os){
				this.os.notifyAll();
			}
		}
	}
	
	/*
	public void reconnectSocketChannel() throws IOException{
		log.info("Close " + this.toString());
		if(this.writeChannel != null){
			this.writeChannel.close();
			this.writeChannel = null;
		}
		if(this.selectionKey != null){
			this.selectionKey.channel().close();
			this.selectionKey.cancel();
			//this.selectionKey = null;
		}
		//this.client.handler.stopPlayVideo(this.file.channel);
	}*/
	
	public void closeVideoChannel() throws IOException{
		log.info("Close " + this.toString());
		if(this.writeChannel != null){
			this.writeChannel.close();
			this.writeChannel = null;
		}
		if(this.selectionKey != null){
			this.selectionKey.channel().close();
			this.selectionKey.cancel();
			//this.selectionKey = null;
		}
		this.client.handler.stopPlayVideo(this.file.channel);	
	}	
	
	/**
	 * 保存数据流，不是些回Socket.
	 */
	public void saveVideoStream(ByteBuffer src) {
		while(src.hasRemaining()){
			try {
				this.downSize += this.writeChannel.write(src);
				if(!src.hasRemaining())break;
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
				}
			} catch (IOException e) {
				break;
			}
		}
		log.debug("downloading size:" + this.downSize);
		//if(this.downSize)
	}	
	
	public String toString(){
		return String.format("Download channel %s<%s>", this.client.info.uuid, this.file.channel);
	};		

}
