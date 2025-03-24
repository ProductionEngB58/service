package ro.unibuc.hello.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import ro.unibuc.hello.dto.vehicle.VehicleDTO;
import ro.unibuc.hello.exceptions.vehicle.VehicleConflictException;
import ro.unibuc.hello.model.Vehicle;
import ro.unibuc.hello.service.VehicleService;

@Controller
@RequestMapping("/vehicles")
public class VehicleController {
    private final VehicleService vehicleService;

    public VehicleController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    @GetMapping
    public ResponseEntity<List<Vehicle>> getAll() {
        List<Vehicle> vehicles = vehicleService.getAll();
        return ResponseEntity.ok(vehicles);
    }

    @PostMapping
    public ResponseEntity<?> addVehicle(@RequestBody VehicleDTO vehicleDTO) {
        try {
            VehicleDTO vehicleResponse = vehicleService.addVehicle(vehicleDTO);
            return ResponseEntity.status(HttpStatus.CREATED).body(null);
        } catch (VehicleConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @PatchMapping("/{oldLicensePlate}/{newLicensePlate}")
    public ResponseEntity<?> updateVehicleLicensePlate(@PathVariable String oldLicensePlate, @PathVariable String newLicensePlate) {
        try {
            VehicleDTO vehicleResponse = vehicleService.updateLicensePlate(oldLicensePlate, newLicensePlate);
            return ResponseEntity.status(HttpStatus.OK).body(vehicleResponse);
        } catch (VehicleConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @DeleteMapping("/{licensePlate}")
    public ResponseEntity<?> deleteVehicle(@PathVariable String licensePlate) {
        try {
            vehicleService.deleteByLicensePlate(licensePlate);
            return ResponseEntity.status(HttpStatus.OK).body(null);
        } catch (VehicleConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }
}