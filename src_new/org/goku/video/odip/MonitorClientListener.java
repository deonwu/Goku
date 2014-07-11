package org.goku.video.odip;

public interface MonitorClientListener {
	public void connected(MonitorClientEvent event);
	
	public void disconnected(MonitorClientEvent event);
	
	public void timeout(MonitorClientEvent event);
	
	public void alarm(MonitorClientEvent event);
	
	public void writeIOException(MonitorClientEvent event);
	
	public void loginError(MonitorClientEvent event);
	public void loginOK(MonitorClientEvent event);
}
