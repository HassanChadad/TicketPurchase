package EventService;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;

import java.util.ArrayList;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A Singleton class that implements Runnable and handles the election process when primary fails
 *
 * @author Hassan Chadad
 */
public class Election implements Runnable {

    String myHost;
    SortedMap<Integer, String> memberMap;
    private static int running;
    private static volatile Election instance;
    private EventServiceDetails eventServiceDetails;
    private final static Logger log = LogManager.getRootLogger(); // Log Object to print to the System.out.println file

    /**
     * private constructor
     */
    private Election() {
        running = 0;
        memberMap = new TreeMap<>();
        eventServiceDetails = EventServiceDetails.getInstance("", "", 0, "");
        myHost = eventServiceDetails.getHost();
    }

    /**
     * Guarantee Singleton concept
     *
     * @return Election class instance
     */
    public static Election getInstance() {
        if (instance == null) {
            synchronized (Election.class) {
                if (instance == null)
                    instance = new Election();
            }
        }
        return instance;
    }

    /**
     * run method that creates a thread of the private class ElectionThread and waits for 5 seconds
     * to see if a new primary is elected. If yes, then it terminates the loop and finish, otherwise
     * it sends a new ElectionThread to start the election process again.
     */
    @Override
    public synchronized void run() {
        try {
            eventServiceDetails.setNewPrimaryElected(false);
            //log.debug(running);
            if (running == 0) {
                running = 1;
                while (!eventServiceDetails.isNewPrimaryElected()) {
                    System.out.println("Primary is dead! \nStart election ");
                    Thread thread = new Thread(new ElectionThread()); // this is a thread since I want to count 5 sec during sending requests
                    thread.start();
                    wait(5000); // wait to see if a new primary is elected or not
                }
                running = 0;
            }
        } catch (Exception e) {
            log.debug(e);
        }
    }

    /**
     * A private class that implements Runnable and handles the election process by sending requests to members,
     * setting new primary, and replicating data.
     */
    private class ElectionThread implements Runnable {

        /**
         * run method that gets all lower members than the current member and sends them election requests.
         * if there are no members lower than the current members it means the previous primary is alive.
         * So it will call newPrimary to send the newPrimary request to everyone.
         */
        @Override
        public void run() { // sendElectionRequests method
            try {
                memberMap = eventServiceDetails.getMemberMapLowerThanHost(myHost);
                if (memberMap.size() > 0) {
                    sendRequests();
                } else { // it means that there are no memebers to send to, so the request reached the previous primary and it is alive
                    sendNewPrimary();
                }
            } catch (Exception e) {
                log.debug("Exception at line 56 Election");
            }
        }

        /**
         * A method that iterates through the memberMap and sends to each lower member an election request.
         * this request is like sending a heartbeat to lower members to see if they are alive to start election.
         * If all the responses returned from the members were "error", it means all the members are dead and
         * the only service alive is this one. So it will be the new primary and will call newPrimary method.
         *
         * @throws Exception
         */
        private void sendRequests() throws Exception {
            ExecutorService threads = Executors.newCachedThreadPool();
            int errorCount = 0;
            //log.debug(myHost + "sending election to all lower members / size " + memberMap.size());
            System.out.println("Sending election to all lower members.");
            SortedMap<Integer, ThreadRequestSender> threadRequestSenderMap = new TreeMap<>();
            /* send election request as thread to each member and save the thread in a threadRequestSenderMap */
            for (int key : memberMap.keySet()) {
                ThreadRequestSender threadRequestSender = new ThreadRequestSender(memberMap.get(key) + "/election", "GET", "", this, "election");
                threadRequestSenderMap.put(key, threadRequestSender);
                threads.submit(threadRequestSender);
            }
            /* Start a while loop to check the responses from all the Threads created above.
            * Iterate through the threads in threadRequestSenderMap and get the response, if one of the responses is still
            * not retrieved keep waiting till all the responses are returned then break the loop.
            * If the response was an error, it means that the member is dead so increment the errorCount and delete the member.
            */
            while (true && threadRequestSenderMap.size() > 0) {
                boolean responded = true;
                errorCount = 0;
                for (int key : threadRequestSenderMap.keySet()) {
                    if (threadRequestSenderMap.get(key).getResponse().equals("no")) {
                        responded = false;
                        break;
                    } else if (threadRequestSenderMap.get(key).getResponse().equals("error")) { // member is dead
                        eventServiceDetails.deleteMember(memberMap.get(key)); // delete member
                        errorCount++;
                        log.debug(memberMap.get(key) + " is dead.");
                    }
                }
                if (responded)
                    break;
            }
            if (errorCount == threadRequestSenderMap.size()) { // it means all previous members are dead, So this service is the new Primary
                System.out.println("I am new primary");
                sendNewPrimary();
            }
            System.out.println("Election finished");
        }

        /**
         * A method called only by the service that became the new primary, it will update first all its details
         * and set the primary variables then will send requests to every secondary member with it's host to update it also.
         * Then it will wait for all the responses (operation IDs) from the members
         * then it will send a request to the member having highest operation ID to send its eventMap to everyone
         * when everyone gets the new eventmap, they will start start heartbeat again.
         * When replication is finished, this service will send newPrimary host to frontend list
         */
        private void sendNewPrimary() {
            try {
                // for demonstration (show failure of coordinator port 2370) only
                if (eventServiceDetails.getPort() != 2370) {
                    // update primary for current service then send update request
                    eventServiceDetails.updatePrimary(myHost);

                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("host", myHost);
                    String jsonData = jsonObject.toJSONString();
                    ExecutorService threads = Executors.newCachedThreadPool();
                    SortedMap<Integer, String> map = eventServiceDetails.getMembershipMap();
                    String myHost = eventServiceDetails.getHost();
                    if (map.size() > 1) { // size 1 is the primary so everything after one will be secondary
                        //log.debug(myHost + " start sending new primary to all members / size" + map.size());
                        SortedMap<Integer, ThreadRequestSender> threadRequestSenderMap = new TreeMap<>();
                        ArrayList<Integer> orderMembersByOperationIdList = new ArrayList<>(); // saves members by eventId
                        ArrayList<String> membersHostList = new ArrayList<>(); // saves members' hosts
                        /* send newPrimary request as thread to each member and save the thread in a threadRequestSenderMap */
                        for (int key : map.keySet()) {
                            if (!myHost.equals(map.get(key))) { // skip sending to myself(this service)
                                ThreadRequestSender threadRequestSender = new ThreadRequestSender(map.get(key) + "/newPrimary", "POST", jsonData, this, "election");
                                threadRequestSenderMap.put(key, threadRequestSender);
                                threads.submit(threadRequestSender);
                            }
                        }
                        /* Start a while loop to check the responses from all the Threads created above.
                         * Iterate through the threads in threadRequestSenderMap and get the response, if one of the responses is still
                         * not retrieved keep waiting till all the responses are returned then break the loop and call a method that asks
                         * the event with highest operation ID to send it's eventMap to everyone.
                         * If the response was an error, it means that the member is dead so delete the member.
                         * Upon successful, the response returned will be the operation ID.
                         */
                        while (true && threadRequestSenderMap.size() > 0) {
                            boolean responded = true;
                            orderMembersByOperationIdList.clear();
                            membersHostList.clear();
                            orderMembersByOperationIdList.add(eventServiceDetails.getOperationId()); // add my operation
                            membersHostList.add(myHost); // add my host
                            for (int key : threadRequestSenderMap.keySet()) {
                                String response = threadRequestSenderMap.get(key).getResponse();
                                if (response.equals("no")) {
                                    responded = false;
                                    break;
                                } else if (response.equals("error")) { // node is dead
                                    eventServiceDetails.deleteMember(map.get(key)); // delete member
                                    log.debug(map.get(key) + " is dead so I can't send new primary to it and I will delete it");
                                } else {
                                    try {
                                        int respondedOperationId = Integer.parseInt(response);
                                        /* sort the orderMembersByOperationIdList by operation IDs in descending order */
                                        int insertionIndex = -1;
                                        for (int i = 0; i < orderMembersByOperationIdList.size(); i++) {
                                            if (respondedOperationId > orderMembersByOperationIdList.get(i)) {
                                                insertionIndex = i;
                                                break;
                                            }
                                        }
                                        /* order the operationId responses in descending order */
                                        if (insertionIndex == -1) { // operationId is less than all elements in the list, so insert at the end
                                            orderMembersByOperationIdList.add(respondedOperationId);
                                            membersHostList.add(map.get(key));
                                        } else { // if greater or equal put it at the index of the element compared to
                                            orderMembersByOperationIdList.add(insertionIndex, respondedOperationId);
                                            membersHostList.add(insertionIndex, map.get(key));
                                        }
                                    } catch (Exception e) {
                                        log.debug("Host returned wrong data so it will not be added to the lists");
                                    }
                                }
                            }
                            if (responded)
                                break;
                        }
                        askHighestHostToSendEventMap(orderMembersByOperationIdList, membersHostList);
                    } else
                        sendNewPrimaryToFE(); // send new primary to frontend list
                    System.out.println("All Members replied to newPrimary request");
                }
            } catch (Exception e) {
            }
        }

        /**
         * Method accessed by primary only that asks the member with the highest operation ID to send its eventMap to every other
         * member. If by any change this member died, then the method will ask the member having the second highest operation ID
         * to send its eventMap and so on till reaching the last element in the memberEventIdList. If the memberEventIdList became empty
         * then all the members are dead.
         *
         * @param memberEventIdList
         * @param memberHost
         * @throws Exception
         */
        private void askHighestHostToSendEventMap(ArrayList<Integer> memberEventIdList, ArrayList<String> memberHost) throws Exception {
            RequestSender requestSender = new RequestSender();
            String result;
            for (int i = 0; i < memberEventIdList.size(); i++) {
                result = "no";
                while (result.equals("no")) {
                    if (!myHost.equals(memberHost.get(i))) { // if secondary, then send request to it
                        System.out.println("Asking " + memberHost.get(i) + " to spread the eventMap");
                        result = requestSender.sendInternalRequest(memberHost.get(i) + "/spreadEvents", "GET", "");
                    } else { // if primary then spread event immediately
                        System.out.println("I will spread the eventMap");
                        InternalRequestParser internalRequestParser = new InternalRequestParser();
                        String response = internalRequestParser.spreadEventMap();
                        if (response.equals("")) {
                            result = "ok";
                        } else
                            result = "no";
                    }
                }
                if (result.equals("ok"))
                    break;
                // if error returned then skip the host and got to the second one
            }
            sendNewPrimaryToFE(); // send new primary to frontend list
            // this case will be reached with result = no if all the members weren't reached which means all are dead including current
        }

        /**
         * A method called by the new primary only that parses the new primary's host in json and sends it to all the frontEnd services
         * and wait for a response.
         * If the FrontEnd list is empty then skip sending requests.
         */
        private void sendNewPrimaryToFE() {
            try {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("host", myHost);
                String jsonData = jsonObject.toJSONString();
                ExecutorService threads = Executors.newCachedThreadPool();
                ArrayList<String> frontEndList = eventServiceDetails.getFrontEndArrayList();
                if (frontEndList.size() > 0) { // if size = 0 then no frontEnds yet so skip sending
                    SortedMap<Integer, ThreadRequestSender> threadRequestSenderMap = new TreeMap<>();
                    /* send new Primary host request as thread to each frontEnd and save the thread in a threadRequestSenderMap */
                    for (int i = 0; i < frontEndList.size(); i++) {
                        ThreadRequestSender threadRequestSender = new ThreadRequestSender(frontEndList.get(i) + "/primary/newEventPrimary", "POST", jsonData, this, "election");
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
                            } else if (response.equals("error")) { // node is dead
                                eventServiceDetails.deleteFrontEndElement(frontEndList.get(key)); // delete member
                                System.out.println(frontEndList.get(key) + " is dead.");
                            }
                        }
                        if (responded)
                            break;
                    }
                }
                System.out.println("All FEnd updated primary.");
            } catch (Exception e) {
                log.debug(e);
            }
        }
    }
}