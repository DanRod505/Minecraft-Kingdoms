# Minecraft: Kingdoms — Fase 1: relatório

**Data:** 2026-10-10 · **Plano:** [`fase-1-plano.md`](fase-1-plano.md) · **Versão do mod:** 0.1.0 · **Save:** schema v2

## 1. O que foi entregue

- **Domínio** (`dev.kingdoms.world`, sem classes do Minecraft): `Kingdom`, `Settlement`, `Citizen`, `Emblem`, `KingdomState` com as regras de fundação, território, tesouro, nome, brasão, exclusão e validação.
- **Território** (`dev.kingdoms.territory`): posse por chunk e por dimensão, salva como listas compactas de `long`.
- **Fundação:** bloco **Estandarte Real** (`kingdoms:royal_standard`, receita: ouro + estandarte + troncos) ou `/kingdom found <nome>`. Nasce o reino, a capital (acampamento) no local, 5 colonos com nomes e o território inicial 3×3 chunks. Um estandarte recusado não é colocado.
- **Comandos do jogador:** `/kingdom found | info | rename | emblem <cor> <cor> <símbolo> | claim | unclaim | map`.
- **Comandos de admin** (`/kingdoms`, operador): `list | info | citizens | treasury <reino> set|add | delete | validate`.
- **Mapa:** `/kingdom map` desenha 11×11 chunks em volta do jogador; cada reino tem letra própria além da cor (GDD §11.5).
- **Config de servidor** `kingdoms-server.toml`: colonos na fundação (5), raio inicial (1), máximo de chunks (64).
- **Persistência:** schema v2 com migração v1 -> v2. O servidor valida o estado ao iniciar e só avisa, nunca apaga dados.
- Tudo traduzido em `en_us` e `pt_br`; um teste garante que nenhuma chave fique sem tradução.
- **Perfil CurseForge:** `./gradlew curseforgeProfile` gera `build/distributions/kingdoms-0.1.0-curseforge.zip` (`manifest.json` com MC 26.2 + Forge 65.1.0 e o jar em `overrides/mods`). O CI também gera e guarda o zip.

## 2. Critérios de aceite

| # | Critério | Resultado | Evidência |
|---|---|---|---|
| B1 | Fundar reino | OK | Teste de jogo coloca o estandarte com um jogador e confere reino, capital na posição do bloco e recusa do segundo estandarte; jogador real fundou por comando num servidor dedicado |
| B2 | Recarregar sem perda | OK | Teste de jogo grava e relê por armazenamento novo e compara o estado inteiro; servidor dedicado reiniciado manteve o reino com 5 súditos, 10 chunks e 500 coroas |
| B3 | IDs persistentes | OK | UUIDs salvos para reino, assentamento e súdito; ida e volta do codec mantém todos |
| B4 | Migração real | OK | O save gerado na Fase 0 (v1) abriu na Fase 1 com o mesmo `world_id` |
| B5 | Comandos de admin | OK | `list`, `treasury add`, `validate` rodados no console com jogador conectado |
| B6 | Mapa | OK | `/kingdom map` conferido no cliente real (captura abaixo) |
| B7 | Regras de território | OK | Testes unitários de continuidade, limite, sobreposição e capital |

Validação extra: o jar do zip rodou num servidor Forge 65.1.0 instalado pelo instalador oficial (ambiente de produção, igual ao do CurseForge) e carregou sem erro do mod.

Capturas no cliente (idioma pt-BR): `fase-1-info.png` e `fase-1-mapa.png` em `/mnt/project-files/kingdoms/`.

## 3. Testes

| Suite | Testes |
|---|---|
| `KingdomStateTest` | 12 (fundação, nomes, terra ocupada, vizinhos, reivindicação, liberar, tesouro, renomear, brasão, excluir, codec, validação) |
| `WorldStateMigrationTest` | 2 (save v1 abre, migração preserva campos) |
| `LangFilesTest` | 2 (mesmas chaves nos dois idiomas, toda chave do código traduzida) |
| Fase 0 (`VersionedCodec`, `Migrations`, `KingdomsConfig`) | 9 |
| Testes de jogo | 3 do mod (`world_state_round_trip`, `kingdom_survives_reload`, `royal_standard_founds_kingdom`) + o `always_pass` do vanilla |

## 4. Baseline de desempenho (container de nuvem)

| Medida | Fase 0 | Fase 1 |
|---|---|---|
| Build com cache | ~23 s | ~15 s |
| Servidor dedicado até `Done` | 4,5 s | 4,5 s |
| Testes de jogo | 0,9 s (2) | 0,7 s (4) |

## 5. Pendências e decisões para fases seguintes

- Súditos são só dados; o NPC físico com o mesmo UUID entra na Fase 2.
- Quebrar o estandarte não desfaz o reino; perda e troca de capital ficam para a Fase 6 (governo e múltiplos assentamentos).
- Não há proteção de blocos no território (GDD §13.3, Fase 10); a fase só registra a posse.
- Custo de reivindicação e tesouro com moeda real chegam com a economia (Fase 4).
- Brasão é dado (cores + símbolo); a arte e o Livro do Reino ficam para a Fase 9.
- Um save migrado é regravado em v2 só na primeira mudança; até lá continua v1 no disco e é migrado a cada leitura, sem perda.
