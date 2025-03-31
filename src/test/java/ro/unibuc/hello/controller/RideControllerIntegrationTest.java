package ro.unibuc.hello.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import ro.unibuc.hello.dto.ride.RideRequestDTO;
import ro.unibuc.hello.enums.RideStatus;
import ro.unibuc.hello.enums.Role;
import ro.unibuc.hello.model.Ride;
import ro.unibuc.hello.repository.RideBookingRepository;
import ro.unibuc.hello.repository.RideRepository;
import ro.unibuc.hello.repository.UserRepository;
import ro.unibuc.hello.repository.VehicleRepository;
import ro.unibuc.hello.model.User;
import ro.unibuc.hello.model.Vehicle;

@AutoConfigureMockMvc
@Testcontainers
@SpringBootTest
@Tag("IntegrationTest")
public class RideControllerIntegrationTest {

    @Container
    public static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:6.0.20")
            .withExposedPorts(27017)
            .withSharding();

    @BeforeAll
    public static void startContainer() {
        mongoDBContainer.start();
    }

    @AfterAll
    public static void stopContainer() {
        mongoDBContainer.stop();
    }

    @DynamicPropertySource
    static void setMongoDBProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
        registry.add("spring.data.mongodb.database", () -> "testdb");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RideRepository rideRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private RideBookingRepository rideBookingRepository;

    @Autowired
    private ObjectMapper objectMapper;

    User driver, savedDriver;
    Vehicle vehicle, savedVehicle;


    @BeforeEach
    void setUp() {
        rideRepository.deleteAll();
        userRepository.deleteAll();
        vehicleRepository.deleteAll();
        rideBookingRepository.deleteAll();

        // 1. Create and save a driver 
        driver = new User();
        driver.setFirstName("John");
        driver.setLastName("Doe");
        driver.setMail("john@example.com");
        driver.setPhoneNumber("0712345678");
        driver.setRole(List.of(Role.DRIVER));
        savedDriver = userRepository.save(driver);
        
        // 2. Create and save a vehicle for this driver
        vehicle = new Vehicle();
        vehicle.setLicensePlate("B123XYZ");
        vehicle.setBrand("Toyota");
        vehicle.setModel("Corolla");
        vehicle.setUserId(savedDriver.getId()); // Use the generated ID
        savedVehicle = vehicleRepository.save(vehicle);
    }

    private RideRequestDTO createValidRideRequest() {
        return new RideRequestDTO(
            savedDriver.getId(),  // Use the generated driver ID
            "Bucharest",
            "Cluj",
            Instant.now().plusSeconds(3600),
            Instant.now().plusSeconds(7200),
            50,
            3,
            savedVehicle.getLicensePlate()  // Use the saved vehicle's license plate
        );
    }

    private void setupValidDriverAndVehicle() {
        User driver = new User("John", "Doe", "john@example.com", "0712345678", List.of(Role.DRIVER));
        userRepository.save(driver);
        
        Vehicle vehicle = new Vehicle("B123XYZ", "Toyota", "Corolla", "driver123");
        vehicleRepository.save(vehicle);
    }

    @Test
    void createRide_ShouldReturnCreated_WhenValidRequest() throws Exception {

        RideRequestDTO request = createValidRideRequest();

        mockMvc.perform(post("/rides")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.startLocation").value(request.getStartLocation()))
            .andExpect(jsonPath("$.endLocation").value(request.getEndLocation()));
    }

    @Test
    void createRide_ShouldReturnBadRequest_WhenDriverNotFound() throws Exception {
        RideRequestDTO request = createValidRideRequest();

        request.setDriverId("123");

        mockMvc.perform(post("/rides")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$").value("Driver does not exist as user."));
    }

    @Test
    void createRide_ShouldReturnBadRequest_WhenDepartureTimeInPast() throws Exception {
        setupValidDriverAndVehicle();
        RideRequestDTO request = createValidRideRequest();
        request.setDepartureTime(Instant.now().minusSeconds(3600));

        mockMvc.perform(post("/rides")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$").value("Departure time must be in the future."));
    }

    @Test
    void createRide_ShouldReturnBadRequest_WhenSameStartEndLocation() throws Exception {
        setupValidDriverAndVehicle();
        RideRequestDTO request = createValidRideRequest();
        request.setEndLocation(request.getStartLocation());

        mockMvc.perform(post("/rides")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$").value("Start location has to be different from end location"));
    }

    @Test
    void createRide_ShouldReturnBadRequest_WhenNoSeatsAvailable() throws Exception {
        setupValidDriverAndVehicle();
        RideRequestDTO request = createValidRideRequest();
        request.setSeatsAvailable(0);

        mockMvc.perform(post("/rides")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$").value("Number of seats has to be greater than 0."));
    }

    @Test
    void createRide_ShouldReturnBadRequest_WhenNegativePrice() throws Exception {
        setupValidDriverAndVehicle();
        RideRequestDTO request = createValidRideRequest();
        request.setSeatPrice(-10);

        mockMvc.perform(post("/rides")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$").value("Price has to be greater or equal to 0."));
    }

    @Test
    void getAllRides_ShouldReturnAllRides() throws Exception {
        setupValidDriverAndVehicle();
        
        Ride ride1 = new Ride(
            "driver123",
            "Bucharest",
            "Cluj",
            Instant.now().plusSeconds(3600),
            Instant.now().plusSeconds(7200),
            50,
            3,
            "B123XYZ"
        );
        
        Ride ride2 = new Ride(
            "driver123",
            "Cluj",
            "Bucharest",
            Instant.now().plusSeconds(10800),
            Instant.now().plusSeconds(14400),
            60,
            4,
            "B123XYZ"
        );
        
        rideRepository.saveAll(List.of(ride1, ride2));

        mockMvc.perform(get("/rides"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].startLocation").value("Bucharest"))
                .andExpect(jsonPath("$[1].startLocation").value("Cluj"));
    }

    @Test
    void getRidesByDate_ShouldReturnFilteredRides() throws Exception {
        setupValidDriverAndVehicle();
        
        Instant testDate = Instant.parse("2023-01-01T00:00:00Z");
        
        Ride ride1 = new Ride(
            "driver123",
            "Bucharest",
            "Cluj",
            testDate.plusSeconds(3600),
            testDate.plusSeconds(7200),
            50,
            3,
            "B123XYZ"
        );
        
        Ride ride2 = new Ride(
            "driver123",
            "Cluj",
            "Bucharest",
            testDate.plusSeconds(86400), // Next day
            testDate.plusSeconds(90000),
            60,
            4,
            "B123XYZ"
        );
        
        rideRepository.saveAll(List.of(ride1, ride2));

        mockMvc.perform(get("/rides")
                .param("date", testDate.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].startLocation").value("Bucharest"));
    }

}