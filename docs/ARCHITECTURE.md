# Arquitetura NFC Key

## 1. Estado real do sistema atual

O `bis_api` preserva o codec proprietário `btlock57L.dll` e usa o shim PC/SC para gravar cartões MIFARE Classic pelo ACR122U. O fluxo físico é:

```text
PMS / Totem
   -> bis_api
   -> btlock57L.dll (codec Be-Tech/SAGA)
   -> AcsReader.dll shim PC/SC
   -> ACR122U
   -> MIFARE Classic
   -> fechadura
```

O HPASS é segredo da instalação do hotel e permanece somente no lado servidor/Windows. Ele não deve ser colocado no telefone, no GitHub nem em payload de credencial móvel.

## 2. O que o Android consegue emular oficialmente

`HostApduService` expõe emulação de cartão baseada em ISO-DEP / ISO 14443-4 e APDUs ISO 7816-4, selecionadas por AID.

Isso não equivale a emular um MIFARE Classic completo:

- não há API Android pública para escolher o UID NFC arbitrariamente;
- HCE não implementa a autenticação Crypto1/setores do MIFARE Classic como um cartão físico;
- uma fechadura que fala somente comandos MIFARE Classic pode nem chegar ao `HostApduService`.

Por isso a versão 0.1 é um laboratório de compatibilidade.

## 3. Teste de campo da versão 0.1

1. Instalar o APK em Android com NFC/HCE.
2. Abrir `NFC Key Lab`.
3. Confirmar `NFC: SIM`, `NFC ligado: SIM` e `HCE: SIM`.
4. Tocar em **Armar teste HCE**.
5. Manter o telefone desbloqueado.
6. Aproximar da fechadura.
7. Observar `APDUs recebidas`.

### Interpretação

- **contador > 0**: a fechadura/leitor iniciou comunicação que chegou ao HCE. Guardar a APDU para análise e evoluir o protocolo.
- **contador = 0** após várias tentativas**:** forte evidência de que a fechadura não está selecionando um app ISO-DEP/AID e provavelmente está no caminho MIFARE Classic legado.

A ausência de APDU não é, isoladamente, prova matemática do protocolo do leitor; deve ser combinada com o teste do cartão físico e, se necessário, captura do lado leitor.

## 4. Diagnóstico do cartão físico

A opção **Ler cartão físico por 12 s** usa apenas descoberta NFC do Android para registrar:

- UID apresentado pelo cartão;
- lista de tecnologias anunciadas pelo chipset;
- presença de `MifareClassic` quando suportada pelo aparelho.

Ela não tenta descobrir HPASS, não autentica setores e não copia o conteúdo da chave.

## 5. Arquitetura de produção proposta

Se a fechadura puder trabalhar com ISO 14443-4 / ISO 7816-4, a chave móvel deve ser diferente de um clone de cartão:

```text
PMS/Hotelaria
    -> orquestrador / módulo de reservas
    -> serviço Mobile Key no backend
       - valida hóspede, reserva, quarto e período
       - vincula credencial ao dispositivo
       - emite credencial curta e revogável
       - assina payload
    -> app NFC Key
       - armazena material no Android Keystore / iOS Secure Element
       - apresenta somente credencial necessária
    -> fechadura/leitor
       - valida assinatura, validade e anti-replay
```

### O que nunca sai do servidor

- HPASS;
- chaves MIFARE de setor;
- segredos mestres do hotel;
- chaves privadas de emissão.

## 6. Se a fechadura for somente MIFARE Classic

Não tentar contornar o controlador NFC do telefone por root, alteração de UID ou APIs privadas. Para produção, os caminhos tecnicamente sustentáveis são:

1. firmware/leitor da fechadura com suporte oficial a credencial móvel ISO-DEP;
2. módulo/leitor compatível com DESFire/ISO 14443-4 e credenciais móveis;
3. SDK/protocolo mobile oficial do fabricante, se existir para o modelo;
4. ponte controlada no hardware da fechadura que receba a credencial móvel e acione o mecanismo autorizado.

A decisão deve preservar compatibilidade com cartão físico durante a migração.

## 7. Próxima integração com bis_api

O `bis_api` atual continua sendo a fonte de verdade para emissão de cartão físico. A futura API móvel deve ser separada do endpoint de gravação MIFARE e jamais retornar HPASS.

Endpoints sugeridos para a fase seguinte:

```text
POST /api/mobile-key/enroll-device
POST /api/mobile-key/issue
POST /api/mobile-key/revoke
GET  /api/mobile-key/status/{credentialId}
```

A emissão deverá exigir reserva válida, identidade autenticada do hóspede, room/door id, janela `validFrom`/`validUntil`, nonce, device binding e auditoria.
