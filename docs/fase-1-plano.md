# Minecraft: Kingdoms — Fase 1: plano técnico

**Data:** 2026-10-10 · **Base:** Fase 0 (PR #1) · **Referência:** GDD §2.4–2.5, §4.1, §7.1–7.2, §14.6, §14.10, §16 (Fase 1), §20

## 1. Objetivo e critérios de aceite

Entregas (GDD §16): entidades de domínio Kingdom/Settlement/Citizen, persistência versionada, fundação, bandeira/capital, nome/brasão, propriedades (território), mapa e debug.

| # | Critério | Como é verificado |
|---|---|---|
| B1 | Fundar reino | Jogador funda pelo estandarte real ou por comando; nasce reino, capital, colonos iniciais e território |
| B2 | Recarregar sem perda | Teste de jogo e servidor dedicado reiniciado: fronteiras, súditos e tesouro iguais antes e depois |
| B3 | IDs persistentes | Reino, assentamento e súdito usam UUID salvo; nada depende de ordem de registro ou de índice |
| B4 | Migração real | Save da Fase 0 (schema v1) abre na Fase 1 (v2) sem perder o `world_id` |
| B5 | Comandos de admin | Listar, inspecionar, renomear, mexer no tesouro, reivindicar/liberar território, validar estado, apagar reino |
| B6 | Mapa | Comando desenha o mapa de chunks ao redor do jogador com donos e legenda (texto, não depende de cor) |
| B7 | Regras de território | Reivindicação exige continuidade e não sobrescreve outro reino (testes unitários) |

## 2. Decisões (defaults, revisáveis)

- **Súditos nesta fase são dados, não entidades.** NPCs físicos, rotina e pathfinding são a Fase 2. Aqui cada súdito tem UUID, nome, reino, assentamento, profissão (`NONE`) e estado; os 5 colonos iniciais (GDD §2.4, acampamento 5–10) nascem com a fundação.
- **Fundação:** bloco `kingdoms:royal_standard` (estandarte real). Colocá-lo num lugar sem dono, sem reino próprio, funda o reino com a capital ali. Comando `/kingdom found <nome>` faz o mesmo onde o jogador está. Um reino por jogador nesta fase (rei = dono).
- **Brasão:** dado simples, cor primária e secundária (`DyeColor`) + símbolo de uma lista fixa; trocado por comando. A arte do brasão e o Livro do Reino ficam para a Fase 9.
- **Tesouro:** inteiro de "coroas" sem economia ainda; só admin altera. A economia real chega na Fase 4.
- **Território:** por chunk e por dimensão. Fundação reivindica raio configurável (padrão 1 = 3×3 chunks). Reivindicar exige chunk vizinho já do reino; chunk de outro reino é recusado (contestação vem na Fase 7). Custo administrativo fica para a Fase 4.
- **Proteção de blocos** nas terras do reino ainda não existe (GDD §13.3 é Fase 10); a fase só registra a posse.
- **Config de servidor** `kingdoms-server.toml` nasce aqui: raio inicial, número de colonos iniciais, máximo de chunks por reino.

## 3. Arquitetura

```
world/      Kingdom, Settlement, Citizen, Emblem, KingdomRegistry (regras puras, sem classes do Minecraft além de codecs)
territory/  ClaimMap (chunk -> reino por dimensão), regras de continuidade
world/      KingdomsWorldData passa a guardar KingdomRegistry + ClaimMap (schema v2)
persistence/ Migrations: passo v1 -> v2 (adiciona listas vazias, mantém world_id)
command/    /kingdom (jogador) e /kingdoms admin (operador, nível gamemaster)
block/      RoyalStandardBlock + item
gametest/   fundação, save/reload com reinos, colocar estandarte
```

Regra 20.8: toda mudança passa pelo servidor (`KingdomService`), que valida dono, alcance e posse antes de alterar o estado e marca o save como sujo.

## 4. Arquivos tocados

Novos pacotes acima, `KingdomsWorldData`, `SchemaVersion` (2), `Migrations`, `KingdomsConfig` (spec de servidor), `KingdomsMod` (registros e comandos), recursos (blockstate, modelo, textura, loot table, receita, lang en_us/pt_br, test_instance).

## 5. Dependências

Nenhuma nova.

## 6. Migrações

v1 -> v2: adiciona `kingdoms: []` e `claims: []`, preserva `world_id`. Testado em unitário (JSON v1 fixo) e por save v1 real gerado na Fase 0.

## 7. Testes

- Unitários: fundar (nome inválido, jogador já rei, local ocupado), continuidade de reivindicação, recusa de sobreposição, liberar chunk, limite de chunks, codec ida e volta, migração v1 -> v2, validação de estado inválido (súdito apontando para assentamento inexistente etc).
- Jogo: fundar e recarregar por armazenamento novo; colocar o estandarte funda e reivindica.
- Servidor dedicado: smoke test do CI continua; reinício manual com reino fundado.

## 8. Riscos

| Risco | Mitigação |
|---|---|
| Save grande com muitos chunks | Claims guardados por dimensão como lista compacta de `long` |
| Estado inconsistente após edição manual ou bug | `/kingdoms admin validate` lista problemas; carregamento não apaga nada sozinho |
| Escopo de UI | Só comandos e mensagens traduzidas nesta fase |
