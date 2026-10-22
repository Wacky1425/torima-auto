## 変更の目的と所有権
- [ ] 対象チャットと担当ブランチ（`dev/torima` / `dev/app-<slug>` / `integration/staging`）を明記
- [ ] 他チャットが管理するソースファイルを無断変更していない
- [ ] 統合するcommit SHAと差分を確認済み

## 動作と安全性
- [ ] Android`applicationId`とtarget packageNameの取り違えがない
- [ ] 既存Torimaの署名・データ・設定との互換性を維持
- [ ] 別APKとして共存できる場合は、別名で表示される
- [ ] 複数のAccessibilityServiceが同じ画面を操作しない
- [ ] 広告の×とガチャ／読み込み中の×を区別
- [ ] インストール・購入・シェア・スキップ券の誤操作を防止
- [ ] 不明画面は未完了／失敗で終了し、回収成功を偽装しない

## 検証
- [ ] `assembleDebug`成功
- [ ] `testDebugUnitTest`成功
- [ ] アプリ固有のタスク完了証拠を検証
- [ ] 実機で確認した内容・未確認の内容を区別
- [ ] 診断ZIPと再現手順を記録（機微情報はGitHubに公開しない）

## リリース運用
- [ ] 開発版APKはActions Artifactsで配布（Releaseを作らない）
- [ ] mainへマージする前に統合担当のレビューを受ける
- [ ] 正式ReleaseはversionCode/versionName、署名、APK assetを検証
- [ ] 回復・ロールバック方法をメモ

### リンク・進捗
担当アプリ：
対象ブランチ：
コミットSHA：
診断結果：
実機テスト：
次にやること：
