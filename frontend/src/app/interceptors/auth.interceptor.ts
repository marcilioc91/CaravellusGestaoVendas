import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

const API = 'http://localhost:8080/';

/**
 * Envia o token da sessão em toda chamada à API. Se o backend responder 401 (sessão expirada ou
 * backend reiniciado), volta para o login. O 401 do próprio login (senha errada) é tratado na tela.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const token = auth.getToken();
  const daApi = req.url.startsWith(API);
  if (token && daApi) {
    req = req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
  }
  return next(req).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status === 401 && daApi && !req.url.startsWith(API + 'auth/login') && auth.getUsuarioLogado()) {
        sessionStorage.removeItem('usuario');
        router.navigate(['/']);
      }
      return throwError(() => err);
    })
  );
};
