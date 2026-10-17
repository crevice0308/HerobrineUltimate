# alpha.3 の公開前検証

設定パーサーの回帰テストは Linux / Java 21 で実施。Forge のサーバー開始リスナー配線は静的検査です。実際にクライアントを閉じずにワールドを再開する試験は未実施です。Fabric の実 NBT / Netty テストは CI の `packetTest` に追加しており、結果は対象コミットの Checks を参照してください。

alpha.3 の全ゲーム内試験を実施済みとは扱いません。以下は alpha.2 に付属していた過去の記録です。

# 検証結果と出典 — 0.1.0-alpha.2

## 添付された開発時の結果

2026-10-17 の提供ソースには、Forge 47.4.10 / Fabric Loader 0.16.14 + Fabric API 0.92.2+1.20.1、Minecraft 1.20.1、Java 17 の実行結果が含まれていました。

- 両ローダー各 14 GameTest 成功。
- 両ローダー各 85 クライアントステップ成功。Forge は開発ラン、Fabric は配布 JAR を読み込んだ実行。
- PlayerSettings 61 assertions、実 Minecraft NBT 64 assertions、構成 33 checks、Fabric パケット 89 assertions 成功。
- 両 JAR の再生成で SHA-256 一致。

これらは提供された開発時の記録であり、GitHub 公開作業で再実行したゲーム内試験ではありません。個人向け依頼文、元リファレンス画像、実行環境のパスを含む生ログ・スクリーンショットは公開ソースから除外しています。

## 公開前に再確認した項目

2026-10-17、Linux / Java 21 環境で PlayerSettings 61 assertions と構成 33 checks を再実行し成功。配布 JAR のハッシュを提供記録と照合し一致。JAR にゲーム本体・Fabric API・テスト専用クラスが含まれないことを確認しました。

この環境では Gradle 配布先へのネットワーク接続ができないため、Java 17 の全ビルドとゲーム起動を新たに実施できていません。GitHub Actions の結果は各コミットの Checks を参照してください。ビルド成功はゲーム内動作の検証完了を意味しません。

## 提供 JAR のハッシュ

- Forge: `5e736b0d009fc4095f0fe7e43643c20a2e539eaf6094fd89cbf9e2e4ceeaf143` (84,611 bytes)
- Fabric: `70b312987a8a326d22cdc73dcf83345ee5ae26b5c210b07deae8b5e802cb83fa` (73,510 bytes)

## 未検証

認証済みの外部クライアント複数人によるマルチプレイ、追加ローダー版のゲーム内動作、Windows / 実 GPU / 他 MOD の組合せ、全推移依存の脆弱性監査、長時間運用は未検証です。alpha として扱い、使用前にワールドをバックアップしてください。
