import { Component, HostListener, signal } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import {
  NavigationCancel,
  NavigationCancellationCode,
  NavigationEnd,
  NavigationError,
  Router,
  RouterOutlet,
} from '@angular/router';
import { filter, take } from 'rxjs';
import { TemaService } from './services/tema.service';
import { Logo } from './shared/logo/logo';

/** Tela que pode bloquear a volta pelo Esc (ex.: Nova Venda com itens no carrinho) */
interface TelaComEsc {
  podeVoltarComEsc(): boolean;
}

/** Telas que não têm "voltar": o Esc não faz nada nelas */
const TELAS_SEM_VOLTAR = ['/', '/home', '/ativacao'];

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, Logo],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  protected readonly title = signal('caravellus');

  /** Tela de carregamento até abrir a primeira tela (o guard de licença espera o backend subir) */
  protected readonly iniciando = signal(true);

  /** Componente da tela aberta no router-outlet */
  private telaAtual: unknown = null;

  // TemaService é instanciado na abertura para aplicar o tema salvo (claro/escuro) em qualquer tela inicial
  constructor(_tema: TemaService, private router: Router, private dialog: MatDialog) {
    router.events
      .pipe(
        filter(e =>
          e instanceof NavigationEnd ||
          e instanceof NavigationError ||
          // Redirecionamento (ex.: para a ativação) abre outra navegação: continua carregando
          (e instanceof NavigationCancel &&
            e.code !== NavigationCancellationCode.Redirect &&
            e.code !== NavigationCancellationCode.SupersededByNewNavigation)
        ),
        take(1)
      )
      .subscribe(() => this.iniciando.set(false));
  }

  protected aoAtivarTela(tela: unknown) {
    this.telaAtual = tela;
  }

  /**
   * Esc = Voltar para o menu principal.
   * Diálogos, listas (select/autocomplete), calendários e menus já tratam o Esc
   * (fecham e marcam o evento com preventDefault); nesse caso a tela não volta.
   */
  @HostListener('document:keydown.escape', ['$event'])
  aoTeclarEsc(event: Event) {
    if (event.defaultPrevented || this.iniciando() || this.dialog.openDialogs.length > 0) return;

    const url = this.router.url.split(/[?#]/)[0];
    if (TELAS_SEM_VOLTAR.includes(url)) return;

    const tela = this.telaAtual as Partial<TelaComEsc> | null;
    if (tela?.podeVoltarComEsc && !tela.podeVoltarComEsc()) return;

    this.router.navigate(['/home']);
  }
}
