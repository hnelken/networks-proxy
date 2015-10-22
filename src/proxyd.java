import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

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
        	Socket socket = serverSocket.accept();
        	
        	// Create a thread to forward the request
            new RequestThread(socket).start();
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
	    private final Socket clientSocket;

	    // The constructor takes the socket the request was made on
	    public RequestThread(Socket socket) {
	    	super("RequestThread");
	        this.clientSocket = socket;
	    }

	    // The response thread's behavior while running
	    public void run() {
	        try {
	            // Setup to read the incoming request
	            InputStream incommingIS = clientSocket.getInputStream();
	            byte[] b = new byte[8196];
	            
	            // Read the request
	            int len = incommingIS.read(b);

	            if (len > 0) {
	            	// Print the request
	                System.out.println("REQUEST" + '\n' + "-------");
	                System.out.println(new String(b, 0, len));
	               
	                // Write request
	                Socket socket = new Socket("localhost", 80);
	                OutputStream outgoingOS = socket.getOutputStream();
	                
	                for (int i = 0; i < len; i++) {
	                	outgoingOS.write(b[i]);
	                	outgoingOS.flush();
	                }

	            //    outgoingOS.close();
	            //    incommingIS.close();

	           //     socket.close();
	              
	            }
	         //   incommingIS.close();
	        }
	        catch (IOException e) {
	            e.printStackTrace();
	        }
		/*
	        finally {
	            try {
	            	clientSocket.close();
	            } 
	            catch (IOException e) {
	                e.printStackTrace();
	            }
	        }
		*/
	    }
	}
	
	private static class ResponseThread extends Thread {
		
		private final Socket clientSocket;
		
		public ResponseThread(Socket socket) {
	        super("ResponseThread");
	        this.clientSocket = socket;
	    }
		
		public void run() {
			// Copy response
			byte[] b = new byte[8196];
            OutputStream incommingOS;
            
			try {
				incommingOS = clientSocket.getOutputStream();
			
	            InputStream outgoingIS = clientSocket.getInputStream();
	            for (int length; (length = outgoingIS.read(b)) != -1;) {
	                incommingOS.write(b, 0, length);
	            }
	
	            incommingOS.close();
	            outgoingIS.close();
	            
            } catch (IOException e) {
				e.printStackTrace();
			} finally {
	            try {
	            	clientSocket.close();
	            } catch (IOException e) {
	                e.printStackTrace();
	            }
	        }
		}
	}

}
