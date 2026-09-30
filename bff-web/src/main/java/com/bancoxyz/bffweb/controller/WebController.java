package com.bancoxyz.bffweb.controller;

import com.bancoxyz.batch.model.TransaccionDiaria;
import com.bancoxyz.bffweb.dto.CuentaWebDto;
import com.bancoxyz.bffweb.dto.DashboardDto;
import com.bancoxyz.bffweb.service.WebBankService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/web")
public class WebController {

    private final WebBankService service;

    public WebController(WebBankService service) {
        this.service = service;
    }

    /** Listado completo de cuentas con estado anual y movimientos. */
    @GetMapping("/cuentas")
    public List<CuentaWebDto> cuentas() {
        return service.listarCuentas();
    }

    /** Detalle completo de una cuenta. */
    @GetMapping("/cuentas/{id}")
    public ResponseEntity<CuentaWebDto> cuenta(@PathVariable String id) {
        return service.obtenerCuenta(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Todas las transacciones diarias saneadas por el batch. */
    @GetMapping("/transacciones")
    public List<TransaccionDiaria> transacciones() {
        return service.listarTransacciones();
    }

    /** Indicadores agregados para el dashboard de banca web. */
    @GetMapping("/dashboard")
    public DashboardDto dashboard() {
        return service.dashboard();
    }
}
