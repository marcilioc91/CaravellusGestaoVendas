import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Categoria } from '../models/categoria';

@Injectable({
  providedIn: 'root',
})
export class CategoriaService {
  private api = 'http://localhost:8080/categorias';

  constructor(private http: HttpClient) {}

  listar() {
    return this.http.get<Categoria[]>(this.api);
  }

  salvar(categoria: Categoria) {
    return this.http.post<Categoria>(this.api, categoria);
  }

  atualizar(id: number, categoria: Categoria) {
    return this.http.put<Categoria>(`${this.api}/${id}`, categoria);
  }

  excluir(id: number) {
    return this.http.delete(`${this.api}/${id}`);
  }
}
