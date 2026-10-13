package com.caravellus.backend.service;

import com.caravellus.backend.entity.Cliente;
import com.caravellus.backend.entity.Pessoa;
import com.caravellus.backend.repository.ClienteRepository;
import com.caravellus.backend.repository.PessoaRepository;
import com.caravellus.backend.repository.UsuarioRepository;
import com.caravellus.backend.repository.VendaRepository;
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

    public List<Cliente> listar() {
        return repository.findAll();
    }

    /** confirmarDuplicidade=false: cadastro parecido já existente gera DuplicidadeException (a tela pede confirmação) */
    @Transactional
    public Cliente salvar(Cliente cliente, boolean confirmarDuplicidade, Long usuarioId, String usuarioNome) {
        String cpf = cliente.getPessoa().getCpf();
        if (cpf != null && !cpf.isBlank()) {
            String cpfLimpo = limparCpf(cpf);
            validarCpf(cpfLimpo);
            if (pessoaRepository.existsByCpf(cpfLimpo)) {
                throw new IllegalArgumentException("CPF já cadastrado.");
            }
            cliente.getPessoa().setCpf(cpfLimpo);
        } else {
            cliente.getPessoa().setCpf(null);
        }
        if (!confirmarDuplicidade) {
            duplicidadeService.verificar(cliente.getPessoa().getNome(), cliente.getPessoa().getTelefone());
        }
        Pessoa pessoa = pessoaRepository.save(cliente.getPessoa());
        cliente.setPessoa(pessoa);
        Cliente salvo = repository.save(cliente);

        auditoriaService.registrar(usuarioId, usuarioNome, "INCLUSAO_CLIENTE",
                "Cliente '" + salvo.getPessoa().getNome() + "' (ID " + salvo.getId() + ") incluído.");

        return salvo;
    }

    @Transactional
    public Cliente atualizar(Long id, Cliente clienteAtualizado, Long usuarioId, String usuarioNome) {
        Cliente existente = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado"));
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

        auditoriaService.registrar(usuarioId, usuarioNome, "ALTERACAO_CLIENTE",
                "Cliente '" + pessoa.getNome() + "' (ID " + id + ") alterado.");

        return atualizado;
    }

    @Transactional
    public void excluir(Long id, Long usuarioId, String usuarioNome) {
        Cliente cliente = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado"));
        Pessoa pessoa = cliente.getPessoa();
        String nomeCliente = pessoa.getNome();

        vendaRepository.deleteAll(vendaRepository.findByClienteId(cliente.getId()));

        usuarioRepository.findByPessoa(pessoa).ifPresent(u -> {
            vendaRepository.deleteAll(vendaRepository.findByUsuarioId(u.getId()));
            usuarioRepository.delete(u);
        });

        repository.delete(cliente);
        pessoaRepository.delete(pessoa);

        auditoriaService.registrar(usuarioId, usuarioNome, "EXCLUSAO_CLIENTE",
                "Cliente '" + nomeCliente + "' (ID " + id + ") excluído.");
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
