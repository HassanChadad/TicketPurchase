package EventService;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import java.util.ArrayList;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A singleton class that contains all the details about an event member
 *
 * @author Hassan Chadad
 */
public class EventServiceDetails {

    private static volatile EventServiceDetails instance;

    private boolean isPrimary; // when service is primary this is true
    private boolean newPrimaryElected; // variable to check if new primary elected
    private int port; // current service port
    private int eventId; // event id auto incremented
    private int memberId; // auto incremented
    private int operationId; // this is timestamp (operation ID)
    private String host, primaryUserHost; // current service host
    private String primaryEventHost;
    private SortedMap<Integer, String> membershipMap; // map storing all members with their IDs.
    private ArrayList<String> frontEndList; // list of front ends
    private SortedMap<Integer, ClientRequestParser> operationMap; // map to handle multiple operation threads
    private SortedMap<Integer, String[]> eventMap; // save created events' info in a map
    private SortedMap<Integer, SortedMap<Integer, Integer>> userTicketMap; // save users' tickets in a map <userid, <eventid, nb of Tickets>>
    private ReentrantReadWriteLock readWriteLockMember; // to ensure thread safety on member map
    private ReentrantReadWriteLock readWriteLockFE; // to ensure thread safety on Front end list
    private ReentrantReadWriteLock readWriteLockEventMap; // to ensure thread safety on event map
    private SortedMap<Integer, ReentrantReadWriteLock> eventMapLocks; // to ensure thread safety on different level of accessing event map
    private ReentrantReadWriteLock readWriteLockOperation; // to ensure thread safety on operation
    private ReentrantReadWriteLock readWriteLockOperationThread; // to ensure thread safety on executing operation threads
    private final static Logger log = LogManager.getRootLogger(); // Log Object to print to the file

    /**
     * Private Constructor
     *
     * @param host
     * @param primaryEventHost
     * @param port
     */
    private EventServiceDetails(String host, String primaryEventHost, int port, String primaryUserHost) {
        this.port = port;
        this.host = host;
        this.primaryEventHost = primaryEventHost;
        this.primaryUserHost = primaryUserHost;
        isPrimary = false;
        newPrimaryElected = true;
        memberId = 0;
        operationId = 0;
        eventId = 0;
        operationMap = new TreeMap<>();
        membershipMap = new TreeMap<>();
        frontEndList = new ArrayList<>();
        eventMap = new TreeMap<>();
        userTicketMap = new TreeMap<>();
        eventMapLocks = new TreeMap<>();
        readWriteLockMember = new ReentrantReadWriteLock();
        readWriteLockFE = new ReentrantReadWriteLock();
        readWriteLockOperation = new ReentrantReadWriteLock();
        readWriteLockEventMap = new ReentrantReadWriteLock();
        readWriteLockOperationThread = new ReentrantReadWriteLock();
    }

    /**
     * A method that guarantees singleton mechanism
     *
     * @param host
     * @param primaryEventHost
     * @param port
     * @return
     */
    public static EventServiceDetails getInstance(String host, String primaryEventHost, int port, String primaryUserHost) {
        if (instance == null) {
            synchronized (EventServiceDetails.class) {
                if (instance == null)
                    instance = new EventServiceDetails(host, primaryEventHost, port, primaryUserHost);
            }
        }
        return instance;
    }

    /**
     * Set method
     *
     * @param status for isPrimary
     */
    public void setPrimary(boolean status) {
        isPrimary = status;
    }

    /**
     * Get method
     *
     * @return isPrimary
     */
    public boolean isPrimary() {
        return isPrimary;
    }

    /**
     * Get method
     *
     * @return port
     */
    public int getPort() {
        return port;
    }

    /**
     * Get method
     *
     * @return host
     */
    public String getHost() {
        return host;
    }

    /**
     * Get method
     *
     * @return primaryEvent Host
     */
    public String getPrimaryUserHost() {
        return primaryUserHost;
    }

    /**
     * Set method
     *
     * @param primaryUserHost
     */
    public void setPrimaryUserHost(String primaryUserHost) {
        this.primaryUserHost = primaryUserHost;
    }

    /**
     * A thread safe Get method
     *
     * @return newPrimaryElected
     */
    public synchronized boolean isNewPrimaryElected() {
        return newPrimaryElected;
    }

    /**
     * A thread safe Set method for newPrimaryElected
     *
     * @param status
     */
    public synchronized void setNewPrimaryElected(boolean status) {
        newPrimaryElected = status;
    }

    /* Operation Code */

    /**
     * A thread safe method that increments the operation ID and wakes the thread waiting on the new
     * incremented ID
     */
    public void incrementOperationId() {
        try {
            readWriteLockOperation.writeLock().lock();
            operationId++;
            if (operationMap.get(operationId) != null)
                operationMap.get(operationId).wakeThread(); // throws error if ID > size
        } catch (Exception e) {
            log.debug(e);
        } finally {
            readWriteLockOperation.writeLock().unlock();
        }
    }

    /**
     * A thread safe method that decrements operation ID if operation failed
     */
    public void decrementOperationId(int opId) {
        readWriteLockOperation.writeLock().lock();
        operationMap.remove(opId);
        System.out.println("Deleting operation " + opId); // for demonstration
        operationId--;
        if (operationId < 0)
            operationId = 0;
        readWriteLockOperation.writeLock().unlock();
    }

    /**
     * A thread safe Set method for operation ID
     *
     * @param id
     */
    public void setOperationId(int id) {
        readWriteLockOperation.writeLock().lock();
        operationId = id;
        System.out.println("OperationId is reset");
        readWriteLockOperation.writeLock().unlock();
    }

    /**
     * A thread safe Get method
     *
     * @return operation ID
     */
    public int getOperationId() {
        try {
            readWriteLockOperation.readLock().lock();
            return operationId;
        } catch (Exception e) {
            log.debug(e);
            return -1;
        } finally {
            readWriteLockOperation.readLock().unlock();
        }
    }

    /**
     * A thread safe method that parses the last operation ID in a JSONArray and return it
     *
     * @return JSONArray
     */
    private JSONArray getOperationIdJson() {
        try {
            readWriteLockOperation.readLock().lock();
            JSONArray jsonArray = new JSONArray();
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("id", operationId);
            jsonArray.add(jsonObject); // add json object to json list
            return jsonArray;
        } catch (Exception e) {
            log.debug(e);
            return null;
        } finally {
            readWriteLockOperation.readLock().unlock();
        }
    }

    /**
     * A thread safe method that checks the clientRequestParser object in operationMap
     * and returns its key (operation ID) on match
     *
     * @param clientRequestParser
     * @return operation ID of specific clientRequestParser object
     */
    public int getOperationIdForCurrent(ClientRequestParser clientRequestParser) {
        try {
            readWriteLockOperation.readLock().lock();
            for (int id : operationMap.keySet()) {
                if (clientRequestParser.equals(operationMap.get(id)))
                    return id;
            }
            return -1;
        } catch (Exception e) {
            log.debug(e);
            return -1;
        } finally {
            readWriteLockOperation.readLock().unlock();
        }
    }

    /**
     * A thread safe method that adds the clientRequestParser object to the operationMap
     * with the current operation ID as a key. Then it increments the operation ID.
     *
     * @param clientRequestParser
     */
    public void addOperationThread(ClientRequestParser clientRequestParser) {
        try {
            readWriteLockOperation.writeLock().lock();
            operationMap.put(operationId, clientRequestParser);
            operationId++;
        } catch (Exception e) {
            log.debug(e);
        } finally {
            readWriteLockOperation.writeLock().unlock();
        }
    }

    /**
     * A method that locks the operationThread lock for primary user
     */
    public void lockOperationThread() {
        try {
            readWriteLockOperationThread.writeLock().lock();
        } catch (Exception e) {
            log.debug(e);
        }
    }

    /**
     * A method that unlocks the operationThread lock for primary user
     */
    public void unlockOperationThread() {
        try {
            readWriteLockOperationThread.writeLock().unlock();
        } catch (Exception e) {
            log.debug(e);
        }
    }

    /* End of Operation Code */

    /* Event Code */

    /**
     * A method that calls getEventsJsonList and return the json array string format
     *
     * @return json array string format
     */
    public String getEventList() {
        try {
            JSONArray jsonArray = getEventsJsonList(false);
            return jsonArray.toJSONString(); // return json list as string
        } catch (Exception e) {
            return "400";
        }
    }

    /**
     * A thread safe method that gets a specific event from event map and parse it in
     * json object and returns the json string
     *
     * @param reqEventId
     * @return json string format
     */
    public String getEvent(int reqEventId) {
        try {
            readWriteLockEventMap.readLock().lock();
            lockEventListReadLock(reqEventId);
            JSONObject jsonObject = new JSONObject();
            if (eventMap.get(reqEventId) != null) {
                String eventName = eventMap.get(reqEventId)[0];
                String userId = eventMap.get(reqEventId)[1];
                String availTickets = eventMap.get(reqEventId)[2];
                String purchTickets = eventMap.get(reqEventId)[3];
                // create json object
                jsonObject.put("eventid", reqEventId);
                jsonObject.put("eventname", eventName);
                jsonObject.put("userid", Integer.parseInt(userId));
                jsonObject.put("avail", Integer.parseInt(availTickets));
                jsonObject.put("purchased", Integer.parseInt(purchTickets));
                return jsonObject.toJSONString(); // return json object as string
            } else
                return "400";
        } catch (Exception e) {
            return "400";
        } finally {
            eventMapLocks.get(reqEventId).readLock().unlock();
            readWriteLockEventMap.readLock().unlock();
        }
    }

    /**
     * A thread safe method that gets all the events from Event Map and parse them in json array
     * and return the json array
     *
     * @return json array string format
     */
    public JSONArray getEventsJsonList(boolean withTickets) {
        JSONArray jsonArray = new JSONArray();
        try {
            readWriteLockEventMap.readLock().lock();
            for (int key : eventMapLocks.keySet()) // lock all readlocks in eventMapLock
                eventMapLocks.get(key).readLock().lock();
            for (int key : eventMap.keySet()) { // iterate through event map
                String eventName = eventMap.get(key)[0];
                String userId = eventMap.get(key)[1];
                String availTickets = eventMap.get(key)[2];
                String purchTickets = eventMap.get(key)[3];
                // create json object
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("eventid", key);
                jsonObject.put("avail", Integer.parseInt(availTickets));
                jsonObject.put("purchased", Integer.parseInt(purchTickets));
                jsonObject.put("userid", Integer.parseInt(userId));
                jsonObject.put("eventname", eventName);
                if (withTickets) {
                    SortedMap<Integer, Integer> userEventTickets = userTicketMap.get(key);
                    JSONArray userTicketJsonArray = new JSONArray();
                    if (userEventTickets != null) {
                        for (int event : userEventTickets.keySet()) {
                            JSONObject eventJsonObject = new JSONObject();
                            eventJsonObject.put("eventid", event);
                            eventJsonObject.put("ticketnb", userEventTickets.get(event));
                            userTicketJsonArray.add(eventJsonObject);
                        }
                    }
                    jsonObject.put("tickets", userTicketJsonArray);
                }
                jsonArray.add(jsonObject); // add json object to json list
            }
            return jsonArray; // return json list as string
        } catch (Exception e) {
            return jsonArray;
        } finally {
            for (int key : eventMapLocks.keySet()) // unlock all readlocks in eventListLock
                eventMapLocks.get(key).readLock().unlock();
            readWriteLockEventMap.readLock().unlock();
        }
    }

    /**
     * A thread safe method that search for events using Lucene and return a map containing matched events
     *
     * @return json array string format
     */
    public String searchEvents(String keywords, int avail) {
        JSONArray jsonArray = new JSONArray();
        try {
            readWriteLockEventMap.readLock().lock();
            for (int key : eventMapLocks.keySet()) // lock all readlocks in eventMapLock
                eventMapLocks.get(key).readLock().lock();

            if (eventMap.size() > 0) {
                SortedMap<Integer, String[]> map = new TreeMap<>();
                for (int key : eventMap.keySet()) { // create a copy of eventMap
                    String[] value = new String[4];
                    value[0] = eventMap.get(key)[0];
                    value[1] = eventMap.get(key)[1];
                    value[2] = eventMap.get(key)[2];
                    value[3] = eventMap.get(key)[3];
                    map.put(key, value);
                }

                LuceneSearch luceneSearch = LuceneSearch.getInstance();
                SortedMap<Integer, String[]> searchedEventMap = luceneSearch.search(map, keywords, avail, map.size() + 10);

                for (int key : searchedEventMap.keySet()) { // iterate through event map
                    String eventName = searchedEventMap.get(key)[0];
                    String userId = searchedEventMap.get(key)[1];
                    String availTickets = searchedEventMap.get(key)[2];
                    String purchTickets = searchedEventMap.get(key)[3];
                    // create json object
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("eventid", key);
                    jsonObject.put("avail", Integer.parseInt(availTickets));
                    jsonObject.put("purchased", Integer.parseInt(purchTickets));
                    jsonObject.put("userid", Integer.parseInt(userId));
                    jsonObject.put("eventname", eventName);
                    jsonArray.add(jsonObject); // add json object to json list
                }
            }
            return jsonArray.toJSONString(); // return json list as string
        } catch (Exception e) {
            return jsonArray.toJSONString();
        } finally {
            for (int key : eventMapLocks.keySet()) // unlock all readlocks in eventListLock
                eventMapLocks.get(key).readLock().unlock();
            readWriteLockEventMap.readLock().unlock();
        }
    }

    /**
     * A method that creates a ReentrantReadWriteLock for an event and adds it to eventMapLocks if it doesn't exist
     * and locks the read lock.
     *
     * @param key - event ID
     */
    private void lockEventListReadLock(int key) {
        if (eventMapLocks.get(key) != null)
            eventMapLocks.get(key).readLock().lock();
        else {
            ReentrantReadWriteLock readWriteLock1 = new ReentrantReadWriteLock();
            readWriteLock1.readLock().lock();
            eventMapLocks.put(key, readWriteLock1);
        }
    }

    /**
     * A thread safe method that creates an event and adds it to the eventMap.
     * On success, it returns json string format of the new created event ID
     *
     * @param eventName
     * @param userId
     * @param numTickets
     * @return json string of event ID
     */
    public String createEvent(String eventName, String userId, String numTickets) {
        try {
            JSONObject responseJson = new JSONObject();
            for (int key : eventMap.keySet()) {
                if (eventMap.get(key)[0].equals(eventName) && eventMap.get(key)[1].equals(userId)) // check if same event created by same user
                    return "400";
            }
            eventId++; // increment event id

            /* eventParam are {event name, creator id, total tickets, purchased tickets} */
            String[] eventParam = {eventName, userId + "", numTickets + "", "0"};

            eventMap.put(eventId, eventParam); // create event and add it to the map
            responseJson.put("eventid", eventId); // create json response
            return responseJson.toJSONString();
        } catch (Exception e) {
            return "400";
        }
    }

    /**
     * A thread safe method that takes event id and user id then deletes an event with the eventid
     * from the eventMap. It also checks if the user sent is the same as the creator.
     *
     * @param userId
     * @param eventId
     * @return empty string (success) - 400 (failure)
     */
    public SortedMap<Integer, String[]> deleteEvent(int eventId, String userId) {
        try {
            SortedMap<Integer, String[]> backupEventMap = new TreeMap<>();
            JSONObject responseJson = new JSONObject();
            if (eventMap.get(eventId) == null) // check if event exists in eventMap
                return null;
            if (!eventMap.get(eventId)[1].equals(userId)) // check if userid is the creator id
                return null;
            for (int key : eventMap.keySet()) { // create a backup of eventMap
                String[] value = new String[4];
                value[0] = eventMap.get(key)[0];
                value[1] = eventMap.get(key)[1];
                value[2] = eventMap.get(key)[2];
                value[3] = eventMap.get(key)[3];
                backupEventMap.put(key, value);
            }
            eventMap.remove(eventId);
            return backupEventMap;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * A thread safe method that restores the backup event Map to the initial eventMap
     *
     * @return empty string (success) - 400 (failure)
     */
    public String restoreBackupEvent(SortedMap<Integer, String[]> backupEventMap) {
        try {
            for (int key : backupEventMap.keySet()) {
                String[] value = new String[4];
                value[0] = backupEventMap.get(key)[0];
                value[1] = backupEventMap.get(key)[1];
                value[2] = backupEventMap.get(key)[2];
                value[3] = backupEventMap.get(key)[3];
                eventMap.put(key, value);
            }
            return "";
        } catch (Exception e) {
            return "400";
        }
    }

    /**
     * A thread safe method that update an event.
     * On success, it returns json string format of the new created event ID
     *
     * @param eventName
     * @param userId
     * @param additionalTickets
     * @return json string of event ID
     */
    public String updateEvent(int eventId, String eventName, String userId, int additionalTickets) {
        try {
            if (eventMap.get(eventId) == null) // event doesn't exist
                return "400";
            if (!eventMap.get(eventId)[1].equals(userId)) // check if the user sent is the creator, if not return 400
                return "400";

            String[] eventParam = eventMap.get(eventId);
            if (eventName.length() > 0) { // it means the client sent a new event name
                for (int id : eventMap.keySet()) { // check if the user created a different event with the same name
                    if (eventMap.get(id)[1].equals(userId) && eventMap.get(id)[0].equals(eventName) && id != eventId)
                        return "400";
                }
                eventParam[0] = eventName;
            }
            if (additionalTickets > 0) { // it means the client sent additional tickets
                int tickets = Integer.parseInt(eventParam[2]);
                tickets += additionalTickets;
                eventParam[2] = tickets + "";
            }
            eventMap.put(eventId, eventParam); // update event's parameter

            return "";
        } catch (Exception e) {
            return "400";
        }
    }

    /**
     * A method that locks the write lock of readWriteLockEventMap
     */
    public void lockWriteLock() {
        readWriteLockEventMap.writeLock().lock();
    }

    /**
     * A method that unlocks the write lock of readWriteLockEventMap
     */
    public void unlockWriteLock() {
        readWriteLockEventMap.writeLock().unlock();
    }

    /**
     * A thread safe method that updates the tickets in a specific event
     *
     * @param eventId
     * @param purchasedTickets
     * @return empty string on success or 400 if failed
     */
    public String purchaseTickets(int eventId, int purchasedTickets, int userId) {
        int availableTickets = Integer.parseInt(eventMap.get(eventId)[2]);

        if (purchasedTickets > availableTickets || purchasedTickets < 1) // purchased tickets = 0 or greater than available
            return "400";

        availableTickets -= purchasedTickets;
        purchasedTickets += Integer.parseInt(eventMap.get(eventId)[3]);
        String eventName = eventMap.get(eventId)[0];
        String creatorId = eventMap.get(eventId)[1];
        String[] eventParam = {eventName, creatorId, availableTickets + "", purchasedTickets + ""};
        eventMap.replace(eventId, eventParam);

        SortedMap<Integer, Integer> ticketsMap = userTicketMap.get(userId);
        if (ticketsMap == null) { // check if user's ticket map is empty ( user didn't purchase before)
            ticketsMap = new TreeMap<>();
            ticketsMap.put(eventId, purchasedTickets);
        } else {
            if (ticketsMap.get(eventId) != null) { // check if the eventId for new tickets exists so I will update the entry and not put
                int allTickets = ticketsMap.get(eventId) + purchasedTickets;
                ticketsMap.put(eventId, allTickets);
            } else
                ticketsMap.put(eventId, purchasedTickets);
        }
        userTicketMap.put(userId, ticketsMap);
        return "";
    }

    /**
     * A thread safe method that resets the tickets to their initial value before calling
     * purchaseTickets method in a specific event.
     *
     * @param id - event id
     * @param purchasedTickets
     * @return empty string on success
     */
    public String rollBackTickets(int id, int purchasedTickets, int userId) {
        if(userTicketMap.get(userId).get(id) != null) {
            int availableTickets = Integer.parseInt(eventMap.get(id)[2]);
            availableTickets += purchasedTickets;
            purchasedTickets = Integer.parseInt(eventMap.get(id)[3]) - purchasedTickets;
            String eventName = eventMap.get(id)[0];
            String creatorId = eventMap.get(id)[1];
            String[] eventParam = {eventName, creatorId, availableTickets + "", purchasedTickets + ""};
            eventMap.replace(id, eventParam);
            if(userTicketMap.get(userId) != null) {
                if(userTicketMap.get(userId).get(id) != null)
                    userTicketMap.get(userId).remove(id);
            }
        }
        return "";
    }

    /**
     * A method that checks if the event exists in the event map then that waits till readWriteLockEventMap
     * write lock is not held by another thread. Then it creates a ReentrantReadWriteLock for a specific event
     * and locks its write lock and adds it to the eventMapLocks if it doesn't exist.
     *
     * @param key
     * @return true if event exists otherwise false
     */
    public boolean lockEventListWriteLock(int key) {
        if (eventMap.get(key) != null) {
            while (readWriteLockEventMap.writeLock().isHeldByCurrentThread()) {
            } // wait till writelock is unlocked
            if (eventMapLocks.get(key) != null)
                eventMapLocks.get(key).writeLock().lock();
            else {
                ReentrantReadWriteLock readWriteLock1 = new ReentrantReadWriteLock();
                readWriteLock1.writeLock().lock();
                eventMapLocks.put(key, readWriteLock1);
            }
            return true;
        } else
            return false;
    }

    /**
     * A method that unlocks write lock of specific event in eventMapLocks
     *
     * @param key - event ID
     */
    public void unlockEventListWriteLock(int key) {
        if (eventMapLocks.get(key) != null)
            eventMapLocks.get(key).writeLock().unlock();
    }

    /**
     * A thread safe method that updates the current event Map with the map sent to it
     * and assigns the eventID as last key if map size > 0 or 0 if the map is empty
     *
     * @param map
     * @return true (success) - false (failure)
     */
    public boolean updateEventMap(SortedMap<Integer, String[]> map,
                                  SortedMap<Integer,SortedMap<Integer, Integer>> ticketMap) {
        try {
            readWriteLockEventMap.writeLock().lock();
            eventMap.clear();
            eventMap.putAll(map);
            if (eventMap.size() > 0)
                eventId = eventMap.lastKey();
            else
                eventId = 0;
            userTicketMap.clear();
            userTicketMap.putAll(ticketMap);
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            readWriteLockEventMap.writeLock().unlock();
        }
    }

    /* End of Event Code */

    /* Member Code */

    /**
     * A thread safe method that checks if the given host exists in the member map
     * if it doesn't exist then add the host with member ID as a key to member map.
     * Otherwise, it means it existed before so it failed and joined back. So get the key of the existed host
     * then remove it from the map and add it at the end.
     * Then increment the memberID.
     *
     * @param host
     * @return true (success) - false (failure)
     */
    public boolean addNewMember(String host) {
        try {
            readWriteLockMember.writeLock().lock();
            // check if the host already exists in the map
            boolean exists = false;
            for (int key : membershipMap.keySet()) {
                if (membershipMap.get(key).equals(host)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                membershipMap.put(memberId, host);
                memberId++;
            } else { // it existed before but failed and returned back
                int index = -1;
                for (int key : membershipMap.keySet()) {
                    if (membershipMap.get(key).equals(host)) {
                        index = key;
                        break;
                    }
                }
                removeMember(index);
                membershipMap.put(memberId, host);
                memberId++;
            }
            /* for demonstration */
            System.out.println("#######################");
            for (int key : membershipMap.keySet())
                System.out.println(membershipMap.get(key));
            System.out.println("#######################");
            /* ------------------ */
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            readWriteLockMember.writeLock().unlock();
        }
    }

    /**
     * A thread safe method that returns a map of membership Map
     *
     * @return membership map
     */
    public SortedMap<Integer, String> getMembershipMap() {
        try {
            readWriteLockMember.readLock().lock();
            SortedMap<Integer, String> temp = new TreeMap<>();
            for (int key : membershipMap.keySet()) {
                String host = membershipMap.get(key);
                temp.put(key, host);
            }
            return temp;
        } catch (Exception e) {
            return null;
        } finally {
            readWriteLockMember.readLock().unlock();
        }
    }

    /**
     * A thread safe method that saves all the members before a specific host in a map
     * and return it.
     *
     * @param host
     * @return map
     */
    public SortedMap<Integer, String> getMemberMapLowerThanHost(String host) {
        try {
            readWriteLockMember.readLock().lock();
            SortedMap<Integer, String> temp = new TreeMap<>();
            for (int key : membershipMap.keySet()) {
                if (membershipMap.get(key).equals(host)) // when I reach the sent host it means I added all previous elements
                    break;
                String memberHost = membershipMap.get(key);
                temp.put(key, memberHost);
            }
            return temp;
        } catch (Exception e) {
            return null;
        } finally {
            readWriteLockMember.readLock().unlock();
        }
    }

    /**
     * A thread safe method that gets all members in memberMap and parse them in
     * jsonArray and return it.
     *
     * @return memberJsonArray
     */
    private JSONArray getMemberMapJSON() {
        JSONArray membershipJsonArray = new JSONArray();
        try {
            readWriteLockMember.readLock().lock();
            for (int key : membershipMap.keySet()) { // iterate through memberList
                // create json object
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("id", key);
                jsonObject.put("memberHost", membershipMap.get(key));
                membershipJsonArray.add(jsonObject); // add json object to json list
            }
            return membershipJsonArray;
        } catch (Exception e) {
            return membershipJsonArray;
        } finally {
            readWriteLockMember.readLock().unlock();
        }
    }

    /**
     * A thread safe method that updates the current memberMap with the send map
     *
     * @param map
     * @return true : success - false : failure
     */
    public boolean updateMemberList(SortedMap<Integer, String> map) {
        try {
            readWriteLockMember.writeLock().lock();
            membershipMap.clear();
            membershipMap.putAll(map);
            memberId = membershipMap.lastKey() + 1;
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            readWriteLockMember.writeLock().unlock();
        }
    }

    /**
     * A method that removes a member if it exists in the memberMap
     * then it re-orders all the members in the map to make the member ID in sequence
     * Ex: previous memberMap after remving memberID 2 will be 1,3,4,5,6
     * so after re-ordering it will be 1,2,3,4,5
     *
     * @param id member ID
     * @throws Exception
     */
    private void removeMember(int id) throws Exception {
        if (membershipMap.get(id) != null) {
            membershipMap.remove(id);

            /* organize memberIds again in sequential order */
            ArrayList<String> members = new ArrayList<>();
            for (int key : membershipMap.keySet()) // copy all members from map to arrayList
                members.add(membershipMap.get(key));
            membershipMap.clear(); // clear member map
            for (int i = 0; i < members.size(); i++) { // copy all members from list to map
                membershipMap.put(i, members.get(i));
            }
            memberId = membershipMap.lastKey() + 1;
        }
    }

    /**
     * A thread safe method that locks the write lock for member and calls removeMember
     *
     * @param host
     */
    public void deleteMember(String host) {
        try {
            readWriteLockMember.writeLock().lock();
            int index = -1;
            for (int id : membershipMap.keySet()) {
                if (host.equals(membershipMap.get(id))) {
                    index = id;
                    break;
                }
            }
            if (index != -1)
                removeMember(index);
        } catch (Exception e) {
        } finally {
            readWriteLockMember.writeLock().unlock();
        }
    }

    /**
     * A thread safe method that gets the new primary host then checks if the host exists.
     * if the primary exists, then remove all members in memberMap that are before the primary host since this host is elected
     * means that all previous members didn't reply (dead)
     * Otherwise, add the primary as the first member in the memberMap.
     * Finally, updates all primary variables.
     *
     * @param newPrimaryHost
     * @return true (success) - false (failure)
     */
    public boolean updatePrimary(String newPrimaryHost) {
        try {
            readWriteLockMember.writeLock().lock();
            int found = -1;
            SortedMap<Integer, String> temp = new TreeMap<>();
            for (int key : membershipMap.keySet())
                temp.put(key, membershipMap.get(key));
            for (int key : temp.keySet()) {
                if (temp.get(key).equals(newPrimaryHost)) {
                    found = key;
                    break;
                }
            }
            if (found != -1) {
                // since primary now is elected then remove all previous nodes
                for (int key : temp.keySet()) {
                    if (key == found)
                        break;
                    membershipMap.remove(key);
                }
            } else {
                ArrayList<String> members = new ArrayList<>();
                for (int key : temp.keySet()) // copy all members from map to list
                    members.add(temp.get(key));
                members.add(0, newPrimaryHost); // add new primary at the beginning of the list
                membershipMap.clear(); // clear map
                for (int i = 0; i < members.size(); i++) // copy all members from list to map
                    membershipMap.put(i, members.get(i));
                memberId = membershipMap.lastKey() + 1;
            }
            synchronized (this) {
                if (newPrimaryHost.equals(host))
                    isPrimary = true;
                newPrimaryElected = true;
                primaryEventHost = newPrimaryHost;
            }
            return true;
        } catch (Exception e) {
            log.debug(e);
            return false;
        } finally {
            readWriteLockMember.writeLock().unlock();
        }
    }

    /* End of Member Code */

    /* FrontEnd Code */

    /**
     * A thread safe method that adds the host to the frontend list if it doesn't exist in the list
     *
     * @param host - new FE host
     */
    public void addFrontEnd(String host) {
        readWriteLockFE.writeLock().lock();
        boolean exists = false;
        for (String h : frontEndList) {
            if (h.equals(host)) {
                exists = true;
                break;
            }
        }
        if (!exists) {
            frontEndList.add(host);
        }
        readWriteLockFE.writeLock().unlock();
    }

    /**
     * A thread safe method that updates the frontEnd list with the given list
     *
     * @param list
     * @return true (success) - false (failure)
     */
    public boolean updateFrontEndList(ArrayList<String> list) {
        try {
            readWriteLockFE.writeLock().lock();
            frontEndList.clear();
            frontEndList = list;
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            readWriteLockFE.writeLock().unlock();
        }
    }

    /**
     * A thread safe method that gets all FE hosts from the list and parses them in
     * JsonArray and returns it
     *
     * @return jsonArray
     */
    private JSONArray getFrontEndJsonArray() {
        JSONArray frontEndJsonArray = new JSONArray();
        try {
            readWriteLockFE.readLock().lock();
            for (int i = 0; i < frontEndList.size(); i++) { // iterate through memberList
                // create json object
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("frontEndHost", frontEndList.get(i));
                frontEndJsonArray.add(jsonObject); // add json object to json list
            }
            return frontEndJsonArray;
        } catch (Exception e) {
            return frontEndJsonArray;
        } finally {
            readWriteLockFE.readLock().unlock();
        }
    }

    /**
     * A thread safe method that return a FE list
     *
     * @return FE list
     */
    public ArrayList<String> getFrontEndArrayList() {
        ArrayList<String> list = new ArrayList<>();
        try {
            readWriteLockFE.readLock().lock();
            for (String member : frontEndList)
                list.add(member);
            return list;
        } catch (Exception e) {
            return list;
        } finally {
            readWriteLockFE.readLock().unlock();
        }
    }

    /**
     * A thread safe method that deletes a FE element by its index from the list
     *
     * @param host
     */
    public void deleteFrontEndElement(String host) {
        try {
            readWriteLockFE.writeLock().lock();
            int index = -1;
            for (int i = 0; i < frontEndList.size(); i++) {
                if (frontEndList.get(i).equals(host)) {
                    index = i;
                    break;
                }
            }
            if (index != -1)
                frontEndList.remove(index);
        } catch (Exception e) {
            log.debug("deleting FE " + e);
        } finally {
            readWriteLockFE.writeLock().unlock();
        }
    }

    /* ENd of FrontEnd Code */

    /**
     * A method called by the primary only that parses the frontend list, membership list, and events map
     * in a json list and return it as a json format string
     *
     * @return json string format
     */
    public String getAllLists() {
        try {
            SortedMap<String, JSONArray> map = new TreeMap<>();
            map.put("members", getMemberMapJSON());
            map.put("fe", getFrontEndJsonArray());
            map.put("events", getEventsJsonList(true));
            map.put("operation", getOperationIdJson());
            String jsonText = JSONValue.toJSONString(map);
            return jsonText;
        } catch (Exception e) {
            return "";
        }
    }
}
