# Torima Auto: 1リポジトリでアプリ内更新を使うまで

## 今回の構成

- 使うGitHubリポジトリは **`Wacky1425/torima-auto` ひとつ**。
- **Public** にする（コードとAPKの両方が公開されます）。
- 同じリポジトリの `main` → GitHub Actionsで署名付きAPKをビルド → 同じリポジトリの **Releases** に配置。
- アプリはそのReleasesから最新版を確認して、Androidのインストール確認画面を開きます。
- 配布用の2個目のリポジトリ、Personal Access Token、`TORIMA_DISTRIBUTION_TOKEN` は**不要**です。

## 手順1: 今あるリポジトリをPublicに変更

1. `https://github.com/Wacky1425/torima-auto/settings` を開く。
2. 下へスクロールして `Danger Zone` → `Change repository visibility` → `Change to public`。
3. 公開されるファイルと**過去のコミット履歴**に秘密鍵・パスワード・個人情報がないことを確認し、公開変更を確定する。

## 手順2: このZIPの中身を既存のGitHubへ上書き

1. ZIPを展開する。
2. `TorimaAuto`フォルダの**中身**を、以前 `git init` して `git push` した既存のローカルプロジェクトに上書きする。
   - 既存の `.git` フォルダは削除しない。
   - ソース側の `applicationId` は変更しない。
3. その既存のプロジェクトフォルダでPowerShellを開き、次を実行する。

```powershell
git add .
git commit -m "Use one public repo for in-app updates"
git push
```

まだ署名用Secretsが登録されていなければ、Actionsは**デバッグAPKだけ**作って正常終了します。ここまでは正常です。

## 手順3: 初回だけ署名鍵を用意する（Windows PowerShell）

**署名鍵のファイルとパスワードを失うと、同じアプリの上書き更新ができなくなります。必ず安全にバックアップしてください。**

1. PowerShellで、プロジェクト外の保存場所を作成する。

```powershell
New-Item -ItemType Directory -Force "$env:USERPROFILE\TorimaAutoSigning" | Out-Null
```

2. `keytool`（Android StudioのJDKに同梱）を実行する。途中でパスワード等を聞かれるので入力する。

```powershell
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v -keystore "$env:USERPROFILE\TorimaAutoSigning\torima-release.jks" -alias torima -keyalg RSA -keysize 3072 -validity 10000
```

3. 秘密鍵のBase64をクリップボードにコピーする（GitHub Secretに登録したら他の文字列をコピーして消す）。

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$env:USERPROFILE\TorimaAutoSigning\torima-release.jks")) | Set-Clipboard
```

## 手順4: GitHub Actionsに4つのSecretsを設定

`https://github.com/Wacky1425/torima-auto/settings/secrets/actions` を開き、`New repository secret` から以下を追加する。

| Secret名 | 内容 |
| --- | --- |
| `TORIMA_KEYSTORE_BASE64` | 手順3でコピーしたBase64の文字列 |
| `TORIMA_STORE_PASSWORD` | 鍵生成時のキーストアパスワード |
| `TORIMA_KEY_ALIAS` | `torima` |
| `TORIMA_KEY_PASSWORD` | 鍵のパスワード（通常、同じパスワード） |

鍵そのもの、Base64、パスワードはリポジトリのコードにもチャットにも貼らないこと。

## 手順5: GitHub Actionsを実行

1. `https://github.com/Wacky1425/torima-auto/actions` を開く。
2. 左の `Torima Auto APK build and release` → `Run workflow` → `Run workflow`。
3. 実行が緑のチェックになったことを確認する。
4. `https://github.com/Wacky1425/torima-auto/releases` で **`v1.0`** を開き、`TorimaAuto-release.apk` があることを確認する。
5. もし `403 Resource not accessible by integration` などの権限エラーが出た場合、リポジトリの `Settings → Actions → General → Workflow permissions` を確認する。`Read and write permissions` を選べる場合は選び直し、ワークフローを再実行する。

> 現在の `versionName` は `1.0`、`versionCode` は `10` です。`v1.0` がすでに存在したら、Actionsは**同じReleaseを上書きせずスキップ**します。

## 手順6: OPPOに初回だけインストール

1. 端末の**Torima Autoテストアプリ**を必要ならアンインストールする。**ポイントアプリ本体の「トリマ」はアンインストールしない。**
2. OPPOのブラウザで同じリポジトリの `Releases → v1.0` を開き、`TorimaAuto-release.apk` をダウンロード。
3. Androidの案内に沿ってインストールする（初回は「この提供元から許可」が必要なことがあります）。
4. アクセシビリティサービスを再度有効化する。
5. `アプリの更新を確認` を押して「最新版です」と出ることを確認する。

## 手順7: 次のバージョンからの更新

次の変更では `app/build.gradle.kts` の以下の**両方**を増やしてから `git push` する。

```
versionCode = 11
versionName = "1.1"
```

Actionsが成功して `v1.1` がReleasesに出たら、OPPOのTorima Autoから `アプリの更新を確認` → ダウンロード → インストール確認を進められます。

## 重要

- APKを更新するには**同じ署名鍵**と**同じ applicationId**が必要。以後は鍵を変えないでください。
- リポジトリをPublicにするとGitHub上のコード、過去の履歴、配布APKが公開されます。
- アプリは更新ファイルのダウンロード後、Androidの確認画面を開くだけです。確認なしのサイレントインストールはしません。
- 最新のReleaseがまだない間、アプリ内更新のチェックはHTTP 404になる場合があります（初回リリース公開前は正常）。
- v1.0の署名付きReleaseビルドや実機インストールは、実際にActions/OPPOで確認してください。
