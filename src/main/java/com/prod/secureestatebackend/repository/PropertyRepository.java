package com.prod.secureestatebackend.repository;

import com.prod.secureestatebackend.Entities.Property;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PropertyRepository extends JpaRepository<Property, Long> {
    List<Property> findByArdhisasaVerifiedTrue();

}
