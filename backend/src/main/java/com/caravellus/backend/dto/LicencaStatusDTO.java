package com.caravellus.backend.dto;

import com.caravellus.backend.service.LicencaService;
import com.caravellus.backend.service.SolicitacaoLicencaService;

import java.time.LocalDateTime;

/**
 * Situação da licença exibida na tela de ativação.
 * email: e-mail do cliente gravado na licença (vazio em licenças antigas).
 * mensagem: motivo de a licença gravada não valer (ex.: copiada de outra máquina), ou null.
 * solicitacao: último pedido de licença enviado desta máquina ao fornecedor, ou null.
 */
public record LicencaStatusDTO(boolean ativa, String cliente, String email, String codigoMaquina, String mensagem,
                               Solicitacao solicitacao) {

    public record Solicitacao(String nome, String email, String telefone, LocalDateTime enviadaEm) {}

    public static LicencaStatusDTO de(LicencaService service, SolicitacaoLicencaService solicitacoes) {
        LicencaService.Licenca licenca = service.getLicenca();
        SolicitacaoLicencaService.Solicitacao s = solicitacoes.getUltima();
        return new LicencaStatusDTO(
                licenca != null,
                licenca != null ? licenca.cliente() : null,
                licenca != null ? licenca.email() : null,
                service.getCodigoMaquina(),
                service.getProblema(),
                s != null ? new Solicitacao(s.nome(), s.email(), s.telefone(), s.enviadaEm()) : null);
    }
}
