package org.goku.image;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.goku.settings.Settings;

public class ASC100MX implements Runnable{
	private Log log = LogFactory.getLog("mx");
	
	private Map<String, String> routeTable = new HashMap<String, String>();	
	private Map<String, ASC100Client> route = Collections.synchronizedMap(new HashMap<String, ASC100Client>());	
	private int remotePort = 0;	
	private int localPort = 0;
	
	private boolean isRunning = true;
	private DatagramChannel channel = null;
	
	private ByteBuffer writeBuffer = null;
	
	public ASC100MX(int remotePort, int localPort){
		this.localPort = localPort; 
		this.remotePort = remotePort;
	}
	
	/**
	 * 所有数据处理都在同一个线程完成，简化Buffer的管理。
	 */
	public void run(){
	    ByteBuffer readBuffer = ByteBuffer.allocate(65535);	  
	    readBuffer.order(ByteOrder.BIG_ENDIAN);
	    
	    writeBuffer = ByteBuffer.allocate(65535);
	    writeBuffer.order(ByteOrder.BIG_ENDIAN);
	    
	    SocketAddress address = new InetSocketAddress(this.localPort);
	    DatagramSocket socket = null; //channel.socket();
	    try{
		    channel = DatagramChannel.open();
		    socket = channel.socket();
		    socket.bind(address);
	    }catch(Exception e){
	    	log.error("Failed open local UDP port " + this.localPort + ", error:" + e.toString());
	    }
	    
	    log.info("Started UPD server at port " + this.localPort);
	    while (this.isRunning) {
			try {
				readBuffer.clear();
				SocketAddress client = channel.receive(readBuffer);
				readBuffer.flip();
				log.info(String.format("Recevive from MX %s, size %s", client.toString(), readBuffer.remaining()));
				this.process(client, readBuffer);
			} catch (Exception ex) {
				log.error("Process Error:" + ex.toString(), ex);
			}
	    }
	    
	    try {
	    	if(channel != null){
	    		channel.close();
	    	}
		} catch (IOException e) {
			log.warn("Exception at shutdown UDP server, error:" + e.toString());
		}
	    
	}
	
	public void register(ASC100Client client){
		this.route.put(client.getClientId(), client);
		client.setASC100MX(this);
	}
	
	public synchronized void send(ASC100Client client, ByteBuffer data) throws IOException{
		String locationId = client.getClientId();
		String mxAddr = routeTable.get(locationId);
		if(mxAddr != null){
			writeBuffer.clear();
			InetSocketAddress addr = new InetSocketAddress(mxAddr, remotePort);
			writeBuffer.put((byte)0);
			writeBuffer.put((byte)1);
			//数据填充结束后再计算校验和。
			writeBuffer.putShort((short)0); 
			String[] channelAddr = locationId.split("\\.");
			writeBuffer.put((byte)Short.parseShort(channelAddr[0]));
			writeBuffer.put((byte)Short.parseShort(channelAddr[1]));
			writeBuffer.put((byte)Short.parseShort(channelAddr[2]));
			writeBuffer.put((byte)0);
			
			//数据长度。
			writeBuffer.putShort((short)data.limit());
			
			writeBuffer.put(data);
			
			ByteBuffer sub = writeBuffer.asReadOnlyBuffer();
			sub.position(4);
			writeBuffer.position(2);
			writeBuffer.putShort(dataSumCheck(sub));
			
			writeBuffer.position(0);
			channel.send(writeBuffer, addr);
			log.info(String.format("Send to MX %s->%s size %s", client.getClientId(), addr.toString(), data.limit()));
		}else {
			log.info("Not found MX server for node:" + locationId);
		}
	}
	
	public void close(){
		this.isRunning = false;
	}
	
	protected void process(SocketAddress client, ByteBuffer data){
		byte cmd = data.get();
		if(cmd == 0xc5){
			this.processRouteTable(client, data);
		}else if(cmd == 0x00){
			byte channelCount = data.get();
			short sumCheck = data.getShort();
			short curSum = dataSumCheck(data.asReadOnlyBuffer());
			if(sumCheck == curSum){
				for(int i = 0; i < channelCount; i++){
					short node2 = data.get();
					short node1 = data.get();
					short channelId = data.get();
					data.get();
					short len = data.getShort();
					ByteBuffer sub = data.asReadOnlyBuffer();
					sub.limit(data.position() + len);
					this.clientRoute(node1, node2, channelId, sub);
				}
			}else {
				log.info("Failed to data sum check, the package is dropped.");
			}
		}else {
			log.info(String.format("Unkown package, Control data:0x%x.", cmd));
		}
	}
	
	protected void clientRoute(short node1, short node2, short channel, ByteBuffer data){
		String channelId = String.format("%s.%s.%s", node1, node2, channel);
		ASC100Client client = this.route.get(channelId);
		if(client != null){
			log.info("Process data client:" + channelId);
			client.process(data);
		}
	}
	
	protected void processRouteTable(SocketAddress client, ByteBuffer data){
		byte channelCount = data.get();
		short reversed = data.getShort();
		
		InetSocketAddress addr = (InetSocketAddress)client;
		
		String ipAddr = addr.getAddress().getHostAddress();
		for(int i = 0; i < channelCount; i++){
			short node2 = data.get();
			short node1 = data.get();
			String channelId = String.format("%s.%s.%s", node1, node2, i);
			String oldMx = routeTable.get(channelId);
			if(oldMx == null || !oldMx.equals(ipAddr)){
				routeTable.put(channelId, ipAddr);
				log.info(String.format("Update Mx table:%s->%s", channelId, ipAddr));
			}
		}
	}
	
	protected short dataSumCheck(ByteBuffer data){
		short sum = 0;
		while(data.hasRemaining()){
			sum += data.get();
		}
		return sum;
	}
}
