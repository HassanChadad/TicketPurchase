package UserService;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.JSONParser;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * a class that parses all the requests sent internally between primary and all frontEnds and secondaries
 * like election requests, add member, add front end...etc
 *
 * @author Hassan Chadad
 */
public class InternalRequestParser {

    private static HeartBeatSender heartBeatSender;
    private UserServiceDetails userServiceDetails;
    private final static Logger log = LogManager.getRootLogger(); // Log Object to print to the System.out.println file

    /**
     * Constructor
     */
    public InternalRequestParser() {
        heartBeatSender = new HeartBeatSender();
        userServiceDetails = UserServiceDetails.getInstance("", "", 0, "");
    }

    /**
     * A method that checks if the method is POST or GET and sends it to the appropriate function
     * and returns the response of the function.
     *
     * @param attribute
     * @param json
     * @param clientHost
     * @return response
     */
    public String parseRequest(String[] attribute, String json, String clientHost) {
        if (attribute[0].equals("POST"))
            return callPostMethod(attribute[1], json);
        else if (attribute[0].equals("GET"))
            return callGetMethod(attribute[1], clientHost);
        else
            return "400";
    }

    /**
     * A method that handles POST requests and checks the api and calls the appropriate method and returns the response
     *
     * @param request
     * @param jsonData
     * @return response
     */
    private String callPostMethod(String request, String jsonData) {
        //log.debug("Inside Internal class POST " + request);
        if (request.equals("allLists"))
            return parseAllLists(jsonData);
        else if (request.equals("addMember"))
            return addMember(jsonData);
        else if (request.equals("newPrimary"))
            return updateNewPrimary(jsonData);
        else if (request.equals("addFrontEnd"))
            return addFrontEndHost(jsonData);
        else if (request.equals("updateUserMap"))
            return updateNewUsersMap(jsonData);
        else if (request.equals("setEventPrimary"))
            return setEventPrimary(jsonData);
        else
            return "400";
    }

    /**
     * A method that handles GET requests and checks the api and calls the appropriate method and returns the response
     *
     * @param request
     * @param clientHost
     * @return
     */
    private String callGetMethod(String request, String clientHost) {
        //log.debug("Inside Internal class GET " + request);
        if (request.equals("alive"))
            return "";
        else if (request.equals("election"))
            return handleElectionRequest();
        else if (request.equals("spreadUsers"))
            return spreadUserMap();
        else if (request.equals("newFE"))
            return addFrontEndHost(clientHost);
        else return "400";
    }

    /**
     * A method that parses jsonData and gets each array, then adds the array to the SortedMap to sort the values by key
     * Then insert the sorted values in a list to send it to the UserServiceDetails/userServiceDataStructure to update everything
     *
     * @param jsonData
     * @return response
     */
    private String parseAllLists(String jsonData) {
        try {
            JSONParser parser = new JSONParser();
            JSONObject jsonObject = (JSONObject) parser.parse(jsonData);

            SortedMap<Integer, String> membersMap = new TreeMap<>();
            SortedMap<Integer, String[]> usersDetailsMap = new TreeMap<>(); // stores users details Map
            SortedMap<Integer, SortedMap<Integer, Integer>> usersTicketsMap = new TreeMap<>(); // stores users tickets Map
            ArrayList<String> frontEndList = new ArrayList<>(); // stores frontEnd list

            /* get members */
            JSONArray arr = (JSONArray) jsonObject.get("members");
            Iterator<JSONObject> iterator = arr.iterator();
            while (iterator.hasNext()) {
                JSONObject res = iterator.next();
                long id = (Long) res.get("id");
                String host = (String) res.get("memberHost");
                membersMap.put((int) id, host);
            }

            /* get frontEnd hosts */
            arr = (JSONArray) jsonObject.get("fe");
            iterator = arr.iterator();
            while (iterator.hasNext()) {
                JSONObject res = iterator.next();
                String host = (String) res.get("frontEndHost");
                frontEndList.add(host);
            }

            /* get users details Map */
            arr = (JSONArray) jsonObject.get("users");
            iterator = arr.iterator();
            while (iterator.hasNext()) {
                JSONObject res = iterator.next();
                long id = (Long) res.get("userid");
                String userName = (String) res.get("username");
                String password = (String) res.get("password");
                String[] userParam = {userName, password};
                usersDetailsMap.put((int) id, userParam);

                SortedMap<Integer, Integer> ticketMap = new TreeMap<>();
                JSONArray arr2 = (JSONArray) res.get("tickets");
                Iterator<JSONObject> iterator2 = arr2.iterator();
                while (iterator2.hasNext()) {
                    JSONObject res2 = iterator2.next();
                    long eventId = (Long) res2.get("eventid");
                    long nbTickets = (Long) res2.get("ticketnb");
                    ticketMap.put((int)eventId, (int) nbTickets);
                }
                if(ticketMap.size() > 0)
                    usersTicketsMap.put((int) id, ticketMap);
            }

            /* get operation Id */
            int operId = 0;
            arr = (JSONArray) jsonObject.get("operation");
            iterator = arr.iterator();
            while (iterator.hasNext()) {
                JSONObject res = iterator.next();
                long id = (Long) res.get("id");
                operId = (int) id;
            }

            boolean memberResult = userServiceDetails.updateMemberList(membersMap);
            boolean frontEndResult = userServiceDetails.updateFrontEndList(frontEndList);
            boolean userResult = userServiceDetails.updateUserMap(usersDetailsMap, usersTicketsMap);
            userServiceDetails.setOperationId(operId);

            if (memberResult && frontEndResult && userResult)
                return ""; // success
            else
                return "400";
        } catch (Exception e) {
            return "400";
        }
    }

    /**
     * A method that adds new member to the primary membership list
     * after sending all the lists to the new member and received a successful response
     *
     * @param jsonData
     * @return
     */
    private String addMember(String jsonData) {
        String host = "";
        try {
            JSONParser parser = new JSONParser();
            JSONObject jsonObject = (JSONObject) parser.parse(jsonData);

            host = (String) jsonObject.get("memberHost");
            System.out.println("Adding the member");

            /* Adding member to primary member map then sending to secondaries because I want to update the list before
             * sending everything to the new secondary. If I updated the map after sending allLists to new member then
             * the map will lack the new member. So if 2 members joined, 2390 2380, then I will send member map without
             * the hosts of new members. So both members will get all members but they won't get each other's hosts.
             */
            if (!userServiceDetails.addNewMember(host))
                return "400";
            if (userServiceDetails.isPrimary()) {
                if (!sendAllLists(host + "/allLists"))
                    return "400";

                /* if a service was failed when sending introduce member, I will still be sending it in the list
                 * to the new member since it will delete it using heartbeat.
                 * I will not introduce the new member unless it receives all the lists and maps from the primary.
                 */
                if (!introduceNewMember(jsonData))
                    return "400";
            }
        } catch (Exception e) {
            return "400";
        }
        return ""; // success
    }

    /**
     * A method accessed by the primary only.
     * It gets the FElist, operation ID, user map, and member map as a json format string
     * and sends it to the new added member and returns true if the response wasa "ok"
     *
     * @param host - host of new member
     * @return true on success - false (failure)
     */
    private boolean sendAllLists(String host) {
        RequestSender requestSender = new RequestSender();
        String jsonPar = userServiceDetails.getAllLists();
        //log.debug("Send all list\n" + jsonPar);
        String result = requestSender.sendInternalRequest(host, "POST", jsonPar);
        //log.debug("did it receive all lists ? " + result);
        if (result.equals("ok"))
            return true;
        else if (result.equals("no"))
            return sendAllLists(host);
        else
            return false; // returning false means the member failed
    }

    /**
     * A method that sends multiple requests to all previous members to add the new member
     *
     * @param jsonData
     * @return true (success) - false (failure)
     */
    private boolean introduceNewMember(String jsonData) {
        try {
            return sendMultiRequests("/addMember", "POST", jsonData);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * A method that adds the frontEnd to the list.
     * If the member accessing this method is primary then the hostJson is not json format and it is a string only,
     * so the primary will send a request to all the members to the new frontEnd then it adds it to its details upon success
     * But if the member accessing this method is a secondary, then it just adds it to its details.
     *
     * @param hostJson for primary it is String host - for slave it is JSON String
     * @return empty string (success) - 400 (failure)
     */
    public String addFrontEndHost(String hostJson) {
        checkFrontEndMembers();
        if (userServiceDetails.isPrimary()) {
            JSONObject jsonPar = new JSONObject();
            jsonPar.put("frontEndHost", hostJson);
            if (sendFrontEndToEveryone(jsonPar.toJSONString())) {
                System.out.println("Added FE " + hostJson);
                userServiceDetails.addFrontEnd(hostJson); // add the frontend to list
                return "";
            }
        } else {
            try {
                JSONParser parser = new JSONParser();
                JSONObject jsonObject = (JSONObject) parser.parse(hostJson);
                String frontEndHost = (String) jsonObject.get("frontEndHost");
                System.out.println("Added FE " + frontEndHost);
                userServiceDetails.addFrontEnd(frontEndHost); // add the frontend to list
                return "";
            } catch (Exception e) {
                return "400";
            }
        }
        return "400";
    }

    /**
     * A method that sends a check request to all the Front Ends in the list to check which ones are still alive
     */
    private synchronized void checkFrontEndMembers() {
        try {
            ExecutorService threads = Executors.newCachedThreadPool();
            ArrayList<String> frontEndList = userServiceDetails.getFrontEndArrayList();
            if (frontEndList.size() > 0) { // no frontEnds yet
                SortedMap<Integer, ThreadRequestSender> threadRequestSenderMap = new TreeMap<>();
                for (int i = 0; i < frontEndList.size(); i++) {
                    ThreadRequestSender threadRequestSender = new ThreadRequestSender(frontEndList.get(i) + "/primary/checkFE", "GET", "", this, "internal");
                    threadRequestSenderMap.put(i, threadRequestSender);
                    threads.submit(threadRequestSender);

                }
                /* Start a while loop to check the responses from all the Threads created above.
                 * Iterate through the threads in threadRequestSenderMap and get the response, if one of the responses is still
                 * not retrieved keep waiting till all the responses are returned then break the loop
                 * If the response was an error, it means that the frontEnd is dead so delete it.
                 */
                while (true && threadRequestSenderMap.size() > 0) {
                    boolean responded = true;
                    for (int key : threadRequestSenderMap.keySet()) {
                        String response = threadRequestSenderMap.get(key).getResponse();
                        if (response.equals("no")) {
                            responded = false;
                            break;
                        } else if (response.equals("error")) { // member is dead
                            userServiceDetails.deleteFrontEndElement(frontEndList.get(key)); // delete member
                            System.out.println(frontEndList.get(key) + " is dead.");
                        }
                    }
                    if (responded)
                        break;
                    wait();
                }
            }
            //System.out.println("All FEnd checked.");
        } catch (Exception e) {
            log.debug(e);
        }
    }

    /**
     * A method that sends multi requests to all the members to add the new frontEnd
     * and returns the response on success
     *
     * @param jsonData
     * @return
     */
    private boolean sendFrontEndToEveryone(String jsonData) {
        try {
            return sendMultiRequests("/addFrontEnd", "POST", jsonData);
        } catch (Exception e) {
            log.debug(e);
            return false;
        }
    }

    /**
     * A method that takes the url, method, and json and creates multiples threads to send requests to members
     * and then waits for a response and returns true on success
     *
     * @param url
     * @param method
     * @param jsonData
     * @return true (success) - false (failure)
     * @throws Exception
     */
    private synchronized boolean sendMultiRequests(String url, String method, String jsonData) throws Exception {
        ExecutorService threads = Executors.newCachedThreadPool();
        SortedMap<Integer, String> map = userServiceDetails.getMembershipMap();
        if (map.size() > 1) { // one is the primary so everything after one will be secondary
            //log.debug(url + " start sending to all members / size" + map.size());
            SortedMap<Integer, ThreadRequestSender> threadRequestSenderMap = new TreeMap<>();
            String myHost = userServiceDetails.getHost();
            for (int key : map.keySet()) {
                if (url.equals("/addMember")) {
                /* skip sending to primary (myself) and last element (newely added member) */
                    if (key != map.firstKey() && key != map.lastKey()) {
                        ThreadRequestSender threadRequestSender = new ThreadRequestSender(map.get(key) + url, method, jsonData, this, "internal");
                        threadRequestSenderMap.put(key, threadRequestSender);
                        threads.submit(threadRequestSender);
                    }
                } else if (url.equals("/addFrontEnd")) {
                    /* skip sending to primary (myself in this case) since it is the one that is sending the requests */
                    if (key != map.firstKey()) {
                        ThreadRequestSender threadRequestSender = new ThreadRequestSender(map.get(key) + url, method, jsonData, this, "internal");
                        threadRequestSenderMap.put(key, threadRequestSender);
                        threads.submit(threadRequestSender);
                    }
                } else { // url is updateUserMap
                    /* skip sending to myself */
                    if (!myHost.equals(map.get(key))) {
                        ThreadRequestSender threadRequestSender = new ThreadRequestSender(map.get(key) + url, method, jsonData, this, "internal");
                        threadRequestSenderMap.put(key, threadRequestSender);
                        threads.submit(threadRequestSender);
                    }
                }
            }
            /* Start a while loop to check the responses from all the Threads created above.
             * Iterate through the threads in threadRequestSenderMap and get the response, if one of the responses is still
             * not retrieved keep waiting till all the responses are returned then break the loop
             * If the response was an error, it means that the member is dead so delete it.
             */
            while (true && threadRequestSenderMap.size() > 0) {
                boolean responded = true;
                for (int key : threadRequestSenderMap.keySet()) {
                    String response = threadRequestSenderMap.get(key).getResponse();
                    if (response.equals("no")) {
                        responded = false;
                        break;
                    } else if (response.equals("error")) { // node is dead
                        if (url.equals("/updateUserMap")) {
                            if (key == map.firstKey()) { // primary failed again
                                return false;
                            }
                        } else
                            userServiceDetails.deleteMember(map.get(key)); // delete member
                        System.out.println(map.get(key) + " is dead.");
                    }
                }
                if (responded)
                    break;
                wait();
            }
        }
        //log.debug("All Members replied to " + url);
        return true;
    }

    /**
     * A method called to start sending heartbeat, it creates a heartbeat thread
     */
    public void startHeartBeat() {
        try {
            heartBeatSender.setHeartBeat(true);
            Thread thread = new Thread(heartBeatSender);
            thread.start();
        } catch (Exception e) {
        }
    }

    /**
     * @param jsonData
     * @return
     */
    private String updateNewPrimary(String jsonData) {
        String host = "";
        try {
            JSONParser parser = new JSONParser();
            JSONObject jsonObject = (JSONObject) parser.parse(jsonData);

            host = (String) jsonObject.get("host");
            System.out.println("Updating Primary");
            if (!userServiceDetails.updatePrimary(host))
                return "400";
            else {
                JSONObject userJson = new JSONObject();
                userJson.put("id", userServiceDetails.getOperationId());
                return userJson.toJSONString(); // return userId in json format
            }
        } catch (Exception e) {
            return "400";
        }
    }

    /**
     * A method that deactivates heartbeat and calls the election class as a thread
     *
     * @return empty string (success) - false (failure)
     */
    private String handleElectionRequest() {
        try {
            heartBeatSender.setHeartBeat(false);
            Election election = Election.getInstance();
            Thread thread = new Thread(election);
            thread.start();
            return "";
        } catch (Exception e) {
            return "400";
        }
    }

    /**
     * A method that gets the users as a json format and sends the json to all the members
     * by calling sendMultiRequests and waits for the response. On success if it is not a primary it starts
     * sending heartbeat again.
     * If sending failed (method returned false) then the primary is dead so start election again.
     *
     * @return
     */
    public String spreadUserMap() {
        try {
            SortedMap<String, JSONArray> jsonMap = new TreeMap<>();
            jsonMap.put("users", userServiceDetails.getUsersJsonList());
            String jsonData = JSONValue.toJSONString(jsonMap);

            if (sendMultiRequests("/updateUserMap", "POST", jsonData)) {
                //log.debug("Done sending to everyone");
                if (!userServiceDetails.isPrimary())
                    startHeartBeat();
                return "";
            } else { // means the primary failed
                Election election = Election.getInstance();
                Thread thread = new Thread(election);
                thread.start();
                return "400";
            }
        } catch (Exception e) {
            return "400";
        }
    }

    /**
     * A method that gets jsonData and parse it to a map and then sends this map to updateUserMap method to
     * update the current user map with this new user map.
     * On success, if the member is not a primary then it starts sending heartBeat again.
     *
     * @param jsonData - contains user map parsed as json
     * @return empty string (success) - 400 (failure)
     */
    private String updateNewUsersMap(String jsonData) {
        try {
            System.out.println("Updating userMap");

            SortedMap<Integer, String[]> usersDetailsMap = new TreeMap<>(); // stores users details Map
            SortedMap<Integer, SortedMap<Integer, Integer>> usersTicketsMap = new TreeMap<>(); // stores users tickets Map

            JSONParser parser = new JSONParser();
            JSONObject jsonObject = (JSONObject) parser.parse(jsonData);
            JSONArray arr = (JSONArray) jsonObject.get("users");
            Iterator<JSONObject> iterator = arr.iterator();

            /* get users details Map */
            while (iterator.hasNext()) {
                JSONObject res = iterator.next();
                long id = (Long) res.get("userid");
                String userName = (String) res.get("username");
                String password = (String) res.get("password");
                String[] userParam = {userName, password};
                usersDetailsMap.put((int) id, userParam);

                SortedMap<Integer, Integer> ticketMap = new TreeMap<>();
                JSONArray arr2 = (JSONArray) res.get("tickets");
                Iterator<JSONObject> iterator2 = arr2.iterator();
                while (iterator2.hasNext()) {
                    JSONObject res2 = iterator2.next();
                    long eventId = (Long) res2.get("eventid");
                    long nbTickets = (Long) res2.get("ticketnb");
                    ticketMap.put((int)eventId, (int) nbTickets);
                }
                if(ticketMap.size() > 0)
                    usersTicketsMap.put((int) id, ticketMap);
            }

            //log.debug("Parsed the json and will call updateUserMap");
            boolean userResult = userServiceDetails.updateUserMap(usersDetailsMap, usersTicketsMap);

            if (userResult) {
                if (!userServiceDetails.isPrimary())
                    startHeartBeat();
                return ""; // success
            } else
                return "400";
        } catch (Exception e) {
            log.debug(e);
            return "400";
        }
    }

    /**
     * A method that parses the jsonData to get the new primary event host and updates it in user's details
     *
     * @param jsonData - contains primary event host
     * @return response (empty string on successful of update, otherwise 400 if an error occured)
     */
    private String setEventPrimary(String jsonData) {
        try {
            JSONParser parser = new JSONParser();
            JSONObject jsonObject = (JSONObject) parser.parse(jsonData);
            String host = (String) jsonObject.get("host");
            userServiceDetails.setPrimaryEventHost(host);
            return "";
        } catch (Exception e) {
            return "400";
        }
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
