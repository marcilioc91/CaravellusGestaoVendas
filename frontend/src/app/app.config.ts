import { ApplicationConfig, DEFAULT_CURRENCY_CODE, inject, LOCALE_ID, provideBrowserGlobalErrorListeners } from '@angular/core';
import { registerLocaleData } from '@angular/common';
import localePt from '@angular/common/locales/pt';
import { provideRouter, Router, ViewTransitionInfo, withHashLocation, withViewTransitions } from '@angular/router';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { routes } from './app.routes';
import { authInterceptor } from './interceptors/auth.interceptor';
import { licencaInterceptor } from './interceptors/licenca.interceptor';

/**
 * Formato brasileiro no app inteiro: os pipes number, currency, percent e date passam a usar
 * vírgula decimal e ponto de milhar (R$ 1.234,50; 42,5%), e o real é a moeda padrão.
 */
registerLocaleData(localePt, 'pt-BR');

/**
 * Transição animada (View Transitions do navegador) só na entrada após o login: as demais
 * navegações continuam instantâneas. Quem pede menos movimento no sistema operacional não vê animação.
 */
function transicaoSoAposLogin({ transition }: ViewTransitionInfo) {
  const aposLogin = inject(Router).currentNavigation()?.extras.state?.['aposLogin'] === true;
  const reduzirMovimento = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
  if (!aposLogin || reduzirMovimento) transition.skipTransition();
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    { provide: LOCALE_ID, useValue: 'pt-BR' },
    { provide: DEFAULT_CURRENCY_CODE, useValue: 'BRL' },
    provideRouter(
      routes,
      withHashLocation(),
      withViewTransitions({ skipInitialTransition: true, onViewTransitionCreated: transicaoSoAposLogin })
    ),
    provideHttpClient(withFetch(), withInterceptors([authInterceptor, licencaInterceptor]))
  ]
};
