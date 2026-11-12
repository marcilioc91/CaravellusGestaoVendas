import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Evento, ResumoEvento } from '../models/evento';

@Injectable({
  providedIn: 'root',
})
export class EventoService {
  private api = 'http://localhost:8080/eventos';

  constructor(private http: HttpClient) {}

  /** Eventos da tela de login (não exige estar logado) */
  listarAtivos() {
    return this.http.get<Evento[]>(`${this.api}/ativos`);
  }

  /** Todos, inclusive inativos (só ADMIN) */
  listar() {
    return this.http.get<Evento[]>(this.api);
  }

  /** O código é gerado pelo backend (próximo número) */
  criar(nome: string) {
    return this.http.post<Evento>(this.api, { nome });
  }

  resumo(codigo: string) {
    return this.http.get<ResumoEvento>(`${this.api}/${codigo}/resumo`);
  }

  /** Exclui o evento e todos os dados dele; devolve o que foi apagado */
  excluir(codigo: string) {
    return this.http.delete<ResumoEvento>(`${this.api}/${codigo}`);
  }

  /** Muda o nome e ativa ou desativa; o código não muda */
  atualizar(codigo: string, evento: Evento) {
    return this.http.put<Evento>(`${this.api}/${codigo}`, evento);
  }
}
