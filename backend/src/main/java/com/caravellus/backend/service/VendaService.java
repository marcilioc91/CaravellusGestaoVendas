package com.caravellus.backend.service;

import com.caravellus.backend.dto.PagamentoRequestDTO;
import com.caravellus.backend.dto.RelatorioInventarioItemDTO;
import com.caravellus.backend.dto.VendaRequestDTO;
import com.caravellus.backend.dto.VendaProdutoRequestDTO;
import com.caravellus.backend.entity.Cliente;
import com.caravellus.backend.entity.Produto;
import com.caravellus.backend.entity.Usuario;
import com.caravellus.backend.entity.Venda;
import com.caravellus.backend.entity.VendaItem;
import com.caravellus.backend.repository.ProdutoRepository;
import com.caravellus.backend.repository.VendaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;

/**
 * Vendas: cada operação que mexe em itens também mexe no estoque, na mesma transação.
 * Quem fez a operação vem sempre da sessão (usuarioId/usuarioNome), nunca do corpo da requisição.
 * Dados inválidos (produto inexistente, quantidade zero, estoque insuficiente): IllegalArgumentException,
 * que desfaz a transação inteira (nenhum item gravado, nenhum estoque alterado).
 */
@Service
public class VendaService {

    @Autowired
    private VendaRepository vendaRepository;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private AuditoriaService auditoriaService;

    @Transactional
    public Venda realizarVenda(VendaRequestDTO dto, Long usuarioId, String usuarioNome) {
        if (dto.getClienteId() == null) {
            throw new IllegalArgumentException("Selecione o cliente da venda.");
        }
        List<ItemConferido> itens = conferirItens(dto.getProdutos());

        Venda venda = new Venda();
        venda.setCliente(entityManager.getReference(Cliente.class, dto.getClienteId()));
        venda.setUsuario(entityManager.getReference(Usuario.class, usuarioId));
        venda.setFormaPagamento(dto.getFormaPagamento());
        venda.setValorPago(dto.getValorPago());
        venda.setItens(new ArrayList<>());
        venda = vendaRepository.save(venda);

        String itensDesc = gravarItens(venda, itens);

        auditoriaService.registrar(usuarioId, usuarioNome, "VENDA",
                "Venda #" + venda.getId() + " realizada. Itens: " + itensDesc);

        return venda;
    }

    public List<Venda> listarTodas() {
        return vendaRepository.findAllComItens();
    }

    /** Vendas do periodo (inicio e fim inclusivos); sem datas, devolve todas. */
    public List<Venda> listarPorPeriodo(LocalDate inicio, LocalDate fim) {
        if (inicio == null && fim == null) return listarTodas();
        LocalDateTime de = inicio != null ? inicio.atStartOfDay() : LocalDateTime.of(1900, 1, 1, 0, 0);
        LocalDateTime ate = fim != null ? fim.plusDays(1).atStartOfDay() : LocalDateTime.of(9999, 1, 1, 0, 0);
        return vendaRepository.findPeriodoComItens(de, ate);
    }

    public List<Venda> listarPorCliente(Long clienteId) {
        return vendaRepository.findByClienteIdComItens(clienteId);
    }

    @Transactional
    public Venda atualizarPagamento(Long id, PagamentoRequestDTO dto, Long usuarioId, String usuarioNome) {
        Venda venda = buscarVenda(id);
        String antes = descreverPagamento(venda.getFormaPagamento(), venda.getValorPago());

        venda.setFormaPagamento(dto.getFormaPagamento());
        venda.setValorPago(dto.getValorPago());
        Venda salva = vendaRepository.save(venda);

        auditoriaService.registrar(usuarioId, usuarioNome, "ALTERACAO_PAGAMENTO_VENDA",
                "Pagamento da venda #" + id + " do cliente '" + nomeCliente(venda) + "' alterado de "
                        + antes + " para " + descreverPagamento(dto.getFormaPagamento(), dto.getValorPago()) + ".");
        return salva;
    }

    public List<RelatorioInventarioItemDTO> gerarRelatorioInventario(LocalDate inicio, LocalDate fim) {
        List<Venda> vendas = listarPorPeriodo(inicio, fim);
        Map<Long, RelatorioInventarioItemDTO> mapa = new LinkedHashMap<>();

        for (Venda v : vendas) {
            if (v.getItens() == null) continue;
            for (VendaItem vi : v.getItens()) {
                Long produtoId = vi.getProduto().getId();
                RelatorioInventarioItemDTO dto = mapa.computeIfAbsent(produtoId,
                        k -> new RelatorioInventarioItemDTO(vi.getProduto().getNome(),
                                vi.getProduto().getCategoria() != null ? vi.getProduto().getCategoria().getNome() : null));

                BigDecimal receita = vi.getPrecoUnitario()
                        .multiply(BigDecimal.valueOf(vi.getQuantidade()));
                BigDecimal custoPorUnidade = vi.getProduto().getPreco_custo() != null
                        ? vi.getProduto().getPreco_custo()
                        : BigDecimal.ZERO;
                BigDecimal custo = custoPorUnidade.multiply(BigDecimal.valueOf(vi.getQuantidade()));

                dto.setQuantidadeVendida(dto.getQuantidadeVendida() + vi.getQuantidade());
                dto.setTotalReceita(dto.getTotalReceita().add(receita));
                dto.setTotalCusto(dto.getTotalCusto().add(custo));
            }
        }

        List<RelatorioInventarioItemDTO> resultado = new ArrayList<>(mapa.values());
        resultado.forEach(dto -> dto.setLucro(dto.getTotalReceita().subtract(dto.getTotalCusto())));
        resultado.sort(Comparator
                .comparing(RelatorioInventarioItemDTO::getNomeCategoria, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(RelatorioInventarioItemDTO::getNomeProduto, String.CASE_INSENSITIVE_ORDER));

        return resultado;
    }

    /**
     * Troca os itens da venda: devolve ao estoque os itens atuais e baixa os novos.
     * O estoque disponível para os novos itens já conta o que foi devolvido.
     */
    @Transactional
    public Venda atualizarItens(Long id, VendaRequestDTO dto, Long usuarioId, String usuarioNome) {
        Venda venda = buscarVenda(id);
        String itensAntes = descreverItens(venda.getItens());

        for (VendaItem item : venda.getItens()) {
            Produto p = item.getProduto();
            p.setEstoque(p.getEstoque() + item.getQuantidade());
            produtoRepository.save(p);

            entityManager.remove(item);
        }
        venda.getItens().clear();
        vendaRepository.save(venda);

        List<ItemConferido> itens = conferirItens(dto.getProdutos());
        String itensDepois = gravarItens(venda, itens);

        auditoriaService.registrar(usuarioId, usuarioNome, "ALTERACAO_ITENS_VENDA",
                "Itens da venda #" + id + " do cliente '" + nomeCliente(venda) + "' alterados de ["
                        + itensAntes + "] para [" + itensDepois + "].");

        return vendaRepository.save(venda);
    }

    /** Exclui a venda e devolve ao estoque a quantidade de cada item */
    @Transactional
    public void excluir(Long id, Long usuarioId, String usuarioNome) {
        Venda venda = buscarVenda(id);

        for (VendaItem item : venda.getItens()) {
            Produto p = item.getProduto();
            p.setEstoque(p.getEstoque() + item.getQuantidade());
            produtoRepository.save(p);
        }
        String itensDesc = descreverItens(venda.getItens());

        String nomeCliente = nomeCliente(venda);
        vendaRepository.delete(venda); // cascade = ALL em Venda.itens apaga os VENDA_ITEM junto

        auditoriaService.registrar(usuarioId, usuarioNome, "EXCLUSAO_VENDA",
                "Venda #" + id + " do cliente '" + nomeCliente + "' excluída. Itens devolvidos ao estoque: " + itensDesc);
    }

    /**
     * Confere os itens antes de gravar qualquer coisa: produto existente, quantidade positiva e estoque
     * suficiente. O mesmo produto repetido na lista é somado (o estoque vale para o total).
     */
    /** Produto já conferido e a quantidade total pedida dele */
    private record ItemConferido(Produto produto, int quantidade) {}

    private List<ItemConferido> conferirItens(List<VendaProdutoRequestDTO> produtos) {
        if (produtos == null || produtos.isEmpty()) {
            throw new IllegalArgumentException("A venda precisa ter pelo menos um produto.");
        }

        Map<Long, Integer> quantidades = new LinkedHashMap<>();
        for (VendaProdutoRequestDTO item : produtos) {
            if (item.getProdutoId() == null) {
                throw new IllegalArgumentException("Produto não informado.");
            }
            if (item.getQuantidade() == null || item.getQuantidade() <= 0) {
                throw new IllegalArgumentException("A quantidade de cada produto deve ser maior que zero.");
            }
            quantidades.merge(item.getProdutoId(), item.getQuantidade(), Integer::sum);
        }

        List<ItemConferido> itens = new ArrayList<>();
        quantidades.forEach((produtoId, quantidade) -> {
            Produto produto = produtoRepository.findById(produtoId)
                    .orElseThrow(() -> new IllegalArgumentException("Produto não encontrado: " + produtoId + "."));
            int disponivel = produto.getEstoque() != null ? produto.getEstoque() : 0;
            if (quantidade > disponivel) {
                throw new IllegalArgumentException("Estoque insuficiente para '" + produto.getNome()
                        + "': pedido " + quantidade + ", disponível " + disponivel + ".");
            }
            itens.add(new ItemConferido(produto, quantidade));
        });
        return itens;
    }

    /** Grava os itens com o preço atual de cada produto e baixa o estoque. Devolve a descrição para a auditoria. */
    private String gravarItens(Venda venda, List<ItemConferido> itens) {
        for (ItemConferido conferido : itens) {
            Produto produto = conferido.produto();
            int quantidade = conferido.quantidade();
            VendaItem item = new VendaItem();
            item.setVenda(venda);
            item.setProduto(produto);
            item.setQuantidade(quantidade);
            item.setPrecoUnitario(produto.getPreco());
            entityManager.persist(item);
            venda.getItens().add(item);

            produto.setEstoque(produto.getEstoque() - quantidade);
            produtoRepository.save(produto);
        }
        return descreverItens(venda.getItens());
    }

    private Venda buscarVenda(Long id) {
        return vendaRepository.findById(id.intValue())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Venda não encontrada."));
    }

    private static String descreverItens(List<VendaItem> itens) {
        StringBuilder desc = new StringBuilder();
        for (VendaItem item : itens) {
            if (!desc.isEmpty()) desc.append(", ");
            desc.append(item.getProduto().getNome()).append(" x").append(item.getQuantidade());
        }
        return desc.toString();
    }

    private static String descreverPagamento(String forma, BigDecimal valor) {
        return (forma != null ? forma : "-") + " R$ " + (valor != null ? valor.setScale(2, RoundingMode.HALF_UP).toPlainString() : "0.00");
    }

    private static String nomeCliente(Venda venda) {
        return venda.getCliente() != null && venda.getCliente().getPessoa() != null
                ? venda.getCliente().getPessoa().getNome()
                : "?";
    }
}
