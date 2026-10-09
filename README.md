# Torima Auto（開発・検証用）

トリマ画面のAccessibility UIツリーを検証する個人用Androidプロジェクト。公式アプリではありません。

- Android Studioで開くフォルダは、このREADMEがあるプロジェクトルート。
- 既存のインストール済みテストアプリとの互換性維持のため、`applicationId` とKotlinパッケージ名は `com.neru.powlautotest` のまま。
- トリマの対象パッケージ名は `jp.co.incrementp.milemobile`。
- `.github/workflows/android.yml` でmainへのpush時にデバッグAPKを生成し、ActionsのArtifactsに保存。
- **まだアプリ内更新機能は未実装**。GitHub ActionsのデバッグAPKは恒久的な更新署名の保証がないため、次の段階で固定のリリース署名とアプリ内更新機能を導入する。
- Accessibilityサービスの許可と操作対象アプリの利用規約に注意。
