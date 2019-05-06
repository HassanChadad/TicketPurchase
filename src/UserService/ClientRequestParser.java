package UserService;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A class that handles all the requests sent by the clients only and returns the response back.
 *
 * @author Hassan Chadad
 */
public class ClientRequestParser {

    private String jsonData;
    private UserServiceDetails userServiceDetails;
    private final static Logger log = LogManager.getRootLogger(); // Log Object to print to the System.out.println file

    /**
     * Constructor
     *
     * @param jsonData
     */
    public ClientRequestParser(String jsonData) {
        this.jsonData = jsonData;
        userServiceDetails = UserServiceDetails.getInstance("", "", 0, "");
    }

    /**
     * A method that checks if the method is GET/POST and sends the request to the appropriate function
     * If the method is POST and the member is primary then it will add the current thread and give it an operation ID
     *
     * @param headerAttr
     * @return response from method called
     */
    public String parse(String[] headerAttr) {
        if (headerAttr[0].equals("GET"))
            return handleGetMethods(headerAttr[1]);
        else if (headerAttr[0].equals("POST")) {
            return handlePostMethods(headerAttr[1]);
        } else
            return "400";
    }

    /**
     * A method that handles GET requests and checks the api and sends the request to the appropriate method
     * then returns the response of the method called
     *
     * @param apiReq
     * @return response
     */
    private String handleGetMethods(String apiReq) {
        try {
            if (apiReq.matches("[\\d]+"))  // return user with userId
                return userServiceDetails.getUser(Integer.parseInt(apiReq));
            else
                return "400";
        } catch (Exception e) {
            return "400";
        }
    }

    /**
     * A method that handles POST requests and checks the api and sends the request to the appropriate method
     * then returns the response of the method called
     *
     * @param apiReq
     * @return
     */
    private String handlePostMethods(String apiReq) {
        if (apiReq.equals("login")) // log in doesn't need operation id
            return login();
        else if (apiReq.equals("logout")) // log out doesn't need operation id
            return logout();
        else {
            if (userServiceDetails.isPrimary()) {
                userServiceDetails.lockOperationThread();
                userServiceDetails.addOperationThread(this);
            }
            if (apiReq.equals("create"))
                return createUser(apiReq);
            else if (apiReq.matches("[\\d]+/tickets/add"))
                return addTickets(apiReq);
            else if (apiReq.matches("[\\d]+/tickets/transfer"))
                return transferTickets(apiReq);
            else if (apiReq.matches("tickets/[\\d]+/return"))
                return returnTickets(apiReq);
            else if (apiReq.equals("delete-tickets"))
                return deleteEventTickets(apiReq);
            else
                return "400";
        }
    }

    /**
     * A thread safe method that creates a new user and adds it to the userMap,
     * then if the member is primary it passes the client request to all secondaries with the operation ID and waits for
     * a response back. On success the primary deletes the operation thread, while secondary increments operation ID
     * Then it returns a response to the client.
     *
     * @param request
     * @return user ID json format (success) - 400 (failure)
     */
    private String createUser(String request) {
        boolean success = false;
        int opId = -1;
        try {
            userServiceDetails.lockWriteLock();
            JSONParser parser = new JSONParser();
            JSONObject jsonObject = (JSONObject) parser.parse(jsonData);

            long jsonOperId = -1;
            if (!userServiceDetails.isPrimary()) { // if secondary
                jsonOperId = (Long) jsonObject.get("operationId"); // get operation ID
                blockThread((int) jsonOperId);
            }

            String userName = (String) jsonObject.get("username");
            String password = (String) jsonObject.get("password");
            if (userName.length() == 0)
                return "400";
            if (password.length() == 0)
                return "400";

            /* for demonstration purpose only */
            if (!userServiceDetails.isPrimary())
                System.out.println("/create excuted of id " + userServiceDetails.getOperationId());
            else {
                opId = userServiceDetails.getOperationIdForCurrent(this);
                System.out.println("/create excuted of id " + opId);
            }
            /* ----------------------- */
            String result = userServiceDetails.createUser(userName, password);
            if (result.equals("400"))
                return "400";
            if (userServiceDetails.isPrimary()) {
                opId = userServiceDetails.getOperationIdForCurrent(this);
                if (opId > -1) {
                    jsonObject.put("operationId", opId);
                    sendMultiRequests(request, "POST", jsonObject.toJSONString());
                    success = true;
                }
            } else {
                userServiceDetails.incrementOperationId();
                success = true;
            }
            return result;
        } catch (Exception e) {
            log.debug(e);
            return "400";
        } finally {
            try {
                if (!success) // if primary failed to execute the operation, it will delete it and decrement
                    rollBackOperationId(opId);
                userServiceDetails.unlockWriteLock(); // release the lock
                if (userServiceDetails.isPrimary()) {
                    userServiceDetails.unlockOperationThread();
                }
            } catch (Exception e) {
                log.debug(e);
            }
        }

    }

    /**
     * A thread safe method that deletes all tickets of an event for all the users who purchased tickets for this event.
     * if the member is primary it passes the client request to all secondaries with the operation ID and waits for
     * a response back. On success the primary deletes the operation thread, while secondary increments operation ID
     * Then it returns a response to the client.
     *
     * @param request
     * @return user ID json format (success) - 400 (failure)
     */
    private String deleteEventTickets(String request) {
        boolean success = false;
        int opId = -1;
        try {
            userServiceDetails.lockWriteLock();
            JSONParser parser = new JSONParser();
            JSONObject jsonObject = (JSONObject) parser.parse(jsonData);

            long jsonOperId = -1;
            if (!userServiceDetails.isPrimary()) { // if secondary
                jsonOperId = (Long) jsonObject.get("operationId"); // get operation ID
                blockThread((int) jsonOperId);
            }

            long eventId = (Long) jsonObject.get("eventid");
            if (eventId <= 0)
                return "400";

            /* for demonstration purpose only */
            if (!userServiceDetails.isPrimary())
                System.out.println("/delete ticket for event excuted of id " + userServiceDetails.getOperationId());
            else {
                opId = userServiceDetails.getOperationIdForCurrent(this);
                System.out.println("/delete tickets for event excuted of id " + opId);
            }

            if (userServiceDetails.deleteEventTickets((int) eventId).equals("400"))
                return "400";
            if (userServiceDetails.isPrimary()) {
                opId = userServiceDetails.getOperationIdForCurrent(this);
                if (opId > -1) {
                    jsonObject.put("operationId", opId);
                    sendMultiRequests(request, "POST", jsonObject.toJSONString());
                    success = true;
                }
            } else {
                userServiceDetails.incrementOperationId();
                success = true;
            }
            return "";
        } catch (Exception e) {
            log.debug(e);
            return "400";
        } finally {
            try {
                if (!success) // if primary failed to execute the operation, it will delete it and decrement
                    rollBackOperationId(opId);
                userServiceDetails.unlockWriteLock(); // release the lock
                if (userServiceDetails.isPrimary()) {
                    userServiceDetails.unlockOperationThread();
                }
            } catch (Exception e) {
                log.debug(e);
            }
        }

    }

    /**
     * A thread safe method that deletes all tickets of an event for a specific user.
     * if the member is primary it passes the client request to all secondaries with the operation ID and waits for
     * a response back. On success the primary deletes the operation thread, while secondary increments operation ID
     * Then it returns a response to the client.
     *
     * @param request
     * @return user ID json format (success) - 400 (failure)
     */
    private String returnTickets(String request) {
        boolean success = false;
        int opId = -1;
        int key = -1;
        try {
            JSONParser parser = new JSONParser();
            JSONObject jsonObject = (JSONObject) parser.parse(jsonData);

            long jsonOperId = -1;
            if (!userServiceDetails.isPrimary()) { // if secondary
                jsonOperId = (Long) jsonObject.get("operationId"); // get operation ID
                blockThread((int) jsonOperId);
            }

            String[] reqArray = request.split("/");
            int userId = Integer.parseInt(reqArray[1]);

            if (!userServiceDetails.lockUserListWriteLock(userId)) // lock the ticketMap on specific event
                return "400";
            key = userId;

            long eventId = (Long) jsonObject.get("eventid");
            if (eventId <= 0)
                return "400";

            /* for demonstration purpose only */
            if (!userServiceDetails.isPrimary())
                System.out.println("/delete tickets for specific user excuted of id " + userServiceDetails.getOperationId());
            else {
                opId = userServiceDetails.getOperationIdForCurrent(this);
                System.out.println("/delete tickets for specific user excuted of id " + opId);
            }

            int originalTickets = userServiceDetails.deleteSpecificUserTickets(userId, (int) eventId);
            if (originalTickets == -1)
                return "400";
            if (userServiceDetails.isPrimary()) {
                RequestSender requestSender = new RequestSender();
                String url = userServiceDetails.getPrimaryEventHost() + "/tickets/" + originalTickets + "/return";
                jsonObject.put("userid", userId);
                boolean eventTicketReturnReq = requestSender.sendRequestBool(url, "POST", jsonObject.toJSONString());
                if(eventTicketReturnReq) {
                    opId = userServiceDetails.getOperationIdForCurrent(this);
                    if (opId > -1) {
                        jsonObject.put("operationId", opId);
                        sendMultiRequests(request, "POST", jsonObject.toJSONString());
                        success = true;
                    }
                }
                else {
                    userServiceDetails.restoreSpecificUserTickets(userId, (int)eventId, originalTickets);
                    return "400";
                }
            } else {
                userServiceDetails.incrementOperationId();
                success = true;
            }
            return "";
        } catch (Exception e) {
            log.debug(e);
            return "400";
        } finally {
            try {
                if (!success) // if primary failed to execute the operation, it will delete it and decrement
                    rollBackOperationId(opId);
                userServiceDetails.unlockUserListWriteLock(key);
                if (userServiceDetails.isPrimary()) {
                    userServiceDetails.unlockOperationThread();
                }
            } catch (Exception e) {
                log.debug(e);
            }
        }

    }

    /**
     * A thread safe method that add the tickets of a user. Then on success it will pass the purchase request
     * to the secondaries otherwise it will return 400.
     *
     * @param request
     * @return empty string (success) - 400 (failure)
     */
    private String addTickets(String request) {
        boolean success = false;
        int key = -1;
        int operId = -1;
        try {
            JSONParser parser = new JSONParser();
            JSONObject jsonObject = (JSONObject) parser.parse(jsonData);

            long jsonOperId = -1;
            if (!userServiceDetails.isPrimary()) { // if secondary then get operation ID
                jsonOperId = (Long) jsonObject.get("operationId");
                blockThread((int) jsonOperId);
            }

            String[] reqArray = request.split("/");
            int userId = Integer.parseInt(reqArray[0]);
            long eventId = (Long) jsonObject.get("eventid");
            long tickets = (Long) jsonObject.get("tickets");

            if (!userServiceDetails.lockUserListWriteLock(userId)) // lock the purchase on specific user
                return "400";
            key = userId;
            /* for demonstration purpose only */
            if (!userServiceDetails.isPrimary())
                System.out.println("/purchase excuted of id " + userServiceDetails.getOperationId());
            else {
                operId = userServiceDetails.getOperationIdForCurrent(this);
                System.out.println("/purchase excuted of id " + operId);
            }
            // purchase is successfully added in the user map
            if (userServiceDetails.addTickets(userId, (int) eventId, (int) tickets).equals("")) {
                if (userServiceDetails.isPrimary()) {
                    int opId = userServiceDetails.getOperationIdForCurrent(this);
                    if (opId > -1) {
                        jsonObject.put("operationId", opId);
                        sendMultiRequests(request, "POST", jsonObject.toJSONString());
                        success = true;
                    }
                } else {
                    userServiceDetails.incrementOperationId();
                    success = true;
                }
                return "";
            } else
                return "400";
        } catch (Exception e) {
            return "400";
        } finally {
            try {
                if (!success)
                    rollBackOperationId(operId);
                userServiceDetails.unlockUserListWriteLock(key);
                if (userServiceDetails.isPrimary()) {
                    userServiceDetails.unlockOperationThread();
                }
            } catch (Exception e) {
                log.debug(e);
            }
        }
    }

    /**
     * A thread safe method that will transfer tickets from one user to another. Then on success it will pass the purchase request
     * to the secondaries otherwise it will rollback.
     *
     * @param request
     * @return empty string (success) - 400 (failure)
     */
    private String transferTickets(String request) {
        boolean success = false;
        int key = -1;
        int operId = -1;
        try {
            JSONParser parser = new JSONParser();
            JSONObject jsonObject = (JSONObject) parser.parse(jsonData);

            long jsonOperId = -1;
            if (!userServiceDetails.isPrimary()) { // if secondary then get operation ID
                jsonOperId = (Long) jsonObject.get("operationId");
                blockThread((int) jsonOperId);
            }

            String[] reqArray = request.split("/");
            int userId = Integer.parseInt(reqArray[0]);
            long eventId = (Long) jsonObject.get("eventid");
            long tickets = (Long) jsonObject.get("tickets");
            long targetUser = (Long) jsonObject.get("targetuser");

            if (!userServiceDetails.lockUserListWriteLock(userId)) // lock the purchase on specific user
                return "400";
            key = userId;
            /* for demonstration purpose only */
            if (!userServiceDetails.isPrimary())
                System.out.println("/transfer tickets excuted of id " + userServiceDetails.getOperationId());
            else {
                operId = userServiceDetails.getOperationIdForCurrent(this);
                System.out.println("/transfer tickets excuted of id " + operId);
            }
            // purchase is successfully added in the user map
            if (userServiceDetails.transferTickets(userId, (int) eventId, (int) tickets, (int) targetUser).equals("")) {
                if (userServiceDetails.isPrimary()) {
                    int opId = userServiceDetails.getOperationIdForCurrent(this);
                    if (opId > -1) {
                        jsonObject.put("operationId", opId);
                        sendMultiRequests(request, "POST", jsonObject.toJSONString());
                        success = true;
                    }
                } else {
                    userServiceDetails.incrementOperationId();
                    success = true;
                }
                return "";
            } else
                return "400";
        } catch (Exception e) {
            return "400";
        } finally {
            try {
                if (!success)
                    rollBackOperationId(operId);
                userServiceDetails.unlockUserListWriteLock(key);
                if (userServiceDetails.isPrimary()) {
                    userServiceDetails.unlockOperationThread();
                }
            } catch (Exception e) {
                log.debug(e);
            }
        }
    }

    /**
     * A thread safe method that logs the user in after checking if it is not logged in before and starts a session for it
     * and returns the response back.
     *
     * @return empty string
     */
    private String login() {
        boolean success = false;
        int key = -1;
        int operId = -1;
        try {
            JSONParser parser = new JSONParser();
            JSONObject jsonObject = (JSONObject) parser.parse(jsonData);

            String username = (String) jsonObject.get("username");
            String password = (String) jsonObject.get("password");
            int userId = userServiceDetails.login(username, password);
            if (userId == -1)
                return "400";

            key = userId;

            System.out.println("/login request");
            RequestSender requestSender = new RequestSender();

            /* check if the user already logged in */
            String result = requestSender.sendRequestJson("http://mc02.cs.usfca.edu:2355/" + userId, "GET", "");
            int session = Integer.parseInt(result);
            if (session > 0) // user is already logged in so it can't log in again
                return "400";
            else {
                JSONObject json = new JSONObject();
                json.put("id", userId + "");
                result = requestSender.sendRequestJson("http://mc02.cs.usfca.edu:2355/start", "POST", json.toJSONString());
                if (result.equals(""))
                    success = true;
                return result;
            }
        } catch (Exception e) {
            return "400";
        } finally {
            try {
                userServiceDetails.unlockUserListWriteLock(key);
                if (userServiceDetails.isPrimary()) {
                    userServiceDetails.unlockOperationThread();
                }
            } catch (Exception e) {
                log.debug(e);
            }
        }
    }

    /**
     * A thread safe method that logs the user out after checking if it is logged in and stops the session for it
     * and returns the response back.
     *
     * @return empty string (success)
     */
    private String logout() {
        boolean success = false;
        int key = -1;
        int operId = -1;
        try {
            JSONParser parser = new JSONParser();
            JSONObject jsonObject = (JSONObject) parser.parse(jsonData);

            String username = (String) jsonObject.get("username");
            int userId = userServiceDetails.checkUser(username);
            if (userId == -1)
                return "400";
            key = userId;

            System.out.println("/logout request");
            RequestSender requestSender = new RequestSender();

            /* check if the user already logged in */
            String result = requestSender.sendRequestJson("http://mc02.cs.usfca.edu:2355/" + userId, "GET", "");
            int session = Integer.parseInt(result);
            if (session <= 0) // user is not logged in so it can't log out
                return "400";
            else {
                JSONObject json = new JSONObject();
                json.put("id", userId + "");
                result = requestSender.sendRequestJson("http://mc02.cs.usfca.edu:2355/stop", "POST", json.toJSONString());
                if (result.equals(""))
                    success = true;
                return result;
            }
        } catch (Exception e) {
            return "400";
        } finally {
            try {
                userServiceDetails.unlockUserListWriteLock(key);
                if (userServiceDetails.isPrimary()) {
                    userServiceDetails.unlockOperationThread();
                }
            } catch (Exception e) {
                log.debug(e);
            }
        }
    }

    /**
     * A method that checks if the operation id sent in the json is the same as the current operation ID in member details,
     * if it is not then it will wait to be notified in the future
     *
     * @param jsonOperId
     */
    private synchronized void blockThread(int jsonOperId) {
        try {
            log.debug("json is " + jsonOperId + "\n" + userServiceDetails.getOperationId());
            while (userServiceDetails.getOperationId() != jsonOperId) { // wait for your turn
                wait();
                log.debug("I am awake so check again");
            }
        } catch (Exception e) {
            log.debug("Request from FE 124" + e);
        }
    }

    /**
     * A method that notifies the thread to wake it up
     */
    public synchronized void wakeThread() {
        //log.debug("notifying thread");
        notify();
    }

    /**
     * A method that passes the client url to all the seconday members to guarantee replication
     *
     * @param url
     * @param method
     * @param jsonData
     * @throws Exception
     */
    private synchronized void sendMultiRequests(String url, String method, String jsonData) throws Exception {
        ExecutorService threads = Executors.newCachedThreadPool();
        SortedMap<Integer, String> map = userServiceDetails.getMembershipMap();
        if (map.size() > 1) { // one is the primary so everything after one will be secondary
            log.debug(url + " start sending operation to all members / size" + map.size());
            SortedMap<Integer, ThreadRequestSender> threadRequestSenderMap = new TreeMap<>();
            String myHost = userServiceDetails.getHost();
            for (int key : map.keySet()) {
                    /* skip sending to myself */
                if (!myHost.equals(map.get(key))) {
                    //log.debug(map.get(key) + url + "," + method + "," + jsonData);
                    ThreadRequestSender threadRequestSender = new ThreadRequestSender(map.get(key) + "/" + url, method, jsonData, this, "client");
                    threadRequestSenderMap.put(key, threadRequestSender);
                    threads.submit(threadRequestSender);
                }

            }
            while (true && threadRequestSenderMap.size() > 0) {
                boolean responded = true;
                for (int key : threadRequestSenderMap.keySet()) {
                    String response = threadRequestSenderMap.get(key).getResponse();
                    if (response.equals("no")) {
                        responded = false;
                        break;
                    } else if (response.equals("error")) { // member is dead
                        userServiceDetails.deleteMember(map.get(key)); // delete member
                        System.out.println(map.get(key) + " is dead.");
                        break;
                    }
                }
                if (responded)
                    break;
                wait();
            }
        }
        log.debug("All Members replied to " + url);
    }

    /**
     * A method that decrements the operation ID because the request failed to execute.
     * It is only accessed by the primary.
     */
    private void rollBackOperationId(int id) {
        if (userServiceDetails.isPrimary())
            userServiceDetails.decrementOperationId(id);
    }

    /**
     * A method that wakes the thread up.
     * Since thread will be in different objects then there is no way for 2 threads to be at 2 different methods
     * at the same object, that is why notifyALL
     */
    public synchronized void wake() {
        this.notifyAll();
    }
}
