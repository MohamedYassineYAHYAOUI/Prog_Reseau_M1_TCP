package fr.upem.net.tcp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;

public class FixedPrestartedConcurrentLongSumServerWithTimeout {

	private static final Logger logger = Logger
			.getLogger(FixedPrestartedConcurrentLongSumServerWithTimeout.class.getName());
	private static final int BUFFER_SIZE = 1024;
	private static final int TIME_OUT = 6000;
	private final int nbThreads;
	private final ServerSocketChannel serverSocketChannel;
	private final List<ThreadData> threadDataList;

	public FixedPrestartedConcurrentLongSumServerWithTimeout(int port, int maxClient) throws IOException {
		serverSocketChannel = ServerSocketChannel.open();
		serverSocketChannel.bind(new InetSocketAddress(port));
		threadDataList = new ArrayList<ThreadData>();
		nbThreads = maxClient;
		logger.info(this.getClass().getName() + " starts on port " + port);
	}

	private void launchClient(ThreadData threadData) {
		try {
			serve(threadData);
		} catch (InterruptedException e) {
			logger.info("Server interrupted" + e.getMessage());
			return;
		} catch (IOException e) {
			logger.log(Level.INFO, "Connection terminated with client by IOException", e.getCause());
			return;
		} finally {
			silentlyClose(threadData);
		}
	}

	/**
	 * Iterative server main loop
	 *
	 * @throws IOException
	 */

	public void launch() throws IOException {
		logger.info("Server started");
		controleThread();
		IntStream.range(0, nbThreads).forEach(i -> {
			new Thread(() -> {

				try {
					var th = new ThreadData();
					threadDataList.add(th);
					while (!Thread.interrupted()) {
						SocketChannel sc = serverSocketChannel.accept();
						th.setSocketChannel(sc);
						logger.info("Connection accepted from " + sc.getRemoteAddress());
						launchClient(th);
					}
				} catch (AsynchronousCloseException e) {
					logger.info("thread stoped" + e.getMessage());
					return;
				} catch (IOException e) {
					logger.log(Level.SEVERE, "thread interrupted " + e.getMessage());
					return;
				}
			}).start();
		});
	}

	/**
	 * Treat the connection sc applying the protocole All IOException are thrown
	 *
	 * @param sc
	 * @throws IOException
	 * @throws InterruptedException
	 */
	private void serve(ThreadData threadData) throws InterruptedException, IOException {

		while (!Thread.interrupted()) {
			ByteBuffer buff = ByteBuffer.allocate(Integer.BYTES);
			if (!readFully(threadData, buff)) {
				logger.info("Client closed the connection");
				return;
			}
			buff.flip();
			int nbOperand = buff.getInt();
			int readOperand = 0;
			long sum = 0;
			buff = ByteBuffer.allocate(Long.BYTES * nbOperand);
			if (!readFully(threadData, buff)) {
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

			threadData.getSocketChannel().write(buff);
		}

	}

	/**
	 * Close a SocketChannel while ignoring IOExecption
	 *
	 * @param sc
	 */

	private void silentlyClose(ThreadData threadData) {
		var sc = threadData.getSocketChannel();
		if (sc != null) {
			try {
				sc.close();
				sc = null;
			} catch (IOException e) {
				// Do nothing
			}
		}
	}

	static boolean readFully(ThreadData threadData, ByteBuffer bb) throws IOException {
		while (bb.hasRemaining()) {
			if (threadData.getSocketChannel().read(bb) == -1) {
				logger.info("Input stream closed");
				return false;
			}
		}
		threadData.tick();
		return true;
	}

	void controleThread() {
		new Thread(() -> {
			try {
				while (!Thread.interrupted()) {
					for (var threadData : threadDataList) {
						threadData.closeIfInactive(TIME_OUT);
					}
					Thread.sleep(TIME_OUT);
				}
			} catch (InterruptedException e) {
				logger.log(Level.SEVERE, "thread interrupted");
				return;
			}
		}).start();
	}

	void serverConsol() {
		try (var scanner = new Scanner(System.in)) {
			while (scanner.hasNextLine()) {
				var line = scanner.nextLine();
				switch (line) {
				case "INFO":
					System.out.println("number of connected clients :"
							+ threadDataList.stream().filter(th ->th.isConnected()).count());
					break;
				case "SHUTDOWN":
					logger.info("shuting down server on safe mode");
					serverSocketChannel.close();
					break;
				case "SHUTDOWNNOW":
					logger.info("force shut down of the server");
					for (var threadData : threadDataList) {
						threadData.close();
					}
					break;
				default:
					System.out.println("must be INFO, SHUTDOWN or SHUTDOWNNOW");
					break;
				}
			}
		} catch (IOException e) {
			logger.log(Level.SEVERE ,"error while shutting down the server "+e.getMessage());
		}

	}

	static public void usage() {
		System.out.println("java fr.upem.net.tcp.FixedPrestartedConcurrentLongSumServerWithTimeout port maxClient");
	}

	public static void main(String[] args) throws NumberFormatException, IOException {

		if (args.length != 2) {
			usage();
			return;
		}
		var server = new FixedPrestartedConcurrentLongSumServerWithTimeout(Integer.parseInt(args[0]),
				Integer.parseInt(args[1]));

		server.launch();

		server.serverConsol();

	}
}
