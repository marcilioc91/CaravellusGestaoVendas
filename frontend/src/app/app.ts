import { Component, signal } from '@angular/core';
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

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  protected readonly title = signal('caravellus');

  /** Tela de carregamento até abrir a primeira tela (o guard de licença espera o backend subir) */
  protected readonly iniciando = signal(true);

  // TemaService é instanciado na abertura para aplicar o tema salvo (claro/escuro) em qualquer tela inicial
  constructor(_tema: TemaService, router: Router) {
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
}
