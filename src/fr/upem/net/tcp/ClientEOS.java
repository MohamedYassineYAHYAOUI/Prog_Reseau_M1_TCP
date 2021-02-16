package fr.upem.net.tcp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.Objects;
import java.util.logging.Logger;

public class ClientEOS {

	public static final Charset UTF8_CHARSET = Charset.forName("UTF-8");
	public static final int BUFFER_SIZE = 1024;
	public static final Logger logger = Logger.getLogger(ClientEOS.class.getName());

	/**
	 * This method: - connect to server - writes the bytes corresponding to request
	 * in UTF8 - closes the write-channel to the server - stores the bufferSize
	 * first bytes of server response - return the corresponding string in UTF8
	 *
	 * @param request
	 * @param server
	 * @param bufferSize
	 * @return the UTF8 string corresponding to bufferSize first bytes of server
	 *         response
	 * @throws IOException
	 */

	public static String getFixedSizeResponse(String request, SocketAddress server, int bufferSize) throws IOException {
		SocketChannel sc = SocketChannel.open();
		sc.connect(server);
		ByteBuffer snedBuffer = ByteBuffer.allocate(BUFFER_SIZE);
		snedBuffer.put(UTF8_CHARSET.encode(request));
		snedBuffer.flip();
		sc.write(snedBuffer);
		sc.shutdownOutput();

		ByteBuffer recieveBuff = ByteBuffer.allocate(bufferSize);
		
		readFully(sc,recieveBuff);
//		while (sc.read(recieveBuff) != -1) {
//			if (!recieveBuff.hasRemaining()) {
//				break;
//			}
//		}
		sc.close();
		recieveBuff.flip();
		return UTF8_CHARSET.decode(recieveBuff).toString();

	}

	/**
	 * This method: - connect to server - writes the bytes corresponding to request
	 * in UTF8 - closes the write-channel to the server - reads and stores all bytes
	 * from server until read-channel is closed - return the corresponding string in
	 * UTF8
	 *
	 * @param request
	 * @param server
	 * @return the UTF8 string corresponding the full response of the server
	 * @throws IOException
	 */

	public static String getUnboundedResponse(String request, SocketAddress server) throws IOException {

		try(SocketChannel sc = SocketChannel.open()){
			//sc.connect(server); il faut pas faire connect ??
			sc.write(UTF8_CHARSET.encode(request));
			sc.shutdownOutput();
			
			var currentBuffSize = BUFFER_SIZE;
			ByteBuffer recieveBuff = ByteBuffer.allocate(currentBuffSize);

			while (true) {
				if(!readFully(sc, recieveBuff)) {
					break;
				}else {
					currentBuffSize *= 2;
					ByteBuffer tmp = ByteBuffer.allocate(currentBuffSize);
					recieveBuff.flip();
					tmp.put(recieveBuff);
					recieveBuff = tmp;
				}		
			}
			recieveBuff.flip();
			return UTF8_CHARSET.decode(recieveBuff).toString();
		}
	}

	/**
	 * Fill the workspace of the Bytebuffer with bytes read from sc.
	 *
	 * @param sc
	 * @param bb
	 * @return false if read returned -1 at some point and true otherwise
	 * @throws IOException
	 */
	static boolean readFully(SocketChannel sc, ByteBuffer bb) throws IOException {
		Objects.requireNonNull(bb);
		Objects.requireNonNull(sc);
		while(bb.hasRemaining()){
			if(sc.read(bb) == -1) {
				return false;
			};
		}
		return true;
	}

	public static void main(String[] args) throws IOException {
		InetSocketAddress google = new InetSocketAddress("www.google.fr", 80);
		//System.out.println(getFixedSizeResponse("GET / HTTP/1.1\r\nHost: www.google.fr\r\n\r\n", google, 512));
		 System.out.println(getUnboundedResponse("GET / HTTP/1.1\r\nHost: www.google.fr\r\n\r\n",
		 google));
	}
}
