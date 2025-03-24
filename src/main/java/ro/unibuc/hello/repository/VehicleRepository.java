package ro.unibuc.hello.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import ro.unibuc.hello.model.Vehicle;

public interface VehicleRepository extends MongoRepository<Vehicle, String> {

    boolean existsByLicensePlate(String licensePlate);

    @Query("{ 'licensePlate': ?0 }")
    Vehicle findOneByLicensePlate(String licensePlate);
}
