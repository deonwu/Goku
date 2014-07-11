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
 * 处理代理连接目标主机的过程。
 * @author deon
 *
 */
public class ConnectionProxy implements SelectionHandler, ChannelHandler, Runnable {
	private Log log = LogFactory.getLog("proxy.server");
	private SocketManager manager = null;
	
	private SocketChannel sourceChannel = null;
	private SocketChannel destChannel = null;
	private SelectionKey selectionKey = null;
	
	private ReadableProxy sourceProxy = null;
	private ReadableProxy destProxy = null;
	
	public ConnectionProxy(SocketManager manager, SocketChannel sourceChannel){
		this.manager = manager;
		this.sourceChannel = sourceChannel;
	}
	

	@Override
	public void run() {
		if(destChannel != null){
			try{
				if(this.selectionKey.isConnectable()){
					destChannel.finishConnect();
					log.info("Created connection to " + destChannel.socket().getRemoteSocketAddress());
					sourceProxy = new ReadableProxy(sourceChannel, destChannel);
					manager.register(sourceChannel, SelectionKey.OP_READ, sourceProxy);
					
					destProxy = new ReadableProxy(destChannel, sourceChannel);
					manager.register(destChannel, SelectionKey.OP_READ, destProxy);				
				}
			}catch(IOException e){
				try {
					log.debug("Can't connect to distination, close the source client, Error:" + e.toString());
					sourceChannel.close();
				} catch (IOException e1) {
					log.error(e1.toString(), e);
				}
			}
		}else {
			log.warn("destChannel is null");
		}
	}

	@Override
	public void setSelectionKey(SelectionKey key) {
		this.selectionKey = key;		
	}
	
	@Override
	public void setSocketChannel(SelectableChannel channel) {
		this.destChannel = (SocketChannel)channel;
	}
}
