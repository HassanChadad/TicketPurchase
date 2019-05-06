package FEService;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * A class the parses the request and sends it to the appropriate service
 *
 * @author Hassan Chadad
 */
public class RequestParser {

    private String method, url, jsonData;
    private FrontEndDetails frontEndDetails;
    private final static Logger log = LogManager.getRootLogger(); // Log Object to print to the debug.log file

    /**
     * Constructor
     *
     * @param jsonData
     * @param frontEndDetails
     */
    public RequestParser(String jsonData, FrontEndDetails frontEndDetails) {
        this.jsonData = jsonData;
        this.frontEndDetails = frontEndDetails;
        method = "GET";
        url = "";
    }

    /**
     * A method that sends the request depending on the api to the appropriate method
     * to excute it and returns back the response
     *
     * @param headerAttr
     * @return response
     */
    public String parse(String[] headerAttr) {
        if (headerAttr[1].equals("primary/newEventPrimary")) { // this api is sent from the primary
            return updateEventPrimary(jsonData);
        } else if (headerAttr[1].equals("primary/newUserPrimary")) { // this api is sent from the primary
            return updateUserPrimary(jsonData);
        } else if (headerAttr[1].equals("primary/checkFE")) { // this api is sent from the primary to check if FE is still alive
            return "";
        } else { // this api is for users or events service
            if (session_alive(headerAttr[1])) // check if session timed out or not
                return handleRequest(headerAttr);
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("error", "User not logged in!");
            return jsonObject.toJSONString();
        }
    }

    /**
     * A method that parses the jsonData to get the new primary event host and updates it in its details. Then it sends the event primary
     * a request to update the user primary host.
     * this method is only called when the new elected primary sends it's host to the front end to update it in its details.
     *
     * @param jsonData - contains primary event host
     * @return response (empty string on successful of update, otherwise 400 if an error occured)
     */
    private String updateEventPrimary(String jsonData) {
        try {
            JSONParser parser = new JSONParser();
            JSONObject jsonObject = (JSONObject) parser.parse(jsonData);
            String host = (String) jsonObject.get("host");
            frontEndDetails.setEventPrimaryHost(host);
            sendPrimariesToHost();
            return "";
        } catch (Exception e) {
            return "400";
        }
    }

    /**
     * A method that parses the jsonData to get the new primary user host and updates it in its details. Then it sends the user primary
     * a request to update the event primary host.
     * this method is only called when the new elected primary sends it's host to the front end to update it in its details.
     *
     * @param jsonData - contains primary user host
     * @return response (empty string on successful of update, otherwise 400 if an error occured)
     */
    private String updateUserPrimary(String jsonData) {
        try {
            JSONParser parser = new JSONParser();
            JSONObject jsonObject = (JSONObject) parser.parse(jsonData);
            String host = (String) jsonObject.get("host");
            frontEndDetails.setUserPrimaryHost(host);
            sendPrimariesToHost();
            return "";
        } catch (Exception e) {
            return "400";
        }
    }

    /**
     * A method that sends a primary update request to the event service to update the user primary host
     * and to the user service to update the event primary host.
     */
    private void sendPrimariesToHost () {
        /* Send to user the event primary host */
        String api = frontEndDetails.getUserPrimaryHost() + "/setEventPrimary";
        JSONObject jsonHost = new JSONObject();
        jsonHost.put("host", frontEndDetails.getEventPrimaryHost());
        sendRequest(api, "POST", jsonHost.toJSONString());
        /* Send to event the user primary host */
        api = frontEndDetails.getEventPrimaryHost() + "/setUserPrimary";
        jsonHost.clear();
        jsonHost.put("host", frontEndDetails.getUserPrimaryHost());
        sendRequest(api, "POST", jsonHost.toJSONString());
    }

    /**
     * A method that parses the url in the headerAttr array and sends the request to the appropriate
     * service depending on it and waits for a response to return back.
     *
     * @param headerAttr
     * @return response returned from the request sent to user/event service
     */
    private String handleRequest(String[] headerAttr) {
        if (headerAttr[0].startsWith("POST"))
            method = "POST";
        if (headerAttr[1].startsWith("events")) {
            url = frontEndDetails.getEventPrimaryHost(); // change this to mc01.cs.usfca.edu
            headerAttr[1] = headerAttr[1].replace("events", "");
            log.debug("new :" + method + "hello");
            if (headerAttr[1].length() == 0) { // initially it was /events
                if (method.equals("POST")) {
                    method = "GET";
                    url += "/list";
                } else
                    return "400";
            } else if (headerAttr[1].equals("/create")) { // Create event
                if (method.equals("POST")) {
                    url += "/create";
                } else
                    return "400";
            } else if (headerAttr[1].equals("/search")) { // search for events
                if (method.equals("POST")) {
                    url += "/search";
                } else
                    return "400";
            } else {
                if (headerAttr[1].matches("/update/[\\d]+")) { // update event
                    if (method.equals("POST"))
                        url += headerAttr[1];
                    else
                        return "400";
                } else if (headerAttr[1].matches("/[\\d]+")) { // Get one event
                    if (method.equals("POST")) {
                        method = "GET";
                        url += headerAttr[1];
                    } else
                        return "400";
                } else if (headerAttr[1].matches("/[\\d]+/delete")) { // delete an event
                    if (method.equals("POST")) {
                        url += headerAttr[1];
                    } else
                        return "400";
                } else if (headerAttr[1].matches("/[\\d]+/purchase/[\\d]+")) {
                    if (method.equals("POST")) {
                        try {
                            String[] attr = headerAttr[1].split("/");
                            url += "/purchase/" + attr[1];
                            JSONParser parser = new JSONParser();
                            JSONObject reqJsonObject = (JSONObject) parser.parse(jsonData);
                            long tickets = (Long) reqJsonObject.get("tickets");
                            JSONObject jsonObject = new JSONObject();
                            jsonObject.put("eventid", Integer.parseInt(attr[1]));
                            jsonObject.put("userid", Integer.parseInt(attr[3]));
                            jsonObject.put("tickets", tickets);
                            jsonData = jsonObject.toJSONString();
                        } catch (Exception e) {
                            return "400";
                        }
                    } else
                        return "400";
                } else
                    return "400";
            }
        } else { // /users
            url = frontEndDetails.getUserPrimaryHost(); // change this to mc01.cs.usfca.edu
            headerAttr[1] = headerAttr[1].replace("users", "");
            log.debug("new :" + headerAttr[1]);
            if (headerAttr[1].equals("/create")) { // create user
                if (method.equals("POST")) {
                    url += "/create";
                } else
                    return "400";
            } else if (headerAttr[1].matches("/[\\d]+")) { // get a user
                if (method.equals("GET"))
                    url += headerAttr[1];
                else
                    return "400";
            } else if (headerAttr[1].matches("/[\\d]+/tickets/transfer")) { // transfer tickets
                if (method.equals("POST")) {
                    url += headerAttr[1];
                } else
                    return "400";
            } else if (headerAttr[1].matches("/tickets/[\\d]+/return")) { // return tickets
                if (method.equals("POST")) {
                    url += headerAttr[1];
                } else
                    return "400";
            } else if (headerAttr[1].equals("/login")) { // login
                if (method.equals("POST")) {
                    url += headerAttr[1];
                } else
                    return "400";
            } else if (headerAttr[1].equals("/logout")) { // logout
                if (method.equals("POST")) {
                    url += headerAttr[1];
                } else
                    return "400";
            } else
                return "400";
        }
        return sendRequest(url, method, jsonData);
    }

    /**
     * A method that sends a request to the event/user service and return back the response
     *
     * @return service's response
     */
    private String sendRequest(String url, String method, String jsonData) {
        try {
            //System.out.println(url + "\t" + method + "\t" + jsonData);
            URL obj = new URL(url);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();

            con.setRequestMethod(method); // if POST or GET
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("Cookie", frontEndDetails.getHost()); // send the host of the frontend as a cookie

            if (method.equals("POST")) { // if post then write post body
                String urlParameters = jsonData;
                // Send post request
                con.setDoOutput(true);
                con.setDoInput(true);
                PrintWriter pw = new PrintWriter(new OutputStreamWriter(con.getOutputStream()));
                pw.print(urlParameters);
                pw.flush();
                pw.close();
            }

            int responseCode = con.getResponseCode();

            StringBuffer response = new StringBuffer();
            log.debug("Response code is " + responseCode);
            if (responseCode == 200) {
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(con.getInputStream()));
                String inputLine;

                while ((inputLine = bufferedReader.readLine()) != null) {
                    response.append(inputLine);
                }
                bufferedReader.close();
                return response.toString(); // response got from the service events/users
            } else
                return "400";

        } catch (Exception e) {
            log.debug(e);
            return "400";
        }
    }

    /**
     * A method that checks for certain requests like events/create, purchase, ticket transfer, and get user then checks if the
     * user's session has timedout or not. if it is timedout then these request won't execute.
     *
     * @param request
     * @return status of session (true - session still alive) , (false - session timedout)
     */
    private boolean session_alive(String request) {
        try {
            int userId = -1;
            if (request.equals("events/create")) {
                JSONParser parser = new JSONParser();
                JSONObject reqJsonObject = (JSONObject) parser.parse(jsonData);
                long id = (Long) reqJsonObject.get("userid");
                userId = (int) id;
            } else if (request.matches("events/update/[\\d]+")) {
                JSONParser parser = new JSONParser();
                JSONObject reqJsonObject = (JSONObject) parser.parse(jsonData);
                long id = (Long) reqJsonObject.get("userid");
                userId = (int) id;
            } else if (request.matches("events/[\\d]+/delete")) {
                JSONParser parser = new JSONParser();
                JSONObject reqJsonObject = (JSONObject) parser.parse(jsonData);
                long id = (Long) reqJsonObject.get("userid");
                userId = (int) id;
            } else if (request.matches("events/[\\d]+") || request.equals("events/search") || request.equals("events")) {
                /* this condition (get events, get event, search) doesn't require log in so I am resetting the session only
                 for logged in users so that the session don't timeout*/
                JSONParser parser = new JSONParser();
                JSONObject reqJsonObject = (JSONObject) parser.parse(jsonData);
                long id = (Long) reqJsonObject.get("userid");
                userId = (int) id;
                sendRequest("http://mc02.cs.usfca.edu:2355/" + userId, "GET", ""); // send internal request to session
                return true;
            } else {
                String[] requestArray = request.split("/");
                if (request.matches("events/[\\d]+/purchase/[\\d]+"))
                    userId = Integer.parseInt(requestArray[3]);

                if (request.matches("users/[\\d]+"))
                    userId = Integer.parseInt(requestArray[1]);

                if (request.matches("users/[\\d]+/tickets/transfer"))
                    userId = Integer.parseInt(requestArray[1]);

                if (request.matches("users/tickets/[\\d]+/return"))
                    userId = Integer.parseInt(requestArray[2]);
            }
            if (userId != -1) {
                String result = sendRequest("http://mc02.cs.usfca.edu:2355/" + userId, "GET", ""); // send internal request to session
                log.debug("The result is \"" + result + "\"");
                if (result.equals("0"))
                    return false;
                else
                    return true;
            } else // request is not in the above list that's why userid didn't change so return true
                return true;
        } catch (Exception e) {
            log.debug(e);
            return false;
        }
    }
}
