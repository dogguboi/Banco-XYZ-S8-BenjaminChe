package com.bancoxyz.batch.repository;

import com.bancoxyz.batch.model.TransaccionDiaria;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransaccionDiariaRepository extends JpaRepository<TransaccionDiaria, Long> {
    long countByAnomaliaTrue();
}
