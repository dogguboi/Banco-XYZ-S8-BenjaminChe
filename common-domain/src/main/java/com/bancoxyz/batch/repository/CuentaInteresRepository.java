package com.bancoxyz.batch.repository;

import com.bancoxyz.batch.model.CuentaInteres;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CuentaInteresRepository extends JpaRepository<CuentaInteres, Long> {

    /**
     * Última foto de la cuenta (corte mensual más reciente calculado por el
     * Job de intereses). Es la que los BFFs usan como "saldo actual".
     */
    Optional<CuentaInteres> findFirstByCuentaIdOrderByFechaDesc(String cuentaId);

    /**
     * Identificadores distintos de cuenta.
     *
     * Se declara con @Query explícita porque la derivación por nombre de
     * método no permite proyectar un único campo escalar en Hibernate 6
     * (intenta devolver la entidad completa y lanza QueryTypeMismatchException).
     */
    @Query("select distinct c.cuentaId from CuentaInteres c")
    List<String> findDistinctCuentaIdBy();
}
