package fr.upem.net.tcp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.logging.Level;
import java.util.logging.Logger;

public class IterativeLongSumServer {

    private static final Logger logger = Logger.getLogger(IterativeLongSumServer.class.getName());
    private static final int BUFFER_SIZE = 1024; 
    private final ServerSocketChannel serverSocketChannel;

    public IterativeLongSumServer(int port) throws IOException {
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress(port));
        logger.info(this.getClass().getName()
                + " starts on port " + port);
    }

    /**
     * Iterative server main loop
     *
     * @throws IOException
     */

    public void launch() throws IOException {
        logger.info("Server started");
        while(!Thread.interrupted()) {
            SocketChannel client = serverSocketChannel.accept();
            try {
                logger.info("Connection accepted from " + client.getRemoteAddress());
                serve(client);
            } catch (IOException ioe) {
                logger.log(Level.INFO,"Connection terminated with client by IOException",ioe.getCause());
            } catch (InterruptedException ie) {
                logger.info("Server interrupted");
                break;
            } finally {
                silentlyClose(client);
            }
        }
    }

    /**
     * Treat the connection sc applying the protocole
     * All IOException are thrown
     *
     * @param sc
     * @throws IOException
     * @throws InterruptedException
     */
    private void serve_naive(SocketChannel sc) throws IOException, InterruptedException{
   	
    	while(true) {
        	ByteBuffer buff = ByteBuffer.allocate(Integer.BYTES);
			if (!readFully(sc, buff)) {
				logger.info("Client closed the connection");
				return;
			}
        	buff.flip();
        	int nbOperand = buff.getInt();
        	int readOperand = 0;
        	long sum =0;
        	buff = ByteBuffer.allocate(Long.BYTES * nbOperand);
			if(!readFully(sc, buff)) {
				logger.log(Level.SEVERE, "Client request not valide, closed connection");
				return;
			}
        	buff.flip();
        	while(readOperand < nbOperand) {
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
    
    
    
    private void serve(SocketChannel sc) throws IOException, InterruptedException{
    	ByteBuffer buffIn = ByteBuffer.allocate(BUFFER_SIZE).flip();
    	ByteBuffer buffOut = ByteBuffer.allocate(Long.BYTES);
    	while(true) {
    		
    		if(!ensure(sc, buffIn, Integer.BYTES)) {
	    		logger.info("connection closed");
	    		return;
	    	}
	    	int nbOperand = buffIn.getInt();
	    	long sum = 0;
	    	while(nbOperand > 0 && ensure(sc, buffIn, Long.BYTES)) {
	    		sum+= buffIn.getLong();
	    		nbOperand --;
	    	}
	    	if(nbOperand !=0) {
	    		return;
	    	}
	    	buffOut.clear();
	    	buffOut.putLong(sum).flip();
	    	sc.write(buffOut);
    	}
    	
    }
    
    
    
    /**
     * bb in read mode, reads at least byteSize from sc to the buffer
     * byteSize must be less then bb capacity 
     * @param sc
     * @param bb
     * @param byteSize
     * @return
     * @throws IOException
     */
   private boolean ensure(SocketChannel sc, ByteBuffer bb, int byteSize) throws IOException {
	   assert(byteSize<= bb.capacity());
	   while(bb.remaining() < byteSize) {
		   bb.compact();
		   try {
			   if( sc.read(bb) == -1) {
				   logger.info("Input stream closed");
				   return false;
			   }
		   }finally {
			   bb.flip();
		   }
	   }
	   return true;
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
        while(bb.hasRemaining()) {
            if (sc.read(bb)==-1){
                logger.info("Input stream closed");
                return false;
            }
        }
        return true;
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        IterativeLongSumServer server = new IterativeLongSumServer(Integer.parseInt(args[0]));
        server.launch();
    }
}
