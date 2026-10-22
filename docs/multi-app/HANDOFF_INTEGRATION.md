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
- Torima開発用に debug `.dev` と**既存署名を使うQA `.qa`** を追加し、GitHub Actionsから `TorimaAuto-QA.apk` を出せるようコード設定済み。[GitHub Actions #39701993971](https://github.com/Wacky1425/torima-auto/actions/runs/39701993971)でビルド・単体テスト・固定署名QA APKの生成・Artifact公開は成功。OPPO端末での独立インストールは要確認。別サンドボックスなので本番の学習データは自動で移らず、必要ならexport/importを設計する。
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

## 統合担当引き継ぎ・初期API追加（2026-10-22）
- 基準コミット: `7dff02b`。作業ブランチ: `integration/staging`。本項を含むコミットが変更単位（SHAはgit logで確認）。
- 変更: `common/`にAndroid非依存APIと対象違い・古い観測・別画面・不正完了のテスト、ルートGradleへの登録、開発CIの`:common:test`、`MODULE_CONTRACT.md`。
- `app/`、正式applicationId、署名、学習保存、mainは未変更。共通APIは既存Torimaに未接続。エンジン抽出/統合APK/サービス多重操作防止は未実装。
- ローカル: `git diff --check`合格。Gradleは配布元へのネットワーク到達不可で起動前に失敗。テスト合格とは扱わず、push後のActionsで検証する。
- 実機: 未実施。QAの共存と学習保持確認が必要。新アプリ名/packageは未確定のまま。
- 次: ActionsのAPI/Androidビルド確認、OPPO QA共存・同一QA更新テスト、その後確定した新アプリのモジュール追加。

## QAアプリ内更新（2026-10-22、CI検証中）
- `integration/staging`で実装。Torimaソース基準は`dev/torima`の`b7899db`。マップ等の専用変更はそのまま保持し、専用ブランチにはpushしない。
- 追加/変更: `common/qa-updater/`、`qa/apps.json`、`scripts/qa/`、`qa-publish.yml`、開発workflow、ルートGradle、`app/build.gradle.kts`と`TorimaUpdater.kt`、本資料と[QA_UPDATES.md](QA_UPDATES.md)。
- QA固定署名・固有IDを維持。正式Releaseと別の公開QAプレリリースにビルド済みAPKを配布。APKに秘密トークンなし。版別ID/署名/ハッシュを端末で検査する。
- ローカルPython配布テスト2件合格、diff整形確認合格。Androidはローカル通信制限でGradle取得不可、Actionsへ引き継ぐ。CI合格・公開asset確認まで完了扱いしない。
- 前回初期APIコミット`d229605`の[Actions #39704114700](https://github.com/Wacky1425/torima-auto/actions/runs/39704114700)は共通API/Android/QAビルド成功を確認。
- OPPOでの上書き・設定/画像/ノード学習保持は未確認。初回手動QA上書き、その次の版からアプリ内更新する手順をQA_UPDATESに用意。

### 初回QA配布の検証結果
- 実装コミット: `b64834174adeaa24c6f021578faef837761952fe`（親: 統合`d229605`とTorima`b7899db`）。
- [QAビルド/テスト/配布 #39723455063](https://github.com/Wacky1425/torima-auto/actions/runs/39723455063)成功。共通API、更新ポリシー、Torima QA/Debugのテスト、固定署名QA、匿名APKのSHA-256確認を完了。
- [DEVビルド #39723455046](https://github.com/Wacky1425/torima-auto/actions/runs/39723455046)も成功。
- 初回導入用: `qa-torima-100001` / `torima-qa-100001.apk`（2,882,332 bytes、SHA-256 `26fef511dfd2dc20d2774de16b21e19f7e65aa5c1d99c6e1ffd1cc0946b4b98d`）。プレリリース/asset実在確認済み。
- 次版はQA現在版の表示と公開manifestの匿名取得確認を追加。初回導入用100001を残して、100002へのアプリ内更新を実機テストできるようにする。端末結果は未確認。
