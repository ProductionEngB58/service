package ro.unibuc.hello.service;

import java.util.List;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import ro.unibuc.hello.dto.vehicle.VehicleDTO;
import ro.unibuc.hello.exceptions.vehicle.VehicleConflictException;
import ro.unibuc.hello.model.Vehicle;
import ro.unibuc.hello.repository.UserRepository;
import ro.unibuc.hello.repository.VehicleRepository;

@Service
public class VehicleService {
    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;

    public VehicleService(VehicleRepository vehicleRepository, UserRepository userRepository) {
        this.vehicleRepository = vehicleRepository;
        this.userRepository = userRepository;
    }

    public List<Vehicle> getAll() {
        return vehicleRepository.findAll();
        
    }
    
    public VehicleDTO addVehicle(VehicleDTO vehicleDTO) {
        if (vehicleRepository.existsByLicensePlate(vehicleDTO.getLicensePlate())) {
            throw new VehicleConflictException("License plate already used.");
        }

        if (userRepository.findById(vehicleDTO.getUserId()).isEmpty()) {
            throw new VehicleConflictException("Owner not stored in the system.");
        }

        vehicleRepository.save(vehicleDTO.toEntity());

        return vehicleDTO;
    }

    public VehicleDTO updateLicensePlate(String oldLicensePlate, String newLicensePlate) {
        if (!vehicleRepository.existsByLicensePlate(oldLicensePlate)) {
            throw new VehicleConflictException("License plate does not match any car in the system.");
        } else if (vehicleRepository.existsByLicensePlate(newLicensePlate)) {
            throw new VehicleConflictException("License plate already used, try a new one.");
        }

        Vehicle vehicle = vehicleRepository.findOneByLicensePlate(oldLicensePlate);

        vehicle.setLicensePlate(newLicensePlate);

        return vehicleRepository.save(vehicle).toDTO();
    }

    public void deleteByLicensePlate(String licensePlate) {
        if (!vehicleRepository.existsByLicensePlate(licensePlate)) {
            throw new VehicleConflictException("License plate does not match any car in the system.");
        } 

        vehicleRepository.delete(vehicleRepository.findOneByLicensePlate(licensePlate));
    }
}
