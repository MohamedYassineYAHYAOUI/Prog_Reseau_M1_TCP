package fr.upem.net.tcp.http;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.channels.UnresolvedAddressException;
import java.nio.charset.Charset;
import java.util.Objects;
import java.util.logging.Logger;

public class HTTPClient {

	private SocketChannel sc;
	private HTTPReader httpReader;
	private final ByteBuffer bb;
	private String request;

	static private final int BUFFER_SIZE = 1024;
	static private final Charset charsetASCII = Charset.forName("ASCII");
	public static final Logger logger = Logger.getLogger(HTTPClient.class.getName());

	private HTTPClient(SocketChannel sc, String request) {
		this.sc = sc;
		this.bb = ByteBuffer.allocateDirect(BUFFER_SIZE);
		this.httpReader = new HTTPReader(sc, bb);
		this.request = request;
	}

	public static HTTPClient connectClient(String server, String ressource) throws IOException {
		Objects.requireNonNull(server);
		try {
			var sc = SocketChannel.open();
			sc.connect(new InetSocketAddress(server, 80));

			String request = "GET " + ressource + " HTTP/1.1\r\n" + "Host: " + server + "\r\n" + "\r\n";

			return new HTTPClient(sc, request);
		} catch (UnresolvedAddressException e) {
			throw new HTTPException("Failed to connect to server " + server);
		} catch (SecurityException e) {
			throw new HTTPException("denied access to server " + server);
		}
	}

	String getRessources() throws IOException {
		HTTPHeader header;
		while (true) {
			bb.clear();
			sc.write(charsetASCII.encode(request));
			header = httpReader.readHeader();
			var code = header.getCode();
			if (code == 302 || code == 301) { // redirection
				var url = new URL(header.getFields().get("location"));
				request = "GET " + url.getPath() + " HTTP/1.1\r\n" + "Host: " + url.getHost() + "\r\n" + "\r\n";
				sc.close();
				sc = SocketChannel.open();
				sc.connect(new InetSocketAddress(url.getHost(), 80));
				httpReader = new HTTPReader(sc, bb);

			} else if (code == 400) { // error
				return "";
			} else { // 200
				int contentLength = header.getContentLength();
				ByteBuffer content = httpReader.readBytes(contentLength);
				content.flip();
				//return header.getCharset().decode(content).toString();
				return Charset.forName("UTF8").decode(content).toString();
			}
		}
	}

	public void closeConnection() throws IOException {
		sc.close();
	}

	public static void usage() {
		System.out.println("java fr.upem.net.tcp.http.HTTPClient server ressource");
	}

	public static void main(String[] args) throws IOException {

		if (args.length != 2) {
			usage();
			return;
		}

		String host = args[0];
		String ressources = args[1];

		var client = HTTPClient.connectClient(host, ressources);
		System.out.println(client.getRessources());
		client.closeConnection();
	}
}
