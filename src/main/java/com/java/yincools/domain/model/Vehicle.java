package com.java.yincools.domain.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Promoted out of Job so repeat vehicles -- especially a fleet -- have a
 * stable identity across visits instead of a re-typed string each time.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Vehicle {

    @Id
    @GeneratedValue
    private Long id;

    private Long customerId;

    private String description;

    private String plateNumber;
}
