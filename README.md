# project-5-HassanChadad for CS682

Because the purcahse event ticket application is limited with the features it is providing. The goal of this project is to add extra features that makes the application more interesting, more user friendly, and flexible. Project 3 implemented some features like event create, user create, ticket purchase, ticket transfer, getting list of events, and getting a specific event information. Based on project 3, project 4 was about creating a distributed application with strong consistency. 

So project 5 will use projects 3 and 4 to add new features like the following:

  1.	user authentication: the application will maintain a session for the logged in user. As long as the user keeps interacting with event or user service the session won’t timeout. The way of implementing this feature is by creating an independent session service application that will maintains a session for a logged in user. Since project 5 is not using jetty servlets, then I will write the session logic in the session service. Also, the reason behind having an independent session service is because both event and user service will talk with the session service when receiving a read/write request. So implementing the session in either the event or the user service would lead to inconsistent sessions since the user/event might fail so each user/event members should implement the session logic since any member can be a leader.
To implement the user authentication, the user will have a username and password and will be able to create user, login, logout. Moreover, in case of timeout after sending a request the event/user will send a response “log in again”

  2.	Create user: The user will send a request “POST users/create” with a json body containing username (unique username) and password. Upon success the user will be logged in. Please note that there is no user id since the username is unique so it is the ID.

  3.	Log in: The user will be able to log in when session times out by sending a request “POST users/login” with a json body containing his username and password. When login is successful the user service will send an internal request to the session service to start a session for the user.
  
  4.	Log out: The user can logout anytime by sending a request “POST users/logout”. When logged out, the user service will send an internal request to the session service to stop the session for the user.
  
  5.	The application will allow the user to search for events by filtering his search, so that he can search for events by keyword. The way to implement that is using Lucene Library.
  
  6.	A user can update an event like changing its name or adding more tickets, but only the user that created the event will be able to update the event otherwise the event service will return a "400" response. When updating an event, most of the data structures will be updated.
  
  7.	A user who purchased tickets from an event will be able to return them back and the tickets will be deleted from the user's purchased tickets. The event master will add the tickets back and will send an internal request for the user service to remove the purchased tickets for this specific event from the its ticket purchase list.
  
  8.	The application will disallow duplicates so that the same user can’t create two events with the same name. So when the same user creates an event with the same name the response will be “event exists” since in real life there is no way two events would have the same name except they are created in different countries.
  
  9.	Event deletion and only the creator will be able to delete the event, knowing that any event deletion will lead to the deletion of all the tickets purchased by the users, so the event service will send an internal request to the user service to update the list of purchased tickets for specific user by removing the tickets for the specified event that were previously purchased.
  
  
The project plan will be as follows: the 1st, 2nd, 3rd, 4th, and 8th feature should be finished before May 8th and will be presented on the demonstration day. The remaining features will be implemented before the final demonstration. When finishing this project, I will be doing both user and event service in addition to strong consistency for both.

In my opinion, finishing this project will help me achieve my goal which is creating a full distributed system. I would really appreciate it if my proposal got an approval.
