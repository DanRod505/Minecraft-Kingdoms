# Minecraft: Kingdoms — Fase 0: relatório

**Data:** 2026-10-10 · **Plano:** [`fase-0-plano.md`](fase-0-plano.md) · **Base:** Minecraft 26.2 + Forge 65.1.0

## 1. O que o MDK real definiu (regra 20.3)

Tudo abaixo foi lido nos arquivos do `forge-26.2-65.1.0-mdk.zip` e nos jars baixados, não em tutoriais.

| Item | Valor efetivo | Onde foi confirmado |
|---|---|---|
| JDK | Java 25 (toolchain; Gradle roda em 21+ e baixa o 25 via foojay/Adoptium) | `build.gradle` do MDK, `version.json` do cliente (`java_version: 25`) |
| Gradle | 9.5.0 (wrapper) | `gradle/wrapper/gradle-wrapper.properties` |
| Plugin | ForgeGradle 7 (`net.minecraftforge.gradle`), resolvido e travado em 7.0.40 | cache do Gradle |
| Mappings | nomes oficiais da Mojang (`official/26.2`) | cache do Mavenizer |
| Descritor | `META-INF/mods.toml`, `loaderVersion="[65,)"` | MDK |
| Event bus | EventBus 7: `context.getModBusGroup()`, `Evento.getBus(modBus)` para eventos do mod, `Evento.BUS` estático para eventos de jogo | `ExampleMod.java`, fontes do Forge |
| Registro | `DeferredRegister` + `RegistryObject` | MDK |
| Config | `ForgeConfigSpec` + `context.registerConfig` | MDK |
| Persistência | `SavedData` + `SavedDataType(id, construtor, Codec, DataFixTypes)`; armazenamento do servidor em `MinecraftServer.getDataStorage()`; Forge aceita `DataFixTypes` nulo para o mod versionar o próprio formato | `SavedDataStorage.java` |
| Rede | `ChannelBuilder.named(...).networkProtocolVersion(int).acceptedVersions(...).simpleChannel()` | `net/minecraftforge/network` |
| GameTest | Registro vanilla `Registries.TEST_FUNCTION` + JSON `test_instance`. A anotação `@GameTest` do Forge existe, mas nada no Forge 65.1.0 registra métodos anotados, e a estrutura padrão `forge:empty3x3x3` não existe no jar | busca em todos os jars do Forge |
| Pack | `pack.mcmeta` com formato 107 | MDK, `version.json` (`data_major: 107`) |

Ajuste ao MDK: o `mods.toml` de exemplo tinha `versionRange="[26.2,26.3,)"` (vírgula sobrando); o nosso usa `[26.2,26.3)`.

## 2. O que foi entregue

- Projeto Gradle a partir do MDK, mod id `kingdoms`, pacote `dev.kingdoms`, versões em `gradle.properties`.
- `core`: `KingdomsConfig` (config comum `kingdoms-common.toml`, chave `logging.debug`) e `KingdomsLog` (logger `kingdoms`, nível DEBUG/INFO pela config).
- `persistence`: `SchemaVersion` (v1), `Migrations` (ponto único de migração; recusa save de versão mais nova em vez de sobrescrever), `VersionedCodec` (grava `schema_version` e migra antes de ler).
- `world`: `KingdomsWorldData`, dado mínimo por mundo em `<mundo>/data/kingdoms/world_state.dat`, com `world_id` UUID estável.
- `network`: canal `kingdoms:main`, protocolo v1, versão exata exigida no login.
- Testes unitários (JUnit 5), teste de jogo `kingdoms:world_state_round_trip`, script `scripts/server-smoke.sh` e CI em `.github/workflows/build.yml`.
- `en_us` e `pt_br` desde já. Convenções em [`convencoes.md`](convencoes.md).

## 3. Critérios de aceite

| # | Critério | Resultado | Evidência |
|---|---|---|---|
| A1 | Build reproduzível | OK | três `clean build` sem cache geraram o mesmo jar (SHA-256 `d24d2fbb…e47d`); plugin, wrapper e versões travados; CI repete a verificação |
| A2 | Mod carrega limpo | OK | Servidor e cliente: `kingdoms` carrega sem exceção nem aviso do mod (só o aviso padrão do Forge ao criar `kingdoms-common.toml` pela primeira vez). Cliente rodado em tela virtual com OpenGL por software (Mesa) até o carregamento de texturas; os únicos erros do log são do ambiente (sem login no Realms, sem dispositivo de áudio) |
| A3 | Servidor dedicado sobe | OK | `runServer --nogui` chega a `Done (4.5s)` e para com `stop`, salvando o mundo |
| A4 | Save funciona | OK | GameTest grava e relê `world_state.dat` por um armazenamento novo; reiniciando o servidor dedicado o `world_id` é o mesmo |
| A5 | Pacote de teste | OK | 9 testes unitários + 1 teste de jogo; um teste de jogo quebrado de propósito faz o Gradle sair com erro |
| A6 | Config e logging | Parcial | Config comum gerada e validada. Não há config de servidor ainda: nenhuma opção de servidor tem uso na Fase 0, ela entra com a primeira regra de jogo na Fase 1 |

## 4. Testes

| Suite | Testes | O que cobre |
|---|---|---|
| `VersionedCodecTest` | 4 | grava versão, ida e volta, rejeita sem versão, rejeita versão futura |
| `MigrationsTest` | 3 | versão atual passa, versão inválida e futura são recusadas |
| `KingdomsConfigTest` | 2 | config vazia recebe padrões, valor inválido volta ao padrão |
| `kingdoms:world_state_round_trip` (GameTest) | 1 | salvar no disco e recarregar o estado do mundo |

## 5. Baseline de desempenho (container de nuvem, 2026-10-10)

| Medida | Valor |
|---|---|
| Primeira build (baixa Minecraft, Forge e JDK 25) | 4 min 34 s |
| Build com cache | ~23 s |
| Servidor dedicado até `Done` | 4,5 s |
| Testes de jogo | 0,9 s (2 testes, incluindo o `always_pass` do vanilla) |

Hardware de referência para benchmarks ainda em aberto (GDD §21).

## 6. Pendências e riscos para a Fase 1

- Config de servidor (`kingdoms-server.toml`) entra com a primeira regra de jogo.
- Quando o Forge passar a registrar `@GameTest`, dá para migrar os testes; até lá o registro vanilla funciona.
- Os testes de jogo ficam no jar principal; o servidor de testes só os executa em ambiente de desenvolvimento, mas o `/test` em produção também os enxerga. Se incomodar, separar em source set próprio.
- `runClient` no CI não roda (precisa de tela); validado em tela virtual nesta fase. Vale o Dalton abrir o cliente uma vez no PC e entrar num mundo single player para conferir.
