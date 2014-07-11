package org.goku.video.odip;

public abstract class AbstractMonitorListener implements MonitorClientListener{

	@Override
	public void connected(MonitorClientEvent event) {
	}

	@Override
	public void disconnected(MonitorClientEvent event) {
	}

	@Override
	public void timeout(MonitorClientEvent event) {
	}

	@Override
	public void alarm(MonitorClientEvent event) {
	}
	
	@Override
	public void writeIOException(MonitorClientEvent event) {
	}

	@Override
	public void loginError(MonitorClientEvent event) {
	}

	@Override
	public void loginOK(MonitorClientEvent event) {
	}	
}
