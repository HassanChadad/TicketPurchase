package FEService;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A class that stores all the details of a frontEnd service
 *
 * @author Hassan Chadad
 */
public class FrontEndDetails {

    private String host, eventPrimaryHost, userPrimaryHost;
    private final static Logger log = LogManager.getRootLogger(); // Log Object to print to the System.out.println file

    /**
     * Constructor
     *
     * @param host
     * @param eventPrimaryHost
     * @param userPrimaryHost
     */
    public FrontEndDetails(String host, String eventPrimaryHost, String userPrimaryHost) {
        this.host = "http://" + host;
        this.eventPrimaryHost = eventPrimaryHost;
        this.userPrimaryHost = userPrimaryHost;
    }

    /**
     * A synchronized method that sets the value of eventPrimaryHost
     *
     * @param eventPrimaryHost
     */
    public synchronized void setEventPrimaryHost(String eventPrimaryHost) {
        this.eventPrimaryHost = eventPrimaryHost;
    }

    /**
     * A synchronized method that gets the value of eventPrimaryHost
     */
    public synchronized String getEventPrimaryHost() {
        return eventPrimaryHost;
    }

    /**
     * A synchronized method that sets the value of userPrimaryHost
     *
     * @param userPrimaryHost
     */
    public synchronized void setUserPrimaryHost(String userPrimaryHost) {
        this.userPrimaryHost = userPrimaryHost;
    }

    /**
     * Get method
     *
     * @return userPrimaryHost
     */
    public synchronized String getUserPrimaryHost() {
        return userPrimaryHost;
    }

    /**
     * Get method
     *
     * @return host
     */
    public String getHost() {
        return host;
    }
}
