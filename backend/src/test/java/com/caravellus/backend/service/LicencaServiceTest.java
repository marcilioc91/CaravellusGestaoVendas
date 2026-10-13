package com.caravellus.backend.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/** Mesmo formato gerado por ferramentas/GeradorLicenca.java */
class LicencaServiceTest {

    private static final String MAQUINA = "ABCD-EFGH-JKLM-NPQR";

    private final KeyPair par = gerarPar();

    @Test
    void aceitaChaveAssinadaParaEstaMaquina() throws Exception {
        String chave = gerarChave(par.getPrivate(), "cliente=Cantina Açaí\nmaquina=" + MAQUINA + "\nemitida=2026-10-08");

        LicencaService.Licenca licenca = LicencaService.verificar(chave, par.getPublic(), MAQUINA);

        assertEquals("Cantina Açaí", licenca.cliente());
        assertEquals("2026-10-08", licenca.emitida());
    }

    @Test
    void aceitaChaveComQuebrasDeLinhaEEspacos() throws Exception {
        String chave = gerarChave(par.getPrivate(), "cliente=X\nmaquina=" + MAQUINA + "\nemitida=2026-10-08");
        String quebrada = chave.substring(0, 40) + "\r\n  " + chave.substring(40) + " ";

        assertNotNull(LicencaService.verificar(quebrada, par.getPublic(), MAQUINA));
    }

    @Test
    void aceitaConteudoDoArquivoDeLicenca() throws Exception {
        String chave = gerarChave(par.getPrivate(), "cliente=Cantina Açaí\nmaquina=" + MAQUINA + "\nemitida=2026-10-08");
        StringBuilder arquivo = new StringBuilder(LicencaService.INICIO_ARQUIVO + "\r\n"
                + "Cliente: Cantina Açaí\r\nMaquina: " + MAQUINA + "\r\nEmitida: 2026-10-08\r\n\r\n");
        for (int i = 0; i < chave.length(); i += 64) {
            arquivo.append(chave, i, Math.min(i + 64, chave.length())).append("\r\n");
        }
        arquivo.append(LicencaService.FIM_ARQUIVO).append("\r\n");

        assertEquals(chave, LicencaService.extrairChave(arquivo.toString()));
        assertEquals("Cantina Açaí", LicencaService.verificar(arquivo.toString(), par.getPublic(), MAQUINA).cliente());
    }

    @Test
    void cabecalhoDoArquivoNaoInfluencia() throws Exception {
        String chave = gerarChave(par.getPrivate(), "cliente=Original\nmaquina=" + MAQUINA + "\nemitida=2026-10-08");
        String arquivo = LicencaService.INICIO_ARQUIVO + "\nCliente: Outro nome\n\n" + chave + "\n" + LicencaService.FIM_ARQUIVO;

        assertEquals("Original", LicencaService.verificar(arquivo, par.getPublic(), MAQUINA).cliente());
    }

    @Test
    void recusaChaveDeOutraMaquina() throws Exception {
        String chave = gerarChave(par.getPrivate(), "cliente=X\nmaquina=ZZZZ-ZZZZ-ZZZZ-ZZZZ\nemitida=2026-10-08");

        var erro = assertThrows(IllegalArgumentException.class,
                () -> LicencaService.verificar(chave, par.getPublic(), MAQUINA));
        assertTrue(erro.getMessage().contains("outra máquina"));
    }

    @Test
    void recusaChaveAssinadaPorOutraChavePrivada() throws Exception {
        String chave = gerarChave(gerarPar().getPrivate(), "cliente=X\nmaquina=" + MAQUINA + "\nemitida=2026-10-08");

        assertThrows(IllegalArgumentException.class, () -> LicencaService.verificar(chave, par.getPublic(), MAQUINA));
    }

    @Test
    void recusaPayloadAlterado() throws Exception {
        String chave = gerarChave(par.getPrivate(), "cliente=X\nmaquina=ZZZZ-ZZZZ-ZZZZ-ZZZZ\nemitida=2026-10-08");
        String[] partes = chave.split("\\.");
        String payloadTrocado = Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("cliente=X\nmaquina=" + MAQUINA + "\nemitida=2026-10-08").getBytes(StandardCharsets.UTF_8));
        String adulterada = partes[0] + "." + payloadTrocado + "." + partes[2];

        assertThrows(IllegalArgumentException.class, () -> LicencaService.verificar(adulterada, par.getPublic(), MAQUINA));
    }

    @Test
    void recusaTextoQualquer() {
        for (String invalida : new String[] {null, "", "abc", "PC1.x.y", "PC2.aaaa.bbbb", "PC1.!!!.???"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> LicencaService.verificar(invalida, par.getPublic(), MAQUINA), String.valueOf(invalida));
        }
    }

    @Test
    void chavePublicaDoCodigoEhValida() {
        assertDoesNotThrow(() -> java.security.KeyFactory.getInstance("Ed25519").generatePublic(
                new java.security.spec.X509EncodedKeySpec(Base64.getDecoder().decode(LicencaService.CHAVE_PUBLICA))));
    }

    @Test
    void codigoMaquinaTemFormatoFixoEIndependeDeMaiusculas() throws Exception {
        String codigo = LicencaService.calcularCodigoMaquina("6f1c2b4e-1234-4abc-9def-0123456789ab");

        assertTrue(codigo.matches("[A-HJ-NP-Z2-9]{4}(-[A-HJ-NP-Z2-9]{4}){3}"), codigo);
        assertEquals(codigo, LicencaService.calcularCodigoMaquina(" 6F1C2B4E-1234-4ABC-9DEF-0123456789AB "));
        assertNotEquals(codigo, LicencaService.calcularCodigoMaquina("6f1c2b4e-1234-4abc-9def-0123456789ac"));
    }

    private static KeyPair gerarPar() {
        try {
            return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String gerarChave(PrivateKey privada, String payload) throws Exception {
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        Signature assinatura = Signature.getInstance("Ed25519");
        assinatura.initSign(privada);
        assinatura.update(bytes);
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        return LicencaService.PREFIXO + "." + b64.encodeToString(bytes) + "." + b64.encodeToString(assinatura.sign());
    }
}
