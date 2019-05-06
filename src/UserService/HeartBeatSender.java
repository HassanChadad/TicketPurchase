package UserService;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.SortedMap;

/**
 * A class that extends a thread and sends an alive request to all members to check if they are still alive
 *
 * @author Hassan Chadad
 */
public class HeartBeatSender implements Runnable {

    private boolean heartBeat;
    private UserServiceDetails userServiceDetails;
    private final static Logger log = LogManager.getRootLogger(); // Log Object to print to the System.out.println file

    /**
     * Constructor
     */
    public HeartBeatSender() {
        heartBeat = false;
        userServiceDetails = UserServiceDetails.getInstance("","", 0, "");
    }

    /**
     * run method that sends heart beat requests to all the members except the current service (myself) every 5 seconds
     * and detects if the primary fails.
     * On primary failure, it stops sending heartbeat and starts an election request
     */
    @Override
    public void run() {
        synchronized (this) {
            try {
                String myHost = userServiceDetails.getHost();
                SortedMap<Integer, String> map = userServiceDetails.getMembershipMap();
                System.out.println("Sending heartbeat to other members");
                while (!userServiceDetails.isPrimary() && heartBeat && map.size() > 1) {
                    for (int key : map.keySet()) {
                    /* skip sending to myself */
                        if (!map.get(key).equals(myHost)) {
                            RequestSender requestSender = new RequestSender();
                            //System.out.println("Sending heartbeat to " + map.get(key));
                            String result = requestSender.sendInternalRequest(map.get(key) + "/alive", "GET", "");
                            if (heartBeat) {
                                if (result.equals("error")) {
                                    if (key == map.firstKey()) {
                                        if (heartBeat && userServiceDetails.isNewPrimaryElected()) { // OMG! the primary failed!
                                            heartBeat = false;
                                            Election election = Election.getInstance();
                                            Thread thread = new Thread(election);
                                            thread.start();
                                            break;
                                        }
                                    } else {
                                        userServiceDetails.deleteMember(map.get(key)); // delete member
                                    }
                                    System.out.println("heartbeat failed so deleting " + map.get(key));
                                }
                            }
                        }
                    }
                    wait(5000); // wait for 5 sec then send again heartbeat request
                    map = userServiceDetails.getMembershipMap();
                }
                if (map.size() > 1)
                    System.out.println("Heartbeat stopped.");
            } catch (Exception e) {
                log.debug("Heartbeat error: " + e);
            }
        }
    }

    /**
     * A set method for heartbeat
     *
     * @param heartBeat
     */
    public void setHeartBeat(boolean heartBeat) {
        this.heartBeat = heartBeat;
    }


}
