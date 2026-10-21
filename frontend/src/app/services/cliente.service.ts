import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Cliente, ImportacaoClientes } from '../models/cliente';

@Injectable({
  providedIn: 'root',
})
export class ClienteService {
  private api = 'http://localhost:8080/clientes';

  constructor(private http: HttpClient) {}

  listar() {
    return this.http.get<Cliente[]>(this.api);
  }

  /** 409 com { semelhantes }: cadastro parecido já existe; reenviar com confirmarDuplicidade para gravar mesmo assim */
  salvar(cliente: Cliente, confirmarDuplicidade = false) {
    return this.http.post<Cliente>(this.api, cliente, {
      params: { confirmarDuplicidade },
    });
  }

  /** confirmar=false: só a prévia; confirmar=true: grava os clientes novos */
  importar(arquivo: File, confirmar: boolean) {
    const dados = new FormData();
    dados.append('arquivo', arquivo);
    return this.http.post<ImportacaoClientes>(`${this.api}/importar`, dados, {
      params: { confirmar },
    });
  }

  atualizar(id: number, cliente: Cliente) {
    return this.http.put<Cliente>(`${this.api}/${id}`, cliente);
  }

  excluir(id: number) {
    return this.http.delete(`${this.api}/${id}`);
  }
}
