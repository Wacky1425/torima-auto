# Torima Auto: PC不要のアプリ内更新 セットアップ

## まず理解すること
- ソースコード: Private `Wacky1425/torima-auto`
- APK配布専用: Public `Wacky1425/torima-auto-distribution` （新規作成が必要）
- 公開リポジトリにはAPKだけ置く。ソースや秘密鍵は置かない。
- **APKは公開される**。第三者がAPKをダウンロードできる点を了承できない場合、この構成を使用しない。
- 初回導入後はAndroid Studioを使わず更新可能。ただし新コードのGitHub反映は必要。

## 手順1：ZIPの中身を既存GitHubに反映
TorimaAutoフォルダを既存ローカルリポジトリに上書きし、PowerShellで以下。
`git add .`
`git commit -m "Add signed release and in-app updater"`
`git push`
この時点では署名用Secretsが無いのでDebug APKだけビルドされる。

## 手順2：配布用Publicリポジトリ作成
https://github.com/new で `torima-auto-distribution` を Public で作成。README追加は任意。

## 手順3：署名鍵を作成（Windows PowerShell）
Android Studio同梱の keytool.exe またはJDKのkeytoolを使う。
`keytool -genkeypair -v -keystore torima-release.jks -alias torima -keyalg RSA -keysize 3072 -validity 10000`
鍵のパスワードを設定し、**torima-release.jks とパスワードを安全に保管**。GitHubには絶対にcommitしない。
`[Convert]::ToBase64String([IO.File]::ReadAllBytes("torima-release.jks")) | Set-Clipboard`
これでbase64の文字列がクリップボードに入る。

## 手順4：Privateソース側のGitHub Secrets
`https://github.com/Wacky1425/torima-auto/settings/secrets/actions` の New repository secret で以下を登録。
- `TORIMA_KEYSTORE_BASE64` : 手順3でコピーしたbase64文字列
- `TORIMA_STORE_PASSWORD` : キーストアパスワード
- `TORIMA_KEY_ALIAS` : `torima`
- `TORIMA_KEY_PASSWORD` : 鍵パスワード（同じに設定した場合は同じ値）

## 手順5：公開配布用GitHubトークン
GitHub Settings → Developer settings → Personal access tokens → Fine-grained tokens → Generate new token。
Repository access は **Only select repositories** → `torima-auto-distribution`、Repository permissions → Contents: Read and write。
トークンをコピーし、Privateソース側のSecret `TORIMA_DISTRIBUTION_TOKEN` に登録。
**トークンはアプリに埋め込まない。チャットにも貼らない。**

## 手順6：Actionsでビルド・配布
Privateソース側のActions → Android APK build and publish → Run workflow。
成功後、公開配布側の Releases に `v1.0` と `TorimaAuto-release.apk` が出る。
同じv1.0タグを再発行すると失敗するので、次回は `app/build.gradle.kts` のversionCode/versionNameを増やす。

## 手順7：OPPOへ初回インストール
現在のテストアプリは署名が異なる可能性が高いため、一度アンインストールする。
公開配布リポジトリのReleasesから `TorimaAuto-release.apk` をスマホでダウンロードしてインストール。
Androidの「不明なアプリのインストール」の許可が必要な場合は案内に従う。
アクセシビリティサービスは再度有効化が必要。

## 手順8：以後のアプリ内更新
将来v1.1などが公開されたら、アプリの「アプリの更新を確認」を押す。
APKダウンロード後、Androidのインストール確認を承認する。

## 注意
- v1.0自身は最新なので、v1.0をインストールした直後は「最新版です」と出るのが正常。
- APKが公開されることが不都合なら公開配布方式は採用せず、認証付き配布へ切り替える。
- 自動ビルドの動作確認はGitHub Actionsで実施する。ここでは実機インストールは未検証。
