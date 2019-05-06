package EventService;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Event service main class that receives all the HTTP requests and sends them
 * as threads to the Request Handler class.
 * It guarantees thread-safe concept.
 *
 * @author Hassan Chadad
 */
public class EventsServiceHandler extends Thread {

    private static String eventPrimaryHost;
    private static String userPrimaryHost;
    private static EventServiceDetails eventServiceDetails; // eventServiceDetails object access by other classes
    private static LuceneSearch luceneSearch;
    private static Election election; // election object accessed by other classes
    private final static Logger log = LogManager.getRootLogger(); // Log Object to print to the System.out.println file

    /**
     * main method that gets all the hosts from the args array and create election object and eventServiceDetails object
     * and adds the hosts to it.
     * Then it calls startServer and addService methods.
     *
     * @param args
     */
    public static void main(String[] args) {
        try {
            eventPrimaryHost = "http://";
            userPrimaryHost = "http://";
            String host = "http://";
            int port = 0;
            for (int i = 0; i < args.length; i += 2) {
                if (args[i].equalsIgnoreCase("-host"))
                    host += args[i + 1];
                if (args[i].equalsIgnoreCase("-primaryE"))
                    eventPrimaryHost += args[i + 1];
                if (args[i].equalsIgnoreCase("-primaryU"))
                    userPrimaryHost += args[i + 1];
            }
            String[] temp = host.split(":"); // split in order to get the port
            port = Integer.parseInt(temp[2]);

            eventServiceDetails = EventServiceDetails.getInstance(host, eventPrimaryHost, port, userPrimaryHost);
            election = Election.getInstance();
            luceneSearch = LuceneSearch.getInstance();

            new EventsServiceHandler().startServer();
            new EventsServiceHandler().addService();
        } catch (Exception e) {
            log.debug(e);
            System.exit(0);
        }
    }

    /**
     * A method that starts the Event Service and accepts client requests,
     * then assign a new thread to work for each client connected
     */
    public void startServer() {
        final ExecutorService threads = Executors.newCachedThreadPool();

        Runnable serverTask = new Runnable() {

            @Override
            public void run() {
                try {
                    ServerSocket welcomingSocket = new ServerSocket(eventServiceDetails.getPort());
                    System.out.println("Waiting for clients to connect...");
                    while (true) {
                        Socket clientSocket = welcomingSocket.accept();
                        threads.submit(new GeneralRequestHandler(clientSocket, userPrimaryHost));
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
     * After opening the server for listening, this method will check if the current service is primary or secondary
     * by calling isPrimary method.
     * If this is the primary then it's host will be added to the memberMap and the primary variable will be set to true
     * Otherwise, this service will send an addMember request to the primary (as a registration) and gets all the data from it
     * including (FE list, membersMap, operation ID, EventMap). Upon success, this service will start sending heartbeat
     * messages to everyone in the membersMap
     */
    private void addService() {
        String host = eventServiceDetails.getHost();
        if (isPrimary(host)) {
            eventServiceDetails.setPrimary(true);
            eventServiceDetails.addNewMember(host);
            System.out.println("I am primary. HaHaHa");
        } else {
            //log.debug("I am secondary so I will send for primary to add me : host " + host);
            System.out.println("I am secondary :(");
            RequestSender requestSender = new RequestSender();
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("memberHost", host);
            boolean response = requestSender.sendRequestBool(eventPrimaryHost + "/addMember", "POST", jsonObject.toJSONString());
            if (response) {
                System.out.println("All data replicated..");
                System.out.println("I am added");
                log.debug("All lists from primary \n" + eventServiceDetails.getAllLists() + "\n---------------------");
                InternalRequestParser internalRequestParser = new InternalRequestParser();
                internalRequestParser.startHeartBeat();
            } else {
                System.out.println("Wasn't added.");
                System.exit(0);
            }
        }
    }

    /**
     * A method that compares the current host with event primary host
     * and return true on match otherwise false.
     *
     * @param host - current
     * @return true/false
     */
    private boolean isPrimary(String host) {
        if (eventPrimaryHost.equals(host))
            return true;
        return false;
    }
}

