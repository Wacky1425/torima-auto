# 新チャットへ最初に貼る文章（コピペ用）

**新チャットはこのGitHubファイルを読んで作業を始める。** 同じアカウントのチャットでもGitブランチの所有権は自動で共有されないので、ブランチと禁止領域を冒頭で明示する。

## A：トリマ専用開発チャット

> トリマ自動回収アプリの開発を旧チャットから引き継いでください。GitHubリポジトリは `Wacky1425/torima-auto`、専用ブランチは `dev/torima` です。最初にGitHubの `AGENTS.md`、`docs/multi-app/START_HERE.md`、`docs/multi-app/HANDOFF_TORIMA.md`、`docs/multi-app/BRANCH_AND_RELEASE_RULES.md` を読んで、実際の`dev/torima`のコードを確認してください。安定版Torima v3.14のAndroidアプリID、署名、学習データを壊さないでください。対象アプリの移動・歩数・HOME上スワイプ・広告／ガチャ・学習の改良をこのチャットで続けたいです。統合用`common/`と`main`は勝手に変更しないで、ブランチにコミットしたコード・テスト・実機結果を引き継ぎ資料へ書いてください。まず未確認の実機テストと既知の問題を短く整理してください。

## B/C/D/...：追加アプリ専用チャット（アプリごとに複製）

> ポイ活自動回収プロジェクトの**新しいアプリ専用**の開発チャットです。GitHubは `Wacky1425/torima-auto`。最初に`AGENTS.md`、`docs/multi-app/START_HERE.md`、`docs/multi-app/HANDOFF_NEW_APP_TEMPLATE.md`、`docs/multi-app/BRANCH_AND_RELEASE_RULES.md`を読んでください。これから開発する対象アプリは「【ここにアプリ名】」です。ブランチは`dev/app-<slug>`、専用ディレクトリは`apps/<slug>/`、独立したAndroidアプリIDと単独テストAPKを作りたいです。具体名と対象packageNameを確定してから統合チャットの台帳へ登録してください。既存`app/`（Torima）と`main`は変更禁止。まず操作画面や回収ループを実機スクショから確認し、1種類の報酬の自動回収を診断ZIPつきで実装してください。共通化が必要な場合は統合チャットへ提案してください。

同時に2つ以上の追加アプリを作る場合、この文を別チャットへそれぞれ貼る。例：アプリ名Aとアプリ名Bを入れて、別々の`dev/app-<slug>`を使う。3つ目以降も同じ。

## 統合専用チャット

> 複数ポイ活アプリ自動回収プロジェクトの**統合担当**になってください。GitHubは `Wacky1425/torima-auto`、統合作業ブランチは`integration/staging`。最初に`AGENTS.md`、`docs/multi-app/START_HERE.md`、`docs/multi-app/ARCHITECTURE.md`、`docs/multi-app/HANDOFF_INTEGRATION.md`、`docs/multi-app/BRANCH_AND_RELEASE_RULES.md`、`docs/multi-app/STATUS.md`を確認してください。Torima本番v3.14の`com.neru.powlautotest`と学習データを壊さず、今後アプリが何個でも追加できるように、独立APKと固有applicationId、共通エンジン、Gradleモジュール、QAビルド、GitHubブランチ運用を段階的に実装してください。Torimaの新チャット`dev/torima`、各アプリの`dev/app-<slug>`と編集範囲が競合しないよう管理し、検証済み変更だけ統合してください。正式な`main`更新・Releaseは統合担当のみが行います。最初はQA APKの独立インストールと、共通APIの最小骨組みから始めてください。

## チャット間の受け渡し
各チャットは**ブランチ／コミットSHA／変更ファイル／単体テスト／実機テスト／残る問題**をGitHubの[STATUS.md](STATUS.md)と専用HANDOFFへ残す。別チャットが勝手に古いコードへ上書きしないよう、統合する直前に最新SHAを再確認する。
