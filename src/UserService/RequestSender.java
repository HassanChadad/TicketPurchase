package UserService;

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
    private UserServiceDetails userServiceDetails;
    private final static Logger log = LogManager.getRootLogger(); // Log Object to print to the System.out.println file

    /**
     * Constructor
     */
    public RequestSender() {
        response = "";
        userServiceDetails = UserServiceDetails.getInstance("","", 0, "");
    }

    /**
     * A method that sends a request to a service (FE/Member/User) and return back the response code
     *
     * @param url
     * @param method        - POST or GET
     * @param jsonParameter
     * @return url's response code
     */
    private int sendRequest(String url, String method, String jsonParameter) throws Exception {
        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();

        con.setRequestMethod(method); // if POST or GET
        con.setRequestProperty("Content-Type", "application/json");
        con.setRequestProperty("Cookie", userServiceDetails.getHost()); // send the host in the request

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
        //log.debug("Response code is " + responseCode);
        if (responseCode == 200) {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String inputLine;

            while ((inputLine = bufferedReader.readLine()) != null) {
                responseBuffer.append(inputLine);
            }
            bufferedReader.close();
            con.disconnect();
            response = responseBuffer.toString();
        } else {
            con.disconnect();
        }
        return responseCode;
    }

    /**
     * A method that sends a request to a service (FE/Member/User) and return back the response as a json string format
     *
     * @param url
     * @param method        - POST or GET
     * @param jsonParameter
     * @return url's response (success)  - 400 (failure) - empty string if url unreachable (service is dead)
     */
    public String sendRequestJson(String url, String method, String jsonParameter) {
        try {
            int responseCode = sendRequest(url, method, jsonParameter);
            if (responseCode == 200) {
                return response; // response got from the service users/users
            } else {
                return "400";
            }
        } catch (Exception e) {
            log.debug(e);
            return ""; // url unreachable so service is dead
        }
    }

    /**
     * A method that sends a request to a service (FE/Member/User) and return back the response as boolean
     *
     * @param url
     * @param method        - POST/GET
     * @param jsonParameter - json body
     * @return true (response Code = 200) - false (response Code != 200)
     */
    public boolean sendRequestBool(String url, String method, String jsonParameter) {
        try {
            int responseCode = sendRequest(url, method, jsonParameter);
            if (responseCode == 200) {
                return true;
            } else
                return false;

        } catch (Exception e) {
            return false; // url unreachable so service is dead
        }
    }


    /**
     * A method that sends an internal request to a service (FE/Member) and return back the response as string
     * depending on the response code
     *
     * @param url
     * @param method        - POST/GET
     * @param jsonParameter - json body
     * @return
     */
    public String sendInternalRequest(String url, String method, String jsonParameter) {
        try {
            int responseCode = sendRequest(url, method, jsonParameter);
            if (responseCode == 200)
                return "ok";
            else if (responseCode == 500)
                return "fail";
            else
                return "no";
        } catch (Exception e) {
            return "error"; // url unreachable so service is dead
        }
    }
}
