package ro.unibuc.hello.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Gauge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class MetricsConfig {
    
    // Counter to track total number of active bookings
    private final AtomicInteger activeBookings = new AtomicInteger(0);
    
    @Bean
    public Counter rideBookingCounter(MeterRegistry registry) {
        return Counter.builder("ride_bookings_total")
                .description("Total number of ride bookings made")
                .register(registry);
    }
    
    @Bean
    public Counter cancelledRideBookingsCounter(MeterRegistry registry) {
        return Counter.builder("ride_bookings_cancelled_total")
                .description("Total number of ride bookings cancelled")
                .register(registry);
    }
    
    @Bean
    public Counter bookingValidationFailuresCounter(MeterRegistry registry) {
        return Counter.builder("ride_booking_validation_failures_total")
                .description("Total number of ride booking validation failures")
                .register(registry);
    }
    
    @Bean
    public Timer rideBookingCreationTimer(MeterRegistry registry) {
        return Timer.builder("ride_booking_creation_seconds")
                .description("Time taken to create a ride booking")
                .register(registry);
    }
    
    @Bean
    public Timer rideBookingCancellationTimer(MeterRegistry registry) {
        return Timer.builder("ride_booking_cancellation_seconds")
                .description("Time taken to cancel a ride booking")
                .register(registry);
    }
    
    @Bean
    public Gauge activeRideBookingsGauge(MeterRegistry registry) {
        return Gauge.builder("active_ride_bookings", activeBookings, AtomicInteger::get)
                .description("Number of currently active ride bookings")
                .register(registry);
    }
    
    public AtomicInteger getActiveBookings() {
        return activeBookings;
    }
}