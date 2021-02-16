package fr.upem.net.tcp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClientConcatenation {

	private static final int BUFFER_SIZE = 1024;
	private static final Charset UTF8 = Charset.forName("UTF-8");
	public static final Logger logger = Logger.getLogger(ClientConcatenation.class.getName());
	private final SocketChannel sc;
	private final ByteBuffer bb = ByteBuffer.allocateDirect(BUFFER_SIZE);

	ClientConcatenation(SocketChannel sc) {
		this.sc = sc;

	}
	
	static boolean readFully(SocketChannel sc, ByteBuffer bb) throws IOException {
		
		while(bb.hasRemaining()) {
            if (sc.read(bb)==-1){
                logger.info("Input stream closed");
                return false;
            }
        }
        return true;
    }
	
	/**
	 * reads unbounded respond from sc
	 * @return a buffer in write mode with containing server respond (must flip to use )
	 * @throws IOException
	 */
	private ByteBuffer respondBuffer() throws IOException {
		
		
		ByteBuffer msgSize = ByteBuffer.allocate(Integer.BYTES);
		readFully(sc, msgSize);
		msgSize.flip();
		ByteBuffer recieveBuff = ByteBuffer.allocate(msgSize.getInt());
		readFully(sc, recieveBuff);

		return recieveBuff;
	}
	

	Optional<String> send(List<String> lines) {
		Objects.requireNonNull(lines);
		try {
			bb.clear();
			bb.putInt(lines.size());
			bb.flip();
			sc.write(bb);

			for (var l : lines) {
				bb.clear();
				bb.putInt(l.length());
				bb.put(UTF8.encode(l));
				bb.flip();
				sc.write(bb);
			}
			
			var buff=  respondBuffer();
			buff.flip();
			return Optional.of(UTF8.decode(buff).toString());
			
		} catch (IOException e) {
			logger.log(Level.SEVERE, e.getMessage());
			return Optional.empty();
		}
	}

	public static void main(String[] args) throws IOException {
		InetSocketAddress server = new InetSocketAddress(args[0], Integer.valueOf(args[1]));
		List<String> lines = new ArrayList<>();

		try (SocketChannel sc = SocketChannel.open(server);
				Scanner scanner = new Scanner(System.in)) {
			var client = new ClientConcatenation(sc);
			while (scanner.hasNextLine()) {
				var line = scanner.nextLine();
				if (line.isEmpty()) {

					var res = client.send(lines);
					if (res.isEmpty()) {
						logger.warning("Connection with server lost");
						return;
					}
					System.out.println(res.get());
					lines.clear();
				} else {
					lines.add(line);
				}
			}
			logger.info("finished");
		}

	}
}
