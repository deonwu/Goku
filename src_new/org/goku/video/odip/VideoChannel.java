package org.goku.video.odip;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.goku.core.model.MonitorChannel;
import org.goku.socket.NIOSession;
import org.goku.socket.SocketListener;

public class VideoChannel implements SocketListener{
	
	public Log log = null;
	//protected SocketChannel socketChannel = null;
	public ODIPHandler handler = null;
	public MonitorClient client = null;	
	//摄像头通道号.
	public MonitorChannel channel = null;
	private Runnable startUp = null;
	public NIOSession session = null;
	
	public VideoChannel(MonitorClient client, MonitorChannel channel, Runnable startUp){
		this.client = client;
		this.log = LogFactory.getLog("node." + client.info.uuid + ".ch" + channel.id);
		this.channel = channel;
		this.startUp = startUp;
	}

	public String toString(){
		return String.format("Video channel %s<%s>.", this.client.info.uuid, this.channel.id);
	}

	@Override
	public void connected(NIOSession s) throws IOException {
		this.session = s;
		this.handler = new ODIPHandler(client, s);
		this.handler.videoStreamAuth(this.channel.id);
		this.handler.isVideoChannel = true;
		this.startUp.run();
	}

	@Override
	public void connect_error(NIOSession s) throws IOException {
		
	}

	@Override
	public void timeout(NIOSession s) throws IOException {
		this.channel.videoChannel = null;
		
	}

	@Override
	public void disconnected(NIOSession s) throws IOException {
		this.channel.videoChannel = null;
	}

	@Override
	public void read(NIOSession s) throws IOException {
		ByteBuffer buffer = null;
		int odipCount = 0;
		long st = System.currentTimeMillis();
		while(odipCount < 10){	//最多一次处理10个OIDP协议包就开始切换, 如果服务器没有性能问题，缓冲区应该低于3个ODIP包。
			odipCount++;
			buffer = handler.getDataBuffer(); //ByteBuffer.allocate(1024 * 64);			
			if(!buffer.hasRemaining()){
				this.handler.processData();
			}else {	//数据处理完成。
				break;
			}
		}
		
		if(odipCount > 5 || System.currentTimeMillis() - st > 5){
			log.warn(String.format("Route server too slow, have too many buffer waiting process. once process spend %s ms, %s odip in buffer.", 
					System.currentTimeMillis() - st, odipCount));
		}
	};
	
	public void close(){
		if(this.session != null){
			this.session.close();
		}		
	}

	@Override
	public void active(NIOSession s) throws IOException {
		
	}
}
