package Session;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Session main class that maintains a session for each user
 *
 * @author Hassan Chadad
 */
public class SessionMain extends Thread {

    private static final int port = 2355;
    private final static Logger log = LogManager.getRootLogger(); // Log Object to print to the System.out.println file
    private SortedMap<String, SessionTimer> sessionTimerSortedMap = new TreeMap<>(); // session map that saves <userID, sessionTimer object>

    /**
     * main method that calls startServer method.
     *
     * @param args
     */
    public static void main(String[] args) {
        try {
            new SessionMain().startServer();

        } catch (Exception e) {
            log.debug(e);
            System.exit(0);
        }
    }

    /**
     * A method that starts the session Service and accepts client requests,
     * then assign a new thread to work for each client connected
     */
    public void startServer() {
        final ExecutorService threads = Executors.newCachedThreadPool();

        Runnable serverTask = new Runnable() {

            @Override
            public void run() {
                try {
                    ServerSocket welcomingSocket = new ServerSocket(port);
                    System.out.println("Waiting for clients to connect...");
                    while (true) {
                        Socket clientSocket = welcomingSocket.accept();
                        threads.submit(new RequestHandler(clientSocket, sessionTimerSortedMap));
                    }
                } catch (IOException e) {
                    log.debug("Unable to process client request");
                }
            }
        };
        Thread serverThread = new Thread(serverTask);
        serverThread.start();
    }
}
