export interface Pessoa {
  id?: number;
  nome: string;
  cpf?: string;
  telefone?: string;
}

export interface Usuario {
  id?: number;
  pessoa?: Pessoa;
  usuarioLogin: string;
  email: string;
  senha?: string;
  perfil?: 'ADMIN' | 'OPERADOR';
  trocarSenha?: boolean;
  /** Token da sessão, devolvido pelo login (ausente enquanto a troca de senha estiver pendente) */
  token?: string;
}

export interface LoginRequest {
  login: string;
  senha: string;
}
