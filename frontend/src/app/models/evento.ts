/** Evento (acampamento, conferência...): separa clientes, produtos, vendas e auditoria */
export interface Evento {
  /** Número de 4 dígitos (0001, 0002...) gerado pelo sistema; não muda depois de criado */
  codigo: string;
  nome: string;
  ativo?: boolean;
  dataCriacao?: string;
}

/** Quanto o evento tem: mostrado no aviso antes de excluir */
export interface ResumoEvento {
  codigo: string;
  nome: string;
  clientes: number;
  categorias: number;
  produtos: number;
  vendas: number;
}
