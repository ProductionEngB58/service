package ro.unibuc.hello.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import ro.unibuc.hello.dto.rideBooking.RideBookingRequestDTO;
import ro.unibuc.hello.dto.rideBooking.RideBookingResponseDTO;
import ro.unibuc.hello.model.RideBooking;
import ro.unibuc.hello.dto.ride.RideRequestDTO;
import ro.unibuc.hello.dto.ride.RideResponseDTO;
import ro.unibuc.hello.enums.RideStatus;
import ro.unibuc.hello.exceptions.rideBooking.InvalidRideBookingException;
import ro.unibuc.hello.exceptions.ride.InvalidRideException;
import ro.unibuc.hello.model.Ride;
import ro.unibuc.hello.model.User;
import ro.unibuc.hello.repository.UserRepository;
import ro.unibuc.hello.repository.RideRepository;
import ro.unibuc.hello.service.UserService;
import ro.unibuc.hello.repository.RideBookingRepository;
import ro.unibuc.hello.exception.EntityNotFoundException;
import ro.unibuc.hello.enums.RideBookingStatus;

import java.time.Clock;
import java.time.Instant;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import ro.unibuc.hello.config.MetricsConfig;

@Service
public class RideBookingService {
    private final RideBookingRepository rideBookingRepository;
    private final UserRepository userRepository;
    private final RideRepository rideRepository;
    private final UserService userService;
    private final Clock clock;


    //  metrics
    private Counter rideBookingCounter;
    private Counter cancelledRideBookingsCounter;
    private Counter bookingValidationFailuresCounter;
    private Timer rideBookingCreationTimer;
    private Timer rideBookingCancellationTimer;
    private MetricsConfig metricsConfig = null;
    private MeterRegistry meterRegistry;

    public RideBookingService(
    RideBookingRepository rideBookingRepository, 
    UserRepository userRepository, 
    RideRepository rideRepository,
    UserService userService, 
    Clock clock
) {
    this(rideBookingRepository, userRepository, rideRepository, userService, clock, 
         null, null, null, null, null, null, null);
}

    public RideBookingService(
    RideBookingRepository rideBookingRepository, 
    UserRepository userRepository, 
    RideRepository rideRepository, 
    UserService userService, 
    Clock clock,
    Counter rideBookingCounter,
    Counter cancelledRideBookingsCounter,
    Counter bookingValidationFailuresCounter,
    Timer rideBookingCreationTimer,
    Timer rideBookingCancellationTimer,
    MetricsConfig metricsConfig,
    MeterRegistry meterRegistry)
{
    this.rideBookingRepository = rideBookingRepository;
    this.userRepository = userRepository;
    this.rideRepository = rideRepository;
    this.userService = userService;
    this.clock = clock;
    
     // Initialize metrics with safe defaults if null
     this.meterRegistry = meterRegistry != null ? meterRegistry : new SimpleMeterRegistry();
    
     // Create metrics if they're null
     this.rideBookingCounter = rideBookingCounter != null ? 
         rideBookingCounter : Counter.builder("ride_bookings_total").register(this.meterRegistry);
     
     this.cancelledRideBookingsCounter = cancelledRideBookingsCounter != null ?
         cancelledRideBookingsCounter : Counter.builder("ride_bookings_cancelled_total").register(this.meterRegistry);
     
     this.bookingValidationFailuresCounter = bookingValidationFailuresCounter != null ?
         bookingValidationFailuresCounter : Counter.builder("ride_booking_validation_failures_total").register(this.meterRegistry);
     
     this.rideBookingCreationTimer = rideBookingCreationTimer != null ?
         rideBookingCreationTimer : Timer.builder("ride_booking_creation_seconds").register(this.meterRegistry);
     
     this.rideBookingCancellationTimer = rideBookingCancellationTimer != null ?
         rideBookingCancellationTimer : Timer.builder("ride_booking_cancellation_seconds").register(this.meterRegistry);
     
     this.metricsConfig = metricsConfig;
}


    public List<RideBookingResponseDTO> getPassengersByRideId(String rideId) {

        // Create a timer for this operation
        Timer.Sample sample = Timer.start(meterRegistry);

        List<RideBooking> bookings = rideBookingRepository.findByRideId(rideId);
        
        // Record the number of passengers found - Metric #1
        meterRegistry.counter("passengers_retrieved_count", 
                             "ride_id", rideId).increment(bookings.size());

         List<RideBookingResponseDTO> result = bookings.stream()
            .map(booking -> {
                // Get passenger information
                User passenger = userRepository.findById(booking.getPassengerId())
                .orElseThrow(() -> new EntityNotFoundException("User"));
                
                // Create response DTO
                RideBookingResponseDTO responseDTO = RideBookingResponseDTO.toDTO(booking);
                
                // Set passenger full name
                responseDTO.setPassengerFullName(passenger.getFirstName() + " " + passenger.getLastName());
                
                return responseDTO;
            })
            .collect(Collectors.toList());
        
            if (meterRegistry != null) {
        // Record the operation time - Metric #2
        sample.stop(meterRegistry.timer("ride_bookings_passenger_retrieval_seconds"));
            }
        return result;
    }

    @Transactional
    public RideBookingResponseDTO createRideBooking (RideBookingRequestDTO rideBookingRequestDTO)
    {
        Timer.Sample sample = Timer.start(meterRegistry);

            try {
                //check if passenger id is in users collection
                if(!userRepository.existsById(rideBookingRequestDTO.getPassengerId())){
                    bookingValidationFailuresCounter.increment(); // Metric #4

                    throw new InvalidRideBookingException("Passenger's id doesnt exist");
                }

                //ride id has to exist
                Ride ride = rideRepository.findById(rideBookingRequestDTO.getRideId())
                .orElseThrow(() -> {
                    bookingValidationFailuresCounter.increment(); // Metric #4
                    return new InvalidRideException("Ride ID does not exist.");
                });

                //passenger shouldnt have already booked
                RideBooking existingBooking = rideBookingRepository.findByRideIdAndPassengerId(
                    ride.getId(), rideBookingRequestDTO.getPassengerId()
                    ).orElse(null);

                if (existingBooking != null) {
                    bookingValidationFailuresCounter.increment(); // Metric #4
                    throw new InvalidRideBookingException("Passenger already booked for this ride.");
                }
                    

                //check if the passenger has a conflicting ride
                List<RideBooking> bookingsInvolvedAsPassenger = rideBookingRepository
                                .findByPassengerId(rideBookingRequestDTO.getPassengerId());

                for (RideBooking booking : bookingsInvolvedAsPassenger) {
                    if (!rideRepository.findByIdAndTimeOverlap(
                        rideBookingRequestDTO.getRideId(),
                        ride.getDepartureTime(),
                        ride.getArrivalTime()
                    ).isEmpty()) {
                        bookingValidationFailuresCounter.increment(); // Metric #4
                        throw new InvalidRideBookingException("User involved in another ride at the same time.");
                    }
                }

                //available seats >0
                if(ride.getSeatsAvailable() < 1) {
                    bookingValidationFailuresCounter.increment(); // Metric #4
                    throw new InvalidRideBookingException("No more seats available");
                }

                //ride has to be scheduled

                if(ride.getStatus() != RideStatus.SCHEDULED)
                {
                    bookingValidationFailuresCounter.increment(); // Metric #4
                    throw new InvalidRideBookingException("Ride is not scheduled");
                }

                RideBooking newRideBooking = rideBookingRequestDTO.toEntity();

                rideBookingRepository.save(newRideBooking);

                ride.setSeatsAvailable(ride.getSeatsAvailable() - 1);
                rideRepository.save(ride);

                    // Increment booking counter - Metric #5
                    rideBookingCounter.increment();
                                
                    // Update active bookings gauge safely
                    if (metricsConfig != null) {
                        metricsConfig.getActiveBookings().incrementAndGet();
                    }

            return RideBookingResponseDTO.toDTO(newRideBooking);

        } finally {
            // stop the timer
            if (meterRegistry != null) {
            sample.stop(rideBookingCreationTimer); }
        }
    };
    

    public RideBookingResponseDTO updateRideBookingStatusToCancelled(String rideId, String passengerId) {
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            Ride ride = rideRepository.findById(rideId)
                    .orElseThrow(() -> new InvalidRideException("Ride not found."));

            RideBooking rideBooking = rideBookingRepository.findByRideIdAndPassengerId(rideId, passengerId)
                            .orElse(null);

            if (rideBooking == null) {
                throw new InvalidRideBookingException("Booking not found.");
            }

            // check if the rideBooking status is BOOKED
            if (rideBooking.getRideBookingStatus() != RideBookingStatus.BOOKED) {
                throw new InvalidRideBookingException("Ride already cancelled.");
            }
        
            // Check if instant.now < departure time
            if (!clock.instant().isBefore(ride.getDepartureTime())) {
                throw new InvalidRideBookingException("Ride cannot be cancelled after it started.");
            }
        
            rideBooking.setRideBookingStatus(RideBookingStatus.CANCELLED);

            ride.setSeatsAvailable(ride.getSeatsAvailable() + 1);
            rideRepository.save(ride);
            
            // Record the cancellation - Metric #7
            cancelledRideBookingsCounter.increment();
            
            if (metricsConfig != null) {
                metricsConfig.getActiveBookings().decrementAndGet();
            }
            
            return RideBookingResponseDTO.toDTO(rideBookingRepository.save(rideBooking));
        } finally {
            // Always stop the timer
            if (meterRegistry != null) {
            sample.stop(rideBookingCancellationTimer);}
        }
    }
}
