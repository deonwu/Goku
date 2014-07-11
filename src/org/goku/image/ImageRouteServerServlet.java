package org.goku.image;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.goku.http.BaseRouteServlet;
import org.json.simple.JSONValue;

public class ImageRouteServerServlet extends BaseRouteServlet {
	private ImageRouteServer server = null;
	private Log log = LogFactory.getLog("http");
	
	public void init(ServletConfig config){
		server = ImageRouteServer.getInstance();
	}	

	@Override
	protected void index_page(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		static_serve("org/goku/image/statics/help_doc.txt", "text/plain", response);

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
	
	/**
	 * 图片服务器，目前没有实现多个服务器，做负载调度。不需要删除操作。
	 */
	public void del_bs(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		String uuid = this.getStringParam(request, "uuid", null);
		if(uuid == null){
			response.getWriter().println("-2:Parameter error");
		}else {
			response.getWriter().println("0:Disconnect BTS");
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
    	response.getWriter().println("Time:" + "OK");
    }
    
    public void restart(HttpServletRequest request, HttpServletResponse response) 
	throws IOException {
		String uuid = this.getStringParam(request, "uuid", null);
		final Map<String, String> result = new HashMap<String, String>();
		result.put("status", "error");
		if(uuid == null){
			result.put("msg", "-2:Parameter error");
		}else {			
			ASC100Client ascClient = server.getMonitorClient(uuid);
			if(ascClient == null){
				result.put("msg", "1$Not found base station");
			}else {
				result.put("status", "timeout");
				ImageClientListener l = new AbstractImageListener() {
					public void message(ImageClientEvent event){
						if(event.data.len == 2){
							ByteBuffer b = event.data.inBuffer.asReadOnlyBuffer();
							if(b.get() == 3 && b.get() == 5){
								if(event.data.cmd == 2){
									result.put("status", "err");
								}else if(event.data.cmd == 0){
									result.put("status", "ok");
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
			}			
		}	
		JSONValue.writeJSONString(result, response.getWriter());	    	
    }      
    
    public void get_date(HttpServletRequest request, HttpServletResponse response) 
	throws IOException {
		String uuid = this.getStringParam(request, "uuid", null);
		final Map<String, String> result = new HashMap<String, String>();
		result.put("status", "error");
		if(uuid == null){
			result.put("msg", "-2:Parameter error");
		}else {			
			ASC100Client ascClient = server.getMonitorClient(uuid);
			if(ascClient == null){
				result.put("msg", "1$Not found base station");
			}else {
				result.put("status", "timeout");
				ImageClientListener l = new AbstractImageListener() {
					public void message(ImageClientEvent event){
						if(event.data.len == 8){
							ByteBuffer b = event.data.inBuffer.asReadOnlyBuffer();
							if(b.get() == 2 && b.get() == 1){
								result.put("status", "ok");
								result.put("date", String.format("20%02x-%02x-%02x %02x:%02x:%02x",
										b.get(), b.get(), b.get(), 
										b.get(), b.get(), b.get()));
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
			}			
		}	
		JSONValue.writeJSONString(result, response.getWriter());	    	
    }    
    
    public void load_version(HttpServletRequest request, HttpServletResponse response)
    throws IOException {
		String uuid = this.getStringParam(request, "uuid", null);
		final Map<String, String> result = new HashMap<String, String>();
		result.put("status", "error");
		if(uuid == null){
			result.put("msg", "-2:Parameter error");
		}else {			
			ASC100Client ascClient = server.getMonitorClient(uuid);
			if(ascClient == null){
				result.put("msg", "1$Not found base station");
			}else {
				result.put("status", "timeout");
				ImageClientListener l = new AbstractImageListener() {
					public void message(ImageClientEvent event){
						if(event.data.len == 5){
							ByteBuffer b = event.data.inBuffer.asReadOnlyBuffer();
							if(b.get() == 2 && b.get() == 12){
								result.put("status", "ok");
								result.put("version", String.format("%c.%c%c",
										b.get(), b.get(), b.get() 
										));
								synchronized(this){
									this.notifyAll();
								}
							}
						}
					};
				};
				ascClient.addListener(l);		
				ascClient.getDevVersion();
				try {
					synchronized(l){
						l.wait(1000 * 10);
					}
				} catch (InterruptedException e) {
				}finally{
					ascClient.removeListener(l);
				}				
			}			
		}	
		JSONValue.writeJSONString(result, response.getWriter());    	
    }

    public void set_date(HttpServletRequest request, HttpServletResponse response) 
	throws IOException {
		String uuid = this.getStringParam(request, "uuid", null);
		String date = this.getStringParam(request, "date", null);
		final Map<String, String> result = new HashMap<String, String>();
		result.put("status", "error");
		if(uuid == null || date == null){
			result.put("msg", "-2:Parameter error");
		}else {			
			ASC100Client ascClient = server.getMonitorClient(uuid);
			if(ascClient == null){
				result.put("msg", "1$Not found base station");
			}else {
				result.put("status", "timeout");
				ImageClientListener l = new AbstractImageListener() {
					public void message(ImageClientEvent event){
						if(event.data.len == 2){
							ByteBuffer b = event.data.inBuffer.asReadOnlyBuffer();
							if(b.get() == 1){
								if(event.data.cmd == 2){
									result.put("status", "error");
								}else if(event.data.cmd == 0){
									result.put("status", "ok");
								}
 								synchronized(this){
									this.notifyAll();
								}
							}
						}
					};
				};
				ascClient.addListener(l);
				date = date.replaceAll("[^0-9]+", "").substring(2);
				log.debug("set date:" + date);
				ascClient.setDateTime(date);
				try {
					synchronized(l){
						l.wait(1000 * 10);
					}
				} catch (InterruptedException e) {
				}finally{
					ascClient.removeListener(l);
				}				
			}			
		}	
		JSONValue.writeJSONString(result, response.getWriter());	    	
    }
    
    public void save_param(HttpServletRequest request, HttpServletResponse response) 
	throws IOException {
		String uuid = this.getStringParam(request, "uuid", null);
		String param = this.getStringParam(request, "param", null);
		final Map<String, String> result = new HashMap<String, String>();
		result.put("status", "error");
		if(uuid == null || param == null){
			result.put("msg", "-2:Parameter error");
		}else {
			String[] p = param.split(",");
			byte[] bParam = new byte[p.length];
			for(int i = 0; i < p.length; i++){
				bParam[i] = Byte.parseByte(p[i]);
			}			
			ASC100Client ascClient = server.getMonitorClient(uuid);
			if(ascClient == null){
				result.put("msg", "1$Not found base station");
			}else {
				result.put("status", "timeout");
				ImageClientListener l = new AbstractImageListener() {
					public void message(ImageClientEvent event){
						if(event.data.len == 2){
							ByteBuffer b = event.data.inBuffer.asReadOnlyBuffer();
							if(b.get() == 1){
								result.put("status", "ok");
								synchronized(this){
									this.notifyAll();
								}
							}
						}
					};
				};
				ascClient.addListener(l);		
				try {
					this.log.debug(String.format("Set image param:%s", param));
					if(ascClient.saveParam(bParam)){
						synchronized(l){
							l.wait(1000 * 10);
						}
					}else {
						result.put("status", "image");
					}
				} catch (InterruptedException e) {
				}finally{
					ascClient.removeListener(l);
				}				
			}			
		}	
		JSONValue.writeJSONString(result, response.getWriter());		
    }
    
    public void read_param(HttpServletRequest request, HttpServletResponse response) 
	throws IOException {
		String uuid = this.getStringParam(request, "uuid", null);
		final Map<String, String> result = new HashMap<String, String>();
		result.put("status", "error");
		if(uuid == null){
			result.put("msg", "-2:Parameter error");
		}else {
			ASC100Client ascClient = server.getMonitorClient(uuid);
			if(ascClient == null){
				result.put("msg", "1$Not found base station");
			}else {
				result.put("status", "timeout");
				ImageClientListener l = new AbstractImageListener() {
					public void message(ImageClientEvent event){
						if(event.data.len == 32){
							ByteBuffer b = event.data.inBuffer.asReadOnlyBuffer();
/*							
"X固定为2表示读取命令，Y固定为4，表示读取当前设备参数，
A(通道1模式)，B(通道2模式)，C(亮度)，D(对比度)，E(饱和度)，F(色调)，

G(敏感度)，H(压缩比)，I(分辨率)，J(告警图片数量)，K(门禁联动开关设置)，
L(串口流控参数(L1，L2)，(T1，T2))，M(错误机制设置(T1，T2)，(Cnt1))，N图片策略(单字节), O(通道3模式)，P(通道4模式)，
(其它字节预留)
"*/							byte b1 = b.get();
							byte b2 = b.get();
							if(b1 != 2 || b2 != 4)return;
							
							result.put("status", "ok");
							result.put("mode_ch1", String.format("%x", b.get()));
							result.put("mode_ch2", String.format("%x", b.get()));
							
							int d = b.get();
							result.put("color_x", (d < 0 ? d + 256 : d) + "");
							d = b.get();
							result.put("color_y", (d < 0 ? d + 256 : d) + "");
							d = b.get();
							result.put("color_z", (d < 0 ? d + 256 : d) + "");
							d = b.get();
							result.put("color_a", (d < 0 ? d + 256 : d) + "");
							
							d = b.get();
							result.put("mg_ch", (d < 0 ? d + 256 : d) + "");
							d = b.get();
							result.put("zip_rate", (d < 0 ? d + 256 : d) + "");
							//分辨率
							d = b.get();
							result.put("fbl", (d < 0 ? d + 256 : d) + "");
							
							d = b.get();
							result.put("image_count", (d < 0 ? d + 256 : d) + "");
							//门禁联动
							d = b.get();
							result.put("mjld", (d < 0 ? d + 256 : d) + "");
							b.get();
							b.get();
							b.get();
							b.get();
							b.get();
							b.get();
							b.get();
							b.get();
							//空5个字节,兼容老设备
							b.get();
							b.get();
							b.get();
							b.get();
							b.get();
							
							result.put("mode_ch3", String.format("%x", b.get()));
							result.put("mode_ch4", String.format("%x", b.get()));							
							synchronized(this){
								this.notifyAll();
							}
						}
					};
				};
				ascClient.addListener(l);		
				try {
					if(ascClient.readParam()){
						synchronized(l){
							l.wait(1000 * 10);
						}
					}else {
						result.put("status", "image");
					}
				} catch (InterruptedException e) {
				}finally{
					ascClient.removeListener(l);
				}				
			}			
		}	
		JSONValue.writeJSONString(result, response.getWriter());		
    }

}
