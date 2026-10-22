# アプリ登録台帳（追加数に上限なし）

新しいアプリが増えたら、統合チャットが**必ず**この表に追加してから開発ブランチを作る。アプリ名・slug・対象packageName・Android applicationIdが重複しないことを確認する。

| slug | 表示名 | 操作対象packageName | 開発APKのapplicationId | 担当ブランチ | 状態 |
|---|---|---|---|---|---|
| torima | トリマ | `jp.co.incrementp.milemobile` | 本番：`com.neru.powlautotest` / debug：`com.neru.powlautotest.dev` / 固定署名QA：`com.neru.powlautotest.qa` | `dev/torima` | 正式v3.14維持、QA100002を専用フィードへ配布済み・上書きデータ保持の端末検証待ち |
| （次のアプリが決まったら追加） | - | - | - | `dev/app-<slug>` | 未着手 |

新規アプリの記入項目:
```
slug:
display_name:
target_package_name:           # 実機で確認、推測禁止
development_application_id:    # 既存と重複禁止
integration_adapter_id:        # 例: reward.<slug>
branch: dev/app-<slug>
module: apps/<slug>/
learning_namespace: <slug>
diagnostics_prefix: <slug>
unit_tests: 未作成 / 合格
phone_test: 未実施 / 実施中 / 合格
owner_chat:
notes:
```

**台帳の行を増やすだけでは新アプリが出来上がるわけではない。** 統合がモジュール登録・アプリID・AccessibilityService衝突回避を整え、担当チャットが回収機能を実装して初めて独立APKになる。

- 公式アプリではないことが分かる表示名にする。
- セキュリティと規約の制約を調べ、問題のある操作を省く。
- 学習済み画像のルールをアプリ間で誤適用しない。
- 実機テスト用アプリは統合版と共存させるが、同時に自動操作を実行しない。
