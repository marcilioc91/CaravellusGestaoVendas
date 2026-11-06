import { AfterViewInit, ChangeDetectorRef, Component, ElementRef, Inject, OnInit, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule, MatIcon } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatTableModule } from '@angular/material/table';
import { MatInputModule } from '@angular/material/input';
import { MatAutocompleteModule, MatAutocompleteSelectedEvent } from '@angular/material/autocomplete';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Produto } from '../../models/produto';
import { Cliente } from '../../models/cliente';
import { ProdutoService } from '../../services/produto.service';
import { VendaService } from '../../services/venda.service';
import { ClienteService } from '../../services/cliente.service';
import { AuthService } from '../../services/auth.service';
import { formatarTelefone, normalizarTexto } from '../../utils/utils';
import { Footer } from '../../footer/footer';

interface ItemCarrinho {
  produto: Produto;
  quantidade: number;
}

interface GrupoCategoria {
  nome: string;
  produtos: Produto[];
}

interface DadosConfirmacao {
  cliente: Cliente;
  itens: ItemCarrinho[];
  total: number;
}

// ── Diálogo de confirmação da venda ───────────────────────────────────────────
@Component({
  selector: 'app-venda-confirmacao-dialog',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatFormFieldModule,
    MatSelectModule,
    MatInputModule,
    MatButtonModule,
    MatTableModule,
    MatIcon,
    MatDialogModule
],
  templateUrl: './venda-confirmacao-dialog.html',
  styleUrl: './vendas.css',
})
export class VendaConfirmacaoDialog implements OnInit {
  colunas = ['produto', 'quantidade', 'subtotal', 'acoes'];
  formaPagamento = '';
  valorPago: number;

  formas = [
    { valor: 'DINHEIRO', label: 'Dinheiro' },
    { valor: 'PIX', label: 'PIX' },
    { valor: 'CARTAO_CREDITO', label: 'Cartão de Crédito' },
    { valor: 'CARTAO_DEBITO', label: 'Cartão de Débito' },
    { valor: 'VOUCHER', label: 'Voucher' },
    { valor: 'PENDENTE', label: 'Deixar Pendente' },
  ];

  constructor(
    private dialogRef: MatDialogRef<VendaConfirmacaoDialog>,
    @Inject(MAT_DIALOG_DATA) public data: DadosConfirmacao
  ) {
    this.valorPago = this.calcularValorNoDialogo();
  }
  ngOnInit() {
    this.atualizarValores();
  }
  
  atualizarValores() {
    const novoTotal = this.calcularValorNoDialogo();
    this.data.total = novoTotal;
    this.valorPago = novoTotal;
  }
  calcularValorNoDialogo(): number {
    return this.data.itens.reduce((acc, i) => acc + i.produto.preco * (i.quantidade || 0), 0);
  }

  confirmar() {
    const vPago = this.formaPagamento === 'PENDENTE' ? 0 : this.valorPago;
    this.dialogRef.close({ formaPagamento: this.formaPagamento, valorPago: vPago });
  }

  cancelar() { this.dialogRef.close(null); }

  remover(produtoId: number) {

    this.data.itens = this.data.itens.filter(i => i.produto.id !== produtoId);

    if (this.data.itens.length === 0) {
      this.dialogRef.close(null);
    }

    this.atualizarValores();
  }

  onQuantidadeChange() {
    this.atualizarValores();
  }
}

// ── Página de vendas ───────────────────────────────────────────────────────────
@Component({
  selector: 'app-vendas',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatSelectModule,
    MatFormFieldModule,
    MatInputModule,
    MatAutocompleteModule,
    MatDialogModule,
    Footer
  ],
  templateUrl: './vendas.html',
  styleUrl: './vendas.css',
})
export class Vendas implements OnInit, AfterViewInit {
  formatarTelefone = formatarTelefone;
  produtos: Produto[] = [];
  grupos: GrupoCategoria[] = [];
  grupoSelecionado: GrupoCategoria | null = null;
  clientes: Cliente[] = [];
  filteredClientes: Cliente[] = [];
  carrinho: ItemCarrinho[] = [];
  clienteSelecionado: Cliente | null = null;
  clienteSearchText = '';
  @ViewChild('campoCliente') campoCliente?: ElementRef<HTMLInputElement>;
  carregando = true;

  colunasCarrinho = ['produto', 'preco', 'quantidade', 'subtotal', 'acoes'];

  constructor(
    public produtoService: ProdutoService,
    private vendaService: VendaService,
    private clienteService: ClienteService,
    private auth: AuthService,
    private dialog: MatDialog,
    private snackBar: MatSnackBar,
    private cdr: ChangeDetectorRef
  ) { }

  ngOnInit(): void {
    this.carregarProdutos();
    this.clienteService.listar().subscribe({
      next: res => {
        this.clientes = res;
        this.filteredClientes = res;
      },
      error: () => this.snackBar.open('Erro ao carregar clientes.', 'Fechar', { duration: 3000 })
    });
  }

   ngAfterViewInit(): void {
    this.focarCliente();
  }

  private focarCliente() {
    setTimeout(() => this.campoCliente?.nativeElement.focus());
  }

  /** "Todas": uma seção por categoria; categoria escolhida no filtro: só a seção dela */
  secoesVisiveis(): GrupoCategoria[] {
    if (!this.grupoSelecionado) return [];
    return this.grupoSelecionado === this.grupos[0] ? this.grupos.slice(1) : [this.grupoSelecionado];
  }

  private agruparPorCategoria(produtos: Produto[]): GrupoCategoria[] {
    const mapa = new Map<number, GrupoCategoria>();
    const semCategoria: Produto[] = [];
    for (const p of produtos) {
      const cat = p.categoria;
      if (!cat?.id) {
        semCategoria.push(p);
        continue;
      }
      if (!mapa.has(cat.id)) mapa.set(cat.id, { nome: cat.nome, produtos: [] });
      mapa.get(cat.id)!.produtos.push(p);
    }
    const grupos = [...mapa.values()].sort((a, b) => a.nome.localeCompare(b.nome));
    if (semCategoria.length > 0) grupos.push({ nome: 'Sem categoria', produtos: semCategoria });
    return [{ nome: 'Todas', produtos: grupos.flatMap(g => g.produtos) }, ...grupos];
  }

  filtrarClientes(valor: string) {
    const filtro = normalizarTexto(valor);
    this.filteredClientes = this.clientes.filter(c =>
      normalizarTexto(c.pessoa.nome).includes(filtro)
    );
    // Editou o nome depois de escolher: o cliente deixa de valer e os produtos voltam a ficar bloqueados
    if (this.clienteSelecionado && valor !== this.clienteSelecionado.pessoa.nome) this.clienteSelecionado = null;
  }

  onClienteSelecionado(event: MatAutocompleteSelectedEvent) {
    this.clienteSelecionado = this.clientes.find(c => c.pessoa.nome === event.option.value) ?? null;
  }

  /** Produtos só ficam disponíveis com um cliente escolhido e se ainda houver estoque */
  podeAdicionar(produto: Produto): boolean {
    return !!this.clienteSelecionado && this.getEstoqueDisponivel(produto) > 0;
  }

  adicionar(produto: Produto) {
    if (!this.podeAdicionar(produto)) return;
    const item = this.carrinho.find(i => i.produto.id === produto.id);
    if (item) {
      item.quantidade++;
    } else {
      this.carrinho = [...this.carrinho, { produto, quantidade: 1 }];
    }
    this.snackBar.open('"' + produto.nome + '" adicionado ao carrinho.', 'Fechar', { duration: 3000 });
  }

  total(): number {
    return this.carrinho.reduce((acc, i) => acc + i.produto.preco * i.quantidade, 0);
  }

  abrirCarrinho() {
    if (!this.clienteSelecionado) {
      this.snackBar.open('Selecione um cliente para continuar.', 'Fechar', { duration: 3000 });
      return;
    }
    if (this.carrinho.length === 0) {
      this.snackBar.open('Adicione pelo menos um produto ao carrinho.', 'Fechar', { duration: 3000 });
      return;
    }

    const usuario = this.auth.getUsuarioLogado();
    if (!this.clienteSelecionado.id || !usuario?.id) {
      this.snackBar.open('Erro ao processar cliente ou usuário.', 'Fechar', { duration: 3000 });
      return;
    }

    const ref = this.dialog.open(VendaConfirmacaoDialog, {
      width: '580px',
      data: {
        cliente: this.clienteSelecionado,
        itens: this.carrinho,
      }
    });

    ref.afterClosed().subscribe((resultado: { formaPagamento: string; valorPago: number } | null) => {
      this.carrinho = [...ref.componentInstance.data.itens];
      this.cdr.detectChanges();
      if (!resultado) return;

      const venda = {
        clienteId: this.clienteSelecionado!.id!,
        usuarioId: usuario.id,
        usuarioNome: usuario?.pessoa?.nome ?? usuario?.usuarioLogin ?? '',
        produtos: this.carrinho.map(i => ({
          produtoId: i.produto.id!,
          quantidade: i.quantidade
        })),
        formaPagamento: resultado.formaPagamento,
        valorPago: resultado.valorPago,
      };

      this.vendaService.realizarVenda(venda).subscribe({
        next: () => {
          const totalFormatado = this.total().toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
          this.snackBar.open(
            `Venda registrada para ${this.clienteSelecionado?.pessoa?.nome}! 
            Total: ${totalFormatado}`, 'Fechar', { duration: 5000 }
          );
          this.novaVenda();
        },
        error: () => {
          this.snackBar.open('Erro ao registrar venda.', 'Fechar', { duration: 3000 });
        }
      });
    });
  }
  novaVenda() {
    this.carrinho = [];
    this.clienteSelecionado = null;
    this.clienteSearchText = '';
    this.filteredClientes = this.clientes;
    this.carregarProdutos();
    this.focarCliente();
  }
  carregarProdutos() {
    const categoriaAtual = this.grupoSelecionado?.nome;
    this.produtoService.listar().subscribe({
      next: produtos => {
        this.produtos = produtos;
        this.grupos = this.agruparPorCategoria(produtos);
        this.grupoSelecionado = this.grupos.find(g => g.nome === categoriaAtual) ?? this.grupos[0];
        this.carregando = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.snackBar.open('Erro ao carregar produtos.', 'Fechar', { duration: 4000 });
        this.carregando = false;
        this.cdr.detectChanges();
      }
    });
  }

  quantidadeTotalNoCarrinho(): number {
    return this.carrinho.reduce((acc, item) => acc + item.quantidade, 0);
  }
  
  getEstoqueDisponivel(produto: Produto): number {
    const itemNoCarrinho = this.carrinho.find(i => i.produto.id === produto.id);
    const quantidadeNoCarrinho = itemNoCarrinho ? itemNoCarrinho.quantidade : 0;
    return produto.estoque - quantidadeNoCarrinho;
  }
}
