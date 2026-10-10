# Minecraft: Kingdoms — Fase 2: relatório

**Data:** 2026-10-10 · **Plano:** [`fase-2-plano.md`](fase-2-plano.md) · **Versão do mod:** 0.2.0 · **Save:** schema v3

## 1. O que foi entregue

- **Súditos físicos** (`kingdoms:citizen`): cada súdito do reino vira um NPC humanoide com o **mesmo UUID** do registro, skin padrão do jogo escolhida pelo UUID, nome e profissão sobre a cabeça e inventário de 9 espaços salvo na própria entidade. Nascem na capital quando ela está carregada; reinos da Fase 1 ganham os NPCs ao abrir o mundo. A morte do NPC tira o súdito do reino (e fica no log); NPC sem registro (reino apagado) some sozinho; NPC removido sem morrer (caiu do mundo) volta a nascer na capital.
- **Ordens** (`dev.kingdoms.orders`): tipo, área, prioridade 1–5, estado (ativa, pausada, concluída, cancelada), motivo de bloqueio, material e progresso. Só dentro do território do próprio reino, com volume máximo configurável (`orders.maxOrderVolume`, padrão 32 768 blocos) e com depósito definido.
- **Cinco trabalhos** (`dev.kingdoms.jobs`):
  - agricultor: colhe plantação madura e replanta com uma semente da colheita (ordem contínua);
  - lenhador: corta troncos, de baixo para cima;
  - minerador: escava a área de cima para baixo; nunca quebra fluidos, blocos inquebráveis, blocos com conteúdo (baús, fornalhas) nem blocos que a ferramenta não colhe (ex.: obsidiana com picareta de pedra);
  - transportador: leva itens soltos e o conteúdo de contêineres da área para o depósito;
  - construtor: busca o material no depósito, preenche a área de baixo para cima e devolve o que sobrar.
- **Máquina de trabalho** (`WorkGoal`): pegar ordem → ir ao alvo → trabalhar (tempo pela dureza) → levar ao depósito quando enche → repetir. Reserva por alvo (dois trabalhadores nunca pegam o mesmo bloco), replanejamento quando surge ordem mais urgente, detector de travamento (sem progresso no caminho = alvo inalcançável), volta à capital após 10 falhas seguidas.
- **Bloqueio explicado:** sem depósito, depósito sumiu, depósito cheio, falta de material, área inalcançável, esperando plantação crescer. Aparece traduzido em `/kingdom orders`.
- **Cetro Real** (`kingdoms:royal_scepter`, receita esmeralda + ouro + graveto; o fundador ganha um): clique num bloco marca o primeiro canto, agachado + clique marca o segundo.
- **Comandos novos** em `/kingdom`: `stockpile [pos]`, `assign <súdito> <profissão>`, `select <de> <até>`, `order farm|chop|mine|haul [prioridade]`, `order build <bloco> [prioridade]`, `order pause|resume|cancel <id>`, `order priority <id> <n>`, `orders` (com botões clicáveis de pausar, retomar e cancelar) e `citizens` (profissão, ordem, o que o NPC faz agora, posição e carga).
- **Persistência:** schema v3 com migração v2 -> v3 (ordens vazias, súditos sem ordem e ainda não materializados). Reservas ficam só em memória e são refeitas ao reabrir (GDD §14.6).
- Tudo traduzido em `en_us` e `pt_br`.

## 2. Critérios de aceite

| # | Critério | Resultado | Evidência |
|---|---|---|---|
| C1 | Súditos físicos | OK | Teste de jogo `citizens_become_npcs` confere um NPC por súdito com o mesmo UUID e a saída do súdito morto; no cliente real, o reino criado na Fase 1 ganhou 5 NPCs ao abrir o mundo |
| C2 | Cinco trabalhos | OK | Um teste de jogo por profissão (`farmer_…`, `lumberjack_…`, `miner_…`, `carrier_…`, `builder_…`); no cliente real as 5 ordens foram cumpridas no mesmo mundo |
| C3 | Tarefas persistem | OK | Ida e volta do codec com ordem, prioridade, bloqueio e atribuição; no servidor dedicado as profissões dadas antes de reiniciar continuaram valendo |
| C4 | Sem duplicar itens | OK | Os testes contam itens nas duas pontas: 3 trigos, 4 toras, 9 + 9 blocos, 10 ferros + 5 maçãs + 3 pães, 20 pedras = 9 colocadas + 11 devolvidas. No cliente real o baú terminou com exatamente o que saiu do mundo (32 pedras − 9 = 23) |
| C5 | Parar e replanejar | OK | `pause_stops_and_resume_continues`: pausar libera o trabalhador na hora, nada é escavado por 100 ticks e retomar volta ao trabalho. Testes unitários de prioridade e troca de ordem |
| C6 | Bloqueio explica o motivo | OK | `builder_waits_for_material_then_builds` vê `missing_materials` antes de o material chegar; motivos traduzidos em `/kingdom orders` |
| C7 | Diagnóstico | OK | `/kingdom citizens` e `/kingdom orders` conferidos no cliente (captura abaixo) |

Validação extra: o jar do zip do CurseForge rodou num servidor Forge 65.1.0 instalado pelo instalador oficial.

Capturas no cliente (pt-BR) em `/mnt/project-files/kingdoms/`: `fase-2-suditos.png` (súditos com skins e ferramentas) e `fase-2-diagnostico.png` (`/kingdom citizens`).

## 3. Testes

| Suite | Testes |
|---|---|
| `OrdersTest` (novo) | 8 (regras de criação, terra do vizinho, prioridade e escolha, pausar/cancelar libera, troca de profissão, bloqueio, codec, morte e exclusão) |
| `V2MigrationTest` (novo) | 2 (save da Fase 1 abre com súditos à espera do NPC, migração mantém ids) |
| Fases 0 e 1 | 25 |
| **Total unitários** | **35** |
| Testes de jogo | 7 novos + 3 da Fase 1 + `always_pass` do vanilla = 11, todos passando |

## 4. Baseline de desempenho (container de nuvem)

| Medida | Fase 1 | Fase 2 |
|---|---|---|
| Build + testes unitários + zip, testes de jogo e servidor de fumaça em sequência | — | 41 s |
| Servidor dedicado até `Done` | 4,5 s | 4,5 s |
| Testes de jogo | 0,7 s (4) | 2 s (11; o servidor de testes roda os ticks sem esperar) |
| Ordens no cliente real | — | 5 ordens pequenas concluídas em 15 s de jogo |

Busca de alvo: varre só a área da ordem (limitada pelo volume máximo) e só quando o trabalhador precisa de um alvo novo.

## 5. Pendências e decisões para fases seguintes

- Só existe a camada de simulação "perto do jogador": trabalho anda com o chunk carregado. Simulação distante é a Fase 10.
- Ferramentas são virtuais (pedra), sem desgaste; ferramentas reais e custo vêm com a economia (Fase 4).
- Tela gráfica de ordens fica para a interface final (Fase 9); por ora, comandos com botões clicáveis.
- Ordens não têm dono individual: qualquer súdito da profissão pega a ordem mais urgente do reino.
- Construção preenche a área com um único material; plantas e projetos de construção ficam para as fases de construção.
- Súditos não comem, dormem nem têm humor ainda (necessidades vêm nas fases seguintes, GDD §4).
- O minerador escava a área inteira, inclusive o chão onde pisa; cabe ao jogador escolher a área.
