package org.goku.socket;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.goku.video.VideoRecorderManager;

public class SimpleSocketServer implements Runnable, SelectionHandler {
	private Log log = LogFactory.getLog("server.socket");
	private SocketManager manager = null;
	private ServerSocketChannel serverChannel = null;
	protected SelectionKey selectionKey = null;
	
	public SocketAdaptor httpAdapter = null;
	public SocketAdaptor videoAdapter = null;
	public SocketAdaptor imageAdapter = null;
	
	private String servelt = null;
	private VideoRecorderManager recordManager = null;
	
	/**
	 * 对象自身的一个引用，因为serverHandler内部需要使用。可能会引起对象无法内存回
	 * 收。SimpleSocketServer本身不应该被大量创建。
	 */
	private SimpleSocketServer server = null;
	private boolean started = false;
	
	public int listenPort = 0;
	
	public SimpleSocketServer(SocketManager manager, int nPort){
		this.manager = manager;
		this.listenPort = nPort;
		server = this;
	}

	@Override
	public void run() {
		//第一次运行初始化Server.
		if(!started){
			started = true;
			httpAdapter = new SocketHTTPAdaptor(servelt);
			if(recordManager != null){
				videoAdapter = new SocketVideoAdapter();
				((SocketVideoAdapter)videoAdapter).setRecorderManager(recordManager);
			}
			serverChannel = manager.listen("0.0.0.0", this.listenPort, this);
		}else if(selectionKey.isAcceptable()){
			try {
				SocketChannel client = serverChannel.accept();
				log.debug("Accept client:" + client.socket().getRemoteSocketAddress());
				client.socket().setSoTimeout(30 * 1000);
				client.configureBlocking(false);
				client.socket().setTcpNoDelay(true);
				client.socket().setKeepAlive(true);
				SocketClient handler = new SocketClient(client, server);
				
				manager.register(client, SelectionKey.OP_READ, handler);
				selectionKey.interestOps(SelectionKey.OP_ACCEPT);
				
				selectionKey.selector().wakeup();
			} catch (IOException e) {
				log.error(e.toString(), e);
			}
		}
	}
	
	/**
	 * 处理客户端的输入命令。
	 * @param data
	 * @param client
	 */
	public void processClient(String data, SocketClient client){
		if(log.isDebugEnabled() &&
		   (!client.cmdDebug.containsKey(data) ||
		     System.currentTimeMillis() - client.cmdDebug.get(data) > 5000
		   )){
			client.cmdDebug.put(data, System.currentTimeMillis());
			log.debug(String.format("processing comand '%s' read from %s", data, client.toString()));
		}
		try{
			if(data.startsWith("cmd>")){
				if(client.connectionMode == SocketClient.MODE_HTTP){
					this.httpAdapter.runCommand(data, client);
				}else {
					log.debug("can't execute control command on video channel.");
				}
			}else if(data.startsWith("video>")){
				this.videoAdapter.runCommand(data, client);
			}else if(data.startsWith("img>")){
				if(this.imageAdapter != null){
					this.imageAdapter.runCommand(data, client);
				}else {
					log.debug("The imageAdapter have not create, or It's not a image rout server.");
				}
			}else {
				if(client.connectionMode == SocketClient.MODE_HTTP){
					String error = String.format("Drop unkonw command:'%s', the valid prefix are 'cmd>', 'video>' and 'img>'.", data);
					client.write(error.getBytes());
				}
			}
		}catch(IOException e){
			log.error(e.toString(), e);
			client.closeSocket();
		}
	}
	
	public void setServlet(String servlet){
		this.servelt = servlet;
	}
	
	public void setRecorderManager(VideoRecorderManager manager){
		this.recordManager = manager;
	}	
	
	/**
	 * 由Selection线程调用。
	 * @param key
	 */
	public void setSelectionKey(SelectionKey key){
		this.selectionKey = key;
	}
	
	public String toString(){
		return "SimpleSocketServer";
	}

}

