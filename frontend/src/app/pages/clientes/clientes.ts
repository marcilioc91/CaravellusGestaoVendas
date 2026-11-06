import { AfterViewInit, ChangeDetectorRef, Component, Inject, OnInit, ViewChild } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatPaginator, MatPaginatorModule } from '@angular/material/paginator';
import { MatSort, MatSortModule } from '@angular/material/sort';
import { MatTableDataSource } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { Router, RouterLink } from '@angular/router';
import { NgxMaskDirective, provideNgxMask } from 'ngx-mask';
import { Cliente, ImportacaoClientes } from '../../models/cliente';
import { VendaResposta } from '../../models/venda';
import { AuthService } from '../../services/auth.service';
import { ClienteService } from '../../services/cliente.service';
import { VendaService } from '../../services/venda.service';
import { CadastroModal } from '../cadastro-modal/cadastro-modal';
import { ImportarClientesDialog } from './importar-clientes-dialog';
import { formatarTelefone, normalizarTexto } from '../../utils/utils';

// ── Diálogo de confirmação ────────────────────────────────────────────────────
@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  imports: [MatButtonModule, MatDialogModule],
  templateUrl: 'cliente-dialog-confirm.html',
})
export class ConfirmDialog {
  constructor(
    public dialogRef: MatDialogRef<ConfirmDialog>,
    @Inject(MAT_DIALOG_DATA) public data: { titulo: string; mensagem: string }
  ) { }
}

// ── Diálogo de edição ──────────────────────────────────────────────────────────
@Component({
  selector: 'app-cliente-edit-dialog',
  standalone: true,
  imports: [FormsModule, MatFormFieldModule, MatInputModule, MatButtonModule, NgxMaskDirective],
  providers: [provideNgxMask()],
  templateUrl: './cliente-edit-dialog.html',
})
export class ClienteEditDialog {
  form = { nome: '', cpf: '', telefone: '', obs: '' };
  erro = '';

  constructor(
    private dialogRef: MatDialogRef<ClienteEditDialog>,
    private clienteService: ClienteService,
    @Inject(MAT_DIALOG_DATA) public data: { cliente: Cliente }
  ) {
    const { pessoa, obs } = data.cliente;
    this.form = {
      nome: pessoa.nome,
      cpf: pessoa.cpf ?? '',
      telefone: pessoa.telefone ?? '',
      obs: obs ?? '',
    };
  }

  salvar() {
    const atualizado: Cliente = {
      pessoa: { nome: this.form.nome, cpf: this.form.cpf, telefone: this.form.telefone || undefined },
      obs: this.form.obs || undefined,
    };
    this.clienteService.atualizar(this.data.cliente.id!, atualizado).subscribe({
      next: () => this.dialogRef.close(true),
      error: () => { this.erro = 'Erro ao salvar. Verifique os dados.'; }
    });
  }

  fechar() { this.dialogRef.close(); }
}

// ── Diálogo de histórico de compras ───────────────────────────────────────────
@Component({
  selector: 'app-historico-cliente-dialog',
  standalone: true,
  imports: [CommonModule, DatePipe, MatTableModule, MatButtonModule, MatExpansionModule],
  templateUrl: './historico-cliente-dialog.html',
})
export class HistoricoClienteDialog implements OnInit {
  vendas: VendaResposta[] = [];
  colunas = ['produto', 'quantidade', 'subtotal'];

  constructor(
    private dialogRef: MatDialogRef<HistoricoClienteDialog>,
    private vendaService: VendaService,
    private cdr: ChangeDetectorRef,
    @Inject(MAT_DIALOG_DATA) public data: { clienteId: number; nomeCliente: string }
  ) { }

  ngOnInit() {
    this.vendaService.listarPorCliente(this.data.clienteId).subscribe(dados => {
      this.vendas = dados;
      this.cdr.detectChanges();
    });
  }

  total(venda: VendaResposta): number {
    return venda.itens?.reduce((acc, i) => acc + i.quantidade * +i.precoUnitario, 0) ?? 0;
  }

  fechar() { this.dialogRef.close(); }
}

// ── Página de clientes ─────────────────────────────────────────────────────────
@Component({
  selector: 'app-clientes',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatAutocompleteModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatPaginatorModule,
    MatSortModule,
    MatTableModule,
    MatDialogModule,
    RouterLink,
  ],
  templateUrl: './clientes.html',
  styleUrl: './clientes.css',
})
export class Clientes implements OnInit, AfterViewInit {
  dataSource = new MatTableDataSource<Cliente>();
  colunas = ['nome', 'telefone', 'obs', 'acoes'];
  filtroCtrl = new FormControl('');
  nomesFiltrados: string[] = [];

  @ViewChild(MatPaginator) paginator!: MatPaginator;
  @ViewChild(MatSort) sort!: MatSort;

  constructor(
    private clienteService: ClienteService,
    private authService: AuthService,
    private dialog: MatDialog,
    private snackBar: MatSnackBar,
    private router: Router,
    private cdr: ChangeDetectorRef
  ) { }

  ngOnInit() {
    this.filtroCtrl.valueChanges.subscribe(val => {
      const v = normalizarTexto(val);
      this.dataSource.filter = v;
      if (this.dataSource.paginator) this.dataSource.paginator.firstPage();
      this.nomesFiltrados = v
        ? [...new Set(this.dataSource.data.map(c => c.pessoa.nome).filter(n => normalizarTexto(n).includes(v)))].slice(0, 8)
        : [];
    });
    this.carregar();
  }

  ngAfterViewInit() {
    this.dataSource.paginator = this.paginator;
    this.dataSource.sort = this.sort;
    this.dataSource.sortingDataAccessor = (item, property) =>
      property === 'nome' ? item.pessoa.nome.toLowerCase() : '';
    this.dataSource.filterPredicate = (data, filter) =>
      normalizarTexto(data.pessoa.nome).includes(filter);
  }

  carregar() {
    this.clienteService.listar().subscribe({
      next: dados => {
        this.dataSource.data = dados;
        this.cdr.detectChanges();
      },
      error: () => {
        this.snackBar.open('Erro ao carregar clientes.', 'Fechar', { duration: 4000 });
      }
    });
  }

  abrirFormulario() {
    const ref = this.dialog.open(CadastroModal, {
      width: '400px',
      data: { modo: 'cliente' }
    });
    ref.afterClosed().subscribe(sucesso => {
      if (!sucesso) return;
      this.snackBar.open('Cliente cadastrado com sucesso!', 'Fechar', { duration: 3000 });
      this.carregar();
    });
  }

  abrirImportacao() {
    const ref = this.dialog.open(ImportarClientesDialog, { width: '860px', maxWidth: '95vw' });
    ref.afterClosed().subscribe((resultado?: ImportacaoClientes) => {
      if (!resultado) return;
      const ignorados = resultado.ignorados ? `, ${resultado.ignorados} já cadastrado(s) ignorado(s)` : '';
      this.snackBar.open(`${resultado.incluidos} cliente(s) importado(s)${ignorados}.`, 'Fechar', { duration: 5000 });
      this.carregar();
    });
  }

  abrirEdicao(cliente: Cliente) {
    const ref = this.dialog.open(ClienteEditDialog, {
      width: '420px',
      data: { cliente }
    });
    ref.afterClosed().subscribe(sucesso => {
      if (!sucesso) return;
      this.snackBar.open('Cliente atualizado!', 'Fechar', { duration: 3000 });
      this.carregar();
    });
  }

  verCompras(cliente: Cliente) {
    this.dialog.open(HistoricoClienteDialog, {
      width: '600px',
      data: { clienteId: cliente.id, nomeCliente: cliente.pessoa.nome }
    });
  }

  excluir(cliente: Cliente) {
    if (!cliente.id) return;
    const ref = this.dialog.open(ConfirmDialog, {
      width: '380px',
      data: {
        titulo: 'Excluir cliente',
        mensagem: `Deseja excluir "${cliente.pessoa.nome}"? Todas as vendas, o usuário e os dados pessoais vinculados serão removidos permanentemente.`,
      },
      panelClass: 'confirm-dialog-panel'
    });
    ref.afterClosed().subscribe((confirmado: boolean) => {
      if (!confirmado) return;
      const usuarioLogado = this.authService.getUsuarioLogado();
      const eOProprio = !!usuarioLogado?.pessoa?.id && usuarioLogado.pessoa.id === cliente.pessoa.id;
      this.clienteService.excluir(cliente.id!).subscribe({
        next: () => {
          this.snackBar.open('Cliente excluído.', 'Fechar', { duration: 3000 });
          if (eOProprio) {
            this.authService.logout();
            this.router.navigate(['/login']);
          } else {
            this.carregar();
          }
        },
        error: () => {
          this.snackBar.open('Erro ao excluir cliente.', 'Fechar', { duration: 3000 });
        }
      });
    });
  }
  formatarTelefone = formatarTelefone;
}
