import java.util.Date;
import java.util.Iterator;
import java.util.Map;

public class Janitor extends Thread {
	
	private Servlet servlet;
	
	public Janitor(Servlet s) {
		this.servlet = s;
	}
	
	public void run() {
		while (true) {
			synchronized (servlet.sessionTable) {
		    	Iterator<Map.Entry<String, Session>> it = servlet.sessionTable.entrySet().iterator();
				while (it.hasNext()) {
					Map.Entry<String, Session> entry = it.next();
					Session session = entry.getValue();
					if (session.discard_time.getTime() < (new Date()).getTime()) {
						servlet.sessionTable.remove(entry.getKey());
					}
				}
	    	}
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e) {
				continue;
			}
		}
	}

}
