package Session;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.util.SortedMap;

/**
 * A class that handles all the requests sent internally from the user service and returns the response back.
 *
 * @author Hassan Chadad
 */
public class RequestParser {

    private String jsonData;
    private SortedMap<String, SessionTimer> sessionTimerSortedMap; // session map that saves <userID, sessionTimer object>
    private final static Logger log = LogManager.getRootLogger(); // Log Object to print to the System.out.println file


    /**
     * Constructor
     *
     * @param jsonData
     * @param sessionTimerSortedMap
     */
    public RequestParser(String jsonData, SortedMap<String, SessionTimer> sessionTimerSortedMap) {
        this.jsonData = jsonData;
        this.sessionTimerSortedMap = sessionTimerSortedMap;
    }

    /**
     * A method that checks if the method is GET/POST and sends the request to the appropriate function
     * and returns the response
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
        if (apiReq.matches("[\\d]+")) { // example /session/2 where 2 is the user ID
            return getSession(apiReq);
        } else { // return event with eventId
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
        if (apiReq.equals("start"))
            return startSession();
        else if (apiReq.startsWith("stop"))
            return stopSession();
        else
            return "400";
    }

    /**
     * A method that gets the session Timer object from the session map and calls getSession method
     * and returns the result
     *
     * @param id - userID
     * @return session variable
     */
    private synchronized String getSession(String id) {
        try {
            SessionTimer sessionTimer = sessionTimerSortedMap.get(id);
            if (sessionTimer != null)
                return sessionTimerSortedMap.get(id).getSession();
            else
                return "0";
        }
        catch (Exception e){
            return "0";
        }
    }

    /**
     * A method that parses the Json Data and gets the id (userID) and checks if it exists in the session map.
     * If it doesn't, then a session timer object will be created as a thread and it will be added with the userID(key)
     * to the session map.
     *
     * @return empty string (success) - 400 (failure)
     */
    private synchronized String startSession() {
        try {
            JSONParser parser = new JSONParser();
            JSONObject jsonObject = (JSONObject) parser.parse(jsonData);
            String id = (String) jsonObject.get("id");
            if (sessionTimerSortedMap.get(id) == null) {
                SessionTimer sessionTimer = new SessionTimer();
                Thread thread = new Thread(sessionTimer);
                thread.start();
                sessionTimerSortedMap.put(id, sessionTimer);
            }
            else { // if session exists but timedout then I will delete it and start new session
                SessionTimer sessionTimer = sessionTimerSortedMap.get(id);
                if(sessionTimer.getSession().equals("0")) { // session timed out
                    sessionTimer = new SessionTimer();
                    Thread thread = new Thread(sessionTimer);
                    thread.start();
                    sessionTimerSortedMap.put(id, sessionTimer);
                }
            }
            return "";
        } catch (Exception e) {
            log.debug(e);
            return "400";
        }
    }

    /**
     * A method that parses the Json Data and gets the id (userID) and checks if it exists in the session map.
     * If it does, then a session timer object will call the stop session method to stop the timer and the user will be
     * deleted from the session map
     *
     * @return empty string (success) - 400 (failure)
     */
    private synchronized String stopSession() {
        try {
            JSONParser parser = new JSONParser();
            JSONObject jsonObject = (JSONObject) parser.parse(jsonData);
            String id = (String) jsonObject.get("id");
            SessionTimer sessionTimer = sessionTimerSortedMap.get(id);
            if (sessionTimer != null) {
                sessionTimer.stop();
                sessionTimerSortedMap.remove(id);
            }
            return "";
        } catch (Exception e) {
            log.debug(e);
            return "400";
        }
    }
}
