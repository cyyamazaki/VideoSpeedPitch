# YamazakiOke

App Android de karaokê (nome do projeto/repositório: VideoSpeedPitch). Ao
abrir, mostra uma tela de splash com o nome do app, a versão e o mês/ano de
lançamento sobre uma ilustração, antes de ir para a tela inicial de verdade.
O ícone do app (launcher) é um microfone branco sobre o azul do tema.

O app:

- Mantém **dois catálogos separados** (Karaokê e Japonês), cada um listado por
  completo em memória e **ordenável por cantor ou por música**, com busca (por
  cantor/intérprete, música ou número do código) que só atualiza a lista ao
  confirmar (Enter/"Buscar"), e um indicador visual (✓/✗) de quais músicas já
  têm vídeo disponível na pasta selecionada.
- Localiza automaticamente o arquivo de vídeo correspondente numa pasta que
  você escolhe uma única vez, usando o **código numérico da música** como nome
  do arquivo — ex.: código `18483` → arquivo `18483.mp4`. O índice dessa pasta
  fica em **cache em disco**, e um botão na tela inicial força atualizá-lo.
- Tem uma **playlist (fila FIFO)**: dá pra adicionar músicas pelo número — na
  tela inicial ou durante o vídeo — que tocam em sequência automaticamente,
  com aviso 5 segundos antes de cada próxima.
- Reproduz o vídeo com **controle de velocidade** (90%/95%/100%) e de
  **tom (pitch) do áudio, independente da velocidade** (90% a 110%, em passos
  de 5%), por botões de seleção — sem os controles nativos do ExoPlayer, para
  não escurecer o vídeo nem atrapalhar a leitura de legendas embutidas.
- Na tela inicial, toca continuamente um **vídeo aleatório embutido** da pasta
  selecionada enquanto você não abre um catálogo ou a playlist — e retoma
  exatamente de onde parou (posição e se estava tocando/pausado) ao voltar de
  outra tela ou de sair do app sem fechá-lo, em vez de sortear um vídeo novo.
  Um botão discreto (➕) adiciona a música desse vídeo à playlist sem
  interromper o que já está tocando.
- Um botão discreto (🔎), tanto no player quanto no vídeo aleatório da tela
  inicial, **busca no YouTube a versão cantada, com letra ("lyrics")** da
  música atual e abre o primeiro resultado encontrado, ou avisa por toast se
  não achar — útil já que o vídeo local é o karaokê, sem voz. Na busca de um
  catálogo sem resultados, um botão parecido busca um **karaokê** dessa
  música no YouTube em vez disso. Essa é a única funcionalidade do app que
  precisa de internet (as demais funcionam totalmente offline).

## Como a busca por catálogo funciona

Os dois catálogos (`catalogo_karaoke.json` e `catalogo_japones.json`, gerados a
partir das planilhas Excel) ficam empacotados dentro do próprio app, na pasta
`app/src/main/assets/`. Eles **nunca são misturados**: você escolhe um catálogo
na tela inicial, e tudo dentro dele (listagem, ordenação e busca) considera
apenas aquele catálogo.

Ao abrir um catálogo, a lista inteira já aparece (ordenada por cantor ou
música, à sua escolha), sem precisar buscar nada. Cada item mostra cantor,
música, código, o início da letra (quando disponível) e se o vídeo já está
disponível na pasta selecionada. Digitar algo no campo de busca e confirmar
(Enter ou "Buscar") filtra essa lista pelo texto — a lista só é recalculada
nessa confirmação, não a cada tecla digitada, para não travar em catálogos
grandes. Para navegar rápido em listas com milhares de músicas, arraste o
dedo na borda direita da lista: ela pula proporcionalmente para aquele ponto,
mostrando uma bolha com a letra inicial (do cantor ou da música, conforme a
ordenação) da posição atual.

Quando você toca em uma música da lista, o app:

1. Olha a pasta de vídeos que você selecionou (uma única vez, na tela inicial).
2. Procura, no índice em cache dessa pasta, um arquivo cujo nome (sem extensão)
   seja exatamente igual ao **código** daquela música (ex.: `18483.mp4`,
   `18483.avi`, `18483.mkv` — qualquer extensão de vídeo funciona, o que
   importa é o nome).
3. Se encontrar, mostra um toast confirmando e abre o player já naquele vídeo.
   Se não encontrar, avisa (com os dados da música) que o arquivo daquele
   código não está na pasta e já busca um karaokê dela no YouTube como
   alternativa.

**Importante:** os vídeos não são baixados nem vêm com o app — você precisa
apontar para uma pasta local (no celular/tablet) que já tenha esses arquivos,
nomeados pelos códigos correspondentes.

Se a busca não encontrar nenhuma música no catálogo, aparece um botão para
buscar um karaokê dela (pelo texto digitado, cantor e/ou nome da música) no
YouTube como alternativa.

## Playlist (fila FIFO)

Além de tocar uma música na hora pelo catálogo, dá pra **enfileirar músicas
pelo número** — na tela inicial (campo sempre em foco, pronto para digitar) ou
no próprio player, sem sair do vídeo atual. Regras:

- A primeira música adicionada com a fila vazia já **toca na hora**, entrando
  em modo playlist a partir daí.
- Com o modo playlist ativo, ao terminar cada vídeo o player avança sozinho
  para a próxima música da fila, avisando por toast 5 segundos antes de cada
  troca.
- A tela **"Ver/tocar playlist"** lista a fila, permite remover itens
  individualmente, tocar a partir dela ou limpá-la.
- Dentro do player, botões discretos no alto do vídeo permitem avançar direto
  para a próxima música da fila ou finalizar a playlist (esvazia a fila e
  desliga o avanço automático) sem precisar sair do vídeo.
- Outro botão discreto (📖) ativa "escolher a próxima no catálogo": quando o
  vídeo atual terminar, em vez do comportamento normal, o último catálogo
  usado é reaberto para você escolher a próxima música — ela entra na fila e
  já começa a tocar em seguida. Se você tocar em "voltar à tela inicial" com
  essa opção ativada, ela também é respeitada (abre o catálogo em vez de
  simplesmente sair).
- Sempre que o vídeo termina (ou a playlist se esvazia), o app volta sozinho
  para a tela inicial — também dá pra voltar manualmente a qualquer momento
  pelo botão de "casa" no mesmo painel.

## Como funciona a velocidade x tom

O app usa **Media3 ExoPlayer**, que expõe `PlaybackParameters(speed, pitch)` —
um algoritmo de *time-stretch* (baseado no Sonic) que muda velocidade e tom
**de forma independente**. Dá pra acelerar o vídeo sem a voz ficar "esquilo",
ou só mudar o tom sem alterar a velocidade. Na tela do player, velocidade e
tom são escolhidos por botões (90%/95%/100% para velocidade; 90% a 110%, de
5 em 5%, para o tom), sem os controles nativos do ExoPlayer — eles
escureceriam o vídeo com um scrim atrás dos botões, o que atrapalharia a
leitura de legendas embutidas nos vídeos de karaokê. Em vez disso, a própria
tela mostra/oculta seu próprio painel de controles ao tocar a tela, mover o
mouse ou digitar um número, com ocultação automática após alguns segundos.

Um overlay discreto no alto do vídeo sempre mostra os dados da música atual
(cantor, música, código e início da letra) e, mais discreto ainda, os da
próxima música da playlist, além de botões de pausar/retomar, avançar a
playlist, finalizar a playlist e voltar à tela inicial.

O estado do player (vídeo, posição, velocidade, tom, modo playlist) é
preservado tanto ao girar a tela quanto ao sair do app sem fechá-lo (por
exemplo, pelo botão Início) e voltar depois — o vídeo retoma exatamente de
onde parou.

## Como abrir o projeto

1. Abra o **Android Studio** (Koala/2024.1 ou mais recente).
2. `File > Open...` e selecione a pasta `VideoSpeedPitch`.
3. Deixe o Gradle sincronizar (ele baixa o wrapper automaticamente na primeira
   sincronização).
4. Rode em um emulador ou dispositivo com **Android 7.0 (API 24)** ou superior.

## Gerando o APK de release (assinado)

O build de debug (`./gradlew assembleDebug`) já é instalável normalmente, mas
para um APK de release (`./gradlew assembleRelease`) — sem o sufixo "debug"
e assinado para distribuição/uso definitivo — é preciso uma keystore local:

1. Copie `keystore.properties.example` para `keystore.properties` (raiz do
   projeto) e gere uma keystore, por exemplo:
   ```
   keytool -genkeypair -v -keystore keystore/yamazakioke-release.jks \
     -alias yamazakioke -keyalg RSA -keysize 2048 -validity 10000
   ```
   **Importante:** com o formato PKCS12 (padrão do `keytool` atual), a senha
   da chave (`-keypass`) precisa ser igual à senha da keystore
   (`-storepass`) — não dá para usar senhas diferentes.
2. Preencha `keystore.properties` com o caminho da keystore e as senhas
   reais.
3. Rode `./gradlew assembleRelease`. O APK sai em
   `app/build/outputs/apk/release/app-release.apk`.

`keystore.properties` e a pasta `keystore/` **nunca** são versionados (estão
no `.gitignore`) — guarde-os em um lugar seguro fora do git, porque **sem a
mesma keystore não dá para instalar uma atualização por cima de uma versão
já instalada** (seria preciso desinstalar primeiro). Sem esse arquivo, o
build de release simplesmente sai sem assinatura (o de debug não é afetado).

## Estrutura do projeto

```
VideoSpeedPitch/
├── build.gradle.kts, settings.gradle.kts, gradle.properties
├── gradle/wrapper/gradle-wrapper.properties
├── keystore.properties.example   # modelo; a keystore/senhas reais não são versionadas
├── README.md
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml
        ├── assets/
        │   ├── catalogo_karaoke.json   # ~12.900 músicas
        │   └── catalogo_japones.json   # ~1.550 músicas
        ├── java/com/example/videospeedpitch/
        │   ├── SplashActivity.kt       # tela de abertura (launcher): nome, versão, mês/ano
        │   ├── MainActivity.kt         # tela inicial (hub): número, catálogos, pasta, vídeo aleatório
        │   ├── CatalogActivity.kt      # lista completa + busca + ordenação de um catálogo
        │   ├── PlaylistActivity.kt     # ver/tocar/remover a fila da playlist
        │   ├── PlaylistManager.kt      # fila FIFO em memória, compartilhada pelo app
        │   ├── PlaylistPlayer.kt       # toca a próxima música válida da fila (compartilhado)
        │   ├── PlayerActivity.kt       # player: velocidade/tom, overlays, playlist
        │   ├── AboutActivity.kt        # tela "Sobre" com o histórico de implementações
        │   ├── CatalogRepository.kt    # lê os JSONs e indexa (com cache em disco) a pasta de vídeos
        │   ├── SongAdapter.kt          # RecyclerView da lista de um catálogo
        │   ├── PlaylistAdapter.kt      # RecyclerView da fila da playlist
        │   ├── YouTubeSearchHelper.kt  # busca o vídeo de uma música no YouTube (sem API key)
        │   ├── Song.kt                 # modelo de dados de uma música + formatação para exibição
        │   └── Prefs.kt                # chaves de SharedPreferences
        └── res/
            ├── layout/
            │   ├── activity_splash.xml     # tela de abertura
            │   ├── activity_main.xml       # hub
            │   ├── activity_catalog.xml    # lista/busca/ordenação
            │   ├── item_song.xml           # item da lista de catálogo
            │   ├── activity_playlist.xml   # tela da fila
            │   ├── item_playlist.xml       # item da fila
            │   ├── activity_player.xml     # player
            │   └── activity_about.xml      # tela "Sobre"
            ├── drawable-nodpi/splash_singer.jpg   # ilustração de fundo do splash (ver Créditos)
            ├── mipmap-anydpi-v26/ic_launcher.xml  # ícone adaptativo (API 26+): fundo + microfone
            ├── mipmap-{m,h,x,xx,xxx}hdpi/         # ic_launcher.png (legado) e ic_launcher_foreground.png
            └── values/{strings.xml, colors.xml, themes.xml}
```

## Uso

1. Na tela inicial, digite o **número (código)** de uma música e confirme —
   se a playlist estiver vazia, ela já começa a tocar na hora; senão, entra na
   fila. O campo fica sempre em foco, pronto para o próximo número.
2. Ou toque em **"Catálogo Karaokê"** ou **"Catálogo Japonês"**, role a lista
   completa (ordenável por cantor ou música) ou busque por cantor,
   música ou número, e toque no resultado desejado.
3. Na primeira vez, toque em **"Selecionar pasta de vídeos"** (seção "Pasta
   onde estão os vídeos") e escolha a pasta com os arquivos nomeados pelos
   códigos das músicas — só precisa fazer isso uma vez, a permissão fica
   salva. Use **"Atualizar catálogos"** depois de adicionar/remover vídeos
   nessa pasta sem trocar de pasta.
4. No player, toque na tela (ou mova o mouse, ou digite um número) para
   revelar os botões de **Velocidade** e **Tom**, e **"Redefinir"** para
   voltar a 100%.

## Possíveis melhorias futuras

- Salvar a última velocidade/tom usados como padrão para o próximo vídeo.
- Reordenar itens dentro da fila da playlist (hoje só dá para remover).
- Editar/atualizar os catálogos (JSON) sem precisar gerar um novo APK.

## Créditos

Ilustração de fundo da tela de abertura
(`app/src/main/res/drawable-nodpi/splash_singer.jpg`) fornecida pelo autor
do projeto.

Ícone do app (microfone) desenhado proceduralmente para o projeto — sem
assets externos.
