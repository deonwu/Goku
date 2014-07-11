package org.goku.socket;

import java.nio.channels.SelectionKey;

public interface SelectionHandler {
	public void setSelectionKey(SelectionKey key);	
}
