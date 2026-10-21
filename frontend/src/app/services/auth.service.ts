import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { LoginRequest, Usuario } from '../models/usuario';

export interface CadastroRequest {
  nome: string;
  cpf?: string;
  email: string;
  telefone?: string;
  usuario: string;
  senha: string;
  obs?: string;
  /** Grava mesmo havendo cadastro parecido (é outra pessoa) */
  confirmarDuplicidade?: boolean;
  /** "Sou eu": cria o acesso para este cadastro existente */
  pessoaIdExistente?: number;
}

/** Cliente já cadastrado com o CPF informado no cadastro de usuário */
export interface ClienteExistente {
  nome: string;
  telefone?: string;
}

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private api = "http://localhost:8080/auth"

  constructor(private http: HttpClient) {}

  /** Devolve o usuário com o token da sessão (sem token se ainda precisar trocar a senha) */
  login(loginRequest: LoginRequest) {
    return this.http.post<Usuario>(this.api + "/login", loginRequest)
  }

  cadastrar(dados: CadastroRequest) {
    return this.http.post(this.api + "/cadastro", dados)
  }

  /** 404: CPF novo; 409: CPF já possui usuário */
  clientePorCpf(cpf: string) {
    return this.http.get<ClienteExistente>(`${this.api}/cadastro/cliente-por-cpf/${cpf.replace(/\D/g, '')}`)
  }

  trocarSenha(login: string, senhaAtual: string, novaSenha: string) {
    return this.http.post<Usuario>(this.api + "/trocar-senha", { login, senhaAtual, novaSenha })
  }

  solicitarRecuperacaoSenha(identificador: string) {
    return this.http.post(this.api + "/recuperar-senha/solicitar", { identificador }, { responseType: 'text' })
  }

  validarCodigoRecuperacao(identificador: string, codigo: string) {
    return this.http.post(this.api + "/recuperar-senha/validar", { identificador, codigo }, { responseType: 'text' })
  }

  redefinirSenhaComCodigo(identificador: string, codigo: string, novaSenha: string) {
    return this.http.post(this.api + "/recuperar-senha/redefinir", { identificador, codigo, novaSenha }, { responseType: 'text' })
  }

  setUsuarioLogado(usuario: any) {
    sessionStorage.setItem('usuario', JSON.stringify(usuario));
  }

  getUsuarioLogado(): any {
    const u = sessionStorage.getItem('usuario');
    return u ? JSON.parse(u) : null;
  }

  /** Token enviado em "Authorization: Bearer" (ver auth.interceptor) */
  getToken(): string | null {
    return this.getUsuarioLogado()?.token ?? null;
  }

  /** Só controla o que a tela mostra: o backend confere o perfil pelo token */
  isAdmin(): boolean {
    return this.getUsuarioLogado()?.perfil === 'ADMIN';
  }

  listarUsuarios() {
    return this.http.get<Usuario[]>(`${this.api}/usuarios`);
  }

  atualizarPerfil(id: number, perfil: string) {
    return this.http.patch<Usuario>(`${this.api}/usuarios/${id}/perfil`, { perfil });
  }

  resetSenha(id: number, novaSenha: string) {
    return this.http.patch(`${this.api}/usuarios/${id}/reset-senha`, { novaSenha }, { responseType: 'text' });
  }

  /** Encerra a sessão no backend (sem esperar a resposta) e esquece o usuário */
  logout() {
    const token = this.getToken();
    if (token) {
      // Cabeçalho explícito: o usuário sai do sessionStorage antes de a requisição partir
      this.http.post(this.api + "/logout", {}, { headers: { Authorization: `Bearer ${token}` } }).subscribe({ error: () => {} });
    }
    sessionStorage.removeItem('usuario');
  }
}
