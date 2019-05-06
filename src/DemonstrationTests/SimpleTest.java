package DemonstrationTests;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

/**
 * A simple test class that creates an event, then purchase tickets, then transfer tickets
 *
 * @author Hassan Chadad
 */
public class SimpleTest {

    private static String frontEnd1 = "http://";


    public static void main(String[] args) {
        try {
            int test = 0;
            String eventName = "";
            int tickets = -1;
            for (int i = 0; i < args.length; i += 2) {
                if (args[i].equalsIgnoreCase("-test"))
                    test = Integer.parseInt(args[i + 1]);
                if (args[i].equalsIgnoreCase("-fe"))
                    frontEnd1 += args[i + 1];
                if (args[i].equalsIgnoreCase("-name"))
                    eventName += args[i + 1];
                if (args[i].equalsIgnoreCase("-tickets"))
                    tickets = Integer.parseInt(args[i + 1]);
            }
            if (test == 1)
                new SimpleTest().runTest1();
            else if (test == 2)
                new SimpleTest().runTest2();
            else if (test == 3)
                new SimpleTest().runTest3();
            else if (test == 4)
                new SimpleTest().runTest4();
            else if (test == 5)
                new SimpleTest().runTest5();
            else if (test == 6)
                new SimpleTest().runTest6();
            else if (test == 7)
                new SimpleTest().runTest7();
            else
                new SimpleTest().runTest8(eventName, tickets);

        } catch (Exception e) {
            System.out.println(e);
            System.exit(0);
        }
    }

    /**
     * Showing duplicate events
     */
    private synchronized void runTest3() {
        try {
            login("hassan_1", "123456");
            wait(700);
            JSONObject jsonPar = new JSONObject();
            jsonPar.put("userid", 1);
            jsonPar.put("eventname", "Event test");
            jsonPar.put("numtickets", 2);
            RequestSender requestSender = new RequestSender();
            String response = requestSender.sendRequest(frontEnd1 + "/events/create", "POST", jsonPar.toJSONString());
            System.out.println("Event create:\n" + response);
            System.out.println("Wait for 2 sec and call the same method again");
            wait(2000);
            jsonPar = new JSONObject();
            jsonPar.put("userid", 1);
            jsonPar.put("eventname", "Event test");
            jsonPar.put("numtickets", 2);
            requestSender = new RequestSender();
            response = requestSender.sendRequest(frontEnd1 + "/events/create", "POST", jsonPar.toJSONString());
            System.out.println("Event create:\n" + response);
            logout("hassan_1");
        } catch (Exception e) {
        }
    }

    /**
     * creating 5 more events
     */
    private synchronized void runTest4() {
        try {
            login("hassan_1", "123456");
            wait(700);
            String[] eventNames = {"dynamic Programming event", "VR event", "Event for programming", "parallel programming", "networking Event"};
            System.out.println("---------------------------");
            System.out.println("Creating 5 more events");
            for (int i = 0; i < 5; i++) {
                JSONObject jsonPar = new JSONObject();
                jsonPar.put("userid", 1);
                jsonPar.put("eventname", eventNames[i]);
                jsonPar.put("numtickets", 2);
                RequestSender requestSender = new RequestSender();
                String response = requestSender.sendRequest(frontEnd1 + "/events/create", "POST", jsonPar.toJSONString());
                System.out.println(response);
            }
            wait(700);
            getEvents();
            logout("hassan_1");
        } catch (Exception e) {
        }
    }

    /**
     * purchasing all tickets from 4 events
     */
    private synchronized void runTest5() {
        try {
            /* purchasing for user id 1 */
            login("hassan_1", "123456");
            wait(700);
            System.out.println("---------------------------");
            System.out.println("Purchasing tickets for user id 1");
            for (int i = 1; i <= 4; i++) {
                JSONObject jsonPar = new JSONObject();
                jsonPar.put("tickets", 1);
                RequestSender requestSender = new RequestSender();
                String response = requestSender.sendRequest(frontEnd1 + "/events/" + i + "/purchase/1", "POST", jsonPar.toJSONString());
                if (response.equals(""))
                    System.out.println("purchase successful from event " + i);
            }
            getUser(1);
            wait(700);
            logout("hassan_1");

            /* purchasing for user id 2 */
            login("hassan_2", "987654");
            wait(700);
            System.out.println("---------------------------");
            System.out.println("Purchasing tickets for user id 2");
            for (int i = 1; i <= 4; i++) {
                JSONObject jsonPar = new JSONObject();
                jsonPar.put("tickets", 1);
                RequestSender requestSender = new RequestSender();
                String response = requestSender.sendRequest(frontEnd1 + "/events/" + i + "/purchase/2", "POST", jsonPar.toJSONString());
                if (response.equals(""))
                    System.out.println("purchase successful from event " + i);
            }
            getUser(2);
            wait(700);
            logout("hassan_2");
            getEvents();
        } catch (Exception e) {
        }
    }

    /**
     * Deleting event
     */
    private synchronized void runTest6() {
        try {
            login("hassan_1", "123456");
            wait(700);
            System.out.println("---------------------------");
            System.out.println("Deleting event id 1");
            JSONObject jsonPar = new JSONObject();
            jsonPar.put("userid", 1);
            RequestSender requestSender = new RequestSender();
            String response = requestSender.sendRequest(frontEnd1 + "/events/1/delete", "POST", jsonPar.toJSONString());
            wait(700);
            getEvents();
            getUser(1);
            logout("hassan_1");
        } catch (Exception e) {
        }
    }

    /**
     * Return tickets
     */
    private synchronized void runTest7() {
        try {
            login("hassan_2", "987654");
            wait(700);
            System.out.println("---------------------------");
            System.out.println("Return tickets to event id 2 from user 2");
            JSONObject jsonPar = new JSONObject();
            jsonPar.put("eventid", 2);
            RequestSender requestSender = new RequestSender();
            String response = requestSender.sendRequest(frontEnd1 + "/users/tickets/2/return", "POST", jsonPar.toJSONString());
            wait(700);
            getEvent(2);
            getUser(2);
            logout("hassan_2");
        } catch (Exception e) {
        }
    }

    /**
     * Update event
     */
    private synchronized void runTest8(String eventName, int additionalTickets) {
        try {
            login("hassan_1", "123456");
            wait(700);
            System.out.println("---------------------------");
            System.out.println("Updating event 3");
            JSONObject jsonPar = new JSONObject();
            jsonPar.put("userid", 1);
            if(!eventName.equals(""))
                jsonPar.put("eventname", eventName);
            if(additionalTickets > 0)
                jsonPar.put("additionaltickets", additionalTickets);
            RequestSender requestSender = new RequestSender();
            String response = requestSender.sendRequest(frontEnd1 + "/events/update/3", "POST", jsonPar.toJSONString());
            wait(700);
            getEvent(3);
            logout("hassan_1");
        } catch (Exception e) {
        }
    }

    private synchronized void runTest1() {
        try {
            int id = createUser("hassan_1", "123456");
            wait(500);
            getUser(id);
            wait(500);
            login("hassan_1", "12345"); // wrong password
            wait(500);
            login("hassan_1", "123456"); // wrong password
            wait(500);
            getUser(id);
            wait(500);
            logout("hassan_1"); // wrong username
            wait(500);
            logout("hassan_1"); // wrong username
        } catch (Exception e) {
        }
    }

    private synchronized void runTest2() {
        try {
            int id = createUser("hassan_2", "987654");
            if (id > 0) {
                wait(500);
                getUser(id);
                wait(500);
                login("hassan_2", "987654");
                wait(500);
                getUser(id);
                System.out.println("----------------------\nwait for 11 seconds");
                wait(11000);
                getUser(id);
            } else
                System.out.println("creating user failed");
        } catch (Exception e) {
        }
    }

    private int createUser(String username, String password) {
        try {
            System.out.println("---------------------------");
            System.out.println("creating user: " + username + ", " + password);
            JSONObject jsonPar = new JSONObject();
            jsonPar.put("username", username);
            jsonPar.put("password", password);
            RequestSender requestSender = new RequestSender();
            String response = requestSender.sendRequest(frontEnd1 + "/users/create", "POST", jsonPar.toJSONString());
            System.out.println(response);
            JSONParser parser = new JSONParser();
            JSONObject jsonObject = (JSONObject) parser.parse(response);
            long userIdLong = (Long) jsonObject.get("userid");
            int userId = (int) userIdLong;
            return userId;
        } catch (Exception e) {
            return -1;
        }
    }

    private void getUser(int id) {
        System.out.println("---------------------------");
        System.out.println("Getting user request");
        RequestSender requestSender = new RequestSender();
        String response = requestSender.sendRequest(frontEnd1 + "/users/" + id, "GET", "");
        System.out.println(response);
    }

    private void login(String username, String password) {
        System.out.println("---------------------------");
        System.out.println("log in -> " + username + ", " + password);
        JSONObject jsonPar = new JSONObject();
        jsonPar.put("username", username);
        jsonPar.put("password", password);
        RequestSender requestSender = new RequestSender();
        String response = requestSender.sendRequest(frontEnd1 + "/users/login", "POST", jsonPar.toJSONString());
        if (response.equals(""))
            System.out.println(username + " successfully logged in");
        else {
            System.out.println("Wrong username or password");
        }
    }

    private void logout(String username) {
        System.out.println("---------------------------");
        System.out.println("log out ->" + username);
        JSONObject jsonPar = new JSONObject();
        jsonPar.put("username", username);
        RequestSender requestSender = new RequestSender();
        String response = requestSender.sendRequest(frontEnd1 + "/users/logout", "POST", jsonPar.toJSONString());
        if (response.equals(""))
            System.out.println(username + " successfully logged out");
        else {
            System.out.println("User is logged out or username doesn't exist.");
        }
    }

    private void getEvents() {
        System.out.println("---------------------------");
        System.out.println("Getting event list");
        RequestSender requestSender = new RequestSender();
        JSONObject jsonPar = new JSONObject();
        jsonPar.put("userid", 1);
        String response = requestSender.sendRequest(frontEnd1 + "/events", "POST", jsonPar.toJSONString());
        System.out.println(response);
    }

    private void getEvent(int id) {
        System.out.println("---------------------------");
        System.out.println("Getting event id "+id);
        RequestSender requestSender = new RequestSender();
        JSONObject jsonPar = new JSONObject();
        jsonPar.put("userid", 2);
        String response = requestSender.sendRequest(frontEnd1 + "/events/"+id, "POST", jsonPar.toJSONString());
        System.out.println(response);
    }
}

