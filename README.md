# NFC Key

POC para estudar chave móvel de hotel via NFC, integrando futuramente com o `bis_api`/BIS Hotel 5.7.

## Objetivo desta fase

1. Android: testar Host Card Emulation (HCE / ISO-DEP / APDU) em telefone real.
2. Detectar se a fechadura Be-Tech/SAGA aceita comunicação ISO 14443-4 ou se depende exclusivamente de MIFARE Classic.
3. Ler, sem copiar segredos, o tipo/UID/tecnologias de um cartão físico de laboratório.
4. Manter HPASS e chaves do hotel **fora do aplicativo**; estes segredos permanecem no `bis_api`/servidor.
5. Preparar contrato de credencial móvel curta, revogável e vinculada ao dispositivo para a fase de integração.

## Limitação técnica conhecida

O `bis_api` atual grava cartões **MIFARE Classic** usando autenticação por setor/bloco. Android HCE padrão emula cartões ISO-DEP (ISO/IEC 14443-4) e recebe APDUs ISO/IEC 7816-4; ele não emula genericamente um MIFARE Classic físico nem permite escolher o UID NFC do telefone. Portanto, este APK é um **laboratório de compatibilidade**: se a fechadura só falar MIFARE Classic nativo, será necessário evoluir o leitor/firmware da fechadura ou adicionar uma ponte compatível com chave móvel.

## Android

- App nativo Java, sem SDKs de terceiros.
- AID de laboratório: `F04E46434B455931`.
- Serviço `HostApduService`.
- Modo de teste armado/desarmado.
- Contador e log da última APDU recebida.
- Diagnóstico de NFC/HCE.
- Leitura passiva do UID e das tecnologias expostas por um cartão aproximado do telefone.

O workflow `.github/workflows/android-build.yml` gera o artefato `nfc-key-debug-apk`.

## iOS

A arquitetura está documentada em `ios/README.md`. No Brasil, a rota oficial para hotel keys é a NFC & SE Platform / SecureElementCredential, sujeita a entitlement e aprovação da Apple. A emulação precisa conversar em ISO 14443-4 / ISO 7816-4; isso também não transforma o iPhone em um MIFARE Classic genérico.

## Segurança

- Nunca versionar HPASS.
- Nunca enviar HPASS ou chaves de setor para o celular.
- Nenhuma credencial real é embutida neste POC.
- Testar apenas com cartão/fechadura autorizados de laboratório.
