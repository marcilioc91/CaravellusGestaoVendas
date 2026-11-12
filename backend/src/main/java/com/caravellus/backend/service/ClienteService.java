package com.caravellus.backend.service;

import com.caravellus.backend.entity.Cliente;
import com.caravellus.backend.entity.Pessoa;
import com.caravellus.backend.repository.ClienteRepository;
import com.caravellus.backend.repository.PessoaRepository;
import com.caravellus.backend.repository.UsuarioRepository;
import com.caravellus.backend.repository.VendaRepository;
import com.caravellus.backend.service.SessaoService.UsuarioSessao;
import com.caravellus.backend.util.ValidacaoUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ClienteService {

    @Autowired
    private ClienteRepository repository;

    @Autowired
    private PessoaRepository pessoaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private VendaRepository vendaRepository;

    @Autowired
    private DuplicidadeService duplicidadeService;

    @Autowired
    private AuditoriaService auditoriaService;

    public List<Cliente> listar(String evento) {
        return repository.findByEventoCodigo(evento);
    }

    /**
     * Inclui o cliente no evento da sessão.
     * PESSOA é única no sistema: CPF de alguém que já existe (cliente de outro evento ou usuário) reaproveita
     * essa pessoa, e os dados informados atualizam o cadastro dela.
     * confirmarDuplicidade=false: cadastro parecido já existente no evento gera DuplicidadeException (a tela pede confirmação).
     */
    @Transactional
    public Cliente salvar(Cliente cliente, boolean confirmarDuplicidade, UsuarioSessao usuario) {
        String evento = usuario.evento();
        Pessoa dados = cliente.getPessoa();
        Pessoa existente = null;
        String cpf = dados.getCpf();
        if (cpf != null && !cpf.isBlank()) {
            String cpfLimpo = limparCpf(cpf);
            validarCpf(cpfLimpo);
            existente = pessoaRepository.findByCpf(cpfLimpo).orElse(null);
            if (existente != null && repository.findByPessoaAndEventoCodigo(existente, evento).isPresent()) {
                throw new IllegalArgumentException("CPF já cadastrado.");
            }
            dados.setCpf(cpfLimpo);
        } else {
            dados.setCpf(null);
        }

        Pessoa pessoa;
        if (existente != null) {
            pessoa = existente;
            if (dados.getNome() != null && !dados.getNome().isBlank()) pessoa.setNome(dados.getNome());
            if (dados.getTelefone() != null && !dados.getTelefone().isBlank()) pessoa.setTelefone(dados.getTelefone());
        } else {
            if (!confirmarDuplicidade) {
                duplicidadeService.verificar(dados.getNome(), dados.getTelefone(), pessoasDoEvento(evento));
            }
            pessoa = dados;
        }
        cliente.setPessoa(pessoaRepository.save(pessoa));
        cliente.setEventoCodigo(evento);
        Cliente salvo = repository.save(cliente);

        auditoriaService.registrar(usuario, "INCLUSAO_CLIENTE",
                "Cliente '" + salvo.getPessoa().getNome() + "' (ID " + salvo.getId() + ") incluído.");

        return salvo;
    }

    @Transactional
    public Cliente atualizar(Long id, Cliente clienteAtualizado, UsuarioSessao usuario) {
        Cliente existente = repository.findByIdAndEventoCodigo(id, usuario.evento())
                .orElseThrow(() -> new IllegalArgumentException("Cliente não encontrado."));
        Pessoa pessoa = existente.getPessoa();

        String cpf = clienteAtualizado.getPessoa().getCpf();
        if (cpf != null && !cpf.isBlank()) {
            String cpfLimpo = limparCpf(cpf);
            validarCpf(cpfLimpo);
            if (pessoaRepository.existsByCpfAndIdNot(cpfLimpo, pessoa.getId())) {
                throw new IllegalArgumentException("CPF já cadastrado para outro cliente.");
            }
            pessoa.setCpf(cpfLimpo);
        } else {
            pessoa.setCpf(null);
        }

        pessoa.setNome(clienteAtualizado.getPessoa().getNome());
        pessoa.setTelefone(clienteAtualizado.getPessoa().getTelefone());
        pessoaRepository.save(pessoa);
        existente.setObs(clienteAtualizado.getObs());
        Cliente atualizado = repository.save(existente);

        auditoriaService.registrar(usuario, "ALTERACAO_CLIENTE",
                "Cliente '" + pessoa.getNome() + "' (ID " + id + ") alterado.");

        return atualizado;
    }

    /**
     * Exclui o cliente e as vendas dele neste evento. Os dados pessoais (PESSOA) só são apagados se a pessoa
     * não for usuário do sistema nem cliente em outro evento.
     */
    @Transactional
    public void excluir(Long id, UsuarioSessao usuario) {
        Cliente cliente = repository.findByIdAndEventoCodigo(id, usuario.evento())
                .orElseThrow(() -> new IllegalArgumentException("Cliente não encontrado."));
        Pessoa pessoa = cliente.getPessoa();
        String nomeCliente = pessoa.getNome();

        vendaRepository.deleteAll(vendaRepository.findByClienteId(cliente.getId()));
        repository.delete(cliente);
        repository.flush();

        if (usuarioRepository.findByPessoa(pessoa).isEmpty() && !repository.existsByPessoa(pessoa)) {
            pessoaRepository.delete(pessoa);
        }

        auditoriaService.registrar(usuario, "EXCLUSAO_CLIENTE",
                "Cliente '" + nomeCliente + "' (ID " + id + ") excluído.");
    }

    private List<Pessoa> pessoasDoEvento(String evento) {
        return repository.findByEventoCodigo(evento).stream().map(Cliente::getPessoa).toList();
    }

    private String limparCpf(String cpf) {
        return cpf == null ? "" : cpf.replaceAll("[^0-9]", "");
    }

    private void validarCpf(String cpfLimpo) {
        if (!ValidacaoUtil.cpfValido(cpfLimpo)) {
            throw new IllegalArgumentException("CPF inválido.");
        }
    }
}
