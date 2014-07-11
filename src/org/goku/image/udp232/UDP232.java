package org.goku.image.udp232;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.comm.CommPortIdentifier;
import javax.comm.SerialPort;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.goku.image.ASC100MX;
import org.goku.settings.Settings;

public class UDP232 implements Runnable{
	private Log log = LogFactory.getLog("upd-232");
	private Collection<String> clients = new ArrayList<String>();
	private int localPort = 5004;
	private int remotePort = 5001;
	private int bitRate = 19200;
	public UDP232(Settings sttings){
		String cfg = null;
		for(int i = 1; i < 10; i++){
			cfg = sttings.getString("rs232_" + i, null);
			if(cfg != null){
				clients.add(cfg);
			}
		}
		localPort = sttings.getInt(Settings.UDP_LOCAL_PORT, 5004);
		remotePort = sttings.getInt(Settings.UDP_REMOTE_PORT, 5001);
		bitRate = sttings.getInt("bitRate", 19200);
	}
	
	@Override
	public void run() {
		ThreadPoolExecutor threadPool = new ThreadPoolExecutor(10, 10, 60, TimeUnit.SECONDS, 
				new LinkedBlockingDeque<Runnable>(5));
		ASC100MX mx = new ASC100MX(remotePort, localPort, threadPool);
		//threadPool.execute(mx);
		
		listPortChoices();
		
		log.info("Start init clients....");
		for(String client: clients){
			String[] temp = client.split("\\$");
			String portName = temp[0], id = temp[1];
			log.info(String.format("init client:%s->%s", portName, id));
			try {
				if(new File(portName).isFile()){
					ReplyFileData co = new ReplyFileData(id, portName);
					threadPool.execute(co);
					mx.register(co);
				}else {
					CommPortIdentifier portId = CommPortIdentifier.getPortIdentifier(portName);
					SerialPort sPort = (SerialPort) portId.open(id, 5000);
					sPort.setSerialPortParams(bitRate, SerialPort.DATABITS_8, 
						SerialPort.STOPBITS_1, 
						SerialPort.PARITY_NONE);								
						UDP232Client co = new UDP232Client(id, sPort);
						threadPool.execute(co);
						mx.register(co);					
				}
			} catch (Exception e) {
				log.info(e.toString(), e);
			}
		}
		mx.run();
	}	
	
	public void listPortChoices() {
        CommPortIdentifier portId;
        Enumeration en = CommPortIdentifier.getPortIdentifiers();
        // iterate through the ports.
        log.info("list all port.....");
        while (en.hasMoreElements()) {
            portId = (CommPortIdentifier) en.nextElement();
            //log.info("found serial port:" + portId.getName());
            if (portId.getPortType() == CommPortIdentifier.PORT_SERIAL) {
            	log.info("found serial port:" + portId.getName());
            }
        }
    }

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		System.out.println("start upd 232...");
		new UDP232(new Settings("udp_232.conf")).run();
		System.out.println("done");
	}

}
