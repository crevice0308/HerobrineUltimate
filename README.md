# Herobrine Ultimate

Minecraft Java Edition **1.20.1** 用の Forge / Fabric MOD。**0.1.0-alpha.2 / Java 17 / MIT**。変身と 12 カテゴリの能力・設定をサイバーパネルから操作できます。

**標準設定ではチート OFF・OP 権限なしで使用できます。** シングルプレイ所有者に限らず、MOD 導入済みサーバーの一般プレイヤーも対象です。OP やコマンドを付与せず、MOD 自身がサーバー側で処理します。

Forge / Fabric の導入用 JAR を作成しています。実施したビルド・ゲーム内テストと未検証範囲は [TEST_RESULTS](docs/TEST_RESULTS.md)、要求との対応は [FEATURE_COVERAGE](docs/FEATURE_COVERAGE.md) にまとめています。

## 導入

1. Minecraft Java Edition 1.20.1、64-bit Java 17、使用するローダーを用意します。
2. 対応する **一つの JAR** を `mods/` に入れます。Forge 版は `herobrineultimate-forge-0.1.0-alpha.2.jar`、Fabric 版は `herobrineultimate-fabric-0.1.0-alpha.2.jar` です。`-sources.jar` は開発用です。
3. Fabric 版では **Fabric API** も導入します。基準構成は Forge **47.4.10**、または Fabric Loader **0.16.14** + Fabric API **0.92.2+1.20.1** です。
4. マルチプレイではサーバーと参加クライアントの両方へ、同じローダー用・同じバージョンの MOD を導入します。Fabric サーバーにも Fabric API が必要です。
5. チート OFF のワールドでも、そのまま `Insert` でパネルを開きます。初期状態の個別能力は OFF、一括スイッチは ON です。使いたい能力を ON にしてください。

バニラサーバーや MOD 未導入サーバーは対象外です。Forge と Fabric の JAR を同じインスタンスへ同時導入しないでください。

## 操作

| 初期キー | 操作 |
|---|---|
| Insert | パネルを開く / 閉じる |
| F6 | 全能力を一括 ON / OFF。個別の選択・倍率を保持 |
| F7 | 視線先へ演出用の落雷 |
| F8 | 現在地を帰還地点として保存 |
| F9 | 保存地点へ帰還 |
| F10 | 視線先へ瞬間移動 |
| Esc | パネルを閉じる |

キーは「設定 → 操作設定 → キー割り当て」の Herobrine Ultimate カテゴリで変更できます。パネル右上の **K ボタン**からもキー割り当て画面を開けます。ショートカット表示は再割り当てしたキーに追従します。

右上の **G ボタン**は、この端末のパネル発光を切り替えます。変身エフェクトとは独立して自動保存します。G / K は画面上のボタン名です。

Tab / Shift+Tab で項目移動、Enter / Space でボタン操作、左右矢印でスライダー操作、PageUp / PageDown で本文スクロール、Alt+↑ / ↓ でカテゴリ変更ができます。左ナビと本文は別々にスクロールします。画面はワールドの進行を止めません。

## 能力

| カテゴリ | 実装仕様 |
|---|---|
| Herobrine | 専用スキンと白く発光する目。1 人称の腕と 3 人称に適用し、頭上・チャット・TAB の表示名を `Herobrine` に変更。OFF で元へ復帰 |
| God Mode | 通常ダメージを無効化。奈落と `/kill` はバニラどおり |
| Infinite Hunger | 満腹度 20、隠し満腹度 20、消耗度 0 を毎 tick 維持。OFF 後はその時点から通常消費 |
| Flight | 通常の自由飛行操作と落下ダメージ防止。OFF 時にこの MOD が追加した飛行状態を復元 |
| Lightning | 視線先 64 ブロックへ落雷、最短 5 tick 間隔。演出用と実体のある落雷を選択 |
| Combat | 通常攻撃の威力 1–1000 倍。GUI の視線攻撃は 24 ブロック、遮蔽物・味方判定あり。通常 / 即死相当の攻撃を選択 |
| Teleport | 視線先 64 ブロックへの安全な移動、1 地点の保存・帰還。保存先の次元へも帰還可能 |
| Mob Control | 狼・猫・オウム・アイアンゴーレムの召喚、追従、集合、攻撃支援、解散。プレイヤーごと最大 16 体 |
| World Control | 現在の次元で昼 / 夜、晴れ / 雨 / 雷雨。確認付きの 3×3×3 地形変更 |
| Invisibility | バニラの透明フラグを設定。当たり判定を維持し、装備・手持ち品と Mob AI はバニラの透明化ルールに従う |
| Motion Boost | 移動 1–10 倍、採掘 1–100 倍、攻撃速度 1–10 倍。OFF 時に独自の属性補正を解除 |
| Herobrine Effects | 変身時の演出用落雷、サーバー / クライアントのパーティクル、約 350 ms の画面フラッシュを ON / OFF |

瞬間移動は床、衝突、液体、危険ブロック、ワールド境界、読み込み済みチャンクを確認します。帰還先のチャンクは事前に読み込み済みである必要があります。

Mob は同じ次元の読み込み済み個体が、64 ブロック以内で追従します。狼・猫・オウムは所有者へテイムされ、ゴーレムはプレイヤー作成扱いです。攻撃支援は種類固有の AI に従います。「解散」は読み込み済みの登録個体を削除し、未読み込み個体は登録に残します。

地形変更は視線先 6 ブロックを中心に最大 27 ブロックを通常のプレイヤー破壊経路で処理し、ブロックエンティティと破壊不能ブロックを除外します。即死相当攻撃、実体のある落雷、地形変更には GUI の確認を挟みます。確認は OP 権限の要求ではありません。地形変更に元に戻す機能はないため、使用前にワールドをバックアップしてください。

即死相当攻撃も通常のダメージ処理を使うため、耐性や他 MOD によって防がれる場合があります。プレイヤーへの特殊攻撃は下記の設定とバニラの PvP / チーム判定に従います。

## 設定と保存

初回起動で `config/herobrine-ultimate-server.properties` を作成します。編集後はワールド / サーバーを再起動してください。

```properties
require-permission=false
allowed-uuids=
destructive-lightning=false
allow-player-combat=false
allow-terrain=true
```

| 項目 | 意味 |
|---|---|
| `require-permission` | `false` が標準。チート OFF、OP なし、UUID 登録なしで使用可能。管理者が `true` にした場合だけ、権限レベル 2 以上の OP・シングルプレイ所有者・UUID 許可リストに限定 |
| `allowed-uuids` | 制限モードで追加許可する UUID。カンマ区切り |
| `destructive-lightning` | 実体のある落雷を使う場合 `true`。GUI の確認も必要。OP である必要はない |
| `allow-player-combat` | 特殊攻撃のプレイヤー対象を使う場合 `true`。バニラの PvP / チーム判定も適用。OP である必要はない |
| `allow-terrain` | 地形変更を有効にするか。標準は `true` |

以前の設定ファイルに `require-permission` がない場合も `false` として読み込みます。死亡中・スペクテイター中の実行アクションは受け付けません。

個別能力、一括スイッチ、4 種の倍率、帰還地点、登録 Mob は **ワールドごと・UUID ごと**に保存します。保存先はワールドの `data/herobrine_ultimate.dat`。ゲーム標準の保存に連動し、別ワールドとは共有しません。能力の設定は死亡・再ログイン・次元移動後も保持します。

パネル発光は `config/herobrineultimate-client.properties` の `panelGlow`、キー割り当ては Minecraft の設定へ保存します。

## 表示とアセット

変身状態はサーバーからクライアントへ同期します。Minecraft アカウント名、UUID、認証情報、GameProfile、署名付きチャット本文は変更しません。表示名を装飾し、スキン・目をクライアントで描画します。チャット / スキン / シェーダー MOD による上書きとの併用確認は別途必要です。

テクスチャの出典は [ASSETS](docs/ASSETS.md)、添付デザインとの差分は [GUI_DIFFERENCES](docs/GUI_DIFFERENCES.md) を参照してください。

## ビルドとテスト

64-bit **JDK 17** を使用します。Gradle Wrapper **8.8**、ForgeGradle **6.0.54**、Fabric Loom **1.6.12** を固定しています。

```sh
./gradlew -Ploader=forge :forge:build
./gradlew -Ploader=fabric :fabric:build
```

Windows は `gradlew.bat` を使用します。初回はネットワークから依存物を取得します。導入用 JAR の出力先は `forge/build/libs/` と `fabric/build/libs/` です。

この環境の Fabric JAR は、実物の Mojang / Fabric マッピング、Mixin Annotation Processor、TinyRemapper を使う `tools/fabric_manual_build.py` で作成しています。Mixin refmap を生成し、intermediary 名へ変換する配布用ビルドです。

```sh
python3 tools/fabric_manual_build.py --cache /path/to/.gradle/caches --toolchain /path/to/toolchain
```

この代替手順は取得済みの実物の Minecraft・マッピング・依存 JAR を使います。必要な入力は [COMPATIBILITY](docs/COMPATIBILITY.md) に記載しています。通常の開発環境では Gradle / Loom を使用してください。

```sh
bash tests/run-core-tests.sh
python3 tests/validate-project.py
./gradlew -Ploader=forge -Pgame_tests :forge:runGameTestServer
./gradlew -Ploader=forge -Pclient_tests :forge:runClient
```

最後の 2 行は実際の Minecraft サーバー / クライアントを起動するテストです。core / NBT テストとは確認範囲が異なります。NBT テストは実物の Mojang 名 Minecraft クラスパスを `HEROBRINE_TEST_CLASSPATH` に設定し、`python3 tests/run-world-data-tests.py` で実行します。

Fabric の共有 GameTest は、代替ビルド後に `python3 tools/fabric_run_gametests.py` で実行します。これは上記キャッシュ構成と追加の upstream `fabric-gametest-api-v1` **1.2.13+1802ada577** を使います。テスト専用 MOD は配布 JAR と分離しています。

Fabric の実クライアント GUI 試験は `python3 tools/fabric_run_client_smoke.py --xvfb-root /path/to/xvfb-root` です。必要な表示環境・出力先と各試験の確認範囲は [TEST_PLAN](docs/TEST_PLAN.md) を参照してください。

## 構成と検証資料

- `common/`：共通の能力、保存、GUI、変身描画
- `forge/`、`fabric/`：ローダーごとの通信・イベント・ビルド
- [FEATURE_COVERAGE](docs/FEATURE_COVERAGE.md)：要求と実装の対応
- [TEST_RESULTS](docs/TEST_RESULTS.md)：実施結果、ログ、実機確認の範囲
- [TEST_PLAN](docs/TEST_PLAN.md)：継続確認の手順
- [COMPATIBILITY](docs/COMPATIBILITY.md)：依存関係と対応範囲
- [SECURITY_REVIEW](docs/SECURITY_REVIEW.md)：静的確認・依存照合
- [NEXT_STEPS](docs/NEXT_STEPS.md)：残る検証項目

互換性は検証した組合せごとに記録します。ローダーの全バージョンや他 MOD との全組合せを、メタデータの範囲だけで動作確認済みとは扱いません。
