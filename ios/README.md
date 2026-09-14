# iOS / NFC Key

## Situação para hotel keys no Brasil

A rota oficial da Apple para credenciais NFC seguras de hotel no Brasil é a **NFC & SE Platform** com o framework **SecureElementCredential**.

Pontos importantes para esta arquitetura:

- hotel keys são um caso de uso aceito pela Apple;
- no Brasil, o suporte existe a partir do iOS 18.1 em aparelhos elegíveis;
- os testes exigem iPhone real com NFC (não há emulação equivalente no Simulator);
- o desenvolvedor precisa solicitar o entitlement da NFC & SE Platform;
- a organização precisa configurar/aprovar o produto e o applet no Apple Business Register (ABR);
- o terminal/leitor deve conversar em ISO 14443-4 / ISO 7816-4.

A API `CardSession` de HCE puro existe, mas a documentação da Apple a trata como HCE para a Área Econômica Europeia. Para nosso cenário brasileiro, o desenho alvo deve usar NFC & SE Platform / SecureElementCredential.

## Bloqueio atual

O cartão que o `bis_api` grava hoje é MIFARE Classic. A NFC & SE Platform também não converte um iPhone em um clone genérico de MIFARE Classic.

Antes de iniciar provisioning, entitlements e applet Apple, precisamos confirmar que a fechadura pode aceitar um protocolo ISO 14443-4 / ISO 7816-4 ou receber atualização/módulo de leitor compatível.

## Segurança proposta

- HPASS permanece apenas no backend/bis_api;
- credenciais móveis devem ser específicas por dispositivo, reserva e validade;
- material criptográfico de apresentação deve ficar no Secure Element quando a arquitetura final for definida;
- revogação e auditoria ficam no backend;
- nenhuma chave mestra deve ser colocada no bundle do app.

## Próxima fase iOS

Depois da validação física do protocolo da fechadura:

1. solicitar entitlement NFC & SE Platform para hotel keys;
2. registrar produto/applet no ABR;
3. criar target iOS 18.1+;
4. provisionar `CredentialSession.Credential`;
5. implementar transação contactless e device binding;
6. integrar emissão/revogação ao serviço Mobile Key do backend.
