package ro.unibuc.hello.controller;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import com.fasterxml.jackson.annotation.JsonFormat;

import ro.unibuc.hello.Metrics.RideMetrics;
import ro.unibuc.hello.dto.ride.RideRequestDTO;
import ro.unibuc.hello.dto.ride.RideResponseDTO;
import ro.unibuc.hello.enums.RideStatus;
import ro.unibuc.hello.exceptions.ride.InvalidRideException;
import ro.unibuc.hello.exceptions.ride.RideConflictException;
import ro.unibuc.hello.model.Ride;
import ro.unibuc.hello.service.RideService;

@Controller
@RequestMapping("/rides")
public class RideController {
    private static final Logger logger = LoggerFactory.getLogger(RideController.class);

    private final RideService rideService;
    private final RideMetrics rideMetrics;

    public RideController(RideService rideService, RideMetrics rideMetrics) {
        this.rideService = rideService;
        this.rideMetrics = rideMetrics;
    }

    // GET /rides 
    @GetMapping
    public ResponseEntity<List<Ride>> getAllRides() {
        logger.info("Received request to get all rides");

        List<Ride> rides = rideMetrics.recordGetAllRides(() -> rideService.getAllRides());

        logger.info("Found {} rides", rides.size());
        return ResponseEntity.ok(rides);
    }

    // GET /rides/by-date?date=YYYY-MM-DD 
    @GetMapping("/by-date")
    public ResponseEntity<List<RideResponseDTO>> getRidesByDate(
            @RequestParam @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC") Instant date) {
        List<RideResponseDTO> rides = rideService.getRidesByDate(date)
                                                 .stream()
                                                 .filter(ride -> ride.getStatus() == RideStatus.SCHEDULED)
                                                 .map(RideResponseDTO::toDTO)
                                                 .collect(Collectors.toList());
        return ResponseEntity.ok(rides);
    }

    // POST /rides
    @PostMapping
    public ResponseEntity<?> createRide(@RequestBody RideRequestDTO rideRequestDTO) {
        try {
            RideResponseDTO rideResponse = rideService.createRide(rideRequestDTO);
            rideMetrics.incrementCreatedRides();

            logger.info("Ride created");
            return ResponseEntity.status(HttpStatus.CREATED).body(rideResponse);
        } catch (InvalidRideException e) {
            logger.error("Invalid ride data: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (RideConflictException e) {
            logger.info("Ride conflict: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Error creating ride: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error creating ride: " + e.getMessage());
        }
    }

    @PatchMapping("/{rideId}/start")
    public ResponseEntity<?> updateRideStatusToInProgress(@PathVariable String rideId) {
        try {
            rideMetrics.recordRideStartTime(() -> rideService.updateRideStatusToInProgress(rideId));
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(null);
        } catch (InvalidRideException e) {
            logger.error("Invalid ride data: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Error starting ride: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error starting ride: " + e.getMessage());
        }
    }

    // PATCH /rides/{rideId}/complete
    @PatchMapping("/{rideId}/complete")
    public ResponseEntity<?> updateRideStatusToCompleted(
            @PathVariable String rideId,
            @RequestParam String currentLocation) {
        try {
            rideService.updateRideStatusToCompleted(rideId, currentLocation);
            rideMetrics.recordRideCompletion(ThreadLocalRandom.current().nextDouble(50, 501)); 
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(null);
        } catch (InvalidRideException e) {
            logger.error("Invalid ride data: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Error completing ride: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error completing ride: " + e.getMessage());
        }
    }

    // PATCH /rides/{rideId}/cancel
    @PatchMapping("/{rideId}/cancel")
    public ResponseEntity<?> updateRideStatusToCancelled(@PathVariable String rideId) {
        try {
            rideService.updateRideStatusToCancelled(rideId);
            rideMetrics.decrementActiveRides();
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(null);
        } catch (InvalidRideException e) {
            logger.error("Invalid ride data: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Error cancelling ride: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error completing ride: " + e.getMessage());
        }
    }


}