package org.goku.video;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.goku.core.model.AlarmDefine;
import org.goku.core.model.RouteRunningStatus;
import org.goku.http.BaseRouteServlet;
import org.goku.video.odip.MonitorClient;
import org.goku.video.odip.MonitorClientEvent;
import org.goku.video.odip.VideoDestination;
import org.mortbay.util.ajax.Continuation;
import org.mortbay.util.ajax.ContinuationSupport;

/**
 * HTTP交互接口
 * 
 * @author deon
 */
public class DefaultRouteServerServlet extends BaseRouteServlet{
	private VideoRouteServer server = null;
	private Log log = LogFactory.getLog("http");
	
	public void init(ServletConfig config){
		server = VideoRouteServer.getInstance();
	}

	public void real_play(HttpServletRequest request, HttpServletResponse response) 
	throws IOException {
		String uuid = this.getStringParam(request, "uuid", null);
		int ch = this.getIntParam(request, "ch", 1);
	    MonitorClient client = null;
		if(uuid == null){
			response.getWriter().println("-2:Parameter error");
		}else {
		    client = server.getMonitorClient(uuid);
		    if(client == null){
		    	response.getWriter().println("1:BTS not found");
		    }else if(client.getClientStatus() == null){
		    	response.getWriter().println("2:BTS disconnected");
		    }else {
		    	client.realPlay(ch);
		    	response.getWriter().println("0:Video request OK");
		    }
		}
	}
	
	/**
	 * 使用录像文件，模拟一个摄像头通道。用于开发调试。
	 * @param request
	 * @param response
	 * @throws IOException
	 */
	public void mock_video(HttpServletRequest request, HttpServletResponse response) 
	throws IOException {
		String uuid = this.getStringParam(request, "uuid", "1001");
		String channel = this.getStringParam(request, "ch", "1");
		String alarm = this.getStringParam(request, "alarm", "");
		String path = this.getStringParam(request, "video", "");
		
		if(request.getMethod().equals("POST")){
			MonitorClient client = server.getMonitorClient(uuid);			
			File videoPath = server.recordManager.getAlarmRecordFile(alarm);
			if(videoPath == null && new File(path).isFile()){
				videoPath = new File(path); 
			}
			if(client != null && videoPath != null){
		    	MockRealVideo mock = new MockRealVideo(videoPath, client, Integer.parseInt(channel));
		    	mock.start();		    					
			}
		}
		
		static_serve("org/goku/video/mock_alarm.txt", "text/html", response);		
	}
	
	public void mock_alarm(HttpServletRequest request, HttpServletResponse response) 
	throws IOException {
		String uuid = this.getStringParam(request, "uuid", "1001");
		String channel = this.getStringParam(request, "ch", "1");
		String code = this.getStringParam(request, "code", "1");
		
		if(request.getMethod().equals("POST")){
			MonitorClient client = server.getMonitorClient(uuid);
			MonitorClientEvent event = new MonitorClientEvent(client);
			if(client != null){
				AlarmDefine alarm = AlarmDefine.alarm(code);
				alarm.channels = new int[1];
				alarm.channels[0] = Integer.parseInt(channel);
				Collection<AlarmDefine> alarms = new ArrayList<AlarmDefine>();
				alarms.add(alarm);
				event.alarms = alarms;			
				client.eventProxy.alarm(event);
			}
		}
		
		static_serve("org/goku/video/mock_alarm.txt", "text/html", response);
	}	
	
	/**
	 * 开始视频录像。
	 * @param request
	 * @param response
	 * @throws IOException
	 */
	public void start_record(HttpServletRequest request, HttpServletResponse response) 
	throws IOException {
		String uuid = this.getStringParam(request, "uuid", null);
		int ch = this.getIntParam(request, "ch", 1);
		String user = this.getStringParam(request, "user", null);
	    MonitorClient client = null;
		if(uuid == null || user == null){
			response.getWriter().println("-2:Parameter error");
		}else {
		    client = server.getMonitorClient(uuid);
		    if(client == null){
		    	response.getWriter().println("1:BTS not found");
		    }else if(client.getClientStatus() == null){
		    	response.getWriter().println("2:BTS disconnected");
		    }else {
		    	String sid = server.recordManager.startManualRecord(client, user);
		    	client.realPlay(ch);
		    	response.getWriter().println("0:Start video recording$" + sid);
		    }
		}
	}
	
	/**
	 * 开启设备代理。
	 * @param request
	 * @param response
	 * @throws IOException
	 */
	public void create_proxy(HttpServletRequest request, HttpServletResponse response) 
	throws IOException {
		String uuid = this.getStringParam(request, "uuid", null);
	    MonitorClient client = null;
		if(uuid == null){
			response.getWriter().println("-2:Parameter error");
		}else {
		    client = server.getMonitorClient(uuid);
		    if(client == null){
		    	response.getWriter().println("1:BTS not found");
		    }else {
		    	int port = server.proxyServer.createProxy(client.ipAddr);
		    	if (port > 0){
		    		response.getWriter().println("0:create proxy ok");
		    		response.getWriter().println(request.getLocalAddr() + ":" + port);
		    	}else {
		    		response.getWriter().println("2:Not found free port");		    		
		    	}
		    }
		}
	}	
	
	/**
	 * 停止录像。
	 * @param request
	 * @param response
	 * @throws IOException
	 */
	public void stop_record(HttpServletRequest request, HttpServletResponse response) 
	throws IOException {
		String uuid = this.getStringParam(request, "uuid", null);
		if(uuid == null){
			response.getWriter().println("-2:Parameter error");
		}else {
			server.recordManager.stoptRecord(uuid);
		    response.getWriter().println("0:Stop video record");
		}
	}
	
	/**
	 *视频传输通道。
	 */
	public void video(HttpServletRequest request, HttpServletResponse response) 
	throws IOException {
	    String uuid = request.getParameter("uuid");
	    String format = this.getStringParam(request, "format", "ogg");
	    int ch = this.getIntParam(request, "ch", 1);	    
	    processRealVideo(uuid, ch, format, request, response);
    }
	
    protected void videoHandler(HttpServletRequest request, HttpServletResponse response) 
	throws IOException {
    	Pattern p = Pattern.compile("/(\\d+)_(\\d+)\\.(flv|mp4|ogg)");
    	Matcher m = p.matcher(request.getRequestURI());
    	String uuid = null;
    	int ch = 0;
    	String format = null;
    	if(m != null && m.find()){
    		 uuid = m.group(1);
    		 ch = Integer.parseInt(m.group(2));
    		 format = m.group(3);
    		 processRealVideo(uuid, ch, format, request, response);
    	}else {
    		response.getWriter().println("error uri:" + request.getRequestURI());
    	}
    }
    
    private void processRealVideo(String uuid, int ch, String format, 
    		HttpServletRequest request, HttpServletResponse response)
    		throws IOException
    {
	    MonitorClient client = null;
	    if(uuid != null){
	    	client = server.getMonitorClient(uuid);
	    }
	    
	    log.debug(String.format("processRealVideo, uuid:%s, ch:%s, format:%s", uuid, ch, format));
	    if(client != null){
	    	client.realPlay(ch);
			response.setHeader("Transfer-Encoding", "chunked");
			if(format.equals("mp4")){
				response.setContentType("video/mp4");
			}else if(format.equals("ogg")){
				response.setContentType("video/ogg");
				//response.setContentType("application/octet-stream");
			}else if(format.equals("flv")){
				response.setContentType("video/x-flv");
			}
			
			response.setHeader("Content-Length", Integer.MAX_VALUE + "");
		    //response.setContentType("application/octet-stream");
			//response.setContentType("video/h264");			
		    response.setStatus(HttpServletResponse.SC_OK);
		    Continuation continuation = ContinuationSupport.getContinuation(request, null);
		    response.flushBuffer();
		    
		    RealPlayRouting callback = new RealPlayRouting(continuation, 
		    		response.getOutputStream(), 
		    		request.getRemoteHost());
		    callback.ch = ch;
		    server.liveVideoEncoder.registerVideoOutput(client, ch, callback, format);
		    //suspend 365 days
		    continuation.suspend(1000 * 60 * 60 * 365);
		    //suspend timeout.
		    callback.close();
	    }else {
	    	response.getWriter().write("Not found client by uuid:" + uuid); 
	    }    	
    }
	
	public void send_play(HttpServletRequest request, HttpServletResponse response) 
	throws IOException {
		response.getWriter().write("Welcome send_play!");
		
		String data = request.getParameter("data");
		
    }
    
	
	public void ping(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		response.getWriter().println("OK");
	}
	
	public void info(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		response.getWriter().println(this.server.groupName + "$" + this.server.socketServer.listenPort);
	}
	
	public void add_bs(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		String uuid = this.getStringParam(request, "uuid", null);
		if(uuid == null){
			response.getWriter().println("-2:Parameter error");
		}else {
			if(server.addMonitorClient(uuid)){
				response.getWriter().println("0:Added BTS");
			}else {
				response.getWriter().println("1:Failed to add BTS");
			}
		}
	}
	
	public void del_bs(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		String uuid = this.getStringParam(request, "uuid", null);
		if(uuid == null){
			response.getWriter().println("-2:Parameter error");
		}else {
			MonitorClient mc = server.getMonitorClient("uuid");
			if(mc == null){
				response.getWriter().println("1:BTS not found");
			}else if(mc.route.destinationSize() > 0){
				response.getWriter().println("2:BTS using by user");
			}else {
				server.removeMonitorClient(mc);
				response.getWriter().println("0:Disconnect BTS");
			}
		}
	}
    
	/**
	 * 服务器的内部状态。
	 */
    public void status(HttpServletRequest request, HttpServletResponse response) 
	throws IOException {
    	DateFormat format= new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    	//response.getWriter().write("Welcome routing_status!");
    	String reset = this.getStringParam(request, "reset", "");
    	RouteRunningStatus status = server.getStatus(runningStatus, reset.equals("Y"));
    	response.getWriter().println("Time:" + format.format(status.statusTime));
    	//response.getWriter().println("UCT:" + status.statusTime.getTime());
    	response.getWriter().println("UCT:" + status.statusTime.getTime()/1000);
    	response.getWriter().println("recvData:" + status.receiveData);
    	response.getWriter().println("sentData:" + status.sendData);
    	response.getWriter().println("activeVideo:" + status.activeVideo);
    	response.getWriter().println("connectVideo:" + status.connectVideo);
    	response.getWriter().println("allVideo:" + status.allVideo);
    	response.getWriter().println("clientRequestCount:" + status.clientRequestCount);
    }

    protected void index_page(HttpServletRequest request, HttpServletResponse response) 
	throws IOException {
    	static_serve("org/goku/video/help_doc.txt", "text/plain", response);
    }
    
    class MockRealVideo extends Thread{
    	public MonitorClient client = null;
    	public File file = null;
    	public int channelId = 0;
    	private FileChannel channel = null;
    	private MappedByteBuffer buffer = null;    	
    	public int fileSize = 0;
    	
    	MockRealVideo(File path, MonitorClient client, int ch){
    		this.file = path;
    		this.channelId = ch;
    		this.client = client;
    	}
    	
    	public void run(){
    		fileSize = (int)file.length();
    		try {
				channel = new FileInputStream(file).getChannel();
				buffer = channel.map(MapMode.READ_ONLY, 0, fileSize);    		
			} catch (Exception e) {
				log.warn("Failed to start video mock with path:" + file.getAbsoluteFile());
			}
			log.info("Start mock for " + client.info.uuid + ":" + channelId + 
					" file:" + file.getAbsolutePath() + 
					" size:" + this.fileSize); 
			int frameSize = 1024 * 100;
			if(buffer != null && fileSize > frameSize){
				while(client.getClientStatus() == null){
					if(buffer.position() + frameSize > fileSize){
						buffer.position(0);
					}
					buffer.limit(buffer.position() + frameSize);
					log.info("Route mock video, position=" + buffer.position());
					client.route.route(buffer, 0, channelId);
					//if(client.)
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
					}
				}
			}
			log.info("Stop video mock " + client.info.uuid + ":" + channelId);
    	}
    }
	
    class RealPlayRouting implements VideoDestination{
    	private OutputStream os = null;
    	private Continuation continuation = null;
    	private boolean running = true;
    	public int ch = 1;
    	
    	private String remoteIp = null;
    	public RealPlayRouting(Continuation continuation, OutputStream os, String ip){
    		this.continuation = continuation;
    		this.os = os;
    		//用来输出Log.
    		this.remoteIp = ip;
    	}

		@Override
		public boolean accept(int sourceType, int channel) {
			return true;
		}
		
		public boolean isClosed(){
			return !this.running;
		}		

		@Override
		public void write(ByteBuffer data, int type, int channel) throws IOException {
			if(!this.running) throw new IOException("Destination closed.");
			if(channel == this.ch){
				log.info("-----------------------xxx-");
				byte[] buffer = new byte[data.remaining()];
				data.get(buffer);
				this.os.write(buffer);
				os.flush();
			}
		}

		@Override
		public void close() {
			if(this.running){
				this.running = false;
				continuation.resume();
			}
		}
		
		public String toString(){
			return String.format("HTTP<%s>", this.remoteIp);
		}    	
    }
}
