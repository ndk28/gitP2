import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

public class UDPServer extends Thread {

	static int msgLength = 512;
	private Servlet servlet;
	public int Port;
	public String Address;
	

	public UDPServer(Servlet s){
		this.servlet = s;
	}
	
	public int getPort(){
		return this.Port;
	}
	
	public String getAddress(){
		return this.Address;
	}

	public void run() {
		
		//System.out.println("Server started");
		
		DatagramSocket sk = null;
		InetAddress localaddr = null;
		try {
			localaddr = InetAddress.getLocalHost();
		} catch (UnknownHostException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
		}
		int randport = (int)(Math.random() * 40000) + 10000;
		try {
			sk = new DatagramSocket(randport,localaddr);
		} catch (SocketException e1) {
			e1.printStackTrace();
		}
		this.Port = sk.getLocalPort();
		this.Address = sk.getLocalAddress().toString().substring(1);
		//System.out.println(this.Address);
		while(true){
			
			byte[] inBuf = new byte[msgLength];
			DatagramPacket dgp = new DatagramPacket(inBuf, inBuf.length);
			
			try {
				sk.receive(dgp);
			} catch (IOException e) {
				e.printStackTrace();
			}
			String data = new String(dgp.getData(), 0, dgp.getLength());
			InetAddress returnAddr = dgp.getAddress();
			int returnPort = dgp.getPort();
			String[] fields = data.split("%");
			
			// if incoming RPC call does not have at exactly fields, it's garbage. ignore
			if (fields.length != 5) { continue; }
			
			String outBuf = "";
			String callIDNumber = fields[0];
			String cmd = fields[1];
			String saddress = fields[2];
			String sport = fields[3];
			this.servlet.mGroupAdd(new Member(saddress, Integer.parseInt(sport)));
			
			if (cmd == "GetMembers") {
				int sz = Integer.parseInt(fields[4]);
				String s = this.servlet.mGroupToString(sz);
				outBuf = callIDNumber + "%" + s;
			}
			else if (cmd == "Delete") {
				String sid = fields[4];
				this.servlet.sTableRemove(sid);
				outBuf = callIDNumber;
			}
			else if (cmd == "Read") {
				// send back session with this sid
				String sid = fields[4];
				Session response = this.servlet.sTableRead(sid);
				outBuf = callIDNumber + "%" + response.toString() + "%" + this.Address + "%" + this.Port;
			}
			else if (cmd == "Write") {
				// write this session to our servlets sessiontable
				Session s = new Session(fields[4]);
				this.servlet.sTableWrite(s);
				outBuf = callIDNumber;
			}
			else {
				continue;
			}
			try {
				DatagramPacket out = new DatagramPacket(outBuf.getBytes(), outBuf.getBytes().length, returnAddr, returnPort);
				sk.send(out);
			} catch (IOException e) {
				e.printStackTrace();
			}
			sk.close();
		}
	}
}
