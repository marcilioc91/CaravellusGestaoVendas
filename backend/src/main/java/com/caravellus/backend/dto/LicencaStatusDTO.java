package com.caravellus.backend.dto;

import com.caravellus.backend.service.LicencaService;

/**
 * Situação da licença exibida na tela de ativação.
 * mensagem: motivo de a licença gravada não valer (ex.: copiada de outra máquina), ou null.
 */
public record LicencaStatusDTO(boolean ativa, String cliente, String codigoMaquina, String mensagem) {

    public static LicencaStatusDTO de(LicencaService service) {
        LicencaService.Licenca licenca = service.getLicenca();
        return new LicencaStatusDTO(
                licenca != null,
                licenca != null ? licenca.cliente() : null,
                service.getCodigoMaquina(),
                service.getProblema());
    }
}
