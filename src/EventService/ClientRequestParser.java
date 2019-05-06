package EventService;

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
    private EventServiceDetails eventServiceDetails;
    private final static Logger log = LogManager.getRootLogger(); // Log Object to print to the System.out.println file

    /**
     * Constructor
     *
     * @param jsonData
     */
    public ClientRequestParser(String jsonData) {
        this.jsonData = jsonData;
        eventServiceDetails = EventServiceDetails.getInstance("", "", 0, "");
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
        if (apiReq.equals("list")) {
            return eventServiceDetails.getEventList();
        } else { // return event with eventId
            try {
                return eventServiceDetails.getEvent(Integer.parseInt(apiReq));
            } catch (Exception e) {
                return "400";
            }
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
        if (apiReq.startsWith("search")) // this doesn't require locking operation (not passing to secondaries)
            return searchEvent();
        else {
            if (eventServiceDetails.isPrimary()) {
                eventServiceDetails.lockOperationThread();
                eventServiceDetails.addOperationThread(this);
            }
            if (apiReq.equals("create"))
                return createEvent(apiReq);
            else if (apiReq.startsWith("purchase/"))
                return purchaseTickets(apiReq);
            else if (apiReq.startsWith("update/"))
                return updateEvent(apiReq);
            else if (apiReq.matches("[\\d]+/delete"))
                return deleteEvent(apiReq);
            else if (apiReq.matches("tickets/[\\d]+/return"))
                return returnTickets(apiReq);
            else
                return "400";
        }
    }

    /**
     * A thread safe method that parses the jsonData and passes the extracted values to searchEvent method and return
     * a string of all matched events.
     *
     * @return matched event map JSON format
     */
    private String searchEvent() {
        try {
            JSONParser parser = new JSONParser();
            JSONObject jsonObject = (JSONObject) parser.parse(jsonData);

            String keywords = (String) jsonObject.get("keywords");
            String availJson = (String) jsonObject.get("avail");
            int avail = Integer.parseInt(availJson);
            keywords = keywords.trim();
            return eventServiceDetails.searchEvents(keywords, avail);
        } catch (Exception e) {
            return "400";
        }
    }

    /**
     * A thread safe method that creates a new event and adds it to the EventMap,
     * then if the member is primary it passes the client request to all secondaries with the operation ID and waits for
     * a response back. On success the primary deletes the operation thread, while secondary increments operation ID
     * Then it returns a response to the client.
     *
     * @param request
     * @return event ID json format (success) - 400 (failure)
     */
    private String createEvent(String request) {
        boolean success = false;
        int opId = -1;
        try {
            eventServiceDetails.lockWriteLock();
            JSONParser parser = new JSONParser();
            JSONObject jsonObject = (JSONObject) parser.parse(jsonData);

            long jsonOperId = -1;
            if (!eventServiceDetails.isPrimary()) { // if secondary
                jsonOperId = (Long) jsonObject.get("operationId"); // get operation ID
                blockThread((int) jsonOperId);
            }

            long userId = (Long) jsonObject.get("userid");
            String eventName = (String) jsonObject.get("eventname");
            long numTickets = (Long) jsonObject.get("numtickets");
            if (eventName.length() == 0)
                return "400";
            if (numTickets <= 0)
                return "400";

            /* for demonstration purpose only */
            if (!eventServiceDetails.isPrimary())
                System.out.println("/create excuted of id " + eventServiceDetails.getOperationId());
            else {
                opId = eventServiceDetails.getOperationIdForCurrent(this);
                System.out.println("/create excuted of id " + opId);
            }
            /* ----------------------- */

            String url = eventServiceDetails.getPrimaryUserHost() + "/" + userId;
            RequestSender requestSender = new RequestSender();
            if (requestSender.sendRequestBool(url, "GET", "")) { // check if user exists
                String result = eventServiceDetails.createEvent(eventName, userId + "", numTickets + "");
                if (result.equals("400"))
                    return "400";
                if (eventServiceDetails.isPrimary()) {
                    opId = eventServiceDetails.getOperationIdForCurrent(this);
                    if (opId > -1) {
                        jsonObject.put("operationId", opId);
                        sendMultiRequests(request, "POST", jsonObject.toJSONString());
                        success = true;
                    }
                } else {
                    eventServiceDetails.incrementOperationId();
                    success = true;
                }
                return result;
            } else {
                return "400";
            }
        } catch (Exception e) {
            log.debug(e);
            return "400";
        } finally {
            try {
                if (!success) // if primary failed to execute the operation, it will delete it and decrement
                    rollBackOperationId(opId);
                eventServiceDetails.unlockWriteLock(); // release the lock
                if (eventServiceDetails.isPrimary()) {
                    eventServiceDetails.unlockOperationThread();
                }
            } catch (Exception e) {
                log.debug(e);
            }
        }

    }

    /**
     * A thread safe method that deletes an event from the EventMap, then if the member is primary it sends a user a delete request
     * then it passes the client request to all secondaries with the operation ID and waits for a response back.
     * On success the primary deletes the operation thread, while secondary increments operation ID
     * Then it returns a response to the client.
     *
     * @param request
     * @return event ID json format (success) - 400 (failure)
     */
    private String deleteEvent(String request) {
        boolean success = false;
        int opId = -1;
        try {
            eventServiceDetails.lockWriteLock();
            JSONParser parser = new JSONParser();
            JSONObject jsonObject = (JSONObject) parser.parse(jsonData);

            String[] reqArray = request.split("/");
            int eventId = Integer.parseInt(reqArray[0]);
            long jsonOperId = -1;
            if (!eventServiceDetails.isPrimary()) { // if secondary
                jsonOperId = (Long) jsonObject.get("operationId"); // get operation ID
                blockThread((int) jsonOperId);
            }

            long userId = (Long) jsonObject.get("userid");
            if (userId <= 0)
                return "400";

            /* for demonstration purpose only */
            if (!eventServiceDetails.isPrimary())
                System.out.println("/delete event excuted of id " + eventServiceDetails.getOperationId());
            else {
                opId = eventServiceDetails.getOperationIdForCurrent(this);
                System.out.println("/delete event excuted of id " + opId);
            }
            /* ----------------------- */

            RequestSender requestSender = new RequestSender();
            SortedMap<Integer, String[]> backupMap = eventServiceDetails.deleteEvent(eventId, userId + "");
            if (backupMap == null)
                return "400";
            boolean userTicketDeleteReq = true;
            String url = eventServiceDetails.getPrimaryUserHost() + "/delete-tickets";
            if (eventServiceDetails.isPrimary()) { // prevent secondaries from updating user tickets on purchase
                JSONObject jsonPar = new JSONObject();
                jsonPar.put("eventid", eventId);
                userTicketDeleteReq = requestSender.sendRequestBool(url, "POST", jsonPar.toJSONString());
            }
            if (userTicketDeleteReq) { // all users deleted the tickets for the deleted event
                if (eventServiceDetails.isPrimary()) {
                    if (opId > -1) {
                        jsonObject.put("operationId", opId);
                        sendMultiRequests(request, "POST", jsonObject.toJSONString());
                        success = true;
                    }
                } else {
                    eventServiceDetails.incrementOperationId();
                    success = true;
                }
                return "";
            } else { // if I wasn't able to delete tickets in the user then restore previous event
                eventServiceDetails.restoreBackupEvent(backupMap);
                return "400";
            }
        } catch (Exception e) {
            log.debug(e);
            return "400";
        } finally {
            try {
                if (!success) // if primary failed to execute the operation, it will delete it and decrement
                    rollBackOperationId(opId);
                eventServiceDetails.unlockWriteLock(); // release the lock
                if (eventServiceDetails.isPrimary()) {
                    eventServiceDetails.unlockOperationThread();
                }
            } catch (Exception e) {
                log.debug(e);
            }
        }
    }

    /**
     * A thread safe method that creates a new event and adds it to the EventMap,
     * then if the member is primary it passes the client request to all secondaries with the operation ID and waits for
     * a response back. On success the primary deletes the operation thread, while secondary increments operation ID
     * Then it returns a response to the client.
     *
     * @param request
     * @return event ID json format (success) - 400 (failure)
     */
    private String updateEvent(String request) {
        boolean success = false;
        int opId = -1;
        try {
            eventServiceDetails.lockWriteLock();
            JSONParser parser = new JSONParser();
            JSONObject jsonObject = (JSONObject) parser.parse(jsonData);

            long jsonOperId = -1;
            if (!eventServiceDetails.isPrimary()) { // if secondary
                jsonOperId = (Long) jsonObject.get("operationId"); // get operation ID
                blockThread((int) jsonOperId);
            }

            String[] requestArray = request.split("/");
            int eventId = Integer.parseInt(requestArray[1]);
            long userId = (Long) jsonObject.get("userid");

            // check if the user sent at least 1 attribute to update, if not then return 400
            if (jsonObject.get("eventname") == null && jsonObject.get("additionaltickets") == null)
                return "400";

            String eventName = "";
            if (jsonObject.get("eventname") != null) { // check if the client sent an eventname, it means s/he wants to update it
                eventName = (String) jsonObject.get("eventname");
                eventName = eventName.trim();
                if (eventName.length() == 0)
                    return "400";
            }

            long additionalTickets = 0;
            if (jsonObject.get("additionaltickets") != null) { // check if the client sent an tickets, it means s/he wants to add more tickets
                additionalTickets = (Long) jsonObject.get("additionaltickets");
                if (additionalTickets <= 0)
                    return "400";
            }

            /* for demonstration purpose only */
            if (!eventServiceDetails.isPrimary())
                System.out.println("/update excuted of id " + eventServiceDetails.getOperationId());
            else {
                opId = eventServiceDetails.getOperationIdForCurrent(this);
                System.out.println("/update excuted of id " + opId);
            }
            /* ----------------------- */

            String url = eventServiceDetails.getPrimaryUserHost() + "/" + userId;
            RequestSender requestSender = new RequestSender();
            if (requestSender.sendRequestBool(url, "GET", "")) { // check if user exists
                String result = eventServiceDetails.updateEvent(eventId, eventName, userId + "", (int) additionalTickets);
                if (result.equals("400"))
                    return "400";
                if (eventServiceDetails.isPrimary()) {
                    opId = eventServiceDetails.getOperationIdForCurrent(this);
                    if (opId > -1) {
                        jsonObject.put("operationId", opId);
                        sendMultiRequests(request, "POST", jsonObject.toJSONString());
                        success = true;
                    }
                } else {
                    eventServiceDetails.incrementOperationId();
                    success = true;
                }
                return result;
            } else {
                return "400";
            }
        } catch (Exception e) {
            log.debug(e);
            return "400";
        } finally {
            try {
                if (!success) // if primary failed to execute the operation, it will delete it and decrement
                    rollBackOperationId(opId);
                eventServiceDetails.unlockWriteLock(); // release the lock
                if (eventServiceDetails.isPrimary()) {
                    eventServiceDetails.unlockOperationThread();
                }
            } catch (Exception e) {
                log.debug(e);
            }
        }

    }

    /**
     * A thread safe method that updates the tickets of an event. Then on success it will pass the purchase request
     * to the secondaries otherwise it will rollback.
     *
     * @param request
     * @return empty string (success) - 400 (failure)
     */
    private String purchaseTickets(String request) {
        boolean success = false;
        int key = -1;
        int operId = -1;
        try {
            JSONParser parser = new JSONParser();
            JSONObject jsonObject = (JSONObject) parser.parse(jsonData);

            long jsonOperId = -1;
            if (!eventServiceDetails.isPrimary()) { // if secondary then get operation ID
                jsonOperId = (Long) jsonObject.get("operationId");
                blockThread((int) jsonOperId);
            }

            long userId = (Long) jsonObject.get("userid");
            long jsonEventId = (Long) jsonObject.get("eventid");
            long purchasedTickets = (Long) jsonObject.get("tickets");

            if (!eventServiceDetails.lockEventListWriteLock((int) jsonEventId)) // lock the purchase on specific event
                return "400";
            key = (int) jsonEventId;

            String requestEventId = request.replace("purchase/", ""); // remove part to get ID

            if (Integer.parseInt(requestEventId) != jsonEventId) // check if event id in API = event id in JSON body
                return "400";
            String userHost = eventServiceDetails.getPrimaryUserHost();
            String url = userHost + "/" + userId;
            RequestSender requestSender = new RequestSender();
            if (requestSender.sendRequestBool(url, "GET", "")) { // check if user exists
                int requestEventIdInt = Integer.parseInt(requestEventId);
                url = userHost + "/" + userId + "/tickets/add";
                JSONObject jsonPar = new JSONObject();
                jsonPar.put("eventid", requestEventIdInt);
                jsonPar.put("tickets", purchasedTickets);

                    /* for demonstration purpose only */
                if (!eventServiceDetails.isPrimary())
                    System.out.println("/purchase excuted of id " + eventServiceDetails.getOperationId());
                else {
                    operId = eventServiceDetails.getOperationIdForCurrent(this);
                    System.out.println("/purchase excuted of id " + operId);
                }
                // purchase is successfully added in the event map
                if (eventServiceDetails.purchaseTickets(requestEventIdInt, (int) purchasedTickets,(int)userId).equals("")) {
                    boolean userPurchaseReq = true;
                    if (eventServiceDetails.isPrimary()) // prevent secondaries from updating user tickets on purchase
                        userPurchaseReq = requestSender.sendRequestBool(url, "POST", jsonPar.toJSONString());
                    if (userPurchaseReq) { // update purchase in user
                        if (eventServiceDetails.isPrimary()) {
                            int opId = eventServiceDetails.getOperationIdForCurrent(this);
                            if (opId > -1) {
                                jsonObject.put("operationId", opId);
                                sendMultiRequests(request, "POST", jsonObject.toJSONString());
                                success = true;
                            }
                        } else {
                            eventServiceDetails.incrementOperationId();
                            success = true;
                        }
                        return "";
                    } else { // if I wasn't able to update the user then roll back tickets
                        eventServiceDetails.rollBackTickets(requestEventIdInt, (int) purchasedTickets, (int) userId).equals("");
                        return "400";
                    }
                } else
                    return "400";
            } else
                return "400";
        } catch (Exception e) {
            return "400";
        } finally {
            try {
                if (!success)
                    rollBackOperationId(operId);
                eventServiceDetails.unlockEventListWriteLock(key);
                if (eventServiceDetails.isPrimary()) {
                    eventServiceDetails.unlockOperationThread();
                }
            } catch (Exception e) {
                log.debug(e);
            }
        }
    }

    /**
     * A thread safe method that adds back tickets to an event. Then on success it will pass the purchase request
     * to the secondaries otherwise it will rollback.
     *
     * @param request
     * @return empty string (success) - 400 (failure)
     */
    private String returnTickets(String request) {
        boolean success = false;
        int key = -1;
        int operId = -1;
        try {
            JSONParser parser = new JSONParser();
            JSONObject jsonObject = (JSONObject) parser.parse(jsonData);

            long jsonOperId = -1;
            if (!eventServiceDetails.isPrimary()) { // if secondary then get operation ID
                jsonOperId = (Long) jsonObject.get("operationId");
                blockThread((int) jsonOperId);
            }

            String[] reqArray = request.split("/");
            int tickets = Integer.parseInt(reqArray[1]);

            long eventId = (Long) jsonObject.get("eventid");
            long userId = (Long) jsonObject.get("userid");
            if (!eventServiceDetails.lockEventListWriteLock((int) eventId)) // lock the return of tickets on specific event
                return "400";
            key = (int) eventId;

            /* for demonstration purpose only */
            if (!eventServiceDetails.isPrimary())
                System.out.println("/return tickets excuted of id " + eventServiceDetails.getOperationId());
            else {
                operId = eventServiceDetails.getOperationIdForCurrent(this);
                System.out.println("/return tickets excuted of id " + operId);
            }
            // purchase is successfully added in the event map
            if (eventServiceDetails.rollBackTickets((int) eventId, tickets, (int) userId).equals("")) {
                if (eventServiceDetails.isPrimary()) {
                    int opId = eventServiceDetails.getOperationIdForCurrent(this);
                    if (opId > -1) {
                        jsonObject.put("operationId", opId);
                        sendMultiRequests(request, "POST", jsonObject.toJSONString());
                        success = true;
                    }
                } else {
                    eventServiceDetails.incrementOperationId();
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
                eventServiceDetails.unlockEventListWriteLock(key);
                if (eventServiceDetails.isPrimary()) {
                    eventServiceDetails.unlockOperationThread();
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
            log.debug("json is " + jsonOperId + "\n" + eventServiceDetails.getOperationId());
            while (eventServiceDetails.getOperationId() != jsonOperId) { // wait for your turn
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
        SortedMap<Integer, String> map = eventServiceDetails.getMembershipMap();
        if (map.size() > 1) { // one is the primary so everything after one will be secondary
            log.debug(url + " start sending operation to all members / size" + map.size());
            SortedMap<Integer, ThreadRequestSender> threadRequestSenderMap = new TreeMap<>();
            String myHost = eventServiceDetails.getHost();
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
                        eventServiceDetails.deleteMember(map.get(key)); // delete member
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
        if (eventServiceDetails.isPrimary())
            eventServiceDetails.decrementOperationId(id);
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
