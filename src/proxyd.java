import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;

/**
 * This class serves as a simple web proxy that accepts GET and POST requests.
 * Requests spawn a thread that forwards the request and then spawns a response thread.
 * @author Harry Nelken
 */
public class proxyd {

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
			System.out.println("Started on port: " + port);
		} 
		catch (IOException e) {
        	System.err.println("******LISTENING ERROR*******");
			System.err.println("Could not listen on port " + port);
			System.exit(-1);
		}

		// Start listening for requests
		while (listening) {
        	Socket client = serverSocket.accept();
        	
        	// Create a thread to forward the request
        	new RequestThread(client).start();
		}
		
		// Clean up
		serverSocket.close();
	}
	
	/**
	 * This class handles the reading and forwarding of incoming requests.
	 * A response thread is spawned once the request is forwarded entirely.
	 */
	private static class RequestThread extends Thread {

		// The socket the request was made on
		private final Socket client;

		// The constructor takes the socket the request was made on
		public RequestThread(Socket client) {
			super("RequestThread");
			this.client = client;
		}

		// The response thread's behavior while running
		public void run() {
			try {
				// Setup to read the incoming request
				char[] buff = new char[4048];
				BufferedReader fromClient = new BufferedReader(new InputStreamReader(client.getInputStream()));
				
				// Read the request and check the contents
				int length = fromClient.read(buff);
				
				if (length > 0) {
					
					// Parse the request for the hostname
					String hostname = parseRequestForHostname(buff, length);
					/*
					// Print the request
					String request = new String(buff, 0, length);
					System.out.println("REQUEST");
					System.out.println("-------");
					System.out.println(request);
					
					// Parse request for hostname
					String[] lines = request.split("\n");	// Split the headers into separate lines
					String[] tokens = lines[1].split(" ");	// Split the "Host: ..." header on the space
					String hostname = tokens[1].trim();		// Take the second token as the hostname
					System.out.println("Hostname is: " + hostname + '\n'); */
					
					// Do a DNS lookup of intended host
					InetAddress address = InetAddress.getByName(hostname);
					
					// Open a connection with the server
					Socket host = new Socket(address.getHostAddress(), 80);
					System.out.println("Connected to host " + hostname + ": " + address.getHostAddress() + '\n');
					
					// Spawn a response thread
					new ResponseThread(client, host).start();
					System.out.println("Response thread spawned\n");

					// Write the request to the host
					OutputStream toHost = host.getOutputStream();
					for (int i = 0; i < length; i++) {
						toHost.write(buff[i]);
						toHost.flush();
					}
				}
				else {	// Request was bogus, close up
					fromClient.close();
					client.close();
				}
			}
			catch (IOException e) {
				System.err.println("******REQUEST ERROR*******");
				e.printStackTrace();
			}
		}
		
		private String parseRequestForHostname(char[] buff, int length) {
			// Print the request
			String request = new String(buff, 0, length);
			System.out.println("REQUEST");
			System.out.println("-------");
			System.out.println(request);
			
			// Parse request for hostname
			String[] lines = request.split("\n");	// Split the headers into separate lines
			String[] tokens = lines[1].split(" ");	// Split the "Host: ..." header on the space
			String hostname = tokens[1].trim();		// Take the second token as the hostname (without the newline)
			System.out.println("Hostname is: " + hostname + '\n');
			return hostname;
		}
		
		private String resolveHostname(String hostname) {
			boolean cached = true;
			
			// TODO: Search cache for hostname
			cached = false;
			
			if (!cached) {
				// Do a DNS lookup of intended host
				try {
					InetAddress address = InetAddress.getByName(hostname);
				}
				catch (IOException e) {
					System.err.println("******DNS ERROR*******");
					e.printStackTrace();
				}
			}
			
			return "";
		}
	}
	
	private static class ResponseThread extends Thread {
		
		private final Socket client, host;
		
		public ResponseThread(Socket client, Socket host) {
	        super("ResponseThread");
	        this.client = client;
	        this.host = host;
	    }
		
		public void run() {
			// Copy response
			byte[] b = new byte[8196];
            
			try {
				if (!client.isClosed() && !host.isClosed()) {
					OutputStream toClient = client.getOutputStream();
					InputStream fromServer = host.getInputStream();
		            
					for (int length; (length = fromServer.read(b)) != -1;) {
						toClient.write(b, 0, length);
					}
		
					toClient.close();
					fromServer.close();
					System.out.println("Streams close\n");
				}
            } 
			catch (IOException e) {
            	System.err.println("******RESPONSE ERROR*******");
				e.printStackTrace();
			}
			finally {
	            try {
	            	client.close();
	            	host.close();
	            	System.out.println("Sockets closed\n");
	            } 
	            catch (IOException e) {
	            	System.err.println("******CLOSING ERROR*******");
	                e.printStackTrace();
	            }
			}
		}
	}

}
