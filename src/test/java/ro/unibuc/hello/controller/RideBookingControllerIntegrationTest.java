// integration between two components: `GreetingsService` and `GreetingsController` (the requests are made using mockMvc)
// Annotation to specify that this class contains integration tests for Spring Boot application.

package ro.unibuc.hello.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import ro.unibuc.hello.dto.rideBooking.RideBookingRequestDTO;
import ro.unibuc.hello.enums.RideBookingStatus;
import ro.unibuc.hello.enums.RideStatus;
import ro.unibuc.hello.model.Ride;
import ro.unibuc.hello.model.RideBooking;
import ro.unibuc.hello.model.User;
import ro.unibuc.hello.repository.RideBookingRepository;
import ro.unibuc.hello.repository.RideRepository;
import ro.unibuc.hello.repository.UserRepository;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import ro.unibuc.hello.service.RideBookingService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
// Annotation to automatically configure a MockMvc instance.
@AutoConfigureMockMvc
// Annotation to enable Testcontainers support.
@Testcontainers
// Tagging this test class as an integration test for categorization.
@Tag("IntegrationTest")
public class RideBookingControllerIntegrationTest {

    // Declaring a Testcontainers MongoDB container.
    @Container
    public static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:6.0.20")
            .withExposedPorts(27017)
            .withSharding();
            

    // Method executed once before all tests to start the MongoDB container.
    @BeforeAll
    public static void setUp() {
        mongoDBContainer.start();
    }

    // Method executed once after all tests to stop the MongoDB container.
    @AfterAll
    public static void tearDown() {
        mongoDBContainer.stop();
    }
    

    // Method to dynamically set MongoDB connection properties for the test environment.
    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        final String MONGO_URL = "mongodb://localhost:";
        final String PORT = String.valueOf(mongoDBContainer.getMappedPort(27017));

        registry.add("mongodb.connection.url", () -> MONGO_URL + PORT);
    }

    // Autowiring MockMvc instance for HTTP request simulation.
    @Autowired
    private MockMvc mockMvc;

    // Autowiring GreetingsService for interacting with the MongoDB database.
    @Autowired
    private RideBookingService rideBookingService;

    @Autowired
    private RideRepository rideRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RideBookingRepository rideBookingRepository;

    @Autowired
    private ObjectMapper objectMapper;
    // Method executed before each test to clean up and add test data to the database.
    @BeforeEach
    public void cleanUpAndAddTestData() {

        rideBookingRepository.deleteAll();
        userRepository.deleteAll();
        rideRepository.deleteAll();

        User user1 = new User("diaconescu", "alexandra", "alexandra@gmail.com", "0721226744", null);
        userRepository.save(user1);

        User user2 = new User("marinescu", "alexandra", "marinescu@gmail.com", "0721226724", null);
        userRepository.save(user2);

        User freeUser = new User("new", "user", "free@example.com", "0712345678", null);
        userRepository.save(freeUser); 

        Instant createdAt = Instant.parse("2025-03-31T15:52:00Z");
        RideBooking rideBooking1 = new RideBooking("r1", user1.getId(), createdAt);
        rideBooking1.setRideBookingStatus(RideBookingStatus.BOOKED);
        rideBookingRepository.save(rideBooking1);

        RideBooking rideBooking2 = new RideBooking("r1", user2.getId(), createdAt);
        rideBooking2.setRideBookingStatus(RideBookingStatus.BOOKED);
        rideBookingRepository.save(rideBooking2);

        Ride ride = new Ride("driver1", "startLocation", "endLocation", Instant.now(), Instant.now().plusSeconds(3600), 20, 3, "XYZ123");
        ride.setStatus(RideStatus.SCHEDULED);
        rideRepository.save(ride); 

        //cancel method
        User cancelUser = new User("test", "user", "test@example.com", "0700000000", null);
        cancelUser.setId("p4");
        userRepository.save(cancelUser);
    
        // Ensure the ride starts in the future
        Instant fixedNow = Instant.parse("2025-04-01T21:54:00Z");  // Current time
        Instant departureTime = Instant.parse("2025-04-01T20:00:00Z");  
        Instant arrivalTime = Instant.parse("2025-04-01T21:00:00Z"); 

        // Create a mock Clock to return the fixed current time
        Clock fixedClock = Clock.fixed(fixedNow, ZoneId.of("UTC"));
    
        // Create a ride that has not yet started
        Ride testRide = new Ride("driver123", "Start", "End", departureTime, arrivalTime, 10, 3, "XYZ123");
        testRide.setId("r3"); 
        rideRepository.save(testRide);
    
        // Book a ride for the test user
        RideBooking rideBooking = new RideBooking("r3", "p4", Instant.parse("2025-04-01T21:39:58Z"));
        rideBooking.setRideBookingStatus(RideBookingStatus.BOOKED);
        rideBookingRepository.save(rideBooking);

    }

    // Integration test to verify the endpoint for retrieving passengers by ride id.
    @Test
    public void testGetPassengerByRideId() throws Exception {
        mockMvc.perform(get("/bookings/r1/passengers"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].rideId").value("r1"))
            .andExpect(jsonPath("$[0].passengerFullName").value("diaconescu alexandra"))
            .andExpect(jsonPath("$[0].rideBookingStatus").value("BOOKED"))
            .andExpect(jsonPath("$[1].rideId").value("r1"))
            .andExpect(jsonPath("$[1].passengerFullName").value("marinescu alexandra"))
            .andExpect(jsonPath("$[1].rideBookingStatus").value("BOOKED"));
    }

    @Test
    public void testCreateRideBooking() throws Exception {
        Ride ride = rideRepository.findAll().get(0);
        User freeUser = userRepository.findAll().get(2);  // Get the free user)

        Instant createdAt = Instant.parse("2025-03-31T15:52:00Z");
        RideBookingRequestDTO rideBookingRequestDTO = new RideBookingRequestDTO();
        rideBookingRequestDTO.setRideId(ride.getId());  
        rideBookingRequestDTO.setPassengerId(freeUser.getId()); 
        rideBookingRequestDTO.setCreatedAt(createdAt);

        mockMvc.perform(post("/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(rideBookingRequestDTO)))
            .andExpect(status().isCreated())  
            .andExpect(content().string(""));
    }

    @Test
    public void testCancelRideBooking() throws Exception {
        mockMvc.perform(patch("/bookings/cancel/r3/p4"))
            .andExpect(status().isAccepted())
            .andExpect(content().string("")); // Expecting an empty response body
    }


}
