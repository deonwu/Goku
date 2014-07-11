package org.goku.image;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.goku.core.model.AlarmRecord;
import org.goku.core.model.SimpleCache;
import org.goku.socket.SocketAdaptor;
import org.goku.socket.SocketClient;

public class ImageSocketAdaptor implements SocketAdaptor{
	public SimpleCache sessionCache = new SimpleCache();
	
	protected DateFormat format= new SimpleDateFormat("yyyy-MM-dd HH-mm-ss");

	private Log log = LogFactory.getLog("client.socket.image");
	private ImageRouteServer server = null;
	private int sessionId = 1;
	
	/**
	 * mode-- all, one, block, unblock
	 * alarm_image?alarmId=001&baseStation=001&status=0&last=100&mode=all&encode=base64
	 * 
	 * real_image?baseStation=001&channel=1&mode=block&encode=base64
	 * 
	 * next_image?sid=1001&encode=base64
	 * 
	 * 0$command$sid$
	 */
	public void runCommand(String command, SocketClient client) throws IOException{
		if(server == null){
			server = ImageRouteServer.getInstance();
		}
		log.info("Read image socket command:" + command);
		Map<String, String> param = parseCommand(command);
		if(!param.containsKey("encode")){
			param.put("encode", "base64");
		}
		if(!param.containsKey("mode")){
			param.put("mode", "one");
		}		
		
		String cmd = param.get("q");
		OutputWriter out = new OutputWriter(client);
		if(cmd.equals("alarm_image")){
			alarmImage(client, out, param.get("alarmId"), 
					param.get("baseStation"), param.get("channel"),
					param.get("last"), 
					param.get("status"),
					param.get("mode"),
					param.get("encode")
					);
		}else if(cmd.equals("real_image")){
			realImage(client, out, param.get("baseStation"),
					param.get("channel"),
					param.get("mode"),
					param.get("encode")
					);
		}else if(cmd.equals("next_image")){
			nextImage(client, out, param.get("session"), param.get("encode"));
		}else if(cmd.equals("restart")){
			restart(client, out, param.get("baseStation"));
		}else if(cmd.equals("get_date")){
			getDate(client, out, param.get("baseStation"));
		}else if(cmd.equals("set_date")){
			setDate(client, out, param.get("baseStation"),					
					param.get("date"));
		}else {
			client.write(("Image Server, unkown command:" + command).getBytes());
		}
		out.println("\n\n\n");
		out.flush();
	}
	
	protected void alarmImage(SocketClient client, OutputWriter out,
			String alarmId, String baseStation, String ch,
			String last, String status, 
			String mode, String encode) throws IOException {
		int nLast = 0, nStatus = 0;
		if(last != null && !"".equals(last)){
			try {
				nLast = Integer.parseInt(last);
			} catch (Exception e) {
				log.error(e.toString());
			}
		}
		if(status != null && !"".equals(status)){
			try {
				nStatus = Integer.parseInt(status);
			} catch (Exception e) {
				log.error(e.toString());
			}
		}
		Collection<AlarmRecord> imageList = server.fileManager.getImageListByAlaram(alarmId, nLast, nStatus, baseStation, ch);
		String sid = createSessionId() + "";
		Iterator<AlarmRecord> iter = imageList.iterator();
		out.println("0$alarm_image$" + sid);
		if(mode.equals("one")){
			SessionCache session = new SessionCache();
			session.sessionType = SessionCache.TYPE_ALARM;
			session.iter = iter;
			session.alarmId = alarmId;
			session.nLast = nLast;
			session.nStatus = nStatus;
			session.baseStation = baseStation;
			sessionCache.set(sid,  session, 30);
			//outputImageFile(out, nextImage(iter), encode);
		}else {
			for(AlarmRecord alarm = nextImage(iter); alarm != null;){
				outputImageFile(out, alarm, encode);
				alarm = nextImage(iter);
			}
		}
	}
	
	private int createSessionId(){
		return sessionId++ % (Integer.MAX_VALUE - 1);
	}
	
	/**
	 * 遍历告警记录，过滤掉找不到文件的记录。
	 */
	private AlarmRecord nextImage(Iterator<AlarmRecord> iter) throws IOException{
		File path = null;
		AlarmRecord alarm = null;
		for(; iter.hasNext();){
			alarm = iter.next();
			path = server.fileManager.getRealPath(alarm);
			if(path == null){
				log.warn("Not found image file:" + alarm.videoPath);
				alarm = null;
			}else {
				alarm.absPath = path;
				break;
			}
		}
		return alarm;
	}
	
	private void outputImageFile(OutputWriter out, AlarmRecord alarm, String encode) throws IOException{
		//size:245*111$start_time:2010-10-11$name:xxx$data_size:1024
		if (alarm == null)return;
		byte[] data = new byte[(int)alarm.absPath.length()];
		String size = "352*288";
		if(alarm.dataSize == 1) size = "704*576";
		String date = format.format(new Date(System.currentTimeMillis()));
		if(alarm.startTime != null){
			date = format.format(alarm.startTime);
		}
		InputStream in = new FileInputStream(alarm.absPath);
		in.read(data);
		in.close();	
		
		//alarm.baseStation;
		//alarm.alarm;
		String name = ImageText.titleName(alarm.baseStation, "");
		String title = String.format("%s_%s 通道%s", alarm.baseStation, name, alarm.channelId);
		
		String label_location = "10x-15";
		if(server != null){
			label_location = server.settings.getString("img_title_location", label_location);
		}
		data = ImageText.drawText(data, title, label_location);
		//String meta = String.format("size:%s$time:%s$length:%s$base:%s$ch:%s$status:$%s",
		//		size, date, data.length, alarm.baseStation, alarm.channelId, alarm.alarmStatus);
		String meta = String.format("%s$%s$%s$%s$%s$$%s",
				size.replace('*', '$'), date, data.length, alarm.baseStation, alarm.channelId, alarm.alarmStatus);		
		log.debug("image file:" + meta);
		out.println(meta);
		out.println(data, encode);
	}
	
	protected void realImage(SocketClient client, OutputWriter out,
			String baseStation, String channel,
			String mode, String encode) throws IOException {
		int nch = 0;
		if(channel != null && !"".equals(channel)){
			nch = Integer.parseInt(channel);
		}
		
		if(baseStation == null || "".equals(baseStation.trim()) || nch == 0){			
			out.println("-2$param error");
		}else {
			ASC100Client ascClient = server.getMonitorClient(baseStation);
			if(client == null){
				out.println("1$Not found base station");
			}else {
				String sid = createSessionId() + "";
				SessionCache session = new SessionCache();
				session.sessionType = SessionCache.TYPE_REAL;
				session.baseStation = baseStation;
				session.channel = channel;
				sessionCache.set(sid,  session, 30);
				ascClient.getRealImage(nch);
				ascClient.addListener(new RealImageListener(sid, nch));
				if(ascClient.image != null && ascClient.image.channel != nch){
					out.println("2$loading$" + ascClient.image.channel);
				}else {
					out.println("0$real_image$" + sid);
				}
			}
		}
	}
	
	private void outputImage(OutputWriter out, ImageInfo image, String encode, String bts, String ch) throws IOException{
		//size:245*111$start_time:2010-10-11
		byte[] data = new byte[image.getDataSize()];
		String size = "352*288";
		if(image.imageSize == 1) size = "704*576";
		String date = format.format(new Date(System.currentTimeMillis()));
		if(image.generateDate != null){
			date = format.format(image.generateDate);
		}
		image.buffer.duplicate().get(data);
		//String meta = String.format("size:%s$time:%s$length:%s",
		//		size, date, data.length);
		
		String name = ImageText.titleName(bts, "");
		String title = String.format("%s_%s 通道%s", bts, name, ch);
		
		String label_location = "10x-15";
		if(server != null){
			label_location = server.settings.getString("img_title_location", label_location);
		}
		
		data = ImageText.drawText(data, title, label_location);
		
		String meta = String.format("%s$%s$%s$%s$%s$1",
				size.replace('*', '$'), date, data.length, bts, ch);	
		log.debug("image data:" + meta);
		out.println(meta);
		out.println(data, encode);		
	}
	
	/**
	 * status:
	 * -2 -- 参数错误
	 * 0 -- ok
	 * 1 -- 过期session
	 * 2 -- 顺序复位
	 * 3 -- 等待返回。
	 * 4 -- end
	 * @throws IOException 
	 */
	protected void nextImage(SocketClient client, OutputWriter out, String sid, String encode) throws IOException {
		if(sid == null || "".equals(sid)){
			out.println("-2$param error");
		}else if(sessionCache.get(sid) == null){
			out.println("1$session expired");
		}else {
			SessionCache session = (SessionCache)sessionCache.get(sid);
			if(session.sessionType == SessionCache.TYPE_REAL){
				if(session.image == null){
					out.println("3$waiting");
				}else {
					out.println("0$ok");
					outputImage(out, session.image, encode, session.baseStation, session.channel);
				}
			}else if(session.sessionType == SessionCache.TYPE_ALARM){
				AlarmRecord alarm = this.nextImage(session.iter);
				if(alarm == null){
					Collection<AlarmRecord> imageList = server.fileManager.getImageListByAlaram(session.alarmId,
							session.nLast, session.nStatus, session.baseStation, session.channel);
					session.iter = imageList.iterator();
					alarm = this.nextImage(session.iter);
					if(alarm == null){
						out.println("4$end");
					}else {
						out.println("3$reset");
						outputImageFile(out, alarm, encode);
					}
				}else {
					out.println("0$ok");
					outputImageFile(out, alarm, encode);
				}
			}else{
				out.println("1$error session type:%s" + session.sessionType);
			}
		}
	}
	
	protected void restart(SocketClient client, OutputWriter out, String baseStation) throws IOException {
		if(baseStation == null || "".equals(baseStation.trim())){
			out.println("-2$param error");
		}else {
			ASC100Client ascClient = server.getMonitorClient(baseStation);
			if(ascClient == null){
				out.println("1$Not found base station");
			}else {				
				final String[] result = new String[]{"2$timeout"};
				ImageClientListener l = new AbstractImageListener() {
					public void message(ImageClientEvent event){
						if(event.data.len == 2){
							ByteBuffer b = event.data.inBuffer.asReadOnlyBuffer();
							if(b.get() == 3 && b.get() == 5){
								if(event.data.cmd == 2){
									result[0] = "3$restart failed";
								}else if(event.data.cmd == 0){
									result[0] = "0$restart ok";
								}
 								synchronized(this){
									this.notifyAll();
								}
							}
						}
					};
				};
				ascClient.addListener(l);		
				ascClient.restart();
				try {
					synchronized(l){
						l.wait(1000 * 10);
					}
				} catch (InterruptedException e) {
				}finally{
					ascClient.removeListener(l);
				}				
				out.println(result[0]);
			}
		}
	}
	
	protected void getDate(SocketClient client, OutputWriter out, String baseStation) throws IOException {
		if(baseStation == null || "".equals(baseStation.trim())){
			out.println("-2$param error");
		}else {
			ASC100Client ascClient = server.getMonitorClient(baseStation);
			if(client == null){
				out.println("1$Not found base station");
			}else {
				final String[] date = new String[1];
				ImageClientListener l = new AbstractImageListener() {
					public void message(ImageClientEvent event){
						if(event.data.len == 8){
							ByteBuffer b = event.data.inBuffer.asReadOnlyBuffer();
							if(b.get() == 2 && b.get() == 1){
								date[0] = String.format("%02x-%02x-%02x %02x:%02x:%02x",
										b.get(), b.get(), b.get(), 
										b.get(), b.get(), b.get());
								synchronized(this){
									this.notifyAll();
								}
							}
						}
					};
				};
				ascClient.addListener(l);		
				ascClient.getDateTime();
				try {
					synchronized(l){
						l.wait(1000 * 10);
					}
				} catch (InterruptedException e) {
				}finally{
					ascClient.removeListener(l);
				}
				if(date[0] != null){
					out.println("0$" + date[0]);
				}else {
					out.println("2$timeout");
				}
			}
		}
	}	
		
	protected void setDate(SocketClient client, OutputWriter out, String baseStation, String date) throws IOException {
		if(baseStation == null || "".equals(baseStation.trim())){
			out.println("-2$param error");
		}else {
			ASC100Client ascClient = server.getMonitorClient(baseStation);
			if(ascClient == null){
				out.println("1$Not found base station");
			}else {
				final String[] result = new String[]{"2$timeout"};
				ImageClientListener l = new AbstractImageListener() {
					public void message(ImageClientEvent event){
						if(event.data.len == 2){
							ByteBuffer b = event.data.inBuffer.asReadOnlyBuffer();
							if(b.get() == 1
							  //&& b.get() == 6 文档上需要检查。设备返回没有这个标志
							  ){
								if(event.data.cmd == 2){
									result[0] = "3$set date failed";
								}else if(event.data.cmd == 0){
									result[0] = "0$set date ok";
								}
 								synchronized(this){
									this.notifyAll();
								}
							}
						}
					};
				};
				ascClient.addListener(l);
				if(date == null || date.length() != 12){
					ascClient.setDateTime(new Date());
				}else {
					ascClient.setDateTime(date);
				}
				try {
					synchronized(l){
						l.wait(1000 * 10);
					}
				} catch (InterruptedException e) {
				}finally{
					ascClient.removeListener(l);
				}
				out.println(result[0]);
			}
		}
	}	
		
	
	protected Map<String, String> parseCommand(String command){
		Map<String, String> p = new HashMap<String, String>();
		command = command.replace("img>", "");
		String[] data = command.split("\\?", 2);
		p.put("q", data[0]);
		if(data.length > 1 && data[1] != null){
			for(String para: data[1].split("&")){
				if(para.indexOf('=') > 0){
					String[] aParam = para.split("=", 2);
					if(aParam.length == 2){
						p.put(aParam[0].trim(), aParam[1].trim());
					}else {
						p.put(aParam[0].trim(), "");
					}
				}
			}
		}
		return p;
	};
	
	class OutputWriter{
		//private SocketClient client = null;
		public OutputStream out = null;
		public OutputWriter(final SocketClient client){
			//this.client = client;
			out = new ByteArrayOutputStream(1024 * 1024){
				public void flush() throws IOException{
					if(this.size() > 0){
						client.write(this.toByteArray());
						this.reset();
					}
				}
			};
		}
		
		public void println(String l) throws IOException{
			out.write((l + "\n").getBytes());
		}
				
		public void println(byte[] data, String encode) throws IOException{
			if(encode != null && "base64".equals(encode)){
				println(Base64.encode(data));
			}else {
				out.write(data);
			}
		}
		
		public void flush() throws IOException{
			out.flush();
		}
	}
	
	class SessionCache{
		public static final int TYPE_REAL = 1;
		public static final int TYPE_ALARM = 2;
		public int sessionType = 0;
		/*
		 * 告警查询时Cache信息
		 */
		public Iterator<AlarmRecord> iter = null;
		public String alarmId, baseStation;
		private int nLast, nStatus;
		
		/*
		 * 点播时Cache信息
		 */
		
		public ImageInfo image;
		public String channel;
	}
	
	/**
	 * 点播图片处理方法，为了实现异步通信。点播操作返回的一个操作的Session ID.
	 * 使用基站回调接口，在收到图片时把图片放到Cache里面。客户端下次发next_image时
	 * 返回点播到的图片。
	 */
	class RealImageListener extends AbstractImageListener {
		public String sessionId = null;
		public int channel = 0;
		public RealImageListener(String sid, int ch){
			this.sessionId = sid;
			this.channel = ch;
		}
		public void recevieImageOK(ImageClientEvent event) {
			if(sessionCache.get(sessionId) == null){
				event.source.removeListener(this);
			}
			if(event.image != null && event.image.imageStatus == 0 &&
			   this.channel == event.image.channel 
			  ){
				SessionCache session = (SessionCache) sessionCache.get(sessionId);
				if(session != null){
					session.image = event.image;
					event.source.removeListener(this);
					log.debug("Get image for session:" + sessionId);
				}
			}
		};
	}
}
