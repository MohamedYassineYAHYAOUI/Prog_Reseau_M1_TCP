package fr.upem.net.tcp.nonblocking;

import java.nio.ByteBuffer;
import java.util.logging.Logger;


public class MessageReader implements Reader<Message> {
	private enum State {
		DONE, WAITING, ERROR
	};
	static private Logger logger = Logger.getLogger(MessageReader.class.getName());
	private State state = State.WAITING;
	private final StringReader stringReader = new StringReader();
	private final Message.Builder builder = new Message.Builder();
	private Message message = null;
	private boolean readLogIn = false;
	private boolean readMsg = false;

	@Override
	public ProcessStatus process(ByteBuffer bb) {
		if (state == State.DONE || state == State.ERROR) {
			throw new IllegalStateException();
		}
        while(!readLogIn || !readMsg) {
        	
    		var srState = stringReader.process(bb);
    		if (srState != ProcessStatus.DONE) {
    			return srState;
    		}
        	if(!readLogIn) {
        		readLogIn = true;
        		builder.setLogin(stringReader.get());

        	}else {
        		readMsg = true;
        		builder.setMessage(stringReader.get());
        		stringReader.reset();
        		
        	}
    		stringReader.reset();
        	
        }
        //
        message = builder.build();
        state=State.DONE;
        return ProcessStatus.DONE;
	}

	@Override
	public Message get() {
		if(state != State.DONE ) {
			throw new IllegalStateException();
		}
		return message;
	}

	@Override
	public void reset() {
		state = State.WAITING;
		stringReader.reset();
		message= null;
		readLogIn = false;
		readLogIn = false;
	}

}
