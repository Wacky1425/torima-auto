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
- `dev/torima`にコミットし、Actions「Development APK (no release)」を利用。テスト署名・appIdの注意は[BRANCH_AND_RELEASE_RULES.md](BRANCH_AND_RELEASE_RULES.md)を参照。
- すべての正式公開をこのチャットで勝手に行わず、統合チャットへリリース候補を渡す。小さな修正でも理由とテスト結果を追記。
- 既存`:app`のapplicationId・署名・保存済みルールを保つ。新アプリを`AutoFlow.kt`に追加しない。
- 統合担当へ共通化したい処理を提案しても、許可前に`common/`を直接書き換えない。

## セッション終了時の引き継ぎ欄（毎回更新）
- 最新コミット：現時点では新ブランチ作成直後のv3.14を起点
- 実機で確かめたこと：なし（移行準備時点）
- 直近の要件：赤丸の実機検証、移動→歩数、広告自動操作・画像学習の信頼性向上
- 新たな診断ZIPの場所・結果：新チャットで受け取ったら追記
- 統合へ渡す変更：コミットSHAと変更ファイル単位で明記
