# GitHub・ブランチ・APK・リリース運用（並行開発の衝突防止）

## 現状の基準
- リポジトリ：[Wacky1425/torima-auto](https://github.com/Wacky1425/torima-auto)
- 現行正式版：Torima Auto **v3.14**
- 元の`main`はGitHubのブランチ保護が **設定されていなかった**（2026-10-22確認）。チャット上のルールだけでは強制不可。可能ならユーザーがGitHub側のbranch rulesetでPR必須にする。
- `.github/workflows/android.yml`は**mainにpushすると**Torimaのdebugビルド・JUnitテスト・署名付き正式APK作成・同名タグ未作成なら最新Release公開まで自動実行する。開発チャットは`main`を更新しない。

## ブランチ
| 名前 | 所有するチャット | 用途 |
|---|---|---|
| `main` | 統合担当のみ | 正式版Torimaと確定済みの共通変更。既存ユーザーデータとの互換性必須 |
| `dev/torima` | Torimaチャット | 移動・歩数・広告・ガチャ・HOME復旧等のTorima専用作業 |
| `integration/staging` | 統合チャット | 複数モジュール、Androidビルド、統合／競合処理 |
| `dev/app-<slug>` | その新アプリ専用チャット | 1アプリ1ブランチ・1独立APK・専用テスト |
| `feature/<topic>` | 所有者が明確な短期作業 | まとまった機能の下書き。最終的に担当ブランチへまとめる |

新しいアプリを増やす際のslugは英数小文字・ハイフン。名称が決まるまでは`dev/app-<slug>`を**実体のないダミーアプリとして作らない**。

## 編集ファイルの基本範囲
1. トリマは原則`app/`内。共通interface・`settings.gradle.kts`・GitHubワークフローを変更する必要が出たら統合チャットへ依頼。
2. 新アプリは`apps/<slug>/`内。対象アプリ名、packageName、必要権限、タスクの完了証明、スクショ学習等を独立実装。Torima専用の`app/`は変更禁止。
3. 統合は`common/`,`integrated/`,ルートGradle設定、CI、共有APIを管理。Torimaを大規模に触るときはTorimaチャットの動作確認と合わせる。
4. 同一ファイルを2つのチャットが同時に直接上書きすることを禁止。特に`AutoFlow.kt`/`PowlAccessibilityService.kt`/`settings.gradle.kts`は所有者を確認する。
5. 本番APKの`applicationId`／署名／保存領域／表示される対象パッケージは無断変更しない。

## チャット間の変更連携
- 開発チャットはまず担当ブランチへコミットする。
- 変更の引き継ぎメモに**コミットSHA・対象ブランチ・変更ファイル・実機確認結果**を残す。
- 統合担当は`integration/staging`へPRまたはcherry-pickする。見た目だけでなく単体テストと対象アプリでの操作を確認。
- 統合が競合したら差分を読み、両方の安全条件を維持してから解消。片方を丸ごと選ぶだけにしない。
- 正式版を出すときだけ`main`へ反映し、アプリの`versionCode`と`versionName`を上げる。Release assetが実在するまで公開成功とは言わない。

## APK作成：正式Release待ちを減らす
追加した`.github/workflows/dev-apk.yml`は、`dev/**`・`integration/**`などの開発ブランチへのpushで**debug APKと単体テスト結果をActions Artifactsとして保存**する。正式Releaseタグは作らない。

手順：
1. 該当の開発ブランチに変更をpush。
2. GitHub → Actions → `Development APK (no release)` → 該当run → **Artifacts**。
3. テストAPKを端末へ保存して実機テスト。Debugキーがrunごとに変わると上書き更新できないことがあるので、既存Torimaの学習データを維持してテストしたい場合は本番と同じ署名の**専用テスト配布フロー**を統合担当に依頼する。
4. 安定Torimaを上書きしない**独立したテスト用アプリID**での配布は次段階で導入予定。現時点の`:app:assembleDebug`は`applicationId=com.neru.powlautotest`なので、**安定Torimaと共存できる開発APKではない**。混同してインストールしないこと。
5. Androidアプリごとの追加`:apps:<slug>`モジュールが存在する場合、`workflow_dispatch`の`module`に`apps:<slug>`を指定してそのAPKと単体テストを生成できる。

### 既存Actionとの違い
| 対象 | 既存`android.yml` | 新規`dev-apk.yml` |
|---|---|---|
| 実行対象 | main / 手動 | dev・integration・feature・fix系 / 手動 |
| ビルド | Torima debug, tests, signed release | 指定モジュールのdebug, tests |
| GitHub Release | 署名秘密鍵があれば作成 | **作成しない** |
| 出力 | Artifacts＋正式Release | **Artifactsのみ** |
| 追加アプリ数 | Torima固定 | Gradle module選択で対応 |

## 危険な操作とデータ
- 広告／ガチャ／購入／シェア／スキップ券の操作を誤らせない。
- ZIPに個人情報がある場合は内容を確認してから共有。Public GitHubに生ログやスクショをコミットしない。
- スマホ実機では複数のAccessibilityサービスを同時動作させない。対象アプリのパッケージが一致しているときだけ操作。
- 各アプリの規約を検討して適切な自動化範囲を決める。

## 統合担当への要望（未実装）
安定Torimaを残したまま、`qa`用applicationIdSuffixや統合版の**固定署名・独立インストール**をテスト用に実装すること。学習データexport/importと専用のAPK表示名を作り、テスト対象を取り違えない仕組みにする。
