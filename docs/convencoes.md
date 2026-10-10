# Convenções do projeto

- Pacote raiz `dev.kingdoms`, mod id `kingdoms` (fixo). Módulos do GDD §14.2 viram pacotes (`core`, `world`, `persistence`, `network`, ...) e só são criados quando a fase correspondente começa.
- Código e identificadores em inglês. Texto para o jogador sempre via chave de tradução, com `en_us` e `pt_br`.
- Lógica de domínio sem dependência de classes do Minecraft sempre que possível, para rodar em JUnit (`src/test`).
- Servidor é a autoridade (regra 20.8). O cliente só exibe e pede.
- Todo dado salvo passa por `VersionedCodec` e carrega `schema_version`. Mudou o formato: sobe `SchemaVersion.CURRENT` e adiciona um passo em `Migrations` com teste.
- Toda mudança de mensagem de rede sobe `KingdomsNetwork.PROTOCOL_VERSION`.
- Testes de jogo: função registrada em `KingdomsGameTests` + `data/kingdoms/test_instance/<nome>.json`.
- Versões travadas: Minecraft 26.2, Forge 65.1.0, ForgeGradle 7.0.40, Gradle 9.5.0 (wrapper), Java 25. Mudança só com aprovação do Dalton.
- Cada fase fecha com `docs/fase-N-relatorio.md`: checklist de aceite, testes, baseline de desempenho e pendências.

## Comandos

| Comando | O que faz |
|---|---|
| `./gradlew build` | Compila, roda testes unitários e gera `build/libs/kingdoms-<versão>.jar` |
| `./gradlew runGameTestServer` | Sobe o servidor de testes de jogo; sai com erro se algum teste obrigatório falhar |
| `scripts/server-smoke.sh` | Sobe o servidor dedicado (`runServer --nogui`), espera "Done", envia `stop` e falha se o mod logar erro |
| `./gradlew runClient` | Abre o cliente de desenvolvimento |
| `./gradlew curseforgeProfile` | Gera o zip de perfil para importar no CurseForge |
