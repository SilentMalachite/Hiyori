# 週ビュー機能 (Compose Desktop実装)

## 概要
Jetpack Compose Desktopで実装した週間カレンダービュー。
既存のJava層（EventService、EventsDao）を再利用し、Kotlin Coroutinesで非同期処理を実現。

## ファイル構成

```
src/main/kotlin/app/compose/events/
├── EventModels.kt          # データモデル・変換・ヘルパー
├── EventServiceBridge.kt   # Java層とのブリッジ
└── WeekViewScreen.kt       # UIコンポーネント
```

## 主要機能

### 1. 週間グリッド表示
- **7列構成**: 月曜日〜日曜日
- **24時間表示**: 0:00〜24:00、1時間ごとの区切り
- **イベントカード**: 
  - 時間位置に基づいた自動配置
  - タイトル・時間範囲を表示
  - クリックで編集ダイアログを開く

### 2. 週のナビゲーション
- **前週/次週**: ツールバーのボタンで移動
- **今週へ戻る**: Todayボタンで現在週に復帰
- **週ヘッダー**: 各日の日付・曜日を表示（今日は強調表示）

### 3. イベント操作
- **新規作成**: ツールバーの+ボタン（現在時刻から90分）
- **編集**: イベントカードをクリック
  - タイトル編集
  - 開始/終了時刻表示
  - 時間の長さ表示
- **削除**: 編集ダイアログから削除可能
- **保存**: 自動的にDBへ保存

### 4. UI/UX設計
- **Material 3 Design**: ダークテーマ、低刺激色
- **スクロール**: 垂直スクロール対応、スクロールバー付き
- **レスポンシブ**: ウィンドウサイズに応じて調整
- **同期スクロール**: 時刻ラベルとグリッドが同期

## データフロー

```
┌─────────────────┐
│ WeekViewScreen  │ (Compose UI)
└────────┬────────┘
         │ suspend functions
         ▼
┌─────────────────┐
│ EventBackend    │ (Kotlin Coroutines)
└────────┬────────┘
         │ Dispatchers.IO
         ▼
┌─────────────────┐
│ EventService    │ (Java)
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ EventsDao       │ (Java + SQLite)
└─────────────────┘
```

## 技術的な特徴

### 1. Kotlin Coroutines
- **非同期処理**: `Dispatchers.IO`でDB操作を非同期実行
- **エラーハンドリング**: `runCatching`で安全に処理
- **LaunchedEffect**: 週が変わるたびに自動リロード

### 2. データ変換
- **EventUi**: UI用の軽量データクラス
- **toUi()/toJava()**: JavaとKotlinの相互変換
- **LocalDateTime**: Java Time APIで時刻計算

### 3. レイアウト計算
- **位置**: `startMinutes / 60f * HOUR_HEIGHT`
- **高さ**: `durationMinutes / 60f * HOUR_HEIGHT`
- **Modifier.offset()**: 絶対位置にイベントカードを配置

### 4. 週の範囲計算
```kotlin
fun getWeekRange(date: LocalDate): WeekRange {
    val monday = date.with(DayOfWeek.MONDAY)
    val sunday = monday.plusDays(6)
    return WeekRange(monday, sunday)
}
```

## 定数設定

```kotlin
private val HOUR_HEIGHT = 60.dp       // 1時間あたりの高さ
private val COLUMN_WIDTH = 120.dp     // 1列（1日）の幅
private val TIME_LABEL_WIDTH = 50.dp  // 時刻ラベルの幅
```

## 今後の拡張可能性

### 短期
- [ ] イベントのドラッグ＆ドロップ移動
- [ ] イベントのリサイズ（上下ハンドル）
- [ ] 時刻編集機能（開始/終了時刻のピッカー）
- [ ] イベントの衝突検出と列分割表示

### 中期
- [ ] 定型ブロック機能（90分集中、30分休憩など）
- [ ] イベントの色分け（カテゴリ別）
- [ ] 空白クリックで新規イベント作成
- [ ] キーボードショートカット対応

### 長期
- [ ] 日ビュー・月ビューの追加
- [ ] イベントの繰り返し設定
- [ ] iCal/Google Calendar連携
- [ ] 複数カレンダー対応

## パフォーマンス最適化

- ✅ LazyColumnは使用せず、通常のColumnで高速描画
- ✅ イベントフィルタリング: 日ごとに事前フィルタ
- ✅ 再描画の最小化: `remember`と`derivedStateOf`活用
- ✅ DB操作: Dispatchers.IOで非同期化

## テスト方針

現在はJava層のテストが完備。今後Compose層のテストも追加予定：

- [ ] EventBackendのユニットテスト
- [ ] WeekRange計算のテスト
- [ ] データ変換のテスト
- [ ] UI統合テスト（ComposeのUIテストフレームワーク）

## 既知の制限事項

1. **日をまたぐイベント**: 現在は開始日のみに表示
2. **時刻編集**: ダイアログでは表示のみ（編集機能は未実装）
3. **タイムゾーン**: システムデフォルトのみ対応
4. **アクセシビリティ**: キーボードナビゲーション未対応

## 関連ファイル

- `app/model/Event.java` - Javaデータモデル
- `app/service/EventService.java` - ビジネスロジック
- `app/db/EventsDao.java` - データアクセス層
- `app/config/AppConfig.java` - 設定（デフォルト時間など）
