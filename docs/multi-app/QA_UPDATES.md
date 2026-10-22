# QAアプリ内更新・配布契約

## 実装と配布先

- 共通Android library `:qa-updater`（`common/qa-updater/`）。更新確認→変更内容表示→ダウンロード→検証→Android確認画面。
- Torima接続変更は`TorimaUpdater.kt`のQA分岐と`app/build.gradle.kts`のみ。既存MainActivityの「更新を確認」を使用。AutoFlow等は書き換えず、`dev/torima`の基準`b7899db`の変更を履歴つきで取り込んでQA機能を巻き戻さない。マップ回収は引き続き実機確認待ち。
- 登録台帳: `qa/apps.json`。Torima QA IDは`com.neru.powlautotest.qa`、署名は既存TORIMA Secrets。既存の正式ID・署名・保存先は変更しない。
- 公開QAフィード: `https://github.com/Wacky1425/torima-auto/releases/download/qa-channel-torima/manifest.json`
- 版別APK: `qa-torima-<versionCode>`プレリリースの`torima-qa-<versionCode>.apk`。上書きしない。変更内容・ソースSHAをフィードに含める。
- チャンネルごとのプレリリース`qa-channel-<appKey>`はmanifestのみを保持し、すべてのビルド・テスト・APK署名/実ID検証・匿名ダウンロード検証後に最後に差し替える。短い切替中の404/通信失敗は更新失敗として再試行できる。既存インストールやデータは変更しない。
- すべて`--prerelease --latest=false`。正式版`releases/latest`と`TorimaAuto-release.apk`には触れない。QAのAPK/変更内容は公開情報。署名鍵はActions Secrets内、CIのGITHUB_TOKENはCI内のみ。APKにトークンは含めない。

## ビルドと版番号

`.github/workflows/qa-publish.yml`は、採用済みブランチのコードpushまたは手動実行でQAをテスト・ビルドして配布する。アプリの更新ボタンは公開HTTPSを読むだけ。ArtifactsへのログインやGitHub API呼び出しは不要。

QAのversionCodeはこのworkflowの`100000 + github.run_number`。複数アプリでも同じカウンターを使用し、各QAの更新順序を維持する。QA出力のみをAndroid Components APIで上書きし、正式版47/3.14を変更しない。workflowの削除・再作成やカウンター初期化は禁止。再実行で同じ版のAPKが変われば公開を拒否し、新しいrunを作る。既存フィードより古い版番号への切替を拒否する。

従来`Development APK`はDEV成果物のみ。署名QAとそのArtifactsは`Publish tested QA update`へ統一し、古い47のQAを新しいQAへ上書きしない。`dev/torima`には所有チャットが本変更を取り込んでから使用する。devブランチへ統合担当が直接pushしない。

公開前に`:common:test`、`:qa-updater:testReleaseUnitTest`、対象アプリ`:testQaUnitTest`/`:testDebugUnitTest`を実行。署名付きQAをapksignerで検証し、aaptで実際のアプリID/版番号/minSdkを検査。キー証明書との一致と公開URLの匿名取得/ハッシュ一致後にフィードを更新する。失敗したrunは更新情報に載せない。

## アプリ側の拒否条件

1. インストール中のID、登録ID、manifestのIDが完全一致。appKeyとchannel=qaも一致。
2. APK URLは登録repo内の当該appKey/版番号の版別APKのみ。HTTPS、許可ホストへのHTTPSリダイレクトのみ。
3. 旧版/同版はインストールしない。対応Android、サイズ上限150MiB、64KiBのmanifest上限、読取制限あり。
4. SHA-256とバイト数一致後、APK内のID/版番号/版名と端末にインストール中の署名証明書集合を照合する。
5. Androidが最終署名検証とユーザー確認を行う。署名不一致時にアンインストールを案内しない。

QA/正式/DEVは同じ配布署名でも別IDなので混同しない。別アプリの更新先へのフォールバックをしない。端末の学習・設定のコピー/消去/移行は一切実行しない。アプリ更新によるAndroidのプロセス停止は通常通り発生するため、自動回収を止めて更新する。

## OPPO Reno11 A：実機確認手順（未実施）

旧QAにはアプリ内更新がないため、最初の1回のみ新QA APKをブラウザーまたはActions Artifactsからダウンロードして**同じQAへ上書き**する。アンインストール・ストレージ消去・本番APKの選択はしない。

1. 更新前のQAの版番号/設定値を控える。ノード/画像学習の件数と「学習済み操作をコピー」の内容を記録し、診断ZIPを端末で保存する。個人情報をGitへコミットしない。本番Torimaの設定も変更していないことを確認する。
2. QAの自動回収を停止する。初回QA APKのAndroid画面が「Torima Auto QA」の**更新**であることを確認し、インストールする。新規の別アプリとして出る、署名不一致、アンインストール要求なら中止して報告する。
3. QAを開き、版番号が増えたこと、設定値・ノード/画像学習件数・内容・診断画像が残ったことを確認する。正式Torimaが別に残っていることを確認する。
4. 次のQA（より大きい版番号）をCIでビルド・テストして配布する。QA内の「更新を確認」→変更内容表示→「ダウンロードして更新」。最新版が配布済みなら「最新版」と表示される。
5. インストール権限の設定画面が出たら**Torima Auto QA**の許可をONにし、戻ってもう一度更新確認→ダウンロードして更新。取得済みAPKはハッシュ再確認後に再利用する。
6. Androidの確認で一度キャンセルし、学習/設定が残って再試行できることを確認。その後更新を承認し、開き直して手順3を再確認する。
7. 更新後にノード/画像学習を使う既知の操作を1件実行し、件数だけでなく使用できることを確認する。Accessibilityが停止/無効ならQAだけを再接続する。
8. 機内モード/配布前404では既存データが残り、通信復旧後に再試行できることを確認する。

ADBを使用する場合の読み取り例:
```sh
adb shell dumpsys package com.neru.powlautotest.qa
adb shell dumpsys package com.neru.powlautotest
```
`versionCode`、`versionName`、`firstInstallTime`を前後で比較する。データ保持の証拠には実際の学習出力/設定の前後比較を使う。`adb uninstall`や`pm clear`は実行しない。

報告項目: 旧/新QA版番号、インストールが更新になったか、学習件数と内容の保持、設定値、正式版共存、更新キャンセル/通信失敗、権限からの復帰、診断ZIPの有無。

## 新アプリへの追加（上限なし）

1. 統合担当がAPPS_REGISTRYと`qa/apps.json`に登録する。新appKey、固有`.qa` ID、Gradleモジュール、署名Secrets prefix、minSdk、許可ブランチを確定する。
2. モジュールへ`implementation(project(":qa-updater"))`、既存固定署名のqa buildType、QA限定の同じ版番号設定を追加。prefixを指定しても環境変数名は`TORIMA_KEYSTORE`等を共通ビルド契約として受け取る（値は各アプリ固有Secrets）。
3. FileProvider `${applicationId}.fileprovider`にcache `updates/`を公開。更新ボタンから登録値で`PublicQaUpdater(activity, status, QaFeedConfig(...)).check()`を呼ぶ。
4. QA workflowを対象アプリのブランチで手動実行しapp_keyを指定。自動push対応が必要ならworkflowの対象ブランチとキー選択を統合担当が追加する。現行自動pushはTorimaのみ。
5. 固有の`qa-channel-<appKey>`と`qa-<appKey>-<versionCode>`で配布し、既存QAとの共存/上書きデータ保持を実機検証する。
