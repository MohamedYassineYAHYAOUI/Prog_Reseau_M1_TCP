package fr.upem.net.tcp.nonblocking;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;


public class StringReader implements Reader<String> {

    private enum State {DONE,WAITING_FOR_SIZE,WAITING_FOR_TEXT,ERROR};
    private static int TEXT_MAX_SIZE = 1024;  
    private static Charset UTF8 = StandardCharsets.UTF_8;
    
    private State state = State.WAITING_FOR_SIZE;
    
    private final ByteBuffer internalbb = ByteBuffer.allocate(TEXT_MAX_SIZE); // write-mode
    private String text;
    private int textSize;
    private int bytesRead;
    private final IntReader intReader = new IntReader();

    
    /**
     * extracts the size from the buffer and compare to TEXT_MAX_SIZE
     * @param bb buffer in read mode
     * @return size
     */
    
    private int extractSize(ByteBuffer bb) {
    	int size =0;
    	if(bb.limit() >= Integer.BYTES) {
    		size = bb.getInt();
    		if( size > TEXT_MAX_SIZE || size < 0) {
    			throw new IllegalStateException(); 
    		}
    	}
    	return size;
    }
 
    public ProcessStatus process(ByteBuffer bb) {
        if (state== State.DONE || state== State.ERROR) {
            throw new IllegalStateException();
        }
        bb.flip();
        try {
            if(state == State.WAITING_FOR_SIZE) {
                textSize = extractSize(bb);
                state= State.WAITING_FOR_TEXT;
            }
            if(state == State.WAITING_FOR_TEXT){
                while(bb.hasRemaining() && internalbb.hasRemaining() && internalbb.position() <textSize ) {
                    internalbb.put(bb.get());
                }
            }
        }catch(IllegalStateException e){
            return ProcessStatus.ERROR;
        }finally {
            bb.compact();
        }
        if (internalbb.position() < textSize){
            return ProcessStatus.REFILL;
        }
        state=State.DONE;
        internalbb.flip();
        text = UTF8.decode(internalbb).toString();
        return ProcessStatus.DONE;
    }
	


	@Override
	public String get() {
        if (state!= State.DONE) {
            throw new IllegalStateException();
        }
        return text;
	}

	@Override
	public void reset() {
        state= State.WAITING_FOR_SIZE;
        internalbb.clear();
        textSize = 0;
        
	}

}
