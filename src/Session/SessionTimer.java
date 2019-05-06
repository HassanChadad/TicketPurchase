package Session;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A class that implements Runnable and maintains a session timer for each user that logged in
 *
 * @author Hassan Chadad
 */

public class SessionTimer implements Runnable {

    boolean timedout, stop; // variables to check timeout and to stop timer
    int session; // session variable to be retrieved for users ( 0 means no session )
    private final static Logger log = LogManager.getRootLogger(); // Log Object to print to the System.out.println file

    /**
     * Constructor
     */
    public SessionTimer() {
        timedout = true;
        stop = false;
        session = 1; // 1 means session didn't timeout yet
    }

    /**
     * a run method used as a timer that waits for 2 minutes, if it was notified before 2 minutes then it will reset the timer
     * and start again otherwise it will change session to zero and breaks from the loop indicating a session timeout.
     * Also, if stop variable became true it means the timer is stopped so the loop will break otherwise continue to check
     * if timedout.
     */
    @Override
    public synchronized void run() {
        try {
            while (true) {
                timedout = true;
                wait (10000); // 10 sec for demonstration but it should be 2 minutes
                if (stop)
                    break;
                if (timedout) {
                    session = 0;
                    break;
                }
            }
        } catch (Exception e) {
            log.debug(e);
        }
    }

    /**
     * A method that checks if session didn't timeout and changes the timeout to false and notifies the run method to start
     * timer again then returns the session variable
     *
     * @return 0 means session timed out - 1 means session still active
     */
    public synchronized String getSession() {
        if (session != 0) { // session didn't timeout yet
            timedout = false;
            notifyAll();
        }
        return session + "";
    }

    /**
     * A method that changes the stop variable to true and notifies the run method to break the loop
     */
    public synchronized void stop() {
        stop = true;
        notifyAll();
    }
}
