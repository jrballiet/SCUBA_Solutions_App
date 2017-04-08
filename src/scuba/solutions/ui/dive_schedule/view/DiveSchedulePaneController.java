
package scuba.solutions.ui.dive_schedule.view;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXTextField;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.ResourceBundle;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import scuba.solutions.database.DbConnection;
import scuba.solutions.emails.EmailAlert;
import scuba.solutions.ui.customers.model.Customer;
import scuba.solutions.ui.dive_schedule.model.DiveTrip;
import scuba.solutions.ui.reservations.model.Waiver;
import scuba.solutions.ui.reservations.model.Payment;
import scuba.solutions.ui.reservations.model.Reservation;
import scuba.solutions.ui.reservations.view.ReservationAddDialog_ExistingCustomerController;
import scuba.solutions.ui.reservations.view.ReservationAddDialog_NewCustomerController;
import scuba.solutions.ui.reservations.view.ReservationAddDialog_SearchCustomerController;
import scuba.solutions.ui.reservations.view.ReservationEditDialogController;
import scuba.solutions.util.AlertUtil;
import scuba.solutions.util.DateUtil;
import scuba.solutions.util.SQLUtil;


/**
 * FXML Controller class
 *
 * @author Jonathan Balliet, Samuel Brock
 * Bugs/TODO:
 * BIG ISSUE: Can add more than one reservation for a single person for a dive event - are also allowed to do this in the DB? 
 * BIG ISSUE: Need to possibly look at ways around security limitations (anti-virus) - causing program to freeze otherwise!
 * Need to make sure resultSets and statements are closed and try/catch statements with DB calls
 * Need to figure out confirmation for email being sent
 * Need to look into selections staying at same place in table view after updates
 */
public class DiveSchedulePaneController implements Initializable 
{
    private static Connection connection;    
    private final ObservableList<Reservation>  reservationData = FXCollections.observableArrayList();
    private final ObservableList<DiveTrip>  tripData = FXCollections.observableArrayList();    
    private FilteredList<DiveTrip> filteredTripData; 
    private Stage primaryStage;
    private Stage dialogStage;
    private String currentTab;
    @FXML
    private AnchorPane rootPane;
    @FXML
    private JFXButton homeButton;
    @FXML
    private JFXButton diveScheduleButton;
    @FXML
    private JFXButton customersButton;
    @FXML
    private JFXButton exitButton;
    @FXML
    private JFXTextField searchTextField;
    @FXML
    private JFXButton clearSearchButton;
    @FXML
    private TableView<Reservation> reservationsTable;
    @FXML
    private TableView<DiveTrip> tripTable;
    @FXML
    private TableColumn<DiveTrip, String> tripDateColumn;
    @FXML
    private TableColumn<DiveTrip, String> dayOfWeekColumn;
    @FXML
    private TableColumn<DiveTrip, LocalTime> departTimeColumn;
    @FXML
    private TableColumn<DiveTrip, String> availSeatsColumn;
    @FXML
    private TableColumn<DiveTrip, String> weatherStatusColumn;
    @FXML
    private TableColumn<Reservation, String> fullNameColumn;
    @FXML
    private Label firstNameLabel;
    @FXML
    private Label lastNameLabel;
    @FXML
    private Label waiverLabel;
    @FXML
    private Label paymentLabel;
    @FXML
    private JFXButton newReservationButton;
    @FXML
    private JFXButton newDiveButton;
    @FXML
    private JFXButton updateDiveButton;
    @FXML
    private JFXButton viewWeatherButton;
    @FXML
    private TableColumn<Reservation, String> statusColumn;
    @FXML
    private JFXButton updateReservationButton;
    


 

    /**
     * Initializes the controller class.
     */
    @Override
    public void initialize(URL url, ResourceBundle rb) 
    {
        primaryStage = DiveSchedulePane.getPrimaryStage();
        diveScheduleButton.setDisable(true);
        
        initializeTrip();
        initializeReservation();
        
        try
        {
            loadDiveTrips();
        }
        catch (FileNotFoundException e)
        {
        }
        catch (IOException e)
        {
        }
        catch (SQLException e)
        {
            AlertUtil.showDbErrorAlert("Error with loading Dive Trips to the Table", e);
        }
        
   
        initializeSearchField();

        tripTable.getSelectionModel().selectedItemProperty().addListener(
        (observable, oldValue, newValue) -> loadTripReservations(newValue));
        
        reservationsTable.getSelectionModel().selectedItemProperty().addListener(
        (observable, oldValue, newValue) -> showReservationStatusDetails(newValue));
        
        tripTable.getSelectionModel().selectedItemProperty().addListener((ObservableValue<? extends DiveTrip> observable, DiveTrip oldValue, DiveTrip newValue) -> {
            if(newValue != null && newValue.getWeatherStatus().equalsIgnoreCase("Cancelled"))
            {
                newReservationButton.setDisable(true);
                updateReservationButton.setDisable(true);
                
            }
            else
            {
                newReservationButton.setDisable(false);
                updateReservationButton.setDisable(false);
            }
            
            if( newValue != null && (newValue.getAvailSeats() == 0 || newValue.getWeatherStatus().equalsIgnoreCase("Cancelled")) )
            {
                newReservationButton.setDisable(true);
            }
            else
            {
                newReservationButton.setDisable(false);
            }
        });
        
        reservationsTable.getSelectionModel().selectedItemProperty().addListener((ObservableValue<? extends Reservation> observable, Reservation oldValue, Reservation newValue) ->
        {
            try
            {
                if(newValue.getDiveTrip().getAvailSeats() == 0 || newValue.getDiveTrip().getWeatherStatus().equalsIgnoreCase("Cancelled"))
                {
                    updateReservationButton.setDisable(true);

                }
                else if((newValue.getDiveTrip().getAvailSeats() == 0 && newValue.getStatus().equalsIgnoreCase("Pending")))
                {
                    updateReservationButton.setDisable(true);
                }
                else
                {
                    updateReservationButton.setDisable(false);
                }
            }
            catch(NullPointerException e)
            {

            }
            
        });
    
    
  
}
    
    public void loadDiveTrips() throws FileNotFoundException, IOException, SQLException
    {
    	tripData.clear();
    	
    	connection = DbConnection.accessDbConnection().getConnection();
		
    	String query = "SELECT * FROM dive_trip";
		
    	Statement statement = null;
    	ResultSet result = null;
    
    	try
    	{
            statement = connection.createStatement();
            result = statement.executeQuery(query);
            
            while(result.next())
            {
                int tripID = result.getInt(1);
                DiveTrip trip = new DiveTrip(tripID);
                
                trip.setTripDate((result.getDate(2)).toLocalDate());
                trip.setAvailSeats(result.getInt(3));
                String strTime = result.getString(4);
                LocalTime time = SQLUtil.intervalToLocalTime(strTime);
                
                trip.setDepartTime(time);
                trip.setWeatherStatus(result.getString(5));
                trip.setDayOfWeek(trip.determineDayOfWeek());
				
                tripData.add(trip);
            }
    	}
    	catch (SQLException e)
    	{
            AlertUtil.showDbErrorAlert("Error with selecting and adding Dive Trips", e);
    	}
        
    	finally
    	{
            try
            {
            	if (statement != null)
            	{
                    statement.close();
            	}
                if (result != null)
                {
                    result.close();
                }
            }
            catch (SQLException e)
            {
            	AlertUtil.showDbErrorAlert("Error with DB Connection", e);
            }
            
    	}
        
        tripTable.setItems(tripData);
        
        initializeSearchField();
    }
    
    // Retrieves customers in dive trip    
    private void loadTripReservations(DiveTrip selectedTrip)
    { 
       int tripId = 0;
       if(selectedTrip != null && selectedTrip.getAvailSeats() == 0){
           newReservationButton.setDisable(true);
       }
       
        reservationData.clear();

       if (selectedTrip != null) 
       {
            tripId = selectedTrip.getTripId();
       }
               
        PreparedStatement preSt = null;           
        ResultSet result = null;          
        PreparedStatement statement = null;
        ResultSet resultSet = null; 

        try
        {
            preSt = connection.prepareStatement("SELECT * FROM RESERVATION WHERE TRIP_ID=?");

            preSt.setInt(1, tripId);

            result = preSt.executeQuery();

           while(result.next())
           {
               int restID = result.getInt(1);
               int custID = result.getInt(2);
               int diveID = result.getInt(3);
               String status = result.getString(4);

               statement = connection.prepareStatement("SELECT FIRST_NAME, LAST_NAME FROM CUSTOMER WHERE CUST_ID=?");

               statement.setInt(1, custID);

               resultSet = statement.executeQuery();
               resultSet.next();

               String firstName = resultSet.getString(1);
               String lastName = resultSet.getString(2);
               
               LocalDate diveDate = null;
               
               statement = connection.prepareStatement("SELECT TRIP_DATE, DEPARTURE_TIME FROM DIVE_TRIP WHERE TRIP_ID=?");

               statement.setInt(1, diveID);

               resultSet = statement.executeQuery();
               resultSet.next();

               if(resultSet.getDate(1) != null)
                diveDate = resultSet.getDate(1).toLocalDate();
               
               String strTime = resultSet.getString(2);
               LocalTime time = SQLUtil.intervalToLocalTime(strTime);
               
               

               Customer customer = new Customer(custID, firstName, lastName);
               DiveTrip diveTrip = new DiveTrip(diveID, diveDate, time);
               Reservation res = new Reservation(restID);
               res.setCustomer(customer);
               res.setDiveTripId(diveID);
               res.setDriveTrip(diveTrip);
               res.setStatus(status);

               reservationData.add(res);
           }
        }
        catch (SQLException e)
        {
        }
        finally
        {
            try
            {
               if (statement != null)
                           statement.close();

               if(preSt != null)
                   preSt.close();

               if(resultSet != null)
                   resultSet.close();

               if(result != null)
                   result.close();

               } catch (SQLException e) {
                   Logger.getLogger(DiveSchedulePaneController.class.getName()).log(Level.SEVERE, null, e);
               }
               }

       reservationsTable.setItems(reservationData);   
    }

    
    // Retrieves customers in dive trip    
 private void showReservationStatusDetails(Reservation reservation)
    {
        int reservationId = 0;
        Waiver waiver = null;
        Payment payment = null;
        
        if(reservation != null) 
        {
            reservationId = reservation.getReservationId();        
            waiver = reservation.getWaiver();
            payment = reservation.getPayment();
        }
        
        PreparedStatement statement = null;
        ResultSet resultSet = null; 

        try 
        {

            statement = connection.prepareStatement("SELECT * FROM WAIVER WHERE RESERVATION_ID=?");            
            statement.setInt(1, reservationId);            
            resultSet = statement.executeQuery();

            if(resultSet.next()) 
            {
                waiver.setWaiverStatus(resultSet.getString(2));
                
                if(resultSet.getDate(3) != null)
                    waiver.setDateSigned(resultSet.getDate(3).toLocalDate());
                
                waiver.setERFirst(resultSet.getString(4));
                waiver.setERLast(resultSet.getString(5));
                waiver.setERPhone(resultSet.getString(6));
            }

            statement = connection.prepareStatement("SELECT * FROM PAYMENT WHERE RESERVATION_ID=?");            
            statement.setInt(1, reservationId);            
            resultSet = statement.executeQuery();            

            if(resultSet.next()) 
            {            
                payment.setReservationId(reservationId);
                payment.setPaymentStatus(resultSet.getString(2));
                payment.setCCConfirmNo(resultSet.getInt(3));
                
                if(resultSet.getDate(4) != null)
                    payment.setDateProcessed(resultSet.getDate(4).toLocalDate());
                
                payment.setAmount(resultSet.getInt(5));
            }

        } 
        catch (SQLException | NullPointerException e)
        {
        }
        finally
        {
            try
            {
                if (statement != null)
                {
                    statement.close();
                }
                
                resultSet.close();
            }
            catch (SQLException e)
            {
                Logger.getLogger(DiveSchedulePaneController.class.getName()).log(Level.SEVERE, null, e);
            }
            catch(NullPointerException e)
            {
            }
        }

        if (reservation != null) 	
        {
            firstNameLabel.setText(reservation.getCustomer().getFirstName());
            lastNameLabel.setText(reservation.getCustomer().getLastName());
            waiverLabel.setText(waiver.getWaiverStatus());
            paymentLabel.setText(payment.getPaymentStatus());
        } 
        else 
        {
            firstNameLabel.setText("");
            lastNameLabel.setText("");
            waiverLabel.setText("");
            paymentLabel.setText("");
        }
    }
   
   public void initializeTrip()
   {
    	tripDateColumn.setCellValueFactory(cellData -> cellData.getValue().tripDateProperty());
        dayOfWeekColumn.setCellValueFactory(cellData -> cellData.getValue().dayOfWeekProperty());
        departTimeColumn.setCellValueFactory(cellData -> cellData.getValue().departTimeProperty());
        availSeatsColumn.setCellValueFactory(cellData -> cellData.getValue().availSeatsProperty());
        weatherStatusColumn.setCellValueFactory(cellData -> cellData.getValue().weatherStatusProperty()); 
        tripDateColumn.setComparator(new DateComparator());
    }
   
   public void initializeReservation()
   {
       fullNameColumn.setCellValueFactory(cellData -> cellData.getValue().getCustomer().fullNameProperty());
       statusColumn.setCellValueFactory(cellData -> cellData.getValue().statusProperty());
   }
   
   public boolean showDiveEditDialog(DiveTrip trip) 
   {
        try
        {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(DiveSchedulePaneController.class.getResource("DiveEditDialog.fxml"));
            Parent root = loader.load();

            dialogStage = new Stage();
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(primaryStage);
            Scene scene = new Scene(root);
            dialogStage.setScene(scene);

            DiveEditDialogController controller = loader.getController();
            controller.setDialogStage(dialogStage);
            controller.setDive(trip);

            dialogStage.showAndWait();
            return controller.isOkClicked(); 
        }
        catch (IOException e) 
    	{
    	    return false;
    	}	
   }
   
   
   @FXML
   public void updateTrip() throws FileNotFoundException, IOException, SQLException
   {
        updateDiveButton.setDisable(true);
        DiveTrip selectedTrip = (DiveTrip) tripTable.getSelectionModel().getSelectedItem();

        if (selectedTrip != null)
        {
            boolean okClicked = false;
    	    okClicked = showDiveEditDialog(selectedTrip);
            
            if (okClicked) 
            {
                int trId = selectedTrip.getTripId();

                LocalDate tripDate = selectedTrip.getTripDate();
                int availSeat = selectedTrip.getAvailSeats();
                LocalTime departTime = selectedTrip.getDepartTime();
                String time = SQLUtil.localTimeToInterval(departTime);
                String tripStatus = selectedTrip.getWeatherStatus();
               

                PreparedStatement preSt;

                try 
                {
                    preSt = connection.prepareStatement("UPDATE DIVE_TRIP SET trip_date=?," +
                                    "available_seats=?, departure_time=?, dive_status=?" +
                                    "WHERE TRIP_ID=?");

                    preSt.setDate(1, Date.valueOf(tripDate));
                    preSt.setInt(2, availSeat);
                    preSt.setString(3, time);
                    preSt.setString(4, tripStatus);
                    preSt.setInt(5, trId);

                    if (preSt.executeUpdate() >= 0)  
                    {
                        AlertUtil.showDbSavedAlert("Dive Trip has successfully been Updated in the database.");
                    }
                } 
                catch (SQLException e) 
                {
                    AlertUtil.showDbErrorAlert("Error Occured with the Update of the Dive Trip", e);
                }
                finally
                {
                    updateDiveButton.setDisable(false);
                }
               
                loadDiveTrips();
            
            }
        } 
        else 
        {
            // If nothing is selected a warning message will pop-up
            AlertUtil.noSelectionAlert("No Dive Trip Selected", "Please Select A Dive Trip in the Table to Update.");
           
        }
        updateDiveButton.setDisable(false);
   }
   
   
   public boolean showDiveAddDialog(DiveTrip trip) 
   {
        try
        {	
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(DiveSchedulePaneController.class.getResource("DiveAddDialog.fxml"));
            Parent root = loader.load();

            dialogStage = new Stage();
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(primaryStage);
            Scene scene = new Scene(root);
            dialogStage.setScene(scene);

            DiveAddDialogController controller = loader.getController();
            controller.setDialogStage(dialogStage);
            controller.setDive(trip);

            dialogStage.showAndWait();
            return controller.isOkClicked();
        }
	catch (IOException e) 
	{
            return false;
	}
   }
   
   @FXML
   public void addTrip() throws FileNotFoundException, IOException, SQLException
   {
       newDiveButton.setDisable(true);
       DiveTrip trip = new DiveTrip();
       boolean okClicked = showDiveAddDialog(trip);
       currentTab = DiveAddDialogController.getTab();
       try 
       {
            if (okClicked && currentTab.equalsIgnoreCase("singleDiveTab"))
            {
            LocalDate tripDate = trip.getTripDate();
            LocalTime departTime = trip.getDepartTime();
            String time = SQLUtil.localTimeToInterval(departTime);
            PreparedStatement preSt;

            
                preSt = connection.prepareStatement("INSERT INTO DIVE_TRIP "
                + "(trip_date, departure_time)"
                + " values(?, ?)");

                preSt.setDate(1, Date.valueOf(tripDate));
                preSt.setString(2, time);

                if (preSt.executeUpdate() >= 0)  
                {
                    AlertUtil.showDbSavedAlert("Dive Trip has successfully been added to the database.");
                }
            }
        }
        catch (SQLException e) 
        {
                AlertUtil.showDbErrorAlert("Error Occured with the Addition of the Dive Trip.", e);
                
            }
            catch (Exception e)
            {
                
            }
            finally
            {
                newDiveButton.setDisable(false);
            }

            loadDiveTrips();
                  
       try
       {
            if (okClicked && currentTab.equalsIgnoreCase("recurringDiveTab"))
            {
                 LinkedList<DiveTrip> trips = DiveAddDialogController.getRecurringTrips();

                 PreparedStatement preSt = connection.prepareStatement("INSERT INTO DIVE_TRIP "
                                 + "(trip_date, departure_time)"
                                 + " values(?, ?)");

                for(DiveTrip diveTrip: trips)
                {
                    LocalDate tripDate = diveTrip.getTripDate();
                    LocalTime departTime = diveTrip.getDepartTime();
                    String time = SQLUtil.localTimeToInterval(departTime);

                    preSt.setDate(1, Date.valueOf(tripDate));
                    preSt.setString(2, time);

                    preSt.addBatch(); 
                }

                int result[] = preSt.executeBatch();

                //if (result[0] == -2)
                //{
                AlertUtil.showDbSavedAlert("Recurring Dive Trips have successfully been added to the database.");
                //}
            }
        }        
        catch (SQLException e) 
        {
            AlertUtil.showDbErrorAlert("Error Occured with the Additon of the Recurring Dive Trips.", e);
        }
        catch (Exception e)
        {

        }
        finally
        {
            newDiveButton.setDisable(false);
        }
            
        loadDiveTrips();
   }
   
    public boolean showReservationEditDialog(Reservation reservation) {
       try
    	{
    	   FXMLLoader loader = new FXMLLoader();
    	   loader.setLocation(DiveSchedulePaneController.class.getResource("/scuba/solutions/ui/reservations/view/ReservationEditDialog.fxml"));
    	   Parent root = loader.load();

    	   dialogStage = new Stage();
    	   dialogStage.initModality(Modality.WINDOW_MODAL);
    	   dialogStage.initOwner(primaryStage);
    	   Scene scene = new Scene(root);
    	   dialogStage.setScene(scene);

    	   ReservationEditDialogController controller = loader.getController();
    	   controller.setDialogStage(dialogStage);
    	   controller.setWaiver(reservation.getWaiver());
           controller.setPayment(reservation.getPayment());

    	   dialogStage.showAndWait();

    	   return controller.isOkClicked();
    	   
        }
       catch (IOException e) 
       {
    	   return false;
       }
   }
   
      /**
    * Updates customer reservation in WAIVER and PAYMENT tables based on
    * user entry.  Calls bookReservation() method
    * 
    * @throws FileNotFoundException
    * @throws IOException
    * @throws SQLException 
    */
    @FXML
    public void updateReservation() throws FileNotFoundException, IOException, SQLException {
        
        updateReservationButton.setDisable(true);
    	 Reservation selectedReservation = (Reservation) reservationsTable.getSelectionModel().getSelectedItem();
         Waiver waiver = null;
         Payment payment = null;
         
         
         if (selectedReservation != null)
         {
            if(selectedReservation.getWaiver() != null)           
                waiver = selectedReservation.getWaiver();
         
            if(selectedReservation.getPayment() != null)
                payment = selectedReservation.getPayment();
             
            boolean okClicked = showReservationEditDialog(selectedReservation);
            
            if (okClicked) 
            {            	 
                int reservationId = selectedReservation.getReservationId();
                int preSt1Result = 0;
                int preSt2Result = 0;
                
                if(waiver != null &&  waiver.isComplete()) {
                    LocalDate dateSigned = waiver.getDateSigned();
                    String erLast = waiver.getERLast();
                    String erFirst = waiver.getERFirst();
                    String erPhone = waiver.getERPhone();
                
                    PreparedStatement preSt1 = connection.prepareStatement("UPDATE WAIVER SET date_signed=?, er_first=?, er_last=?,"+
                            "er_phone=? WHERE reservation_id=?");
          
                    preSt1.setDate(1, Date.valueOf(dateSigned));
                    preSt1.setString(2, erFirst);
                    preSt1.setString(3, erLast);
                    preSt1.setString(4, erPhone);
                    preSt1.setInt(5, reservationId);
                    
                    preSt1Result = preSt1.executeUpdate();
                }
                
                if(payment != null && payment.isComplete()) {
                    int ccConfirmNo = payment.getCCConfirmNo();
                    LocalDate dateProc = payment.getDateProcessed();
                    int amount = payment.getAmount();
          
                    PreparedStatement preSt2 = connection.prepareStatement("UPDATE PAYMENT SET cc_confirm_no=?, date_processed=?, amount=?"+
                             "WHERE reservation_id=?");
          
                    preSt2.setInt(1, ccConfirmNo);
                    preSt2.setDate(2, Date.valueOf(dateProc));
                    preSt2.setInt(3, amount);
                    preSt2.setInt(4, reservationId);
                    
                    preSt2Result = preSt2.executeUpdate();
                }
                
                if (preSt1Result ==  1 || preSt2Result == 1) {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setHeaderText("Saved!");
                    alert.setContentText("Reservation has successfully been updated in the database.");
                    alert.showAndWait();
                } else {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setHeaderText(null);
                    alert.setContentText("Error Occured with update of reservation.");
                    alert.showAndWait();
                }
                
                
                bookReservation(selectedReservation);
                showReservationStatusDetails(selectedReservation);
             }
         } 
         else 
         {
             Alert alert = new Alert(Alert.AlertType.WARNING);
             alert.initOwner(DiveSchedulePane.getPrimaryStage());
             alert.setTitle("No Selection");
             alert.setHeaderText("No Customer Selected");
             alert.setContentText("Please select a customer in the table to update.");

             alert.showAndWait();
         }
         
         updateReservationButton.setDisable(false);
    }
    
     /**
     * Updates RESERVATION, WAIVER, and PAYMENT tables to indicate completeness
     * of data entries
     * 
     * @param selectedReservation
     * @throws FileNotFoundException
     * @throws IOException
     * @throws SQLException 
     */
    public void bookReservation(Reservation selectedReservation) throws FileNotFoundException, IOException, SQLException {
        int reservationId = selectedReservation.getReservationId();
        Waiver waiver = selectedReservation.getWaiver();
        Payment payment = selectedReservation.getPayment();
        
        String update = null;		
        Statement statement = connection.createStatement();
        
        if(waiver.isComplete()) 
        {
            statement.executeUpdate("UPDATE WAIVER SET waiver_status='COMPLETE' WHERE reservation_id=" + reservationId);
        } 
        else 
        {
           statement.executeUpdate("UPDATE WAIVER SET waiver_status='INCOMPLETE' WHERE reservation_id=" + reservationId);
        }
        
        if(payment.isComplete()) 
        {
            statement.executeUpdate("UPDATE PAYMENT SET payment_status='PAID' WHERE reservation_id=" +  + reservationId);
        } 
        else 
        {
            statement.executeUpdate("UPDATE PAYMENT SET payment_status='UNPAID' WHERE reservation_id=" +  + reservationId);
        }     
        
        ResultSet resultSet = null;
        int availSeats;
        
        //resultSet = statement.executeQuery("SELECT trip_id FROM RESERVATION WHERE reservation_id=" + reservationId);
        //resultSet.next();
            
        int tripId = selectedReservation.getDiveTripId();
            
        resultSet = statement.executeQuery("SELECT available_seats FROM DIVE_TRIP WHERE trip_id=" + tripId);
        resultSet.next();
        availSeats = resultSet.getInt(1);
        
        if(waiver.isComplete() && payment.isComplete()) 
        {
            if (selectedReservation.getStatus() == null || selectedReservation.getStatus().equalsIgnoreCase("Pending"))
            {
                update = "UPDATE RESERVATION SET reservation_status='BOOKED' WHERE reservation_id=" + reservationId;            
                statement.executeUpdate(update);
                
                Executors.newSingleThreadExecutor().execute(() -> {
                    try {
                        EmailAlert.sendConfirmationEmail(selectedReservation);
                    } catch (IOException e) {
                    }
                });
                
               AlertUtil.showEmailSent("Reservation Email Confirmation Successfully sent to " + selectedReservation.getCustomer().getFullName() + ".");
                availSeats--;
            }
            
        } 
        else 
        {
            String reservationStatus = selectedReservation.getStatus();
            
            if(reservationStatus == null || reservationStatus.equalsIgnoreCase("Booked")) 
            {
                statement.executeUpdate("UPDATE RESERVATION SET reservation_status='Pending' WHERE reservation_id=" + reservationId);
                
                if(reservationStatus != null && reservationStatus.equalsIgnoreCase("Booked"))
                    availSeats++;
            }
        }
        
        PreparedStatement preSt = 
               connection.prepareStatement("UPDATE DIVE_TRIP SET available_seats=? WHERE trip_id=?");
        preSt.setInt(1, availSeats);
        preSt.setInt(2, tripId);
        preSt.executeUpdate();
        
        loadDiveTrips();
        loadTripReservations(selectedReservation.getDiveTrip());
        
        statement.close();
        preSt.close();
        resultSet.close();
    }
   
   
    @FXML
    private void clearSearch(ActionEvent event) 
    {
        searchTextField.clear();
    }
    
    public void initializeSearchField()
    {
        	filteredTripData = new FilteredList<>(tripData, p -> true);

        // Sets the search filter Predicate whenever the search values change.
        searchTextField.textProperty().addListener((ObservableValue<? extends String> observable, String oldValue, String newValue) -> {
            filteredTripData.setPredicate(trip -> {
                // If the search field text is empty, display all customers in the table
        if (newValue == null || newValue.isEmpty()) {
                    return true;
        }

        // Compares the name, id, and date of birth of every customer with the search text
        String lowerCaseFilter = newValue.toLowerCase();
       
        if (DateUtil.format(trip.getTripDate()).contains(lowerCaseFilter)) {
            return true; // Search matches date of trip
        } else if (trip.getDepartTime().toString().contains(lowerCaseFilter)) {
            return true; // Search matches depart time
        } else if (trip.getDayOfWeek().toLowerCase().contains(lowerCaseFilter)) {
            return true;  // Search matches day of week.
        }
        return false; // Search does not match any data.
            });
        });
         SortedList<DiveTrip> sortedData = new SortedList<>(filteredTripData);

        //  Bind the SortedList comparator to the TableView comparator.
        sortedData.comparatorProperty().bind(tripTable.comparatorProperty());

        //  Add sorted (and filtered) data to the table.
        tripTable.setItems(sortedData);
       
    }
    
    @FXML
    public void transitionToCustomers() throws IOException
    {
        
        Stage stage = (Stage) rootPane.getScene().getWindow();
        FXMLLoader loader = new FXMLLoader();
        loader.setLocation(getClass().getResource("/scuba/solutions/ui/customers/view/CustomerPane.fxml"));
        Parent root = loader.load();
        stage.setScene(new Scene(root));
        stage.show( );
    }
    
    public boolean showReservationAddDialog_Search() 
    {
        try
        {
            // Load the fxml file and create a new stage for the popup dialog.
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(DiveSchedulePaneController.class.getResource("/scuba/solutions/ui/reservations/view/ReservationAddDialog_SearchCustomer.fxml"));
            Parent root = loader.load();

            // Create the dialog Stage.
            dialogStage = new Stage();
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(primaryStage);
            Scene scene = new Scene(root);
            dialogStage.setScene(scene);

            // Set the customer into the controller.
            ReservationAddDialog_SearchCustomerController controller = loader.getController();
            controller.setDialogStage(dialogStage);

            // Show the dialog and wait until the user closes it
            dialogStage.showAndWait();

            return controller.isProceedClicked(); 
        }
        catch (IOException e) 
    	{
    	    return false;
    	}	
   }
    
    public boolean showReservationAddDialog_NewCustomer(Customer customer) 
    {
        try
        {
            // Load the fxml file and create a new stage for the popup dialog.
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(DiveSchedulePaneController.class.getResource("/scuba/solutions/ui/reservations/view/ReservationAddDialog_NewCustomer.fxml"));
            Parent root = loader.load();

            // Create the dialog Stage.
            dialogStage = new Stage();
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(primaryStage);
            Scene scene = new Scene(root);
            dialogStage.setScene(scene);

            // Set the customer into the controller.
            ReservationAddDialog_NewCustomerController controller = loader.getController();
            controller.setDialogStage(dialogStage);
    	    controller.setCustomer(customer);

            // Show the dialog and wait until the user closes it
            dialogStage.showAndWait();

            return controller.isOkClicked(); 
        }
        catch (IOException e) 
    	{
            e.printStackTrace();
    	    return false;
    	}	
   }
    
    public boolean showReservationAddDialog_ExistingCustomer(Customer customer) 
    {
        try
        {
            // Load the fxml file and create a new stage for the popup dialog.
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(DiveSchedulePaneController.class.getResource("/scuba/solutions/ui/reservations/view/ReservationAddDialog_ExistingCustomer.fxml"));
            Parent root = loader.load();

            // Create the dialog Stage.
            dialogStage = new Stage();
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(primaryStage);
            Scene scene = new Scene(root);
            dialogStage.setScene(scene);

            // Set the customer into the controller.
            ReservationAddDialog_ExistingCustomerController controller = loader.getController();
            controller.setDialogStage(dialogStage);
    	    controller.setCustomer(customer);

            // Show the dialog and wait until the user closes it
            dialogStage.showAndWait();

            return controller.isOkClicked(); 
        }
        catch (IOException e) 
    	{
    	    return false;
    	}	
   }
        @FXML
    private void addReservation(ActionEvent event) throws IOException, SQLException, CloneNotSupportedException 
    {
        DiveTrip selectedTrip = (DiveTrip) tripTable.getSelectionModel().getSelectedItem();
        newReservationButton.setDisable(true);
         if (selectedTrip != null)
         {
            try
            {
                
            
                boolean isProceedClicked = showReservationAddDialog_Search();
                boolean isCustomerFound = ReservationAddDialog_SearchCustomerController.returnIsCustomerFound();
                if(isProceedClicked && isCustomerFound)
                {
                    Customer customer = ReservationAddDialog_SearchCustomerController.returnCustomer();
                    Customer selectedCustomer = new Customer(customer);
                   
                    boolean confirmClicked = showReservationAddDialog_ExistingCustomer(selectedCustomer);
                    if(confirmClicked)
                    {
  
                        if(!selectedCustomer.equals(customer))
                        {
                             Customer.updateCustomer(selectedCustomer);
                        }

                        int custId = Customer.getCustId(selectedCustomer);

                        if(Reservation.addReservation(custId, selectedTrip.getTripId()) > 0)
                        {
                            AlertUtil.showDbSavedAlert("New Reservation for " + selectedCustomer.getFullName() + " succesfully saved.");
                            int reservationId = Reservation.getReservationId(custId, selectedTrip.getTripId());
                            Waiver.addWaiver(reservationId);
                            Payment.addPayment(reservationId);
                            loadDiveTrips();
                            loadTripReservations(selectedTrip);
                            Executors.newSingleThreadExecutor().execute(() -> {
                                try {
                                    EmailAlert.sendRequestEmail(reservationId, selectedCustomer, selectedTrip);
                                } catch (IOException | SQLException | InterruptedException e){
                                     AlertUtil.showErrorAlert("Email Error!", e);
                                }
                            });
                            AlertUtil.showEmailSent("Reservation Email Request Successfully sent to " + selectedCustomer.getFullName() + ".");
                        }

                    }
                } 
                else if(isProceedClicked && !isCustomerFound)
                {
                    Customer customer = new Customer();
                    boolean confirmClicked = showReservationAddDialog_NewCustomer(customer);
                    if(confirmClicked)
                    {

                        Customer.addCustomer(customer);

                        int custId = Customer.getCustId(customer);
                       

                        if(Reservation.addReservation(custId, selectedTrip.getTripId()) > 0)
                        {
                            AlertUtil.showDbSavedAlert("New Reservation for " + customer.getFullName() + " succesfully saved.");
                            int reservationId = Reservation.getReservationId(custId, selectedTrip.getTripId());
                            Waiver.addWaiver(reservationId);
                            Payment.addPayment(reservationId);
                            loadDiveTrips();
                            loadTripReservations(selectedTrip);
                            Executors.newSingleThreadExecutor().execute(() -> {
                                try {
                                    EmailAlert.sendRequestEmail(reservationId, customer, selectedTrip);
                                } catch (IOException | SQLException | InterruptedException e){
                                }
                            });
                            AlertUtil.showEmailSent("Reservation Email Request Successfully sent to " + customer.getFullName() + ".");
                        }

                    }
                   // newReservationButton.setDisable(true);

                }
            }
            catch(SQLException e)
            {
                 AlertUtil.showDbErrorAlert("Error with adding new Reservation", e);
            }
         }
         else
         {
             AlertUtil.noSelectionAlert("No Dive Trip Selected", "Please select a dive trip in the table for the new reservation.");
         }
         newReservationButton.setDisable(false);
         
    } 
    
    
    @FXML
    public void transitionToHome(ActionEvent event) throws IOException 
    {
                Stage stage = (Stage) rootPane.getScene().getWindow();
        FXMLLoader loader = new FXMLLoader();
	    loader.setLocation(getClass().getResource("/scuba/solutions/ui/home/view/HomePane.fxml"));
	    Parent root = loader.load();
        //Stage stage = new Stage();
        stage.setScene(new Scene(root));
        stage.show( );
    }
    
        @FXML
    private void transitionToRecords(ActionEvent event) throws IOException 
    {
                        Stage stage = (Stage) rootPane.getScene().getWindow();
        FXMLLoader loader = new FXMLLoader();
	    loader.setLocation(getClass().getResource("/scuba/solutions/ui/records/view/RecordsPane.fxml"));
	    Parent root = loader.load();
        //Stage stage = new Stage();
        stage.setScene(new Scene(root));
        stage.show( );
    }
    
    @FXML
    public void exitProgram(ActionEvent event)
    {
        Stage stage = (Stage) rootPane.getScene().getWindow();
        stage.close();
    }
    
    public class DateComparator implements Comparator<String>
    {
        @Override
        public int compare(String str1, String str2)
        {
            LocalDate dateStr1 = DateUtil.parse(str1);
            LocalDate dateStr2 = DateUtil.parse(str2);
            
          
            return dateStr1.compareTo(dateStr2);
        }
    }

}