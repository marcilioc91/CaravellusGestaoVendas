import { isPlatformBrowser } from '@angular/common';
import { Component, HostListener, inject, PLATFORM_ID, signal } from '@angular/core';
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
import { AtualizacaoService } from './services/atualizacao.service';
import { TemaService } from './services/tema.service';
import { AtualizacaoProgresso } from './shared/atualizacao-progresso/atualizacao-progresso';
import { Logo } from './shared/logo/logo';

/** Abertura típica do backend (ms): em 10 s a barra está em ~57%, em 30 s em ~85% */
const TEMPO_TIPICO_ABERTURA = 10000;

/** Teto da estimativa: os últimos 10% só completam quando a primeira tela abre */
const TETO_ESTIMATIVA = 90;

/** O backend não informa o próprio progresso: estima pelo tempo desde que a página abriu */
function estimarProgressoAbertura(ms: number): number {
  return TETO_ESTIMATIVA * (1 - Math.exp(-ms / TEMPO_TIPICO_ABERTURA));
}

/** Tela que pode bloquear a volta pelo Esc (ex.: Nova Venda com itens no carrinho) */
interface TelaComEsc {
  podeVoltarComEsc(): boolean;
}

/** Telas que não têm "voltar": o Esc não faz nada nelas */
const TELAS_SEM_VOLTAR = ['/', '/home', '/ativacao'];

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, Logo, AtualizacaoProgresso],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  protected readonly title = signal('caravellus');

  /** Tela de carregamento até abrir a primeira tela (o guard de licença espera o backend subir) */
  protected readonly iniciando = signal(true);

  /** Barra da tela de carregamento (0 a 100) */
  protected readonly progressoInicial = signal(0);

  /** Componente da tela aberta no router-outlet */
  private telaAtual: unknown = null;

  // TemaService é instanciado na abertura para aplicar o tema salvo (claro/escuro) em qualquer tela inicial
  constructor(
    _tema: TemaService,
    private router: Router,
    private dialog: MatDialog,
    private atualizacao: AtualizacaoService
  ) {
    const navegador = isPlatformBrowser(inject(PLATFORM_ID));
    let relogio: ReturnType<typeof setInterval> | undefined;
    if (navegador) {
      const atualizar = () => this.progressoInicial.set(estimarProgressoAbertura(performance.now()));
      atualizar();
      relogio = setInterval(atualizar, 200);
    }

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
      .subscribe(() => {
        clearInterval(relogio);
        this.progressoInicial.set(100);
        const abrirSistema = () => {
          this.iniciando.set(false);
          // Só agora: o diálogo de atualização não aparece sobre a tela "Iniciando o sistema"
          this.atualizacao.iniciar();
        };
        // Deixa a barra cheia aparecer por um instante antes de sair a tela de carregamento
        if (navegador) setTimeout(abrirSistema, 300);
        else abrirSistema();
      });
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
