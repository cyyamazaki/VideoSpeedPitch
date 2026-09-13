# Video Speed Pitch

App Android que:

- Permite **escolher a música por busca**, em um de **dois catálogos separados**
  (Karaokê e Japonês), buscando por **cantor/intérprete** ou por **música**.
- Localiza automaticamente o arquivo de vídeo correspondente numa pasta que você
  escolhe uma única vez, usando o **código numérico da música** (segunda coluna
  dos catálogos) como nome do arquivo — ex.: código `18483` → arquivo `18483.mp4`.
- Também permite escolher um **vídeo avulso** manualmente (fora dos catálogos),
  como antes.
- Reproduz o vídeo com **controle de velocidade** (0.25x a 3.00x) e de
  **tom (pitch) do áudio, independente da velocidade** (0.50x a 2.00x).

## Como a busca por catálogo funciona

Os dois catálogos (`catalogo_karaoke.json` e `catalogo_japones.json`, gerados a
partir das planilhas Excel) ficam empacotados dentro do próprio app, na pasta
`app/src/main/assets/`. Eles **nunca são misturados**: você escolhe um catálogo
na tela inicial, e a busca dentro dele (por cantor ou música) considera apenas
aquele catálogo.

Quando você toca em uma música da lista, o app:

1. Olha a pasta de vídeos que você selecionou (uma única vez, na tela inicial).
2. Procura, dentro dela, um arquivo cujo nome (sem extensão) seja exatamente
   igual ao **código** daquela música (ex.: `18483.mp4`, `18483.avi`,
   `18483.mkv` — qualquer extensão de vídeo funciona, o que importa é o nome).
3. Se encontrar, abre o player já naquele vídeo. Se não encontrar, avisa que o
   arquivo daquele código não está na pasta.

**Importante:** os vídeos não são baixados nem vêm com o app — você precisa
apontar para uma pasta local (no celular/tablet) que já tenha esses arquivos,
nomeados pelos códigos correspondentes.

## Como funciona a velocidade x tom

O app usa **Media3 ExoPlayer**, que expõe `PlaybackParameters(speed, pitch)` —
um algoritmo de *time-stretch* (baseado no Sonic) que muda velocidade e tom
**de forma independente**. Dá pra acelerar o vídeo sem a voz ficar "esquilo",
ou só mudar o tom sem alterar a velocidade.

## Como abrir o projeto

1. Abra o **Android Studio** (Koala/2024.1 ou mais recente).
2. `File > Open...` e selecione a pasta `VideoSpeedPitch`.
3. Deixe o Gradle sincronizar (ele baixa o wrapper automaticamente na primeira
   sincronização).
4. Rode em um emulador ou dispositivo com **Android 7.0 (API 24)** ou superior.

## Estrutura do projeto

```
VideoSpeedPitch/
├── build.gradle.kts, settings.gradle.kts, gradle.properties
├── gradle/wrapper/gradle-wrapper.properties
├── README.md
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml
        ├── assets/
        │   ├── catalogo_karaoke.json   # ~12.900 músicas
        │   └── catalogo_japones.json   # ~1.550 músicas
        ├── java/com/example/videospeedpitch/
        │   ├── MainActivity.kt         # tela inicial (hub): pasta + catálogos
        │   ├── CatalogActivity.kt      # busca + lista de um catálogo
        │   ├── PlayerActivity.kt       # player (velocidade/tom), tela antiga
        │   ├── CatalogRepository.kt    # lê o JSON e indexa a pasta de vídeos
        │   ├── SongAdapter.kt          # RecyclerView da lista de músicas
        │   ├── Song.kt                 # modelo de dados de uma música
        │   └── Prefs.kt                # chaves de SharedPreferences
        └── res/
            ├── layout/
            │   ├── activity_main.xml      # hub
            │   ├── activity_catalog.xml   # busca/lista
            │   ├── item_song.xml          # item da lista
            │   └── activity_player.xml    # player
            └── values/{strings.xml, themes.xml}
```

## Uso

1. Na tela inicial, toque em **"Selecionar pasta de vídeos"** e escolha a pasta
   onde estão os arquivos nomeados pelos códigos das músicas (só precisa fazer
   isso uma vez; a permissão fica salva).
2. Toque em **"Catálogo Karaokê"** ou **"Catálogo Japonês"**.
3. Digite pelo menos 2 letras do nome do cantor/intérprete ou da música.
4. Toque no resultado desejado — o vídeo correspondente abre automaticamente.
5. Ajuste os sliders de **Velocidade** e **Tom** como quiser, e use
   **"Redefinir"** para voltar a 1.00x.

Se preferir tocar um vídeo que não faz parte de nenhum catálogo, use o botão
**"Escolher vídeo avulso (arquivo)"** na tela inicial.

## Possíveis melhorias futuras

- Cache do índice de arquivos da pasta em disco (hoje é reconstruído a cada
  vez que a tela do catálogo é aberta).
- Indicar visualmente, na lista de busca, quais músicas já têm vídeo
  disponível na pasta selecionada (hoje isso só é checado ao tocar no item).
- Salvar a última velocidade/tom usados.
- Suporte a rotação de tela salvando o estado do player.
