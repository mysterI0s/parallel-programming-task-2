import handler.RequestsHandlerSocket;
import util.Log;
import util.PoolFactory;

import java.io.*;
import java.net.*;
import java.util.concurrent.ExecutorService;

/**
 * This program demonstrates a simple TCP/IP socket server.
 *
 * @author www.codejava.net
 */
public class WebServerSocket {

    private static ExecutorService executorService;
    private static final int PORT = 8000;
    private static boolean running = true;

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {

            System.out.println("WebServerSocket is listening on port " + PORT);

            executorService = PoolFactory.fromEnvironment();

            while (running) {
                // The main thread only accepts connections; the work itself is
                // wrapped in a Runnable and handed to a pool thread.
                Socket socket = serverSocket.accept();
                Log.info("Accepted connection from " + socket.getRemoteSocketAddress() + ", handing it to the pool");
                RequestsHandlerSocket handler = new RequestsHandlerSocket(socket);
                executorService.submit(handler);
            }

        } catch (IOException ex) {
            System.out.println("Web server socket exception: " + ex.getMessage());
            ex.printStackTrace();
        } finally {
            if (executorService != null) {
                executorService.shutdown();
            }
        }
    }
}
