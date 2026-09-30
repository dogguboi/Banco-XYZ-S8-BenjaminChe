package com.bancoxyz.bffatm.controller;

import com.bancoxyz.bffatm.dto.RetiroRequest;
import com.bancoxyz.bffatm.dto.RetiroResponse;
import com.bancoxyz.bffatm.dto.SaldoDto;
import com.bancoxyz.bffatm.service.AtmBankService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/atm")
public class AtmController {

    private final AtmBankService service;

    public AtmController(AtmBankService service) {
        this.service = service;
    }

    /** Consulta de saldo de una cuenta. */
    @GetMapping("/cuentas/{id}/saldo")
    public ResponseEntity<SaldoDto> saldo(@PathVariable String id, Principal principal) {
        return service.consultarSaldo(id, principal.getName())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Retiro de efectivo con validaciones de integridad y persistencia transaccional. */
    @PostMapping("/cuentas/{id}/retiro")
    public RetiroResponse retiro(@PathVariable String id,
                                 @RequestBody RetiroRequest request,
                                 Principal principal) {
        return service.retirar(id, request.monto(), principal.getName());
    }

    @ExceptionHandler(AtmBankService.OperacionRechazadaException.class)
    public ResponseEntity<Map<String, String>> operacionRechazada(
            AtmBankService.OperacionRechazadaException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of("operacion", "RECHAZADA", "motivo", e.getMessage()));
    }
}
