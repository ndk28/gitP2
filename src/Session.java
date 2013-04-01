import java.util.Date;
import java.util.UUID;

public class Session {
	
	public String sid;
	public String primaryHost;
	public int primaryPort;
	public String backupHost;
	public int backupPort;
	public int version;
	public Date discard_time;
	public String message;

	public static final int COOKIE_TIMEOUT = 10;
	
	/**
	 * Generate a new session data object. This is for cases where we generate a new
	 * cookie on empty requests.
	 */
	public Session(String host, int port) {
		this.primaryHost = host;
		this.primaryPort = port;
		this.sid = UUID.randomUUID().toString() + "-" + host + "-" + port;
		this.version = 1;
		this.discard_time = new Date((new Date()).getTime() + COOKIE_TIMEOUT);
		this.message = "Hello there!";
	}
	
	/**
	 * Build a session data object from a data string.
	 * @param data
	 */
	public Session(String data) {
		String[] fields = data.split("_");
		this.sid = fields[0];
		this.version = Integer.parseInt(fields[1]);
		this.primaryHost = fields[2];
		this.primaryPort = Integer.parseInt(fields[3]);
		this.backupHost = fields[4];
		this.backupPort = Integer.parseInt(fields[5]);
		this.message = fields[6];
		this.discard_time = new Date((new Date()).getTime() + COOKIE_TIMEOUT);
	}
	
	public String toString() {
		String ret = ""
				+ this.sid + "_"
				+ this.version + "_"
				+ this.primaryHost + "_"
				+ this.primaryPort + "_"
				+ this.backupHost + "_"
				+ this.backupPort + "_"
				+ this.message;
		return ret;
	}

}
