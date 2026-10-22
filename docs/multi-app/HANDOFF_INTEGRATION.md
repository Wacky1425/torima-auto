# 統合専用チャット：設計・開発統制・最終APK 引き継ぎ

## 自分の責務
これは各アプリの画面を毎回実装するチャットではなく、**複数の独立チャットが衝突せず開発できる土台を維持する統合チャット**。新アプリは何個でも追加可能。アプリ専用ロジックは原則その担当に任せ、統合が所有するのは共通エンジン・Gradle・Androidサービスの多重操作防止・CI・データ移行・正式版。

- GitHub：https://github.com/Wacky1425/torima-auto
- 作業ブランチ：`integration/staging`、正式リリースする時だけ`main`。
- 必読： [AGENTS.md](../../AGENTS.md) / [ARCHITECTURE.md](ARCHITECTURE.md) / [BRANCH_AND_RELEASE_RULES.md](BRANCH_AND_RELEASE_RULES.md) / [STATUS.md](STATUS.md)
- Torima専用ブランチ：`dev/torima`。新アプリ：`dev/app-<slug>`（増加数の上限なし）。

## 現状（2026-10-22）
**正式Torima v3.14が基準**。`:app`一つ、Kotlinコードに共通化候補とトリマ固有処理が混在。現在、`common/`、`apps/<slug>/`、`integrated/`は目標の**設計案**であり、実モジュールの存在を意味しない。

`.github/workflows/android.yml`はmain pushでTorimaの署名付きReleaseを作る。今回新設の`.github/workflows/dev-apk.yml`は独立した開発ブランチのdebug APKテスト成果物を作るだけで正式Releaseしない。

## 絶対に避ける破壊的移行
- Torimaの`applicationId=com.neru.powlautotest`変更、Package rename、既存の学習データ破棄、署名キー変更を伴うアップデート。
- 別のアプリから`main`へ直pushさせる。
- トリマに特有の画面名／座標／ガチャ条件を`common`のグローバルルールとして使用する。
- アプリが複数のAccessibilityServiceを同時に動かして相互にタップする。
- Debug署名が変わるAPKで既存本番アプリを不用意に上書きさせる。
- 動作確認不足の大量コード移動を一気に行う。

## 統合の実装フェーズ

### Phase 0：現在（完了を目指す）
- AGENTSと各ハンドオフ、ブランチ命名、PRテンプレート、開発用APKのActionsワークフローを整える。
- `dev/torima`, `integration/staging`を作成。`dev/app-<slug>`は実際のアプリが決まり次第作成。
- `main`はv3.14 APKをそのまま維持。ドキュメントだけの更新は正式Releaseを増やさない。

### Phase 1：独立APKの実機テスト基盤
- 新アプリのための`apps/<slug>`のモジュール登録契約を策定する。新アプリ用`applicationId`は一意、`FileProvider`のauthorityも一意。
- トリマdebug APKに `applicationIdSuffix=.dev`、表示名 `Torima Auto DEV` を設定済み。**GitHubビルドとOPPO端末での独立インストールは未検証**。本番の学習データは別サンドボックスなのでexport/import機能を設計する。
- QA用の表示名／バージョン表示／共存インストールを用意。必要ならGitHub Actions Secretsの署名鍵を再利用し、**Secrets自体は公開しない**。
- 新アプリ2つ以上が別々にビルドできることを実機で確認。

### Phase 2：安全な共通API
- `ScreenObservation`、`SafeAction`、`OperationOutcome`、`CompletionEvidence`、`RewardAppAdapter`を定義。
- まず純粋な画像比較や状態管理（`VisualTemplateModel`等）を小さく移動してテスト。
- `AutoFlow`一括置換は不可。Torimaの従来エンジンを維持し、抽出ごとに回帰テスト。
- 汎用画像学習の保存は**対象アプリID**を含め、Torima用の×が新アプリで押されないように分離。

### Phase 3：統合マネージャー
- 別IDの`integrated/`アプリを作り、アプリ選択、個別・一括実行、開始停止、完了画面、失敗理由、1つの診断ZIPを提供。
- **同時実行禁止の単一Accessibility host／タスクキュー**を基本とする。ターゲットパッケージの前面確認、デバイスの状態、実行上限、再開・停止を管理。
- 各アプリのスタンドアロンAPKは統合版と共存。どのAccessibilityServiceを有効にするかユーザーが分かるよう表示する。
- 正式Releaseは統合チャットが判断し、Torima安定APKは勝手に消さない。

## 変更が統合に届いた時のチェック
1. 差分と所有者の範囲（他のチャットのファイルを巻き込んでいないか）。
2. ターゲットpackageNameとアプリIDの一意性。
3. 画像学習・広告×・ガチャ誤クリック・券消費・購入・シェア等の安全性。
4. Unit testとworkflow結果。端末上の本物のタップ／画面遷移／完了判定の実機証拠。
5. 設定・学習データ・パッケージ・署名の互換性。
6. PRに再現手順とrollbackがあり、後のブランチで安全に続けられるか。

## 新しいアプリを追加する依頼が来たら
1. 名前とAndroidターゲットpackageを確定。
2. スラッグを`[a-z0-9-]+`で一意に決定し、[APPS_REGISTRY.md](APPS_REGISTRY.md)へ追加。
3. `dev/app-<slug>`ブランチを適切な統合基準から作成。担当チャットへ[新アプリ引き継ぎ](HANDOFF_NEW_APP_TEMPLATE.md)のリンクを渡す。
4. Gradle追加モジュールと独立アプリIDのビルド契約を整える。
5. 既存トリマと新アプリを同一端末に共存させ、各自のサービスを1つずつ実機確認。

最終目的は「いくつ増えても各アプリを単独テストでき、必要なときだけ統合できること」。統合後も各専用チャットが独立して不具合修正できる構成を維持する。
