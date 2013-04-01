import java.util.*;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.javatuples.Pair;

public class Servlet extends HttpServlet {

	private static final long serialVersionUID = 1L;
	private static final String COOKIE_NAME = "CS5300PROJ2SESSION";
	public ConcurrentHashMap<String, Session> sessionTable = new ConcurrentHashMap<String, Session>();
	private ArrayList<Member> GroupMembers = new ArrayList<Member>();
	private UDPServer server;
	private int callIDNumber;
	private int socket_timeout = 3000;
	private int serverPort;
	private String serverAddress;
	private int packet_length = 512;
	private int delta = 10;
	private int gamma = 5;
	private boolean first_req;

	/**
	 * Servlet constructor.
	 * 
	 * Initializes a Janitor thread to clean the sessionTable periodically.
	 * @throws UnknownHostException 
	 * @throws NumberFormatException 
	 */
	public Servlet() throws ServletException {
		super();
		new Janitor(this).start();
		this.server = new UDPServer(this);
		this.server.start();
		while (server.getAddress() == null) { System.out.println("stuck");}
		this.serverPort = server.Port;
		this.serverAddress = server.Address;
		this.first_req = true;
		try {
			this.callIDNumber = this.serverPort * 10000;
		} catch (NumberFormatException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * 
	 */
	public ConcurrentHashMap<String, Session> getTable(){
		return this.sessionTable;
	}
	
	/**
	 * 
	 */
	public Session sTableRead(String sid){
		return this.sessionTable.get(sid);
	}
	
	/**
	 * 
	 */
	public void sTableWrite(Session s){
		this.sessionTable.put(s.sid,  s);
	}
	
	/**
	 * 
	 */
	public void sTableRemove(String sid) {
		this.sessionTable.remove(sid);
	}
	/**
	 * 
	 */
	public void mGroupAdd(Member m){
		this.GroupMembers.add(m);
	}
	
	/**
	 * 
	 */
	public void mGroupRemove(Member m){
		this.GroupMembers.remove(m);
	}

	/**
	 * 
	 * @param size
	 * @return if size >= GroupMembers.size(), then return string representation of entire list.
	 * otherwise, return a string representation of (size) entries picked uniformly at random
	 */
	public String mGroupToString(int size) {
		int count = 0;
		String out = "";
		// randomizes the ArrayList
		Collections.shuffle(GroupMembers);
		while (count < GroupMembers.size() && count < 20) {
			Member m = GroupMembers.get(count);
			out = out + m.host + ":" + m.port + "/";
			count++;
		}
		out = out.substring(0,out.length()-1); // chops off the trailing "/"
		return out;
	}
	/**
	 * @throws IOException 
	 * 
	 */
	private String GetMembers(int sz, Member primary) throws IOException {
		DatagramSocket socket = new DatagramSocket();
		socket.setSoTimeout(this.socket_timeout);
		this.callIDNumber++;
		String outBuf = "" + this.callIDNumber + "%GetMembers%" + this.serverAddress + "%" + this.serverPort + "%" + sz;
		DatagramPacket p = new DatagramPacket(outBuf.getBytes(), outBuf.getBytes().length, InetAddress.getByName(primary.host), primary.port);
		socket.send(p);
		byte[] inBuf = new byte[packet_length];
		DatagramPacket recv = new DatagramPacket(inBuf,inBuf.length);
		try {
			socket.receive(recv);
			String data = p.getData().toString();
			String[] received = data.split("%");
			if (Integer.parseInt(received[0]) == this.callIDNumber && 
					recv.getAddress() == InetAddress.getByName(primary.host)) {
				socket.close();
				return received[1];
			}
		}
		catch (SocketTimeoutException e) {
			GroupMembers.remove(primary);
		}
		socket.close();
		return null;
	}
	
	/**
	 * @throws IOException 
	 * 
	 */
	private void SessionDelete(String sid) throws IOException{
		DatagramSocket socket = new DatagramSocket();
		socket.setSoTimeout(this.socket_timeout);
		for (Member m : GroupMembers) {
			this.callIDNumber++;
			String outBuf = "" + this.callIDNumber + "%Delete%" + this.serverAddress + "%" + this.serverPort + "%" + sid;
			DatagramPacket p = new DatagramPacket(outBuf.getBytes(), outBuf.getBytes().length, InetAddress.getByName(m.host), m.port);
			socket.send(p);
			byte[] inBuf = new byte[packet_length];
			DatagramPacket recv = new DatagramPacket(inBuf,inBuf.length);
			try {
				// just to see if members are still active. don't really care if the delete succeeded or not
				socket.receive(recv);
			}
			catch (SocketTimeoutException e) {
				GroupMembers.remove(m);
			}
		}
		socket.close();
	}
	
	/**
	 * @throws IOException 
	 * 
	 */
	private Pair<Session,String> SessionReadClient(String sid, Member primary, Member backup) throws IOException{
		DatagramSocket socket = new DatagramSocket();
		socket.setSoTimeout(this.socket_timeout);
		this.callIDNumber++;
		String outBuf = "" + this.callIDNumber + "%Read%" + this.serverAddress + "%" + this.serverPort + "%" + sid;
		DatagramPacket pp = new DatagramPacket(outBuf.getBytes(), outBuf.getBytes().length, InetAddress.getByName(primary.host), primary.port);
		DatagramPacket sp = new DatagramPacket(outBuf.getBytes(), outBuf.getBytes().length, InetAddress.getByName(backup.host), backup.port);
		socket.send(pp);
		socket.send(sp);
		byte[] inBuf = new byte[packet_length];
		DatagramPacket recvPacket = new DatagramPacket(inBuf, inBuf.length);
		boolean confirmed = false;
		try {
			while(!confirmed){
				socket.receive(recvPacket);
				String data = recvPacket.getData().toString();
				String[] received = data.split("%");
				if (received.length != 4) { continue; }
				// check both that callID is correct as well as correct host
				if (Integer.parseInt(received[0]) == this.callIDNumber && 
						(recvPacket.getAddress() == InetAddress.getByName(primary.host) ||
						recvPacket.getAddress() == InetAddress.getByName(backup.host))){
					Session read_session = new Session(received[1]);
					String found_on = received[2] + ":" + received[3];
					socket.close();
					return Pair.with(read_session, found_on);
				}
			}
		} catch (SocketTimeoutException e) {
			e.printStackTrace();
		}
		socket.close();
		return Pair.with(null, "");
	}

	/**
	 * @throws IOException 
	 * 
	 */
	private boolean SessionWriteClient(Session s, Member back_up) throws IOException{
		InetAddress back_up_host = InetAddress.getByName(back_up.host);
		this.callIDNumber++;
		String outBuf = "" + this.callIDNumber + "%Write%" + this.serverAddress + "%" + this.serverPort + "%" + s.toString();
		DatagramSocket socket = new DatagramSocket();
		socket.setSoTimeout(this.socket_timeout);
		DatagramPacket p = new DatagramPacket(outBuf.getBytes(), outBuf.getBytes().length, back_up_host, back_up.port);
		socket.send(p);
		byte[] inBuf = new byte[packet_length];
		DatagramPacket recvPacket = new DatagramPacket(inBuf, inBuf.length);
		boolean confirmed = false;
		try{
			while (!confirmed) {
				socket.receive(p);
				String data = p.getData().toString();		
				if (Integer.parseInt(data) == this.callIDNumber && 
						recvPacket.getAddress() == InetAddress.getByName(back_up.host)) { 
					socket.close();
					return true;
				}
			}
		}
		catch (SocketTimeoutException e){
			socket.close();
			return false;
		}
		socket.close();
		return false;
	}

	/**
	 * Respond to HTTP GET requests.
	 * 
	 * If the request has no session cookie, create a new one, save it in the local sessionTable with IPP_local
	 * as IPP_primary, then send a SessionWrite to a random Group Member as IPP_backup.
	 * 
	 * If the request has a session cookie, try to find it in the local sessionTable or send a SessionRead to
	 * IPP_primary and IPP_backup to see that the session is still alive and valid. If it is, increment the
	 * version number, save it locally and to a random Group Member, then respond. If it's not valid, send
	 * the cookie back with a MAX_AGE of 0 to delete it from the client.
	 * @throws IOException 
	 * @throws ServletException 
	 * 
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
		//Session session = getCookieSession(request);
		Cookie new_cookie = new Cookie(COOKIE_NAME, "");
		Cookie[] reqCookies = request.getCookies();
		String[] cookie_data = null;
		Session new_session = null;
		if (reqCookies != null){
			for (Cookie c : reqCookies) {
				if (c.getName().equals(COOKIE_NAME)) {
					cookie_data = c.getValue().split("#");
				}
			}
		}
		
		// Query parameter parsing in order to respond accordingly
		Map<String, String[]> params = request.getParameterMap();
		String action = params.containsKey("cmd") ? params.get("cmd")[0] : "";
		
		// if crash, stop responding to RPC and HTTP requests
		if (action.equals("Crash")) {
			System.exit(0);
		}
		
		// cookie_data is null, new session.
		if (cookie_data == null) {
			new_session = new Session(this.serverAddress, this.serverPort);
			request.setAttribute("foundOn", "");
		}
		// cookie exists already. find session state
		else {
			// cookie_data has sid, version, primaryhost, primaryport, backuphost, backupport
			String sid = cookie_data[0];
			int version = Integer.parseInt(cookie_data[1]); // technically don't need version as hashtable only contains the most recent version
			String phost = cookie_data[2];
			int pport = Integer.parseInt(cookie_data[3]);
			String bhost = cookie_data[4];
			int bport = Integer.parseInt(cookie_data[5]);
			
			if (action.equals("LogOut")) {
				sessionTable.remove(sid);
				SessionDelete(sid);
				new_cookie.setMaxAge(0);
				response.addCookie(new_cookie);
				request.getRequestDispatcher("/logout.jsp").forward(request, response);
				return;
			}
			
			Member primary = new Member(phost, pport);
			Member backup = null;
			if (!bhost.equals("0.0.0.0") || bport != 0) {
				backup = new Member(bhost, bport);
			}
			
			// local server is either primary or backup. no RPC call necessary
			if (phost.equals(this.serverAddress) || bhost.equals(this.serverAddress)) {
				if (phost.equals(this.serverAddress)) { 
					if (backup != null) {
						GroupMembers.add(backup); 
						System.out.println("line 321");
					}
				}
				else { 
					GroupMembers.add(primary); 
					System.out.println("line 325");
				}
				new_session = this.sessionTable.get(sid);
				new_session.version++;
				request.setAttribute("foundOn", this.serverAddress + ":" + this.serverPort);
			}
			// local server does not have most recent session state. need RPC call
			else {
				GroupMembers.add(primary);
				GroupMembers.add(backup);
				System.out.println("line 337");
				// if newly booted up, call GetMembers to speed up group membership protocol
				// a callID, in string form, uses at most 9 chars. "#" uses 1. that leaves 512 - 10 = 502 
				// bytes for members. each member uses at most 15 bytes for the IP address (xxx.xxx.xxx.xxx),
				// 1 byte for ":", and 5 bytes for the port. 502/21 ~= 24. But just to be safe, we will use 20.
				if (this.first_req) {
					String mbr_string = GetMembers(20, primary);
					if (mbr_string == null) { 
						//if (!backup.host.equals("0.0.0.0") || backup.port != 0) {
						if (backup != null) {
							GetMembers(20, backup); 
						}
					}
					if (mbr_string != null) {
						String[] mbrs = mbr_string.split("/");
						for (String s : mbrs) {
							String[] host_port = s.split(":");
							GroupMembers.add(new Member(host_port[0],Integer.parseInt(host_port[1])));
						}
					}
					first_req = false;
				}
				List<Member> dests = new ArrayList<Member>();
				dests.add(primary);
				dests.add(backup);
				Pair<Session,String> pair = SessionReadClient(sid, primary, backup);
				new_session = pair.getValue0();
				// RPC calls timed out. Start a new session for the user
				if(new_session == null) { 
					GroupMembers.remove(phost);
					GroupMembers.remove(bhost);
					request.setAttribute("error", "Sorry, your session state has unfortunately been lost. A new session has been created for you.");
					new_session = new Session(this.serverAddress.toString(), this.serverPort);
					request.setAttribute("foundOn", "");
				}
				// RPC did not time out. Session state was found.
				else {
					new_session.version++;
					request.setAttribute("foundOn", pair.getValue1());
				}
			}
		}
		
		// Replace message text, if requested
		if (action.equals("Replace")) {
			String message = params.get("NewText")[0];
			Pattern p = Pattern.compile("[^a-zA-Z0-9]");
			if (p.matcher(message).find()) {
				request.setAttribute("error", "Error: Messages can only include alphanumeric characters.");
			}
			else {
				String old_msg = new_session.message;
				new_session.message = message;
				if (new_session.toString().getBytes().length > packet_length) {
					new_session.message = old_msg;
					request.setAttribute("error", "Error: New message is too long.");
				}
			}
		}
		request.setAttribute("message", new_session.message);
		new_session.primaryHost = this.serverAddress;
		new_session.primaryPort = this.serverPort;
		//System.out.println("Primary host is" + new_session.primaryHost);
		//System.out.println("Primary port is" + new_session.primaryPort);
		
		// set cookie timeout and session discard_time
		new_cookie.setMaxAge(Session.COOKIE_TIMEOUT + delta);
		request.setAttribute("expiry", new Date((new Date()).getTime() + 1000*(Session.COOKIE_TIMEOUT + delta)));
		new_session.discard_time = 
				new Date((new Date()).getTime() + 1000*(Session.COOKIE_TIMEOUT + 2*delta + gamma));
		request.setAttribute("discard_time", new_session.discard_time);
		System.out.println("checkpoint A");
		// pick a random group member to set as back up
		boolean confirmed = false;
		// continuously chooses potential backup until (a) one is found, or (b) they all timeout
		while(!confirmed && GroupMembers.size() > 0){
			//System.out.println(GroupMembers);
			int rand = new Random().nextInt(GroupMembers.size());
			Member new_backup = GroupMembers.get(rand);
			System.out.println(new_backup.host);
			new_session.backupHost = new_backup.host;
			new_session.backupPort = new_backup.port;
			if(SessionWriteClient(new_session, new_backup)) {confirmed = true;}
			else { GroupMembers.remove(new_backup); }
		}
		// this only happens during a newly-booted server, or if more than 1 failure has already occurred
		if (GroupMembers.size() == 0) {
			new_session.backupHost = "0.0.0.0";
			new_session.backupPort = 0;
		}
		
		// put into local hashtable
		System.out.println(new_session);
		this.sessionTable.put(new_session.sid, new_session);
		//System.out.println(new_session);
		// set cookie, set attributes, send to jsp
		String new_cookie_data = new_session.sid + "#" + new_session.version + "#" + new_session.primaryHost + 
				"#" + new_session.primaryPort + "#" + new_session.backupHost + "#" + new_session.backupPort;
		new_cookie.setValue(new_cookie_data);
		response.addCookie(new_cookie);
		request.setAttribute("serverID", this.serverAddress + ":" + this.serverPort);
		request.setAttribute("sid", new_session.sid);
		request.setAttribute("version", new_session.version);
		request.setAttribute("IPPprimary", new_session.primaryHost + ":" + new_session.primaryPort);
		request.setAttribute("IPPbackup", new_session.backupHost + ":" + new_session.backupPort);
		request.setAttribute("MbrSet", GroupMembers.toString());
		request.setAttribute("hashmap", sessionTable.toString());
		request.getRequestDispatcher("/index.jsp").forward(request, response);
		
	}




}
