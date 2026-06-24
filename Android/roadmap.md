# Roadmap de compatibilidade Android para o vLabeler

Este documento descreve um plano incremental para tornar o vLabeler compatível com Android a partir do código atual, que hoje está centrado em Compose Desktop/JVM. A meta é chegar a um aplicativo Android funcional sem quebrar a distribuição desktop existente.

## Objetivos

- Manter o app desktop atual funcionando durante toda a migração.
- Isolar regras de negócio, formatos de projeto, parsers, plugins e estado compartilhável em módulos multiplataforma.
- Substituir integrações JVM/Desktop por abstrações com implementações Android.
- Entregar uma primeira versão Android com edição básica de projetos, reprodução/visualização de áudio e persistência via Storage Access Framework.
- Postergar recursos muito dependentes de desktop, como VLC/JNA/AWT, para fases posteriores ou alternativas nativas.

## Estado atual do projeto

- O Gradle usa Kotlin Multiplatform apenas com target `jvm`.
- A UI está em Compose Desktop e usa APIs desktop, como AWT/Swing, previews desktop e distribuição nativa desktop.
- Há dependências sem suporte direto no Android, incluindo `compose.desktop.currentOs`, `vlcj`, LWJGL, Ktor Apache engine, Logback clássico e APIs Java desktop.
- O código acessa arquivos por `java.io.File`, o que precisa ser adaptado para o modelo de permissões e documentos do Android.
- Recursos e plugins ficam em `resources/common`, o que é um bom ponto de partida para empacotar assets compartilhados.

## Estratégia geral

1. Transformar o projeto em Kotlin Multiplatform real, com `commonMain`, `desktopMain` e `androidMain`.
2. Mover lógica reutilizável do `src/jvmMain` para `src/commonMain` em pequenos lotes.
3. Criar interfaces `expect/actual` ou serviços injetáveis para diferenças de plataforma.
4. Criar um módulo/app Android separado dentro de `Android/` ou um módulo Gradle `:androidApp` apontando para essa pasta.
5. Recriar apenas os fluxos essenciais da UI em Compose Multiplatform/Compose Android antes de tentar portar todos os diálogos e ferramentas.

## Fase 0 — Preparação e auditoria

- Inventariar todos os usos de APIs desktop:
  - `java.awt.*`, `javax.swing.*` e `SwingPanel`.
  - `java.awt.Desktop` para abrir arquivos, pastas e URLs.
  - `java.awt.FileDialog` e diálogos customizados baseados em `File`.
  - `vlcj`, LWJGL e qualquer integração nativa desktop.
  - Engines Ktor JVM-específicas, como Apache.
- Classificar arquivos em três grupos:
  - **Compartilhável:** modelos, serialização, parsers, writers, validações e estado puro.
  - **Adaptável:** acesso a arquivos, logs, tracking, rede e execução de plugins.
  - **Desktop-only:** vídeo via VLC, janelas desktop, AWT/Swing e empacotamento desktop.
- Definir critérios mínimos do MVP Android.

## Fase 1 — Reestruturação Gradle

- Adicionar Android Gradle Plugin ao `pluginManagement` e aos plugins do projeto.
- Criar o target Android no bloco `kotlin`.
- Introduzir source sets:
  - `commonMain` para domínio e UI compartilhável.
  - `desktopMain` para o código hoje em `jvmMain` que deve continuar desktop-only.
  - `androidMain` para implementações Android.
- Avaliar a atualização do Android Gradle Plugin, pois a versão declarada atualmente é antiga para builds Android modernos.
- Configurar `compileSdk`, `minSdk`, `namespace` e Manifest Android.

## Fase 2 — Separação de domínio e plataforma

Mover gradualmente para `commonMain`:

- Modelos de projeto, entrada, labeler e plugin.
- Serialização JSON e DTOs.
- Parsers e writers que não dependem diretamente de `File`.
- Regras de edição, seleção, filtros, undo/redo e validações.
- Strings e recursos de UI que sejam independentes de plataforma.

Criar abstrações para:

- Sistema de arquivos e documentos.
- Diretórios de configuração, cache, logs e dados do app.
- Abertura de URLs e compartilhamento/exportação.
- Seletores de arquivos e pastas.
- Reprodução de áudio/vídeo.
- Clipboard.
- Tracking/analytics.
- Logs.

## Fase 3 — Arquivos e permissões no Android

- Substituir fluxos baseados em caminhos absolutos por URIs quando estiver no Android.
- Usar Storage Access Framework para abrir/importar/exportar projetos e amostras.
- Criar um `DocumentProvider`/`FileGateway` interno com operações como:
  - Abrir stream de leitura.
  - Abrir stream de escrita.
  - Listar documentos de uma pasta autorizada.
  - Resolver nome exibível e metadados.
  - Persistir permissões de URI quando necessário.
- Definir como projetos existentes com caminhos relativos serão mapeados no Android.
- Adicionar migração segura para projetos abertos a partir de documentos Android.

## Fase 4 — UI Android MVP

- Criar uma `MainActivity` com Compose Android.
- Montar navegação mínima:
  - Tela inicial.
  - Abrir projeto.
  - Criar projeto rápido.
  - Editor básico.
  - Preferências essenciais.
- Adaptar layouts para toque:
  - Áreas clicáveis maiores.
  - Gestos de zoom/pan.
  - Menus contextuais alternativos.
  - Teclado virtual e ausência de atalhos físicos.
- Revisar componentes desktop-only, especialmente previews e diálogos.
- Manter componentes compartilháveis quando não dependerem de AWT/Swing.

## Fase 5 — Áudio e visualização

- Avaliar a substituição de bibliotecas JVM/desktop por alternativas Android:
  - Reprodução: ExoPlayer/Media3 ou APIs nativas Android.
  - Decodificação/metadata: MediaExtractor, MediaMetadataRetriever ou biblioteca multiplataforma compatível.
  - Waveform: pré-processamento em Kotlin comum quando possível; fallback Android para decodificação.
- Implementar sincronização básica entre cursor do editor e reprodução.
- Medir performance com arquivos grandes e dispositivos intermediários.

## Fase 6 — Plugins e scripts

- Auditar o runtime JavaScript atual e sua compatibilidade Android.
- Substituir GraalVM JS no Android, se necessário, por uma opção compatível, como QuickJS, Rhino ou execução limitada própria.
- Definir uma sandbox de permissões para plugins no Android.
- Garantir que plugins baseados em arquivos recebam APIs compatíveis com URI/documentos, não apenas caminhos locais.
- Criar testes de compatibilidade com os plugins em `resources/common/plugins`.

## Fase 7 — Recursos desktop-only

Decidir por recurso:

- **Vídeo via VLC/vlcj:** substituir por Media3 ou desabilitar temporariamente no Android.
- **Janelas separadas:** converter para telas, painéis ou dialogs Compose Android.
- **Abrir pasta no sistema:** substituir por intents/document tree quando aplicável.
- **File dialogs desktop:** substituir por Activity Result APIs e Storage Access Framework.
- **LWJGL/native file dialog:** manter apenas no source set desktop.

## Fase 8 — Qualidade, testes e CI

- Adicionar testes comuns para parsers, writers e serialização.
- Adicionar testes unitários Android para gateways de documento e ViewModels.
- Adicionar testes instrumentados para fluxos principais de abertura/salvamento.
- Configurar CI para:
  - Build desktop existente.
  - Build Android debug.
  - Testes comuns/JVM.
  - Lint Android.
- Criar matriz mínima de dispositivos/emuladores para validação manual.

## Fase 9 — Empacotamento e publicação

- Definir application id, ícones adaptativos e nome de exibição.
- Configurar assinatura local e de release.
- Gerar APK/AAB.
- Criar política de privacidade, se tracking/analytics permanecer ativo.
- Revisar permissões declaradas no Manifest para evitar permissões desnecessárias.
- Preparar canal de distribuição interno antes de qualquer publicação ampla.

## MVP sugerido

A primeira versão Android deve conter:

- Abrir/criar projeto por seletor de documentos.
- Carregar labelers e plugins empacotados como assets.
- Listar entradas do projeto.
- Exibir waveform ou visualização simplificada do áudio.
- Reproduzir áudio básico.
- Editar parâmetros principais de uma entrada.
- Salvar/exportar o projeto.
- Preferências mínimas.

Ficam fora do MVP:

- Vídeo avançado.
- Execução completa de todos os plugins se o runtime JavaScript ainda não estiver validado.
- Paridade total com atalhos e menus desktop.
- Empacotamento Play Store final.

## Riscos principais

- Dependências desktop sem equivalente Android direto.
- Uso extensivo de `java.io.File` e caminhos absolutos.
- Adaptação de uma UI desenhada para mouse/teclado para toque.
- Performance de waveform/áudio em dispositivos móveis.
- Compatibilidade e segurança do sistema de plugins.
- Diferenças entre permissões de armazenamento em versões recentes do Android.

## Marcos de entrega

1. **Build Android vazio:** módulo Android compila e abre uma tela inicial.
2. **Domínio compartilhado:** modelos e parsers principais compilam em `commonMain`.
3. **Abertura de projeto:** app Android abre um projeto via SAF e lista entradas.
4. **Editor básico:** app edita e salva parâmetros principais.
5. **Áudio básico:** reprodução e cursor funcionam em Android.
6. **Plugins básicos:** plugins essenciais rodam ou têm alternativa nativa.
7. **Beta interno:** build debug/release assinado para testes reais.
8. **Paridade planejada:** backlog restante priorizado com base no uso real.

## Próximas ações recomendadas

- Criar o módulo Android mínimo e validar versões de Gradle/AGP/Compose compatíveis.
- Fazer uma auditoria automatizada dos imports desktop-only.
- Escolher a abstração de arquivos antes de mover parsers para `commonMain`.
- Definir explicitamente quais recursos entram no MVP Android.
- Adicionar testes de regressão para formatos de projeto antes de refatorar o acesso a arquivos.
