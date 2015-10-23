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
				// Char buffer (requests are always text only)
				char[] buff = new char[4048];
				BufferedReader fromClient = new BufferedReader(new InputStreamReader(client.getInputStream()));
				
				// Read the request and check the contents
				int length = (client.isClosed()) ? 0 : fromClient.read(buff); 
				
				if (length > 0) {
					
					// Print the request
					String request = new String(buff, 0, length);
					System.out.println("REQUEST");
					System.out.println("-------");
					System.out.println(request);
					
					// Parse request for hostname
					String[] lines = request.split("\n");	// Split the headers into separate lines
					String[] tokens = lines[1].split(" ");	// Split the "Host: ..." header on the space
					String hostname = tokens[1].trim();		// Take the second token as the hostname
					
					// Do a DNS lookup of intended host
					InetAddress address = InetAddress.getByName(hostname);
					String hostAddress = address.getHostAddress();
					
					// Open a connection with the server
					Socket host = new Socket(hostAddress, 80);
					
					// Spawn a response thread
					new ResponseThread(client, host).start();

					// Write the request to the host
					OutputStream toHost = host.getOutputStream();
					for (int i = 0; i < length; i++) {
						toHost.write(buff[i]);
						toHost.flush();
					}
				}
			}
			catch (IOException e) {
				System.err.println("******REQUEST ERROR*******");
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * This class writes the response from the host back to the client.
	 * The socket connections are closed when complete.
	 */
	private static class ResponseThread extends Thread {
		
		// The client and host sockets
		private final Socket client, host;
		
		// Constructor takes client and host sockets
		public ResponseThread(Socket client, Socket host) {
	        super("ResponseThread");
	        this.client = client;
	        this.host = host;
	    }
		
		// Handles responses from the host
		public void run() {
			// Data buffer (responses can be binary data)
			byte[] buff = new byte[8196];
            
			try {
				// The stream for reading from the host
				InputStream fromServer = host.getInputStream();
				// The stream for writing to the client
				OutputStream toClient = client.getOutputStream();
		        
				// As long as the host is putting out data, write it to the client
				for (int length; !host.isClosed() && (length = fromServer.read(buff)) != -1;) {
					if (!client.isClosed()) {
						toClient.write(buff, 0, length);
					}
				}
				
				// Close connections
				client.close();
				host.close();
            } 
			catch (IOException e) {
            	System.err.println("******RESPONSE ERROR*******");
				e.printStackTrace();
			}
		}
	}

}
