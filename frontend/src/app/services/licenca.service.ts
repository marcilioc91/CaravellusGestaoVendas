import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { tap } from 'rxjs';

/** Pedido de licença já enviado desta máquina ao fornecedor */
export interface SolicitacaoLicenca {
  nome: string;
  email: string;
  telefone: string;
  /** Data e hora do envio (yyyy-MM-dd HH:mm:ss) */
  enviadaEm: string;
}

export interface LicencaStatus {
  ativa: boolean;
  cliente: string | null;
  /** E-mail do cliente gravado na licença (vazio em licenças antigas) */
  email: string | null;
  codigoMaquina: string | null;
  /** Motivo de a licença gravada não valer (ex.: copiada de outra máquina) */
  mensagem: string | null;
  solicitacao: SolicitacaoLicenca | null;
}

@Injectable({
  providedIn: 'root',
})
export class LicencaService {
  private api = "http://localhost:8080/licenca"

  /** Depois de confirmada, não consulta o backend a cada navegação */
  private ativa = false;

  constructor(private http: HttpClient) {}

  status() {
    return this.http.get<LicencaStatus>(this.api + "/status").pipe(tap(s => (this.ativa = s.ativa)))
  }

  /** Primeiro acesso: o backend envia ao fornecedor o pedido com o código desta máquina */
  solicitar(dados: { nome: string; email: string; telefone?: string }) {
    return this.http.post<LicencaStatus>(this.api + "/solicitar", dados)
  }

  ativar(chave: string) {
    return this.http.post<LicencaStatus>(this.api + "/ativar", { chave }).pipe(tap(s => (this.ativa = s.ativa)))
  }

  estaAtiva(): boolean {
    return this.ativa;
  }

  /** Chamado quando a API responde 423 (sistema bloqueado) */
  marcarBloqueado() {
    this.ativa = false;
  }
}
