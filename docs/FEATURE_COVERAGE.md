# 要求と実装の対応 — 0.1.0-alpha.2

元の仕様と GUI リファレンスに対する実装対応表です。公開用ソースには個人向けの依頼文と元リファレンス画像を含めていません。標準設定はチート OFF・OP 不要です。

この表の「実装」はソース上の対応です。実際に通過した試験、失敗や未検証範囲は `TEST_RESULTS.md` を正とします。

| 要求 | 現在の実装 | 主な実装箇所 |
|---|---|---|
| Forge / Fabric、Minecraft 1.20.1 / Java 17 | 別 JAR、共通コードとローダー固有コードを分離。Fabric API を依存宣言 | `forge/`、`fabric/`、`common/` |
| チート OFF・OP なしで全能力を利用 | 標準 `require-permission=false`。サーバー参加者は所有者でなくても操作可。OP やコマンドを付与しない | `HerobrineServer.allowed`、`init` |
| 管理者の任意の制限 | `require-permission=true` の場合のみ OP / 所有者 / UUID 許可リストで判定。地形・実体落雷・プレイヤー対象設定は別項目 | `HerobrineServer` |
| 半透明サイバーパネルと実操作部品 | シアン / パープル、左カテゴリ、実プレイヤープレビュー、ボタン、4 種の倍率、スクロール、ツールチップ | `HerobrineScreen` |
| 解像度・GUI スケール・キー操作 | サイズに応じた配置、狭い画面では操作を優先。マウス・Tab・矢印・PageUp / Down・Alt+↑ / ↓、キー割り当て画面 | `HerobrineScreen`、両ローダーの client |
| 発光 ON / OFF | 右上 G ボタンでローカル発光を独立切替・自動保存 | `HerobrineClient.togglePanelGlow` |
| Insert / F6 / Esc、再割り当て | 6 個の標準キー割り当て。一括 OFF でも個別選択・倍率を保持。画面は実際の割り当てを表示 | `ForgeClient`、`HerobrineFabricClient` |
| 変身と解除 | 専用スキン、標準幅モデル、発光する白い目。スキン参照を差し替えて 1 人称の腕と 3 人称へ適用。解除で元の参照へ復帰 | `AbstractClientPlayerMixin`、`HerobrineEyesLayer` |
| 頭上 / チャット / TAB の表示名 | 表示名のみ `Herobrine` に変更。UUID、GameProfile、アカウント名、署名付き本文を維持 | `PlayerRendererMixin`、`PlayerDisplayNameMixin`、`PlayerTabOverlayMixin` |
| God Mode | 通常ダメージを抑止。奈落・`/kill` は仕様上の例外 | `HerobrineServer.preventDamage`、両ローダーの damage hook |
| Infinite Hunger | 毎 tick 20 / 20、消耗度 0 | `HerobrineServer.tick` |
| Flight | 自由飛行、落下ダメージ対策、元の飛行権限の復元 | `reconcile`、`restoreFlight` |
| Lightning | 視線先への連続落雷、演出 / 実体の選択、サーバー設定と確認 | `lightning` |
| Combat | 威力倍率、視線対象の選択、遮蔽物・味方・PvP 判定、確認付き即死相当攻撃 | `target`、`specialAttack`、`eligibleTarget` |
| Teleport | 視線先へ移動、地点保存・帰還、次元間帰還、衝突・床・危険・境界・読み込み確認 | `safe`、`safeNear`、`teleportLook`、`returnWaypoint` |
| Mob Control | 4 種類の召喚、追従、集合、攻撃支援、解散、所有者照合。登録個体の死亡・再読み込み・次元移動を追跡 | `summon`、`updateMobs`、`attacked`、`entityDied`、`entityLoaded` |
| World Control | 昼夜・天候と確認付き 3×3×3 地形変更。通常のプレイヤー破壊処理を使用 | `world`、`terrain` |
| Invisibility | バニラの透明化。当たり判定と装備 / AI の仕様を README に明記。解除時にポーション等の透明化を維持 | `reconcile`、`restoreInvisibility` |
| Motion Boost | 移動・採掘・攻撃速度、威力の倍率。有限値・上限検査、固有 UUID の属性補正 | `PlayerSettings`、`modifier`、`mining` |
| 変身演出 | 演出用落雷、パーティクル、約 350 ms の画面フラッシュ。Effects で停止 | `reconcile`、`HerobrineClient.accept`、`GuiRenderMixin` |
| 自動保存・状態同期 | ワールド / UUID 別の SavedData、サーバーからの承認済み状態、退出時のクライアント初期化 | `HerobrineWorldData`、`sync`、`HerobrineClient.clear` |
| 死亡・再接続・次元・再起動の整合性 | 効果の所有状態をプレイヤータグで照合して復元。旧 Player の効果を解除して置換プレイヤーに再適用 | `session`、`recoverOwnedState`、`restore` |
| 専用サーバー分離・通信 | クライアント専用初期化を分離。サーバースレッドで操作し、クライアントから他人の UUID や任意座標を受け取らない | 両ローダーの main / client と `HerobrineServer.handle` |
| ソース・JAR・導入と検証資料 | 両ローダーのビルド手順、MIT、設定例、実行テスト、GUI 差分資料を収録 | README、`docs/`、`tests/`、`tools/` |

要求本文の能力カテゴリについて、未実装のまま残したカテゴリはありません。範囲を定めている項目は、帰還地点 1 個、召喚 Mob 4 種類・最大 16、読み込み済み範囲の追従、倍率上限、地形 27 ブロックです。これらは README に操作仕様として明記しています。

添付画像の回路装飾、ブルーム、アイコン、名前表示箇所ごとの独立チェックなど、実装と画像に残る差は `GUI_DIFFERENCES.md` に列挙しています。ゲーム内の全視点・全ローダー版・全 MOD 組合せの検証完了を、実装済みという語で代用しません。
