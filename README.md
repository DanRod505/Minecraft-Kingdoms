# Minecraft: Kingdoms

Mod Forge para Minecraft Java 26.2 (Forge 65.1.0, MDK `forge-26.2-65.1.0-mdk`), mod id `kingdoms`.

O jogador governa um reino: funda a capital, organiza súditos, economia, governo, diplomacia e guerra.
O desenvolvimento segue as fases do GDD (seção 16). Documentos de cada fase ficam em [`docs/`](docs/).

Estado: Fase 0 (fundação técnica) concluída, ver [`docs/fase-0-relatorio.md`](docs/fase-0-relatorio.md). Convenções e comandos em [`docs/convencoes.md`](docs/convencoes.md).

## Compilar

Precisa só de um JDK 21+ para rodar o Gradle; o Java 25 exigido pelo Minecraft 26.2 é baixado automaticamente pelo toolchain.

```
./gradlew build              # jar em build/libs
./gradlew runClient          # cliente de desenvolvimento
./gradlew runGameTestServer  # testes de jogo
```
