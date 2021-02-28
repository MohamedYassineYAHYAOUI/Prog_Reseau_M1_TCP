package fr.upem.net.tcp;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.logging.Logger;

public class ThreadData {

	private static final Logger logger = Logger.getLogger(ThreadData.class.getName());
	private SocketChannel sc;
	private long lastActivity;
	private final Object token = new Object();

	public ThreadData() {
		this.lastActivity = 0;
	}

	SocketChannel getSocketChannel() {
		synchronized (token) {
			return sc;
		}
	}
	
	boolean isConnected() {
		synchronized (token) {
			return sc != null && sc.isConnected();
		}
	}
	
	void setSocketChannel(SocketChannel client) {
		synchronized (token) {
			if (sc == null) {
				sc = client;
			} else {
				throw new IllegalStateException();
			}
			lastActivity = System.currentTimeMillis();
		}
	}

	void tick() {
		synchronized (token) {
			lastActivity = System.currentTimeMillis();
		}
	}

	void closeIfInactive(int timeout) {
		synchronized (token) {
			if ((System.currentTimeMillis() - lastActivity) >= timeout) {
				close();
			}
		}
	}

	void close() {
		synchronized (token) {
			try {
				if(sc != null) {
					sc.close();
					sc=null;					
				}
			} catch (IOException e) {
				logger.info("closed thread");
			}
		}
	}
	
}
