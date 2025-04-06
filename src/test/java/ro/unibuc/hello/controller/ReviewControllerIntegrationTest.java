package ro.unibuc.hello.controller;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;

import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;


import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import ro.unibuc.hello.dto.review.ReviewRequestDTO;
import ro.unibuc.hello.model.Review;
import ro.unibuc.hello.model.Ride;
import ro.unibuc.hello.model.User;
import ro.unibuc.hello.enums.RideBookingStatus;
import ro.unibuc.hello.enums.Role;
import ro.unibuc.hello.enums.RideStatus;
import ro.unibuc.hello.model.RideBooking;
import ro.unibuc.hello.service.ReviewService;

import ro.unibuc.hello.repository.RideBookingRepository;
import ro.unibuc.hello.repository.RideRepository;
import ro.unibuc.hello.repository.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Tag("IntegrationTest")
public class ReviewControllerIntegrationTest {

    @Container
    public static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:6.0.20")
            .withExposedPorts(27017).withSharding();

    @BeforeAll
    public static void setUp() {
        mongoDBContainer.start();
    }

    @AfterAll
    public static void tearDown() {
        mongoDBContainer.stop();
    }

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        final String MONGO_URL = "mongodb://localhost:";
        final String PORT = String.valueOf(mongoDBContainer.getMappedPort(27017));
        registry.add("spring.data.mongodb.uri", () -> MONGO_URL + PORT);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RideRepository rideRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RideBookingRepository rideBookingRepository;

    @Autowired
    private ReviewService reviewService;

    @BeforeEach
    public void cleanUpAndAddTestData() {
        reviewService.deleteAllReviews();
        rideRepository.deleteAll();
        userRepository.deleteAll();
        rideBookingRepository.deleteAll();

        Review review1 = new Review("user1", "driver1", "ride1", 5, "Excellent ride!");
        Review review2 = new Review("user2", "driver1", "ride2", 4, "Good experience.");

        reviewService.saveReview(review1);
        reviewService.saveReview(review2);
    }

    @Test
    public void testGetReviewsByRide() throws Exception {
        mockMvc.perform(get("/reviews/by-ride/ride1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].rating").value(5))
                .andExpect(jsonPath("$[0].comment").value("Excellent ride!"));
    }

    @Test
    public void testGetReviewsByDriver() throws Exception {
        mockMvc.perform(get("/reviews/by-driver/driver1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].reviewerId").value("user1"))
                .andExpect(jsonPath("$[1].reviewerId").value("user2"));
    }

    @Test
    public void testCreateReview() throws Exception {
        ReviewRequestDTO validReviewRequest = new ReviewRequestDTO();
        validReviewRequest.setReviewerId("user3");
        validReviewRequest.setReviewedId("driver2");
        validReviewRequest.setRideId("ride3");
        validReviewRequest.setRating(5);
        validReviewRequest.setComment("Great ride!");

        Ride ride = new Ride("driver2", "startLocation", "endLocation", Instant.now(), Instant.now().plusSeconds(3600), 20, 3, "XYZ123");
        ride.setStatus(RideStatus.COMPLETED);
        ride.setId("ride3");
        rideRepository.save(ride);

        User reviewer = new User("John", "Doe", "john@mail.com", "1234567890", Collections.singletonList(Role.PASSENGER));
        reviewer.setId("user3");
        userRepository.save(reviewer);

        User reviewed = new User("Driver", "One", "driver@mail.com", "0987654321", Collections.singletonList(Role.DRIVER));
        reviewed.setId("driver2");
        userRepository.save(reviewed);

        RideBooking rideBooking = new RideBooking("ride3", "user3", Instant.now());
        rideBooking.setRideBookingStatus(RideBookingStatus.BOOKED);
        rideBookingRepository.save(rideBooking);

        mockMvc.perform(post("/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(validReviewRequest)))
                .andExpect(status().isCreated());

        List<Review> reviews = reviewService.getReviewsByRide("ride3");
        Assertions.assertEquals(1, reviews.size());
        Assertions.assertEquals("Great ride!", reviews.get(0).getComment());
    }
}
