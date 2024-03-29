package fr.upem.net.tcp.nonblocking;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;


public class ServerEchoWithConsole {

	static private class Context {

		final private SelectionKey key;
		final private SocketChannel sc;
		final private ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);
		private boolean closed = false;


		private Context(SelectionKey key) {
			this.key = key;
			this.sc = (SocketChannel) key.channel();
		}

		/**
		 * Update the interestOps of the key looking only at values of the boolean
		 * closed and the ByteBuffer buff.
		 *
		 * The convention is that buff is in write-mode.
		 */
		private void updateInterestOps() {
			int intrestOps = 0;
			if (bb.hasRemaining() && !closed) {
				intrestOps |= SelectionKey.OP_READ;
			}
			if (bb.position() != 0) {
				intrestOps |= SelectionKey.OP_WRITE;
			}
			if (intrestOps == 0) {
				silentlyClose();
			} else {
				key.interestOps(intrestOps);
			}

		}

		/**
		 * Performs the read action on sc
		 *
		 * The convention is that buff is in write-mode before calling doRead and is in
		 * write-mode after calling doRead
		 *
		 * @throws IOException
		 */
		private void doRead() throws IOException {

			if (sc.read(bb) == -1) {
				closed = true;
			}
			updateInterestOps();
		}

		/**
		 * Performs the write action on sc
		 *
		 * The convention is that buff is in write-mode before calling doWrite and is in
		 * write-mode after calling doWrite
		 *
		 * @throws IOException
		 */
		private void doWrite() throws IOException {
			bb.flip();
			sc.write(bb);
			bb.compact();
			updateInterestOps();
		}

		private void silentlyClose() {
			try {
				sc.close();
			} catch (IOException e) {
				// ignore exception
			}
		}
	}

	static private int BUFFER_SIZE = 1_024;
	static private Logger logger = Logger.getLogger(ServerEchoWithConsole.class.getName());

	private final ServerSocketChannel serverSocketChannel;
	private final Selector selector;
	private final ArrayBlockingQueue<String> commandQueue;

	public ServerEchoWithConsole(int port) throws IOException {
		serverSocketChannel = ServerSocketChannel.open();
		serverSocketChannel.bind(new InetSocketAddress(port));
		selector = Selector.open();
		this.commandQueue = new ArrayBlockingQueue<>(10);
	}

	public void launch() throws IOException {
		serverSocketChannel.configureBlocking(false);
		serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
		while (!Thread.interrupted()) {
			printKeys(); // for debug
			System.out.println("Starting select");
			try {
				selector.select(this::treatKey);
				processCommands();

			} catch (UncheckedIOException tunneled) {
				throw tunneled.getCause();
			}
			System.out.println("Select finished");
		}
	}

	private void treatKey(SelectionKey key) {
		printSelectedKey(key); // for debug
		try {
			if (key.isValid() && key.isAcceptable()) {
				doAccept(key);
			}
		} catch (IOException ioe) {
			// lambda call in select requires to tunnel IOException
			throw new UncheckedIOException(ioe);
		}
		try {
			if (key.isValid() && key.isWritable()) {
				((Context) key.attachment()).doWrite();
			}
			if (key.isValid() && key.isReadable()) {
				((Context) key.attachment()).doRead();
			}
		} catch (IOException e) {
			logger.log(Level.INFO, "Connection closed with client due to IOException", e);
			silentlyClose(key);
		}
	}

	private void doAccept(SelectionKey key) throws IOException {
		// synchornized
		var ssc = (ServerSocketChannel) key.channel();
		var sc = ssc.accept();

		if (sc != null) {

			sc.configureBlocking(false);
			var scKey = sc.register(selector, SelectionKey.OP_READ);
			scKey.attach(new Context(scKey));

		}
	}

	private void silentlyClose(SelectionKey key) {
		Channel sc = (Channel) key.channel();
		try {
			sc.close();
		} catch (IOException e) {
			// ignore exception
		}
	}

	
    private void sendCommand(String command) throws InterruptedException {
    	Objects.requireNonNull(command);
    	try {
    		commandQueue.add(command);
    		selector.wakeup();
    	}catch (IllegalStateException e) {
    		logger.warning("queue is full, msg ignored");
    	}
    }
	
    
    private void processCommands() throws IOException{
    	//intrestOPS
    	synchronized (serverSocketChannel) {
    		var command = commandQueue.poll();
    			if (command== null) {
    				return;
    			}
    			switch (command) {
				case "INFO":
					System.out.println("number of connected clients :" + (selector.keys().size()-1)); 
					break;
				case "SHUTDOWN":
					logger.info("shuting down server on safe mode");
					serverSocketChannel.close();
					break;
				case "SHUTDOWNNOW":
					logger.info("force shut down of the server");

					for (var key : selector.keys()) {
						silentlyClose(key);
					}
					break;
			default:
				System.out.println("must be INFO, SHUTDOWN or SHUTDOWNNOW");
				break;
			}
		}
    }

	
	void serverConsol() {
		try (var scanner = new Scanner(System.in)) {
			while (scanner.hasNextLine() && !Thread.interrupted()) {
				sendCommand( scanner.nextLine());
			}
		} catch (InterruptedException e) {
			logger.log(Level.WARNING, "erreur in consol thread " + e.getCause());
			return;
		}

	}

	public static void main(String[] args) throws NumberFormatException, IOException {
		if (args.length != 1) {
			usage();
			return;
		}
		var server = new ServerEchoWithConsole(Integer.parseInt(args[0]));
		new Thread(server::serverConsol).start();
		server.launch();

	}

	private static void usage() {
		System.out.println("Usage : ServerEchoWithConsole port");
	}

	/***
	 * Theses methods are here to help understanding the behavior of the selector
	 ***/

	private String interestOpsToString(SelectionKey key) {
		if (!key.isValid()) {
			return "CANCELLED";
		}
		int interestOps = key.interestOps();
		ArrayList<String> list = new ArrayList<>();
		if ((interestOps & SelectionKey.OP_ACCEPT) != 0)
			list.add("OP_ACCEPT");
		if ((interestOps & SelectionKey.OP_READ) != 0)
			list.add("OP_READ");
		if ((interestOps & SelectionKey.OP_WRITE) != 0)
			list.add("OP_WRITE");
		return String.join("|", list);
	}

	public void printKeys() {
		Set<SelectionKey> selectionKeySet = selector.keys();
		if (selectionKeySet.isEmpty()) {
			System.out.println("The selector contains no key : this should not happen!");
			return;
		}
		System.out.println("The selector contains:");
		for (SelectionKey key : selectionKeySet) {
			SelectableChannel channel = key.channel();
			if (channel instanceof ServerSocketChannel) {
				System.out.println("\tKey for ServerSocketChannel : " + interestOpsToString(key));
			} else {
				SocketChannel sc = (SocketChannel) channel;
				System.out.println("\tKey for Client " + remoteAddressToString(sc) + " : " + interestOpsToString(key));
			}
		}
	}

	private String remoteAddressToString(SocketChannel sc) {
		try {
			return sc.getRemoteAddress().toString();
		} catch (IOException e) {
			return "???";
		}
	}

	public void printSelectedKey(SelectionKey key) {
		SelectableChannel channel = key.channel();
		if (channel instanceof ServerSocketChannel) {
			System.out.println("\tServerSocketChannel can perform : " + possibleActionsToString(key));
		} else {
			SocketChannel sc = (SocketChannel) channel;
			System.out.println(
					"\tClient " + remoteAddressToString(sc) + " can perform : " + possibleActionsToString(key));
		}
	}

	private String possibleActionsToString(SelectionKey key) {
		if (!key.isValid()) {
			return "CANCELLED";
		}
		ArrayList<String> list = new ArrayList<>();
		if (key.isAcceptable())
			list.add("ACCEPT");
		if (key.isReadable())
			list.add("READ");
		if (key.isWritable())
			list.add("WRITE");
		return String.join(" and ", list);
	}
}
