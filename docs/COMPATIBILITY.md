# 互換性と依存関係 — 0.1.0-alpha.2

記録日：2026-10-17。メタデータが許可する版と、実際に起動・操作を確認した版を分けます。実行ログ・成功 / 失敗・未検証は `TEST_RESULTS.md` を参照してください。

## 基準構成

| 項目 | 固定値 | 設定元 |
|---|---|---|
| Minecraft Java Edition | 1.20.1 | `gradle.properties`、両 MOD メタデータ |
| Java | JDK / JVM 17、64 bit | ローダー別 `build.gradle` |
| Gradle Wrapper | 8.8 | `gradle/wrapper/gradle-wrapper.properties` |
| ForgeGradle | 6.0.54 | `forge/build.gradle` |
| Forge | 1.20.1-47.4.10 | `gradle.properties` |
| Fabric Loom | 1.6.12 | `settings.gradle` |
| Fabric Loader | 0.16.14 | `gradle.properties` |
| Fabric API | 0.92.2+1.20.1 | `gradle.properties` |
| マッピング | Mojang official、Minecraft 1.20.1 | ローダー別 `build.gradle` |
| MOD ライセンス | MIT | `LICENSE`、両 MOD メタデータ |

Fabric API は Fabric クライアント・サーバー双方の実行時依存です。Forge 版は Fabric API を使用しません。マルチプレイでは、サーバー・クライアントを同じローダー・MOD バージョンでそろえます。

## 宣言範囲と追加確認候補

| 系統 | メタデータの宣言 | 基準構成 | 追加確認候補 |
|---|---|---|---|
| Forge | `>=47.4.10, <48` | 47.4.10 | 47.4.28 |
| Fabric Loader | `>=0.15.11` | 0.16.14 | 0.15.11、0.20.0 |
| Fabric API / MC 1.20.1 | `>=0.92.2+1.20.1` | 0.92.2+1.20.1 | 0.92.12+1.20.1 |

上記候補は全組合せの動作実績ではありません。Forge 47.0.0–47.4.9 は現行 JAR の宣言対象外です。47.x 全体に対応済みとは表記しません。Fabric Loader でも宣言範囲全体を試験済みとは扱いません。

各組合せで確認する内容は、配布 JAR の起動・Mixin 適用・専用サーバー・統合サーバー・GUI 同期・主要能力・保存 / 再接続です。Minecraft 1.20 / 1.20.2 以降、NeoForge、Quilt、互換サーバー、Java 21 は今回の基準対象に含めません。

他 MOD のスキン、アニメーション、透明化、飛行、チャット、地形保護、シェーダーとの互換性は、導入した版ごとに追加確認します。

## Fabric 配布 JAR の代替ビルド

通常の開発環境では、README の Gradle / Loom 手順を使用します。この実行環境では `tools/fabric_manual_build.py` により、実物の Mojang / Fabric マッピングと upstream の Mixin Annotation Processor / TinyRemapper を使用して配布 JAR を作成します。

```sh
python3 tools/fabric_manual_build.py --cache /path/to/.gradle/caches --toolchain /path/to/toolchain
```

既定値は `--cache ~/.gradle/caches`、`--toolchain ../toolchain`、作業出力は `fabric/build/manual` です。`--output` で作業出力を変更できます。最終 JAR は `fabric/build/libs/herobrineultimate-fabric-0.1.0-alpha.2.jar` へ出力します。

必要な取得済み入力：

- Gradle キャッシュ内の Minecraft 1.20.1 `client.jar` と公式 `client_mappings.txt` / `server_mappings.txt`。
- Gradle キャッシュ内の TinyRemapper 0.10.3、mapping-io 0.5.1、ASM 9.6。
- `toolchain/deps/` の実物のゲーム依存 JAR。
- `toolchain/fabric-deps/` の `intermediary.jar`、`fabric-loader.jar`、元の `fabric-api.jar`。基準版は Loader 0.16.14 / Fabric API 0.92.2+1.20.1。Fabric API の実物の内包モジュールからビルド用クラスパス JAR を自動生成します。

公式マッピングと intermediary を全体で結合し、実際のゲーム / API に対して Java 17 コンパイル、Mixin refmap 生成、intermediary へのリマップを行います。出力へ Minecraft / Fabric / Mixin の依存クラスを混入させない検査も行います。取得済みキャッシュ一式はソース ZIP に同梱しません。

この手順の成功と、配布 JAR の実際の起動・ゲーム内操作試験は別項目です。結果は `TEST_RESULTS.md` に記録します。

## 公式資料

- [Forge 1.20.1 開発前提](https://docs.minecraftforge.net/en/1.20.1/gettingstarted/)
- [Forge 1.20.1 配布一覧](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html)
- [ForgeGradle 配布一覧](https://files.minecraftforge.net/net/minecraftforge/gradle/ForgeGradle/index.html)
- [Fabric Loader Maven](https://maven.fabricmc.net/net/fabricmc/fabric-loader/)
- [Fabric Loom Maven](https://maven.fabricmc.net/net/fabricmc/fabric-loom/)
- [Fabric API Maven](https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/)
- [Fabric 開発案内](https://fabricmc.net/develop/)

## GitHub の互換性ビルド

公開時の CI は基準の Forge / Fabric を必須ジョブとし、Forge 47.4.28 と Fabric Loader 0.20.0 + API 0.92.12+1.20.1 を非必須の候補ジョブでビルドします。候補版は 2026-10-17 に上記公式配布一覧で存在を確認しました。候補ビルドの成功だけでは対応済みとせず、ゲーム内回帰試験後に基準版の更新を判断します。CI はリリースを作成せず、EULA を自動承諾せず、ゲームを起動しません。
