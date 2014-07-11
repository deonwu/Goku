package org.goku.socket;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayDeque;
import java.util.Queue;

import org.apache.commons.logging.Log;

public class NIOSession implements Runnable, SelectionHandler {
	private Log log = null;
	private SelectionKey selectionKey = null;
	private SocketListener listener = null;
	private Queue<ByteBuffer> writeQueue = new ArrayDeque<ByteBuffer>(5);
	
	public double readSpeed, writeSpeed;
	public long readSize, writeSize, lastReadTime, lastWriteTime;
	public boolean isRunning = true;
	
	public NIOSession(SocketListener listener, Log log){
		this.listener = listener;
		this.log = log;
	}

	@Override
	public void run() {
		if(!isRunning){
			try{
				this.selectionKey.channel().close();
				this.selectionKey.cancel();
			}catch(IOException e){
				log.error(e.toString(), e);
			}
		}else {
			synchronized(this){
				try {
					if(this.selectionKey.isConnectable()){
						try{
							((SocketChannel)selectionKey.channel()).finishConnect();
							listener.connected(this);
							this.selectionKey.interestOps(selectionKey.interestOps() | SelectionKey.OP_READ);
						}catch(IOException conn){
							selectionKey.channel().close();
							this.selectionKey.cancel();
							this.listener.connect_error(this);
						}
					}else if(this.selectionKey.isReadable()){
						this.listener.read(this);
						if(this.selectionKey.isValid()){
							this.selectionKey.interestOps(selectionKey.interestOps() | SelectionKey.OP_READ);
							this.selectionKey.selector().wakeup();
						}
					}
					if(this.selectionKey.isValid() && this.selectionKey.isWritable() && this.writeQueue.size() > 0){
						this.writeBuffer((SocketChannel)selectionKey.channel());
					}
				} catch (Exception e) {
					log.error(e.toString(), e);
					this.selectionKey.cancel();
					this.close();
				}
			}	
		}
	}
	
	protected void writeBuffer(SocketChannel channel) throws IOException{
		synchronized(this.writeQueue){
			ByteBuffer buffer = this.writeQueue.peek();
			while(buffer != null){
				writeSize += channel.write(buffer);
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
		
		if(System.currentTimeMillis() - this.lastWriteTime > 1000){
			writeSpeed = this.writeSize * 1.0 / (System.currentTimeMillis() - this.lastWriteTime);
			this.writeSize = 0;
			this.lastWriteTime = System.currentTimeMillis();
			
			if(log.isDebugEnabled()){
				log.debug(String.format("Write speed %1.3f Kb/s on %s.", this.writeSpeed, this.listener.toString()));
				if(this.writeQueue.size() > 0){
					log.warn(String.format("Write buffer queue size:%s, on %s", this.writeQueue.size(), this.listener.toString()));
				}
			}
		}
	}
	
	public int read(ByteBuffer buffer){
		int readLen = -1;
		if(this.selectionKey.isReadable()){
			try {
				readLen = ((SocketChannel)this.selectionKey.channel()).read(buffer);
			} catch (IOException e) {
				log.error(e.toString(), e);
			}
			if(readLen == -1){
				this.close();
				//读出错，端口网络连接。
				try {
					this.listener.disconnected(this);
				} catch (IOException e) {
					log.error(e.toString(), e);
				}
			}else {
				this.readSize += readLen;
			}
		}
		if(System.currentTimeMillis() - this.lastReadTime > 1000){
			readSpeed = this.readSize * 1.0 / (System.currentTimeMillis() - this.lastReadTime);
			this.readSize = 0;
			this.lastReadTime = System.currentTimeMillis();
			
			if(log.isDebugEnabled()){
				log.debug(String.format("read speed %1.3f Kb/s on %s.", this.readSpeed, this.listener.toString()));
			}
		}
		return readLen;
	}
	
	public void write(ByteBuffer src, boolean sync){
		if(this.selectionKey == null || !this.selectionKey.isValid())return;
		//log.debug("wirte to DVR Video channel:" + src.remaining());
		if(this.writeBusy()){
			this.close();	
			//写出错，设置网络超时错误。关闭连接。
			try {
				this.listener.timeout(this);
			} catch (IOException e) {
				log.error(e.toString(), e);
			}
		}else if(this.writeQueue.offer(src)){
			//如果当前Socket没有注册写操作.
			if(this.writeQueue.size() == 1 &&
			  (this.selectionKey.interestOps() & SelectionKey.OP_WRITE) != SelectionKey.OP_WRITE){
				this.selectionKey.interestOps(selectionKey.interestOps() | SelectionKey.OP_WRITE);
				this.selectionKey.selector().wakeup();
			}
		}else {
			log.warn("Write buffer queue full, client:" + this.toString());				
		}
		
		while(sync && src.remaining() > 0){
			synchronized(writeQueue){
				try {
					writeQueue.wait();
				} catch (InterruptedException e) {
				}
			}
		}
	}
	
	/**
	 * 如果写缓存队列大于5个包，认为客户端太忙.
	 * @return
	 */
	public boolean writeBusy(int qSize){
		return this.writeQueue.size() > qSize;
	}
	
	public boolean writeBusy(){
		return this.writeBusy(5);
	}

	@Override
	public void setSelectionKey(SelectionKey key) {
		this.selectionKey = key;		
	}
	
	/**
	 * 主动关闭会话，不会触发disconnect事件。
	 */
	public void close(){
		this.isRunning = false;
		if(this.selectionKey != null && this.selectionKey.isValid()){
			try {
				this.selectionKey.channel().close();
			} catch (IOException e) {
				log.error(e.toString(), e);
			}
			this.selectionKey.cancel();
		}
	}
	
}
