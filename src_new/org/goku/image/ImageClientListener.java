package org.goku.image;

public interface ImageClientListener {
	public void recevieImageOK(ImageClientEvent event);
	public void notFoundImage(ImageClientEvent event);
}
