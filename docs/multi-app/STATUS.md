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
- [ ] ドキュメント・workflowを安定ブランチに反映したことの確認
- [ ] `dev/torima`、`integration/staging`の開発ブランチ作成・確認
- [x] Torima debug `.dev` と固定署名QA `.qa` の独立アプリID・表示名・Actionsビルド設定
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

## 2026-10-22 Torima専用：マップストックQA候補
- `dev/torima`：既存QAの実機基本動作はユーザー報告あり。詳細ZIPなし。
- 移動→歩数→赤丸ありマップ→ストック宝箱2広告回収を初回実装。コード・制約・テストは同ブランチHANDOFF_TORIMA末尾参照。新機能の実機は未確認。
- 正式版・統合コード・CIは変更なし。QAビルド結果は実行後記録。

- 初回マップ実装SHA：`a75e6c0ad651b5f95409d2da63f2a2ae46026ef7`。Actions `39714388563` のdebugビルド・単体テスト成功。親ボタン解決を補強した後続コミットのQAはActionsで確認。
