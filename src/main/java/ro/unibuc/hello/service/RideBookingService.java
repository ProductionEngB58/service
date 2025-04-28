package ro.unibuc.hello.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import ro.unibuc.hello.config.MetricsConfig;
import ro.unibuc.hello.dto.rideBooking.RideBookingRequestDTO;
import ro.unibuc.hello.dto.rideBooking.RideBookingResponseDTO;
import ro.unibuc.hello.enums.RideBookingStatus;
import ro.unibuc.hello.enums.RideStatus;
import ro.unibuc.hello.exception.EntityNotFoundException;
import ro.unibuc.hello.exceptions.ride.InvalidRideException;
import ro.unibuc.hello.exceptions.rideBooking.InvalidRideBookingException;
import ro.unibuc.hello.model.Ride;
import ro.unibuc.hello.model.RideBooking;
import ro.unibuc.hello.model.User;
import ro.unibuc.hello.repository.RideBookingRepository;
import ro.unibuc.hello.repository.RideRepository;
import ro.unibuc.hello.repository.UserRepository;

@Service
public class RideBookingService {
    private final RideBookingRepository rideBookingRepository;
    private final UserRepository userRepository;
    private final RideRepository rideRepository;
    private final UserService userService;
    private final Clock clock;
    private final MeterRegistry meterRegistry;
    private final MetricsConfig metricsConfig;

    // Metrics
    private final Counter rideBookingCounter;
    private final Counter cancelledRideBookingsCounter;
    private final Counter bookingValidationFailuresCounter;
    private final Timer rideBookingCreationTimer;
    private final Timer rideBookingCancellationTimer;

    @Autowired
    public RideBookingService(
            RideBookingRepository rideBookingRepository,
            UserRepository userRepository,
            RideRepository rideRepository,
            UserService userService,
            Clock clock,
            MeterRegistry meterRegistry,
            MetricsConfig metricsConfig) {

        this.rideBookingRepository = rideBookingRepository;
        this.userRepository = userRepository;
        this.rideRepository = rideRepository;
        this.userService = userService;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
        this.metricsConfig = metricsConfig;

        // Initialize metrics
        this.rideBookingCounter = Counter.builder("ride_bookings_total")
                .description("Total number of ride bookings made")
                .register(meterRegistry);
        this.cancelledRideBookingsCounter = Counter.builder("ride_bookings_cancelled_total")
                .description("Total number of ride bookings cancelled")
                .register(meterRegistry);
        this.bookingValidationFailuresCounter = Counter.builder("ride_booking_validation_failures_total")
                .description("Total number of ride booking validation failures")
                .register(meterRegistry);
        this.rideBookingCreationTimer = Timer.builder("ride_booking_creation_seconds")
                .description("Time taken to create a ride booking")
                .register(meterRegistry);
        this.rideBookingCancellationTimer = Timer.builder("ride_booking_cancellation_seconds")
                .description("Time taken to cancel a ride booking")
                .register(meterRegistry);
    }

    public List<RideBookingResponseDTO> getPassengersByRideId(String rideId) {
        Timer.Sample sample = Timer.start(meterRegistry);

        List<RideBooking> bookings = rideBookingRepository.findByRideId(rideId);
        meterRegistry.counter("passengers_retrieved_count", "ride_id", rideId)
                   .increment(bookings.size());

        List<RideBookingResponseDTO> result = bookings.stream()
                .map(booking -> {
                    User passenger = userRepository.findById(booking.getPassengerId())
                            .orElseThrow(() -> new EntityNotFoundException("User"));
                    RideBookingResponseDTO responseDTO = RideBookingResponseDTO.toDTO(booking);
                    responseDTO.setPassengerFullName(passenger.getFirstName() + " " + passenger.getLastName());
                    return responseDTO;
                })
                .collect(Collectors.toList());

        sample.stop(meterRegistry.timer("ride_bookings_passenger_retrieval_seconds"));
        return result;
    }

    @Transactional
    public RideBookingResponseDTO createRideBooking(RideBookingRequestDTO rideBookingRequestDTO) {
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            // Validation logic
            if (!userRepository.existsById(rideBookingRequestDTO.getPassengerId())) {
                bookingValidationFailuresCounter.increment();
                throw new InvalidRideBookingException("Passenger ID doesn't exist");
            }

            Ride ride = rideRepository.findById(rideBookingRequestDTO.getRideId())
                    .orElseThrow(() -> {
                        bookingValidationFailuresCounter.increment();
                        return new InvalidRideException("Ride ID does not exist");
                    });

            if (rideBookingRepository.findByRideIdAndPassengerId(ride.getId(), 
                    rideBookingRequestDTO.getPassengerId()).isPresent()) {
                bookingValidationFailuresCounter.increment();
                throw new InvalidRideBookingException("Passenger already booked for this ride");
            }

            if (ride.getSeatsAvailable() < 1) {
                bookingValidationFailuresCounter.increment();
                throw new InvalidRideBookingException("No more seats available");
            }

            if (ride.getStatus() != RideStatus.SCHEDULED) {
                bookingValidationFailuresCounter.increment();
                throw new InvalidRideBookingException("Ride is not scheduled");
            }

            // Create booking
            RideBooking newRideBooking = rideBookingRequestDTO.toEntity();
            rideBookingRepository.save(newRideBooking);

            // Update ride
            ride.setSeatsAvailable(ride.getSeatsAvailable() - 1);
            rideRepository.save(ride);

            // Update metrics
            rideBookingCounter.increment();
            metricsConfig.getActiveBookings().incrementAndGet();

            return RideBookingResponseDTO.toDTO(newRideBooking);
        } finally {
            sample.stop(rideBookingCreationTimer);
        }
    }

    @Transactional
    public RideBookingResponseDTO updateRideBookingStatusToCancelled(String rideId, String passengerId) {
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            Ride ride = rideRepository.findById(rideId)
                    .orElseThrow(() -> new InvalidRideException("Ride not found"));

            RideBooking rideBooking = rideBookingRepository.findByRideIdAndPassengerId(rideId, passengerId)
                    .orElseThrow(() -> new InvalidRideBookingException("Booking not found"));

            if (rideBooking.getRideBookingStatus() != RideBookingStatus.BOOKED) {
                throw new InvalidRideBookingException("Ride already cancelled");
            }

            if (!clock.instant().isBefore(ride.getDepartureTime())) {
                throw new InvalidRideBookingException("Cannot cancel after departure time");
            }

            rideBooking.setRideBookingStatus(RideBookingStatus.CANCELLED);
            ride.setSeatsAvailable(ride.getSeatsAvailable() + 1);
            rideRepository.save(ride);

            cancelledRideBookingsCounter.increment();
            metricsConfig.getActiveBookings().decrementAndGet();

            return RideBookingResponseDTO.toDTO(rideBookingRepository.save(rideBooking));
        } finally {
            sample.stop(rideBookingCancellationTimer);
        }
    }
}