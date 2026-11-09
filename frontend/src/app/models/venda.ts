import { Pessoa } from "./usuario";

export interface VendaProduto {
  produtoId: number;
  quantidade: number;
}

export interface Venda {
  clienteId: number;
  produtos: VendaProduto[];
  formaPagamento: string;
  valorPago: number;
}

export interface RelatorioInventarioItem {
  nomeProduto: string;
  nomeCategoria: string | null;
  quantidadeVendida: number;
  totalReceita: number;
  totalCusto: number;
  lucro: number;
}

export interface VendaItemResposta {
  id: number;
  produto: { id: number; nome: string };
  quantidade: number;
  precoUnitario: number;
}

export interface VendaResposta {
  id: number;
  cliente: { id: number; pessoa: { id?: number; nome: string; cpf?: string } };
  usuario: {
    pessoa: Pessoa; id: number; usuarioLogin: string
  };
  itens: VendaItemResposta[];
  data_venda: string;
  formaPagamento: string;
  valorPago?: number;
}
