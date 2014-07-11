package org.goku.socket;

import java.io.IOException;

public interface SocketAdaptor {
	public void runCommand(String command, SocketClient client) throws IOException;
}
