package fr.upem.net.tcp.nonblocking;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Set;
import java.util.logging.Logger;

public class ServerSum {
	static private int BUFFER_SIZE = 2*Integer.BYTES;
	static private Logger logger = Logger.getLogger(ServerSum.class.getName());

	private final ServerSocketChannel serverSocketChannel;
	private final Selector selector;

	public ServerSum(int port) throws IOException {
		serverSocketChannel = ServerSocketChannel.open();
		serverSocketChannel.bind(new InetSocketAddress(port));
		selector = Selector.open();
	}

	public void launch() throws IOException {
		serverSocketChannel.configureBlocking(false);
		serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
			
		
		while (!Thread.interrupted()) {
			try {
				printKeys(); // for debug
				System.out.println("Starting select");
				selector.select(this::treatKey);
				System.out.println("Select finished");
			}catch(UncheckedIOException e) {
				throw new IOException(e);
			}
		}
	}

	private void treatKey(SelectionKey key) {
		try {
			printSelectedKey(key); // for debug
			if (key.isValid() && key.isAcceptable()) {
				doAccept(key);
			}
			if (key.isValid() && key.isWritable()) {
				doWrite(key);
			}
			if (key.isValid() && key.isReadable()) {
				doRead(key);
			}	
		}catch(IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private void doAccept(SelectionKey key) throws IOException {
		//key.attach(ByteBuffer.allocateDirect(BUFFER_SIZE));
		var ssc = (ServerSocketChannel) key.channel();
		var sc = ssc.accept();
		if (sc != null) {
			sc.configureBlocking(false);
			sc.register(selector,SelectionKey.OP_READ, ByteBuffer.allocateDirect(BUFFER_SIZE) );
		}
	}

	private void doRead(SelectionKey key) throws IOException {
		var sc = (SocketChannel) key.channel();
		var bb = (ByteBuffer) key.attachment();
		
		if(sc.read(bb)==-1) {
			silentlyClose(key);
		};
		
		if(bb.hasRemaining()) {
			return;
		}
		bb.flip();
		var sum =bb.getInt() + bb.getInt();
		bb.clear();
		bb.putInt(sum);
	
		bb.flip();
		key.interestOps(SelectionKey.OP_WRITE);
	}

	private void doWrite(SelectionKey key) throws IOException {
		var sc = (SocketChannel) key.channel();
		var bb = (ByteBuffer) key.attachment();
		sc.write(bb);

		if(bb.hasRemaining()) { // first write
			return ;
		} 
		
		key.interestOps(SelectionKey.OP_READ);
		bb.clear();
	}

	private void silentlyClose(SelectionKey key) {
		Channel sc = (Channel) key.channel();
		try {
			sc.close();
		} catch (IOException e) {
			// ignore exception
		}
	}

	public static void main(String[] args) throws NumberFormatException, IOException {
		if (args.length!=1){
			usage();
			return;
		}
		new ServerSum(Integer.parseInt(args[0])).launch();
	}

	private static void usage(){
		System.out.println("Usage : ServerSum port");
	}

	/***
	 *  Theses methods are here to help understanding the behavior of the selector
	 ***/

	private String interestOpsToString(SelectionKey key){
		if (!key.isValid()) {
			return "CANCELLED";
		}
		int interestOps = key.interestOps();
		ArrayList<String> list = new ArrayList<>();
		if ((interestOps&SelectionKey.OP_ACCEPT)!=0) list.add("OP_ACCEPT");
		if ((interestOps&SelectionKey.OP_READ)!=0) list.add("OP_READ");
		if ((interestOps&SelectionKey.OP_WRITE)!=0) list.add("OP_WRITE");
		return String.join("|",list);
	}

	public void printKeys() {
		Set<SelectionKey> selectionKeySet = selector.keys();
		if (selectionKeySet.isEmpty()) {
			System.out.println("The selector contains no key : this should not happen!");
			return;
		}
		System.out.println("The selector contains:");
		for (SelectionKey key : selectionKeySet){
			SelectableChannel channel = key.channel();
			if (channel instanceof ServerSocketChannel) {
				System.out.println("\tKey for ServerSocketChannel : "+ interestOpsToString(key));
			} else {
				SocketChannel sc = (SocketChannel) channel;
				System.out.println("\tKey for Client "+ remoteAddressToString(sc) +" : "+ interestOpsToString(key));
			}
		}
	}

	private String remoteAddressToString(SocketChannel sc) {
		try {
			return sc.getRemoteAddress().toString();
		} catch (IOException e){
			return "???";
		}
	}

	public void printSelectedKey(SelectionKey key) {
		SelectableChannel channel = key.channel();
		if (channel instanceof ServerSocketChannel) {
			System.out.println("\tServerSocketChannel can perform : " + possibleActionsToString(key));
		} else {
			SocketChannel sc = (SocketChannel) channel;
			System.out.println("\tClient " + remoteAddressToString(sc) + " can perform : " + possibleActionsToString(key));
		}
	}

	private String possibleActionsToString(SelectionKey key) {
		if (!key.isValid()) {
			return "CANCELLED";
		}
		ArrayList<String> list = new ArrayList<>();
		if (key.isAcceptable()) list.add("ACCEPT");
		if (key.isReadable()) list.add("READ");
		if (key.isWritable()) list.add("WRITE");
		return String.join(" and ",list);
	}
}
