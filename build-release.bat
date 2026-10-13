@echo off
setlocal EnableDelayedExpansion
chcp 65001 > nul

echo.
echo ============================================
echo   BUILD - Caravellus
echo ============================================

set ROOT=%~dp0
set FRONTEND_DIR=%ROOT%frontend
set BACKEND_DIR=%ROOT%backend
set STATIC_DIR=%BACKEND_DIR%\src\main\resources\static
set ANGULAR_DIST=%FRONTEND_DIR%\dist\caravellus-frontend\browser
set EMAIL_PADRAO=%BACKEND_DIR%\src\main\resources\email-padrao.properties

:: ---- 0. Conta de e-mail padrao (vai embutida no JAR, fica fora do Git) ----
if not exist "%EMAIL_PADRAO%" (
    echo.
    echo ERRO: Arquivo nao encontrado: %EMAIL_PADRAO%
    echo Copie backend\email-padrao.exemplo.properties para esse caminho e preencha login e chave SMTP do Brevo.
    echo Sem ele, a recuperacao de senha por e-mail nao funciona no sistema instalado.
    pause
    exit /b 1
)
for %%P in (spring.mail.username spring.mail.password app.mail.remetente) do (
    findstr /r /c:"^%%P=..*" "%EMAIL_PADRAO%" > nul
    if !errorlevel! neq 0 (
        echo.
        echo ERRO: %%P vazio em %EMAIL_PADRAO%
        pause
        exit /b 1
    )
)

:: ---- 1. Build do Frontend Angular ----
echo.
echo [1/5] Compilando frontend Angular (producao)...
cd /d "%FRONTEND_DIR%"
call npm run build -- --configuration production
if %errorlevel% neq 0 (
    echo.
    echo ERRO: Falha no build do frontend. Verifique se o Node.js esta instalado.
    pause
    exit /b 1
)

:: ---- 2. Copiar frontend para os recursos estaticos do backend ----
echo.
echo [2/5] Copiando arquivos do frontend para o backend...
if exist "%STATIC_DIR%" rmdir /s /q "%STATIC_DIR%"
mkdir "%STATIC_DIR%"

if not exist "%ANGULAR_DIST%" (
    echo ERRO: Pasta de saida do Angular nao encontrada: %ANGULAR_DIST%
    pause
    exit /b 1
)

xcopy /e /i /q /y "%ANGULAR_DIST%\*" "%STATIC_DIR%\"
if %errorlevel% neq 0 (
    echo ERRO: Falha ao copiar arquivos do frontend.
    pause
    exit /b 1
)
echo Arquivos copiados com sucesso para: %STATIC_DIR%

:: ---- 3. Build do Backend Spring Boot ----
echo.
echo [3/5] Compilando backend Spring Boot (pode demorar alguns minutos)...
cd /d "%BACKEND_DIR%"
call mvnw.cmd package -DskipTests
if %errorlevel% neq 0 (
    echo.
    echo ERRO: Falha no build do backend. Verifique se o Java 25 esta instalado.
    pause
    exit /b 1
)

:: ---- 4. Integrar com Electron ----
echo.
echo [4/5] Integrando JAR ao Electron...
set ELECTRON_DIR=%ROOT%electron
set ELECTRON_RESOURCES=%ELECTRON_DIR%\resources

:: Cria a pasta de recursos no Electron se não existir
if not exist "%ELECTRON_RESOURCES%" mkdir "%ELECTRON_RESOURCES%"

:: Copia o JAR gerado para dentro da pasta do Electron
copy /y "%BACKEND_DIR%\target\backend-0.0.1-SNAPSHOT.jar" "%ELECTRON_RESOURCES%\backend.jar"
if %errorlevel% neq 0 (
    echo ERRO: Falha ao copiar o JAR para o Electron.
    pause
    exit /b 1
)
echo JAR copiado para: %ELECTRON_RESOURCES%\backend.jar

:: ---- 5. Gerar o Executável do Electron (.exe) ----
echo.
echo [5/5] Gerando executável do Electron...
cd /d "%FRONTEND_DIR%"

:: Pasta de saida com a versao do package.json, pontos trocados por hifens (ex.: electron\Caravellus1-3-1)
set VERSAO=
for /f "usebackq delims=" %%V in (`node -p "require('./package.json').version.replace(/\./g, '-')"`) do set VERSAO=%%V
if "%VERSAO%"=="" (
    echo ERRO: Nao foi possivel ler a versao em frontend\package.json.
    pause
    exit /b 1
)
set SAIDA_DIR=Caravellus%VERSAO%

:: Certifique-se de que o electron-builder está instalado no projeto electron
:: O caminho de saida e relativo a pasta frontend
call npm run dist -- -c.directories.output=../electron/%SAIDA_DIR%
if %errorlevel% neq 0 (
    echo ERRO: Falha ao gerar o executável do Electron.
    pause
    exit /b 1
)
echo Executável do Electron gerado com sucesso!

echo.
echo ============================================
echo   BUILD CONCLUIDO COM SUCESSO!
echo ============================================
echo.
echo Executável disponibilizado na pasta: %ELECTRON_DIR%\%SAIDA_DIR%
echo.
pause
