package com.caravellus.backend.service;

import com.caravellus.backend.IntegracaoTest;
import com.caravellus.backend.dto.PagamentoRequestDTO;
import com.caravellus.backend.dto.VendaProdutoRequestDTO;
import com.caravellus.backend.dto.VendaRequestDTO;
import com.caravellus.backend.entity.AuditoriaLog;
import com.caravellus.backend.entity.Cliente;
import com.caravellus.backend.entity.Evento;
import com.caravellus.backend.entity.Pessoa;
import com.caravellus.backend.entity.Produto;
import com.caravellus.backend.entity.Usuario;
import com.caravellus.backend.entity.Venda;
import com.caravellus.backend.entity.VendaItem;
import com.caravellus.backend.repository.AuditoriaLogRepository;
import com.caravellus.backend.repository.ClienteRepository;
import com.caravellus.backend.repository.EventoRepository;
import com.caravellus.backend.repository.PessoaRepository;
import com.caravellus.backend.repository.ProdutoRepository;
import com.caravellus.backend.repository.UsuarioRepository;
import com.caravellus.backend.repository.VendaRepository;
import com.caravellus.backend.service.SessaoService.UsuarioSessao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regras de venda contra o banco real: baixa e estorno de estoque, estoque insuficiente (com rollback),
 * reajuste de preço só em vendas pendentes, auditoria das alterações e separação por evento.
 */
class VendaServiceTest extends IntegracaoTest {

    /** Evento criado pela migration V9; OUTRO_EVENTO é criado pelo próprio teste */
    private static final String EVENTO = "0001";
    private static final String OUTRO_EVENTO = "TST2";

    @Autowired private VendaService vendaService;
    @Autowired private ProdutoService produtoService;
    @Autowired private VendaRepository vendaRepository;
    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private PessoaRepository pessoaRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private AuditoriaLogRepository auditoriaRepository;
    @Autowired private EventoRepository eventoRepository;

    private Usuario operador;
    private UsuarioSessao sessao;
    private Cliente cliente;

    @BeforeEach
    void preparar() {
        operador = usuarioRepository.findByUsuarioLogin("admin").orElseThrow();
        sessao = new UsuarioSessao(operador.getId(), "Teste", operador.getPerfil(), EVENTO);
        cliente = novoCliente(EVENTO);
    }

    @Test
    void vendaBaixaEstoqueGravaPrecoAtualERegistraAuditoria() {
        Produto refri = novoProduto("Refrigerante", "6.00", 10);
        Produto pastel = novoProduto("Pastel", "8.50", 5);

        Venda venda = vender(cliente, "PIX", "29.50", item(refri, 2), item(pastel, 2));

        assertEquals(8, estoque(refri));
        assertEquals(3, estoque(pastel));

        Venda gravada = vendaDoCliente(cliente);
        assertEquals(venda.getId(), gravada.getId());
        assertEquals(operador.getId(), gravada.getUsuario().getId(), "operador vem da sessão");
        assertEquals(2, gravada.getItens().size());
        assertEquals(0, new BigDecimal("8.50").compareTo(precoDoItem(gravada, pastel)));

        AuditoriaLog log = ultimaAuditoria("VENDA", "Venda #" + venda.getId() + " ");
        assertTrue(log.getDescricao().contains(pastel.getNome() + " x2"));
        assertEquals(operador.getId(), log.getUsuarioId());
    }

    @Test
    void mesmoProdutoRepetidoNaListaContaOTotalNoEstoque() {
        Produto agua = novoProduto("Água", "3.00", 3);

        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
                () -> vender(cliente, "DINHEIRO", "12.00", item(agua, 2), item(agua, 2)));

        assertTrue(erro.getMessage().contains("pedido 4, disponível 3"), erro.getMessage());
        assertEquals(3, estoque(agua));
    }

    @Test
    void estoqueInsuficienteNaoGravaNadaNemBaixaOutrosItens() {
        Produto bolo = novoProduto("Bolo", "5.00", 10);
        Produto cafe = novoProduto("Café", "2.00", 1);

        assertThrows(IllegalArgumentException.class,
                () -> vender(cliente, "PIX", "14.00", item(bolo, 2), item(cafe, 2)));

        assertEquals(10, estoque(bolo), "o primeiro item não pode ter baixado o estoque");
        assertEquals(1, estoque(cafe));
        assertTrue(vendaRepository.findByClienteIdComItens(EVENTO, cliente.getId()).isEmpty(), "nenhuma venda gravada");
    }

    @Test
    void quantidadeZeroOuNegativaERecusada() {
        Produto pipoca = novoProduto("Pipoca", "4.00", 10);

        assertThrows(IllegalArgumentException.class, () -> vender(cliente, "PIX", "0", item(pipoca, 0)));
        assertThrows(IllegalArgumentException.class, () -> vender(cliente, "PIX", "0", item(pipoca, -3)));
        assertEquals(10, estoque(pipoca));
    }

    @Test
    void editarItensDevolveOsAntigosEBaixaOsNovos() {
        Produto suco = novoProduto("Suco", "5.00", 10);
        Produto salgado = novoProduto("Salgado", "6.00", 10);
        Venda venda = vender(cliente, "PENDENTE", "0", item(suco, 4));

        vendaService.atualizarItens(venda.getId(), pedido(cliente, "PENDENTE", "0", item(suco, 1), item(salgado, 3)),
                sessao);

        assertEquals(9, estoque(suco));
        assertEquals(7, estoque(salgado));
        AuditoriaLog log = ultimaAuditoria("ALTERACAO_ITENS_VENDA", "venda #" + venda.getId() + " ");
        assertTrue(log.getDescricao().contains("[" + suco.getNome() + " x4] para [" + suco.getNome() + " x1, " + salgado.getNome() + " x3]"), log.getDescricao());
    }

    @Test
    void editarItensContaOEstoqueDevolvidoEDesfazTudoSeFaltar() {
        Produto sorvete = novoProduto("Sorvete", "7.00", 2);
        Venda venda = vender(cliente, "PIX", "14.00", item(sorvete, 2));
        assertEquals(0, estoque(sorvete));

        // Os 2 devolvidos voltam a valer: trocar 2 por 2 é permitido mesmo com estoque zerado
        vendaService.atualizarItens(venda.getId(), pedido(cliente, "PIX", "14.00", item(sorvete, 2)), sessao);
        assertEquals(0, estoque(sorvete));

        // Pedir 3 não cabe: a venda continua como estava e nada volta ao estoque
        assertThrows(IllegalArgumentException.class, () -> vendaService.atualizarItens(
                venda.getId(), pedido(cliente, "PIX", "21.00", item(sorvete, 3)), sessao));
        assertEquals(0, estoque(sorvete));
        assertEquals(2, vendaDoCliente(cliente).getItens().getFirst().getQuantidade());
    }

    @Test
    void excluirVendaDevolveItensAoEstoque() {
        Produto chocolate = novoProduto("Chocolate", "4.50", 6);
        Venda venda = vender(cliente, "PENDENTE", "0", item(chocolate, 5));
        assertEquals(1, estoque(chocolate));

        vendaService.excluir(venda.getId(), sessao);

        assertEquals(6, estoque(chocolate));
        assertTrue(vendaRepository.findByClienteIdComItens(EVENTO, cliente.getId()).isEmpty());
        assertNotNull(ultimaAuditoria("EXCLUSAO_VENDA", "Venda #" + venda.getId() + " "));
    }

    @Test
    void vendaInexistenteDa404() {
        ResponseStatusException erro = assertThrows(ResponseStatusException.class,
                () -> vendaService.excluir(999_999_999L, sessao));
        assertEquals(404, erro.getStatusCode().value());
    }

    @Test
    void alterarPagamentoRegistraAntesEDepoisNaAuditoria() {
        Produto lanche = novoProduto("Lanche", "10.00", 5);
        Venda venda = vender(cliente, "PENDENTE", "0", item(lanche, 1));

        PagamentoRequestDTO pagamento = new PagamentoRequestDTO();
        pagamento.setFormaPagamento("PIX");
        pagamento.setValorPago(new BigDecimal("10"));
        vendaService.atualizarPagamento(venda.getId(), pagamento, sessao);

        assertEquals("PIX", vendaDoCliente(cliente).getFormaPagamento());
        AuditoriaLog log = ultimaAuditoria("ALTERACAO_PAGAMENTO_VENDA", "venda #" + venda.getId() + " ");
        assertTrue(log.getDescricao().contains("de PENDENTE R$ 0.00 para PIX R$ 10.00"), log.getDescricao());
    }

    @Test
    void novoPrecoReajustaSoVendasPendentes() {
        Produto x = novoProduto("Cachorro-quente", "8.00", 20);
        Cliente outro = novoCliente(EVENTO);
        vender(cliente, "PENDENTE", "0", item(x, 1));
        vender(outro, "PIX", "8.00", item(x, 1));

        Produto alterado = produtoRepository.findById(x.getId()).orElseThrow();
        alterado.setPreco(new BigDecimal("9.00"));
        produtoService.salvar(alterado, sessao);

        assertEquals(0, new BigDecimal("9.00").compareTo(precoDoItem(vendaDoCliente(cliente), x)), "pendente: preço novo");
        assertEquals(0, new BigDecimal("8.00").compareTo(precoDoItem(vendaDoCliente(outro), x)), "quitada: preço da venda");
    }

    @Test
    void produtoEClienteDeOutroEventoNaoEntramNaVenda() {
        UsuarioSessao noOutro = new UsuarioSessao(operador.getId(), "Teste", operador.getPerfil(), outroEvento());
        Produto daqui = novoProduto("Refri", "6.00", 5);
        Cliente deLa = novoCliente(OUTRO_EVENTO);

        IllegalArgumentException produto = assertThrows(IllegalArgumentException.class,
                () -> vendaService.realizarVenda(pedido(deLa, "PIX", "6.00", item(daqui, 1)), noOutro));
        assertTrue(produto.getMessage().contains("Produto não encontrado"), produto.getMessage());

        assertThrows(IllegalArgumentException.class,
                () -> vendaService.realizarVenda(pedido(cliente, "PIX", "6.00", item(daqui, 1)), noOutro),
                "cliente do evento 0001 não pode comprar no outro evento");
        assertEquals(5, estoque(daqui));
    }

    @Test
    void vendaDeOutroEventoContaComoInexistente() {
        Produto pastel = novoProduto("Pastel", "8.00", 5);
        Venda venda = vender(cliente, "PENDENTE", "0", item(pastel, 1));
        UsuarioSessao noOutro = new UsuarioSessao(operador.getId(), "Teste", operador.getPerfil(), outroEvento());

        ResponseStatusException erro = assertThrows(ResponseStatusException.class,
                () -> vendaService.excluir(venda.getId(), noOutro));
        assertEquals(404, erro.getStatusCode().value());
        assertEquals(4, estoque(pastel), "a venda continua lá, sem devolver estoque");
        assertTrue(vendaService.listarTodas(OUTRO_EVENTO).stream().noneMatch(v -> v.getId().equals(venda.getId())));
    }

    // ── apoio ────────────────────────────────────────────────────────────────

    private String outroEvento() {
        if (!eventoRepository.existsById(OUTRO_EVENTO)) {
            Evento e = new Evento();
            e.setCodigo(OUTRO_EVENTO);
            e.setNome("EVENTO DE TESTE 2");
            e.setDataCriacao(LocalDateTime.now());
            eventoRepository.save(e);
        }
        return OUTRO_EVENTO;
    }

    private Venda vender(Cliente c, String forma, String valorPago, VendaProdutoRequestDTO... itens) {
        return vendaService.realizarVenda(pedido(c, forma, valorPago, itens), sessao);
    }

    private static VendaRequestDTO pedido(Cliente c, String forma, String valorPago, VendaProdutoRequestDTO... itens) {
        VendaRequestDTO dto = new VendaRequestDTO();
        dto.setClienteId(c.getId());
        dto.setFormaPagamento(forma);
        dto.setValorPago(new BigDecimal(valorPago));
        dto.setProdutos(List.of(itens));
        return dto;
    }

    private static VendaProdutoRequestDTO item(Produto p, int quantidade) {
        VendaProdutoRequestDTO dto = new VendaProdutoRequestDTO();
        dto.setProdutoId(p.getId());
        dto.setQuantidade(quantidade);
        return dto;
    }

    private Produto novoProduto(String nome, String preco, int estoque) {
        Produto p = new Produto();
        p.setNome(nome + " " + sufixo());
        p.setPreco(new BigDecimal(preco));
        p.setPreco_custo(BigDecimal.ONE);
        p.setEstoque(estoque);
        p.setEventoCodigo(EVENTO);
        return produtoRepository.save(p);
    }

    private Cliente novoCliente(String evento) {
        if (!evento.equals(EVENTO)) outroEvento();
        Pessoa pessoa = new Pessoa();
        pessoa.setNome("CLIENTE TESTE " + sufixo());
        Cliente c = new Cliente();
        c.setEventoCodigo(evento);
        c.setPessoa(pessoaRepository.save(pessoa));
        return clienteRepository.save(c);
    }

    private int estoque(Produto p) {
        return produtoRepository.findById(p.getId()).orElseThrow().getEstoque();
    }

    private Venda vendaDoCliente(Cliente c) {
        List<Venda> vendas = vendaRepository.findByClienteIdComItens(EVENTO, c.getId());
        assertEquals(1, vendas.size());
        return vendas.getFirst();
    }

    private static BigDecimal precoDoItem(Venda venda, Produto p) {
        return venda.getItens().stream()
                .filter(i -> i.getProduto().getId().equals(p.getId()))
                .map(VendaItem::getPrecoUnitario)
                .findFirst().orElseThrow();
    }

    private AuditoriaLog ultimaAuditoria(String tipo, String trecho) {
        return auditoriaRepository.findAllByOrderByDataHoraDesc().stream()
                .filter(l -> tipo.equals(l.getTipoOperacao()) && l.getDescricao().contains(trecho))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Auditoria " + tipo + " não encontrada: " + trecho));
    }

    private static String sufixo() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
