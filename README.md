# Torima Auto (Android 個人用検証アプリ)

トリマのAccessibility UIツリーを検証するための個人用Androidプロジェクトです。トリマ公式アプリではありません。

- **GitHubリポジトリは1つ**: https://github.com/Wacky1425/torima-auto （Public）
- GitHub ActionsでDebug APKを生成します。
- GitHub Actionsに署名鍵Secretsが登録されていれば、署名付きRelease APKを作り、同じリポジトリの**Releases**に公開します。
- アプリ内の「アプリの更新を確認」から、同じリポジトリの最新Releasesを読み取ります。
- Android Studioで開くのは、このREADMEがある`TorimaAuto`フォルダです。
- 既存のテストアプリとの互換性維持のため、`applicationId`とKotlinパッケージ名は`com.neru.powlautotest`のままです。
- 操作対象のトリマのパッケージ名は`jp.co.incrementp.milemobile`です。
- **毎回、配布する際に`app/build.gradle.kts`の`versionCode`と`versionName`を増やしてください**。同じタグの既存Releaseは上書きしません。

セットアップの詳細は [`SETUP_UPDATE_JA.md`](SETUP_UPDATE_JA.md) にあります。

注意: GitHubをPublicにすると、ソースコード・コミット履歴・ReleaseのAPKが誰でも見られます。署名鍵、認証情報や個人情報をcommitしないでください。トリマの利用規約にも注意してください。

## 複数アプリの並行開発・新チャット引き継ぎ（2026-10-22）

Torima v3.14の正式APK・学習データを維持しながら、今後増える複数のポイ活アプリを**アプリ別チャット・独立Gitブランチ・独立APK**で開発していきます。統合担当が検証済みの機能だけを段階的に取り込みます。アプリ数は4つに限定しません。

- **[必読：開発体制の入口](docs/multi-app/START_HERE.md)**
- [新チャット用のコピペ文](docs/multi-app/CHAT_START_PROMPTS.md)
- [ブランチ・APK・リリース運用](docs/multi-app/BRANCH_AND_RELEASE_RULES.md)
- [実コードの担当と共通化計画](docs/multi-app/ARCHITECTURE.md)
- [現在の作業状況](docs/multi-app/STATUS.md)

Torimaの安定版は従来の`com.neru.powlautotest`。開発用は`com.neru.powlautotest.dev`（通常のdebug）と`com.neru.powlautotest.qa`（固定署名で上書き更新できるQA）の2種類です。QA APKはActions Artifactsの`TorimaAuto-QA.apk`から入手。学習データは本番と別保存なので、**安定版から自動では引き継がれません**。実機での同居・操作確認は引き続き必要です。新アプリの独立APKと統合版APKはこれから追加します。
