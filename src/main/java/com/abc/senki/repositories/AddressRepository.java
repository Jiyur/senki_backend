package com.abc.senki.repositories;

import com.abc.senki.model.entity.AddressEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AddressRepository extends JpaRepository<AddressEntity, String> {
    Optional<AddressEntity> findById(String id);

    @Override
    List<AddressEntity> findAll();


}

