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
					BufferedReader clientOutput = new BufferedReader(new InputStreamReader(client.getInputStream()));
					InputStream fromClient = client.getInputStream();
					byte[] buffer = new byte[8196];
					
					
					// Read the request
					int len = fromClient.read(buffer);
			
					if (len > 0) {
						// Print the request
						String request = new String(buffer, 0, len);
						System.out.println("REQUEST: " + System.currentTimeMillis());
						System.out.println("-------");
						System.out.println(request);
						
						// Parse request for hostname
						String[] lines = request.split("\n");
						String[] tokens = lines[0].split(" ");
						String hostname = tokens[1];
						System.out.println("Hostname is: " + hostname);
						
						// Do a DNS lookup of intended host
						InetAddress address = InetAddress.getByName(hostname);
						
						// Open a connection with the server and spawn a response thread
						Socket server = new Socket(address.getHostAddress(), 80);
						System.out.println("Connected to host " + hostname + ": " + address.getHostAddress());
						OutputStream toServer = server.getOutputStream();
						new ResponseThread(client, server).start();
						System.out.println("Response thread spawned");
							
						for (int i = 0; i < len; i++) {
							toServer.write(buffer[i]);
							toServer.flush();
						}
						//toServer.close();
						//fromClient.close();
						//server.close();
					}
					else {
						//fromClient.close();
					}
		        }
		        catch (IOException e) {
		            e.printStackTrace();
		        }
	    }
	}
	
	private static class ResponseThread extends Thread {
		
		private final Socket client, server;
		
		public ResponseThread(Socket client, Socket server) {
	        super("ResponseThread");
	        this.client = client;
	        this.server = server;
	    }
		
		public void run() {
			// Copy response
			byte[] b = new byte[8196];
            
			try {
				OutputStream toClient = client.getOutputStream();
				InputStream fromServer = server.getInputStream();
	            
				for (int length; (length = fromServer.read(b)) != -1;) {
					toClient.write(b, 0, length);
				}
	
				toClient.close();
				fromServer.close();
				System.out.println("Streams closed: " + System.currentTimeMillis());
            } 
			catch (IOException e) {
				e.printStackTrace();
			}
			finally {
	            try {
	            	client.close();
	            	server.close();
	            	System.out.println("Sockets closed: " + System.currentTimeMillis());
	            } 
	            catch (IOException e) {
	                e.printStackTrace();
	            }
	        }
		}
	}

}
