# ポイ活自動回収：複数アプリ並行開発 / START HERE

最終更新：2026-10-22。現状の参照先：[Wacky1425/torima-auto](https://github.com/Wacky1425/torima-auto)。**これは開発体制の整理資料であり、新アプリの実装や統合完了を意味しない。**

## ゴール
- トリマを含め、ユーザーが今後追加する**アプリ数に上限を設けない**。
- 開発中はアプリごとに**固有のapplicationIdを持つ独立APK**をスマホに共存させ、異なるチャットで実装・診断・ビルド・実機テストできる。
- 本番用Torima v3.14のAPK、署名、既存の学習データを維持する。
- 統合担当が検証済み機能を取り込み、最後に別IDの**統合マネージャー**（対象アプリ選択、個別／一括実行、開始停止、学習、記録）を開発する。
- 既存アプリの修正ごとに正式Releaseを作らず、開発ブランチのGitHub Actions **テスト版APK**で実機確認し、統合後に正式Releaseを作る。

## 現在の事実（2026-10-22）
- 最新の確認済み正式Release：**v3.14**。APK名`TorimaAuto-release.apk`。
- Androidプロジェクトは`settings.gradle.kts`上では**`:app`のみ**。現在のアプリIDは`com.neru.powlautotest`、対象Torimaは`jp.co.incrementp.milemobile`。
- Kotlinコードは`app/src/main/java/com/neru/powlautotest/`に混在。特に`AutoFlow.kt`、`PowlAccessibilityService.kt`は大きく、今すぐファイル移動すると動作を壊すリスクが高い。
- 移動タンク回収、歩数回収、HOME紫ボタンの上スワイプ復旧、広告／ガチャ／Play復帰、画像学習、赤丸判定、診断ZIPが実装済み。**全機能がOPPO実機のあらゆる広告で動作検証済み、という意味ではない**。
- 既存`.github/workflows/android.yml`は`main`へのpushでビルド・単体テスト・署名付き正式Releaseを試行する。作業用に`.github/workflows/dev-apk.yml`を追加して、開発ブランチでは公開Releaseを作らない。 Torima debug `.dev` と固定署名QA `.qa` は別インストール用の設定を追加した（ビルド・実機は要確認）。
- 自動操作は利用規約や端末の画面表示・Accessibility制約に左右される。誤操作したら診断して修正する。

## 入口（担当別）
| 担当 | 読む資料 | Gitブランチ | 主な作業 |
|---|---|---|---|
| トリマ専用チャット | [HANDOFF_TORIMA.md](HANDOFF_TORIMA.md) | `dev/torima` | Torima固有の修正・実機テスト |
| 新アプリ用チャット（1アプリに1つ） | [HANDOFF_NEW_APP_TEMPLATE.md](HANDOFF_NEW_APP_TEMPLATE.md) | `dev/app-<slug>` | そのアプリ専用モジュールと診断 |
| 統合専用チャット | [HANDOFF_INTEGRATION.md](HANDOFF_INTEGRATION.md) | `integration/staging` | 共通機能の抽出・APK統合・正式Release |
| 全チャット共通 | [AGENTS.md](../../AGENTS.md)、[BRANCH_AND_RELEASE_RULES.md](BRANCH_AND_RELEASE_RULES.md) | - | 衝突防止・署名・安全対策 |

新チャットには[CHAT_START_PROMPTS.md](CHAT_START_PROMPTS.md)の該当文を送る。別チャットの曖昧な記憶に頼らず、GitHubの資料と対象ブランチを読んでから作業する。

## 進める順番
1. **今回：** 既存Torimaコードを動かさず、機能ごとの所有範囲、引き継ぎ資料、ブランチ、開発版APKのワークフローを整える。
2. Torima新チャットを`dev/torima`から始動。現在のv3.14からバグ修正継続。
3. 別のポイントアプリが決まり次第、`dev/app-<slug>`を追加。**2個以上**の新アプリでも、それ以上でも同じ手順。アプリIDは重複禁止。
4. 統合チャットが`integration/staging`に`common/`のinterfaceと`apps/<slug>/`モジュールを段階的に追加。元のTorima動作との回帰テストを毎回行う。
5. 各APKは単独で実機確認を続け、共通化済み機能は統合版でも検証。完成してからいきなり全アプリを合体させない。

## 現段階の制限
これは**分離開発の土台**であり、Androidアプリの物理的なマルチモジュール化、ほかのアプリの実装、学習データの統合版への移行、統合版APK作成はまだ。完了状況は[STATUS.md](STATUS.md)に記録する。
