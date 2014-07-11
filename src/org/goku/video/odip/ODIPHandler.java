package org.goku.video.odip;

import static org.goku.video.odip.ProtocolHeader.ACK_GET_ALARM;
import static org.goku.video.odip.ProtocolHeader.ACK_GET_VIDEO;
import static org.goku.video.odip.ProtocolHeader.ACK_LOGIN;
import static org.goku.video.odip.ProtocolHeader.CMD_LOGIN;
import static org.goku.video.odip.ProtocolHeader.ACK_DIR_FILE;
import static org.goku.video.odip.ProtocolHeader.ACK_DOWNLOAD_VIDEO;
import static org.goku.video.odip.ProtocolHeader.ACK_DEV_INFO;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.goku.core.model.AlarmDefine;
import org.goku.socket.NIOSocketChannel;

/**
 * 处理视频协议数据，
 * 
 * 登录流程：
 * 1. 0xA0 --->          登录
 * 2.     <--- 0xB0     应答登录
 * 
 * 实时监控
 * 1. 0xf1 --->          请求实时视频  //建立认证关系。
 *         <--- 0xF1     ????协议中要求返回，抓包分析发现未返回。
 *    0x11 --->          请求媒体数据 ???什么场景使用 //登录后使用
 * 4.      <--- 0xBC     视频数据
 * 
 * 设备信息查询：
 * 1. 0xA4 --->          查询设备信息, 01(基本信息), 07(设备ID)
 *         <--- 0xB4     应答
 * ---
 *    0xA8 --->          ??? 什么应用场景
 *         <--- 0xB8
 * --- 配置参数
 *    0xA3 --->          
 *         
 * 告警查询：
 * 1. 0xA1 --->
 *         <--- 0xB1     告警状态
 *         
 * 文件查询下载：
 * 1. 0xA5H --->         查询文件信息        
 *          <--- 0xB6H   返回文件信息
 * @author deon
 */
public class ODIPHandler {
	public ByteBuffer buffer = ByteBuffer.allocate(1024 * 64);
	public ProtocolHeader protoHeader = new ProtocolHeader();
	private Log log = null; //#LogFactory.getLog(");
	
	/**
	 * 阻塞Login方法，等待应答消息.
	 */
	private Object lockLogin = new Object();
	
	/**
	 * 查询文件返回结果。
	 */
	private Collection<RecordFileInfo> recordFile = null;
	/**
	 * 当前查询通道，和recordFile 使用相同的临界区域。
	 */
	private int currentDirFileChannel = 0;
	
	private int loginStatus = -2;
	
	private MonitorClient client = null;
	private NIOSocketChannel socket = null;
	public boolean isVideoChannel = System.getenv("SVC") != null;
	//private SelectionHandler
	
	//用来缓存相同消息的上次读到的时间，避免输出大量的相同消息log.
	private Map<Byte, Long> cmdDebug = new HashMap<Byte, Long>();
	private long lastAlarmTime = 0;
	
	public ODIPHandler(MonitorClient client, NIOSocketChannel socket){
		this.client = client;
		this.log = LogFactory.getLog("node." + client.info.uuid + ".odip");
		this.resetBuffer();
		this.socket = socket;
	}
	
	public ByteBuffer getDataBuffer(){
		return buffer;
	}
	
	public void processData() throws IOException{
		//this.buffer.flip();
		
		if(protoHeader.cmd == 0){
			this.buffer.flip();
			protoHeader.loadBuffer(this.buffer);
			//可能是协议乱序或其他原因，读到了错误的协议头。
			if(!protoHeader.supportCommand()) throw new IOException(String.format("unknown ODIP message, cmd=0x%x", protoHeader.cmd));
			int extLen = this.protoHeader.externalLength;
			if(log.isDebugEnabled()){
				if(!cmdDebug.containsKey(protoHeader.cmd) ||
				   System.currentTimeMillis() - cmdDebug.get(protoHeader.cmd) > 5000
				  ){
					cmdDebug.put(protoHeader.cmd, System.currentTimeMillis());
					log.debug(String.format("cmd=0x%x, ext len=%s, source='%s'", 
							protoHeader.cmd, extLen, this.socket.toString()));
				}
			}
			
			if(extLen > 0){
				this.buffer.clear();
				this.buffer.limit(extLen);
				socket.read(buffer);
			}else {
				buffer.limit(0);
			}
		}
		
		//ODIP协议头和扩展数据都读完成，开始处理协议信息。
		if(protoHeader.cmd != 0 && !buffer.hasRemaining()){
			buffer.flip();
			long st = System.currentTimeMillis();
			this.processODIPMessage(protoHeader, buffer);
			st = System.currentTimeMillis() - st;
			if(st > 3){
				log.warn(String.format("ODIP '0x%x' is processed %s ms.", protoHeader.cmd, st));
			}
			//处理完成后，重新开始读协议头。
			this.resetBuffer();
		}else {
			//log.debug("waiting data remaining buffer:" + buffer.remaining());
		}
	}
	
	public void processODIPMessage(ProtocolHeader header, ByteBuffer buffer){
		//int unsignedBypte = header.cmd >= 0 ? header.cmd : header.cmd + 256;
		switch(header.cmd){
			case ACK_LOGIN:
				this.ackLogin(header, buffer);
				break;
			case ACK_GET_VIDEO:
				//如果不是视通流通道，不处理视频。
				if (isVideoChannel)this.ackVideoData(header, buffer);
				break;
			case ACK_GET_ALARM:
				this.ackAlarmStatus_B1(header, buffer);
				break;
			case ACK_DIR_FILE:
				this.ackFileList(header, buffer);
				break;
			case ACK_DOWNLOAD_VIDEO:
				this.ackDownVideo(header, buffer);
				break;
			case ACK_DEV_INFO:
				this.ackDevInfo(header, buffer);
				break;
			default:
				log.warn(String.format("Not found handler for command:0x%x", header.cmd));
		}
	}
	
	public void resetBuffer(){
		this.protoHeader.cmd = 0;
		this.buffer.clear();
		this.buffer.limit(32);
	}
	
	public int login(String user, String password){
		return this.login(user, password, true);
	}
	/**
	 * 登录系统，等待回应后返回。
	 * @param user
	 * @param password
	 * @return -- 成功返回-1, 失败返回0-7的错误码。
	 */
	public int login(String user, String password, boolean sync){
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		ProtocolHeader header = new ProtocolHeader();
		header.cmd = CMD_LOGIN;
		header.setByte(26, (byte)1);
		header.setByte(27, (byte)1);
		
		byte[] extra = this.encode(user, password);
		header.externalLength = extra.length;
		
		header.mapToBuffer(buffer);
		buffer.put(extra);
		
		buffer.flip();
		socket.write(buffer, false);
		
		log.info("Login to " + client.info.locationId);
		
		if(sync) {
			synchronized(this.lockLogin){
				try {
					lockLogin.wait(1000 * 10);
				} catch (InterruptedException e) {
					log.info("InterruptedException, lock Login");
				}
			}
			if(this.loginStatus == -2){
				log.warn("Time out wait login response.");
			}
			return this.loginStatus;
		}else {
			return -2;
		}
		
	}
	
	public void videoStreamAuth(int channelId){
		videoStreamAuth(channelId, 1);
	}
	/**
	 * 0xf1
	 * @param action 1--开始监控， 0--关闭监控。
	 */
	public void videoStreamAuth(int channelId, int type){
		if(this.client.getClientStatus() != null &&
		   this.client.info.getChannel(channelId) != null){
			//this.client.getClientStatus().sessionId
			ByteBuffer buffer = ByteBuffer.allocate(64);
			ProtocolHeader header = new ProtocolHeader();
			header.cmd = ProtocolHeader.CMD_CONNECT;
			header.externalLength = 0;
			header.version = 0;
			//header.setInt(8, this.client.getClientStatus().sessionId);
			header.setLong(8, this.client.getClientStatus().sessionId);
			header.setInt(12, type);
			header.setByte(13, (byte)channelId);
			
			header.mapToBuffer(buffer);			
			buffer.position(32);
			buffer.flip();
			socket.write(buffer, false);
			log.info("Connect to video:" + channelId + ", sid:" + this.client.getClientStatus().sessionId);
		}
	}
	/**
	 * 0x11
	 * @param action 1--开始监控， 0--关闭监控。
	 */
	public void videoStreamControl(int action, int channelId, int mode){
		if(this.client.getClientStatus() == null)
			throw new UnsupportedOperationException("Can't connection before login.");
		//this.videoStreamAuth(channelId);
		
		log.info("videoStreamControl:" + action + ", channel:" + channelId);
		ByteBuffer buffer = ByteBuffer.allocate(64);
		ProtocolHeader header = new ProtocolHeader();
		header.cmd = ProtocolHeader.CMD_GET_VIDEO;
		header.externalLength = 16;
		header.version = 0;
		
		//header.setByte(this.client.channelId + 7, (byte)1);
		//只修改指定通道，其他通道不影响。
		for(int i = 1; i <= this.client.getClientStatus().channelCount; i++){
			if(i == channelId){
				header.setByte(i + 7, (byte)action);
			}else {
				header.setByte(i + 7, (byte)2);
			}
		}
		
		header.mapToBuffer(buffer);

		buffer.position(32 + 16);
		buffer.flip();
		socket.write(buffer, false);
		
		if(this.client.info.getChannel(channelId) != null){
			this.client.info.getChannel(channelId).videoStatus(mode, action == Constant.CTRL_VIDEO_START);
		}
	}
	
	/**
	 * 0xa1
	 * @param action 
	 */	
	public void getAlarmStatus_A1(int type){
		ByteBuffer buffer = ByteBuffer.allocate(64);
		ProtocolHeader header = new ProtocolHeader();
		header.cmd = ProtocolHeader.CMD_GET_ALARM;
		header.version = 0;
		
		header.mapToBuffer(buffer);

		buffer.position(32);
		buffer.flip();
		socket.write(buffer, false);
	}
	
	/**
	 * 0xa4
	 * @param action 
	 */	
	public void getDevStatus(int type){
		ByteBuffer buffer = ByteBuffer.allocate(64);
		ProtocolHeader header = new ProtocolHeader();
		header.cmd = (byte)0xA4; //ProtocolHeader.CMD_GET_ALARM;
		header.version = 0;
		header.setByte(8, (byte)type);
		
		header.mapToBuffer(buffer);

		buffer.position(32);
		buffer.flip();
		socket.write(buffer, false);		
	}
	
	/**
	 * 停止某个通道的视频传输。
	 * @param header
	 * @param buffer
	 */	
	public void stopPlayVideo(int channel){
		ByteBuffer buffer = ByteBuffer.allocate(64);
		ProtocolHeader header = new ProtocolHeader();
		header.cmd = (byte)0xc9;
		header.version = 0;
		header.setByte(8, (byte)channel);		
		header.mapToBuffer(buffer);

		buffer.position(32);
		buffer.flip();
		socket.write(buffer, false);
		
	}
	
	public void downLoadFile(RecordFileInfo file){
		ByteBuffer buffer = ByteBuffer.allocate(64);
		ProtocolHeader header = new ProtocolHeader();
		header.cmd = (byte)0xcb;
		header.version = 0;
		header.setByte(8, (byte)file.channel);
		
		Calendar time = Calendar.getInstance();
		time.setTimeInMillis(file.startTime.getTime());
		
		int d =  time.get(Calendar.YEAR);	
		header.setByte(9, (byte)(d & 0xff));
		header.setByte(10, (byte)(d >> 8 & 0xff));
		
		header.setByte(11, (byte)(time.get(Calendar.MONTH) + 1));
		header.setByte(12, (byte)(time.get(Calendar.DAY_OF_MONTH)));
		header.setByte(13, (byte)time.get(Calendar.HOUR_OF_DAY));
		header.setByte(14, (byte)time.get(Calendar.MINUTE));
		header.setByte(15, (byte)time.get(Calendar.SECOND));		
		header.setByte(16, (byte)file.dirveNo);
		header.setLong(17, file.startCluster);
		
		header.mapToBuffer(buffer);
		buffer.position(32);
		buffer.flip();
		socket.write(buffer, false);
	}	
	
	/**
	 * 查询硬盘文件列表, 只能单线程调用。因为需要等待返回结果。
	 * @param header
	 * @param buffer
	 */	
	public Collection<RecordFileInfo> listRecordFile(int channel, int type, Date start){
		ByteBuffer buffer = ByteBuffer.allocate(64);
		ProtocolHeader header = new ProtocolHeader();
		header.cmd = ProtocolHeader.CMD_DIR_FILE;
		header.version = 0;
		header.setByte(8, (byte)channel);
			
		Calendar time = Calendar.getInstance();
		time.setTimeInMillis(start.getTime());
		
		int d =  time.get(Calendar.YEAR);		
		header.setByte(9, (byte)(d & 0xff));
		header.setByte(10, (byte)(d >> 8 & 0xff));
		
		header.setByte(11, (byte)(time.get(Calendar.MONTH) + 1));
		header.setByte(12, (byte)(time.get(Calendar.DAY_OF_MONTH)));
		header.setByte(13, (byte)time.get(Calendar.HOUR_OF_DAY));
		header.setByte(14, (byte)time.get(Calendar.MINUTE));
		header.setByte(15, (byte)time.get(Calendar.SECOND));

		//header.setByte(13, (byte)0);
		//header.setByte(14, (byte)0);
		//header.setByte(15, (byte)0);
		
		
		header.setByte(16, (byte)type);
		
		header.mapToBuffer(buffer);

		buffer.position(32);
		buffer.flip();
		socket.write(buffer, false);
		
		Collection<RecordFileInfo> recordFile = null;
		//避免多个线程在用一个连接上调用查询接口。
		synchronized(lockLogin){
			this.recordFile = new ArrayList<RecordFileInfo>(16);
			this.currentDirFileChannel = channel;
			synchronized(this.recordFile){
				try {
					this.recordFile.wait(1000 * 5);
				} catch (InterruptedException e) {
				}
			}
			recordFile = this.recordFile; 
		}
		return recordFile;
	}
	
	public void ackDownVideo(ProtocolHeader header, ByteBuffer buffer){
		//int channel = header.getByte(8) + 1;
		//5 -- 副码流1
		//6 -- 副码流2
		//int type = header.getByte(24);
		//this.client.route.route(buffer, 20, channel);
		if(socket instanceof DownloadChannel){
			((DownloadChannel)socket).saveVideoStream(buffer);
			if(header.externalLength == 0){
				try {
					((DownloadChannel)socket).closeVideoChannel();
				} catch (IOException e) {
					log.error(e.toString(), e);
				}
			}
		}
	}
	
	public void ackFileList(ProtocolHeader header, ByteBuffer buffer){
		RecordFileInfo file = null;
		if(recordFile != null){
			//每个文件记录大小为24字节。
			while(buffer.remaining() >= 24){
				file = new RecordFileInfo();
				file.decode(buffer);
				if(log.isDebugEnabled()){
					log.debug("decode file:" + file.toString());
				}
				recordFile.add(file);
				file.channel = this.currentDirFileChannel;
			}
			synchronized(recordFile){
				recordFile.notifyAll();
			}				
		}
	}
	
	public void ackAlarmStatus_B1(ProtocolHeader header, ByteBuffer buffer){
		if(header.getByte(8) == 0){
			byte status = header.getByte(13);
			Collection<AlarmDefine> alarms = new ArrayList<AlarmDefine>();
			
			AlarmDefine alarm = null;
			if((status & 1) == 0){ //外部报警
				alarm = AlarmDefine.alarm(AlarmDefine.AL_1001);
				alarm.fillAlarmChannels(header.getInt(16));
				alarms.add(alarm);
			}
			if((status & 2) == 0){ //视频丢失
				alarm = AlarmDefine.alarm(AlarmDefine.AL_1002);
				alarm.fillAlarmChannels(header.getInt(20));
				alarms.add(alarm);				
			}
			if((status & 4) == 0){ //动检报警
				alarm = AlarmDefine.alarm(AlarmDefine.AL_1003);
				alarm.fillAlarmChannels(header.getInt(24));
				alarms.add(alarm);				
			}
			if((status & 8) == 0){ //第三方报警				
			}
			if((status & 16) == 0){ //串口故障报警	
			}
			
			/**
			 * 超过3分钟没有任何告警，也需要触发一个空的告警。用来更新中心服务起状态，
			 * 避免管理服务器，认为设备超时。
			 */
			if(alarms.size() > 0 || 
			   System.currentTimeMillis() - lastAlarmTime > 60 * 1000 * 3
			  ){
				lastAlarmTime = System.currentTimeMillis();
				MonitorClientEvent event = new MonitorClientEvent(client);
				event.alarms = alarms;
				client.eventProxy.alarm(event);
			}
		}else {
			log.warn(String.format("Not supported alarm ack: 0x%x", header.getByte(8)));
		}
	}
	
	protected void ackDevInfo(ProtocolHeader header, ByteBuffer buffer){
		//主要用来检查硬盘数量。
		byte type = header.getByte(8);
		if(type == 0){
			//没有找到工作的硬盘。
			byte drive_num = buffer.get();
			if(drive_num == 0){
				Collection<AlarmDefine> alarms = new ArrayList<AlarmDefine>();
				alarms.add(AlarmDefine.alarm(AlarmDefine.AL_1004));
				MonitorClientEvent event = new MonitorClientEvent(client);
				event.alarms = alarms;
				client.eventProxy.alarm(event);	
			}else {
				log.debug("working driver number:" + drive_num);
			}
		}else {
			log.debug(String.format("Ingore unknown dev info: 0x%x", type));
		}		
	}
	//ACK_DEV_INFO
	
	/*
	public void login(String user, String password){
		
	}*/
	
	/**
49:75:59:74: 31:70:4a:53:
44:51:45:3d: 26:26:32:55:
67:32:52:59: 67:34:74:58:
30:3d
	 * @param user
	 * @param password
	 * @return
	 */
	private byte[] encode(String user, String password){
		/*
		return new byte[]{0x49, 0x75, 0x74, 0x31, 0x31, 0x70, 0x41, 0x53,
						  0x44, 0x51, 0x45, 0x3d, 0x26, 0x26, 0x32, 0x55,
						  0x67, 0x32, 0x52, 0x59, 0x67, 0x34, 0x74, 0x58,
						  0x30, 0x3d
						  };
						  */
		return new byte[]{0x49, 0x75, 0x59, 0x74, 0x31, 0x70, 0x4a, 0x53,
						  0x44, 0x51, 0x45, 0x3d, 0x26, 0x26, 0x32, 0x55,
						  0x67, 0x32, 0x52, 0x59, 0x67, 0x34, 0x74, 0x58,
						  0x30, 0x3d
				  };		
	}
	
	protected void ackLogin(ProtocolHeader header, ByteBuffer buffer){
		if(header.getByte(8) != 0){
			log.info("Failed to login, error code:" + header.getByte(9));
			MonitorClientEvent event = new MonitorClientEvent(client);
			event.header = header;
			client.eventProxy.loginError(event);
			this.loginStatus = header.getByte(9);
		}else {
			ClientStatus status = new ClientStatus();
			status.channelCount = header.getByte(10);
			status.videoType = header.getByte(11);
			status.devType = header.getByte(12);
			status.sessionId = header.getUnsignedInt(16);
			status.devMode = header.getByte(28);
			
			this.client.setClientStatus(status);
			
			log.info(String.format("Login successful, channel count:%s, videoType:%s" +
					", devType:%s, sessionId:%s, devMode:%s",
					status.channelCount, 
					status.videoTypeName(),
					status.devTypeName(), 
					status.sessionId, 
					status.devModeName()));
			
			client.eventProxy.loginOK(new MonitorClientEvent(client));
			this.loginStatus = -1;
		}
		synchronized(this.lockLogin){
			this.lockLogin.notifyAll();
		}
	}
	
	protected void ackVideoData(ProtocolHeader header, ByteBuffer buffer){
		
		int channel = header.getByte(8) + 1;

		//5 -- 副码流1
		//6 -- 副码流2
		int type = header.getByte(24);
		this.client.route.route(buffer, 0, channel);
	}
}
