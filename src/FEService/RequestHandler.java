package FEService;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;

/**
 * A class that implements runnable and handles requests and analyze them by breaking the url request
 * and adding each part of it (method, url, json Data) to an array and sends it to the Request Parser
 * to excute the proper code for the request.
 *
 * @author Hassan Chadad
 */
public class RequestHandler implements Runnable {
    private final Socket connectionSocket;
    private String jsonData;
    private FrontEndDetails frontEndDetails;
    private final static Logger log = LogManager.getRootLogger(); // Log Object to print to the debug.log file

    /**
     * Constructor
     *
     * @param connectionSocket - client's socket
     * @param frontEndDetails  - frontEndDetails Object
     */
    public RequestHandler(Socket connectionSocket, FrontEndDetails frontEndDetails) {
        this.connectionSocket = connectionSocket;
        this.frontEndDetails = frontEndDetails;
        jsonData = "";
    }

    /**
     * Run method that gets the request and parse it to get each line and adds it to headerRequestList,
     * then it splits the API request to get the url and method from it.
     * Then sends it to handleRequest method and returns the response back
     */
    @Override
    public void run() {
        //System.out.println("A client connected..." + connectionSocket);
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
                System.err.println("Can't close the socket : " + e);
            }
        }
    }

    /**
     * A method that gets the http request from the bufferReader and iterates through each it and adds
     * each line to an arrayList
     *
     * @param bufferedReader
     * @return list of all lines in the request
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
                //System.out.println(input);
            }
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
                JSONParser parser = new JSONParser();
                JSONObject jsonObject = (JSONObject) parser.parse(jsonData);
                jsonData = jsonObject.toJSONString();

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
     * A method that checks if the api request is valid, then sends it to the RequestParser
     * to parse/excute the api request
     *
     * @param headerAttr
     * @return response (400 for any error or a string if successful)
     * @throws Exception
     */
    private String handleRequest(String[] headerAttr) throws Exception {
        if (headerAttr[1].equals("events") || headerAttr[1].equals("events/create") ||
                headerAttr[1].matches("events/[\\d]+/delete") ||
                headerAttr[1].matches("events/update/[\\d]+") || headerAttr[1].equals("events/search") ||
                headerAttr[1].matches("events/[\\d]+") || headerAttr[1].matches("events/[\\d]+/purchase/[\\d]+") ||
                headerAttr[1].matches("users/create") || headerAttr[1].matches("users/[\\d]+") ||
                headerAttr[1].matches("users/[\\d]+/tickets/transfer") || headerAttr[1].matches("users/tickets/[\\d]+/return") ||
                headerAttr[1].matches("users/login") || headerAttr[1].matches("users/logout") ||
                headerAttr[1].equals("primary/newEventPrimary") || headerAttr[1].equals("primary/newUserPrimary") ||
                headerAttr[1].equals("primary/checkFE")) {
            System.out.println("Request recieved " + headerAttr[1]);
            RequestParser requestParser = new RequestParser(jsonData, frontEndDetails);
            return requestParser.parse(headerAttr);
        } else
            return "400";
    }
}
