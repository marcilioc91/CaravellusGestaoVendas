@echo off
setlocal
chcp 65001 > nul

set ROOT=%~dp0
set GERADOR=%ROOT%ferramentas\GeradorLicenca.java

rem Sem argumentos (ou duplo clique): abre a janela do gerador, sem console
if "%~1"=="" (
    start "" javaw "%GERADOR%"
    exit /b 0
)

echo.
echo ============================================
echo   GERADOR DE LICENCA - Caravellus
echo ============================================
echo.
echo Uso:
echo   gerar-licenca.bat
echo       abre a janela do gerador (com historico das licencas emitidas)
echo   gerar-licenca.bat chaves
echo       cria o par de chaves (somente na primeira vez)
echo   gerar-licenca.bat "Nome do cliente" XXXX-XXXX-XXXX-XXXX [pasta]
echo       gera a chave de licenca para o codigo da maquina do cliente
echo       (com [pasta]: salva tambem o arquivo privatekey.lic nessa pasta)
echo.
echo Chave privada: %%USERPROFILE%%\.portocabral\licenca-privada.key
echo   (ou o caminho da variavel PORTOCABRAL_CHAVE_PRIVADA)
echo.

if /i "%~1"=="chaves" (
    java "%GERADOR%" chaves
) else (
    if "%~3"=="" (
        java "%GERADOR%" gerar "%~1" "%~2"
    ) else (
        java "%GERADOR%" gerar "%~1" "%~2" "%~3"
    )
)

if %errorlevel% neq 0 (
    echo.
    echo ERRO: veja a mensagem acima.
    pause
    exit /b 1
)

echo.
pause
