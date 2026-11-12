import { ChangeDetectionStrategy, ChangeDetectorRef, Component, ElementRef, Inject, ViewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { NgxMaskDirective, provideNgxMask } from 'ngx-mask';
import { MatIcon } from "@angular/material/icon";
import { CommonModule } from '@angular/common';
import { AuthService, CadastroRequest } from '../../services/auth.service';
import { ClienteService } from '../../services/cliente.service';
import { Cliente, PessoaSemelhante } from '../../models/cliente';
import { cpfValido, emailValido, formatarTelefone, SENHA_TAMANHO_MINIMO, senhaValida } from '../../utils/utils';

export interface CadastroModalData {
  modo: 'usuario' | 'cliente';
  /** Modo usuário: evento escolhido na tela de login (o novo usuário vira cliente dele) */
  evento?: string;
}

@Component({
  selector: 'app-cadastro-modal',
  standalone: true,
  imports: [
    FormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    NgxMaskDirective,
    MatIcon,
    CommonModule
  ],
  providers: [provideNgxMask()],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './cadastro-modal.html',
  styleUrl: './cadastro-modal.css',
})
export class CadastroModal {
  conta = {
    nome: '',
    cpf: '',
    email: '',
    telefone: '',
    usuario: '',
    senha: '',
    obs: ''
  }

  /**
   * Tela "Dados para Login": o campo Login é criado de novo cada vez que a tela aparece (@if do step),
   * e este setter roda nesse momento. O setTimeout espera o campo estar na tela para receber o foco.
   */
  @ViewChild('campoLogin') set campoLogin(campo: ElementRef<HTMLInputElement> | undefined) {
    if (campo) setTimeout(() => campo.nativeElement.focus());
  }

  /** CPF de um cliente já cadastrado: o cadastro cria o acesso para ele (promoção a usuário) */
  clienteExistente = false;
  /** CPF que já pertence a um usuário: não deixa avançar */
  cpfComUsuario = false;
  /** Cadastros parecidos apontados pelo backend: grava só depois que a pessoa decide */
  semelhantes: PessoaSemelhante[] = [];
  formatarTelefone = formatarTelefone;
  senhaValida = senhaValida;
  senhaMinima = SENHA_TAMANHO_MINIMO;
  private cpfConsultado = '';

  validarCpf() {
    if (this.conta.cpf && !cpfValido(this.conta.cpf)) {
      this.erro = 'CPF inválido.';
      this.cdr.markForCheck();
    } else {
      this.erro = '';
    }
    if (!this.modoCliente) {
      this.buscarClientePorCpf();
    }
  }

  private buscarClientePorCpf() {
    const cpf = (this.conta.cpf ?? '').replace(/\D/g, '');
    if (cpf === this.cpfConsultado) return;
    this.cpfConsultado = cpf;

    // CPF trocado: os dados preenchidos eram de outra pessoa
    if (this.clienteExistente) {
      this.conta.nome = '';
      this.conta.telefone = '';
    }
    this.clienteExistente = false;
    this.cpfComUsuario = false;
    this.cdr.markForCheck();
    if (!cpfValido(cpf)) return;

    this.auth.clientePorCpf(cpf).subscribe({
      next: (cliente) => {
        if (cpf !== this.cpfConsultado) return;
        this.clienteExistente = true;
        this.conta.nome = cliente.nome ?? '';
        this.conta.telefone = cliente.telefone ?? '';
        this.conta.obs = '';
        this.cdr.markForCheck();
      },
      error: (err) => {
        if (cpf !== this.cpfConsultado || err.status !== 409) return;
        this.cpfComUsuario = true;
        this.erro = typeof err.error === 'string' ? err.error : 'Este CPF já possui usuário.';
        this.cdr.markForCheck();
      }
    });
  }

  get isCpfValido(): boolean {
    return !this.conta.cpf || cpfValido(this.conta.cpf);
  }

  get isEmailValido(): boolean {
    return emailValido(this.conta.email);
  }

  erro = '';

  constructor(
    private dialogRef: MatDialogRef<CadastroModal>,
    private auth: AuthService,
    private clienteService: ClienteService,
    private cdr: ChangeDetectorRef,
    @Inject(MAT_DIALOG_DATA) public data: CadastroModalData
  ) {}

  get modoCliente() {
    return this.data?.modo === 'cliente';
  }

  salvar() {
    if (this.modoCliente) {
      this.salvarCliente();
    } else {
      this.salvarUsuario();
    }
  }

  /** É outra pessoa: grava mesmo com cadastro parecido */
  cadastrarMesmoAssim() {
    this.semelhantes = [];
    if (this.modoCliente) {
      this.salvarCliente(true);
    } else {
      this.salvarUsuario({ confirmarDuplicidade: true });
    }
  }

  /** "Sou eu": cria o acesso para o cadastro que já existe */
  usarCadastro(pessoa: PessoaSemelhante) {
    this.semelhantes = [];
    this.salvarUsuario({ pessoaIdExistente: pessoa.pessoaId });
  }

  cancelarDuplicidade() {
    this.semelhantes = [];
  }

  /** 409 com a lista de cadastros parecidos */
  private tratarDuplicidade(err: any): boolean {
    if (err.status !== 409 || !Array.isArray(err.error?.semelhantes)) return false;
    this.semelhantes = err.error.semelhantes;
    this.erro = '';
    this.cdr.markForCheck();
    return true;
  }

  private validarCamposComuns(): boolean {
    if (this.conta.cpf && !cpfValido(this.conta.cpf)) {
      this.erro = 'CPF inválido.';
      this.cdr.markForCheck();
      return false;
    }
    return true;
  }

  private salvarUsuario(opcoes: Partial<CadastroRequest> = {}) {
    if (!this.validarCamposComuns()) return;
    if (!emailValido(this.conta.email)) {
      this.erro = 'E-mail inválido.';
      this.cdr.markForCheck();
      return;
    }
    this.erro = '';
    this.auth.cadastrar({
      nome: this.conta.nome,
      cpf: this.conta.cpf,
      email: this.conta.email,
      telefone: this.conta.telefone || undefined,
      usuario: this.conta.usuario,
      senha: this.conta.senha,
      obs: this.conta.obs || undefined,
      evento: this.data.evento,
      ...opcoes
    }).subscribe({
      next: () => this.dialogRef.close(true),
      error: (err) => {
        if (this.tratarDuplicidade(err)) return;
        this.erro = typeof err.error === 'string' ? err.error : 'Erro ao realizar cadastro.';
        this.cdr.markForCheck();
      }
    });
  }

  private salvarCliente(confirmarDuplicidade = false) {
    if (!this.validarCamposComuns()) return;
    this.erro = '';
    const cliente: Cliente = {
      pessoa: {
        nome: this.conta.nome,
        cpf: this.conta.cpf || undefined,
        telefone: this.conta.telefone || undefined,
      },
      obs: this.conta.obs || undefined,
    };
    this.clienteService.salvar(cliente, confirmarDuplicidade).subscribe({
      next: () => this.dialogRef.close(true),
      error: (err) => {
        if (this.tratarDuplicidade(err)) return;
        this.erro = typeof err.error === 'string' ? err.error : 'Erro ao cadastrar cliente.';
        this.cdr.markForCheck();
      }
    });
  }

  fechar() {
    this.dialogRef.close();
  }

  hide = true;

  togglePassword() {
    this.hide = !this.hide;
  }

  step = 0;

  nextStep() {
    if (!this.validarCamposComuns() || this.cpfComUsuario) return;
    if (!emailValido(this.conta.email)) {
      this.erro = 'E-mail inválido.';
      this.cdr.markForCheck();
      return;
    }
    this.erro = '';
    this.step = 1;
    this.cdr.markForCheck();
  }

  prevStep() {
    this.step = 0;
  }
}
