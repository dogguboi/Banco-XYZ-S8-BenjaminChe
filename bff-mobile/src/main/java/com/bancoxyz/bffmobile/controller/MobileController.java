package com.bancoxyz.bffmobile.controller;

import com.bancoxyz.bffmobile.dto.CuentaMovilDto;
import com.bancoxyz.bffmobile.dto.ResumenMovilDto;
import com.bancoxyz.bffmobile.service.MobileBankService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/mobile")
public class MobileController {

    private final MobileBankService service;

    public MobileController(MobileBankService service) {
        this.service = service;
    }

    /** Cuentas en versión ligera, paginadas. */
    @GetMapping("/cuentas")
    public List<CuentaMovilDto> cuentas(@RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "5") int size) {
        return service.listarCuentas(page, size);
    }

    /** Detalle mínimo de una cuenta. */
    @GetMapping("/cuentas/{id}")
    public ResponseEntity<CuentaMovilDto> cuenta(@PathVariable String id) {
        return service.obtenerCuenta(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Resumen compacto para la home de la app. */
    @GetMapping("/resumen")
    public ResumenMovilDto resumen() {
        return service.resumen();
    }
}
