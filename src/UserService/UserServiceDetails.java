package UserService;

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
 * A singleton class that contains all the details about an user member
 *
 * @author Hassan Chadad
 */
public class UserServiceDetails {

    private static volatile UserServiceDetails instance;

    private boolean isPrimary; // when service is primary this is true
    private boolean newPrimaryElected; // variable to check if new primary elected
    private int port; // current service port
    private int userId; // user id auto incremented
    private int memberId; // auto incremented
    private int operationId; // this is timestamp (operation ID)
    private String host; // current service host
    private String primaryUserHost, primaryEventHost;
    private SortedMap<Integer, String> membershipMap; // map storing all members with their IDs.
    private ArrayList<String> frontEndList; // list of front ends
    private SortedMap<Integer, ClientRequestParser> operationMap; // map to handle multiple operation threads
    private SortedMap<Integer, SortedMap<Integer, Integer>> userTicketMap; // save users' tickets in a map <userid, <eventid, nb of Tickets>>
    private SortedMap<Integer, String[]> userDetailsMap; // save created users' info in a map
    private ReentrantReadWriteLock readWriteLockMember; // to ensure thread safety on member map
    private ReentrantReadWriteLock readWriteLockFE; // to ensure thread safety on Front end list
    private ReentrantReadWriteLock readWriteLockUserMap; // to ensure thread safety on user map
    private SortedMap<Integer, ReentrantReadWriteLock> userMapLocks; // to ensure thread safety on different level of accessing user map
    private ReentrantReadWriteLock readWriteLockOperation; // to ensure thread safety on operation
    private ReentrantReadWriteLock readWriteLockOperationThread; // to ensure thread safety on executing operation threads
    private final static Logger log = LogManager.getRootLogger(); // Log Object to print to the file

    /**
     * Private Constructor
     *
     * @param host
     * @param primaryUserHost
     * @param port
     */
    private UserServiceDetails(String host, String primaryUserHost, int port, String primaryEventHost) {
        this.port = port;
        this.host = host;
        this.primaryUserHost = primaryUserHost;
        this.primaryEventHost = primaryEventHost;
        isPrimary = false;
        newPrimaryElected = true;
        memberId = 0;
        operationId = 0;
        userId = 0;
        operationMap = new TreeMap<>();
        membershipMap = new TreeMap<>();
        frontEndList = new ArrayList<>();
        userDetailsMap = new TreeMap<>();
        userTicketMap = new TreeMap<>();
        userMapLocks = new TreeMap<>();
        readWriteLockMember = new ReentrantReadWriteLock();
        readWriteLockFE = new ReentrantReadWriteLock();
        readWriteLockOperation = new ReentrantReadWriteLock();
        readWriteLockUserMap = new ReentrantReadWriteLock();
        readWriteLockOperationThread = new ReentrantReadWriteLock();
    }

    public static UserServiceDetails getInstance(String host, String primaryUserHost, int port, String primaryEventHost) {
        if (instance == null) {
            synchronized (UserServiceDetails.class) {
                if (instance == null)
                    instance = new UserServiceDetails(host, primaryUserHost, port, primaryEventHost);
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
    public String getPrimaryEventHost() {
        return primaryEventHost;
    }

    /**
     * Set method
     *
     * @param primaryEventHost
     */
    public void setPrimaryEventHost(String primaryEventHost) {
        this.primaryEventHost = primaryEventHost;
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

    /* user Code */

    /**
     * A thread safe method that gets a specific user from user map and parse it in
     * json object and returns the json string
     *
     * @param reqUserId
     * @return json string format
     */
    public String getUser(int reqUserId) {
        try {
            readWriteLockUserMap.readLock().lock();
            lockUserListReadLock(reqUserId);
            if (userDetailsMap.get(reqUserId) != null) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("userid", reqUserId);
                jsonObject.put("username", userDetailsMap.get(reqUserId)[0]);
                jsonObject.put("password", userDetailsMap.get(reqUserId)[1]);
                JSONArray jsonArray = new JSONArray();
                SortedMap<Integer, Integer> eventTickets = userTicketMap.get(reqUserId);
                if (eventTickets != null) {
                    for (int event : eventTickets.keySet()) {
                        for (int i = 0; i < eventTickets.get(event); i++) {
                            JSONObject eventJsonObject = new JSONObject();
                            eventJsonObject.put("eventid", event);
                            jsonArray.add(eventJsonObject);
                        }
                    }
                }
                jsonObject.put("tickets", jsonArray);
                return jsonObject.toJSONString(); // return json object as string
            } else
                return "400";
        } catch (Exception e) {
            return "400";
        } finally {
            userMapLocks.get(reqUserId).readLock().unlock();
            readWriteLockUserMap.readLock().unlock();
        }
    }

    /**
     * A thread safe method that gets all the users' details from user Maps and parse them in json array
     * and return the json array
     *
     * @return json array string format
     */
    public JSONArray getUsersJsonList() {
        JSONArray userJsonArray = new JSONArray();
        try {
            readWriteLockUserMap.readLock().lock();
            for (int key : userMapLocks.keySet()) // lock all readlocks in userMapLock
                userMapLocks.get(key).readLock().lock();
            for (int key : userDetailsMap.keySet()) { // iterate through user map
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("userid", key);
                jsonObject.put("username", userDetailsMap.get(key)[0]);
                jsonObject.put("password", userDetailsMap.get(key)[1]);
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
                userJsonArray.add(jsonObject);
            }
            return userJsonArray; // return json list as string
        } catch (Exception e) {
            return userJsonArray;
        } finally {
            for (int key : userMapLocks.keySet()) // unlock all readlocks in userListLock
                userMapLocks.get(key).readLock().unlock();
            readWriteLockUserMap.readLock().unlock();
        }
    }

    /**
     * A method that creates a ReentrantReadWriteLock for an user and adds it to userMapLocks if it doesn't exist
     * and locks the read lock.
     *
     * @param key - user ID
     */
    private void lockUserListReadLock(int key) {
        if (userMapLocks.get(key) != null)
            userMapLocks.get(key).readLock().lock();
        else {
            ReentrantReadWriteLock readWriteLock1 = new ReentrantReadWriteLock();
            readWriteLock1.readLock().lock();
            userMapLocks.put(key, readWriteLock1);
        }
    }

    /**
     * A thread safe method that creates a user and adds it to the userDetailsMap if the username doesn't exist.
     * On success, it returns json string format of the new created user ID
     *
     * @param userName
     * @param password
     * @return json string of user ID
     */
    public String createUser(String userName, String password) {
        try {

            for (int key : userDetailsMap.keySet()) {
                if (userDetailsMap.get(key)[0].equals(userName))
                    return "400";
            }
            JSONObject responseJson = new JSONObject();
            userId++; // increment user id

            /* userParam are {user name, password} */
            String[] userParam = {userName, password};

            userDetailsMap.put(userId, userParam); // create user and add it to the map
            responseJson.put("userid", userId); // create json response
            return responseJson.toJSONString();
        } catch (Exception e) {
            return "400";
        }
    }

    /**
     * A method that locks the write lock of readWriteLockUserMap
     */
    public void lockWriteLock() {
        readWriteLockUserMap.writeLock().lock();
    }

    /**
     * A method that unlocks the write lock of readWriteLockUserMap
     */
    public void unlockWriteLock() {
        readWriteLockUserMap.writeLock().unlock();
    }

    /**
     * A thread safe method that updates the tickets map for a specific user. It checks if the tickets map for the user
     * is empty it means s/he never did any purchase before then checks if s/he purchased a tickets for same events before.
     *
     * @param userId
     * @param eventId
     * @param tickets
     * @return empty string on success or 400 if failed
     */
    public String addTickets(int userId, int eventId, int tickets) {
        try {
            SortedMap<Integer, Integer> ticketsMap = userTicketMap.get(userId);
            if (ticketsMap == null) { // check if user's ticket map is empty ( user didn't purchase before)
                ticketsMap = new TreeMap<>();
                ticketsMap.put(eventId, tickets);
            } else {
                if (ticketsMap.get(eventId) != null) { // check if the eventId for new tickets exists so I will update the entry and not put
                    int allTickets = ticketsMap.get(eventId) + tickets;
                    ticketsMap.put(eventId, allTickets);
                } else
                    ticketsMap.put(eventId, tickets);
            }
            userTicketMap.put(userId, ticketsMap);
            return "";
        } catch (Exception e) {
            return "400";
        }
    }

    /**
     * A thread safe method that transfers tickets from one user to another
     *
     * @param userId
     * @param eventId
     * @param tickets
     * @return empty string on success or 400 if failed
     */
    public String transferTickets(int userId, int eventId, int tickets, int targetUserId) {

        SortedMap<Integer, Integer> ticketsMap = userTicketMap.get(userId);
        if (ticketsMap == null) { // check if original user's ticket map is empty
            return "400";
        } else {
            if (ticketsMap.get(eventId) != null) { // check if original user has the tickets for the event
                int nbTickets = ticketsMap.get(eventId);
                nbTickets -= tickets;
                if (nbTickets < 0) // nb of tickets to transfer greater than nb of tickets previously purchased
                    return "400";
                else if (nbTickets == 0) {
                    ticketsMap.remove(eventId); // remove the entry because no more tickets
                } else {
                    ticketsMap.put(eventId, nbTickets);
                }
                addTickets(targetUserId, eventId, tickets);
            } else
                return "400";
        }
        return "";
    }

    /**
     * A thread safe method that deletes all the tickets of a specific event for a specific user
     *
     * @param userId
     * @param eventId
     * @return nb of initial tickets before remove (success)
     */
    public int deleteSpecificUserTickets(int userId, int eventId) {
        int nbTickets = -1;
        if (userTicketMap.get(userId) != null && userTicketMap.get(userId).get(eventId) != null) { // check if user's ticket map is not empty and user has tickets for an event
            nbTickets = userTicketMap.get(userId).get(eventId);
            userTicketMap.get(userId).remove(eventId);
            return nbTickets;
        }
        return nbTickets;
    }

    /**
     * A thread safe method that restores all deleted tickets of a specific event for a specific user
     *
     * @param userId
     * @param eventId
     * @return empty string (success)
     */
    public String restoreSpecificUserTickets(int userId, int eventId, int tickets) {
        userTicketMap.get(userId).put(eventId, tickets);
        return "";
    }

    /**
     * A thread safe method that deletes all the tickets of a specific event in all users
     *
     * @param eventId
     * @return empty string on success
     */
    public String deleteEventTickets(int eventId) {
        try {
            for (int userId : userTicketMap.keySet()) { // get each user's ticket map <userid, <eventid, nb tickets>>
                if (userTicketMap.get(userId).get(eventId) != null) { // get TicketMap <eventid, nb of tickets>
                    userTicketMap.get(userId).remove(eventId);
                }
            }
            return "";
        } catch (Exception e) {
            return "400";
        }
    }

    /**
     * A thread safe method that checks if the username and password matches an entry in the user Map
     * and returns the id of the user
     *
     * @param username
     * @param password
     * @return user ID
     */
    public int login(String username, String password) {
        try {
            int id = checkUser(username);
            readWriteLockUserMap.readLock().lock();
            if (id != -1) {
                if (!userDetailsMap.get(id)[1].equals(password))
                    return -1;
            }
            return id;
        } catch (Exception e) {
            return -1;
        } finally {
            readWriteLockUserMap.readLock().unlock();
        }
    }

    /**
     * A thread safe method that checks if the username matches an entry in the user Map
     * and returns the id of the user
     *
     * @param username
     * @return user ID
     */
    public int checkUser(String username) {
        try {
            readWriteLockUserMap.readLock().lock();
            for (int id : userDetailsMap.keySet()) {
                if (userDetailsMap.get(id)[0].equals(username))
                    return id;
            }
            return -1;
        } catch (Exception e) {
            return -1;
        } finally {
            readWriteLockUserMap.readLock().unlock();
        }
    }

    /**
     * A method that checks if the user exists in the user map then that waits till readWriteLockUserMap
     * write lock is not held by another thread. Then it creates a ReentrantReadWriteLock for a specific user
     * and locks its write lock and adds it to the userMapLocks if it doesn't exist.
     *
     * @param key
     * @return true if user exists otherwise false
     */
    public boolean lockUserListWriteLock(int key) {
        if (userDetailsMap.get(key) != null) {
            while (readWriteLockUserMap.writeLock().isHeldByCurrentThread()) {
            } // wait till writelock is unlocked
            if (userMapLocks.get(key) != null)
                userMapLocks.get(key).writeLock().lock();
            else {
                ReentrantReadWriteLock readWriteLock1 = new ReentrantReadWriteLock();
                readWriteLock1.writeLock().lock();
                userMapLocks.put(key, readWriteLock1);
            }
            return true;
        } else
            return false;
    }

    /**
     * A method that unlocks write lock of specific user in userMapLocks
     *
     * @param key - user ID
     */
    public void unlockUserListWriteLock(int key) {
        if (userMapLocks.get(key) != null)
            userMapLocks.get(key).writeLock().unlock();
    }

    /**
     * A thread safe method that updates the current user Maps with the maps sent to it
     * and assigns the userID as last key if map size > 0 or 0 if the map is empty
     *
     * @param detailsMap
     * @param ticketMap
     * @return true (success) - false (failure)
     */
    public boolean updateUserMap(SortedMap<Integer, String[]> detailsMap,
                                 SortedMap<Integer,SortedMap<Integer, Integer>> ticketMap) {
        try {
            readWriteLockUserMap.writeLock().lock();
            userDetailsMap.clear();
            userDetailsMap.putAll(detailsMap);
            if (userDetailsMap.size() > 0)
                userId = userDetailsMap.lastKey();
            else
                userId = 0;
            userTicketMap.clear();
            userTicketMap.putAll(ticketMap);
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            readWriteLockUserMap.writeLock().unlock();
        }
    }

    /* End of user Code */

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
                primaryUserHost = newPrimaryHost;
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
     * A method called by the primary only that parses the frontend list, membership list, and users map
     * in a json list and return it as a json format string
     *
     * @return json string format
     */
    public String getAllLists() {
        try {
            SortedMap<String, JSONArray> map = new TreeMap<>();
            map.put("members", getMemberMapJSON());
            map.put("fe", getFrontEndJsonArray());
            map.put("users", getUsersJsonList());
            map.put("operation", getOperationIdJson());
            String jsonText = JSONValue.toJSONString(map);
            return jsonText;
        } catch (Exception e) {
            return "";
        }
    }
}
