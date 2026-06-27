package com.mteam.rebuildengine.repository;

import com.mteam.rebuildengine.model.entity.TestPropertyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TestPropertyRepository extends JpaRepository<TestPropertyEntity, Long> {

}