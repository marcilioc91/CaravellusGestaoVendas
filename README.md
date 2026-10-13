# Caravellus - Gestão de Vendas

Sistema web e desktop para gerenciamento de vendas de cantina, com controle de clientes, produtos, usuários, auditoria e relatórios.

---

## Tecnologias

| Camada | Tecnologia |
|---|---|
| Frontend | Angular 21.2 (Standalone Components) |
| UI Components | Angular Material 21.2 + Bootstrap 5.3.8 |
| Backend | Java 25 + Spring Boot 4.0.4 |
| ORM | Spring Data JPA + Hibernate |
| Banco de dados | Firebird 5 embarcado (Jaybird 6) + Flyway |
| Build | Maven (backend) · Angular CLI (frontend) |
| Desktop | Electron 41.5 + NSIS (Windows installer) |
| Utilitários | Lombok · ngx-mask · BCryptPasswordEncoder |

---

## Arquitetura

```
Angular (Frontend)
        ↓
REST API (HTTP / JSON)
        ↓
Spring Boot (Backend)
        ↓
JPA / Hibernate
        ↓
Firebird 5 (embarcado, arquivo .fdb)
```

---

## Estrutura do Projeto

```
sistema-porto-cabral/
├── backend/               # Spring Boot (Java)
├── frontend/              # Angular + Electron
├── ferramentas/
│   └── GeradorLicenca.java # Gerador de licenças: janela + linha de comando (uso do fornecedor)
├── Annotations/
│   └── novaEstrutura.sql  # Scripts do banco de dados
├── gerar-licenca.bat      # Abre o gerador de licenças (duplo clique)
└── build-release.bat      # Build do app desktop
```

### Backend

```
src/main/java/com/caravellus/backend/
├── config/
│   └── SecurityConfig.java           # BCrypt + CORS
├── controller/
│   ├── AuditoriaController.java
│   ├── ClienteController.java
│   ├── ProdutoController.java
│   ├── SpaController.java
│   ├── UsuarioController.java
│   └── VendaController.java
├── dto/
│   ├── AtualizarPerfilDTO.java
│   ├── CadastroRequestDTO.java
│   ├── LoginRequestDTO.java
│   ├── PagamentoRequestDTO.java
│   ├── RelatorioInventarioItemDTO.java
│   ├── ResetSenhaDTO.java
│   ├── VendaProdutoRequestDTO.java
│   └── VendaRequestDTO.java
├── entity/
│   ├── AuditoriaLog.java
│   ├── Cliente.java
│   ├── PerfilUsuario.java            # Enum: ADMIN, OPERADOR
│   ├── Pessoa.java
│   ├── Produto.java
│   ├── Usuario.java
│   ├── Venda.java
│   └── VendaItem.java
├── repository/
│   ├── AuditoriaLogRepository.java
│   ├── ClienteRepository.java
│   ├── PessoaRepository.java
│   ├── ProdutoRepository.java
│   ├── UsuarioRepository.java
│   └── VendaRepository.java
├── service/
│   ├── AuditoriaService.java
│   ├── ClienteService.java
│   ├── ProdutoService.java
│   ├── UsuarioService.java
│   └── VendaService.java
├── util/
│   └── ValidacaoUtil.java            # Validação de CPF e email
└── CaravellusApplication.java
```

### Frontend

```
src/app/
├── guards/
│   ├── auth.guard.ts                 # loginGuard
│   └── login.guard.ts                # adminGuard
├── models/
│   ├── auditoria.ts
│   ├── cliente.ts
│   ├── produto.ts
│   ├── usuario.ts
│   └── venda.ts
├── pages/
│   ├── auditoria/
│   ├── cadastro-modal/
│   ├── clientes/
│   ├── gerenciar-usuarios/
│   ├── historico-vendas/
│   ├── home/
│   ├── login/
│   ├── produto-form/
│   ├── produtos/
│   ├── relatorio-inventario/
│   └── vendas/
├── services/
│   ├── auth.service.ts
│   ├── auditoria.service.ts
│   ├── cliente.service.ts
│   ├── produto.service.ts
│   └── venda.service.ts
├── footer/
└── utils/
    └── utils.ts
```

---

## Endpoints da API

### Autenticação (`/auth`)

| Método | Rota | Descrição | Perfil |
|---|---|---|---|
| POST | `/auth/login` | Autenticar usuário | Todos |
| POST | `/auth/cadastro` | Cadastrar novo usuário (CPF de cliente existente: promove o cliente a usuário) | Todos |
| GET | `/auth/cadastro/cliente-por-cpf/{cpf}` | Dados do cliente com esse CPF, para preencher o cadastro (404: CPF novo; 409: já é usuário) | Todos |
| POST | `/auth/recuperar-senha/solicitar` | Enviar código de recuperação para o e-mail cadastrado (`identificador`: login ou e-mail) | Todos |
| POST | `/auth/recuperar-senha/validar` | Conferir o código sem consumi-lo (`identificador`, `codigo`) | Todos |
| POST | `/auth/recuperar-senha/redefinir` | Redefinir senha com o código (`identificador`, `codigo`, `novaSenha`) | Todos |
| GET | `/auth/usuarios` | Listar usuários | ADMIN |
| PATCH | `/auth/usuarios/{id}/reset-senha` | Redefinir senha | ADMIN |
| PATCH | `/auth/usuarios/{id}/perfil` | Alterar perfil | ADMIN |

### Clientes (`/clientes`)

| Método | Rota | Descrição |
|---|---|---|
| GET | `/clientes` | Listar todos |
| POST | `/clientes` | Criar cliente (409 se houver cadastro parecido; `?confirmarDuplicidade=true` grava mesmo assim) |
| POST | `/clientes/importar` | Importar CSV (multipart, campo `arquivo`); `?confirmar=false` devolve só a prévia, `true` grava os novos |
| PUT | `/clientes/{id}` | Atualizar cliente |
| DELETE | `/clientes/{id}` | Excluir cliente |

### Produtos (`/produtos`)

| Método | Rota | Descrição |
|---|---|---|
| GET | `/produtos` | Listar todos (ordenado por nome) |
| POST | `/produtos` | Criar produto |
| PUT | `/produtos/{id}` | Atualizar produto |
| DELETE | `/produtos/{id}` | Excluir produto |

### Vendas (`/vendas`)

| Método | Rota | Descrição | Perfil |
|---|---|---|---|
| POST | `/vendas` | Realizar nova venda | Todos |
| GET | `/vendas` | Listar todas | Todos |
| GET | `/vendas/cliente/{clienteId}` | Listar por cliente | Todos |
| PATCH | `/vendas/{id}/pagamento` | Atualizar pagamento | Todos |
| PUT | `/vendas/{id}/itens` | Editar itens da venda | Todos |
| GET | `/vendas/relatorio/inventario` | Relatório de inventário | ADMIN |

### Auditoria (`/auditoria`)

| Método | Rota | Descrição |
|---|---|---|
| GET | `/auditoria` | Listar logs (filtro opcional por `usuarioId`) |

### Licença (`/licenca`)

Únicos endpoints liberados enquanto o sistema não está ativado; os demais respondem **423 (Locked)**.

| Método | Rota | Descrição |
|---|---|---|
| GET | `/licenca/status` | Situação da licença e código da máquina |
| POST | `/licenca/ativar` | Ativar o sistema com a chave de licença (`chave`) |

---

## Banco de Dados

O banco é um **Firebird 5 embarcado**: roda dentro do próprio backend, sem instalação de servidor.
A biblioteca nativa do Firebird vem como dependência Maven (`jaybird-firebird-embedded-win32-x86-64`), então o backend só roda em **Windows x64**.

- **Pasta de dados** (banco `PORTOCABRAL.FDB` + `config.properties`), definida em `PastaDados.java`:
  - **Desenvolvimento:** `backend/dados/`, dentro do projeto (ignorada pelo Git).
  - **Sistema instalado:** `C:\PortoCabral\dados`, fora da pasta do programa, então atualizações e desinstalação não apagam o banco. O instalador cria a pasta e dá permissão de escrita ao grupo Usuários (`frontend/installer/installer.nsh`).
  - Outro local: `--app.dados.dir=C:/outra/pasta` ou variável de ambiente `APP_DADOS_DIR` (só o arquivo do banco: `--app.db.path` / `APP_DB_PATH`).
  - O banco é criado automaticamente na primeira execução (UTF8, collation `UNICODE_CI`), e o caminho em uso aparece no log ao iniciar (`Pasta de dados: ... | Banco de dados: ...`).
- **Estrutura:** versionada com Flyway em `backend/src/main/resources/db/migration`. Toda alteração de tabela deve virar um novo script `V<n>__descricao.sql`; nunca altere um script já aplicado.
- **Acesso exclusivo:** no modo embarcado, só o processo do backend abre o arquivo. Para inspecionar o banco com uma ferramenta externa (ex.: DBeaver, FlameRobin), pare o backend antes.
- **Usuário padrão:** toda instalação já vem com o administrador **`admin` / senha `admin`** (script `V3__usuario_admin_padrao.sql`). No primeiro login o sistema **obriga a troca da senha** antes de liberar o acesso (coluna `USUARIO.TROCAR_SENHA`, endpoint `POST /auth/trocar-senha`). Novos cadastros feitos pela tela de login entram como `OPERADOR`.
- **Backup:** pare o sistema e copie o arquivo `PORTOCABRAL.FDB` da pasta de dados.

### Configuração local (fora do Git)

Ajustes por máquina ficam em `config.properties`, na pasta de dados (mesma pasta do banco), carregado automaticamente se existir. É opcional: o sistema funciona sem ele.
Modelo com instruções: [`backend/config.exemplo.properties`](backend/config.exemplo.properties). Outro caminho: variável de ambiente `APP_CONFIG_PATH`.

### Recuperação de senha por e-mail

Na tela de login, **"Esqueci minha senha"** → informar usuário ou e-mail → o sistema envia um **código de 6 dígitos** para o e-mail cadastrado → informar o código (validado antes de seguir) → informar a nova senha.

- Código válido por **15 minutos**, de uso único, guardado como hash BCrypt (tabela `RECUPERACAO_SENHA`).
- Máximo de **5 tentativas** por código, somando a validação e a redefinição; novo envio só após 60 segundos; pedir um novo código invalida o anterior.
- Usuário/e-mail não cadastrado ou usuário sem e-mail: a tela mostra o erro e não avança.
- Pedido e redefinição ficam registrados na auditoria.
- Envio via **Brevo** (`smtp-relay.brevo.com:587`, STARTTLS, plano gratuito com cerca de 300 e-mails/dia), com remetente **portocabral.sistemas@gmail.com**. A conta de envio já vai embutida no JAR: o cliente não configura nada.
  - Login SMTP, chave SMTP e remetente ficam em `backend/src/main/resources/email-padrao.properties` (fora do Git; modelo e passo a passo em [`backend/email-padrao.exemplo.properties`](backend/email-padrao.exemplo.properties)). O `build-release.bat` não gera o instalador sem esse arquivo preenchido.
  - A chave SMTP só permite enviar: não dá acesso à caixa do Gmail. Se vazar, basta revogá-la no Brevo e gerar outra.
  - Como o remetente é `@gmail.com` enviado por outro serviço, o e-mail pode cair no spam; com domínio próprio autenticado no Brevo isso deixa de acontecer.
  - Para um cliente usar outra conta (Brevo ou Gmail próprio), basta informá-la no `config.properties`, que prevalece sobre a conta padrão.
  - Sem credenciais, a tela informa que o envio não está configurado.
- Requer internet na máquina no momento do envio.

### Licença de uso (ativação)

Na primeira execução o sistema abre a tela **Ativação do sistema** e bloqueia todo o resto até receber uma chave de licença válida.

1. A tela mostra o **código da máquina** (`XXXX-XXXX-XXXX-XXXX`, derivado do `MachineGuid` do Windows). O cliente envia esse código ao fornecedor.
2. O fornecedor gera a chave no **Gerador de Licenças**: dois cliques em `gerar-licenca.bat` abrem a janela (cliente + código da máquina → **Gerar chave**; a chave já vai para a área de transferência) e, com **Salvar arquivo de licença...**, gera o arquivo **`privatekey.lic`** para enviar ao cliente. Pela linha de comando: `gerar-licenca.bat "Nome do cliente" XXXX-XXXX-XXXX-XXXX [pasta]` (com `[pasta]`, salva também o `privatekey.lic`).
3. O cliente clica em **Importar arquivo de licença** e escolhe o `privatekey.lic` (ou cola a chave e clica em **Ativar com a chave**). A chave fica em `licenca.lic`, na pasta de dados, e é conferida a cada inicialização.

O arquivo de licença tem um cabeçalho legível e a chave assinada entre as linhas `-----BEGIN PORTO CABRAL LICENCA-----` e `-----END PORTO CABRAL LICENCA-----`. Apesar do nome, **não contém a chave privada**: o cabeçalho é só informativo (editá-lo não muda a licença) e o que vale é a chave assinada.

- A chave é **assinada (Ed25519)** com a chave privada do fornecedor; o sistema só tem a chave pública (`LicencaService.CHAVE_PUBLICA`). Sem a chave privada não é possível gerar chaves válidas.
- Vale **só para a máquina** do código informado (copiar a instalação ou o `licenca.lic` para outro computador não funciona) e é **vitalícia**.
- Reinstalar o Windows ou trocar de computador muda o código da máquina: é preciso gerar uma nova chave.
- Bloqueio no backend (`LicencaInterceptor`): toda a API responde 423 sem licença, mesmo chamada direto, exceto `/licenca/**`.

**Chave privada (fornecedor):** criada uma única vez com `gerar-licenca.bat chaves`, fica em `%USERPROFILE%\.portocabral\licenca-privada.key` (ou no caminho da variável `PORTOCABRAL_CHAVE_PRIVADA`). Faça cópia de segurança e **nunca** a coloque no Git ou no instalador: quem tiver esse arquivo gera licenças. Se ela for perdida, é preciso criar um novo par, trocar `CHAVE_PUBLICA` no código e reemitir as licenças.

**Histórico:** toda chave gerada (pela janela ou pela linha de comando) é registrada em `licencas-emitidas.csv`, na mesma pasta da chave privada. A janela lista esse histórico com busca por cliente ou máquina, mostra de novo a chave de uma licença já emitida e avisa quando a máquina já foi licenciada.

### Diagrama simplificado

```
PESSOA ──────┬── USUARIO
             └── CLIENTE ── VENDA ── VENDA_ITEM ── PRODUTO ── CATEGORIA
                                 └── AUDITORIA_LOG
```

### Tabelas

| Tabela | Campos principais |
|---|---|
| `PESSOA` | id, nome, cpf (unique), telefone |
| `USUARIO` | id, pessoa_id, usuario_login, email, senha (BCrypt), perfil, data_criacao |
| `CLIENTE` | id, pessoa_id, obs |
| `CATEGORIA` | id, nome |
| `PRODUTO` | id, nome, preco, preco_custo, estoque, categoria_id |
| `VENDA` | id, cliente_id, usuario_id, forma_pagamento, valor_pago, data_venda |
| `VENDA_ITEM` | id, venda_id, produto_id, quantidade, preco_unitario |
| `RECUPERACAO_SENHA` | id, usuario_id, codigo_hash, criado_em, expira_em, tentativas, usado |
| `AUDITORIA_LOG` | id, usuario_id, usuario_nome, tipo_operacao, descricao, data_hora |

---

## Perfis de Usuário

| Perfil | Acesso |
|---|---|
| `OPERADOR` | Clientes, produtos, vendas, histórico, auditoria |
| `ADMIN` | Tudo acima + relatório de inventário + gerenciar usuários |

---

## Fluxo de Venda

1. Selecionar cliente (autocomplete)
2. Adicionar produtos ao carrinho
3. Confirmar forma de pagamento e valor pago
4. Backend cria a venda, baixa o estoque e registra a auditoria

### Formas de pagamento suportadas

`DINHEIRO` · `PIX` · `CARTAO_CREDITO` · `CARTAO_DEBITO` · `VOUCHER` · `PENDENTE`

---

## Funcionalidades

- **Autenticação**: login com BCrypt, sessão em sessionStorage, guards de rota
- **Cadastro**: cria automaticamente Pessoa + Usuário + Cliente
- **Cliente → usuário**: no cadastro, informar o CPF de um cliente já existente preenche nome e telefone e cria só o acesso (login, e-mail, senha), mantendo o mesmo cliente e o histórico de compras; fica registrado na auditoria. CPF que já tem usuário é recusado
- **Cadastro duplicado**: ao cadastrar cliente ou usuário, o sistema avisa se já existir cadastro parecido e só grava após confirmação. No cadastro de usuário, **"Sou eu"** cria o acesso para o cadastro existente em vez de criar outro. Regra (`DuplicidadeService`): mesmo nome completo (ignorando acentos, maiúsculas e espaços) **ou** mesmo celular (últimos 8 dígitos) **e** mesmo primeiro nome; o celular sozinho não basta, porque irmãos e casais costumam compartilhar o número
- **Importar clientes** (tela de clientes → **Importar**): arquivo CSV com prévia antes de gravar. Aceita o CSV do Google Forms como foi exportado e o modelo do sistema (**Baixar modelo**: `Nome;CPF;Telefone;Observações`); as colunas são reconhecidas pelo cabeçalho (`Nome`/`Nome completo`, `Telefone`/`Celular`, `CPF`, `Observações`) e as demais são ignoradas
  - Do formulário, `Nome do responsável` + `Celular do responsável`, `Restrição alimentar` e `Alergia` vão para as observações (respostas como "Não", "Nenhuma", "Sem alergias" são descartadas); a coluna livre `Observação` do formulário fica de fora
  - Nome em maiúsculas; celular só com dígitos (tira o 55, põe o 9 em celular antigo de 10 dígitos)
  - Quem já está cadastrado (mesma regra de duplicidade ou mesmo CPF) ou aparece repetido no arquivo é **ignorado**; a importação grava tudo ou nada e fica na auditoria
  - Aceita UTF-8 (Google Forms) e Windows-1252 (Excel), separador `,` ou `;`
- **Clientes**: CRUD, visualização de histórico de compras, exclusão em cascata
- **Produtos**: CRUD, controle de estoque com validação anti-negativo
- **Vendas**: carrinho dinâmico, cálculo de total, confirmação de pagamento
- **Histórico de vendas**: agrupado por cliente, filtros por mês/ano/status, edição de itens e pagamentos
- **Relatório de inventário**: receita, custo e lucro por produto (ADMIN)
- **Auditoria**: log de todas as operações com filtros por usuário e data
- **Gerenciar usuários**: reset de senha e alteração de perfil (ADMIN)
- **App desktop**: instalador NSIS para Windows via Electron

---

## Executar localmente

### Backend

```bash
cd backend
./mvnw spring-boot:run
```

Porta padrão: `8080`

### Frontend

```bash
cd frontend
npm install
npm start
```

Porta padrão: `4200`

### App desktop (Electron)

```bash
cd frontend
npm run electron
```

### Build de distribuição

```bat
build-release.bat
```
