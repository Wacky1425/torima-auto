Torima Auto v0.1

目的:
PC/ADB/scrcpyを使わず、OPPO単体のAccessibilityService.dispatchGesture()で
トリマ HOMEの実測座標 (521,1243) を1回だけタップできるか検証します。

安全仕様:
- 自動ループなし
- 5秒後に1回だけタップ
- ランダム座標なし
- 「友達にシェアする」等の探索・押下なし

Android Studio:
1. このフォルダを Open
2. Gradle Sync
3. OPPOを接続して Run
4. アプリの「アクセシビリティ設定を開く」
5. Torima Auto を有効化
6. トリマ HOMEで紫ボタンが出ていることを確認
7. テストアプリへ戻り「5秒後に…」を押す
8. 5秒以内にトリマ HOMEへ戻る
9. 紫ボタンが反応するか確認

成功したら次段階:
- AccessibilityNodeInfoでトリマの文字/UI取得テスト
- UI要素が取れる箇所はACTION_CLICK
- 取れない箇所だけdispatchGesture
- 状態判定→安全な自動周回へ拡張
