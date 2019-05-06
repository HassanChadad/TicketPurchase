package FEService;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Front end service main class that receives all the HTTP requests and sends them
 * as threads to the Request Handler class
 *
 * @author Hassan Chadad
 */
public class FEServiceHandler extends Thread {

    private static int port;
    private final static Logger log = LogManager.getRootLogger(); // Log Object to print to the System.out.println file
    private static FrontEndDetails frontEndDetails;

    /**
     * main method that iterates through args array and gets all the hosts including it's host.
     * Then it adds all the details to the FrontEndDetails class and calls the sendRegisterRequest function.
     *
     * @param args
     */
    public static void main(String[] args) {
        try {
            String eventPrimaryHost = "http://";
            String userPrimaryHost = "http://";
            String host = "";
            for (int i = 0; i < args.length; i += 2) {
                if (args[i].equalsIgnoreCase("-host"))
                    host += args[i + 1];
                if (args[i].equalsIgnoreCase("-primaryE"))
                    eventPrimaryHost += args[i + 1];
                if (args[i].equalsIgnoreCase("-primaryU"))
                    userPrimaryHost += args[i + 1];
            }
            String[] temp = host.split(":"); // split in order to get the port
            port = Integer.parseInt(temp[1]);
            frontEndDetails = new FrontEndDetails(host, eventPrimaryHost, userPrimaryHost);

            // register frontend in both
            if(sendRegisterRequest(eventPrimaryHost + "/newFE", "event") && sendRegisterRequest(userPrimaryHost + "/newFE", "user")) {
                System.out.println("I registered myself to primary services");
                new FEServiceHandler().startServer();
            }

        } catch (Exception e) {
            log.debug(e);
            System.exit(0);
        }
    }

    /**
     * A method that starts the FE Service and accepts client requests,
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
                        threads.submit(new RequestHandler(clientSocket, frontEndDetails));
                    }
                } catch (IOException e) {
                    log.debug("Unable to process client request");
                }
            }
        };
        Thread serverThread = new Thread(serverTask);
        serverThread.start();
    }

    /**
     * A method that sends the host of the current frontEnd to the primary services, it is like
     * a registration process for the frontend before it starts listening.
     * If the registration was successful, the frontend will send the service the host of the other service.
     * then the frontEnd starts listening/accepting connection sockets,
     * otherwise it exits.
     *
     * @param url - host of the primary Event Service
     */
    private static boolean sendRegisterRequest(String url, String service) {
        try {
            URL obj = new URL(url);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();

            con.setRequestMethod("GET"); // if POST or GET
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("Cookie", frontEndDetails.getHost()); // send the host in the request

            int responseCode = con.getResponseCode();
            con.disconnect();
            if(responseCode == 200) {
                String api = "";
                JSONObject jsonHost = new JSONObject();
                if(service.equals("user")) {
                    api = frontEndDetails.getUserPrimaryHost() + "/setEventPrimary";
                    jsonHost.put("host", frontEndDetails.getEventPrimaryHost());
                }
                else {
                    api = frontEndDetails.getEventPrimaryHost() + "/setUserPrimary";
                    jsonHost.put("host", frontEndDetails.getUserPrimaryHost());
                }
                sendRequest(api, "POST", jsonHost.toJSONString());
                return true;
            }
            else {
                System.exit(0);
                return false;
            }
        } catch (Exception e) {
            System.out.println("Register failed.");
            System.exit(0);
            return false;
        }
    }

    /**
     * A method that sends a request to the event/user service and return back the response
     *
     * @return service's response
     */
    private static void sendRequest(String url, String method, String jsonData) {
        try {
            //System.out.println(url + "\t" + method + "\t" + jsonData);
            URL obj = new URL(url);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();

            con.setRequestMethod(method); // if POST or GET
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("Cookie", frontEndDetails.getHost()); // send the host of the frontend as a cookie

            if (method.equals("POST")) { // if post then write post body
                String urlParameters = jsonData;
                // Send post request
                con.setDoOutput(true);
                con.setDoInput(true);
                PrintWriter pw = new PrintWriter(new OutputStreamWriter(con.getOutputStream()));
                pw.print(urlParameters);
                pw.flush();
                pw.close();
            }

            int responseCode = con.getResponseCode();

        } catch (Exception e) {
            log.debug(e);
        }
    }
}
