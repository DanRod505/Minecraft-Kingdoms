# Minecraft: Kingdoms — Fase 0: plano técnico

**Data:** 2026-10-10 · **Base fixa:** Minecraft Java 26.2 + Forge 65.1.0 (`forge-26.2-65.1.0-mdk`) · **Mod id:** `kingdoms`
**Referência:** GDD seções 14, 16 (Fase 0), 18.5 e 20.

## 1. Objetivo e critérios de aceite

Entregas (GDD §16): importar o MDK, validar Gradle/JDK efetivo, `runClient` e `runServer`, configurar CI, repositório, convenções, config, logging e mod id, provar cliente/servidor e pacote de teste.

| # | Critério | Como é verificado |
|---|---|---|
| A1 | Build reproduzível | `./gradlew clean build` duas vezes, mesmo resultado; wrapper e versões travados no repo; CI verde |
| A2 | Mod carrega limpo | Log do `runClient` mostra `kingdoms` carregado, sem exceção nem aviso do mod |
| A3 | Servidor dedicado sobe | `runServer` (headless, `nogui`) chega a "Done" e para com `/stop` sem erro |
| A4 | Save funciona | Teste de jogo cria mundo, grava dado mínimo versionado (`schemaVersion`) e recarrega |
| A5 | Pacote de teste | Testes unitários (JUnit) e testes de jogo (GameTest, se o MDK expuser) rodam no CI |
| A6 | Config e logging | Arquivos de config comum/servidor gerados; logger com prefixo e nível configurável |

## 2. O que precisa ser lido no MDK antes de codificar (regra 20.3)

Nada abaixo será presumido; cada item será confirmado nos arquivos efetivos e registrado em `fase-0-relatorio.md`:

1. Versão do JDK (toolchain em `build.gradle`) e versão do Gradle (`gradle/wrapper/gradle-wrapper.properties`).
2. Plugin de build do Forge (ForgeGradle ou sucessor), mappings e configuração de `runs` (client, server, data, gameTestServer).
3. Formato do descritor do mod (`mods.toml` ou equivalente) e propriedades em `gradle.properties`.
4. Classe principal de exemplo: anotação `@Mod`, acesso ao event bus, eventos de ciclo de vida (setup comum/cliente/servidor).
5. API de registro (DeferredRegister ou equivalente) e de config (`ForgeConfigSpec` ou equivalente).
6. Persistência por mundo (SavedData ou equivalente, e se é baseada em Codec/NBT).
7. Networking (canal/payload) e versão de protocolo.
8. Suporte a GameTest e a testes unitários no ambiente de dev.

## 3. Estrutura do projeto

Pacote raiz provisório `dev.kingdoms` (ajustável; o mod id `kingdoms` é fixo). Módulos do GDD §14.2 como pacotes, criados vazios só quando a fase correspondente começar, exceto os de infraestrutura:

```
kingdoms/
  build.gradle, settings.gradle, gradle.properties, gradlew*  (do MDK, ajustados)
  .github/workflows/build.yml       CI: build + testes unitários + gameTestServer
  docs/                              convenções, decisões (ADR), relatório de fase
  src/main/java/dev/kingdoms/
    KingdomsMod.java                 ponto de entrada @Mod
    core/  KingdomsConfig, KingdomsLog, lifecycle
    persistence/  SchemaVersion, contrato de migração (vazio além do v1)
    network/  ProtocolVersion (constante versionada desde já, regra 20.7)
  src/main/resources/  descritor do mod, pack metadata, lang en_us + pt_br
  src/test/java/...                  JUnit (lógica pura)
  src/gametest/ ou equivalente       smoke test de mundo (se suportado)
```

## 4. Convenções

- Código e identificadores em inglês; textos ao jogador via chaves de tradução, com `en_us` e `pt_br` desde a Fase 0.
- Toda lógica de domínio sem dependência de classes do Minecraft quando possível, para teste unitário rápido.
- Servidor é autoridade (regra 20.8); nada de estado de jogo decidido no cliente.
- Dados persistentes e protocolos com versão explícita desde o primeiro arquivo (regra 20.7).
- Commits pequenos por entrega; cada fase termina com `fase-N-relatorio.md` (checklist, testes, benchmark, pendências).

## 5. Arquivos tocados na Fase 0

Arquivos do MDK (build, wrapper, descritor, classe exemplo renomeada) + os listados na seção 3. Nenhum conteúdo de jogo (itens, blocos, entidades) nesta fase.

## 6. Dependências

Somente as que o MDK já declara, mais JUnit para testes unitários se o MDK não incluir. Nenhuma biblioteca externa de runtime.

## 7. Migrações

Nenhuma (primeira versão). Fica criado o contrato: `schemaVersion = 1` em todo dado salvo e um ponto único de migração para fases futuras.

## 8. Testes

- Unitários: leitura de config com valores padrão e inválidos; serialização/round-trip do dado mínimo versionado.
- Jogo: smoke test que sobe o servidor de teste, grava e relê o dado mínimo.
- Manual automatizável: `runServer` headless no CI com timeout, checando o log por "Done" e ausência de exceções do mod.
- Baseline: tempo de build e tempo de boot do servidor registrados no relatório (início do histórico de performance, GDD §18.5).

## 9. Riscos

| Risco | Mitigação |
|---|---|
| A rede da nuvem bloqueia o Maven do Forge e da Mojang (confirmado em 2026-10-10) | Liberar domínios no ambiente ou rodar a build na máquina do Dalton |
| API da 65.1.0 diferente de tutoriais antigos | Só usar o que estiver no MDK e nos jars baixados (regra 20.3) |
| CI do GitHub sem acesso ao Minecraft (EULA/downloads) | Runners do GitHub baixam normalmente; aceitar EULA apenas no diretório de run de teste |
| `runClient` exige tela | No CI só servidor e GameTest; cliente validado manualmente uma vez por fase |

## 10. Estado em 2026-10-10

Plano pronto. Execução bloqueada: o ambiente de nuvem nega acesso a `files.minecraftforge.net`, `maven.minecraftforge.net`, `piston-meta.mojang.com` e `libraries.minecraft.net`, necessários para baixar o MDK e compilar. JDK 21 e Gradle (services.gradle.org) e Maven Central estão acessíveis.
