# 開発全体ステータス / 引き継ぎ更新欄

更新：2026-10-22。ここは全チャット共通の**短い状態台帳**。終わったことと未着手のことを混同しない。

## 出発点
- 安定Torima正式Release: **v3.14** / `com.neru.powlautotest`
- Torima`app/`はそのまま。現時点では物理的な`common/`、`apps/<slug>/`、`integrated/`のモジュール分割は**未着手**。
- 他のポイ活アプリの具体名はまだ決まっていない。**新アプリ2つ以上**を順次並行開発し、増加数の上限は設けない。

## 役割別の次の作業
| 担当 | ブランチ | 直近目標 | 開始時に読む |
|---|---|---|---|
| Torima新チャット | `dev/torima` | v3.14をベースに実機検証・移動と歩数の自動回収の修正 | [HANDOFF_TORIMA.md](HANDOFF_TORIMA.md) |
| 新アプリ① | アプリ確定後`dev/app-<slug>` | 独立APK + 1種類の確実な回収 | [HANDOFF_NEW_APP_TEMPLATE.md](HANDOFF_NEW_APP_TEMPLATE.md) |
| 新アプリ② | アプリ確定後`dev/app-<slug>` | 独立APK + 1種類の確実な回収 | 同上 |
| 新アプリ③以降 | アプリごとに増やす | 個別に並行開発 | 同上 |
| 統合チャット | `integration/staging` | QA APK共存の確認、共通API、段階的統合 | [HANDOFF_INTEGRATION.md](HANDOFF_INTEGRATION.md) |

## 現在の準備作業
- [x] GitHubの現状確認、正式Torima v3.14を基準に選定
- [x] ドキュメント用の`AGENTS.md`と`docs/multi-app/`を作成
- [x] アプリ専用・統合担当の所有ファイル境界を文書化
- [x] 新チャット用の引き継ぎ資料とコピー用開始文を用意
- [x] 開発版APKを正式Releaseせずビルドするworkflowを追加
- [x] ドキュメント・workflowをmainへ反映（既存Torima v3.14正式Releaseは維持）
- [x] `dev/torima`、`integration/staging`の専用ブランチを作成しSHAを確認
- [x] Torima debug `.dev` と固定署名QA `.qa` の独立アプリID・表示名・Actionsビルド設定
- [x] [開発ビルド#39701993971](https://github.com/Wacky1425/torima-auto/actions/runs/39701993971)でDebug/単体テスト/固定署名QA APK/Artifacts公開が成功
- [ ] OPPO端末でTorima QAと正式Torimaが共存・QAに再インストールしても学習が残るかを確認
- [ ] 新アプリ名確定・新規ブランチ/モジュールの作成
- [ ] 共通エンジンの物理的なGradleモジュール分割
- [ ] 統合版APK

## チャット終了時の報告テンプレート
```text
日付:
担当アプリ／チャット:
対象ブランチ:
確定コミットSHA:
変更したファイル:
Androidビルド: 未実行 / 合格 / 失敗
単体テスト: 未実行 / 合格 / 失敗
端末で確認済み:
未確認:
既知の問題:
次の変更:
統合担当への依頼:
診断ZIPの有無:
```

## 引き継ぎが混線しそうになったら
まずこの台帳と[ブランチ運用](BRANCH_AND_RELEASE_RULES.md)を参照。GitHubコミットを確認せずに「他チャットが作った」と断定しない。同名Releaseが見えても、対象のAPK・version・SHAを検証する。古い開発チャットの説明が実装と食い違う場合は、対象ブランチの実際のコード・テストを優先する。

## 統合担当引き継ぎ・初期API追加（2026-10-22）
- 基準コミット: `7dff02b`。作業ブランチ: `integration/staging`。本項を含むコミットが変更単位（SHAはgit logで確認）。
- 変更: `common/`にAndroid非依存APIと対象違い・古い観測・別画面・不正完了のテスト、ルートGradleへの登録、開発CIの`:common:test`、`MODULE_CONTRACT.md`。
- `app/`、正式applicationId、署名、学習保存、mainは未変更。共通APIは既存Torimaに未接続。エンジン抽出/統合APK/サービス多重操作防止は未実装。
- ローカル: `git diff --check`合格。Gradleは配布元へのネットワーク到達不可で起動前に失敗。テスト合格とは扱わず、push後のActionsで検証する。
- 実機: 未実施。QAの共存と学習保持確認が必要。新アプリ名/packageは未確定のまま。
- 次: ActionsのAPI/Androidビルド確認、OPPO QA共存・同一QA更新テスト、その後確定した新アプリのモジュール追加。
## 2026-10-22 Torima専用：マップストックQA候補
- `dev/torima`：既存QAの実機基本動作はユーザー報告あり。詳細ZIPなし。
- 移動→歩数→赤丸ありマップ→ストック宝箱2広告回収を初回実装。コード・制約・テストは同ブランチHANDOFF_TORIMA末尾参照。新機能の実機は未確認。
- 正式版・統合コード・CIは変更なし。QAビルド結果は実行後記録。

- 初回マップ実装SHA：`a75e6c0ad651b5f95409d2da63f2a2ae46026ef7`。Actions `39714388563` のdebugビルド・単体テスト成功。親ボタン解決を補強した後続コミットのQAはActionsで確認。

- 最終QAコードSHA：`2ef51f2a43ad277cc105380552c6314d972e4122`。Actions [39714575369](https://github.com/Wacky1425/torima-auto/actions/runs/39714575369) でdebugビルド・単体テスト・署名QA APK生成すべて成功。Artifact `12398087850`。新マップ機能の実機検証は未実施。提供3画面の赤丸を縮小画像で確認（PC上端を除いた場合も検出）。

## QAアプリ内更新（2026-10-22、CI検証中）
- `integration/staging`で実装。Torimaソース基準は`dev/torima`の`b7899db`。マップ等の専用変更はそのまま保持し、専用ブランチにはpushしない。
- 追加/変更: `common/qa-updater/`、`qa/apps.json`、`scripts/qa/`、`qa-publish.yml`、開発workflow、ルートGradle、`app/build.gradle.kts`と`TorimaUpdater.kt`、本資料と[QA_UPDATES.md](QA_UPDATES.md)。
- QA固定署名・固有IDを維持。正式Releaseと別の公開QAプレリリースにビルド済みAPKを配布。APKに秘密トークンなし。版別ID/署名/ハッシュを端末で検査する。
- ローカルPython配布テスト2件合格、diff整形確認合格。Androidはローカル通信制限でGradle取得不可、Actionsへ引き継ぐ。CI合格・公開asset確認まで完了扱いしない。
- 前回初期APIコミット`d229605`の[Actions #39704114700](https://github.com/Wacky1425/torima-auto/actions/runs/39704114700)は共通API/Android/QAビルド成功を確認。
- OPPOでの上書き・設定/画像/ノード学習保持は未確認。初回手動QA上書き、その次の版からアプリ内更新する手順をQA_UPDATESに用意。
