# Bifrost

RPG 2D para Android, offline, inspirado nos MMORPGs clássicos (Base/Job Lv, 6 atributos, classes, cartas, refino, chefes).
Kotlin puro (Canvas), sem dependências e sem assets: toda a arte é desenhada em código.

## Build
```
$env:JAVA_HOME="C:\Users\diego.aragao\AndroidDev\jdk17"; .\gradlew.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## Arquivos
- `Defs.kt`: classes, skills, monstros, mapas, itens e curvas de EXP (balanceamento fica aqui)
- `Player.kt`: atributos, fórmulas derivadas (ATK/MATK/DEF/ASPD...) e save JSON
- `Game.kt`: combate, IA dos monstros, auto-ataque, spawn/chefes, lojas
- `Offline.kt`: autobot offline (60% de eficiência, máx. 12h, usa poções)
- `Art.kt` / `Renderer.kt`: desenho do mundo (chão em cache por mapa, y-sort, contornos), HUD e janelas
- `Audio.kt`: efeitos e músicas clássicas (domínio público) sintetizados em código, gerados no cache na 1ª vez
- `Style` (topo de `Defs.kt`): paleta, tamanhos e cores do HUD centralizados

Botão **FX** no HUD liga/desliga os efeitos visuais novos (comparar antes/depois). Status: zoom, som, música, vibração.

## Arte em sprites
- `art/source/guerreiro_sheet.png`: folha original do guerreiro.
- `art/tools/SpriteTool.java`: recorta as 5 vistas, alinha pelos pés, espelha para 8 direções, salva em `res/drawable-nodpi` (guerreiro_*.png), recorta as partes em `art/parts` e gera `art/preview_8dir.png`.
  Rodar: `java art/tools/SpriteTool.java art/source/guerreiro_sheet.png app/src/main/res/drawable-nodpi art/parts art/preview_8dir.png 420`.
- `SpriteHero` (Art.kt): 8 direções + animações por transformação do sprite (parado, andar, ataque com hit stop e rastro, dano, conjurar). Botão **Anim** abre o teste.

### Andar por camadas (sem arte nova)
- `art/tools/SideLayers.java`: divide o sprite de lado em corpo, braço+espada, perna da frente e perna de trás (`lado_*.png`), preenchendo as áreas escondidas.
- `art/tools/FrontLayers.java`: nas vistas frente, 3/4 e costas separa as pernas esquerda/direita abaixo da barra da túnica (`frente_*.png`).
- O ciclo de passos acompanha a velocidade de deslocamento; parado, o jogo desenha o sprite original.
