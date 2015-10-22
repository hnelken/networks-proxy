import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * This class serves as a simple web proxy that accepts GET and POST requests.
 * Each request spawns a thread that forwards the request and one that handles the response.
 * @author hnelken
 *
 */
public class proxyd {

	public static void main(String[] args) throws IOException {
		ServerSocket serverSocket = null;
        boolean listening = true;

        int port = 10000;	//default
       
        for (int i = 0; i < args.length; i++) {
        	if (args[i].equals("-port")) {
        		port = Integer.parseInt(args[i+1]);
        		break;
        	}
        }

        try {
            serverSocket = new ServerSocket(port);
            System.out.println("Started on: " + port);
        } catch (IOException e) {
            System.err.println("Could not listen on port: " + args[0]);
            System.exit(-1);
        }

        while (listening) {
        	Socket socket = serverSocket.accept();
            new RequestThread(socket).start();
            new ResponseThread(socket).start();
        }
        
        serverSocket.close();
	}
	
	private static class RequestThread extends Thread {

	    private final Socket serverSocket;

	    public RequestThread(Socket socket) {
	        this.serverSocket = socket;
	    }

	    public void run() {
	        try {
	            // Read request
	            InputStream incommingIS = serverSocket.getInputStream();
	            byte[] b = new byte[8196];
	            int len = incommingIS.read(b);

	            if (len > 0) {
	                System.out.println("REQUEST"
	                        + System.getProperty("line.separator") + "-------");
	                System.out.println(new String(b, 0, len));

	                // Write request
	                Socket socket = new Socket("localhost", 80);
	                OutputStream outgoingOS = socket.getOutputStream();
	                outgoingOS.write(b, 0, len);

	                
	                outgoingOS.close();
	                incommingIS.close();

	                socket.close();
	            } else {
	                incommingIS.close();
	            }
	        } catch (IOException e) {
	            e.printStackTrace();
	        } finally {
	            try {
	                serverSocket.close();
	            } catch (IOException e) {
	                e.printStackTrace();
	            }
	        }
	    }
	}
	
	private static class ResponseThread extends Thread {
		
		private final Socket socket;
		
		public ResponseThread(Socket socket) {
	        super("ResponseThread");
	        this.socket = socket;
	    }
		
		public void run() {
			// Copy response
			byte[] b = new byte[8196];
            OutputStream incommingOS;
            
			try {
				incommingOS = socket.getOutputStream();
			
	            InputStream outgoingIS = socket.getInputStream();
	            for (int length; (length = outgoingIS.read(b)) != -1;) {
	                incommingOS.write(b, 0, length);
	            }
	
	            incommingOS.close();
	            outgoingIS.close();
	            
            } catch (IOException e) {
				e.printStackTrace();
			} finally {
	            try {
	                socket.close();
	            } catch (IOException e) {
	                e.printStackTrace();
	            }
	        }
		}
	}

}
