# Minecraft: Kingdoms — Fase 2: plano técnico

**Data:** 2026-10-10 · **Base:** Fase 1 (schema v2) · **Referência:** GDD §3, §4.1, §4.5–4.6, §12.4, §14.3–14.5, §16 (Fase 2), §20

## 1. Objetivo e critérios de aceite

Entregas (GDD §16): trabalhos básicos (agricultor, lenhador, minerador, transportador, construtor), estado de tarefas, reservas, seleção de áreas, interface de ordens, navegação e recuperação, diagnósticos.

| # | Critério | Como é verificado |
|---|---|---|
| C1 | Súditos físicos | Cada súdito do reino vira um NPC com o mesmo UUID do registro; reinos da Fase 1 ganham NPCs ao carregar |
| C2 | Cinco trabalhos | Teste de jogo por profissão: colhe e replanta, corta toras, escava, transporta e constrói |
| C3 | Tarefas persistem | Ordens, prioridade, estado e atribuição sobrevivem a salvar e recarregar |
| C4 | Sem duplicar itens | Testes contam itens antes e depois: o que sai do mundo ou de um baú entra exatamente uma vez no depósito |
| C5 | Parar e replanejar | Pausar ou cancelar libera o trabalhador e as reservas; ele volta a ficar ocioso ou pega outra ordem |
| C6 | Bloqueio explica o motivo | Ordem sem depósito, sem material, sem trabalhador ou inalcançável mostra o motivo traduzido |
| C7 | Diagnóstico | Comandos mostram estado de cada súdito e de cada ordem |

## 2. Decisões (defaults, revisáveis)

- **NPC `kingdoms:citizen`**: humanoide com as skins padrão do jogo (escolhida pelo UUID), nome e profissão sobre a cabeça, inventário de 9 espaços salvo na própria entidade. Morte do NPC remove o súdito do reino e fica no log.
- **Ordens por comando + Cetro Real**: o cetro marca dois cantos da área (clique direito e agachado + clique direito); `/kingdom order <tipo>` cria a ordem na área marcada. A tela gráfica de ordens fica para a Fase 9 (interface final); comandos com mensagens clicáveis cobrem listar, pausar, retomar, cancelar e mudar prioridade.
- **Depósito do reino**: um baú (ou qualquer contêiner) dentro do território, definido com o cetro (`/kingdom stockpile`). Tudo que é colhido vai para ele; o construtor tira material dele.
- **Profissão por comando**: `/kingdom assign <súdito> <profissão>`. Cada profissão só pega ordens do seu tipo, por prioridade (1 a 5) e depois por antiguidade.
- **Ferramentas**: nesta fase cada profissão usa uma ferramenta de pedra virtual (só para o cálculo de drops e a animação). Ferramentas reais vêm com a economia (Fase 4).
- **Regras de segurança (GDD §20.9)**: ordens só dentro do território do próprio reino; nada fora da área; minerador não quebra blocos com inventário, inquebráveis ou fluidos; nenhum chunk é carregado à força: trabalho só anda com o chunk carregado.
- **Simulação**: só a camada "próxima ao jogador" (GDD §14.3). Simulação distante é a Fase 10.

## 3. Arquitetura

```
orders/   Order, OrderType, OrderStatus, BlockReason (domínio puro) e regras em KingdomState
entity/   CitizenEntity, WorkGoal (máquina de estados), Reservations (memória, por ordem)
jobs/     um Job por profissão: escolhe alvo, executa ação, decide quando depositar
item/     RoyalScepterItem (seleção de área e depósito)
client/   CitizenRenderer
```

Estados do trabalhador: `IDLE -> PICK_ORDER -> MOVE_TO_TARGET -> WORK -> (cheio) MOVE_TO_STOCKPILE -> DEPOSIT -> PICK_ORDER`. Construtor e transportador passam antes por `FETCH`. Sem progresso por 10 s no caminho: tenta outro alvo; o alvo vai para uma lista de inalcançáveis da ordem; se nenhum alvo é alcançável, a ordem fica bloqueada com motivo.

Reservas: cada bloco ou item alvo fica reservado para um único trabalhador, só em memória; recarregar o mundo libera tudo e os trabalhadores re-reservam (GDD §14.6: reconciliar reservas após crash).

## 4. Persistência

Schema v3. Migração v2 -> v3: `orders: []`, súditos ganham `order` vazio e `materialized: false`, reinos ganham `stockpile` vazio. Os NPCs dos reinos antigos nascem na capital quando o chunk dela carrega.

## 5. Testes

- Unitários: criar ordem (fora do território, área grande demais, sem depósito), prioridade e escolha de ordem, pausar/cancelar libera atribuição, codec v3 e migração v2 -> v3.
- Jogo: um por profissão com contagem de itens; pausa interrompe; bloqueio por falta de material; NPC nasce com o UUID do súdito.
- Cliente real conectado ao servidor dedicado: fundar, marcar área, criar ordens e ver os súditos trabalhando.

## 6. Riscos

| Risco | Mitigação |
|---|---|
| Custo de busca de alvo em áreas grandes | Volume máximo por ordem (config, padrão 32 768 blocos) e busca incremental |
| NPC preso | Detector de progresso, lista de inalcançáveis, retorno à capital após falhas seguidas |
| Duplicar item em crash | Item só existe num lugar por vez (mundo, inventário do NPC ou contêiner) e cada movimento acontece num único tick |
