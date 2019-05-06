package DemonstrationTests;

import EventService.EventServiceDetails;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * A class that sends HTTP requests to URL and returns back the response
 *
 * @author Hassan Chadad
 */
public class RequestSender {

    String response;
    private final static Logger log = LogManager.getRootLogger(); // Log Object to print to the System.out.println file

    /**
     * Constructor
     */
    public RequestSender() {
        response = "";
    }

    /**
     * A method that sends a request to a service (FE/Member/User) and return back the response code
     *
     * @param url
     * @param method        - POST or GET
     * @param jsonParameter
     * @return url's response code
     */
    public String sendRequest(String url, String method, String jsonParameter) {
        try {
            URL obj = new URL(url);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();

            con.setRequestMethod(method); // if POST or GET
            con.setRequestProperty("Content-Type", "application/json");

            if (method.equals("POST")) { // if post then write post body
                String urlParameters = jsonParameter;
                // Send post request
                con.setDoOutput(true);
                con.setDoInput(true);
                PrintWriter pw = new PrintWriter(new OutputStreamWriter(con.getOutputStream()));
                pw.print(urlParameters);
                pw.flush();
                pw.close();
            }

            int responseCode = con.getResponseCode();

            StringBuffer responseBuffer = new StringBuffer();
            //System.out.println("Response code is " + responseCode);
            if (responseCode == 200) {
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(con.getInputStream()));
                String inputLine;

                while ((inputLine = bufferedReader.readLine()) != null) {
                    responseBuffer.append(inputLine);
                }
                bufferedReader.close();
                con.disconnect();
                response = responseBuffer.toString();
                return response;
            } else {
                con.disconnect();
                return "400";
            }
        }
        catch (Exception e) {
            log.debug(e);
            return "400"; // url unreachable so service is dead
        }
    }
}
