package ro.unibuc.hello.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import ro.unibuc.hello.dto.rideBooking.RideBookingRequestDTO;
import ro.unibuc.hello.dto.rideBooking.RideBookingResponseDTO;
import ro.unibuc.hello.model.Ride;
import ro.unibuc.hello.model.RideBooking;
import ro.unibuc.hello.model.User;
import ro.unibuc.hello.repository.RideBookingRepository;
import ro.unibuc.hello.repository.UserRepository;
import ro.unibuc.hello.repository.RideRepository;
import ro.unibuc.hello.enums.RideBookingStatus;
import ro.unibuc.hello.enums.Role;
import ro.unibuc.hello.exceptions.rideBooking.InvalidRideBookingException;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.time.Clock;


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
public class RideBookingServiceTest {

    @Mock
    private RideBookingRepository rideBookingRepository;
    
    @Mock
    Clock clock; 

    @Mock
    private RideRepository rideRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private RideBookingService rideBookingService;

    @InjectMocks
    private UserService userService;

    @Test
    public void testGetPassengerByRideId() {
        // Arrange
        String rideId = "67d60c0f12400d215806d06c";
        Instant createdAt1 = Instant.parse("2025-03-24T15:52:00Z");
        Instant createdAt2 = Instant.parse("2025-03-24T15:55:00Z");

        // Create two bookings
        RideBooking booking1 = new RideBooking(rideId, "67d767dca086f159e0e3ad65", createdAt1);
        RideBooking booking2 = new RideBooking(rideId, "67d767dca086f159e0e3ad66", createdAt2);
        
        List<RideBooking> bookings = Arrays.asList(booking1, booking2);

        // Mock repository method
        when(rideBookingRepository.findByRideId(rideId)).thenReturn(bookings);

        // Create mock User objects for passengers
        User user1 = new User("diaconescu", "alexandra", "alexandra@gmail.com", "0721226744", Arrays.asList(Role.PASSENGER));
        User user2 = new User("popescu", "mihai", "mihai@gmail.com", "0721226544", Arrays.asList(Role.PASSENGER));

        // Mock user repository
        when(userRepository.findById("67d767dca086f159e0e3ad65")).thenReturn(Optional.of(user1));
        when(userRepository.findById("67d767dca086f159e0e3ad66")).thenReturn(Optional.of(user2));

        // Act
        List<RideBookingResponseDTO> rideBookings = rideBookingService.getPassengersByRideId(rideId);

        // Assert
        assertNotNull(rideBookings);
        assertEquals(2, rideBookings.size());
        
        verify(rideBookingRepository).findByRideId(rideId);
        verify(userRepository, times(2)).findById(anyString());

        RideBookingResponseDTO responseDTO1 = rideBookings.get(0);
        assertEquals("67d60c0f12400d215806d06c", responseDTO1.getRideId());
        assertEquals(RideBookingStatus.BOOKED, responseDTO1.getRideBookingStatus()); 
        assertEquals(createdAt1, responseDTO1.getCreatedAt());
        assertEquals("diaconescu alexandra", responseDTO1.getPassengerFullName());

        RideBookingResponseDTO responseDTO2 = rideBookings.get(1);
        assertEquals("67d60c0f12400d215806d06c", responseDTO2.getRideId());
        assertEquals(RideBookingStatus.BOOKED, responseDTO2.getRideBookingStatus()); 
        assertEquals(createdAt2, responseDTO2.getCreatedAt());
        assertEquals("popescu mihai", responseDTO2.getPassengerFullName());
    }
    


    @Test
        void testCreateRideBooking_Success() {
            
            RideBookingRequestDTO rideBookingRequestDTO = new RideBookingRequestDTO();
            rideBookingRequestDTO.setRideId("r1");
            rideBookingRequestDTO.setPassengerId("p1");
            
            User user = new User("diaconescu", "alexandra", "alexandra@gmail.com", "0721226744", null);
            
            when(userRepository.existsById("p1")).thenReturn(true);
            
          
            Instant departureTime = Instant.parse("2025-03-24T15:52:00Z");
            Instant arrivalTime = Instant.parse("2025-03-24T16:52:00Z");
            Ride ride = new Ride("driver123", "City A", "City B", departureTime, arrivalTime, 
                                100, 10, "B45NNN");
            when(rideRepository.findById("r1")).thenReturn(Optional.of(ride));
            
            when(rideBookingRepository.findByRideIdAndPassengerId("r1", "p1")).thenReturn(Optional.empty());
            
            when(rideBookingRepository.findByPassengerId("p1")).thenReturn(java.util.Collections.emptyList());
            
            RideBookingResponseDTO responseDTO = rideBookingService.createRideBooking(rideBookingRequestDTO);

            assertNotNull(responseDTO);
            assertEquals("r1", responseDTO.getRideId());
            assertEquals(9, ride.getSeatsAvailable()); 
            
            verify(rideBookingRepository).save(any(RideBooking.class));
            verify(rideRepository).save(ride);
        }

    @Test
        void testCreateRideBooking_PassengerIdDoesntExist() {
            
        RideBookingRequestDTO rideBookingRequestDTO = new RideBookingRequestDTO();
        rideBookingRequestDTO.setRideId("r1");
        rideBookingRequestDTO.setPassengerId("p1");

        //passenger ID does not exist
        when(userRepository.existsById("p1")).thenReturn(false);

        // Act , Assert
        assertThrows(InvalidRideBookingException.class, 
            () -> rideBookingService.createRideBooking(rideBookingRequestDTO),
            "Passenger's id doesnt exist");

        // Verify that rideRepository and rideBookingRepository were never called
        verify(rideRepository, never()).findById(anyString());
        verify(rideBookingRepository, never()).findByRideIdAndPassengerId(anyString(), anyString());
        }

    @Test
        void testCreateRideBooking_PassengerHasConflictingRide() {

            RideBookingRequestDTO rideBookingRequestDTO = new RideBookingRequestDTO();
            rideBookingRequestDTO.setRideId("r1");
            rideBookingRequestDTO.setPassengerId("p1");
        
            // passenger exists
            when(userRepository.existsById("p1")).thenReturn(true);
        
            // Create a ride that the passenger wants to book
            Instant departureTime = Instant.parse("2025-03-24T15:52:00Z");
            Instant arrivalTime = Instant.parse("2025-03-24T16:52:00Z");
            Ride ride = new Ride("driver123", "City A", "City B", departureTime, arrivalTime, 
                                 100, 10, "B45NNN");
        
            when(rideRepository.findById("r1")).thenReturn(Optional.of(ride));
        
            // existing ride booking that overlaps with the new ride
            Instant existingBookingTime = Instant.parse("2025-03-24T16:00:00Z"); 
            RideBooking existingBooking = new RideBooking("existingRideId", "p1", existingBookingTime);
            when(rideBookingRepository.findByPassengerId("p1")).thenReturn(List.of(existingBooking));
        
            // findByIdAndTimeOverlap() to return : conflict exists
            when(rideRepository.findByIdAndTimeOverlap("r1", departureTime, arrivalTime))
                .thenReturn(List.of(new Ride()));
        
            // Act, assert
            InvalidRideBookingException exception = assertThrows(InvalidRideBookingException.class, 
                () -> rideBookingService.createRideBooking(rideBookingRequestDTO));
        
            assertEquals("User involved in another ride at the same time.", exception.getMessage());
        
            // Verify that rideBookingRepository.save() was never called
            verify(rideBookingRepository, never()).save(any(RideBooking.class));
            verify(rideRepository, never()).save(any(Ride.class));
        }
        
        @Test
        void testUpdateRideBookingStatusToCancelled_Success() {
            String rideId = "r1";
            String passengerId = "p1";

            Instant fixedNow = Instant.parse("2025-03-24T19:54:00Z");  // Current time
            Instant departureTime = Instant.parse("2025-03-25T20:00:00Z");  
            Instant arrivalTime = Instant.parse("2025-03-25T21:00:00Z"); 

            // Create a mock Clock to return the fixed current time
            Clock fixedClock = Clock.fixed(fixedNow, ZoneId.of("UTC"));

            // Create the service using the mocked Clock
            RideBookingService rideBookingService = new RideBookingService(rideBookingRepository, userRepository, rideRepository, userService, fixedClock);

            // mock Ride
            Ride ride = new Ride("driver123", "City A", "City B", departureTime, arrivalTime, 100, 5, "B45NNN");
            ride.setId(rideId);  
            when(rideRepository.findById(rideId)).thenReturn(Optional.of(ride));

            // mock RideBooking
            RideBooking rideBooking = new RideBooking(rideId, passengerId, Instant.parse("2025-03-24T19:39:58Z"));
            rideBooking.setRideBookingStatus(RideBookingStatus.BOOKED);
            when(rideBookingRepository.findByRideIdAndPassengerId(rideId, passengerId))
                    .thenReturn(Optional.of(rideBooking));

            // save operation to return the updated rideBooking
            when(rideBookingRepository.save(any(RideBooking.class))).thenReturn(rideBooking);
            when(rideRepository.save(any(Ride.class))).thenReturn(ride);

            // Act
            RideBookingResponseDTO responseDTO = rideBookingService.updateRideBookingStatusToCancelled(rideId, passengerId);

            // Assert
            assertNotNull(responseDTO);
            assertEquals(RideBookingStatus.CANCELLED, rideBooking.getRideBookingStatus());  
            assertEquals(6, ride.getSeatsAvailable());  

            // Verify repository save calls
            verify(rideBookingRepository).save(rideBooking);
            verify(rideRepository).save(ride);
        }
        

}