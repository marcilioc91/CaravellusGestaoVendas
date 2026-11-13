import { Directive, ElementRef, forwardRef, HostListener } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

const FORMATO = new Intl.NumberFormat('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

/** "1.234,5" / "51,4" / "51.4" -> número com 2 casas; vazio ou inválido -> null */
export function lerValorMonetario(texto: string): number | null {
  let t = texto.replace(/[^\d,.-]/g, '');
  if (!t) return null;
  // Com vírgula, ela é a casa decimal e os pontos separam milhar; sem vírgula, o ponto é decimal
  if (t.includes(',')) t = t.replace(/\./g, '').replace(',', '.');
  const valor = Number(t);
  return Number.isFinite(valor) ? Math.round(valor * 100) / 100 : null;
}

export function formatarValorMonetario(valor: number | null | undefined): string {
  return valor == null || !Number.isFinite(valor) ? '' : FORMATO.format(valor);
}

/**
 * Campo de valor em reais: mostra sempre 2 casas no padrão brasileiro (51,40)
 * e entrega ao ngModel um number (51.4). Usar com type="text".
 */
@Directive({
  selector: 'input[appValorMonetario]',
  standalone: true,
  host: { inputmode: 'decimal', autocomplete: 'off' },
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => ValorMonetarioDirective), multi: true }],
})
export class ValorMonetarioDirective implements ControlValueAccessor {
  private aoMudar: (valor: number | null) => void = () => {};
  private aoTocar: () => void = () => {};

  constructor(private el: ElementRef<HTMLInputElement>) {}

  writeValue(valor: number | null): void {
    this.el.nativeElement.value = formatarValorMonetario(valor);
  }

  registerOnChange(fn: (valor: number | null) => void): void {
    this.aoMudar = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.aoTocar = fn;
  }

  setDisabledState(desabilitado: boolean): void {
    this.el.nativeElement.disabled = desabilitado;
  }

  @HostListener('input')
  aoDigitar(): void {
    const campo = this.el.nativeElement;
    const limpo = campo.value.replace(/[^\d,.]/g, '');
    if (limpo !== campo.value) campo.value = limpo;
    this.aoMudar(lerValorMonetario(limpo));
  }

  @HostListener('focus')
  aoEntrar(): void {
    // Seleciona tudo: digitar já substitui o valor sugerido
    setTimeout(() => this.el.nativeElement.select());
  }

  @HostListener('blur')
  aoSair(): void {
    const valor = lerValorMonetario(this.el.nativeElement.value);
    this.el.nativeElement.value = formatarValorMonetario(valor);
    this.aoTocar();
  }
}
