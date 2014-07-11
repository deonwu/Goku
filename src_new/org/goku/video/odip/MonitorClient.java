package org.goku.video.odip;

import static org.goku.video.odip.Constant.CTRL_VIDEO_START;
import static org.goku.video.odip.Constant.CTRL_VIDEO_STOP;

import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Queue;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.goku.core.model.BaseStation;
import org.goku.core.model.MonitorChannel;
import org.goku.core.model.RouteRunningStatus;
import org.goku.socket.NIOSession;
import org.goku.socket.SocketListener;
import org.goku.socket.SocketManager;

/**
 * 监控客户端，处理于摄像头的交互。
 * @author deon
 */
public class MonitorClient implements SocketListener{
	public BaseStation info = null;
	public Collection<MonitorClientListener> ls = Collections.synchronizedCollection(new ArrayList<MonitorClientListener>());
	
	public VideoRoute route = null;
	public RouteRunningStatus runningStatus = new RouteRunningStatus();
	
	public String ipAddr = "";	
	protected Log log = null;
	/**
	 * 网络连接的操作对象，可以得到SocketChannel.
	 */
	protected SocketManager socketManager = null;
	protected ClientStatus status = null;
	protected NIOSession session = null;
	
	/**
	 * 当前处理Client事件的对象，类似一个状态机。某一个时刻，只能有一个状态。
	 */
	protected ODIPHandler handler = null;

	
	public MonitorClient(BaseStation info, VideoRoute route, SocketManager socketManager){
		this.info = info;
		log = LogFactory.getLog("node." + info.uuid);
		
		this.route = route;
		this.route.setLogger(log);
		this.route.start(this);

		this.socketManager = socketManager;
		//this.handler = new ODIPHandler(this, this);		
	}
	
	/** 
	 * @param selector
	 */
	public void connect() throws IOException{
		String[] address = this.info.locationId.split(":");
		if(address.length < 2){
			throw new IOException("Invalid location Id, <host>:<port>");
		}
		//this.channelId = Integer.parseInt(address[2]);
		this.ipAddr = address[0] + ":" + address[1];
		
		session = new NIOSession(this, this.log);
		SocketChannel socket = this.socketManager.connect(address[0], Integer.parseInt(address[1]),
								   session);		
		socket.socket().setReceiveBufferSize(1024 * 64);
	}
	
	public void sendAlarmRequest(){
		this.handler.getAlarmStatus_A1(0);
	}
	
	/**
	 * 登录系统。
	 */
	public void login(boolean sync){
		if(this.getClientStatus() == null){
			if(this.session == null || !this.session.isRunning){
				try {
					this.connect();
					synchronized(this.session){
						try {
							this.session.wait(30 * 1000);
						} catch (InterruptedException e) {
						}
					}
				} catch (IOException e) {
					log.warn("Connection error, error:" + e.toString());
				}
			}
			
			//在多线程时，有可能在等待期间由其他线程完成了login操作。
			if(this.getClientStatus() == null &&  
					this.session != null &&
					this.session.isRunning){
				this.handler.login("", "", sync);
			}else if(this.getClientStatus() == null){
				log.debug("The socket channel isn't OK, can't login to client.");
			}
		}
	}
	
	/**
	 * 开启实时监控。
	 * @param player 收实时监控视频数据。
	 */
	public void realPlay(int channel){
		this.realPlay(channel, MonitorChannel.MAIN_VIDEO);
	}
	
	public void realPlay(final int channel, final int mode){
		if(log.isDebugEnabled()){
			log.debug("realPlay:" + channel + ", mode:" + mode);
		}
		this.login(true);
		if(this.getClientStatus() != null){
			MonitorChannel ch = info.getChannel(channel);
			if(ch == null){
				log.warn("Not found chanel id:" + channel);
			}else {
				if(ch.videoChannel == null){
					log.debug("Create video channel for " + this.toString() + ", ch:" + channel);
					
					ch.videoChannel = new VideoChannel(this, ch,
							//连接成功后的回调方法。
							new Runnable(){
								public void run() {
									handler.videoStreamControl(CTRL_VIDEO_START, channel, mode);
								}
							});
					String[] address = this.ipAddr.split(":");					
					NIOSession s = new NIOSession(ch.videoChannel, ch.videoChannel.log);					
					this.socketManager.connect(address[0],
											Integer.parseInt(address[1]),
											s);
				}else {
					this.handler.videoStreamControl(CTRL_VIDEO_START, channel, mode);
				}
			}
		}else {
			log.warn("Can't open real play in disconnected client.");
		}		
	}
	
	public void openSound(){
		
	}

	/**
	 * 初始化设备状态, 包括时间， 硬盘查询， 通道同步等。
	 */	
	public void initMonitorClientStatus(){
		/*
		for(int i = 1; i < 5; i++){
			this.handler.videoStreamAuth(i);
		}*/		
	}
	
	/**
	 * 查询回放记录。
	 */
	public void queryRecordFile(){
		
	}
	
	public void downloadByRecordFile(){
		
	}
	
	/**
	 * 当视频接受端为空时调用。发送关闭视频流的命令。
	 */
	public void videoDestinationEmpty(int channel, int mode){
		this.handler.videoStreamControl(CTRL_VIDEO_STOP, channel, mode);
		
		/**
		 * 关闭视频数据通道。
		 */
		MonitorChannel ch = info.getChannel(channel);
		if(ch != null && ch.videoChannel != null){
			ch.videoChannel.close();
			ch.videoChannel = null;
		}
	}

	public void close(){
		this.session.close();
	}
	
	public void addListener(MonitorClientListener l){
		if(! this.ls.contains(l)){
			this.ls.add(l);
		}
	}
	
	public void removeListener(MonitorClientListener l){
		if(this.ls.contains(l)){
			this.ls.remove(l);
		}
	}
	
	public MonitorClientListener eventProxy = new MonitorClientListener(){

		@Override
		public void connected(MonitorClientEvent event) {
			for(MonitorClientListener l: ls){
				l.connected(event);
			}
		}

		@Override
		public void disconnected(MonitorClientEvent event) {
			for(MonitorClientListener l: ls){
				l.disconnected(event);
			}
		}
		
		@Override
		public void timeout(MonitorClientEvent event) {
			for(MonitorClientListener l: ls){
				l.timeout(event);
			}
		}
		
		@Override
		public void alarm(MonitorClientEvent event) {
			for(MonitorClientListener l: ls){
				l.alarm(event);
			}
		}	
		
		@Override
		public void writeIOException(MonitorClientEvent event) {
			for(MonitorClientListener l: ls){
				l.writeIOException(event);
			}
		}		
		
		@Override
		public void loginError(MonitorClientEvent event) {
			for(MonitorClientListener l: ls){
				l.loginError(event);
			}
		}	
		
		@Override
		public void loginOK(MonitorClientEvent event) {
			for(MonitorClientListener l: ls){
				l.loginOK(event);
			}
		}			
	};

	public void setClientStatus(ClientStatus status){
		this.status = status;
		if(status != null){
			this.status.lastActionTime = System.currentTimeMillis();
			this.status.lastActiveTime = System.currentTimeMillis();
		}
	}
	
	public ClientStatus getClientStatus(){
		return this.status;
	}	

	public String toString(){
		return "DVR " + this.info.uuid;
	}

	@Override
	public void connected(NIOSession s) throws IOException {
		this.handler = new ODIPHandler(this, s);
		this.handler.isVideoChannel = false;
		this.eventProxy.connected(new MonitorClientEvent(this));
	}

	@Override
	public void connect_error(NIOSession s) throws IOException {
		this.eventProxy.timeout(new MonitorClientEvent(this));		
	}

	@Override
	public void timeout(NIOSession s) throws IOException {
		this.setClientStatus(null);
		this.eventProxy.timeout(new MonitorClientEvent(this));		
	}

	@Override
	public void disconnected(NIOSession s) throws IOException {
		this.setClientStatus(null);
		this.eventProxy.disconnected(new MonitorClientEvent(this));		
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
	}

	/**
	 * 发送响应消息，避免被超时断开连接。
	 */	
	@Override
	public void active(NIOSession s) throws IOException {
		this.handler.getAlarmStatus_A1(0);		
	};
}
