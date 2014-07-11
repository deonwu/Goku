package org.goku.socket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.ThreadPoolExecutor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * 实现Socket的连接管理，在Socket有可读数据时分配一个线程处理数据。
 * @author deon
 */
public class SocketManager implements Runnable{
	private Log log = LogFactory.getLog("main");
	private Selector selector = null;
	private ThreadPoolExecutor threadPool = null;
	private boolean isRunning = false;
	
	private Collection<ChangeRequest> paddings = new ArrayList<ChangeRequest>();//#this.pendingData
	
	public SocketManager(ThreadPoolExecutor threadPool) throws IOException{
		selector = SelectorProvider.provider().openSelector(); 		
		this.threadPool = threadPool;
		//threadPool.execute(this);
	}
	
	public SocketChannel connect(String host, int port, Runnable handler){
		SocketChannel socketChannel = null;
		if(!isRunning){
			log.warn("The SocketManager have not running.");
		}
		try{
			socketChannel = SocketChannel.open();
			if(handler instanceof ChannelHandler){
				((ChannelHandler)handler).setSocketChannel(socketChannel);
			}
			socketChannel.socket().setSoTimeout(5 * 1000);
			socketChannel.configureBlocking(false);
			socketChannel.connect(new InetSocketAddress(host, port));		
			log.info("connecting to " + host + ":" +port);		
			this.register(socketChannel, SelectionKey.OP_CONNECT, handler);
		}catch(IOException e){
			log.error("Failed to connect " +  host  + ":" + port);
		}
		return socketChannel;
	}
	
	public ServerSocketChannel listen(String ip, int port, Runnable handler){
		if(!isRunning){
			log.warn("The SocketManager have not running.");
		}
		ServerSocketChannel serverChannel = null;
		try{
			serverChannel = ServerSocketChannel.open();
			if(handler instanceof ChannelHandler){
				((ChannelHandler)handler).setSocketChannel(serverChannel);
			}		
			serverChannel.socket().bind(new InetSocketAddress(port)); 
			serverChannel.configureBlocking(false); 
			this.register(serverChannel, SelectionKey.OP_ACCEPT, handler);
			log.info(String.format("Binding port %d for %s", port, handler.toString()));
		}catch(IOException e){
			log.error("Failed to bind port " + port, e);
		}
		
		return serverChannel;
	}
	
	/**
	 * 不同的线程注册Selector会被阻塞。先保存注册结果，由selecting线程完成操作。
	 * @param channel
	 * @param ops
	 * @param att
	 * @return
	 * @throws ClosedChannelException
	 */
	public void register(SelectableChannel channel, int ops, Object att) 
		throws ClosedChannelException{
		//SelectionKey key = channel.register(this.selector, ops, att);		
		synchronized(paddings){
			this.paddings.add(new ChangeRequest(channel, ops, att));
			this.selector.wakeup();
		}
	}

	@Override
	public void run() {
		log.info("The ChannelSelector is started..");		
		isRunning = true;
		while(isRunning){
			try{
				synchronized(paddings){
					for(ChangeRequest req: this.paddings){
						if(!req.channel.isOpen())continue;
						SelectionKey key = req.channel.register(this.selector, req.ops, req.att);
						if(req.att instanceof SelectionHandler){
							((SelectionHandler)req.att).setSelectionKey(key);
						}
					}
					this.paddings.clear();
				}
				//log.info("process selected....");
					
				this.selector.select();
				Iterator<SelectionKey> selectedKeys = this.selector.selectedKeys().iterator();
				while (selectedKeys.hasNext()) {
					SelectionKey key = selectedKeys.next();
					//log.info("process key:" + key.toString());
					selectedKeys.remove();
					if(!key.isValid()){
						continue;
					}
					/*
					 * 避免当前Key再次被select,导致多一个线程同时处理相同的collection.
					 * 数据处理完成后，需要重新设置需要监控的操作。
					 */					
					key.interestOps(key.interestOps() & ~key.readyOps());
					
					Object att = key.attachment();
					if(att != null && att instanceof Runnable){
						this.threadPool.execute((Runnable)att);
					}else {
						log.warn("The selection key have not runnable attachment.");
						key.cancel();
					}
				}
			}catch(IOException e){
				log.error(e.toString(), e);
			}
		}
		log.info("The ChannelSelector is shutting down..");
	}
	
	
	public void shutdown(){
		this.isRunning = false;
		this.selector.wakeup();		
	}
	
	class ChangeRequest{
		SelectableChannel channel = null;
		int ops = 0;
		Object att = null;
		public ChangeRequest(SelectableChannel channel, int ops, Object att){
			this.channel = channel;
			this.ops = ops;
			this.att = att;
		}
	}

}
