# 開発チャット共通ルール / AGENTS.md

このリポジトリは**既存トリマ（Torima Auto v3.14）を壊さず、複数ポイ活アプリを個別APKで開発し、最後に統合する**ための作業場です。アプリは4個に制限しません。新しいアプリが決まり次第、1アプリにつき1つの専用チャットと開発ブランチを作ります。

## 最初に必ず読む
1. [docs/multi-app/START_HERE.md](docs/multi-app/START_HERE.md)
2. [docs/multi-app/BRANCH_AND_RELEASE_RULES.md](docs/multi-app/BRANCH_AND_RELEASE_RULES.md)
3. 自分の担当用引き継ぎ資料: [Torima](docs/multi-app/HANDOFF_TORIMA.md) / [新アプリ](docs/multi-app/HANDOFF_NEW_APP_TEMPLATE.md) / [統合](docs/multi-app/HANDOFF_INTEGRATION.md)
4. 新しいチャットの冒頭文: [docs/multi-app/CHAT_START_PROMPTS.md](docs/multi-app/CHAT_START_PROMPTS.md)

## 所有権と安全な作業範囲
- **トリマ専用チャット**：`dev/torima` ブランチ。既存`app/`のトリマ固有処理の修正、テスト追加、診断改善。将来`apps/torima/`へ移すのは統合担当の承認後。
- **アプリ固有チャット**：`dev/app-<slug>` ブランチ。原則`apps/<slug>/`だけ編集。既存`app/`と他アプリの実装を直接変更しない。
- **統合チャット**：`integration/staging` ブランチ。`common/`・`integrated/`・Gradleモジュール登録・GitHub Actions・衝突の解決・`main`への正式反映を担当。
- **`main`は正式版**。個別チャットが`main`を直接更新したり、勝手に正式Releaseを作ったりしない。現在はGitHubの強制的な保護設定は未構成のため、この運用ルールを必ず守ること。
- ブランチ所有者以外の変更はまず提案し、統合チャットへ渡す。各チャットは最新の`integration/staging`との差分を確認し、統合はPRまたはコミット単位で行う。
- 競合を「mainに強制push」「他人のファイルを丸ごと上書き」で解消しない。`git merge`と`cherry-pick`は意図を確認する。テスト済みの変更のみ統合する。

## Android固有の不変条件
- 既存Torimaの`applicationId=com.neru.powlautotest`、現行署名、保存済み学習データの互換性を維持する。名称変更・アプリID変更・データ削除を伴う変更は禁止（明示承認なし）。
- 新アプリは**各自固有のapplicationId**を持つ。独立APKを同じ端末に共存できることをテストする。統合版も別applicationIdを持つ。
- **AccessibilityServiceはテスト時に対象のものだけ有効・起動**する。複数のサービスが同じ対象画面を操作しないよう、起動時に対象パッケージと実行権限を検証する。
- 共通化候補は即「共通実装済み」ではない。現時点の`AutoFlow.kt`、`PowlAccessibilityService.kt`、画像学習クラスはTorima依存が混在。引き抜く前にテストと抽象化を用意。
- 指タップとAccessibility合成タップは動作が異なる場合がある。ジェスチャー完了と報酬獲得を同一視しない。
- 広告終了後の×とガチャ演出／読み込み中の×を混同しない。シェア、インストール、購入、スキップ券消費を誤タップしない。画面が不明なら失敗・未完了として記録する。
- 画像学習は端末内処理。学習結果は**ノードまたはその場で取得した画像と画面結果**で検証。信頼度の高い類似操作も誤操作の条件下では実行しない。
- 実行時間上限・再試行回数・失敗停止を維持。報酬の「回収完了」は、対象アプリの画面による独立した証拠で判断する。
- 外部サービスの利用規約、利用可能な操作、Accessibility権限とプライバシーに注意する。

## ビルドとリリース
- 作業途中は`.github/workflows/dev-apk.yml`で **ActionsのテストAPK成果物**を取得する。正式Releaseは不要。
- 既存の`.github/workflows/android.yml`はmain向け正式Releaseワークフロー。バージョンを上げると正式APKがリリースされる。個別チャットから実行しない。
- コード修正には同じスコープの単体テスト、失敗例の回帰テスト、できれば実機診断ZIPを添付する。
- GitHubのActionsが成功しただけで「OPPO実機で動いた」とは主張しない。APKリリースを宣言する前に`releases/latest`と配布assetを検証する。
- 署名鍵・個人情報・診断内の機密データをpublic GitHubへ貼らない。シークレットはGitHub Actions Secretsに保持する。

## 引き継ぎ更新
各チャットの作業終了時に、担当資料へ「ブランチ」「コミットSHA」「変更ファイル」「ビルド結果」「実機結果」「既知の問題」「次の一手」を追記する。統合に渡す場合は[PRチェックリスト](.github/PULL_REQUEST_TEMPLATE.md)を使う。
