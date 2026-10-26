# 開発全体ステータス / 引き継ぎ更新欄

更新：2026-10-22。ここは全チャット共通の**短い状態台帳**。終わったことと未着手のことを混同しない。

## 出発点
- 安定Torima正式Release: **v3.14** / `com.neru.powlautotest`
- Torima `:app` を維持。共通API `:common` とQA更新ライブラリ `:qa-updater` を追加済み。既存回収エンジンの抽出、`apps/<slug>/`、`integrated/`は未着手。
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

### 初回QA配布の検証結果
- 実装コミット: `b64834174adeaa24c6f021578faef837761952fe`（親: 統合`d229605`とTorima`b7899db`）。
- [QAビルド/テスト/配布 #39723455063](https://github.com/Wacky1425/torima-auto/actions/runs/39723455063)成功。共通API、更新ポリシー、Torima QA/Debugのテスト、固定署名QA、匿名APKのSHA-256確認を完了。
- [DEVビルド #39723455046](https://github.com/Wacky1425/torima-auto/actions/runs/39723455046)も成功。
- 初回導入用: `qa-torima-100001` / `torima-qa-100001.apk`（2,882,332 bytes、SHA-256 `26fef511dfd2dc20d2774de16b21e19f7e65aa5c1d99c6e1ffd1cc0946b4b98d`）。プレリリース/asset実在確認済み。
- 次版はQA現在版の表示と公開manifestの匿名取得確認を追加。初回導入用100001を残して、100002へのアプリ内更新を実機テストできるようにする。端末結果は未確認。

## QA更新・配布検証完了（2026-10-22、端末確認は未実施）
- コード確定SHA: `72563bb34342e35631520afdc37d0df3ec14cde0`。作業ブランチ`integration/staging`。以下の資料更新コミットはコード/配布APKを変更しない。
- [QA更新版100002のActions #39723776583](https://github.com/Wacky1425/torima-auto/actions/runs/39723776583)成功。共通API/更新ライブラリ/Torima QA/Debugテスト、固定署名、APK実ID・版番号、公開APKとmanifestの匿名取得・ハッシュを検証。
- [DEV Actions #39723776584](https://github.com/Wacky1425/torima-auto/actions/runs/39723776584)成功。
- 更新先は`qa-channel-torima/manifest.json`。現在版`3.14-qa.100002`、APK `torima-qa-100002.apk`（2,882,384 bytes、SHA-256 `0df040321b5542a6c1907e6206d11a7985c24587a226da1259b9018758dfa8ca`）。[版別配布ページ](https://github.com/Wacky1425/torima-auto/releases/tag/qa-torima-100002)でasset実在確認。
- 初回導入用[QA 100001 APK](https://github.com/Wacky1425/torima-auto/releases/download/qa-torima-100001/torima-qa-100001.apk)を残し、アプリ内で100002へ上書きする実機検証が可能。100001/100002とも既存と同じQA ID/固定署名。
- 正式版`releases/latest`はv3.14、Release ID `420174934`、APK asset ID `664167534`で前後一致。mainと正式APKは変更なし。
- 専用チャット取り込み用[draft PR #1](https://github.com/Wacky1425/torima-auto/pull/1)を用意。`dev/torima`へは直接push/mergeしない。担当はPRの共通API/更新module/Gradle/CI/QA接続をまとめて取り込み、専用開発を続ける。取り込み前の旧dev workflowから出る47のQAを新QAへ戻そうとしない。
- 未確認: OPPO Reno11 Aの実際のインストール、画像/ノード学習・設定保持、権限画面からの復帰、キャンセル後の再試行。診断ZIP未取得。次は[QA_UPDATES.md](QA_UPDATES.md)の実機手順。回収・学習操作はテストAPKの端末確認結果を待つ。

## QA更新の実機報告（2026-10-22）
- ユーザーから「更新できた」、続いて設定・画像/ノード学習データの保持について「大丈夫よ～」との回答を受領。QA更新とデータ保持はユーザー報告により確認済み。
- 端末の実際の更新前後の版番号、キャンセル/通信失敗/権限設定からの復帰の個別検証、診断ZIPは未取得。これらまで合格したとは扱わない。

## 2026-10-23 Torima担当：学習・撮影診断の修正
- 対象：`dev/torima`。統合担当提供のQAアプリ内更新一式（integration/staging `f30682509f196a1353d81da53155f544ce3f08c3`）を履歴ごとfast-forwardで取り込み。共通module/CIの独自変更なし。正式mainは変更しない。
- 根拠：ユーザー共有ZIPでは操作履歴が自動ログ80件で上書きされ、撮影予約が「画像取得中」で繰り返し拒否。同梱画像が古い。コードでAutoFlowのlive probe開始→同じpoll内の診断captureがbusyで拒否される順序を確認。保存枚数24上限は古い順の入れ替えであり、容量満杯のエラーではない。
- 修正：診断captureを予約し、live probeの再開始より優先。15秒待機上限/完了期限と遅延コールバック保護、予約と結果の専用ログを追加。
- 学習：1回目の位置指定膜は画像特徴取得まで保持し、本画面への早すぎる追加タップを遮断。2回目は従来どおり本人の実タップを受動観察。学習開始/位置/画像取得/2回目待機/クリック通知/結果/失敗を自動ログと別ファイルへ保存。指の生入力元・圧力は観測できず、2回目の入力そのものを直接捕捉したとは記録しない。
- ZIP追加：09_学習診断.json、10_撮影診断.json。manifestに実applicationId/versionCode/versionNameを記録。失敗理由を特徴量不足・画像サイズ・座標範囲外・許可領域外へ分離。画像学習の照合閾値や自動タップ条件は緩めない。
- 対象ファイル：TeachOverlay、VisualLearnedActions、ScreenshotDiagnostics、DiagnosticBundleExporter、新規DiagnosticJournal/DiagnosticCaptureScheduleとその単体テスト、および本引き継ぎ資料。
- テスト：撮影予約中にlive probeが割り込まない、重複要求で待機期限が延長されない、撮影間隔を待って保存できる回帰テストを追加。ローカルQA配布Pythonテスト2件合格。AndroidはActionsで確認し追記。
- 実機：今回修正は未検証。既存ZIPではHOME上スワイプ後にSKIP_NOへの遷移成功、右上ImageViewの学習登録成功例あり。1回目漏れの原因自体は未確定。
- 次：新QAをアプリ内更新で取得し、右上×を位置指定→2回目案内→本人タップ。失敗時に09/10と最新画像がZIPに残るか確認。生ログ/ユーザー画面画像は公開Gitへ保存しない。

- 補足：ZIP作成用のローカル関数`put(String,String)`がJSONObject内の文字列putを隠し、appVersion等がmanifestではなくZIPルートの別エントリになっていた問題も確認。ZIP関数をwriteTextEntryへ改名し、版情報がmanifest内へ入るよう修正。

### 最終確認（2026-10-23）
- コードSHA `437607c5c03049a42924ce210a56ad257f688abc`、[QA Actions #39878094830](https://github.com/Wacky1425/torima-auto/actions/runs/39878094830) が成功。共通・更新ライブラリ・Torima QA/Debug単体テスト、署名QA APKビルド、実ID/版番号/署名/公開配布検証を通過。QA100005をアプリ内更新へ配布。実機は未検証。
- 初回のBuildConfig参照によるコンパイル失敗はPackageManagerから実インストール情報を読む形へ修正済み。失敗版100003/100004は配布されていない。

## 2026-10-23 Torima：学習位置の×出現前タップ防止
- `dev/torima` コード `49fdb6f7bf6d067c86825e4ec202a8fe921c1f5c`。学習位置の実画像×を2回確認し、未表示/消失/古い証拠では操作しない。学習ルールの個別ON/OFFと診断11/12を追加。既存学習データ互換を維持。
- QA100006配布済み。QA Actions #39880406207、DEV #39880406118成功。公開manifestの版/sourceShaと公開APK assetを確認。main/正式v3.14は変更なし。OPPO実機は未検証。詳細はHANDOFF_TORIMA末尾。

## 2026-10-23 Torima：マップ「今日」の任意分岐
- `dev/torima` コード `f073c43c9feb51ed069838b0318774e394282bb1`。「今日」表示時だけ操作し、なしならストックへ直接進む。
- QA100007配布、QA #39881505467 / DEV #39881505481成功。正式版/mainは変更なし。実機は未検証。詳細HANDOFF_TORIMA末尾。

## 2026-10-23 Torima：上下のHOME/MAP判定・ストック画像照合
- `dev/torima` コード `2909291be08072717531a4190ed2b24e3b49a87a`。上部見出し/下部ナビの選択状態でHOME/MAPを識別。取得不足時は上部マップ見出し＋下部黄色選択を画像照合。右下円形ストックの画像2枚一致で開く。操作バーで覆われた候補は拒否。
- QA100008配布。QA #39882702602 / DEV #39882702711成功、公開APK/更新manifestを検証。main/正式v3.14は維持。ユーザー画像で検出差を確認、実機は未検証。詳細HANDOFF_TORIMA末尾。

## 2026-10-23 Torima：操作バーなし撮影・Drive診断・学習種別分離
- `dev/torima` 最終コード `46831f59fdfd83bf10e21c937064fb838a8e31af`（機能追加e15cca7eを含む）。操作バーを撮影中だけ隠し、座標タップ前にタッチ透過。Drive保存先を初回選択して以後同じ診断ZIPへ書き込み。接続済みDriveの専用フォルダ作成/読み取り確認済み。
- 学習メニューを広告×/スキップとアプリ内MAP/TODAY/STOCK、HOME動画実タップ検証へ分離。アプリ操作は画面/役割/位置を制限し独立ファイルへ保存。広告への流用なし。
- QA100010配布。QA #39884989958 / DEV #39884989947成功。main/正式版維持。実機およびDrive送信から読取までの通し確認は未実施。HANDOFF_TORIMA末尾参照。

## 2026-10-26 Torima：名前付きアプリ内登録・灰色×と画像鮮度の修正
- `dev/torima` 最終コード `e6c933e8a67b23015777df9f731e8c38e56352e3`。Driveに送られた実QA100010のZIPを直接取得/展開でき、共有経路の実機確認が成立。
- 「アプリ内の操作を登録」で用途と操作名・今の画面名・移動先を入力。MAPが未確定でも下部ナビが確認できる場合に上下の画像指紋を登録し再判定。動く地図の背景と固定ボタンを分離して照合。広告の学習には流用しない。
- 灰色×を局所背景とのコントラストで確認し＋/四角を拒否。画像学習と×を同一フレームで更新して証拠の期限切れを防ぐ。2フレーム/鮮度/ガチャ保護維持。
- QA100013配布、QA #40288987738 / DEV #40288987736成功。QA100011構文エラー、100012四角拒否テスト失敗はいずれも配布なし。main/正式v3.14変更なし。修正版の実機は未検証。詳細HANDOFF_TORIMA末尾。
