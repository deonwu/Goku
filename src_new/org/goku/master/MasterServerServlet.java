package org.goku.master;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.goku.core.model.AlarmRecord;
import org.goku.core.model.BaseStation;
import org.goku.core.model.Location;
import org.goku.core.model.RouteServer;
import org.goku.core.model.SimpleCache;
import org.goku.core.model.SystemLog;
import org.goku.core.model.User;
import org.goku.core.model.VideoTask;
import org.goku.db.QueryParameter;
import org.goku.db.QueryResult;
import org.goku.http.BaseRouteServlet;
import org.goku.http.HTTPRemoteClient;
import org.goku.http.HttpResponse;
import org.goku.http.SimpleHttpClient;
import org.json.simple.JSONValue;

public class MasterServerServlet extends BaseRouteServlet{
	protected DateFormat format= new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	private static SimpleCache cache = new SimpleCache();
	private MasterVideoServer server = null;
	private Log log = LogFactory.getLog("http");
	
	public void init(ServletConfig config){
		server = MasterVideoServer.getInstance();
		User u = new User();
		u.name = "test";
		cache.set("test", u, 60 * 30);
	}

	public void replay(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		String id = request.getParameter("id");
		if(id == null){
			response.getWriter().write("Parameter list 'id=<PK>', 'mime=text/plain'");
		}else {
			String file = server.settings.getString(id, null);
			if(file != null && new File(file).exists() ){
				_play(file, request, response);		
			}else if(file != null){
				response.getWriter().write("Not found file:" + file);
			}else {
				response.getWriter().write("Not found file by id " + id);
			}
		}		
	}
	
	private void _play(String file, HttpServletRequest request, 
					HttpServletResponse response) throws ServletException, IOException{
		response.setHeader("Transfer-Encoding", "chunked");
		
		String mime = request.getParameter("mime");
		mime = mime == null ? "application/octet-stream" : mime;		
	    response.setContentType(mime);
	    response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
	    
	    String range = request.getHeader("Range");
	    long start = 0, end = Integer.MAX_VALUE;
	    if(range != null){
	    	log.debug("Request range:" + range);
	    	String[] ranges = range.split("=", 2)[1].split("-", 2);
	    	try{
	    		start = Integer.parseInt(ranges[0].trim());
	    	}catch(Exception e){}
	    	try{
	    		end = Integer.parseInt(ranges[1].trim());
	    	}catch(Exception e){}
	    }
	    	    
	    long fileSize = new File(file).length();
	    end = Math.min(end, fileSize);
	    //end = Math.min(end, start + 1024 * 1024);
	    start = Math.min(start, end);
	    
	    log.info(String.format("Start replay video, mime:%s, Range bytes=%s-%s, file:%s", 
				   mime, start, end, file));
	    
	    response.setHeader("Content-Length", (end - start) + "");
	    response.setHeader("Content-Range", String.format("bytes %s-%s/%s", start, end, fileSize));
	    
	    FileChannel channel = new FileInputStream(file).getChannel();
	    MappedByteBuffer buffer = channel.map(MapMode.READ_ONLY, start, end - start);
	    
	    byte[] byteBuffer  = new byte[1024 * 640];
	    for(int remain = 0; buffer.hasRemaining();){
	    	//如果剩余数据大于Buffer直接发送整个Buffer, 否则只发送剩余数据。
	    	remain = buffer.remaining();
	    	if(remain > byteBuffer.length){
	    		buffer.get(byteBuffer);
	    		response.getOutputStream().write(byteBuffer);
	    	}else {
	    		buffer.get(byteBuffer, 0, remain);
	    		response.getOutputStream().write(byteBuffer, 0, remain);
	    	}
	    	response.flushBuffer();
	    }
	    
	    channel.close();
	    
	    log.debug("Done replay.");
	}
	
	@Override
	protected void index_page(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		//response.getWriter().write("Welcome master server!");
		static_serve("org/goku/master/statics/index_home_page.txt", HTML, response);
	}
	
	public void help_doc(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		static_serve("org/goku/master/statics/help_doc.txt", TEXT, response);
	}
	
	public void new_password(HttpServletRequest request,
			HttpServletResponse response)throws ServletException, IOException {
		String sid = this.getStringParam(request, "sid", null);		
		String oldPassword = this.getStringParam(request, "old", null);
		String newPassword = this.getStringParam(request, "new", null);
		
		if(sid == null || oldPassword == null || newPassword == null){
			response.getWriter().println("-2:Parameter error");
		}else {
			User userObj = (User)cache.get(sid);
			if(userObj == null){
				response.getWriter().println("1:Session is expired or logout");
			}else if(!userObj.password.equals(oldPassword)) {
				response.getWriter().println("2:Old password error");
			}else {
				userObj.password = newPassword;
				server.storage.save(userObj, new String[]{"password"});
				response.getWriter().println("0:ok");
			}
		}		
	}	
	
	public void list_task(HttpServletRequest request,
			HttpServletResponse response)throws ServletException, IOException {
		String sid = this.getStringParam(request, "sid", null);
		
		if(sid == null){
			response.getWriter().println("-2:Parameter error");
		}else {
			User userObj = (User)cache.get(sid);
			if(userObj == null){
				response.getWriter().println("1:Session is expired or logout");
			}else {
				Collection<VideoTask> list = server.storage.listTask(userObj);
				response.getWriter().println("0:Video task list$" + list.size());
				outputVideoTask(list, response.getWriter());
				response.getWriter().println("");
			}
		}		
	}
	
	private void outputVideoTask(Collection<VideoTask> list, PrintWriter writer){
		VideoTask task = null;
		String data = null;
		//"<taskID>$<name>$<uuid>$<channel>$<windowID>$<startDate>$<endDate>$<weeks>$<startTime>$<endTime>$<minShowTime>$<showOrder>$<status>"
		String[] attrs = new String[]{
				"taskID", "name", "uuid", "channel", "windowID", "startDate", "endDate", "weekDays", "startTime",  
				"endTime",  "minShowTime", "showOrder", "status"};

		for(Iterator<VideoTask> iter = list.iterator(); iter.hasNext();){
			task = iter.next();
			Object val = null;
			data = "";
			for(String attr: attrs){
				val = null;
				try{
					val = VideoTask.class.getField(attr).get(task);
				}catch(Exception e){
				}
				val = val == null || val.toString().trim().equals("") ? "0": val;
				if(data.length() > 0){data += "$";};
				data += val.toString();				
			}
			writer.println(data);
		}
		writer.println();		
		
	}
	
	public void save_task(HttpServletRequest request,
			HttpServletResponse response)throws ServletException, IOException {
		//"<taskID>$<name>$<uuid>:<channel>$<windowID>$<startDate>$<endDate>$<startTime>$<weekDays>$<endTime>$<minShowTime>$<showOrder>$<status>"
		String sid = this.getStringParam(request, "sid", null);
		
		if(sid == null){
			response.getWriter().println("-2:Parameter error");
		}else {
			User userObj = (User)cache.get(sid);
			if(userObj == null){
				response.getWriter().println("1:Session is expired or logout");
			}else {
				saveOrCreateTask(request, userObj.name);
				response.getWriter().println("0:Save OK");
				response.getWriter().println("");
			}
		}
	}
	
	private void saveOrCreateTask(HttpServletRequest request, String userName){
		int taskId = this.getIntParam(request, "taskID", 0);
		VideoTask task = null;
		Collection<String> updated = new ArrayList<String>();
		if(taskId != 0){
			task = (VideoTask)server.storage.load(VideoTask.class, taskId + "");
		}
		if(task == null){
			task = VideoTask.newDefault(server.storage, userName);
		}else if(this.getIntParam(request, "status", 0) == 9) {
			try {
				server.storage.execute_sql("delete from video_task where taskID=${0}", 
						new Integer[]{taskId});
			} catch (SQLException e) {
				log.error(e.toString(), e);
			}
			return;
		}
		if(!task.userName.equals(userName)) {
			log.warn("Task id:" + task.taskID + ", username:" + task.userName);
			return;
		}
		String[] attrs = new String[]{
				"name", "uuid", "channel", "windowID", "startDate", "endDate", "weekDays", "startTime",  
				"endTime",  "minShowTime", "showOrder", "status"};
		String val = null;
		for(String attr: attrs){
			val = this.getStringParam(request, attr, null);
			if(val == null)continue;
			try{
				if(attr.equals("showOrder") || attr.equals("windowID")){
					task.getClass().getField(attr).set(task, new Integer(val));
				}else {
					task.getClass().getField(attr).set(task, val);
				}
			}catch(Exception e){
				log.warn(String.format("Set Attribute error, taskId: %s, %s->%s",
						task.taskID, 
						attr, val));
			}
		}
		server.storage.save(task, updated.toArray(new String[]{}));		
	}
	
	/**
	 * 返回基站列表 
	 */
	public void list_bs(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		String sid = this.getStringParam(request, "sid", null);
		String mode = this.getStringParam(request, "mode", null);
		
		if(sid == null){
			response.getWriter().println("-2:Parameter error");
		}else {
			User userObj = (User)cache.get(sid);
			if(userObj == null){
				response.getWriter().println("1:Session is expired or logout");
			}else {
				Collection<BaseStation> list = server.storage.listStation(userObj);
				response.getWriter().println("0:Base station list$" + list.size());
				outputStationInfo(list, response.getWriter(), server.routeManager, mode);
				response.getWriter().println("");
			}
		}
	}
	
	/**
	 * 返回基站列表 
	 */
	public void list_bs_tree(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		String sid = this.getStringParam(request, "sid", null);
		String mode = this.getStringParam(request, "mode", null);
		
		if(sid == null){
			response.getWriter().println("-2:Parameter error");
		}else {
			User userObj = (User)cache.get(sid);
			if(userObj == null){
				response.getWriter().println("1:Session is expired or logout");
			}else {
				//response.getWriter().println("0:Base station list$" + list.size());
				Location node = server.storage.getRootLocation(userObj);
				response.getWriter().println("0:Base station list$" + node.getTreeCount());
				outputStationTreeInfo(node, response.getWriter(), server.routeManager, mode);
				response.getWriter().println("");
			}
		}
	}	
	
	/**
	 * 返回告警信息列表。 
	 */
	public void list_al(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		String sid = this.getStringParam(request, "sid", null);
		
		if(sid == null){
			response.getWriter().println("-2:Parameter error");
		}else {
			User userObj = (User)cache.get(sid);
			if(userObj == null){
				response.getWriter().println("1:Session is expired or logout");
			}else {
				QueryParameter param = new QueryParameter();
				param.qsid = this.getStringParam(request, "qsid", null);
				param.limit = this.getIntParam(request, "limit", 100);
				param.offset = this.getIntParam(request, "offset", 0);
				param.order = this.getStringParam(request, "order", null);
				
				int category = this.getIntParam(request, "c", 2);
				Map<String, Object> filter = new HashMap<String, Object>();
				if(category == 1){
					filter.put("lastUpdateTime__>=", userObj.lastRealAlarmTime);
					userObj.lastRealAlarmTime = new Date(System.currentTimeMillis());
				}
				
				//基站ID过滤
				String bsId = this.getStringParam(request, "uuid", null);
				if(bsId != null && !"".equals(bsId) && !"all".equals(bsId)){
					filter.put("uuid__=", bsId);
				}
				
				//状态过滤
				String status = this.getStringParam(request, "status", null);
				if(status != null && !"".equals(status) && !"all".equals(status)){
					filter.put("status__=", status);
				}

				//告警级别
				String level = this.getStringParam(request, "level", null);
				if(level != null && !"".equals(level) && !"all".equals(level)){
					filter.put("level__>=", level);
				}
				
				//告警类型
				String type = this.getStringParam(request, "type", null);
				if(type != null && !"".equals(type) && !"all".equals(type)){
					filter.put("alarmCode__=", type);
				}
								
				param.param = filter;
				QueryResult alarms = server.storage.queryData(AlarmRecord.class, param);
				outputAlarmList(alarms, response.getWriter());
			}
		}
	}
	
	/**
	 * 告警确认操作. 
	 */
	public void alarm_action(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		String sid = this.getStringParam(request, "sid", null);
		String uuid = this.getStringParam(request, "uuid", null);
		
		if(sid == null || uuid == null){
			response.getWriter().println("-2:Parameter error");
		}else {
			User userObj = (User)cache.get(sid);
			if(userObj == null){
				response.getWriter().println("1:Session is expired or logout");
			}else {
				AlarmRecord alarm = (AlarmRecord)server.storage.load(AlarmRecord.class, uuid);
				if(alarm == null){
					response.getWriter().println("2:Not found alarm by uuid");
				}else {
					SystemLog.saveLog(SystemLog.ALARM_CONFIRM, userObj.name, alarm.uuid, "");
					alarm.alarmStatus = this.getStringParam(request, "status", "1");
					alarm.user = userObj.name;
					alarm.comfirmTime = new Date(System.currentTimeMillis());
					alarm.lastUpdateTime = new Date(System.currentTimeMillis());
					//不用指定更新字段，因为对象是刚从数据库加载。而且记录不会在其他地方被修改。
					server.storage.save(alarm);
					response.getWriter().println("0:Alarm confirm$" + alarm.alarmStatus);
				}
			}
		}
	}
		
	/**
	 * 返回基站列表 
	 */
	public void add_route(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		String port = this.getStringParam(request, "port", "8081");
		String socketPort = this.getStringParam(request, "socketPort", null);
		String groupName = this.getStringParam(request, "group", "default");
		
		String client = request.getRemoteHost();
		String url = "http://" + client + ":" + port;
		HTTPRemoteClient httpClient = new HTTPRemoteClient(url);
		if(httpClient.checkConnection()){
			log.info(String.format("add route:%s, group:%s, socket:%s", client + ":" + port, groupName, socketPort));
			RouteServer route = this.server.addRouteServer(client + ":" + port, groupName);
			route.socketPort = socketPort;			
			response.getWriter().println("0:Added route server");
		}else {
			response.getWriter().println("1:Failed to add route server");
		}
	}	

	/**
	 * 登录系统
	 */
	public void login(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		String user = this.getStringParam(request, "user", null);
		String password = this.getStringParam(request, "password", null);
		
		String remoteAddr = request.getRemoteAddr();
		if(user == null || password == null){
			response.getWriter().println("-2:Parameter error");
		}else {
			User userObj = (User) server.storage.load(User.class, user);
			if(userObj != null){
				if(userObj.password != null && userObj.password.equals(password)){
					if(userObj.status !=null && userObj.status.equals("ok")){
						String key = md5(userObj.name + System.currentTimeMillis());
						cache.set(key, userObj, 60 * 30);
						request.setAttribute(SESSION_ID, key);
						request.setAttribute(SESSION_USER, userObj);
						SystemLog.saveLog(SystemLog.LOGIN_OK, user, "master", remoteAddr);
						response.getWriter().println("0:login ok$" + key);
						userObj.lastRealAlarmTime = new Date(System.currentTimeMillis());
					}else {
						SystemLog.saveLog(SystemLog.LOGIN_FAIL, user, "master", remoteAddr);
						response.getWriter().println("3:locked or removed.");
					}
				}else {
					SystemLog.saveLog(SystemLog.LOGIN_FAIL, user, "master", remoteAddr);
					response.getWriter().println("2:password error");
				}
			}else {
				SystemLog.saveLog(SystemLog.LOGIN_FAIL, user, "master", remoteAddr);
				response.getWriter().println("1:account not exist");
			}
		}
	}
	
	public void logout(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		String sid = this.getStringParam(request, "sid", null);
		
		if(sid != null){
			cache.remove(sid);
			request.setAttribute(SESSION_ID, null);
			request.setAttribute(SESSION_USER, null);
			User userObj = (User)cache.get(sid);
			if(userObj != null){
				String remoteAddr = request.getRemoteAddr();
				SystemLog.saveLog(SystemLog.LOGOUT, userObj.name, "master", remoteAddr);
			}
			response.getWriter().println("0:logout ok");
		}else {
			response.getWriter().println("-2:Parameter error");
		}
		
	}
	
	public void ping(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		response.getWriter().println("OK");
	}

	public void init_sql(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		static_serve("org/goku/master/statics/init_db_sql.txt", "text/plain", response);
	}
	
	public void settings_doc(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		static_serve("org/goku/master/statics/settings_doc.txt", "text/plain", response);
	}
	
	/**
	 * 模拟创建一条告警记录，用于开发测试阶段使用。
	 */
	public void mock_alarm(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException{
		String uuid = this.getStringParam(request, "uuid", "1001:1");
		
		AlarmRecord alarm = new AlarmRecord();
		alarm.startTime = new Date();
		alarm.endTime = new Date();
		
		alarm.alarmCode = this.getStringParam(request, "alarmCode", "001");
		alarm.alarmStatus = "1";
		alarm.alarmCategory = this.getStringParam(request, "category", "1");
		alarm.videoPath = this.getStringParam(request, "f", "001.h264");
		
		alarm.baseStation = uuid.split(":", 2)[0];
		alarm.channelId = uuid.split(":", 2)[1];
		alarm.generatePK();
		
		server.storage.save(alarm);
		
		response.getWriter().println("0:mock_ok");		
	}
	
	/**
	 * 将请求转发到视频转发服务器。
	 */
	public void start_record(HttpServletRequest request, HttpServletResponse response) 
	throws IOException, ServletException {
		forwardToRoute("start_record", request, response);
	}
	
	public void stop_record(HttpServletRequest request, HttpServletResponse response) 
	throws IOException, ServletException{
		forwardToRoute("stop_record", request, response);
	}

	public void create_proxy(HttpServletRequest request, HttpServletResponse response) 
	throws IOException, ServletException{
		forwardToRoute("create_proxy", request, response);
	}	
	
	public void rpc_add_location(HttpServletRequest request, HttpServletResponse response) 
	throws IOException{
		
	}
	
	public void rpc_add_bts(HttpServletRequest request, HttpServletResponse response) 
	throws IOException{
		
	}
	
	public void rpc_delete(HttpServletRequest request, HttpServletResponse response) 
	throws IOException{
		
	}
	
	public void rpc_list_bts(HttpServletRequest request, HttpServletResponse response) 
	throws IOException{
		response.setContentType("application/octet-stream");
        response.setCharacterEncoding("utf8");	
		String sid = this.getStringParam(request, "sid", null);
		Map<String, Object> data = new HashMap<String, Object>();		
		if(sid == null){
			data.put("status", "-2");
		}else {
			User userObj = (User)cache.get(sid);
			if(userObj == null){
				data.put("status", "1");
				response.getWriter().println("1:Session is expired or logout");
			}else {
				data.put("status", "0");
				Location node = server.storage.getRootLocation(userObj);
				data.put("data", node);
			}
		}
		JSONValue.writeJSONString(data, response.getWriter());
	}
	
	public void rpc_list_alarm(HttpServletRequest request, HttpServletResponse response) 
	throws IOException{
		response.setContentType("application/octet-stream");
        response.setCharacterEncoding("utf8");	
		String sid = this.getStringParam(request, "sid", null);
		
		Map<String, Object> data = new HashMap<String, Object>();
		if(sid == null){
			data.put("status", "-2");
		}else {
			User userObj = (User)cache.get(sid);
			if(userObj == null){
				data.put("status", "1");
			}else {
				data.put("status", "0");
				QueryParameter param = new QueryParameter();
				param.qsid = this.getStringParam(request, "qsid", null);
				param.limit = this.getIntParam(request, "limit", 100);
				param.offset = this.getIntParam(request, "offset", 0);
				param.order = this.getStringParam(request, "order", null);
				
				QueryResult alarms = server.storage.queryData(AlarmRecord.class, param);
				data.put("data", alarms);
			}
		}
		JSONValue.writeJSONString(data, response.getWriter());
	}	

	/**
	 * 将请求转发到视频转发服务器。
	 */	
	@SuppressWarnings("unchecked")
	protected void forwardToRoute(String action, HttpServletRequest request,
			HttpServletResponse response)throws ServletException, IOException {
		String uuid = this.getStringParam(request, "uuid", null);
		String sid = this.getStringParam(request, "sid", null);
		if(uuid == null){
			response.getWriter().println("-2:Parameter error");
		}else {
			BaseStation info = (BaseStation)server.storage.load(BaseStation.class, uuid);
			if(info == null){
				response.getWriter().println("1:BTS not found");
			}else {
				User userObj = (User)cache.get(sid);
				if(userObj == null){
					response.getWriter().println("1:Session is expired or logout");
				}else{
					if(info.routeServer != null && !info.equals("")){
						log.debug("Forward request to route:" + info.routeServer);
						SimpleHttpClient http = new SimpleHttpClient(new URL("http://" + info.routeServer));
						
						Map<String, String> param = new HashMap<String, String>();
						String name = null;
						for(Enumeration<String> enums = request.getParameterNames(); enums.hasMoreElements();){
							name = enums.nextElement();
							param.put(name, request.getParameter(name));
						}
						param.put("user", userObj.name);
						
						HttpResponse resp = http.post("/", param);
						response.getWriter().println(resp.getResponseMessage());
					}else {
						response.getWriter().println("9:BTS not connect to route server");
					}
				}
			}
		}
	}
	
	private void outputStationTreeInfo(Location root, PrintWriter out, RouteServerManager rm, String mode){
		Queue<Location> nodes = new ArrayDeque<Location>(500);
		nodes.add(root);
		out.println(getLocationInfo(null, root));
		Location node = null;
		int count = 0;
		while(nodes.size() > 0){
			node = nodes.poll();
			for(Location sub: node.children){
				out.println(getLocationInfo(node, sub));
				nodes.add(sub);
				count++;
				if(count % 100 == 0)out.flush();
			}
			for(BaseStation sub: node.listBTS){
				out.println(getStationInfo(node, sub, rm, mode));
				count++;
				if(count % 100 == 0)out.flush();
			}
		}
	}
	
	private String getLocationInfo(Location root, Location node){
		String data = null;
		data = node.uuid + "$3$0$0";
		if(root != null){
			data += "$" + root.uuid;
		}else {
			data += "$0";
		}
		data += "$" + node.name;
		
		return data;
	}
	
	private String getStationInfo(Location root, BaseStation info, RouteServerManager rm, String mode){
		String data = null;
		String routeAddr = info.routeServer;
		if(mode != null && mode.equals("socket")){
			RouteServer route = rm.getRouteReserver(info.routeServer);
			if(route != null){
				routeAddr = route.getConnectAddr(mode);
			}else {
				routeAddr = null;
			}
		}
		routeAddr = routeAddr == null || "".equals(routeAddr.trim()) 
					? "0": routeAddr;
		
		/**
		 * 用于在外网测试的时候，做地址转换。比如在外网通过NAT访问，在服务器上注册的地址
		 * 是192.168.1.2.需要得到对应的外网地址。
		 */
		routeAddr = server.settings.getString(routeAddr.replace('.', '_').replace(':', '_'),
											  routeAddr);
		
		data = info.uuid + "$" + info.devType + "$" +routeAddr + "$" + info.getStatus();
		if(root != null){
			data += "$" + root.uuid;
		}else {
			data += "$0";
		}
		data += "$" + info.getName();
		data += "$" + info.channels;
		
		return data;
	}
	
	private void outputStationInfo(Collection<BaseStation> list, PrintWriter out, RouteServerManager rm, String mode){
		for(BaseStation info: list){
			out.println(getStationInfo(null, info, rm, mode));
		}
	}
	
	private void outputAlarmList(QueryResult result, PrintWriter out){
		out.println(String.format("0:alarm list$%s$%s$%s", result.count, result.data.size(), result.sessionId));
		AlarmRecord alarm = null;
		String data = null;
		String endTime = "", startTime = "";
		for(Iterator iter = result.data.iterator(); iter.hasNext();){
			alarm = (AlarmRecord)iter.next();
			endTime = alarm.endTime != null ? format.format(alarm.endTime) : "0";
			startTime = alarm.startTime != null ? format.format(alarm.startTime) : "0";
			data = String.format("%s$%s$%s$%s$%s$%s$%s$%s$%s", alarm.uuid, alarm.alarmCode, alarm.getChannelId(),
					alarm.alarmStatus, alarm.getLevel(),
					//format.format(alarm.startTime),
					startTime,
					endTime,
					alarm.alarmCategory, 
					"001"
					);
			out.println(data);
		}
		out.println();
	}	
	
	//private String md5(String str)
	
	private String md5(String str){
	    MessageDigest messageDigest = null;
		try {
			messageDigest = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e1) {
		}
		if(messageDigest != null){
			messageDigest.reset();
			messageDigest.update(str.getBytes(Charset.forName("UTF8")));
			final byte[] resultByte = messageDigest.digest();
		    String result = "";
		    for(byte e: resultByte){
		    	result += String.format("%x", e);
		    }
		    return result;			
		}
		return "n/a";
	}
}
