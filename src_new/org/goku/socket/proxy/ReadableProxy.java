package org.goku.socket.proxy;

import java.io.IOException;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.goku.socket.SelectionHandler;

/**
 * 处理代理的转发操作。
 * @author deon
 *
 */
public class ReadableProxy implements SelectionHandler, Runnable {
	private Log log = LogFactory.getLog("proxy.conn");
	private SocketChannel inChannel = null;
	private SocketChannel outChannel = null;
	private SelectionKey selectionKey = null;
	
	private String idName;
	
	protected ByteBuffer readBuffer = ByteBuffer.allocate(1024 * 16);
	
	public ReadableProxy(SocketChannel inChannel, SocketChannel outChannel){
		this.inChannel = inChannel;
		this.outChannel = outChannel;
		
		idName = inChannel.socket().getInetAddress() + "->" + outChannel.socket().getInetAddress();
		try {
			this.inChannel.socket().setReceiveBufferSize(readBuffer.capacity());
			this.outChannel.socket().setSendBufferSize(readBuffer.capacity());
		} catch (SocketException e) {
			log.error(e.toString(), e);
		}
	}

	@Override
	public void setSelectionKey(SelectionKey key) {
		this.selectionKey = key;
	}

	@Override
	public void run() {
		synchronized(this){
			try {
				if(this.selectionKey.isReadable()){
					readBuffer.clear();
					int len = inChannel.read(readBuffer);
					readBuffer.flip();
					if(log.isDebugEnabled()){
						log.debug(String.format("Read data size:%s, from:%s", readBuffer.remaining(), idName));
					}
					if(len > 0){
						while(readBuffer.remaining() > 0){
							outChannel.write(readBuffer);
							try {
								Thread.sleep(100);
							} catch (InterruptedException e) {
							}
						}
						if(this.selectionKey.isValid()){
							this.selectionKey.interestOps(SelectionKey.OP_READ);
							this.selectionKey.selector().wakeup();
						}
					}else {
						this.close();
					}
				}else {
					log.warn("closed channel.");
				}
			} catch (IOException e) {
				log.error(e.toString(), e);
				this.close();
			}
		}
	}
	
	public void close(){
		log.debug("close:" + idName);
		try {
			if(inChannel != null && inChannel.isOpen()){
				inChannel.close();
			}
		} catch (IOException e) {
			log.error(e.toString(), e);
		}
		try{
			if(outChannel != null && outChannel.isOpen()){
				outChannel.close();
			}
		} catch (IOException e) {
			log.error(e.toString(), e);
		}
	}
}
