import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Hashtable;
import java.net.InetAddress;

/**
 * This class serves as a simple web proxy that accepts GET and POST requests.
 * Requests spawn a thread that forwards the request and then spawns a response thread.
 * @author Harry Nelken
 */
public class proxyd {

	private static int threadCount = 0;
	private static Hashtable<String, String> cache = new Hashtable<String, String>();
	
	/**
	 * This process accepts all requests and spawns the necessary request threads.
	 * @param args Use -port <port#> to specify which port requests are accepted on.
	 * @throws IOException If an IOException occurs working with sockets.
	 */
	public static void main(String[] args) throws IOException {
		
		// The socket between the client and this proxy
		ServerSocket serverSocket = null;
		
		// The activity state of the proxy
		boolean listening = true;

		// The port requests are received through
		int port = 50055;	// I am student 55 in the 325 list
       
		// Grab port number from arguments
		for (int i = 0; i < args.length; i++) {
			// Check for -port argument
			if (args[i].equals("-port")) {
				// Grab the port number
				port = Integer.parseInt(args[i+1]);
				break;
			}
		}

		try {
			// Begin listening on the given port
			serverSocket = new ServerSocket(port);
		} 
		catch (IOException e) {
			System.err.println("Could not listen on port " + port);
			System.exit(-1);
		}

		// Start listening for requests
		while (listening) {
        	Socket client = serverSocket.accept();
        	
        	// Create a thread to forward the request
        	new RequestThread(client, threadCount++).start();
		}
	}
	
	/**
	 * This class handles the reading and forwarding of incoming requests.
	 * A response thread is spawned once the request is forwarded entirely.
	 */
	private static class RequestThread extends Thread {

		// The socket the request was made on
		private int threadCount;
		private final Socket client;

		// The constructor takes the socket the request was made on
		public RequestThread(Socket client, int threadCount) {
			super("RequestThread" + threadCount);
			this.threadCount = threadCount;
			this.client = client;
			System.out.println("RequestThread " + threadCount + " has started");
			System.out.flush();
		}

		// The response thread's behavior while running
		public void run() {
			try {
				// Byte buffer (POST requests can be binary)
				byte[] buff = new byte[8196];
				InputStream fromClient = client.getInputStream();
				String hostname = "www.case.edu";
				
				// Read the request as long as there is data coming through
				for (int length; (length = fromClient.read(buff)) > 0;) {
					
					// Parse request for host name 
					hostname = parseRequestForHostname(buff, length, hostname);
					
					// Resolve address of host through cache or DNS lookup
					String hostAddress = resolveHostname(hostname);
					
					// Open a connection with the host
					Socket host = new Socket(hostAddress, 80);
					
					// Spawn a thread to handle the response
					new ResponseThread(client, host, threadCount).start();

					// Write the request to the host (flush after each byte)
					OutputStream toHost = host.getOutputStream();
					for (int i = 0; i < length; i++) {
						toHost.write(buff[i]);
						toHost.flush();
					}
				}
			}
			catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		// This helper prints the request, parses it, and returns the host name
		private String parseRequestForHostname(byte[] buff, int length, String lastHost) throws IOException {
			// Print the request
			String request = new String(buff, 0, length);
			System.out.println("REQUEST by " + threadCount);
			System.out.println("-------");
			System.out.println(request);
			System.out.flush();
			
			// Parse request for host name
			String[] lines = request.split("\n");		// Split the headers into separate lines
			for (int i = 0; i < lines.length; i++) {	// Find the "Host" header
				if (lines[i].contains("Host: ")) {
					String[] tokens = lines[1].split(" ");	// Split the "Host: ..." header on the space
					return tokens[1].trim();				// Take the second token as the host name
				}
			}
			
			System.err.println("Request is missing host name, sent to " + lastHost);
			return lastHost;
		}
		
		// This helper resolves the address of the host through the cache or a DNS lookup
		private String resolveHostname(String hostname) throws UnknownHostException {
			// Search the cache for the address before doing a DNS lookup
			if (!proxyd.cache.containsKey(hostname)) {
				InetAddress address = InetAddress.getByName(hostname);
				String hostAddress = address.getHostAddress();
				
				// Cache the result
				proxyd.cache.put(hostname, hostAddress);
				
				// Start the timer to discard it after 30s
				new CacheTimerThread(hostname).start();
				
				// Return the resolved address
				return hostAddress;
			}
			else {	// The host name is in the cache
				// Return cached address
				return proxyd.cache.get(hostname);
			}
		}
	}
	
	/**
	 * This class writes the response from the host back to the client.
	 */
	private static class ResponseThread extends Thread {
		
		// The client and host sockets
		private final Socket client, host;
		private int threadCount;
		
		// Constructor takes client and host sockets
		public ResponseThread(Socket client, Socket host, int threadCount) {
	        super("ResponseThread" + threadCount);
	        this.threadCount = threadCount;
	        this.client = client;
	        this.host = host;
	        
	        System.out.println("ResponseThread Spawned for " + threadCount);
			System.out.flush();
	    }
		
		// Handles responses from the host
		public void run() {
			// Data buffer (responses can be binary data)
			byte[] buff = new byte[8196];
            
			try {
				// The stream for reading the response from the host
				InputStream fromServer = host.getInputStream();
				// The stream for writing the response to the client
				OutputStream toClient = client.getOutputStream();

				System.out.println("RESPONSE TO " + threadCount);
				System.out.flush();
				
				boolean open = connectionsOpen();
				// As long as the host is putting out data, write it to the client
				for (int length; open && (length = fromServer.read(buff)) != -1; open = connectionsOpen()) {
					// Write response to client
					for (int i = 0; i < length; i++) {
						toClient.write(buff[i]);
						toClient.flush();
					}
					//toClient.write(buff, 0, length);
					//toClient.flush();
				}
            } 
			catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		private boolean connectionsOpen() {
			return !client.isClosed() && !host.isClosed();
		}
	}
	
	/**
	 * This class runs a timer for each cache entry.
	 * Cache entries are deleted after 30 seconds.
	 */
	private static class CacheTimerThread extends Thread {
		
		String hostname;
		
		// Get cache entry key on creation
		public CacheTimerThread(String hostname) {
			this.hostname = hostname;
		}
		
		public void run() {
			try {
				// Sleep for 30 seconds
				Thread.sleep(30000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			finally {
				// Remove the cache entry
				proxyd.cache.remove(hostname);
			}
		}
	}
}
