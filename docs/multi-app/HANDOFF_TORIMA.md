# 新チャットA：Torima Auto 引き継ぎ（2026-10-22）

## 開始コマンド（チャットが最初に理解すること）
**リポジトリ：** https://github.com/Wacky1425/torima-auto
**開発ブランチ：** `dev/torima`（`main`は正式v3.14）
**必読：** [AGENTS.md](../../AGENTS.md) / [START_HERE.md](START_HERE.md) / [ARCHITECTURE.md](ARCHITECTURE.md) / [BRANCH_AND_RELEASE_RULES.md](BRANCH_AND_RELEASE_RULES.md)
**次の作業:** v3.14の実機検証、移動・歩数赤丸判定とHOME上スワイプ、広告／学習の未解決を修正。大規模移行は統合担当が計画。

## いま動いている実装（コード上の実装。実機全パターン検証済みではない）
- Android Kotlin `:app`、`applicationId=com.neru.powlautotest`。操作対象`jp.co.incrementp.milemobile`。
- 現在正式リリース **v3.14**: https://github.com/Wacky1425/torima-auto/releases/tag/v3.14
- トリマの移動タンク動画と歩数の動画回収。どちらも同じ広告→ガチャ→HOMEループ。進捗は「あとN回獲得できます」と紫／灰色ボタンなどで確認。
- 歩数から開始した場合、上部アイコン右上の赤丸と選択中タブのオレンジ色をスクショ2枚で確認。移動赤丸なし＋歩数赤丸ありの時のみ未訪問の移動をスキップ（履歴はSKIPPED）。赤丸不明なら移動タブを訪問して残回数・ボタンを確認。
- HOMEの紫ボタンが1回のタップで進まなければ短い**上方向スワイプ**を1回試行し、ボタンの現在位置を再取得して再タップ。その後も進まなければAndroidアプリ情報からの強制停止・再起動を試す。
- 画像認識できる広告の×、複数フレームでの位置照合、ユーザー操作を学習したノード／画像ルール。
- 学習は透明膜上の位置指定（第一タップ）→膜と操作バーを除去・タッチ透過→ユーザー自身の指がTorimaへ直接届く第二タップ、の2段階。結果は画面遷移／TYPE_VIEW_CLICKEDから判定し自動登録。学習失敗で保留記録を増やさない。
- 操作バーは開始・停止・学習の3ボタン、長押し移動、詳細を折りたたみ。
- 診断ZIP一括出力、Google PlayからTorimaへの復帰、学習ルール、強制停止UI試行ログ。
- 実行全体の上限3時間を維持。実際の入力完了＝報酬獲得完了とは扱わない。

## 調べるべき主なソース
| ファイル | 見る理由 |
|---|---|
| `app/src/main/java/com/neru/powlautotest/AutoFlow.kt` | 現行の画面判定・遷移・広告閉じる・タブ変更・実行制御の中心。大規模な置換禁止 |
| `RewardTabPolicy.kt`, `RewardBadgeDetector.kt` | 移動と歩数、赤丸、灰色ボタンと切り替え |
| `HomeSwipeRecoveryPolicy.kt`, `HomeRecoveryPolicy.kt`, `HomeSettingsRecovery.kt` | 初回失敗時の上スワイプと最後の再起動 |
| `ScreenshotDiagnostics.kt`, `VisualLearnedActions.kt`, `VisualTemplateModel.kt` | スクショ解析と画像学習 |
| `TeachOverlay.kt`, `ScreenCollector.kt` | 2段階指学習・フローティングバー・タッチ透過 |
| `GachaSafety.kt`, `StableAdClosePolicy.kt`, `PostAdTransitionPolicy.kt` | 広告×とガチャ×の混同防止 |
| `CollectionRunStore.kt`, `CollectionTaskPolicy.kt` | 進捗と終了根拠、診断記録 |
| `DiagnosticBundleExporter.kt` | 1つのZIPで全体の診断 |

過去のバージョンごとの判断は`docs/`下、特に[赤丸v3.14](../reward-badges-v3.14-ja.md)と[HOME指学習v3.12.2](../home-swipe-physical-teaching-v3.12.2-ja.md)。

## 既知の問題・要実機確認
1. **赤丸画像認識の位置・色は、ユーザーが提示したスクショをもとに作成。OPPO実機の端末解像度・広告表示中・タブごとの状態で未検証。** 2枚の一致が実機で正しいかZIPで追う。
2. 赤丸がないことは必ずしも回収完了の直接証明ではない。今の実装では移動はSKIPPED（未訪問）として履歴へ残す。この区別を壊さない。
3. スワイプ成功・ジェスチャー完了・紫ボタンの処理成功は別。HOMEで指なら進むのに自動で進まない場合は、`HomeTrialJournal`の試行結果を確認。
4. 画像学習の類似率を緩めすぎると、広告終了ではない×やガチャの読み込みを押す可能性がある。
5. v3.12.2の2段階学習でAccessibilityクリックイベントが拾えない広告もあり得る。学習時の実画面変化確認を優先し、全てを「成功」と表示しない。
6. v3.10以来、**高速実験モード**は広告終了後の4秒保護を切る設定を持つ。ガチャ誤キャンセルに注意。
7. 古いメモ／ロードマップの「タンクのみ対応」は**更新されていない説明**。現行v3.14のソースと実機ZIPを優先する。
8. ビルドに成功したことと端末実機での成功は区別する。

## 開発フロー
- `dev/torima`にコミットし、Actions「Development APK (no release)」を利用。**最初の動作確認ビルドは成功**：[Actions run #39701993971](https://github.com/Wacky1425/torima-auto/actions/runs/39701993971)。同runのArtifacts内の`TorimaAuto-QA.apk`（`com.neru.powlautotest.qa`）は既存署名で作成済み。OPPO実機での独立インストールは未検証。
- すべての正式公開をこのチャットで勝手に行わず、統合チャットへリリース候補を渡す。小さな修正でも理由とテスト結果を追記。
- 既存`:app`のapplicationId・署名・保存済みルールを保つ。新アプリを`AutoFlow.kt`に追加しない。
- 統合担当へ共通化したい処理を提案しても、許可前に`common/`を直接書き換えない。

## セッション終了時の引き継ぎ欄（毎回更新）
- 新ブランチ起点：`dev/torima` のコミット `2326e780c673ee7b4fdaed2f0df2d76f5c1a4bf7`。この初回コミットはQA検証案内のみでTorima回収ロジックを変更していない。
- 実機で確かめたこと：なし（移行準備時点）
- 直近の要件：赤丸の実機検証、移動→歩数、広告自動操作・画像学習の信頼性向上
- 新たな診断ZIPの場所・結果：新チャットで受け取ったら追記
- 統合へ渡す変更：コミットSHAと変更ファイル単位で明記

## 2026-10-22 Torimaチャット：マップ宝箱の初回QA実装
- 対象ブランチ：`dev/torima`。基準SHA：`2326e780c673ee7b4fdaed2f0df2d76f5c1a4bf7`。この節と同じコミットの実装を参照。
- ユーザー実機報告：既存QA版をインストール後「動いた動いた」。詳細な回収回数・ZIPは未受領。全機能検証済みとは扱わない。
- 新要件：移動→歩数の後、下部マップタブに赤丸があればマップ→ストック。必要なら「今日」を選択。ストック済み宝箱を全件回収。動画40マイル→「宝箱の底のおもちゃをチェック」で2本目の広告。Wチャンスなし。
- 変更：`AutoFlow.kt`、`MapStockPolicy.kt`、`RewardBadgeDetector.kt`、`PostAdTransitionPolicy.kt`、`MapStockPolicyTest.kt`。本番ID・署名・学習形式・CIは変更なし。
- 実装：赤丸を端末内スクショ2枚で確認。未確認時はマップSKIPPED（未訪問）を記録。宝箱広告は既存広告閉じる処理を再利用し、ガチャ操作を禁止。残数減少と0個の複数回確認で進捗・終了を確認。全体3時間・操作上限・段階別2分待機上限あり。
- テスト：残数/最大数の区別、未知の残数と0の区別、残数減少、赤丸と他タブ/赤い帯の区別、広告後保護の回帰テストを追加。Actions結果は実行後追記。
- 新機能の実機結果：未確認。スクショの見た目は確認済みだが、Accessibilityノードの行構造と2本目広告後の最終画面は未受領。固定座標での行タップや不明な×は実装しない。候補不明時は停止し診断ZIPで確認。
- 既知の制限：赤丸領域は提供画面の比率に依存し実機検証が必要。「今日」または「ストック」の文字ノードがなければ遷移しない。宝箱行の検出はストック個数の下の行構造に依存。2本目後は一覧復帰、または広告が終了して確認できた「このまま受け取る」のみ対応し、未知画面は未完了停止。
- 次：QA APKを上書きインストールし、移動→歩数→マップを確認。マップで止まった場合は停止画面と診断ZIPを受領してノードを調整。

### ビルド追記
- 初回実装コミット：`a75e6c0ad651b5f95409d2da63f2a2ae46026ef7`。Actions `39714388563` のdebugビルド＋単体テストは成功（署名QA工程は確認中）。
- 後続の小修正：マップ・ストックの文字ノードから、同じ範囲にあるクリック可能な親ボタンも解決。マップは下部ナビ、ストックは地図右側に限定し、見出しとの混同を防止。最終QA結果はこの追記と同じコミットのActionsを参照。

- 最終QAコードSHA：`2ef51f2a43ad277cc105380552c6314d972e4122`。Actions [39714575369](https://github.com/Wacky1425/torima-auto/actions/runs/39714575369) でdebugビルド・単体テスト・署名QA APK生成すべて成功。Artifact `12398087850`。新マップ機能の実機検証は未実施。提供3画面の赤丸を縮小画像で確認（PC上端を除いた場合も検出）。

## QA配布方式の更新（統合担当、2026-10-22）
アプリ内QA更新は[QA_UPDATES.md](QA_UPDATES.md)を参照。`qa-publish.yml`がテスト済み固定署名QAを専用プレリリースへ公開し、通常開発workflowはDEV Artifactsのみを生成する構成へ変更した。正式版`releases/latest`は維持。専用チャットは統合ブランチの本変更を取り込んでから使用し、更新処理・app Gradle・共有module/CI以外の専用ロジックを上書きしない。旧QAの初回上書きとデータ保持は実機確認待ち。

## QA更新・配布検証完了（2026-10-22、端末確認は未実施）
- コード確定SHA: `72563bb34342e35631520afdc37d0df3ec14cde0`。作業ブランチ`integration/staging`。以下の資料更新コミットはコード/配布APKを変更しない。
- [QA更新版100002のActions #39723776583](https://github.com/Wacky1425/torima-auto/actions/runs/39723776583)成功。共通API/更新ライブラリ/Torima QA/Debugテスト、固定署名、APK実ID・版番号、公開APKとmanifestの匿名取得・ハッシュを検証。
- [DEV Actions #39723776584](https://github.com/Wacky1425/torima-auto/actions/runs/39723776584)成功。
- 更新先は`qa-channel-torima/manifest.json`。現在版`3.14-qa.100002`、APK `torima-qa-100002.apk`（2,882,384 bytes、SHA-256 `0df040321b5542a6c1907e6206d11a7985c24587a226da1259b9018758dfa8ca`）。[版別配布ページ](https://github.com/Wacky1425/torima-auto/releases/tag/qa-torima-100002)でasset実在確認。
- 初回導入用[QA 100001 APK](https://github.com/Wacky1425/torima-auto/releases/download/qa-torima-100001/torima-qa-100001.apk)を残し、アプリ内で100002へ上書きする実機検証が可能。100001/100002とも既存と同じQA ID/固定署名。
- 正式版`releases/latest`はv3.14、Release ID `420174934`、APK asset ID `664167534`で前後一致。mainと正式APKは変更なし。
- 専用チャット取り込み用[draft PR #1](https://github.com/Wacky1425/torima-auto/pull/1)を用意。`dev/torima`へは直接push/mergeしない。担当はPRの共通API/更新module/Gradle/CI/QA接続をまとめて取り込み、専用開発を続ける。取り込み前の旧dev workflowから出る47のQAを新QAへ戻そうとしない。
- 未確認: OPPO Reno11 Aの実際のインストール、画像/ノード学習・設定保持、権限画面からの復帰、キャンセル後の再試行。診断ZIP未取得。次は[QA_UPDATES.md](QA_UPDATES.md)の実機手順。回収・学習操作はテストAPKの端末確認結果を待つ。

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

## 2026-10-23 Torima担当：学習位置の×出現前タップを防ぐ
- ブランチ `dev/torima`、コードSHA `49fdb6f7bf6d067c86825e4ec202a8fe921c1f5c`。main/共通module/CIの変更なし。既存学習ファイル・QA ID/署名を維持。
- 根拠：学習ノードのImageViewは×が描画される前にも同じbounds/classで存在し得る。画面一致だけではglyphの存在を保証しない。
- 対策：学習アイコン/画像等の閉じる候補は、操作前に実画面から学習位置近傍の明暗両極性の×形状を確認。500ms〜4秒間隔の2画像が必要、最終確認から900msを超えた証拠は無効。消失/撮影失敗/別ルールは許可しない。通常操作・再試行より前に共通ガードを置き、2分未検出で停止。速度設定で省略しない。既存広告証拠/ガチャ誤操作防止も維持。
- 学習ルールの管理（個別ON/OFF）を追加。チェックを外したノード/画像ルールは自動照合から除外。元の学習データを削除せずSharedPreferencesへ無効キーを保存。ノードと画像の両方が同じ位置を学習している場合は両方をOFFにする。
- 診断ZIPに `11_学習ルール設定.json` / `12_学習位置の表示確認.json` を追加。
- 変更：AutoFlow、LearnedActions、VisualLearnedActions、ScreenshotDiagnostics、PowlAccessibilityService、MainActivity、activity_main、DiagnosticBundleExporter、新規LearnedRuleManager/LearnedXPresence/回帰テスト。
- 回帰テスト：空背景/塗り四角/＋を拒否、小さい白黒×の局所検出、1画像のみ/古い証拠/別ルール/消失を拒否。ローカルdiff check合格。ローカルGradleは配布ホストへのネットワーク制限で起動不可のためActionsで実行。
- 制限：×の描画を認識できない広告や文字のみの閉じるは保守的に待機し得る。OPPO実機では未検証。次は更新後、×出現前に押さず、出現後に閉じるか確認。異常時は最新ZIPの12番と画面を確認する。

### 配布確認
- QA100006（3.14-qa.100006）を配布。QA Actions #39880406207、DEV #39880406118成功。共通/更新ライブラリ/Torima QA・Debugテスト、固定署名ビルド、公開配布検証が通過。
- 配布asset 2,892,580 bytes、SHA-256 `02527916c8bd1c8b79dfb87b2a26e7ac680b098a253eeb9089f8545811d4bac7`。公開manifestを匿名取得しversionCode100006/sourceSha49fdb6fと一致確認。
- 正式版releases/latestはv3.14/Release ID420174934を維持。実機未検証。ユーザーはQA内の更新、またはqa-torima-100006のAPKを上書きインストールし、×表示前待機→表示後の回収を確認する。

## 2026-10-23 Torima担当：マップ「今日」は表示時のみ操作
- ブランチ `dev/torima`、コードSHA `f073c43c9feb51ed069838b0318774e394282bb1`。ユーザー確認仕様：「今日」がある場合は押し、ない場合はすでに今日なので右下「ストック」へ直接進む。
- 旧処理はストック候補がないと「今日」を必須探索し、ボタンなしで待機していた。表示テキストからTODAY/STOCKを分岐し、「今日」なしでTODAY操作を要求しない。両方表示時は「今日」を優先して次画面を再判定。
- 対象：AutoFlow、MapStockPolicy、MapStockPolicyTest。分岐と「今日」消失後のストック移行をテスト追加。diff check合格。Android実行はActionsで確認。
- ストック自体を一意に検出できない時は座標を推測せず待機し、既存2分上限で未完了停止。残数0/減少確認、広告/学習のガードは維持。学習データ・QA ID/署名・main/共通/CIの変更なし。実機未検証。

- 配布確認：QA100007（3.14-qa.100007）公開。QA Actions #39881505467、DEV #39881505481成功。共通/更新/QA・Debug単体テストと固定署名APKビルド・匿名配布検証が通過。公開asset 2,893,076 bytes。正式版latestはv3.14/Release ID420174934のまま。実機確認は未実施。次は「今日」あり/なし両経路からストック一覧に進むか確認。

## 2026-10-23 Torima担当：HOME/MAPの上下判定とストック画像操作
- ブランチ `dev/torima`、コードSHA `2909291be08072717531a4190ed2b24e3b49a87a`。ユーザー提供scrcpy画像2枚を確認。上部「マップ」、下部「マイル／マップ」と黄色いマップ選択、右下の円形「ストック」宝箱が見える。「現在地」は常時取得できる保証がなく、必須から除外。
- MainScreenPolicyは上部3〜24%と下部85%以上のラベル/選択状態でHOME/MAP/UNKNOWNを判定。中央の地図/広告文言は使わない。両タブのラベルが単に存在するだけでは選択済みとしない。矛盾時はUNKNOWN。下部HOMEラベルは「マイル」にも対応。
- ノードが不足したマップ入口では実画像で「上部マップ見出し＋下部黄色選択」を追加照合。古い画像でHOME判定を上書きせず、キャッシュは2.5秒限定。
- ストックの文字を押せるノードとして取得できない場合、提示された円形宝箱だけの16x16色特徴を右下限定/複数サイズで検索。地図や住所の画像/座標は保存しない。上部/下部のMAP画像証拠＋2画像の宝箱一致、最終確認900ms以内でだけジェスチャー。マップ上の通常宝箱や覆われたアイコンは対象外。
- ユーザー画像の1枚目は操作バーがストックに重なる。2枚目は見える。ローカルで同じ照合式を適用し、1枚目ストックなし、2枚目あり（平均誤差24）、両画像のMAP見出し平均誤差8/黄色163pxを確認。実機での入力成功は未確認。
- MAPを開いた状態から開始した場合はストック回収から再開（移動/歩数を完了扱いにしない）。通常HOMEからは従来の移動→歩数→赤丸→マップ順を維持。
- 新規MainScreenPolicy/MapStockIconDetectorと単体テスト、AutoFlow/ScreenshotDiagnostics/PowlAccessibilityService/DiagnosticBundleExporterを変更。診断ZIPに13_HOME_MAP判定.jsonを追加。
- テスト：中央にマップ文字があってもHOMEを維持、下部ラベルだけではMAP判定不可、上下矛盾/ナビなし広告を拒否、右下のみ宝箱照合、隠れ/白背景を拒否。diff check合格。Android結果はActions確認後追記。
- 制限：異なるレイアウト/描画倍率/アイコンデザインでは保守的に未検出となり得る。最新ZIPの13番と画面で追跡。個人の地図画像は公開Gitへ保存していない。main/共通module/CI/QA署名/保存済み学習データは変更なし。

- 配布確認：QA100008（3.14-qa.100008）公開。QA Actions #39882702602、DEV #39882702711成功。共通/更新/QA・Debug単体テスト、固定署名APKビルド/匿名配布検証通過。公開APK 2,902,752 bytes。正式latestはv3.14/Release ID420174934のまま。実機タップは未確認。次は操作バーをストックに重ねず、マップからストック一覧へ進むか確認。

## 2026-10-23 Torima担当：操作バーなし撮影・Drive送信・学習の分離
- `dev/torima`。機能コード `e15cca7e0b552ffa7ef0cfcef6941f9ffabea35d`、最終補強 `46831f59fdfd83bf10e21c937064fb838a8e31af`。main/共通module/CIの変更なし。QA署名/ID/既存学習データを維持。
- 操作バー：画像解析と診断保存はバーをINVISIBLEにするlease取得後200ms待って撮影。完了/拒否/例外/3秒の解析撮影タイムアウト/15秒の診断タイムアウトで復元。二重解放防止、学習中の非表示状態を終了しない。撮影中はAutoFlowの入力を待機。ストック等の座標操作前はWindowManagerのNOT_TOUCHABLEを既存prepareHomeTouchで設定し、復元後のバーに入力が吸われる問題も防ぐ。
- 状態文「バーを移動してください」をやめ、未検出/ソース確認/撮影失敗を診断へ記録。マップ起動時のノード情報が不足しても下部ナビが存在する場合は画像判定を試す。UNKNOWNだけでタップを許可しない。
- Drive：Android ACTION_CREATE_DOCUMENTで初回だけGoogle Drive上の `torima-latest-diagnostics.zip` を選択。永続URI権限で、以後アプリの「Driveに診断を送信」から同じファイルへ書き込み。端末内保存プロバイダは拒否、write権限必須。権限取り消し/ファイル削除等は失敗表示し再設定。APKにGoogle/GitHubトークンやユーザーのDrive URLを埋め込まない。任意の自動送信/公開共有は行わず、明示ボタンでのみ送信。
- ユーザーはChatGPT共有画面案を取り下げ、Driveへの直接送信を明示希望。接続済みDriveに専用Torima Diagnosticsフォルダを作り、connectorで空フォルダの読み取りまで検証。フォルダURL/IDは公開資料へ記載しない。ユーザーが同じGoogleアカウントを選び初回保存後、こちらで最新ZIPの日時/実版を読む。現時点でスマホからの書き込み/クラウド反映/ZIP読み取りは未検証。Drive providerへ書き込み完了とクラウド同期完了は区別して表示。
- 学習：操作バー「学習」から広告の×/スキップ、アプリ内MAP/TODAY/STOCK、HOME動画の実タップ検証を明示選択。広告学習はHOME/MAP判定時に拒否。アプリ内MAP/TODAY/STOCKは位置を1回指定するだけ（合成タップなし）。広告学習の従来2段階とは別。ソース画面と役割を確認し、小さい画像特徴を `torima_app_navigation_rules.json` へ独立保存。広告のLearntActions/VisualLearnedActionsへ混ぜない。HOME動画は従来の成功検証方式を維持。
- 自動使用：対応するmapPhaseでだけ登録画像を照合。HOME→MAP、MAP→TODAY/STOCKのソース制限/位置制限、現在の2フレームと900ms鮮度を要求。広告の×検出条件は流用しない。登録済みアプリ操作だけの削除あり。従来の広告ルールは移行/自動削除しない。
- 新規AppNavigationRules/AppNavigationTeachOverlay/MainScreenReader/DriveDiagnosticTarget/AppNavigationRulesTest。AutoFlow、ScreenCollector、PowlAccessibilityService、ScreenshotDiagnostics、MainActivity、MapStockIconDetector、DiagnosticBundleExporter、activity_mainを変更。ZIPに14_アプリ内操作の登録.json、15_Drive送信履歴.jsonを追加。ZIP生成を同期化。
- 回帰テスト：役割別ソースとMAP/ストックの位置制限、広告隅/地図中央の拒否、Drive以外/類似authorityの拒否。既存HOME/MAP/画像/広告ガードのテストもActions実行。ローカルdiff check・配布Pythonテスト2件合格。e15のDEV/QA Actions成功（QA100009）。最終468の結果は以下へ追記。
- 端末未検証：OPPOのバー描画消去/復元、役割別登録、persistable Drive URIの書き込み、Drive providerオンライン同期、フォルダ上の最新ZIP読取。次は新版導入→Drive保存先指定→アプリ内ストック登録→開始→診断送信、ユーザー「送った」でconnectorから検証。

### 最終配布確認
- QA100010（3.14-qa.100010）、コード46831f59を配布。QA Actions #39884989958、DEV #39884989947成功。共通/更新ライブラリ/Torima QA・Debug単体テスト・固定署名ビルド・匿名配布検証通過。公開APK asset 2,912,736 bytes。公開manifestを匿名取得し実versionCode/sourceSha一致確認。正式latestはv3.14/Release ID420174934を維持。
- 実機の初回Drive保存先指定・永続URI上書き・オンライン同期とconnectorの最新ZIP取得はユーザー操作待ち。実機成功済みとは扱わない。テスト済みコードと配布/フォルダ読み取り確認まで完了。

## 2026-10-26 Torima担当：Drive実機診断・名前付きアプリ内登録
- ブランチ `dev/torima`、コード `cd2efb5b146b061b4bd4fa41a93637083ea90c7b`。Driveの専用フォルダにユーザーが送信したZIPをconnectorで取得し、6,052,562 bytes/manifest versionCode100010・QA ID・日時を確認して展開。スマホ→Drive→このチャットの診断取得が実際に成立。非公開のまま。Drive URL/IDやスクショ/生ログを公開Gitへ保存しない。
- 診断事実：アプリ内登録JSONは空。MAP判定がUNKNOWNのままsourceConfirmed=falseを繰り返す記録あり（当該記録は10/24）。10/26の最新実行は移動13→11回の減少、ガチャ進行とPlay復旧あり。直近の操作履歴80件には過去の全広告失敗/マップ失敗の文脈が残っていない。すべて同一原因と断定しない。
- 広告：未表示の×を拒否していた場面に加え、灰色×のある広告画像とUNKNOWNも確認。従来glyph検出は絶対輝度170以上を要求し灰色を落とすため、局所背景との輝度差と4本の対角線で判定（+、四角、単色は拒否）。教えた小領域内でのみ探索し、2フレーム/鮮度/広告とガチャ保護は維持。
- 画像学習：独立したXプローブが元の画像学習一致を3秒で期限切れにし、2フレームの証拠が途切れ得る順序を確認。同じ撮影画像でXと画像学習を再照合し、ルールID・位置が一致した時だけ同一フレームの最新証拠を更新。保存座標/グレー画像540pxと実画面座標の変換を維持。X撮影でも操作バーを除外。
- アプリ内登録：学習メニューに「アプリ内の操作を登録（名前・画面を指定）」を独立表示。用途（MAP/TODAY/STOCK）と操作名・今の画面名・移動先を入力して位置を1回選ぶ。文字ラベルだけでは座標を再生しない。HOME動画の実タップ検証、広告の2段階学習は別入口のまま。既存登録は後方互換で既定名を使う。
- MAP自動判定が未確定でも、現在の下部HOME/MAPナビを確認でき、選択状態/ヘッダにHOME等との矛盾がない場合は、ユーザーが指定した用途の画面として上部/下部の小さい指紋を登録。以後はナビの存在と両指紋の一致で再確認。既知のHOMEや広告を名前だけでMAPへ上書きしない。現在サポートする自動実行用途は3種類であり、任意の未実装画面遷移エンジンではない。
- 固定ボタン照合では変動する地図の周辺contextを必須にせず、確認済みソース画面＋ボタン近傍の強い一致（850/1000以上、輝度/特徴量条件あり）を要求。広告の画像照合閾値は変更しない。
- 新規NavigationVisualPolicyとテスト、AppNavigationRules/AppNavigationTeachOverlay/AutoFlow/DiagnosticBundleExporter/LearnedXPresence/MainScreenPolicy/PowlAccessibilityService/ScreenshotDiagnosticsを変更。ZIPに16_アプリ内登録の診断.json追加。マップ未検出を5秒後/最大60秒間隔で早めに撮影し、次のZIPに根拠を残す。
- テスト追加：灰色X/ずれ/単色、名前入力/空欄既定、上部下部と変動する地図の独立性、ボタン消失の拒否。diff check合格。Android Actions結果は追記。既存main/署名/ID/Drive保存先・共通module/CIは変更なし。実機修正版は未検証。
- 次：新版→MAPで「学習」→名前付きアプリ内操作（用途ストック）を登録→自動開始→失敗時Drive送信。今回すべての広告が解消したとは主張しない。

- 補足：最終コードは `0c239e46b9f6223a56b4a052fd38c78fb0d3762e`。同一画像プローブの座標変換に括弧が1個多い構文エラーをActionsで検出し修正。QA100011はビルド失敗で配布なし。
- 実データの灰色×（診断内2026-10-26の広告画像）へ両判定式を同一座標で適用し、従来false/修正後trueを確認。原画像は公開Gitへ保存していない。これは実機の自動タップ成功とは別の検証。

- 最終補強コード `e6c933e8a67b23015777df9f731e8c38e56352e3`。QA100012の単体テストが、局所輝度の平均だけでは四角の周囲をXと誤認し得る点を検出し配布停止。縦横4方向それぞれよりも対角線が明確に強いことを要求する形へ修正。元の＋/四角拒否テストを維持。実画像の灰色X=true、同一条件の＋/四角=falseをローカル検証。QA100011/100012はいずれも配布されていない。

### 配布確認（2026-10-26）
- QA100013（3.14-qa.100013）公開。QA Actions #40288987738 / DEV #40288987736成功。共通/更新/Torima QA・Debug単体テストと固定署名ビルド・匿名配布検証通過。公開asset 2,918,012 bytes。公開manifestを匿名取得しversionCode100013とsourceSha e6c933e8一致を確認。正式latestはv3.14/Release ID420174934を維持。
- 灰色×とnamed navigationの修正版は端末未検証。Driveの送信→取得は今回実機データで成功確認。今後は同じ保存先へ送信してもらい、manifest日時と版を読んでから診断する。
