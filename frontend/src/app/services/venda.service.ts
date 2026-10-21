import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { RelatorioInventarioItem, Venda, VendaResposta } from '../models/venda';

@Injectable({
  providedIn: 'root',
})
export class VendaService {
  // No venda.service.ts
atualizarItens(vendaId: number, dados: { produtos: any[] }) {
  return this.http.put(`${this.api}/${vendaId}/itens`, dados);
}
  private api = 'http://localhost:8080/vendas';

  constructor(private http: HttpClient) {}

  realizarVenda(venda: Venda) {
    return this.http.post(this.api, venda);
  }

  listarTodas(inicio?: string, fim?: string) {
    return this.http.get<VendaResposta[]>(this.api, { params: this.periodo(inicio, fim) });
  }

  listarPorCliente(clienteId: number) {
    return this.http.get<VendaResposta[]>(`${this.api}/cliente/${clienteId}`);
  }

  atualizarPagamento(id: number, dados: { formaPagamento: string; valorPago: number }) {
    return this.http.patch(`${this.api}/${id}/pagamento`, dados);
  }

  private periodo(inicio?: string, fim?: string) {
    let params = new HttpParams();
    if (inicio) params = params.set('inicio', inicio);
    if (fim) params = params.set('fim', fim);
    return params;
  }

  getRelatorioInventario(inicio?: string, fim?: string) {
    return this.http.get<RelatorioInventarioItem[]>(`${this.api}/relatorio/inventario`, { params: this.periodo(inicio, fim) });
  }
}
