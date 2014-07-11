package org.goku.socket.proxy;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.goku.socket.ChannelHandler;
import org.goku.socket.SelectionHandler;
import org.goku.socket.SocketManager;

/**
 * Bind监听端口，接受代理连接请求。
 * @author deon
 */
public class ListenProxy implements SelectionHandler, ChannelHandler, Runnable {
	private Log log = LogFactory.getLog("proxy.server");
	
	public long lastActive = 0;
	private SocketManager manager = null;	
	private ServerSocketChannel channel = null;
	private SelectionKey selectionKey = null;
	private String destHost = null;
	private int port = 0;	
	
	public ListenProxy(SocketManager manager, String dest) throws IOException{
		this.manager = manager;
		String[] hostInfo = dest.split(":");
		if(hostInfo.length != 2){
			throw new IOException("Invalid dest host " + dest);
		}
		destHost = hostInfo[0];
		try{
			port = Integer.parseInt(hostInfo[1]);
		}catch(Exception e){
		}
		this.lastActive = System.currentTimeMillis();
	}

	@Override
	public void run() {
		if(this.selectionKey == null){
			log.warn("selectionKey is null");
			return;
		}
		if(this.selectionKey.isAcceptable()){
			try{
				SocketChannel client = channel.accept();
				this.lastActive = System.currentTimeMillis();
				log.debug("Accept proxy client:" + client.socket().getRemoteSocketAddress() + "@" + destHost + ":" + port);
				client.configureBlocking(false);
				client.socket().setTcpNoDelay(true);
				ConnectionProxy proxy = new ConnectionProxy(manager, client);				
				SocketChannel dest = manager.connect(destHost, port, proxy);
				proxy.setSocketChannel(dest);
								
				selectionKey.interestOps(SelectionKey.OP_ACCEPT);
			}catch(IOException e){
				log.error(e + "@" + this.toString(), e);
			}
		}
	}
	
	public int listenPort(){
		return this.channel.socket().getLocalPort();
	}
	
	@Override
	public void setSelectionKey(SelectionKey key) {
		this.selectionKey = key;
	}
	
	public void close(){
		try {
			channel.close();
			log.debug("Close proxy " + toString());
		}catch(IOException e) {
			log.error(e.toString(), e);
		}
	}

	public String toString(){
		return "proxy@" + destHost + ":" + port;
	}

	@Override
	public void setSocketChannel(SelectableChannel key) {
		this.channel = (ServerSocketChannel)key;
	}
}
