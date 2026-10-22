# 独立APKモジュールの追加契約

統合担当がアプリ名・実機確認済み対象packageNameを確認し、APPS_REGISTRY.mdへ登録する。未確定アプリは作らない。登録数の上限なし。

1. `apps/<slug>/`をAndroid applicationモジュールとして作成。統合担当がsettingsへ`include(":apps:<slug>")`を追加する。自動ディレクトリ走査ではなく明示登録し、担当外アプリが意図せずビルド対象にならないようにする。
2. 独立applicationIdを登録する。slugのハイフンはAndroid IDにそのまま使用できないため、IDは別途確定し、debug/QAを含め全IDの重複を確認する。
3. FileProviderは`${applicationId}.fileprovider`、表示名は対象アプリ名とDEV/QAを識別できるものにする。対象packageNameと自動化APKのapplicationIdは別物。
4. 共通APIが必要なら`implementation(project(":common"))`。commonはAndroid非依存の契約のみ。既存トリマはまだ接続しない。
5. Actions手動実行のmoduleへ`apps:<slug>`を指定。現行pushビルドの既定対象はappなので、新モジュールのpush自動ビルドへの登録は統合担当へ依頼する。固定QA署名は現在Torimaのみ。新アプリの繰り返し更新用署名は別途整備する。
6. APKのインストールID・Provider・サービスを確認し、正式Torimaと共存、同一署名APKで上書き後の学習保持、対象サービス1つのみの動作を実機検証する。

## commonの初期API

`ScreenObservation`、`SafeAction`、`OperationOutcome`、`CompletionEvidence`、`RewardAppAdapter`を追加。SafeActionは操作提案でありタップを実行しない。対象・画面・観測鮮度の確認だけでは安全実行は完成しない。将来のAndroid hostは操作ロック、実行直前の前面再確認、座標検証、禁止操作、停止/時間上限を実装する必要がある。広告の外部packageを操作する許可は現段階では設けない。

学習namespaceはslugと対象packageの組。保存・移行・診断ZIPへの適用は未実装。既存Torimaのデータは変更しない。
