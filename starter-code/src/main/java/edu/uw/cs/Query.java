package edu.uw.cs;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.security.*;
import java.security.spec.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import javax.xml.bind.*;

/**
 * Runs queries against a back-end database
 */
public class Query {
  // DB Connection
  private Connection conn;

  // Password hashing parameter constants
  private static final int HASH_STRENGTH = 65536;
  private static final int KEY_LENGTH = 128;

  // Canned queries
  private static final String CHECK_FLIGHT_CAPACITY = "SELECT capacity FROM Flights WHERE fid = ?";
  private PreparedStatement checkFlightCapacityStatement;

  private static final String CREATE_USER = "INSERT INTO User VALUES (?, ?, ?)";
  private PreparedStatement createUser;

  private static final String LOGIN = "SELECT password FROM Users WHERE username = ?";
  private PreparedStatement login;

  private static final String IT_DIRECT = "SELECT TOP (?) actual_time, fid, day_of_month, carrier_id, flight_num, capacity, price, origin_city, dest_city FROM Flights WHERE origin_city = ? AND dest_city = ? AND day_of_month = ? AND canceled = 0 ORDER BY actual_time ASC, fid ASC";
  private PreparedStatement directFlights;

  private static final String IT_INDIRECT = "SELECT TOP (?) F1.actual_time + F2.actual_time AS total_time, F1.actual_time AS F1_actual_time, F1.fid AS F1_fid, F1.day_of_month AS F1_day_of_month, F1.carrier_id AS F1_carrier_id, F1.flight_num AS F1_flight_num, F1.capacity AS F1_capacity, F1.price AS F1_price, F1.origin_city AS F1_origin_city, F1.dest_city AS F1_dest_city, F2.actual_time AS F2_actual_time, F2.fid AS F2_fid, F2.day_of_month AS F2_day_of_month, F2.carrier_id AS F2_carrier_id, F2.flight_num AS F2_flight_num, F2.capacity AS F2_capacity, F2.price AS F2_price, F2.origin_city AS F2_origin_city, F2.dest_city AS F2_dest_city FROM Flights AS F1, Flights AS F2 WHERE F1.origin_city = ? AND F2.dest_city = ? AND F1.dest_city = F2.origin_city AND F1.day_of_month = ? AND F1.day_of_month = F2.day_of_month AND F1.canceled = 0 AND F2.canceled = 0 ORDER BY total_time ASC, F1.fid ASC, F2.fid ASC";
  private PreparedStatement allFlights;

  private static final String DELETE_USERS = "DELETE FROM Users";
  private PreparedStatement deleteUsers;

  private static final String DELETE_RESERVATIONS = "DELETE FROM Reservations";
  private PreparedStatement deleteReservations;

  private static final String CHECK_DAY = "SELECT COUNT(day_of_month) AS count FROM Reservations WHERE username = ? AND day_of_month = ? AND canceled = 0";
  private PreparedStatement checkDay;

  private static final String RES_CAPACITY_F1 = "SELECT COUNT(*) AS count FROM Reservations WHERE fid_1 = ?";
  private PreparedStatement resCapacityF1;

  private static final String RES_CAPACITY_F2 = "SELECT COUNT(*) AS count FROM Reservations WHERE fid_2 = ?";
  private PreparedStatement resCapacityF2;

  private static final String UPDATE_RES = "INSERT INTO Reservations VALUES (?, ?, ?, ?, ?, ?, ?)";
  private PreparedStatement updateReservation;

  private static final String PAY_RES = "SELECT fid_1, fid_2 FROM Reservations WHERE username = ? AND canceled = 0 AND ID = ? AND paid = 0";
  private PreparedStatement payReservation;

  private static final String FIND_PRICE = "SELECT price FROM Flights WHERE fid = ?";
  private PreparedStatement findPrice;

  private static final String FIND_BALANCE = "SELECT balance FROM Users WHERE username = ?";
  private PreparedStatement findBalance;

  private static final String UPDATE_BALANCE = "UPDATE Users SET balance = ? WHERE username = ?";
  private PreparedStatement updateBalance;
  
  private static final String CANCEL = "SELECT fid_1, fid_2, canceled, paid FROM Reservations WHERE ID = ? AND canceled = 0";
  private PreparedStatement checkCancel;

  private static final String UPDATE_CANCEL = "UPDATE Reservations SET canceled = 1, paid = 0 WHERE ID = ?";
  private PreparedStatement updateCancel;

  private static final String UPDATE_PAID = "UPDATE Reservations SET paid = 1 WHERE ID = ?";
  private PreparedStatement updatePaid;

  private static final String RID = "SELECT COUNT(*) AS CNT FROM Reservations";
  private PreparedStatement rid;

  private static final String RESERVE = "SELECT * FROM Reservations WHERE username = ? AND canceled = 0 ORDER BY ID";
  private PreparedStatement reserve;

  private static final String FLIGHTS = "SELECT * FROM Flights WHERE fid = ?";
  private PreparedStatement flights;

  // Checks if user is logged in
  public boolean loginCheck = false; 

  // A list to store the Itineraries and a counter to check how many indirect flights to include
  List<IT> list_it = new LinkedList<>();
  public int itCounter = 0;

  // For updating reservation IDs
  public int reservationID;

  // For storing userID when logged in
  public String userID;
  /**
   * Establishes a new application-to-database connection. Uses the
   * dbconn.properties configuration settings
   * 
   * @throws IOException
   * @throws SQLException
   */
  public void openConnection() throws IOException, SQLException {
    // Connect to the database with the provided connection configuration
    Properties configProps = new Properties();
    configProps.load(new FileInputStream("dbconn.properties"));
    String serverURL = configProps.getProperty("hw1.server_url");
    String dbName = configProps.getProperty("hw1.database_name");
    String adminName = configProps.getProperty("hw1.username");
    String password = configProps.getProperty("hw1.password");
    String connectionUrl = String.format("jdbc:sqlserver://%s:1433;databaseName=%s;user=%s;password=%s", serverURL,
        dbName, adminName, password);
    conn = DriverManager.getConnection(connectionUrl);

    // By default, automatically commit after each statement
    conn.setAutoCommit(true);

    // By default, set the transaction isolation level to serializable
    conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
  }

  /**
   * Closes the application-to-database connection
   */
  public void closeConnection() throws SQLException {
    conn.close();
  }

  /**
   * Clear the data in any custom tables created.
   * 
   * WARNING! Do not drop any tables and do not clear the flights table.
   */

  public void clearTables() {
    try {
      deleteUsers.clearParameters();
      deleteReservations.clearParameters();
      deleteReservations.executeUpdate();   // Deletes tuples from reservations followed by Users
      deleteUsers.executeUpdate();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /*
   * prepare all the SQL statements in this method.
   */
  public void prepareStatements() throws SQLException {
    checkFlightCapacityStatement = conn.prepareStatement(CHECK_FLIGHT_CAPACITY);
    createUser = conn.prepareStatement(CREATE_USER);
    login = conn.prepareStatement(LOGIN);
    directFlights = conn.prepareStatement(IT_DIRECT);
    allFlights = conn.prepareStatement(IT_INDIRECT);
    deleteUsers = conn.prepareStatement(DELETE_USERS);
    deleteReservations = conn.prepareStatement(DELETE_RESERVATIONS);
    checkDay = conn.prepareStatement(CHECK_DAY);
    resCapacityF1 = conn.prepareStatement(RES_CAPACITY_F1);
    resCapacityF2 = conn.prepareStatement(RES_CAPACITY_F2);
    updateReservation = conn.prepareStatement(UPDATE_RES);
    payReservation = conn.prepareStatement(PAY_RES);
    findPrice = conn.prepareStatement(FIND_PRICE);
    findBalance = conn.prepareStatement(FIND_BALANCE);
    updateBalance = conn.prepareStatement(UPDATE_BALANCE);
    checkCancel = conn.prepareStatement(CANCEL);
    updateCancel = conn.prepareStatement(UPDATE_CANCEL);
    updatePaid = conn.prepareStatement(UPDATE_PAID);
    rid = conn.prepareStatement(RID);
    reserve = conn.prepareStatement(RESERVE);
    flights = conn.prepareStatement(FLIGHTS);
    // TODO: YOUR CODE HERE
  }

  /**
   * Takes a user's username and password and attempts to log the user in.
   *
   * @param username user's username
   * @param password user's password
   *
   * @return If someone has already logged in, then return "User already logged
   *         in\n" For all other errors, return "Login failed\n". Otherwise,
   *         return "Logged in as [username]\n".
   */
  public String transaction_login(String username, String password) {

    if(loginCheck) {
      return "User already logged in\n";
    } else {
      try{

        // To get reservation ID from reservations incase User books
        rid.clearParameters();
        ResultSet r = rid.executeQuery();
        r.next();
        reservationID = r.getInt("CNT");
        r.close();      
        
        // To Check login details while logging in and outputs related messages
        login.clearParameters();
        login.setString(1, username);
        ResultSet results = login.executeQuery();
        results.next();
        String p = results.getString("password");
        results.close();

        if(!p.isEmpty() && p.equalsIgnoreCase(password)){
          loginCheck = true;
          itCounter = 0;
          userID = username;
          return ("Logged in as " + username + "\n");
        } else {
          return "Login failed\n";
         }
      } catch(Exception e) {
          return "Login failed\n";
      }
  }
}

  /**
   * Implement the create user function.
   *
   * @param username   new user's username. User names are unique the system.
   * @param password   new user's password.
   * @param initAmount initial amount to deposit into the user's account, should
   *                   be >= 0 (failure otherwise).
   *
   * @return either "Created user {@code username}\n" or "Failed to create user\n"
   *         if failed.
   */
  public String transaction_createCustomer(String username, String password, int initAmount) {

    // TODO: YOUR CODE HERE
    
    try {

      // Creates a new user and adds a tuple to the users table. 
      // The users table automatically checks for negative balance (CHECK constraint)
      
      createUser.clearParameters();
      
      createUser.setString(1, username);
      
      createUser.setString(2, password);
      
      createUser.setInt(3, initAmount);
      
      createUser.executeUpdate();
      
      return ("Created user  " + username + "\n");
    } catch(Exception e){
      
      e.printStackTrace();
      return "Failed to create user\n";
      
      
    }
  
  }

  /**
   * Implement the search function.
   *
   * Searches for flights from the given origin city to the given destination
   * city, on the given day of the month. If {@code directFlight} is true, it only
   * searches for direct flights, otherwise is searches for direct flights and
   * flights with two "hops." Only searches for up to the number of itineraries
   * given by {@code numberOfItineraries}.
   *
   * The results are sorted based on total flight time.
   *
   * @param originCity
   * @param destinationCity
   * @param directFlight        if true, then only search for direct flights,
   *                            otherwise include indirect flights as well
   * @param dayOfMonth
   * @param numberOfItineraries number of itineraries to return
   *
   * @return If no itineraries were found, return "No flights match your
   *         selection\n". If an error occurs, then return "Failed to search\n".
   *
   *         Otherwise, the sorted itineraries printed in the following format:
   *
   *         Itinerary [itinerary number]: [number of flights] flight(s), [total
   *         flight time] minutes\n [first flight in itinerary]\n ... [last flight
   *         in itinerary]\n
   *
   *         Each flight should be printed using the same format as in the
   *         {@code Flight} class. Itinerary numbers in each search should always
   *         start from 0 and increase by 1.
   *
   * @see Flight#toString()
   */
  public String transaction_search(String originCity, String destinationCity, boolean directFlight, int dayOfMonth,
      int numberOfItineraries) {
    // WARNING the below code is unsafe and only handles searches for direct flights
    // You can use the below code as a starting reference point or you can get rid
    // of it all and replace it with your own implementation.
    //
    
    // Sets counter to zero (to take true false cases into account) and clears previous search history
    itCounter = 0;  
    list_it.clear();
    String sb = "";


    try {
      search_helper(originCity, destinationCity, dayOfMonth, numberOfItineraries);
      if(directFlight){
        for(int j = 0; j < list_it.size(); j++){
          IT z = list_it.get(j);
          sb = sb + "Itinerary " + j + ": 1 flight(s), " + z.total_time + " minutes\n" + z.F1.toString() + "\n";
        }
        return sb.toString();  // Returns direct flights if user enters 1
      } else {
        
          int check = numberOfItineraries - itCounter;
          if(check > 0) {

            // Executes query for all indirect flights
            allFlights.clearParameters();
            allFlights.setString(2, originCity);
            allFlights.setString(3, destinationCity);
            allFlights.setInt(4, dayOfMonth);
            allFlights.setInt(1, check);
            ResultSet result2 = allFlights.executeQuery();

            // Stores flight info in objects and inserts into a list
            while(result2.next()) {
              Flight F2 = new Flight();
              Flight F3 = new Flight();

              F2.fid = result2.getInt("F1_fid");
              F2.dayOfMonth = result2.getInt("F1_day_of_month");
              F2.carrierId = result2.getString("F1_carrier_id");
              F2.flightNum = result2.getString("F1_flight_num");
              F2.originCity = result2.getString("F1_origin_city");
              F2.destCity = result2.getString("F1_dest_city");
              F2.time = result2.getInt("F1_actual_time");
              F2.capacity = result2.getInt("F1_capacity");
              F2.price = result2.getInt("F1_price");
              F3.fid = result2.getInt("F2_fid");
              F3.dayOfMonth= result2.getInt("F2_day_of_month");
              F3.carrierId = result2.getString("F2_carrier_id");
              F3.flightNum = result2.getString("F2_flight_num");
              F3.originCity = result2.getString("F2_origin_city");
              F3.destCity = result2.getString("F2_dest_city");
              F3.time= result2.getInt("F2_actual_time");
              F3.capacity = result2.getInt("F2_capacity");
              F3.price = result2.getInt("F2_price");
              int time = result2.getInt("total_time");

              IT i2 = new IT(F2, F3, time);
              list_it.add(i2);
              itCounter++;
           }
            result2.close();

            // Sorts the itinieraries (based on comparable) 
            Collections.sort(list_it);

          }

          // For returning the string
          for(int j = 0; j < list_it.size(); j++){
            IT z = list_it.get(j);
            if(z.F2 == null){
              sb = sb + "Itinerary " + j + ": 1 flight(s), " + z.total_time + " minutes\n" + z.F1.toString() + "\n";
            } else {
              sb = sb + "Itinerary " + j + ": 2 flight(s), " + z.total_time + " minutes\n" + z.F1.toString() + "\n" + z.F2.toString() + "\n";
            }   
          }  
        }

    } catch (SQLException e) {
      e.printStackTrace();
    }
    return sb;
  }

  // To help with direct flight searches
  private void search_helper(String originCity, String destinationCity, int dayOfMonth, int numberOfItineraries) {
    try {
      directFlights.clearParameters();
      directFlights.setString(2, originCity);
      directFlights.setString(3, destinationCity);
      directFlights.setInt(4, dayOfMonth);
      directFlights.setInt(1, numberOfItineraries);
      ResultSet result = directFlights.executeQuery();

      while (result.next()) {
        
        Flight F1 = new Flight();
        F1.fid = result.getInt("fid");
        F1.dayOfMonth = result.getInt("day_of_month");
        F1.carrierId= result.getString("carrier_id");
        F1.flightNum = result.getString("flight_num");
        F1.originCity = result.getString("origin_city");
        F1.destCity = result.getString("dest_city");
        F1.time = result.getInt("actual_time");
        F1.capacity = result.getInt("capacity");
        F1.price = result.getInt("price");
        int total_time = F1.time;
        IT i = new IT(F1, null, total_time);
        list_it.add(i);
        itCounter++;
      }
      result.close();
    } catch (SQLException e) {
      e.printStackTrace();
    }
}


  /**
   * Implements the book itinerary function.
   *
   * @param itineraryId ID of the itinerary to book. This must be one that is
   *                    returned by search in the current session.
   *
   * @return If the user is not logged in, then return "Cannot book reservations,
   *         not logged in\n". If try to book an itinerary with invalid ID, then
   *         return "No such itinerary {@code itineraryId}\n". If the user already
   *         has a reservation on the same day as the one that they are trying to
   *         book now, then return "You cannot book two flights in the same
   *         day\n". For all other errors, return "Booking failed\n".
   *
   *         And if booking succeeded, return "Booked flight(s), reservation ID:
   *         [reservationId]\n" where reservationId is a unique number in the
   *         reservation system that starts from 1 and increments by 1 each time a
   *         successful reservation is made by any user in the system.
   */
  public String transaction_book(int itineraryId) {
    try{
      if(!loginCheck){
        return "Cannot book reservations, not logged in\n";
      } 

      // Tests for invalid itinerary id
      if (itineraryId >= list_it.size() || itineraryId < 0) {
        return "No such itinerary " + itineraryId + "\n";
      } 

      // Checks if user already has a flight on the same day
      if(check_day(userID, itineraryId)){
        return "You cannot book two flights in the same day\n";
      } 

      // Checks if flight has enough capacity
      if(!capacity_helper(itineraryId)) {
        return "Booking failed, flight is full\n";
      } 
      reservationID++; // Updates ID for next use, if user books in the same login session again

      IT i = list_it.get(itineraryId);
      int day = i.F1.dayOfMonth;
      Flight f1 = i.F1;
      Flight f2 = i.F2;
      int fid1 = f1.fid;
      int fid2;
      updateReservation.clearParameters();
      updateReservation.setInt(1, fid1);

      // Incase od direct flights, FID2 is set to -1 to differentiate. (However, -1 is never visible to the user)
      if(f2 == null){
        updateReservation.setInt(2, -1);
      } else {
        fid2 = f2.fid;
        updateReservation.setInt(2, fid2);
      }

      // Setting parameters and updating tuples to reservations with itinerary information
      int y = 0;
      updateReservation.setInt(3, day);
      updateReservation.setInt(4, y);
      updateReservation.setInt(5, y);
      updateReservation.setInt(6, reservationID);
      updateReservation.setString(7, userID);
      updateReservation.executeUpdate();
      return "Booked flight(s), reservation ID: " + reservationID + "\n";
    
    } catch (Exception e) { 
      return "Booking failed\n";
    }
    
  }

  // To help check if user is trying to book 2 flights on the same day
  private boolean check_day (String username, int itineraryId) throws SQLException { 
    IT i = list_it.get(itineraryId);
    int day = i.F1.dayOfMonth;
    checkDay.clearParameters();
    checkDay.setString(1, username);
    checkDay.setInt(2, day);
    ResultSet result = checkDay.executeQuery();
    result.next();
    int dayCheck = result.getInt("count");
    return !(dayCheck == 0);

  }

  // To check if flight has enough capacity
  private boolean capacity_helper(int itineraryId) throws SQLException{
    IT i = list_it.get(itineraryId);
    int x = checkFlightCapacity(i.F1.fid);
    resCapacityF1.clearParameters();
    resCapacityF1.setInt(1, i.F1.fid);
    ResultSet result = resCapacityF1.executeQuery();
    result.next();
    x = x - result.getInt("count");
    result.close();


    if(x < 1){
      return false;
    } else if (i.F2 != null){   // Checks capacity of 2nd flight if it exists and if 1st flight has enough capacity
      int y = checkFlightCapacity(i.F2.fid);
      resCapacityF2.clearParameters();
      resCapacityF2.setInt(1, i.F2.fid);
      ResultSet results = resCapacityF1.executeQuery();
      results.next();
      y = y - results.getInt("count");
      results.close();

      if(y < 1){
        return false;
      } else {
        return true;
      }
    } else {
      return true;
    }
  }

  /**
   * Implements the pay function.
   *
   * @param reservationId the reservation to pay for.
   *
   * @return If no user has logged in, then return "Cannot pay, not logged in\n"
   *         If the reservation is not found / not under the logged in user's
   *         name, then return "Cannot find unpaid reservation [reservationId]
   *         under user: [username]\n" If the user does not have enough money in
   *         their account, then return "User has only [balance] in account but
   *         itinerary costs [cost]\n" For all other errors, return "Failed to pay
   *         for reservation [reservationId]\n"
   *
   *         If successful, return "Paid reservation: [reservationId] remaining
   *         balance: [balance]\n" where [balance] is the remaining balance in the
   *         user's account.
   */
  public String transaction_pay(int reservationId){
    try {
      if(!loginCheck){
        return "Cannot pay, not logged in\n";
      }

      // Looks for reservation to be paid for
      payReservation.clearParameters();
      payReservation.setString(1, userID);
      payReservation.setInt(2, reservationId);
      ResultSet result = payReservation.executeQuery();
      if(!result.next()) { 
        return ("Cannot find unpaid reservation " + reservationId + " under user: " + userID + "\n");
      }
      int f1 = result.getInt("fid_1");
      int f2 = result.getInt("fid_2");
      result.close();

      // Finds the total price to be paid for flight(s)
      findPrice.clearParameters();
      findPrice.setInt(1, f1);
      ResultSet results = findPrice.executeQuery();
      results.next();
      int f1Price = results.getInt("price");
      int f2Price = 0;
      results.close();

      // Checks price for flight 2 if it exists
      if (f2 != -1) {
        findPrice.clearParameters();
        findPrice.setInt(1,f2);
        ResultSet results2 = findPrice.executeQuery();
        results2.next();
        f2Price = results2.getInt("price");
        results2.close();
      }
      int totalPrice = f1Price + f2Price;
      
      // Checks for users balance
      findBalance.clearParameters();
      findBalance.setString(1, userID);
      ResultSet r = findBalance.executeQuery();
      r.next();
      int balance = r.getInt("balance");
      r.close();
      int change = balance - totalPrice;

      if(change < 1) {
        return "User has only " + balance + " in account but itinerary costs " + totalPrice + "\n";
      } else {
        // If user has enough money, it updates  balance of user and updates paid to "1"
        updateBalance.clearParameters();
        updateBalance.setInt(1, change);
        updateBalance.setString(2, userID);
        updateBalance.executeUpdate();
        updatePaid.clearParameters();
        updatePaid.setInt(1, reservationId);
        updatePaid.executeUpdate();

        return "Paid reservation: " + reservationId + " remaining balance: " + change + "\n";
      }
    } catch (Exception e) {
      return "Failed to pay for reservation " + reservationId + "\n";
    }

  }

  /**
   * Implements the reservations function.
   *
   * @return If no user has logged in, then return "Cannot view reservations, not
   *         logged in\n" If the user has no reservations, then return "No
   *         reservations found\n" For all other errors, return "Failed to
   *         retrieve reservations\n"
   *
   *         Otherwise return the reservations in the following format:
   *
   *         Reservation [reservation ID] paid: [true or false]:\n" [flight 1
   *         under the reservation] [flight 2 under the reservation] Reservation
   *         [reservation ID] paid: [true or false]:\n" [flight 1 under the
   *         reservation] [flight 2 under the reservation] ...
   *
   *         Each flight should be printed using the same format as in the
   *         {@code Flight} class.
   *
   * @see Flight#toString()
   */
  public String transaction_reservations() {

    String s = "";
    try {
      if(!loginCheck){
        return "Cannot view reservations, not logged in\n";
      } 

      // Checks for reservations the user has
      reserve.clearParameters();
      reserve.setString(1, userID);
      ResultSet Result = reserve.executeQuery();
      if(!Result.next()){
        return "No reservations found\n";
      } 
      do{   
        
        // If found, it queries the databse for flight info to display to the user
        int fid = Result.getInt("fid_1");
        int fid2 = Result.getInt("fid_2");
        s = s + "Reservation " + Result.getInt("ID") + " paid: " + (Result.getInt("paid")==1) + ":\n";
        
        flights.clearParameters();
        flights.setInt(1, fid);
        ResultSet result = flights.executeQuery();
        result.next();
        Flight F1 = new Flight();
        F1.fid = result.getInt("fid");
        F1.dayOfMonth = result.getInt("day_of_month");
        F1.carrierId= result.getString("carrier_id");
        F1.flightNum = result.getString("flight_num");
        F1.originCity = result.getString("origin_city");
        F1.destCity = result.getString("dest_city");
        F1.time = result.getInt("actual_time");
        F1.capacity = result.getInt("capacity");
        F1.price = result.getInt("price");
       
        s = s + F1.toString() + "\n";

        // Account for the case if there is a flight 2
        if(fid2 != -1){
          flights.clearParameters();
          flights.setInt(1, fid2);
          ResultSet result2 = flights.executeQuery();
          result2.next();
          Flight F2 = new Flight();
          F2.fid = result.getInt("fid");
          F2.dayOfMonth = result.getInt("day_of_month");
          F2.carrierId= result.getString("carrier_id");
          F2.flightNum = result.getString("flight_num");
          F2.originCity = result.getString("origin_city");
          F2.destCity = result.getString("dest_city");
          F2.time = result.getInt("actual_time");
          F2.capacity = result.getInt("capacity");
          F2.price = result.getInt("price");        
          s = s + F2.toString() + "\n";
        }

        
      } while (Result.next());

      return s;
    } catch(Exception e) {
      return "Failed to retrieve reservations\n";
    }
  }

  /**
   * Implements the cancel operation.
   *
   * @param reservationId the reservation ID to cancel
   *
   * @return If no user has logged in, then return "Cannot cancel reservations,
   *         not logged in\n" For all other errors, return "Failed to cancel
   *         reservation [reservationId]\n"
   *
   *         If successful, return "Canceled reservation [reservationId]\n"
   *
   *         Even though a reservation has been canceled, its ID should not be
   *         reused by the system.
   */
  public String transaction_cancel(int reservationId) {
    try {
      if(!loginCheck) {
        return "Cannot cancel reservations, not logged in\n";
      }
      // Looks for flights with reservation ID
      checkCancel.clearParameters();
      checkCancel.setInt(1, reservationId);
      ResultSet result = checkCancel.executeQuery();
      if(!result.next()) {
        return "Failed to cancel reservation " + reservationId + "\n";
      }

      // Checks if the reservation was already canceled and also find price of flights in case of refund
      int canceled = result.getInt("canceled");
      int paid = result.getInt("paid");
      int fid1 = result.getInt("fid_1");
      int fid2 = result.getInt("fid_1");
      int price = 0;
      result.close();
      findPrice.clearParameters();
      findPrice.setInt(1, fid1);
      ResultSet f1 = findPrice.executeQuery();
      f1.next();
      price = price + f1.getInt("price");
      f1.close();

      // Accounts for flight 2
      if(fid2 != -1){
        findPrice.clearParameters();
        findPrice.setInt(1, fid2);
        ResultSet f2 = findPrice.executeQuery();
        f2.next();
        price = price + f2.getInt("price");
        f2.close();
      }
      

      // If the flight was not previuosly canceled, it updates to canceled and refunds the money (if user had paid)
      if(canceled == 0) {
        updateCancel.clearParameters();
        updateCancel.setInt(1, reservationId);
        updateCancel.executeUpdate();
        if(paid == 1) {
          findBalance.clearParameters();
          findBalance.setString(1, userID);
          ResultSet set = findBalance.executeQuery();
          set.next();
          int balance = set.getInt("balance");
          balance = balance + price;
          set.close();
          updateBalance.clearParameters();
          updateBalance.setInt(1, balance);
          updateBalance.setString(2, userID);
          updateBalance.executeUpdate();
        }
      }

      return "Canceled reservation " + reservationId + "\n";

    } catch (Exception e) {
      return "Failed to cancel reservation " + reservationId + "\n";
    }
    
  }

  /**
   * Example utility function that uses prepared statements
   */
  private int checkFlightCapacity(int fid) throws SQLException {
    checkFlightCapacityStatement.clearParameters();
    checkFlightCapacityStatement.setInt(1, fid);
    ResultSet results = checkFlightCapacityStatement.executeQuery();
    results.next();
    int capacity = results.getInt("capacity");
    results.close();

    return capacity;
  }

  /**
   * A class to store flight information.
   */
  class Flight {
    public int fid;
    public int dayOfMonth;
    public String carrierId;
    public String flightNum;
    public String originCity;
    public String destCity;
    public int time;
    public int capacity;
    public int price;

    @Override
    public String toString() {
      return "ID: " + fid + " Day: " + dayOfMonth + " Carrier: " + carrierId + " Number: " + flightNum + " Origin: "
          + originCity + " Dest: " + destCity + " Duration: " + time + " Capacity: " + capacity + " Price: " + price;
    }
  }



  // A class to store itineraries, along with a comparable method to implement sorting
  public class IT implements Comparable<IT>{
    public Flight F1;
    public Flight F2;
    public int total_time;

    public IT(Flight F1, Flight F2, int total_time){
      this.F1 = F1;
      this.F2 = F2;
      this.total_time = total_time;
    }

    public int compareTo(IT other) {
      if(total_time < other.total_time) {
        return -1;
      } else if (total_time > other.total_time) {
        return 1;
      } else if (F1.fid < other.F1.fid) {
        return -1;
      } else if (F1.fid > other.F1.fid) {
        return 1;
      } else if (F2.fid < F2.fid) {
        return -1;
      } else {
        return 1;
      }
    }
  }
}
