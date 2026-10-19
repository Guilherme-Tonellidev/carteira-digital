const brl = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });

export function formatMoney(value: number): string {
  return brl.format(value);
}

export function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' });
}

/**
 * Converte o que o usuário digitou ("100,50" ou "100.50") em número.
 * Aceita só valores positivos com até 2 casas decimais; devolve null se for inválido.
 */
export function parseAmount(input: string): number | null {
  const text = input.trim();
  if (!/^\d{1,12}([.,]\d{1,2})?$/.test(text)) {
    return null;
  }
  const value = Number(text.replace(',', '.'));
  return value > 0 ? value : null;
}

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function isUuid(value: string): boolean {
  return UUID_PATTERN.test(value);
}

export function shortId(id: string | null): string {
  return id ? `${id.slice(0, 8)}…` : '';
}
