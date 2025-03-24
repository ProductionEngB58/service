package ro.unibuc.hello.service;

import static org.junit.Assert.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import ro.unibuc.hello.dto.vehicle.VehicleDTO;
import ro.unibuc.hello.exceptions.vehicle.VehicleConflictException;
import ro.unibuc.hello.model.User;
import ro.unibuc.hello.model.Vehicle;
import ro.unibuc.hello.repository.UserRepository;
import ro.unibuc.hello.repository.VehicleRepository;

@ExtendWith(MockitoExtension.class)
public class VehicleServiceTest {
    
    @Mock 
    private VehicleRepository vehicleRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private VehicleService vehicleService;

    @Test
    void testGetAll() {
        List<Vehicle> vehicles = Arrays.asList(
            new Vehicle("67d02c7ce08f3e1e5c96ef95", "Dacia", "Logan", "B-123-XYZ"),
            new Vehicle("67d02c7ce08f3e1e5c96ef95", "Dacia", "Duster", "B-001-DUS"),
            new Vehicle("67d02c7ce08f3e1e5c96ef95", "Dacia", "Spring", "B-022-SPR")
        );
        when(vehicleRepository.findAll()).thenReturn(vehicles);

        List<Vehicle> result = vehicleService.getAll();

        // Assert size of the list
        assertEquals(3, result.size());

        // Assert license plates
        assertEquals("B-123-XYZ", result.get(0).getLicensePlate());
        assertEquals("B-001-DUS", result.get(1).getLicensePlate());
        assertEquals("B-022-SPR", result.get(2).getLicensePlate());

        // Assert car brand
        assertEquals("Dacia", result.get(0).getBrand());

        // Assert car model
        assertEquals("Spring", result.get(2).getModel());

        verify(vehicleRepository, times(1)).findAll();
    }
    
    @Test
    void testAddVehicle_Success() {
        // Given
        VehicleDTO vehicleDTO = new VehicleDTO("userId1", "Dacia", "Logan", "B-123-XYZ");
        Vehicle vehicle = vehicleDTO.toEntity();
        
        when(vehicleRepository.existsByLicensePlate("B-123-XYZ")).thenReturn(false);
        when(userRepository.findById("userId1")).thenReturn(Optional.of(new User()));
        when(vehicleRepository.save(any(Vehicle.class))).thenReturn(vehicle);

        // When
        VehicleDTO result = vehicleService.addVehicle(vehicleDTO);

        // Then
        assertEquals("B-123-XYZ", result.getLicensePlate());
        verify(vehicleRepository, times(1)).existsByLicensePlate("B-123-XYZ");
        verify(userRepository, times(1)).findById("userId1");
        verify(vehicleRepository, times(1)).save(any(Vehicle.class));
    }

    @Test
    void testAddVehicle_LicensePlateConflict() {
        // Given
        VehicleDTO vehicleDTO = new VehicleDTO("userId1", "Dacia", "Logan", "B-123-XYZ");
        
        when(vehicleRepository.existsByLicensePlate("B-123-XYZ")).thenReturn(true);

        // When/Then
        assertThrows(VehicleConflictException.class, () -> {
            vehicleService.addVehicle(vehicleDTO);
        });
        
        verify(vehicleRepository, times(1)).existsByLicensePlate("B-123-XYZ");
        verify(userRepository, never()).findById(anyString());
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    void testAddVehicle_UserNotFound() {
        // Given
        VehicleDTO vehicleDTO = new VehicleDTO("userId1", "Dacia", "Logan", "B-123-XYZ");
        
        when(vehicleRepository.existsByLicensePlate("B-123-XYZ")).thenReturn(false);
        when(userRepository.findById("userId1")).thenReturn(Optional.empty());

        // When/Then
        assertThrows(VehicleConflictException.class, () -> {
            vehicleService.addVehicle(vehicleDTO);
        });
        
        verify(vehicleRepository, times(1)).existsByLicensePlate("B-123-XYZ");
        verify(userRepository, times(1)).findById("userId1");
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    void testUpdateLicensePlate_Success() {
        // Given
        String oldPlate = "B-OLD-123";
        String newPlate = "B-NEW-123";
        Vehicle vehicle = new Vehicle("id1", "Dacia", "Logan", oldPlate);
        
        when(vehicleRepository.existsByLicensePlate(oldPlate)).thenReturn(true);
        when(vehicleRepository.existsByLicensePlate(newPlate)).thenReturn(false);
        when(vehicleRepository.findOneByLicensePlate(oldPlate)).thenReturn(vehicle);
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        VehicleDTO result = vehicleService.updateLicensePlate(oldPlate, newPlate);

        // Then
        assertEquals(newPlate, result.getLicensePlate());
        verify(vehicleRepository, times(1)).existsByLicensePlate(oldPlate);
        verify(vehicleRepository, times(1)).existsByLicensePlate(newPlate);
        verify(vehicleRepository, times(1)).findOneByLicensePlate(oldPlate);
        verify(vehicleRepository, times(1)).save(any(Vehicle.class));
    }

    @Test
    void testUpdateLicensePlate_OldPlateNotFound() {
        // Given
        String oldPlate = "B-OLD-123";
        String newPlate = "B-NEW-123";
        
        when(vehicleRepository.existsByLicensePlate(oldPlate)).thenReturn(false);

        // When/Then
        assertThrows(VehicleConflictException.class, () -> {
            vehicleService.updateLicensePlate(oldPlate, newPlate);
        });
        
        verify(vehicleRepository, times(1)).existsByLicensePlate(oldPlate);
        verify(vehicleRepository, never()).existsByLicensePlate(newPlate);
        verify(vehicleRepository, never()).findOneByLicensePlate(anyString());
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    void testUpdateLicensePlate_NewPlateConflict() {
        // Given
        String oldPlate = "B-OLD-123";
        String newPlate = "B-NEW-123";
        
        when(vehicleRepository.existsByLicensePlate(oldPlate)).thenReturn(true);
        when(vehicleRepository.existsByLicensePlate(newPlate)).thenReturn(true);

        // When/Then
        assertThrows(VehicleConflictException.class, () -> {
            vehicleService.updateLicensePlate(oldPlate, newPlate);
        });
        
        verify(vehicleRepository, times(1)).existsByLicensePlate(oldPlate);
        verify(vehicleRepository, times(1)).existsByLicensePlate(newPlate);
        verify(vehicleRepository, never()).findOneByLicensePlate(anyString());
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    void testDeleteByLicensePlate_Success() {
        // Given
        String licensePlate = "B-123-XYZ";
        Vehicle vehicle = new Vehicle("id1", "Dacia", "Logan", licensePlate);
        
        when(vehicleRepository.existsByLicensePlate(licensePlate)).thenReturn(true);
        when(vehicleRepository.findOneByLicensePlate(licensePlate)).thenReturn(vehicle);
        doNothing().when(vehicleRepository).delete(vehicle);

        // When
        vehicleService.deleteByLicensePlate(licensePlate);

        // Then
        verify(vehicleRepository, times(1)).existsByLicensePlate(licensePlate);
        verify(vehicleRepository, times(1)).findOneByLicensePlate(licensePlate);
        verify(vehicleRepository, times(1)).delete(vehicle);
    }

    @Test
    void testDeleteByLicensePlate_NotFound() {
        // Given
        String licensePlate = "B-123-XYZ";
        
        when(vehicleRepository.existsByLicensePlate(licensePlate)).thenReturn(false);

        // When/Then
        assertThrows(VehicleConflictException.class, () -> {
            vehicleService.deleteByLicensePlate(licensePlate);
        });
        
        verify(vehicleRepository, times(1)).existsByLicensePlate(licensePlate);
        verify(vehicleRepository, never()).findOneByLicensePlate(anyString());
        verify(vehicleRepository, never()).delete(any());
    }

}
