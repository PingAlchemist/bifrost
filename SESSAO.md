# Bifrost — estado da sessão (09/11/2026)

Jogo Android 2D (Kotlin + Canvas, sem dependências), inspirado na sensação do Ragnarok Online com arte própria.
APK atual: `Bifrost.apk` (raiz). Build: `$env:JAVA_HOME="C:\Users\diego.aragao\AndroidDev\jdk17"; .\gradlew.bat assembleDebug`.
PC de casa (C:\Users\Diego): JDK em `C:\Program Files\Microsoft\jdk-17.0.21.21-hotspot`, SDK em `%LOCALAPPDATA%\Android\Sdk`;
emulador NÃO roda aqui (virtualização desligada na BIOS) — testar no celular. Emulador (outro PC): AVD `caca` (`AndroidDev\sdk\emulator\emulator.exe -avd caca`). ffmpeg instalado via winget (Gyan.FFmpeg).

## Feito e aprovado
- Jogo base: classes (Aprendiz→Guerreiro/Mago/Caçador/Ladino), Base/Job Lv, 6 atributos, 4 skills por classe, auto-ataque,
  progressão offline (bot 60%, máx 12h), cartas, refino, chefes, Vila com NPCs, 7 mapas de caça.
- HUD/visual (fases 1–5), som e músicas sintetizadas (`Audio.kt`), zoom, notch/áreas seguras.
- **Guerreiro em sprites** (folha em `art/source/guerreiro_sheet.png`): 8 direções, andar por camadas
  (lado: `lado_*.png` via `art/tools/SideLayers.java`; frente/costas/3-4: `frente_*.png` via `FrontLayers.java`).
  Vale para Aprendiz e Guerreiro; outras classes usam o boneco desenhado em código (`HeroRig`).
- **Combate no ritmo do vídeo de referência** (`art/source/video/…mp4`, quadros em `art/source/referencia_combate.png`):
  máquina de estados PARADO→ANDANDO→POSICIONANDO→ATACANDO→PAUSA; preparação 0,40s, impacto 0,50s (dano aqui),
  hit stop 60ms, segura pose até 0,80s, fim 1,0s; comprime proporcionalmente se a velocidade de ataque for maior.
  Ataque sempre de lado; herói se posiciona AO LADO do alvo. Pausa pós-abate 0,8s, morte 0,4s, números com "pop".
  Botão Anim → "Combate lento 0,25x" (desliga no botão "0,25x ✕").
- **Floresta substitui o Campo de Aprendizes** (mapa id 1): `art/tools/ForestExtract.java` + `art/floresta/sementes.txt`
  (uma semente por objeto; editar e rodar de novo). Camadas `floresta_chao/topo/base.png` + 27 `floresta_obj_XX.png`,
  objeto `Forest` em `Art.kt` (dados de objetos, colisão, saídas). Esquerda → Vila; direita e topo → "Em breve".

- **Morte do herói em sprite** (`SpriteHero.drawDead`): pisca, cai de costas em 0,4s e quica (antes caía no boneco antigo).
- **Aprendiz com roupa própria**: `art/source/aprendiz_sheet.png` (fundo removido com rembg/isnet-anime a partir de
  `Imagens/aprendiz/…`). `SpriteSkin` em `Art.kt` guarda PNGs e pivôs por visual; ferramentas aceitam perfil:
  `SpriteTool … 420 aprendiz`, `SideLayers <aprendiz_e.png> <res> art/debug aprendiz`, `FrontLayers <res> art/debug aprendiz`.
- **Seleção de personagem**: até 3 slots (`save0..2`), save antigo migrado para o slot 1; "Trocar personagem" no painel Status.

- **Tela inicial** (logo + ponte arco-íris) antes da seleção; botão **Sair** no HUD e voltar do celular levam à seleção.
- **Floresta**: borda de baixo bloqueia (não entra sob as copas); herói navega com A* (`NavGrid` em Art.kt).
- **Vila Aurora refeita** a partir de `art/vila/Vila Fantasia e a Fonte Azul.png` (1536x1024, 1:1): a pintura é o chão
  (`vila_chao.png`), objeto `Town` em Art.kt com colisão por retângulos (casas, postes, bancos, barris, floreiras,
  pilares) + elipse da fonte; muros com portões oeste/leste. Conferência: `art/debug/vila_colisao.png`.
  `art/source/vila_layout.png` é a planta da vila ANTIGA.
- **Fluxo**: tudo direto na `main`; todo deploy = push + `Bifrost.apk`. APK deve ser assinado com a
  `debug.keystore` do outro PC (copiar para `C:\Users\Diego\.android\`) para atualizar no celular sem perder o save.

## Pendente / combinado
- **EXP e drop 15x para testes**: `EXP_RATE = 15f` e `DROP_RATE = 15f` em `Defs.kt` — voltar para `1f` depois.
- Usuário vai mandar print/vídeo da floresta integrada e do combate novo para próximos ajustes.
- Música: as faixas oficiais do RO (C:\Gravity\Ragnarok\BGM) NÃO podem ser usadas/recriadas. Opções oferecidas e
  ainda sem resposta: (1) composições originais no mesmo clima, (2) opção "música personalizada" tocando arquivos do celular.
- Folha nova de partes frente/costas (`Imagens/Folha de sprites do cavaleiro chibi (1).png`) ainda não usada.
- Próximos mapas/cidade/paisagens serão refeitos com arte do usuário, um por vez (mesmo fluxo da floresta).

## Forma de trabalho do usuário
- Prompts em etapas; muitas vezes autoriza seguir sozinho e validar no emulador (ele também usa o emulador e às vezes o fecha).
- Português. Mexer só no pedido; avisar antes de criar arquivos novos de código ou dependências.

## Arquivos principais
`Defs.kt` (dados, Style, EXP_RATE) · `Player.kt` · `Game.kt` (lógica, estados de combate, colisão) · `Offline.kt` ·
`Art.kt` (arte em código, animação, SpriteHero, Forest) · `Renderer.kt` (mundo, HUD, painéis, modo de teste) ·
`GameView.kt` · `MainActivity.kt` · `Audio.kt` · ferramentas em `art/tools/`.
