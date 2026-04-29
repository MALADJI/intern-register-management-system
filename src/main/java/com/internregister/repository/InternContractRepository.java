package com.internregister.repository;

import com.internregister.entity.InternContract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InternContractRepository extends JpaRepository<InternContract, Long> {
    Optional<InternContract> findByIntern_InternId(Long internId);
}
