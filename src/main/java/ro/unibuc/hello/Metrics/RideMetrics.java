package ro.unibuc.hello.Metrics;

import io.micrometer.core.instrument.*;
import ro.unibuc.hello.model.Ride;

import java.util.List;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

@Component
public class RideMetrics {

    private final Counter rideCreatedCounter;
    private final Gauge activeRidesGauge;
    private final Timer rideStartTimer;
    private final DistributionSummary rideCompletionSummary;
    private final Timer getAllRidesTimer;

    private static int activeRides = 0; 

    public RideMetrics(MeterRegistry meterRegistry) {
        this.rideCreatedCounter = Counter.builder("rides_created_total")
                .description("Total number of rides created")
                .register(meterRegistry);

        this.activeRidesGauge = Gauge.builder("active_rides_gauge", this, RideMetrics::getActiveRides)
                .description("Current number of active rides")
                .register(meterRegistry);

        this.rideStartTimer = Timer.builder("ride_start_duration_seconds")
                .description("Time taken to start a ride")
                .register(meterRegistry);

        this.rideCompletionSummary = DistributionSummary.builder("ride_completion_summary")
                .description("Summary of ride completions")
                .register(meterRegistry);

        this.getAllRidesTimer = Timer.builder("get_all_rides_duration_seconds")
                .description("Time taken to get all rides")
                .register(meterRegistry);
    }

    public void incrementCreatedRides() {
        rideCreatedCounter.increment();
        incrementActiveRides();
    }

    public void incrementActiveRides() {
        activeRides++;
    }

    public void decrementActiveRides() {
        if (activeRides > 0) {
            activeRides--;
        }
    }

    public void recordRideStartTime(Runnable runnable) {
        rideStartTimer.record(runnable);
    }

    public void recordRideCompletion(double distance) {
        rideCompletionSummary.record(distance);
        decrementActiveRides();
    }

    public List<Ride> recordGetAllRides(Supplier<List<Ride>> supplier) {
    return getAllRidesTimer.record(supplier);
}

    public int getActiveRides() {
        return activeRides;
    }
}