# 現在のコード分類と段階的な共通化設計

## 原則
共通化候補は**まだTorima依存を含む既存ソース**。クラスをそのまま`common`へコピーしただけでは汎用モジュールにならない。Torima v3.14のアプリID・学習保存先・画面判定を壊さないよう、一度に巨大な`AutoFlow.kt`を解体しない。

## 現行構成（事実）
```text
settings.gradle.kts   -> include(":app") のみ
app/
  build.gradle.kts    -> com.neru.powlautotest, version 3.14
  src/main/java/com/neru/powlautotest/
    AutoFlow.kt                    現在の操作・画面遷移の大部分を保持
    PowlAccessibilityService.kt    Androidアクセシビリティサービス、画面操作、連携
    MainActivity.kt                UIと各種設定
    ScreenCollector.kt             フローティング操作バー
    TeachOverlay.kt                学習時の位置指定・手動タップ確認
    ScreenshotDiagnostics.kt       端末内スクリーンショット解析・診断画像
    LearnedActions.kt              ノード／タップ学習と保存
    VisualLearnedActions.kt        画像テンプレート学習のルール保存・照合
    VisualTemplateModel.kt         小さい画像の類似比較ロジック
    PixelAdCloseDetector.kt        白い×のピクセル検出
    OverlayIconDetector.kt         画像中の閉じるアイコン候補
    GachaSafety.kt                 ガチャを広告と間違えないための安全判定
    RewardTabPolicy.kt             移動／歩数の切り替えと完了条件
    RewardBadgeDetector.kt         移動／歩数の赤丸と選択色
    CollectionTaskPolicy.kt        トリマの「あとN回」を解析
    CollectionRunStore.kt          回収セッション／履歴保存
    HomeSwipeRecoveryPolicy.kt    HOME紫ボタンが反応しないときの上スワイプ
    HomeSettingsRecovery.kt       トリマの強制停止・再起動補助
    HomeTouchProfile.kt           HOME紫ボタンの学習タップ位置
    HomeTouchTrials.kt            HOME操作の複数方式
    HomeTrialJournal.kt           HOME試行の診断
    DiagnosticBundleExporter.kt   ZIP形式の一括診断
    FastAdMode.kt                 広告後の待機切り替え
    TorimaUpdater.kt              Torima Auto専用の更新確認
  src/test/...                    個別のJUnitテスト
.github/workflows/android.yml     mainから正式Torima APKを発行
```

### 所有範囲の分類
| 分類 | クラス例 | 現段階の担当と注意 |
|---|---|---|
| **Torima固有（移動しない）** | RewardTabPolicy, RewardBadgeDetector, CollectionTaskPolicy, Home*, GachaSafety, TorimaUpdater | Torimaチャットで修正。トリマ用IDや文字列を使用 |
| **共通化候補（現状は混在）** | ScreenshotDiagnostics, VisualTemplateModel, VisualLearnedActions, LearnedActions, PixelAdCloseDetector, OverlayIconDetector, ScreenCollector, TeachOverlay, DiagnosticBundleExporter, CollectionRunStore | 統合チャットが汎用interfaceを作ってから段階的に抽出 |
| **分割が必要な巨大クラス** | AutoFlow, PowlAccessibilityService, MainActivity | Torima画面判定・Android操作・復旧・データ保存が絡む。全置換禁止 |
| **既存の安全条件** | PostAdTransitionPolicy, StableAdClosePolicy, LearnedClosePolicy, TeachRulePolicy | 共通化より先に安全な回帰テストを確保 |

現在のコード内のサブクラス・ヘルパーが依存しているパッケージ名、`object`、`internal`スコープ、リソース参照は実際の抽出時に調べること。この資料はファイルの分類でありコンパイル済みの独立モジュールを示すものではない。

## 目標モジュール（統合チャットが段階的に作る）
```text
repo/
  app/                      ← 既存Torima v3.14の安定した移行元。即削除しない
  common/                   ← Android共通のUI/実行/学習/記録インターフェース
  apps/
    torima/                 ← 段階的に移行するトリマ専用アダプター
    <slug-a>/               ← 新しいアプリA固有の実行ロジックと独立APK
    <slug-b>/               ← 新しいアプリB固有の実行ロジックと独立APK
    <slug-n>/               ← アプリは無制限に追加可能
  integrated/               ← アプリ選択・一括実行のための別ID統合APK
  docs/multi-app/           ← すべての開発チャットの引き継ぎ資料
```

- `apps/<slug>`を増やすのが基本。固定の`app2`,`app3`に限定しない。
- 共通`common`は**ルール処理・画像比較・リトライ状態管理・ログモデル**から抽出開始。端末権限や画面のタップ実行は、いずれ共通AccessibilityServiceが所有する。
- アプリ専用アダプターが`targetPackageName`, 検出可能な画面種別, 実行可能な操作, 安全な停止条件, 完了確認を実装。
- 共有`AndroidAccessibilityHost`（計画中）は**どのアプリを操作してよいか**を一元的に確認し、同時に複数アダプターが画面を操作することを防ぐ。
- 統合版はサービスを複数重複起動せず、`IDLE → RUNNING(appX) → VERIFYING → COMPLETE/FAILED → NEXT/STOP`という単一キューを持つ。
- アプリ別の学習データ／設定はパッケージまたはapp keyで分離し、意図せず別アプリの画像ルールを使わない。

### 将来のインターフェース案（未実装）
```kotlin
interface RewardAppAdapter {
    val appId: String                 // torima, slug-a, ...
    val targetPackageName: String     // 操作対象のパッケージ
    fun identify(observation: ScreenObservation): ScreenState
    fun nextAction(state: ScreenState, task: RewardTask): SafeAction?
    fun verifyOutcome(before: ScreenState, after: ScreenState): OperationOutcome
    fun completionEvidence(state: ScreenState, task: RewardTask): CompletionEvidence?
}
```
`SafeAction`は座標だけでなく「なぜその画面を操作してよいか」「保留・停止条件」を持つ。広告・ガチャ・購入等の画面別禁止条件は統合で必須。これは設計案であり、`common`のKotlin interfaceを作成したわけではない。

## 個別APKと学習データ
- **安定Torima:** `applicationId="com.neru.powlautotest"`（現行署名、保存済みルール、アップデートパスを維持）。
- **新アプリ:** 例`com.neru.rewardauto.<slug>`。別アプリID・別表示名・別FileProvider authority。実アプリ対象パッケージも固有。
- **統合版:** 例`com.neru.rewardauto.integrated`。別APK、別保存領域。既存Torimaの学習データはAndroid sandbox間で自動共有されない。**export/import移行機構を別途設計・テストする必要がある**。
- AndroidのDebug署名はビルド端末ごとに変わり得る。上書きテストしたいAPKは署名統一、共存テストしたいAPKはapplicationId分離。
- スクリーンショットと診断ZIPには私的情報が含まれる可能性があるので、必要最小限・端末内処理・手動共有を維持。

## 最初に実装する共通化（推奨順）
1. アプリIDと画面観測・操作結果のinterfaceを抽象化（既存Torimaには薄いAdapterを付ける）。
2. 汎用の`VisualTemplateModel`などAndroid非依存の純粋なクラスを個別の小変更で移動し、元の単体テストを保つ。
3. 診断ZIP・学習保存をアプリID別に分離。
4. 共通のAccessibilityServiceと操作ロックを構成し、**1対象アプリだけ実行**することを端末で確認。
5. 統合マネージャーとタスクキューを追加。後からでも何アプリでもプラグイン登録可能にする。

禁止事項：動作が不明なのに無条件クリックを緩めない。旧`app/`を削除・パッケージ変更しない。新アプリのコードを直接`AutoFlow.kt`へ積み上げて巨大化させない。
