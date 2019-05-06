package EventService;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;

/**
 * A class that implements runnable and handles requests from FrontEnd or event services.
 * It handles the request and sends it to the RequestParser and returns the response.
 *
 * @author Hassan Chadad
 */
public class GeneralRequestHandler implements Runnable {
    private final Socket connectionSocket;
    private String userHost;
    private String jsonData;
    private String clientHost;
    private final static Logger log = LogManager.getRootLogger(); // Log Object to print to the log.debug file

    /**
     * Constructor
     *
     * @param connectionSocket
     * @param userHost         - in case it needs to connect with user service
     */
    public GeneralRequestHandler(Socket connectionSocket, String userHost) {
        this.connectionSocket = connectionSocket;
        this.userHost = userHost;
        jsonData = "";
        clientHost = "";
    }

    /**
     * Run method that gets the request and parse it to get each line and adds it to headerRequestList,
     * then it splits the API request to get the url and method from it.
     * Then sends it to handleRequest method and returns the response back
     */
    @Override
    public void run() {
        //log.debug("A client connected..." + connectionSocket);
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(connectionSocket.getOutputStream()));
             BufferedReader reader = new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()))) {
            ArrayList<String> headerRequestList = parseUrlRequest(reader);
            try {
                String[] headerAttributes = parseHeader(headerRequestList.get(0));

                String responseJson = handleRequest(headerAttributes);
                if (!responseJson.equals("400")) {
                    String response = "HTTP/1.1 200 Successful";
                    response += System.lineSeparator();
                    response += "Content-Type: application/json";
                    response += System.lineSeparator();
                    response += System.lineSeparator();
                    response += responseJson;
                    pw.print(response);
                } else
                    pw.println("HTTP/1.1 400 Request not found.");
                pw.flush();
            } catch (Exception e) { // any error occurs will return 400, ex: headerReqList is null and line 29 is executed will have error
                log.debug("General "+e);
                pw.println("HTTP/1.1 400 Request not found.");
                pw.flush();
            }
        } catch (IOException e) {
            log.debug(e);
        } finally {
            try {
                if (connectionSocket != null)
                    connectionSocket.close();
            } catch (IOException e) {
                log.debug("Can't close the socket : " + e);
            }
        }
    }

    /**
     * A method that parses the HTTP request and adds each line to the list
     *
     * @param bufferedReader
     * @return list of http request lines
     */
    private ArrayList<String> parseUrlRequest(BufferedReader bufferedReader) {
        ArrayList<String> requestList = new ArrayList<>();
        int contentLength = 0;
        try {
            String input = bufferedReader.readLine();
            log.debug("before " + input);
            while (bufferedReader.ready() && input != null) {
                requestList.add(input);
                input = bufferedReader.readLine();
                if (input.equals("")) // break when reading empty line, it means I started reading JSON body
                    break;
            }
            // get client source host from cookie
            for (String s : requestList) {
                if (s.startsWith("Cookie") || s.startsWith("cookie")) {
                    String[] temp = s.split(":");
                    String ip = temp[1].trim() + ":" + temp[2];
                    String port = temp[3].trim();
                    clientHost = ip + ":" + port;
                    break;
                }
            }
            //log.debug("client is " + clientHost);
            // get the number of bytes to read from the content-length property
            for (String s : requestList) {
                if (s.startsWith("Content-Length") || s.contains("content-length")) {
                    String[] temp = s.split(":");
                    String num = temp[1];
                    num = num.replace(" ", "");
                    num = num.trim();
                    contentLength = Integer.parseInt(num); // skipping the "}"
                    break;
                }
            }
            requestList.add(input);
            for (int i = 0; i < contentLength; i++) {
                char charByte = (char) bufferedReader.read();
                jsonData += charByte;
            }
            if (requestList.get(0).startsWith("POST")) {
                try {
                    JSONParser parser = new JSONParser();
                    JSONObject jsonObject = (JSONObject) parser.parse(jsonData);
                    jsonData = jsonObject.toJSONString();
                } catch (Exception e) {
                    log.debug(e);
                }
                if (jsonData.length() < 7) // the minimum length of a json is 7 ex: {"a":0} -> length = 7. Less than 7 means not json
                    return null;
            }
            return requestList;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * A method that will return the attributes of the header
     * ex: POST /create will return [POST,create]
     *
     * @param header
     * @return string array of header atttribute
     */
    private String[] parseHeader(String header) {
        header = header.trim();
        String[] headerAttr = header.split(" ");
        headerAttr[1] = headerAttr[1].substring(1);
        return headerAttr;
    }


    /**
     * A method that checks if the request is sent from FE as a client request or as an internal request
     * and sends it to the appropriate class and returns the response of the request.
     *
     * @param headerAttr
     * @return response of the request
     * @throws Exception
     */
    private String handleRequest(String[] headerAttr) throws Exception {
        if (headerAttr[1].matches("[\\d]+") || headerAttr[1].startsWith("purchase") ||
                headerAttr[1].startsWith("create") || headerAttr[1].startsWith("list") ||
                headerAttr[1].matches("update/[\\d]+") || headerAttr[1].startsWith("search") ||
                headerAttr[1].matches("[\\d]+/delete") || headerAttr[1].matches("tickets/[\\d]+/return")) {
            ClientRequestParser requestParser = new ClientRequestParser(jsonData);
            return requestParser.parse(headerAttr);
        } else if (headerAttr[1].equals("alive") || headerAttr[1].equals("allLists") ||
                headerAttr[1].startsWith("newPrimary") || headerAttr[1].startsWith("election") ||
                headerAttr[1].startsWith("addMember") || headerAttr[1].startsWith("addFrontEnd") ||
                headerAttr[1].startsWith("spreadEvents") || headerAttr[1].startsWith("updateEventMap")
                || headerAttr[1].startsWith("newFE") || headerAttr[1].startsWith("setUserPrimary")) {
            InternalRequestParser internalRequestParser = new InternalRequestParser();
            return internalRequestParser.parseRequest(headerAttr, jsonData, clientHost);
        } else
            return "400";
    }
}
