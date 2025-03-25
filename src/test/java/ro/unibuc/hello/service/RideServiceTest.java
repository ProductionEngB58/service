package ro.unibuc.hello.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ro.unibuc.hello.dto.ride.RideRequestDTO;
import ro.unibuc.hello.dto.ride.RideResponseDTO;
import ro.unibuc.hello.dto.user.UserResponseDTO;
import ro.unibuc.hello.enums.RideStatus;
import ro.unibuc.hello.exceptions.ride.InvalidRideException;
import ro.unibuc.hello.exceptions.ride.RideConflictException;
import ro.unibuc.hello.exceptions.rideBooking.InvalidRideBookingException;
import ro.unibuc.hello.model.Ride;
import ro.unibuc.hello.model.RideBooking;
import ro.unibuc.hello.repository.RideBookingRepository;
import ro.unibuc.hello.repository.RideRepository;
import ro.unibuc.hello.repository.UserRepository;
import ro.unibuc.hello.repository.VehicleRepository;

@ExtendWith(MockitoExtension.class)
public class RideServiceTest {
    @Mock
    private RideRepository rideRepository;

    @Mock
    private RideBookingRepository rideBookingRepository;

    @Mock
    private UserRepository userRepository;

    @Mock 
    private VehicleRepository vehicleRepository;

    @Mock 
    private RideBookingService rideBookingService;

    @InjectMocks
    private RideService rideService;

    private RideRequestDTO createValidRideRequest() {
        return new RideRequestDTO(
            "driver123",
            "Bucuresti",
            "Cluj",
            Instant.now().plusSeconds(3600), // 1 hour in future
            Instant.now().plusSeconds(7200), // 2 hours in future
            50,
            3,
            "B-123-ABC"
        );
    }

    @Test
    void testCreateRide_DriverNotFound() {
       
        RideRequestDTO request = createValidRideRequest();
        when(userRepository.existsById(request.getDriverId())).thenReturn(false);

   
        assertThrows(InvalidRideException.class, () -> {
            rideService.createRide(request);
        });
        
        verify(userRepository, times(1)).existsById(request.getDriverId());
        verify(rideRepository, never()).save(any());
    }

    @Test
    void testCreateRide_DepartureTimeInPast() {

        RideRequestDTO request = createValidRideRequest();
        request.setDepartureTime(Instant.now().minusSeconds(3600)); // 1 hour in the past
        
        when(userRepository.existsById(request.getDriverId())).thenReturn(true);

        assertThrows(InvalidRideException.class, () -> {
            rideService.createRide(request);
        });
    }

    @Test
    void testCreateRide_SameStartEndLocation() {
 
        RideRequestDTO request = createValidRideRequest();
        request.setEndLocation(request.getStartLocation());
        
        when(userRepository.existsById(request.getDriverId())).thenReturn(true);

        assertThrows(InvalidRideException.class, () -> {
            rideService.createRide(request);
        });
    }

    @Test
    void testCreateRide_NoSeatsAvailable() {

        RideRequestDTO request = createValidRideRequest();
        request.setSeatsAvailable(0);
        
        when(userRepository.existsById(request.getDriverId())).thenReturn(true);

        assertThrows(InvalidRideException.class, () -> {
            rideService.createRide(request);
        });
    }

    @Test
    void testCreateRide_NegativePrice() {

        RideRequestDTO request = createValidRideRequest();
        request.setSeatPrice(-10);
        
        when(userRepository.existsById(request.getDriverId())).thenReturn(true);

        assertThrows(InvalidRideException.class, () -> {
            rideService.createRide(request);
        });
    }

    @Test
    void testCreateRide_VehicleNotFound() {

        RideRequestDTO request = createValidRideRequest();
        
        when(userRepository.existsById(request.getDriverId())).thenReturn(true);
        when(vehicleRepository.existsByLicensePlate(request.getCarLicensePlate())).thenReturn(false);

        assertThrows(InvalidRideException.class, () -> {
            rideService.createRide(request);
        });
    }

    @Test
    void testCreateRide_DriverOverlapAsDriver() {

        RideRequestDTO request = createValidRideRequest();
        
        when(userRepository.existsById(request.getDriverId())).thenReturn(true);

        when(vehicleRepository.existsByLicensePlate(request.getCarLicensePlate())).thenReturn(true);

        when(rideRepository.findByDriverIdAndTimeOverlap(
            request.getDriverId(),
            request.getDepartureTime(),
            request.getArrivalTime()
        )).thenReturn(List.of(new Ride())); 

        assertThrows(RideConflictException.class, () -> {
            rideService.createRide(request);
        });
    }


    @Test
    void testCreateRide_Success() {

        RideRequestDTO request = createValidRideRequest();
        Ride expectedRide = request.toEntity();
        
        when(userRepository.existsById(request.getDriverId())).thenReturn(true);

        when(vehicleRepository.existsByLicensePlate(request.getCarLicensePlate())).thenReturn(true);

        when(rideRepository.findByDriverIdAndTimeOverlap(any(), any(), any())).thenReturn(List.of());

        when(rideBookingRepository.findByPassengerId(request.getDriverId())).thenReturn(List.of());
        
        when(rideRepository.save(any(Ride.class))).thenReturn(expectedRide);

        RideResponseDTO result = rideService.createRide(request);

        assertNotNull(result);
        assertEquals(request.getStartLocation(), result.getStartLocation());
        assertEquals(request.getEndLocation(), result.getEndLocation());
        
        verify(rideRepository, times(1)).save(any(Ride.class));
    }

    @Test
    public void testGetAllRides() {

        List<Ride> mockRides = Arrays.asList(
            new Ride("driver1", "Bucuresti", "Cluj", Instant.now(), Instant.now().plusSeconds(3600), 50, 3, "B-123-ABC"),
            new Ride("driver2", "Iasi", "Brasov", Instant.now(), Instant.now().plusSeconds(7200), 60, 4, "IS-456-DEF")
        );
        when(rideRepository.findAll()).thenReturn(mockRides);

        List<Ride> result = rideService.getAllRides();

        assertEquals(2, result.size());
        verify(rideRepository, times(1)).findAll();
    }

    @Test
    void testGetRidesByDate() {

        Instant testDate = Instant.parse("2023-01-01T00:00:00Z");
        List<Ride> mockRides = Arrays.asList(
            new Ride("1", "Bucuresti", "Cluj", testDate.plusSeconds(3600), testDate.plusSeconds(7200), 50, 3, "B-123-ABC")
        );
        
        when(rideRepository.findAllByDepartureDate(any(Instant.class), any(Instant.class)))
            .thenReturn(mockRides);

        List<Ride> result = rideService.getRidesByDate(testDate);

        assertEquals(1, result.size());
        verify(rideRepository, times(1)).findAllByDepartureDate(any(), any());
    }

    @Test
    void testUpdateRideStatusToInProgress_Success() {

        String rideId = "ride123";
        Ride mockRide = new Ride("driver1", "Bucuresti", "Cluj", 
            Instant.now().plusSeconds(0), Instant.now().plusSeconds(7200), 50, 3, "B-123-ABC");
        mockRide.setStatus(RideStatus.SCHEDULED);
        
        when(rideRepository.findById(rideId)).thenReturn(Optional.of(mockRide));
        when(rideRepository.save(any(Ride.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RideResponseDTO result = rideService.updateRideStatusToInProgress(rideId);

        assertEquals(RideStatus.IN_PROGRESS, mockRide.getStatus());
        verify(rideRepository, times(1)).findById(rideId);
        verify(rideRepository, times(1)).save(mockRide);
    }

    @Test
    void testUpdateRideStatusToInProgress_InvalidStatus() {

        String rideId = "ride123";
        Ride mockRide = new Ride("driver1", "Bucuresti", "Cluj", 
            Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200), 50, 3, "B-123-ABC");
        mockRide.setStatus(RideStatus.IN_PROGRESS);
        
        when(rideRepository.findById(rideId)).thenReturn(Optional.of(mockRide));

        assertThrows(InvalidRideException.class, () -> {
            rideService.updateRideStatusToInProgress(rideId);
        });
    }

    @Test
    void testUpdateRideStatusToCompleted_Success() {

        String rideId = "ride123";
        String currentLocation = "Cluj";
        Ride mockRide = new Ride("driver1", "Bucuresti", "Cluj", 
            Instant.now().minusSeconds(3600), Instant.now().plusSeconds(3600), 50, 3, "B-123-ABC");
        mockRide.setStatus(RideStatus.IN_PROGRESS);
        
        when(rideRepository.findById(rideId)).thenReturn(Optional.of(mockRide));
        when(rideRepository.save(any(Ride.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RideResponseDTO result = rideService.updateRideStatusToCompleted(rideId, currentLocation);

        assertEquals(RideStatus.COMPLETED, mockRide.getStatus());
        verify(rideRepository, times(1)).findById(rideId);
        verify(rideRepository, times(1)).save(mockRide);
    }

    @Test
    void testUpdateRideStatusToCompleted_NotInProgress() {

        String rideId = "ride123";
        String currentLocation = "Cluj";
        Ride mockRide = new Ride("driver1", "Bucuresti", "Cluj", 
            Instant.now().minusSeconds(7200), Instant.now().minusSeconds(3600), 50, 3, "B-123-ABC");
        mockRide.setStatus(RideStatus.SCHEDULED); // Status invalid
        
        when(rideRepository.findById(rideId)).thenReturn(Optional.of(mockRide));

        assertThrows(InvalidRideException.class, () -> {
            rideService.updateRideStatusToCompleted(rideId, currentLocation);
        });
        
        verify(rideRepository, times(1)).findById(rideId);
        verify(rideRepository, never()).save(any());
    }

    @Test
    void testUpdateRideStatusToCompleted_LocationMismatch() {

        String rideId = "ride123";
        String wrongLocation = "Brasov";
        Ride mockRide = new Ride("driver1", "Bucuresti", "Cluj", 
            Instant.now().minusSeconds(7200), Instant.now().minusSeconds(3600), 50, 3, "B-123-ABC");
        mockRide.setStatus(RideStatus.IN_PROGRESS);
        
        when(rideRepository.findById(rideId)).thenReturn(Optional.of(mockRide));

        assertThrows(InvalidRideException.class, () -> {
            rideService.updateRideStatusToCompleted(rideId, wrongLocation);
        });
        
        verify(rideRepository, times(1)).findById(rideId);
        verify(rideRepository, never()).save(any());
    }

    @Test
    void testUpdateRideStatusToCancelled_Success() {

        String rideId = "ride123";
        Ride mockRide = new Ride("driver1", "Bucuresti", "Cluj", 
            Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200), 50, 3, "B-123-ABC");
        mockRide.setStatus(RideStatus.SCHEDULED);
        
        RideBooking booking1 = new RideBooking("1", "67d767dca086f159e0e3ad65", Instant.now());
        RideBooking booking2 = new RideBooking("2", "67d767dca086f159e0e3ad65", Instant.now());
        List<RideBooking> mockBookings = Arrays.asList(booking1, booking2);
        
        when(rideRepository.findById(rideId)).thenReturn(Optional.of(mockRide));
        when(rideBookingRepository.findByRideId(rideId)).thenReturn(mockBookings);
        when(rideRepository.save(any(Ride.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RideResponseDTO result = rideService.updateRideStatusToCancelled(rideId);

        assertEquals(RideStatus.CANCELLED, mockRide.getStatus());
        verify(rideBookingRepository, times(1)).findByRideId(rideId);
        verify(rideBookingService, times(2)).updateRideBookingStatusToCancelled(anyString(), anyString());
        verify(rideRepository, times(1)).save(mockRide);
    }

    @Test
    void testUpdateRideStatusToCancelled_NotScheduled() {
 
        String rideId = "ride123";
        Ride mockRide = new Ride("driver1", "Bucuresti", "Cluj", 
            Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200), 50, 3, "B-123-ABC");
        mockRide.setStatus(RideStatus.IN_PROGRESS); // Setăm un status invalid
        
        when(rideRepository.findById(rideId)).thenReturn(Optional.of(mockRide));

        assertThrows(InvalidRideException.class, () -> {
            rideService.updateRideStatusToCancelled(rideId);
        });
        
        verify(rideRepository, times(1)).findById(rideId);
        verify(rideBookingRepository, never()).findByRideId(anyString());
        verify(rideBookingService, never()).updateRideBookingStatusToCancelled(anyString(), anyString());
        verify(rideRepository, never()).save(any());
    }

    @Test
    void testUpdateRideStatusToCancelled_DepartureTimePassed() {

        String rideId = "ride123";
        Ride mockRide = new Ride("driver1", "Bucuresti", "Cluj", 
            Instant.now().minusSeconds(3600), Instant.now().plusSeconds(3600), 50, 3, "B-123-ABC");
        mockRide.setStatus(RideStatus.SCHEDULED);
        
        when(rideRepository.findById(rideId)).thenReturn(Optional.of(mockRide));

        assertThrows(InvalidRideException.class, () -> {
            rideService.updateRideStatusToCancelled(rideId);
        });
        
        verify(rideRepository, times(1)).findById(rideId);
        verify(rideBookingRepository, never()).findByRideId(anyString());
        verify(rideBookingService, never()).updateRideBookingStatusToCancelled(anyString(), anyString());
        verify(rideRepository, never()).save(any());
    }

}
