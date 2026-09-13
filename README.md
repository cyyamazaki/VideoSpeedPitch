# Video Speed Pitch

App Android simples que:

- Permite **escolher um arquivo de vídeo** do dispositivo (usando o seletor de arquivos do sistema).
- Reproduz o vídeo com **controle de velocidade** (0.25x a 3.00x).
- Permite variar o **tom (pitch) do áudio de forma independente** da velocidade (0.50x a 2.00x).

## Como funciona a mágica velocidade x tom

O app usa **Media3 ExoPlayer** (a biblioteca oficial de reprodução de mídia do Android/Google).
O ExoPlayer expõe `PlaybackParameters(speed, pitch)`, que internamente usa um algoritmo de
*time-stretch* (baseado no Sonic) capaz de:

- Mudar a **velocidade** sem necessariamente mudar o tom, e
- Mudar o **tom** independentemente da velocidade.

Ou seja, dá para deixar o vídeo mais rápido sem a voz ficar aguda ("efeito esquilo"), ou
deixar a voz mais grave/aguda sem alterar a velocidade — e qualquer combinação entre os dois.

## Como abrir o projeto

1. Abra o **Android Studio** (versão Koala/2024.1 ou mais recente recomendado).
2. `File > Open...` e selecione a pasta `VideoSpeedPitch` (a pasta raiz deste projeto).
3. Deixe o Android Studio sincronizar o Gradle (ele vai baixar o wrapper do Gradle automaticamente
   na primeira sincronização, já que o `gradle-wrapper.jar` binário não vem incluído neste pacote).
   Se ele perguntar, aceite a opção de criar/atualizar o Gradle Wrapper.
4. Rode o app em um emulador ou dispositivo físico com **Android 7.0 (API 24)** ou superior.

## Estrutura do projeto

```
VideoSpeedPitch/
├── build.gradle.kts                  # config raiz (plugins Android/Kotlin)
├── settings.gradle.kts               # módulos do projeto
├── gradle.properties
├── gradle/wrapper/gradle-wrapper.properties
└── app/
    ├── build.gradle.kts              # dependências (Media3 ExoPlayer, AppCompat, Material)
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/example/videospeedpitch/MainActivity.kt
        └── res/
            ├── layout/activity_main.xml
            └── values/{strings.xml, themes.xml}
```

## Uso

1. Toque em **"Escolher vídeo"** e selecione um arquivo de vídeo do dispositivo.
2. O vídeo começa a tocar automaticamente com controles padrão do player (play/pause, seek).
3. Use o slider **"Velocidade"** para acelerar ou desacelerar a reprodução.
4. Use o slider **"Tom"** para deixar o áudio mais grave ou mais agudo, independente da velocidade.
5. Toque em **"Redefinir"** para voltar velocidade e tom para 1.00x.

## Possíveis melhorias futuras

- Salvar a última velocidade/tom usados (SharedPreferences).
- Adicionar suporte a lista de reprodução (vários vídeos).
- Tratar foco de áudio (pausar quando outro app tocar som) e o evento de "áudio ficou ruidoso"
  (ex: quando o fone de ouvido é desconectado).
- Trocar os `SeekBar` por `Slider` do Material Design 3 para uma UI mais moderna.
- Adicionar suporte a rotação de tela salvando o estado do player (`onSaveInstanceState`).
