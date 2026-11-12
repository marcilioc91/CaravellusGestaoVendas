-- Eventos (acampamento, conferencia...): cada evento tem os proprios clientes, categorias,
-- produtos, estoque, vendas e auditoria. Usuarios (login e senha) valem para todos os eventos.
-- Escolhido na tela de login; fica na sessao do servidor (SessaoService).
-- DDL separado: os UPDATE (V9) e as restricoes (V10) so enxergam as colunas novas apos o commit.

CREATE TABLE EVENTO (
    CODIGO        VARCHAR(4)   NOT NULL,
    NOME          VARCHAR(100) NOT NULL,
    ATIVO         BOOLEAN DEFAULT TRUE NOT NULL,
    DATA_CRIACAO  TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT PK_EVENTO PRIMARY KEY (CODIGO),
    CONSTRAINT UK_EVENTO_NOME UNIQUE (NOME)
);

ALTER TABLE CATEGORIA ADD EVENTO_CODIGO VARCHAR(4);
ALTER TABLE PRODUTO ADD EVENTO_CODIGO VARCHAR(4);
ALTER TABLE CLIENTE ADD EVENTO_CODIGO VARCHAR(4);
ALTER TABLE VENDA ADD EVENTO_CODIGO VARCHAR(4);

-- Na auditoria o evento e opcional: troca de senha, recuperacao e cadastro de usuario nao pertencem a um evento
ALTER TABLE AUDITORIA_LOG ADD EVENTO_CODIGO VARCHAR(4);
