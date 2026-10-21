package com.caravellus.backend.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Licença de uso: o sistema fica bloqueado até ser ativado com uma chave gerada pelo fornecedor
 * (ferramentas/GeradorLicenca.java) para o código desta máquina. A licença é vitalícia.
 *
 * Chave: PC1.<payload Base64 URL>.<assinatura Ed25519 do payload Base64 URL>
 * Payload (UTF-8): linhas "cliente=...", "email=...", "maquina=XXXX-XXXX-XXXX-XXXX", "emitida=AAAA-MM-DD".
 * "email" (e-mail do cliente informado no pedido de licença) é opcional: licenças antigas não têm.
 * Só quem tem a chave privada do fornecedor consegue gerar uma assinatura válida.
 *
 * O cliente pode colar a chave ou importar o arquivo de licença gerado pelo fornecedor, que traz a
 * mesma chave entre INICIO_ARQUIVO e FIM_ARQUIVO, após um cabeçalho informativo "Campo: valor".
 *
 * A chave ativada fica em licenca.lic, na pasta de dados (sobrevive a atualizações),
 * e é conferida de novo a cada inicialização.
 */
@Service
public class LicencaService {

    private static final Logger log = LoggerFactory.getLogger(LicencaService.class);

    /**
     * Chave pública (Ed25519, X.509 em Base64) do par criado com "gerar-licenca.bat chaves".
     * Fica no código, e não em .properties, para não poder ser trocada pelo config.properties do cliente.
     */
    static final String CHAVE_PUBLICA = "MCowBQYDK2VwAyEArTLe0an/tKTBqKiHWWjN7aZrraYu4Gz+ECA5abbiqp8=";

    static final String PREFIXO = "PC1";
    static final String NOME_ARQUIVO = "licenca.lic";
    /** Delimitadores do arquivo de licença enviado ao cliente (privatekey.lic, gerado pelo GeradorLicenca) */
    static final String INICIO_ARQUIVO = "-----BEGIN PORTO CABRAL LICENCA-----";
    static final String FIM_ARQUIVO = "-----END PORTO CABRAL LICENCA-----";
    private static final String ERRO_CHAVE = "Chave de licença inválida. Confira se ela foi copiada por completo.";
    /** Base32 sem I, O, 0 e 1 (evita confusão ao ditar ou digitar o código) */
    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    public record Licenca(String cliente, String email, String maquina, String emitida) {}

    private final Path arquivo;
    private String codigoMaquina;
    private volatile Licenca licenca;
    /** Motivo de a licença gravada não valer (ex.: arquivo copiado de outra máquina) */
    private volatile String problema;

    public LicencaService(@Value("${app.dados.dir}") String pastaDados) {
        this.arquivo = Path.of(pastaDados).resolve(NOME_ARQUIVO);
    }

    @PostConstruct
    void carregar() {
        try {
            codigoMaquina = calcularCodigoMaquina(lerMachineGuid());
        } catch (Exception e) {
            log.error("Não foi possível identificar esta máquina para a licença.", e);
            problema = "Não foi possível identificar esta máquina. Procure o fornecedor do sistema.";
            return;
        }
        if (!Files.exists(arquivo)) {
            log.warn("Sistema não ativado. Código da máquina: {}", codigoMaquina);
            return;
        }
        try {
            licenca = verificar(Files.readString(arquivo, StandardCharsets.US_ASCII), chavePublica(), codigoMaquina);
            log.info("Licença ativa para '{}' (emitida em {}).", licenca.cliente(), licenca.emitida());
        } catch (IllegalArgumentException | IOException e) {
            problema = "A licença instalada não é válida: " + e.getMessage();
            log.warn("{} Código da máquina: {}", problema, codigoMaquina);
        }
    }

    public boolean isAtiva() {
        return licenca != null;
    }

    public Licenca getLicenca() {
        return licenca;
    }

    public String getCodigoMaquina() {
        return codigoMaquina;
    }

    public String getProblema() {
        return problema;
    }

    /** Confere a chave e, se valer para esta máquina, grava em licenca.lic e libera o sistema. */
    public synchronized Licenca ativar(String chave) {
        if (codigoMaquina == null) {
            throw new IllegalStateException(problema);
        }
        Licenca nova = verificar(chave, chavePublica(), codigoMaquina);
        try {
            Files.createDirectories(arquivo.getParent());
            Files.writeString(arquivo, extrairChave(chave), StandardCharsets.US_ASCII);
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível gravar a licença em " + arquivo + ".", e);
        }
        licenca = nova;
        problema = null;
        log.info("Sistema ativado para '{}'.", nova.cliente());
        return nova;
    }

    /** Valida formato, assinatura e máquina. Lança IllegalArgumentException com a mensagem para o usuário. */
    static Licenca verificar(String chave, PublicKey chavePublica, String codigoMaquina) {
        String[] partes = extrairChave(chave).split("\\.");
        if (partes.length != 3 || !partes[0].equals(PREFIXO)) {
            throw new IllegalArgumentException(ERRO_CHAVE);
        }

        byte[] payload;
        byte[] assinatura;
        try {
            Base64.Decoder b64 = Base64.getUrlDecoder();
            payload = b64.decode(partes[1]);
            assinatura = b64.decode(partes[2]);
            Signature verificador = Signature.getInstance("Ed25519");
            verificador.initVerify(chavePublica);
            verificador.update(payload);
            if (!verificador.verify(assinatura)) {
                throw new IllegalArgumentException(ERRO_CHAVE);
            }
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            throw new IllegalArgumentException(ERRO_CHAVE);
        }

        Map<String, String> campos = new HashMap<>();
        for (String linha : new String(payload, StandardCharsets.UTF_8).split("\n")) {
            int igual = linha.indexOf('=');
            if (igual > 0) campos.put(linha.substring(0, igual), linha.substring(igual + 1));
        }
        if (!codigoMaquina.equals(campos.get("maquina"))) {
            throw new IllegalArgumentException("Esta chave de licença foi gerada para outra máquina.");
        }
        return new Licenca(campos.getOrDefault("cliente", ""), campos.getOrDefault("email", ""), campos.get("maquina"),
                campos.getOrDefault("emitida", ""));
    }

    /** Código exibido na tela de ativação: hash do identificador do Windows, no formato XXXX-XXXX-XXXX-XXXX. */
    static String calcularCodigoMaquina(String identificador) throws GeneralSecurityException {
        byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(("PortoCabral:" + identificador.trim().toLowerCase()).getBytes(StandardCharsets.UTF_8));

        // 10 bytes = 80 bits = 16 caracteres de 5 bits
        StringBuilder codigo = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (int i = 0; i < 10; i++) {
            buffer = (buffer << 8) | (hash[i] & 0xFF);
            bits += 8;
            while (bits >= 5) {
                codigo.append(ALFABETO.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
            buffer &= (1 << bits) - 1;
        }
        for (int i = 12; i > 0; i -= 4) codigo.insert(i, '-');
        return codigo.toString();
    }

    /** MachineGuid do Windows: criado na instalação do sistema operacional e único por máquina. */
    private static String lerMachineGuid() throws IOException, InterruptedException {
        Process processo = new ProcessBuilder("reg", "query", "HKLM\\SOFTWARE\\Microsoft\\Cryptography",
                "/v", "MachineGuid", "/reg:64")
                .redirectErrorStream(true)
                .start();
        String saida = new String(processo.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!processo.waitFor(10, TimeUnit.SECONDS)) {
            processo.destroyForcibly();
            throw new IOException("Tempo esgotado ao consultar o registro do Windows.");
        }
        Matcher m = Pattern.compile("MachineGuid\\s+REG_SZ\\s+(\\S+)").matcher(saida);
        if (!m.find()) {
            throw new IOException("MachineGuid não encontrado no registro do Windows: " + saida.trim());
        }
        return m.group(1);
    }

    private static PublicKey chavePublica() {
        try {
            return KeyFactory.getInstance("Ed25519")
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(CHAVE_PUBLICA)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Chave pública da licença inválida.", e);
        }
    }

    /**
     * Aceita a chave pura ou o conteúdo do arquivo de licença (o cabeçalho "Campo: valor" é só
     * informativo e é ignorado). Remove espaços e quebras de linha (a chave pode chegar quebrada).
     */
    static String extrairChave(String texto) {
        if (texto == null) return "";
        int inicio = texto.indexOf(INICIO_ARQUIVO);
        int fim = texto.indexOf(FIM_ARQUIVO);
        if (inicio >= 0 && fim > inicio) {
            StringBuilder chave = new StringBuilder();
            for (String linha : texto.substring(inicio + INICIO_ARQUIVO.length(), fim).split("\\R")) {
                if (!linha.contains(":")) chave.append(linha);
            }
            texto = chave.toString();
        }
        return texto.replaceAll("\\s", "");
    }
}
