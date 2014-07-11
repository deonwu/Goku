package org.goku.socket;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * 把字符串，封装成为一个Sevlet API调用Sevelet来处理，统一HTTP和Socket的服务
 * 端处理方式。
 * 
 * 命令格式：
 * 
 * cmd>login?uuid=123&xxx=bbb
 */

public class SocketHTTPAdaptor {
	private Log log = LogFactory.getLog("client.socket.http");
	private String servlet = null;
	private HttpServlet httpServelt = null;
	
	public SocketHTTPAdaptor(String servlet){
		try{
			Class clazz = Class.forName(servlet);
			httpServelt = (HttpServlet)clazz.newInstance();
			httpServelt.init(null);
		}catch(Exception e){
			log.error(e.toString(), e);
		}
	}
	
	public void runCommand(String command, SocketClient client) throws IOException{
		//log.info("run command:" + command);
		Map<String, String> param = parseCommand(command);
		if(client.sessionId != null){
			param.put("sid", client.sessionId);
		}
		param.put("mode", "socket");
		
		DummyHttpRequest req = new DummyHttpRequest(param, client);
		DummyHttpResponse resp = new DummyHttpResponse(client);
		try {
			httpServelt.service(req, resp);
			resp.flushBuffer();
		} catch (ServletException e) {
			log.error(e.toString(), e);
			client.write("505:System error".getBytes());
		}
	}
	
	protected Map<String, String> parseCommand(String command){
		Map<String, String> p = new HashMap<String, String>();
		command = command.replace("cmd>", "");
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

}
