package fr.upem.net.tcp.nonblocking;

import java.util.Objects;

public class Message {

	static class Builder {
		private String login;
		private String message;

		Builder setLogin(String login) {
			this.login = Objects.requireNonNull(login);
			return this;
		}

		Builder setMessage(String message) {
			this.message = Objects.requireNonNull(message);
			return this;
		}

		Message build() {
			if(login == null || message == null) {
				throw new IllegalStateException();
			}
			return new Message(login, message);
		}
	}

	private final String login;
	private final String message;

	private Message(String login, String message) {
		this.login = login;
		this.message = message;
	}
	
	public String getLogin() {
		return login;
	}
	public String getMessage() {
		return message;
	}
	
	int size() {
		return Integer.BYTES *2 + (login.length() + message.length()) * Character.BYTES;  
	}

}
