package org.goku.image;

public class AbstractImageListener implements ImageClientListener{

	@Override
	public void recevieImageOK(ImageClientEvent event) {};

	@Override
	public void notFoundImage(ImageClientEvent event) {};

	@Override
	public void connectionError(ImageClientEvent event) {};

	@Override
	public void message(ImageClientEvent event) {};
	
	@Override
	public void active(ImageClientEvent event) {};
	
	@Override
	public void debugRawData(ImageClientEvent event) {};
}
