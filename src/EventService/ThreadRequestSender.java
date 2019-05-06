package EventService;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A class that implements Runnable that send HTTP url requests.
 *
 * @author Hassan Chadad
 */
public class ThreadRequestSender implements Runnable {

    private String jsonData;
    private String response;
    private String url;
    private String method;
    private Object object;
    private String type;

    /**
     * Constructor
     *
     * @param host
     * @param method
     * @param jsonData
     * @param object
     * @param type
     */
    public ThreadRequestSender(String host, String method, String jsonData, Object object, String type) {
        this.jsonData = jsonData;
        this.method = method;
        this.object = object;
        this.type = type;
        url = host;
        response = "no";
    }

    /**
     * A run method that creates a RequestSender object to send HTTP URL Requests
     * if the request is newPrimary then the expected response is the operation ID, so the method will
     * match the response with regex pattern and assign it if it matches to the response variable
     * If the request is not newPrimary which means any other request, then assign ok to response on success
     */
    @Override
    public void run() {
        RequestSender requestSender = new RequestSender();
        if (!url.contains("/newPrimary")) { // any url
            String result = requestSender.sendInternalRequest(url, method, jsonData);
            while (result.equals("no"))
                result = requestSender.sendInternalRequest(url, method, jsonData);
            if (result.equals("error"))
                response = "error";
            else
                response = "ok";
            notifyObject();
            //log.debug("From " + url + " response is " + response);
        } else { // newPrimary request
            String result = requestSender.sendRequestJson(url, method, jsonData); // get the operation ID as response
            Pattern pattern = Pattern.compile("\\{\"id\":(\\d+)}");
            Matcher matcher = pattern.matcher(result);
            while (result.equals("400"))
                result = requestSender.sendRequestJson(url, method, jsonData); // send again
            if (result.equals("")) { // member failed
                response = "error";
                notifyObject();
            } else { // result returned is operation
                //log.debug(url +"response returned is "+result+".");
                if (matcher.matches()) { // OK
                    response = matcher.group(1);
                    notifyObject();
                }
            }
            //log.debug("From " + url + " response is " + response);
        }
    }

    /**
     * A Get method for response
     *
     * @return response
     */
    public String getResponse() {
        return response;
    }

    /**
     * A method that casts the object to a specific class to call its wake method.
     */
    private void notifyObject() {
        if (type.equals("client")) {
            ClientRequestParser client = (ClientRequestParser) object;
            client.wake();
        } else if (type.equals("internal")) {
            InternalRequestParser internal = (InternalRequestParser) object;
            internal.wake();
        }
    }
}