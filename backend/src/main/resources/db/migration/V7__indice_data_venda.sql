-- Filtro do historico de vendas por periodo (VendaRepository.findPeriodoComItens)
CREATE INDEX IX_VENDA_DATA_VENDA ON VENDA (DATA_VENDA);
