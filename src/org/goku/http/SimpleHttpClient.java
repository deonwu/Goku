package org.goku.http;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.SSLSocketFactory;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class SimpleHttpClient {
	private Log log = LogFactory.getLog("SimpleHttpClient");
	private boolean hasProxy = false;
	private URL requestURL = null;
	private Socket socket = null;
	
	private HttpResponse response = null;
	
	private Map<String, String> header = new HashMap<String, String>();
	private byte[] body = null;
	
	private ByteArrayOutputStream buffer = new ByteArrayOutputStream(1024);
	private PrintWriter out = null;//new PrintWriter(buffer);
	//private LineReader in = null;
	
	private BufferedInputStream bis = null;

	public SimpleHttpClient(URL url){
		this.requestURL = url;
		this.hasProxy = System.getProperty("http.proxyHost", null) != null;
	}	
	
	public HttpResponse get(String uri, Map<String, String> param) throws IOException{
		return get(uri, param, new HashMap<String, String>());
	}		
	
	public HttpResponse get(String uri, Map<String, String> param, Map<String, String> header) throws IOException{
		this.createSocket();
		synchronized(this.socket){
			log.trace("-----Start GET--------");
			this.createHttpRequest("GET", uri);
			this.setHeader(header);
			this.buildBody(param);
			this.commit();
			this.processResponse();
			log.trace("-----end get--------");
		}
		this.close();
		return this.response;
	}
	
	public HttpResponse post(String uri, Map<String, String> param) throws IOException{
		return post(uri, param, new HashMap<String, String>());
	}	
	
	
	public HttpResponse post(String uri, Map<String, String> param, Map<String, String> header) throws IOException{
		this.createSocket();
		synchronized(this.socket){
			log.trace("-----Start port--------");
			this.createHttpRequest("POST", uri == null ? "/" + this.requestURL.getPath(): uri);
			this.setHeader(header);
			this.buildBody(param);		
			this.commit();
			this.processResponse();
			log.trace("-----end port--------");
		}
		this.close();
		return this.response;
	}	
	
	public HttpResponse post(Map<String, String> param, Map<String, String> head) throws IOException{
		this.createSocket();
		synchronized(this.socket){
			log.trace("-----Start port--------");
			this.createHttpRequest("POST", "/" + this.requestURL.getPath());
			this.setHeader(head);
			this.buildBody(param);
			this.commit();	
			log.trace("----wait response------------------");
			this.processResponse();
			log.trace("-----end port--------");
		}
		this.close();
		return this.response;
	}
	
	private void processResponse() throws IOException{
		response = new HttpResponse();
		response.connection = this;
		String status = readLine();
		for(int i = 0; i < 10 && status.indexOf("HTTP") != 0;status = readLine(),i++){
			log.trace("Head:" + status);
		}
		log.trace(status);
		if(status.indexOf("HTTP") != 0){
			this.close();
			throw new IOException("Not found HTTP Head, Connection is closed.");
		}
		
		int headerLength = 0, contentLength = 0;
		byte[] content = null;
		
		boolean chunkedContent = false;
		if(status != null && status.indexOf("HTTP") >= 0){
			headerLength += status.length() + 1;
			response.setStatus(status);
			for(String line = readLine(); line != null && line.length() > 0; 
				line = readLine()){
				headerLength += status.length() + 1;
				response.addHeader(line);
				if(line.startsWith("Content-Length:")){
					contentLength = Integer.parseInt(line.split(" ", 2)[1]);
				}else if(line.startsWith("Transfer-Encoding: chunked")){
					chunkedContent = true;
				}
				log.trace(String.format("Head:%s, len:%s", line, line.length()));
			}
			if(chunkedContent){
				contentLength = 1;
				while(contentLength > 0){
					contentLength = HexToInt(readLine());
					if(contentLength > 0){
						content = new byte[contentLength];
						for(;contentLength >0;){
							int count = bis.read(content, content.length - contentLength, contentLength);
							if(count == -1)break;
							contentLength -= count;
						}
						response.setContent(content);
					}
					readLine();
				}
				
			}else if(response.getResponseStatus() != 302){
				content = new byte[contentLength];
				for(;contentLength >0;){
					int count = bis.read(content, content.length - contentLength, contentLength);
					if(count == -1)break;
					contentLength -= count;
				}
				response.setContent(content);
			}
			log.trace("Response:" + response.toString());
		}else {
			throw new IOException("Invalid HTTP response hread:" + status);
		}
	}
	
	private void createHttpRequest(String method, String uri){
		String requestLine = method;
		requestLine += " " + uri;
		requestLine += " HTTP/1.1";
		out.println(requestLine);
		out.println("Host: " + this.requestURL.getHost());
		//out.println("User-Agent: NoteBook/1.0 java1.6 client");
		out.println("User-Agent: Mozilla/5.0 (Windows;en-GB; rv:1.8.0.11) Gecko/20070312 Firefox/1.5.0.11");
		out.println("Accept: text/html;q=0.9,text/plain;q=0.8,*/*;q=0.5");
		out.println("Accept-Language: en-gb,en;q=0.5");
		out.println("Keep-Alive: 300");
		out.println("Connection: keep-alive");
		out.println("Accept-Charset: ISO-8859-1,utf-8;q=0.7,*;q=0.7");
	}
	
	private void commit() throws IOException{
		out.println("Content-Type: application/x-www-form-urlencoded");
		out.println("Content-Length: " + this.body.length);
		out.println("");
		out.flush();
		buffer.write(this.body);
		out.close();
		log.trace("Request:" + buffer.toString());
		
		this.socket.getOutputStream().write(buffer.toByteArray());
		this.socket.getOutputStream().flush();
	}

	private void buildBody(Map<String, String> param) throws IOException{
		//this.header.putAll(head);
		String body = "";
		for(String name: param.keySet()){
			try {
				if(body.length() > 0) body += "&";
				body += name + "=" + URLEncoder.encode(param.get(name), "UTF-8");
			} catch (UnsupportedEncodingException e) {
				throw new IOException("encoding error, param key " + name);
			}
		}
		this.body = body.getBytes();
	}
	
	
	private void setHeader(Map<String, String> head){
		for(String key: head.keySet()){
			out.println(key + ": " + head.get(key));
		}
		//this.header.putAll(head);
	}
	//
	
	private void createSocket() throws IOException{
		if(this.socket == null || this.socket.isClosed() || !this.socket.isConnected()){
			if(requestURL.getProtocol().equals("https")){
				this.createHTTPSSocket();
			}else {
				this.createHTTPSocket();
			}
			bis = new BufferedInputStream(socket.getInputStream(), 4 * 1024);
		}
		buffer.reset();
		//in = new BufferedReader(new InputStreamReader(bis));
		out = new PrintWriter(buffer);
	}
	
	private void createHTTPSocket() throws IOException{
		if(hasProxy){
			String host = System.getProperty("http.proxyHost");
			String proxyPort = System.getProperty("http.proxyPort", "80");
			log.debug("Connect to http proxy:" + host + ", port:" + proxyPort);
			socket = new Socket(host, Integer.parseInt(proxyPort));
		}else {
			int port = requestURL.getPort();
			port = port > 0 ? port : 80;
			log.debug("Connect to http:" + requestURL.getHost() + ", port:" + port);
			socket = new Socket(requestURL.getHost(), port);
		}
	}
	
	private Socket connectHTTPSProxy() throws IOException{
		String host = System.getProperty("http.proxyHost");
		String proxyPort = System.getProperty("http.proxyPort", "80");
		Socket proxySocket = new Socket(host, Integer.parseInt(proxyPort));
		byte[] command = ("CONNECT "+ requestURL.getHost() +":443 HTTP/1.0\n\n").getBytes();
		log.debug("proxy:" + new String(command));
		proxySocket.getOutputStream().write(command);
		proxySocket.getOutputStream().flush();
		
		BufferedReader proxyReader = new BufferedReader(new InputStreamReader(proxySocket.getInputStream()));
		boolean established = false;
		for(String l = proxyReader.readLine(); l != null && l.length() > 0; 
			l = proxyReader.readLine()){
			if(l.startsWith("HTTP") && l.indexOf("200") > 1){
				established = true;
			}
			log.debug("proxy:" + l);
		}
		if(!established)throw new IOException("Failed to established HTTPS connection on proxy, "
				+ host + ":" + proxyPort);
		return proxySocket;
	}
	
	private void createHTTPSSocket() throws IOException{
		int port = requestURL.getPort();
		port = port > 0 ? port : 443;
		
		log.debug("connect to https:" + requestURL.getHost() + ", port:" + port);
		SSLSocketFactory sslsf = (SSLSocketFactory)SSLSocketFactory.getDefault();
		if(hasProxy){
			Socket proxySocket = connectHTTPSProxy();
			socket = sslsf.createSocket(proxySocket, requestURL.getHost(), port, true);
		}else {
			socket = sslsf.createSocket(requestURL.getHost(), port);
		}
	}
		
	public void close() throws IOException{
		if(this.out != null){this.out.close();};
		if(this.socket != null){this.socket.close();};
	};
	
	//public static
	public static HttpResponse post(URL url, Map<String, String> param) throws IOException{
		return new SimpleHttpClient(url).post(param, new HashMap<String, String>());
	}
	
	public static HttpResponse post(URL url, Map<String, String> param, Map<String, String> head) throws IOException{
		return new SimpleHttpClient(url).post(param, head);
	}
	
	public String readLine() throws IOException{
		StringBuffer strBuffer = new StringBuffer();
		for(int i = bis.read(); i != '\n' && i > 0; i = bis.read()){
			strBuffer.append((char)i);
		}
		String xx = strBuffer.toString().trim();
		log.trace("read line:" + xx);
		return xx;
	}
	
	private int HexToInt(String x){
		int ret = 0;
		for(int i : x.trim().getBytes()){
			if(i >= '0' && i <= '9'){
				ret = ret * 16 + i - '0';
			}else if(i >= 'A' && i <= 'F'){
				ret = ret * 16 + i - 'A' + 10;
			}else if(i >= 'a' && i <= 'f'){
				ret = ret * 16 + i - 'a' + 10;
			}
		}
		return ret;
	}	
}
