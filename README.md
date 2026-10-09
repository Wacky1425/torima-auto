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
