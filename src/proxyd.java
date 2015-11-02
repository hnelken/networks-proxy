import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.concurrent.ConcurrentHashMap;
import java.net.InetAddress;

/**
 * This class serves as a simple web proxy that accepts GET requests from browsers.
 * Requests spawn a thread that forwards the request and a thread to write back the response.
 * Persistent connections are supported with the client. Host connections are made to be closed.
 * @author Harry Nelken (hrn10)
 */
public class proxyd {

	private static Hashtable<String, String> cache;
	
	/**
	 * This process accepts all requests and spawns the necessary request threads.
	 * @param args Use -port <port#> to specify which port requests are accepted on.
	 * @throws IOException If an IOException occurs working with sockets.
	 */
	public static void main(String[] args) throws IOException {
		
		// Thread count
		int threadCount = 0;
		
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
		
		// Initialize proxy
		proxyd.cache = new Hashtable<String, String>();

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
		private final Socket client;

		// This thread's number
		private int threadNum;
		
		// Number of response threads
		private int threadCount;
		
		// The constructor takes the socket the request was made on
		public RequestThread(Socket client, int threadNum) {
			super("RequestThread#" + threadNum);
			this.client = client;
			this.threadNum = threadNum;
			this.threadCount = 0;
		}

		// The response thread's behavior while running
		public void run() {
			try {
				// Byte buffer (POST requests can be binary)
				byte[] buff = new byte[8196];
				InputStream fromClient = client.getInputStream();
				
				// Read the request as long as there is data coming through
				for (int length; (length = fromClient.read(buff)) > 0;) {
					// Get info about the request
					HashMap<String, String> info =
							parseAndCleanRequest(buff, length);
					
					if (info != null) {
						// Resolve address of host through cache or DNS lookup
						String hostname = info.get("hostname");
						String hostAddress = resolveHostname(hostname);
						
						// Open a connection with the host on the HTTP port
						Socket host = new Socket(hostAddress, 80);
	
						byte[] bytes = info.get("request").getBytes();
	
						// Write the request to the host (flush after each byte)
						OutputStream toHost = host.getOutputStream();
						for (int i = 0; i < bytes.length; i++) {
							toHost.write(bytes[i]);
							toHost.flush();
						}
						
						System.out.println("Request ");
						System.out.println(info.get("request"));
						System.out.flush();
						
						// Spawn a thread to handle the response
						new ResponseThread(client, host, threadNum + "." + threadCount++).start();
					}
				}
			}
			catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		/**
		 * Removes any "keep-alive" headers and inserts a "Connection: close" line in the request
		 */
		private HashMap<String, String> parseAndCleanRequest(byte[] buff, int length) {

			// Prepare result hash
			HashMap<String, String> requestInfo = new HashMap<String, String>();
			
			// Convert the request to a string
			String request = new String(buff, 0, length);
			
			// If its a text based request
			if (request.contains("GET")) {

				// Parse the request while building a new version
				StringBuilder requestBuilder = new StringBuilder();
				String[] lines = request.split(System.lineSeparator());
				for (int i = 0; i < lines.length; i++) {
					
					// Parse for Host header
					if (lines[i].contains("Host: ")) {
						// Split the "Host: ..." header on the space
						String[] tokens = lines[1].split(" ");
						
						// Take the second token as the host name
						requestInfo.put("hostname", tokens[1].trim());
					}
					// Clean the line otherwise
					requestBuilder.append(cleanRequestLine(i, lines));
				}
				// Add the cleaned request to the request info
				requestInfo.put("request", requestBuilder.toString());
				return requestInfo;
			}
			// Not going to send this request
			return null;
		}
		
		/**
		 *  This cleans a request line in case it is a "Connection:" header
		 */
		private String cleanRequestLine(int line, String[] lines) {
			
			StringBuilder lineBuilder = new StringBuilder();
			
			// Append the "Connection: close" header right before the end
			if (line == lines.length - 1) {
				lineBuilder.append("Connection: close");
				lineBuilder.append(System.lineSeparator());
			}
			
			// Append all non-connection related headers
			if (!lines[line].contains("Connection:") &&
					!lines[line].contains("Keep-Alive:") &&
					!lines[line].contains("Proxy-Connection:")) {
				
				lineBuilder.append(lines[line]);
				lineBuilder.append(System.lineSeparator());
			}
			// Return the cleaned line
			return lineBuilder.toString();
		}
		
		// This helper resolves the address of the host through the cache or a DNS lookup
		private String resolveHostname(String hostname) throws UnknownHostException {
			// Search the cache for the address before doing a DNS lookup
			if (!proxyd.cache.containsKey(hostname)) {
				// Address not in cache, do a lookup
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
		
		// Constructor takes client and host sockets
		public ResponseThread(Socket client, Socket host, String threadName) {
			super("ResponseThread#" + threadName);
	        this.client = client;
	        this.host = host;
	    }
		
		// Handles responses from the host
		public void run() {
			
			// Data buffer (responses can be binary data)
			byte[] buff = new byte[8196];
            
			try {
				// Turn off keep alive
				host.setKeepAlive(false);
				
				// The stream for reading the response from the host
				InputStream fromHost = host.getInputStream();
				// The stream for writing the response to the client
				OutputStream toClient = client.getOutputStream();
				try {
					// As long as the host is putting out data, write it to the client
					for (int length; (length = fromHost.read(buff)) != -1;) {
						// Write response to client
						for (int i = 0; i < length; i++) {
							toClient.write(buff[i]);
							toClient.flush();
						}
					}
					// Finished getting from host
					fromHost.close();
				}
				catch (SocketException e) {
					System.err.println("****ERROR-" + this.getName() + "****");
					e.printStackTrace();
				}
            } 
			catch (IOException e) {
				e.printStackTrace();
			}
			finally {
				// Non-persistent with host, close up host connection
				try {
					host.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
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
