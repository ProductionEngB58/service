package ro.unibuc.hello.repository;

import java.time.Instant;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import ro.unibuc.hello.model.Ride;

public interface RideRepository extends MongoRepository<Ride, String> {

    @Query("{ 'id': ?0, " +
       "$or: [ " +
       "{ 'departureTime': { $lte: ?1 }, 'arrivalTime': { $gte: ?1 } }, " +
       "{ 'departureTime': { $lte: ?2 }, 'arrivalTime': { $gte: ?2 } } " +
       "] }")
    List<Ride> findByIdAndTimeOverlap(String id, Instant departureTime, Instant arrivalTime);

    @Query("{ 'driverId': ?0, " +
       "$or: [ " +
       "{ 'departureTime': { $lte: ?1 }, 'arrivalTime': { $gte: ?1 } }, " +
       "{ 'departureTime': { $lte: ?2 }, 'arrivalTime': { $gte: ?2 } } " +
       "] }")
    List<Ride> findByDriverIdAndTimeOverlap(String driverId, Instant departureTime, Instant arrivalTime);

    @Query("{ 'departureTime': { $gte: ?0, $lt: ?1 } }") 
    List<Ride> findAllByDepartureDate(Instant startOfDay, Instant endOfDay);
}
