/**
 * Atalhos de teclado Alt+letra (fixos, valem para todos os usuários).
 *
 * Compara pelo código físico da tecla (event.code): funciona em qualquer layout de teclado
 * e não diferencia maiúscula de minúscula. Ctrl, Shift e Meta não podem estar pressionados
 * (no Windows o Alt Gr chega como Ctrl+Alt e não dispara atalho). Segurar a tecla não repete
 * o atalho, para não abrir a mesma tela ou diálogo várias vezes.
 */
export function ehAtalhoAlt(event: KeyboardEvent, letra: string): boolean {
  return event.altKey && !event.ctrlKey && !event.shiftKey && !event.metaKey
    && !event.repeat && !event.defaultPrevented
    && event.code === 'Key' + letra.toUpperCase();
}
