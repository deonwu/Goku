package org.goku.video.odip;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ProtocolHeader {
	public static final byte CMD_LOGIN = (byte) 0xa0;
	public static final byte ACK_LOGIN = (byte) 0xb0;
	
	/**
	 * 建立 实时， 对讲， 连接。
	 */
	public static final byte CMD_CONNECT = (byte) 0xf1;
	//public static final byte ACK_LOGIN = (byte) 0xb0;

	/**
	 * 停止， 开启。
	 */
	public static final byte CMD_VIDEO_CTRL = (byte) 0xc5;	

	/**
	 * 媒体数据请求
	 */
	public static final byte CMD_GET_VIDEO = (byte) 0x11;
	
	public static final byte CMD_GET_ALARM = (byte) 0xa1;
	public static final byte ACK_GET_ALARM = (byte) 0xb1;

	public static final byte CMD_DEV_ALARM = (byte) 0x68;
	public static final byte ACK_DEV_ALARM = (byte) 0x69;
	
	/**
	 * 媒体数据响应
	 */
	public static final byte ACK_GET_VIDEO = (byte) 0xbc;	

	
	private static final byte[] SUPPORT_COMMAND = 
	{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, //00-0F
	 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, //10-1F
	 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, //20-2F
	 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, //30-3F
	 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, //40-4F
	 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, //50-5F
	 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0, 0, //60-6F
	 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, //70-7F
	 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, //80-8F
	 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, //90-9F
	 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, //A0-AF
	 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, //B0-BF
	 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, //C0-CF
	 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, //D0-DF
	 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, //E0-EF
	 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 //F0-FF
	};
	
	public byte cmd = 0;
	public byte reserved_1 = 0;
	public byte reserved_2 = 0;
	
	public byte headerLength = 0;
	public byte version = 0x28;
	
	public int externalLength = 0;
	
	public byte[] data = new byte[24];
	
	public void loadBuffer(ByteBuffer buffer){
		buffer.order(ByteOrder.LITTLE_ENDIAN);
		this.cmd = buffer.get();
		this.reserved_1 = buffer.get();
		this.reserved_2 = buffer.get();
		version = buffer.get();
		externalLength = buffer.getInt();
		
		buffer.get(data);		
	}
	
	public void mapToBuffer(ByteBuffer buffer){
		buffer.order(ByteOrder.LITTLE_ENDIAN);
		buffer.put(cmd);
		buffer.put(this.reserved_1);
		buffer.put(this.reserved_2);
		buffer.put(version);
		buffer.putInt(externalLength);
		
		buffer.put(data);
	}
	
	public boolean supportCommand(){
		//int xx = (0 | this.cmd);
		int unsignedBypte = this.cmd >= 0 ? this.cmd : this.cmd + 256;
		return SUPPORT_COMMAND[unsignedBypte] == 1;
	}
	
	/**
	 * 设定data区域的数据， 
	 * @param index -- 不是从0 开始，第一个数据从8。为了和文档上的index一致，方便
	 * 对照文档开发。
	 * @param data
	 */
	public void setByte(int index, byte v){
		if(index < 8 || index > 31) return;
		this.data[index - 8] = v;
	}
	
	/**
	 * 取data区域的数据， 
	 * @param index -- 不是从0 开始，第一个数据从8。为了和文档上的index一致，方便
	 * 对照文档开发。
	 * @param data
	 */	
	public byte getByte(int index){
		if(index < 8 || index > 31) return -1;
		return this.data[index - 8];
	}
	
	public int getInt(int index){
		if(index < 8 || index + 4 > 31) return -1;
		int re = 0;
		re = this.data[index - 8];
		re += this.data[index - 7] << 8;
		re += this.data[index - 6] << 16;
		re += this.data[index - 5] << 24;
		return re;
	}	
	
	public void setInt(int index, int data){
		if(index < 8 || index + 4 > 31) return;
		
		this.data[index - 8] = (byte)(data & 0xff);
		this.data[index - 7] = (byte)(data >> 8 & 0xff);
		this.data[index - 6] = (byte)(data >> 16 & 0xff);
		this.data[index - 5] = (byte)(data >> 24 & 0xff);
	}	
}
