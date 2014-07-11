package org.goku.image;

import java.nio.ByteBuffer;

public class ASC100Package implements Cloneable{
	public static final int STATUS_INIT = 1;
	public static final int STATUS_CMD = 2;
	public static final int STATUS_LENGTH = 3;
	public static final int STATUS_DATA = 4;
	public static final int STATUS_CHECKSUM = 5;
	public static final int STATUS_END = 6;
	
	/**
	 * 当前包处理状态。
	 */
	public int status = STATUS_INIT;
	/**
	 * 协议包的命令字。
	 */
	public byte cmd = 0;
	/**
	 * 数据包的长度。-1表示没有初始化
	 */		
	public int len = -1;
	public short checkSum = 0;
	public short bufferCheckSum = 0;
	
	public ByteBuffer inBuffer = ByteBuffer.allocate(64 * 1024);
	
	/**
	 * 读缓冲
	 */
	public byte[] padding = new byte[5];
	public int paddingIndex = 0;
	
	
	/**
	 * 当前是否正读到一个escaped;
	 */
	public boolean escaped = false;
	public boolean autoEscaped = true;
	
	public void clear(){
		this.status = STATUS_CMD;
		this.cmd = 0;
		this.len = -1;
		this.autoEscaped = true;
		this.checkSum = 0;
		this.bufferCheckSum = 0;
		this.paddingIndex = 0;
		inBuffer.clear();
	}
	
	public ASC100Package copy(){
		ASC100Package n = null;
		try {
			n = (ASC100Package)this.clone();
			n.inBuffer = ByteBuffer.allocate(inBuffer.remaining());
			n.inBuffer.put(inBuffer.asReadOnlyBuffer());
			n.inBuffer.flip();
		} catch (CloneNotSupportedException e) {
		}
		return n;
	}
}
