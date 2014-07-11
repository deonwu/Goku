package org.goku.socket;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.goku.core.model.User;

/**
 * 用于当Socket有可读数据时，调用读操作，读一行有效的命令，再传给SocketServer处理。
 * 主要作用是为了非阻塞Socket操作的一个数据处理接口。
 * 
 * 另外保存一些和客户端相关的描述信息。
 * @author deon
 */
public class SocketClient implements SelectionHandler, Runnable {
	public static final int MODE_HTTP = 1;
	public static final int MODE_REALLPLY = 2;
	public static final int MODE_REPLAY = 3;
	
	public SocketChannel socket = null;
	
	/**
	 * 用户登录Session ID
	 */
	public String sessionId = null;
	
	/**
	 * 用户登录Session的用户名。
	 */
	public User loginUser = null;
	public String encoding = "unicode";
	public int connectionMode = MODE_HTTP;
	
	public FileReplayController replay = null;
	//public VideoDestination realPlay = null;
	//public int connectionMode = MODE_HTTP;
	
	protected SelectionKey selectionKey = null;	
	protected SimpleSocketServer server = null;
	private Log log = LogFactory.getLog("client.socket");
	
	protected ByteBuffer readBuffer = ByteBuffer.allocate(1024);
	protected StringBuffer curCmd = null;
	private String remoteIP = null;
	private Queue<ByteBuffer> writeQueue = new ArrayDeque<ByteBuffer>(100);
	
	private long lastBenckmark = 0, writeSize = 0, lastDropPackage = 0;
	//private long startDropTime = 0;
	private double writeSpeed = 0;
	
	//用来缓存相同消息的上次读到的时间，避免输出大量的相同消息log.
	public Map<String, Long> cmdDebug = new HashMap<String, Long>();
	
	

	public SocketClient(SocketChannel client, SimpleSocketServer server){
		this.socket = client;
		this.server = server;
		remoteIP = socket.socket().getRemoteSocketAddress().toString();
		lastBenckmark = System.currentTimeMillis();
		//socket.socket().setKeepAlive(true);
	}

	/**
	 * 有可读数据时，调用处理数据。
	 */
	public void run() {
		try {
			if(this.selectionKey.isReadable()){
				this.read();
				if(this.selectionKey.isValid()){
					this.selectionKey.interestOps(selectionKey.interestOps() | SelectionKey.OP_READ);
					this.selectionKey.selector().wakeup();
				}
			}
			if(this.selectionKey.isValid() && this.selectionKey.isWritable() && this.writeQueue.size() > 0){
				this.writeBuffer();
			}
		} catch (Exception e) {
			log.error(e.toString(), e);
			this.selectionKey.cancel();
			this.closeSocket();
		}
	}
	
	protected void writeBuffer() throws IOException{
		synchronized(this.writeQueue){
			ByteBuffer buffer = this.writeQueue.peek();
			while(buffer != null){
				writeSize += this.socket.write(buffer);
				if(buffer.hasRemaining()){
					this.selectionKey.interestOps(selectionKey.interestOps() | SelectionKey.OP_WRITE);
					this.selectionKey.selector().wakeup();
					break;
				}else {
					this.writeQueue.remove();
					buffer = this.writeQueue.peek();
				}
			}
			this.writeQueue.notifyAll();
		}
		
		if(System.currentTimeMillis() - this.lastBenckmark > 1000){
			if(this.writeSize == 0){ //一秒内没有写任何数据，认为网络连接已断开。
				this.closeSocket();
			}else {
				writeSpeed = this.writeSize * 1.0 / (System.currentTimeMillis() - this.lastBenckmark);
				this.writeSize = 0;
				this.lastBenckmark = System.currentTimeMillis();
			}
			
			if(log.isDebugEnabled()){
				log.debug(String.format("The client '%s' write speed %1.3f Kb/s.", this.toString(), this.writeSpeed));
				if(this.writeQueue.size() > 0){
					log.warn(String.format("The client too slow, Write buffer queue size:%s, to:%s", this.writeQueue.size(), toString()));
				}
			}
		}
	}
	
	/**
	 * 读Socket Buffer让后传给Socket服务器处理，每次读一行空行丢弃。
	 * @throws IOException 
	 */
	public void read() throws IOException{
		synchronized(this){
			readBuffer.clear();
			int readLen = this.socket.read(readBuffer);
			if(readLen == -1){
				this.closeSocket();
			}else{
				readBuffer.flip();
				//if(log.isDebugEnabled()){
				//    log.debug(String.format("Read data size:%s, from:%s", readBuffer.remaining(), toString()));
				//}
				this.processBuffer(readBuffer);
			}
		}
	}
	
	protected void write(byte[] src) throws IOException{
		ByteBuffer buffer = ByteBuffer.allocate(src.length);
		buffer.put(src);
		buffer.flip();
		this.write(buffer, true);
	}
	
	public void write(ByteBuffer src, boolean sync) throws IOException{
		if(log.isDebugEnabled() && 
		   src.remaining() < 4096 //不写视频的日志，避免日志量太大了。
		  ){ 
			log.debug(String.format("write buffer size:%s, to:%s", src.remaining(), toString()));
		}

		if(this.writeBusy()){
			this.writeBuffer();
			if(this.socket.socket().isClosed()) throw new IOException("socket is closed.");
			//5秒内不重复输出警告消息。
			if(System.currentTimeMillis() - lastDropPackage > 5000){
				log.warn("Socket client is too slow, start to drop the write package. client:" + toString());
				lastDropPackage = System.currentTimeMillis();
				//重新注册写操作。
				this.selectionKey.interestOps(selectionKey.interestOps() | SelectionKey.OP_WRITE);
				this.selectionKey.selector().wakeup();
			}
		}else{
			if(this.writeQueue.offer(src)){
				//如果当前Socket没有注册写操作.
				if(this.writeQueue.size() == 1 &&
				  (this.selectionKey.interestOps() & SelectionKey.OP_WRITE) != SelectionKey.OP_WRITE){
					this.selectionKey.interestOps(selectionKey.interestOps() | SelectionKey.OP_WRITE);
					this.selectionKey.selector().wakeup();
				}
				while(sync && src.remaining() > 0){
					synchronized(writeQueue){
						try {
							writeQueue.wait();
						} catch (InterruptedException e) {
						}
					}
				}
			}else {
				log.warn("Write buffer queue full, client:" + this.toString());				
			}
		}
	}
	
	/**
	 * 如果写缓存队列大于5个包，认为客户端太忙.
	 * @return
	 */
	public boolean writeBusy(){
		return this.writeQueue.size() > 100;
	}
	
	protected void processBuffer(ByteBuffer in){
		if(curCmd == null) curCmd = new StringBuffer();
		byte data = 0;
		while(in.hasRemaining()){
			data = in.get();
			if(data == '\n'){
				String cmd = this.curCmd.toString().trim();
				if(cmd.length() > 0){
					//if(!cmdDebug.containsKey(cmd) )
					this.server.processClient(cmd, this);
				}
				curCmd = new StringBuffer();
			}else {
				curCmd.append((char)data);
			}
			//log.info("cmd:" + curCmd);
		}
		if(curCmd.length() > 0){
			log.debug("incomplete command in buffer:" + this.curCmd.toString());
		}
	}
	
	/**
	 * 由Selection线程调用。
	 * @param key
	 */
	public void setSelectionKey(SelectionKey key){
		this.selectionKey = key;
	}
	
	public void close(){
		this.closeSocket();
	}
	
	public void closeSocket(){
		log.info("Close socket, " + toString());
		this.writeQueue.clear();
		if(this.selectionKey != null){
			this.selectionKey.cancel();
		}
		if(this.socket != null){
			try {
				this.socket.close();
			} catch (IOException e) {
				log.error(e.toString(), e);
			}
		}
	}
	
	public String toString(){
		String remoteIp = this.remoteIP;
		if(this.loginUser != null){
			remoteIp = loginUser.name + "@" + remoteIp;
		}
		return remoteIp;
	}
}
