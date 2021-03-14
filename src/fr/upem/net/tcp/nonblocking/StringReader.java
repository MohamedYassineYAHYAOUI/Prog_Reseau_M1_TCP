package fr.upem.net.tcp.nonblocking;


import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

public class StringReader implements Reader<String> {
    private enum State {DONE, WAITING_FOR_SIZE, WAITING_FOR_CONTENT, ERROR};
	static private Logger logger = Logger.getLogger(StringReader.class.getName());
    private final int MAX_SIZE = 1_024;
    private static final Charset UTF8 = StandardCharsets.UTF_8;
    private final IntReader intReader = new IntReader();
    private final ByteBuffer internalbb = ByteBuffer.allocate(MAX_SIZE);
    private State state = State.WAITING_FOR_SIZE;
    private int size;
    private String value;


    @Override
    public ProcessStatus process(ByteBuffer bb) {
        switch (state) {
            case WAITING_FOR_SIZE:
                var status = intReader.process(bb);
                if(status == ProcessStatus.REFILL) {
                	return status ;
                }
                size = intReader.get();
            	if(size < 0 || size > 1024) {
            		return ProcessStatus.ERROR;
            	}
            	//reset();
                state = State.WAITING_FOR_CONTENT;
            case WAITING_FOR_CONTENT:
                var missing = size -internalbb.position();
                try {
                	bb.flip();
                	while(internalbb.hasRemaining() && missing > 0) {
                		internalbb.put(bb.get());
                		missing--;
                	}
                }catch(BufferUnderflowException e){
                	return ProcessStatus.REFILL;
                }finally {
                	bb.compact();
                }
                if(missing == 0){
                	state=State.DONE;
                	internalbb.flip();
                	value = UTF8.decode(internalbb).toString();
                	internalbb.compact();
                	return ProcessStatus.DONE;
                }
            default:
                throw new IllegalStateException();
        }
    }

   

    @Override
    public String get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        
        return value;
    }

    @Override
    public void reset() {
        state = State.WAITING_FOR_SIZE;
        intReader.reset();
        internalbb.clear();
        value = null;
    }
}