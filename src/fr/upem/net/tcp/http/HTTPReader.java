package fr.upem.net.tcp.http;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Objects;

public class HTTPReader {

	private final Charset ASCII_CHARSET = Charset.forName("ASCII");
	private final SocketChannel sc;
	private final ByteBuffer buff;

	public HTTPReader(SocketChannel sc, ByteBuffer buff) {
		this.sc = sc;
		this.buff = buff;
	}

	/**
	 * @return The ASCII string terminated by CRLF without the CRLF
	 *         <p>
	 *         The method assume that buff is in write mode and leaves it in
	 *         write-mode The method does perform a read from the socket if the
	 *         buffer data. The will process the data from the buffer if necessary
	 *         will read from the socket.
	 * @throws IOException HTTPException if the connection is closed before a line
	 *                     could be read
	 */
	public String readLineCRLF() throws IOException {

		buff.flip();
		var builder = new StringBuilder();
		char previousOct = '\0';
		while (true) {
			if (!buff.hasRemaining()) {
				buff.clear();
				if (sc.read(buff) == -1) {
					throw new HTTPException();
				}
				buff.flip();
			} else {
				var currentOct = (char) buff.get();
				builder.append(currentOct);

				if (previousOct == '\r' && currentOct == '\n') {
					buff.compact();
					break;
				}
				previousOct = currentOct;
			}
		}
		builder.setLength(builder.length() - 2);
		return builder.toString();
	}

	/**
	 * @return The HTTPHeader object corresponding to the header read
	 * @throws IOException HTTPException if the connection is closed before a header
	 *                     could be read if the header is ill-formed
	 */
	public HTTPHeader readHeader() throws IOException {

		var responseLine = readLineCRLF();
		var fields = new HashMap<String, String>();
		String header;
		while (!(header = readLineCRLF()).isEmpty()) {
			var tokens = header.split(": ");
			if (tokens.length != 2) {
				throw new HTTPException("invalid header " + header);
			}
			fields.compute(tokens[0], (k, v) -> (v == null) ? tokens[1] : v + ";" + tokens[1]);
		}
		return HTTPHeader.create(responseLine, fields);
	}

	/**
	 * @param size The method assume that buff is in write mode and leaves it in
	 *             write-mode The method does perform a read from the socket if the
	 *             buffer data. The will process the data from the buffer if
	 *             necessary will read from the socket.
	 * @return a ByteBuffer in write-mode containing size bytes read on the socket
	 * @throws IOException HTTPException is the connection is closed before all
	 *                     bytes could be read
	 */
	public ByteBuffer readBytes(int size) throws IOException {
		var bb = ByteBuffer.allocate(size);
		int bytesRead = 0;

		buff.flip();
		while (bytesRead < size) {
			if (!buff.hasRemaining()) {
				buff.clear();
				if (sc.read(buff) == -1) {
					throw new HTTPException();
				}
				;
				buff.flip();
			} else {
				bb.put(buff.get());
				bytesRead++;
			}
		}
		buff.compact();
		return bb;
	}

	/**
	 * @return a ByteBuffer in write-mode containing a content read in chunks mode
	 * @throws IOException HTTPException if the connection is closed before the end
	 *                     of the chunks if chunks are ill-formed
	 */

	public ByteBuffer readChunks() throws IOException {

		int fullSize = 0;
		ByteBuffer bb = ByteBuffer.allocate(fullSize);
		while (true) {

			String size = readLineCRLF();
			int nextChunkSize = Integer.parseInt(size, 16);

			if (nextChunkSize == 0) {
				break;
			}
			fullSize += nextChunkSize;

			ByteBuffer tmp = ByteBuffer.allocate(fullSize);
			bb.flip();
			tmp.put(bb);

			var bytesRead = readBytes(nextChunkSize + 2);
			bytesRead.limit(nextChunkSize);
			bytesRead.flip();

			tmp.put(bytesRead);
			bb = tmp;
		}
		return bb;
	}

	public static void main(String[] args) throws IOException {
		Charset charsetASCII = Charset.forName("ASCII");
		String request = "GET / HTTP/1.1\r\n" + "Host: www.w3.org\r\n" + "\r\n";

		SocketChannel sc = SocketChannel.open();
		sc.connect(new InetSocketAddress("www.w3.org", 80));
		sc.write(charsetASCII.encode(request));
		ByteBuffer bb = ByteBuffer.allocate(50);
		HTTPReader reader = new HTTPReader(sc, bb);
		reader.readLineCRLF();
		
		System.out.println(reader.readLineCRLF());
		System.out.println(reader.readLineCRLF());
		System.out.println(reader.readLineCRLF());
		sc.close();

		bb = ByteBuffer.allocate(50);
		sc = SocketChannel.open();
		sc.connect(new InetSocketAddress("www.w3.org", 80));
		reader = new HTTPReader(sc, bb);
		sc.write(charsetASCII.encode(request));
		System.out.println(reader.readHeader());
		sc.close();

		bb = ByteBuffer.allocate(50);
		sc = SocketChannel.open();
		sc.connect(new InetSocketAddress("www.w3.org", 80));
		reader = new HTTPReader(sc, bb);
		sc.write(charsetASCII.encode(request));
		HTTPHeader header = reader.readHeader();
		System.out.println(header);
		ByteBuffer content = reader.readBytes(header.getContentLength());
		content.flip();
		System.out.println(header.getCharset().decode(content));
		sc.close();

		bb = ByteBuffer.allocate(50);
		request = "GET / HTTP/1.1\r\n" + "Host: www.u-pem.fr\r\n" + "\r\n";
		sc = SocketChannel.open();
		sc.connect(new InetSocketAddress("www.u-pem.fr", 80));
		reader = new HTTPReader(sc, bb);
		sc.write(charsetASCII.encode(request));
		header = reader.readHeader();
		System.out.println(header);
		content = reader.readChunks();
		content.flip();
		System.out.println(header.getCharset().decode(content));
		sc.close();

	}
}
