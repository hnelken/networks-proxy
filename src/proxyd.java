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
					
					// Clean the connection header in the request
					String request = cleanRequest(buff, length);
					
					// Parse request for host name 
					hostname = parseRequestForHostname(request, hostname);
					
					/*
					// Resolve address of host through cache or DNS lookup
					String hostAddress = resolveHostname(hostname);
					
					// Open a connection with the host on the HTTP port
					Socket host = new Socket(hostAddress, 80);
					
					// Spawn a thread to handle the response
					new ResponseThread(client, host, threadCount).start();

					// Write the request to the host (flush after each byte)
					byte[] bytes = request.getBytes();
					OutputStream toHost = host.getOutputStream();
					for (int i = 0; i < bytes.length; i++) {
						toHost.write(bytes[i]);
						toHost.flush();
					}
					*/
				}
			}
			catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		/**
		 * Removes any "keep-alive" headers and inserts a "Connection: close" line in the request
		 */
		private String cleanRequest(byte[] buff, int length) {

			// Convert the request to a string
			String request = new String(buff, 0, length);
			
			// If its a text based request
			if (request.contains("HTTP ")) {
				
				// Find any existing "Connection" header
				String header = "Connection: ";
				int headerStart = request.indexOf(header);
				
				// If there is one:
				if (headerStart != -1) {
					// Replace it with a "Connection: close" header
					request.replace("Connection: keep-alive", "Connection: close");
					request.replace("Connection: Keep-Alive", "Connection: close");
					
					// And remove any "Keep-Alive" header lines there may be
					request.replaceAll("Keep-Alive: *\n", "");
				}
				else {	// Otherwise insert a "Connection: close" header at the end
					int insertIndex = header.indexOf(System.lineSeparator(), header.lastIndexOf(':'));
					String top = request.substring(0, insertIndex);
					String bottom = request.substring(insertIndex);
					request = top + "\nConnection: close" + bottom;
				}
			}

			// Print the request
			System.out.println("CLEANED REQUEST by " + threadCount);
			System.out.println("-------");
			System.out.println(request);
			System.out.flush();
			
			return request;
		}
		
		// This helper prints the request, parses it, and returns the host name
		private String parseRequestForHostname(String request, String lastHost) throws IOException {
			
			// Split the request into separate header lines for parsing
			String[] lines = request.split("\n");
			for (int i = 0; i < lines.length; i++) {	
				if (lines[i].contains("Host: ")) {						// Find the "Host" header
					String[] tokens = lines[1].split(" ");				// Split the "Host: ..." header on the space
					return tokens[1].trim();							// Take the second token as the host name
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
	 * Connections between host and proxy are non-persistent and are closed.
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
				InputStream fromHost = host.getInputStream();
				// The stream for writing the response to the client
				OutputStream toClient = client.getOutputStream();

				System.out.println("RESPONSE TO " + threadCount);
				System.out.flush();
				
				// As long as the host is putting out data, write it to the client
				for (int length; (length = fromHost.read(buff)) != -1;) {
					// Write response to client
					for (int i = 0; i < length; i++) {
						toClient.write(buff[i]);
						toClient.flush();
					}
				}

				// Non-persistent with host, close up host connections
				System.out.println("Closing host connections");
				fromHost.close();
				host.close();
            } 
			catch (IOException e) {
				e.printStackTrace();
			}
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
