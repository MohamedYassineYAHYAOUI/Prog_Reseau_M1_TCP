package fr.upem.net.tcp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.logging.Level;
import java.util.logging.Logger;

public class OnDemandConcurrentLongSumServer {

	private static final Logger logger = Logger.getLogger(OnDemandConcurrentLongSumServer.class.getName());
	private static final int BUFFER_SIZE = 1024;
	private final ServerSocketChannel serverSocketChannel;

	public OnDemandConcurrentLongSumServer(int port) throws IOException {
		serverSocketChannel = ServerSocketChannel.open();
		serverSocketChannel.bind(new InetSocketAddress(port));
		logger.info(this.getClass().getName() + " starts on port " + port);
	}

	/**
	 * Iterative server main loop
	 *
	 * @throws IOException
	 */

	public void launch() throws IOException {
		logger.info("Server started");
		while (!Thread.interrupted()) {
			SocketChannel client = serverSocketChannel.accept();
			logger.info("Connection accepted from " + client.getRemoteAddress());

			new Thread(() -> {
				try {
					serve(client);
				} catch (InterruptedException e) {
					logger.info("Server interrupted"+ e.getMessage());
					return;
				} catch (IOException e) {
					logger.log(Level.INFO, "Connection terminated with client by IOException", e.getCause());
					return;
				} finally {
					silentlyClose(client);
				}
			}).start();

		}
	}

	/**
	 * Treat the connection sc applying the protocole All IOException are thrown
	 *
	 * @param sc
	 * @throws IOException
	 * @throws InterruptedException
	 */
	private void serve(SocketChannel sc) throws InterruptedException, IOException {

		while (!Thread.interrupted()) {
			ByteBuffer buff = ByteBuffer.allocate(Integer.BYTES);
			if (!readFully(sc, buff)) {
				logger.info("Client closed the connection");
				return;
			}
			buff.flip();
			int nbOperand = buff.getInt();
			int readOperand = 0;
			long sum = 0;
			buff = ByteBuffer.allocate(Long.BYTES * nbOperand);
			if (!readFully(sc, buff)) {
				logger.log(Level.WARNING, "Client request not valide, closed connection");
				return;
			}
			buff.flip();
			while (readOperand < nbOperand) {
				sum += buff.getLong();
				readOperand++;
			}
			buff.clear();
			buff.limit(Long.BYTES);
			buff.putLong(sum);
			buff.flip();
			sc.write(buff);
		}

	}

	/**
	 * Close a SocketChannel while ignoring IOExecption
	 *
	 * @param sc
	 */

	private void silentlyClose(SocketChannel sc) {
		if (sc != null) {
			try {
				sc.close();
			} catch (IOException e) {
				// Do nothing
			}
		}
	}

	static boolean readFully(SocketChannel sc, ByteBuffer bb) throws IOException {
		while (bb.hasRemaining()) {
			if (sc.read(bb) == -1) {
				logger.info("Input stream closed");
				return false;
			}
		}
		return true;
	}

	public static void main(String[] args) throws NumberFormatException, IOException {
		OnDemandConcurrentLongSumServer server = new OnDemandConcurrentLongSumServer(Integer.parseInt(args[0]));
		server.launch();
	}
}