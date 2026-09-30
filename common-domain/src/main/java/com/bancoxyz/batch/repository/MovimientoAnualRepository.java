package com.bancoxyz.batch.repository;

import com.bancoxyz.batch.model.MovimientoAnual;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MovimientoAnualRepository extends JpaRepository<MovimientoAnual, Long> {

    /**
     * Identificadores distintos de cuenta con movimientos.
     * Se declara con @Query explícita por la misma razón que en
     * CuentaInteresRepository (proyección escalar en Hibernate 6).
     */
    @Query("select distinct m.cuentaId from MovimientoAnual m")
    List<String> findDistinctCuentaIdBy();

    List<MovimientoAnual> findByCuentaId(String cuentaId);
}
