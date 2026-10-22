package com.java.yincools.domain;

import com.java.yincools.domain.model.Vehicle;
import com.java.yincools.persistence.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class VehicleServiceTest {

    @Autowired
    private VehicleRepository vehicleRepository;

    private VehicleService vehicleService;

    @BeforeEach
    void setUp() {
        vehicleService = new VehicleService(vehicleRepository);
    }

    @Test
    void noteWithPlateAppendsTheNormalizedPlateToTheDescription() {
        assertThat(VehicleService.noteWithPlate("Camry 2010", "abc-123")).isEqualTo("Camry 2010 (ABC123)");
    }

    @Test
    void noteWithPlateFallsBackToJustThePlateWhenThereIsNoDescription() {
        assertThat(VehicleService.noteWithPlate(null, "abc 123")).isEqualTo("ABC123");
        assertThat(VehicleService.noteWithPlate("", "abc 123")).isEqualTo("ABC123");
    }

    @Test
    void noteWithPlateReturnsTheDescriptionUnchangedWhenThereIsNoPlate() {
        assertThat(VehicleService.noteWithPlate("Camry 2010", null)).isEqualTo("Camry 2010");
        assertThat(VehicleService.noteWithPlate("Camry 2010", "   ")).isEqualTo("Camry 2010");
    }

    @Test
    void findOrCreateTreatsDifferentlyFormattedPlatesAsTheSameCar() {
        Vehicle first = vehicleService.findOrCreate(1L, "Camry 2010", "abc 123");
        Vehicle second = vehicleService.findOrCreate(1L, "Camry 2010", "ABC-123");

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(vehicleRepository.count()).isEqualTo(1);
        assertThat(second.getPlateNumber()).isEqualTo("ABC123");
    }

    @Test
    void findOrCreateUpdatesThePlateWhenARealChangeIsTyped() {
        Vehicle first = vehicleService.findOrCreate(1L, "Camry 2010", "ABC123");
        Vehicle second = vehicleService.findOrCreate(1L, "Camry 2010", "XYZ999");

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getPlateNumber()).isEqualTo("XYZ999");
    }
}
