# 週ビュー高度な機能

## 実装完了した機能

### 1. イベントの衝突検出と列分割 ✅

**概要**: 時間が重なるイベントを自動的に検出し、複数列に分割して表示

**実装ファイル**: `EventLayoutHelper.kt`

**アルゴリズム**:
```kotlin
fun calculateEventLayouts(events: List<EventUi>): List<EventLayout>
```

1. イベントを開始時刻でソート
2. 各イベントについて、既存の配置と重なりをチェック
3. 空いている列を探して配置（貪欲法）
4. 重なっているグループごとに総列数を計算

**使用例**:
```kotlin
val layouts = calculateEventLayouts(events)
layouts.forEach { layout ->
    // layout.column: 0始まりの列番号
    // layout.totalColumns: その時間帯の総列数
    // レイアウトに応じて幅とオフセットを計算
}
```

**表示効果**:
- 3つのイベントが重なる場合 → 3列に分割
- イベント幅 = `列幅 / 総列数`
- X位置 = `列番号 × 列幅`

---

### 2. ドラッグ&ドロップでイベント移動 ✅

**概要**: イベントカードをドラッグして別の時刻に移動

**実装箇所**: `EventCard` コンポーネント

**機能詳細**:
- **ドラッグ中**: リアルタイムで視覚的フィードバック
- **スナップ**: 15分単位に自動吸着
- **制約**: 0:00〜24:00の範囲内
- **期間維持**: ドラッグしても元の時間の長さを保持

**技術実装**:
```kotlin
.pointerInput(event.id) {
    detectDragGestures(
        onDragStart = { isDragging = true },
        onDrag = { change, dragAmount ->
            dragOffsetY += dragAmount.y
        },
        onDragEnd = {
            val offsetMinutes = (dragOffsetY / HOUR_HEIGHT.value * 60).toInt()
            onDrag(event, offsetMinutes)
        }
    )
}
```

**計算式**:
```kotlin
fun calculateNewStartTime(
    originalEvent: EventUi,
    dragOffsetMinutes: Int,
    snapInterval: Int = 15
): LocalDateTime
```

---

### 3. リサイズハンドルで時間調整 ✅

**概要**: イベントの下部をドラッグして終了時刻を調整

**実装箇所**: `EventCard` 内の下部ハンドル

**機能詳細**:
- **リサイズハンドル**: 下部に8dpの高さの領域
- **視覚的フィードバック**: ドラッグ中は半透明の青色で強調
- **インジケータ**: 3dp幅のバー表示
- **最小時間**: 15分以上を保証
- **スナップ**: 15分単位に自動吸着

**UI要素**:
```kotlin
Box(
    modifier = Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .height(8.dp)
        .pointerInput(event.id) { /* resize gesture */ }
) {
    // インジケータバー
    Box(
        modifier = Modifier
            .width(30.dp)
            .height(3.dp)
            .background(...)
    )
}
```

**計算式**:
```kotlin
fun calculateNewEndTime(
    originalEvent: EventUi,
    resizeOffsetMinutes: Int,
    snapInterval: Int = 15,
    minDurationMinutes: Int = 15
): LocalDateTime
```

---

### 4. 時刻ピッカーでの編集 ✅

**概要**: ダイアログから時・分を選択して正確な時刻設定

**実装ファイル**: `TimePicker.kt`

**コンポーネント**:
1. **TimePickerDialog**: シンプルな時刻選択
2. **DateTimePickerDialog**: 日付+時刻選択（拡張版）
3. **TimeColumn**: スクロール可能な数値選択列

**UI設計**:
```
┌─────────────────────┐
│   開始時刻           │
├─────────────────────┤
│  [時]  :  [分]      │
│   ↓        ↓        │
│  [10]     [30]      │ ← スクロール可能
│   11       45       │
│   12       00       │
│  ...      ...       │
└─────────────────────┘
```

**特徴**:
- **時**: 0〜23の24時間表示
- **分**: 0, 15, 30, 45の15分刻み
- **スクロール**: LazyColumnで選択
- **選択中**: プライマリカラーで強調表示
- **自動検証**: 終了時刻は開始時刻より後を保証

**使用方法**:
```kotlin
TimePickerDialog(
    title = "開始時刻",
    initialTime = event.startTime,
    onDismiss = { showPicker = false },
    onConfirm = { newTime ->
        updateEventTime(newTime)
    }
)
```

---

## 実装統計

### 新規ファイル
- `EventLayoutHelper.kt`: 155行（衝突検出・レイアウト計算）
- `TimePicker.kt`: 203行（時刻ピッカーUI）

### 更新ファイル
- `WeekViewScreen.kt`: 大幅拡張
  - ドラッグ&ドロップハンドラ追加
  - リサイズハンドラ追加
  - 時刻ピッカー統合

### 合計追加コード
- **約500行**の新しいKotlinコード
- すべてCompose Desktopで実装
- Material 3 Designに準拠

---

## ユーザー体験の向上

### Before（基本版）
- ❌ イベント重複時は重なって表示
- ❌ 編集は ダイアログのみ
- ❌ 時刻調整は手入力のみ

### After（高度版）
- ✅ イベント重複時は列分割で見やすく
- ✅ ドラッグで直感的に移動
- ✅ リサイズで素早く時間調整
- ✅ 時刻ピッカーで正確な時刻入力

---

## 技術的なポイント

### 1. Compose Gesturesの活用
```kotlin
detectDragGestures()  // ドラッグ&ドロップ
pointerInput()        // カスタムジェスチャー
```

### 2. 状態管理
```kotlin
var isDragging by remember { mutableStateOf(false) }
var dragOffsetY by remember { mutableStateOf(0f) }
```

### 3. 座標計算
```kotlin
// ピクセル → 分
val offsetMinutes = (dragOffsetY / HOUR_HEIGHT.value * 60).toInt()

// 分 → ピクセル
val topOffset = (startMinutes / 60f) * HOUR_HEIGHT.value
```

### 4. スナップ機能
```kotlin
fun roundToMinutes(minutes: Int, interval: Int = 15): Int {
    return (minutes / interval) * interval
}
```

---

## パフォーマンス最適化

- ✅ `remember`でレイアウト計算をキャッシュ
- ✅ ジェスチャー中は軽量な状態更新のみ
- ✅ ドラッグ/リサイズ完了時のみDBアクセス
- ✅ イベント単位での再描画（全体再描画なし）

---

## 今後の拡張案

### 短期
- [ ] Ctrl/Cmdキーでスナップ解除（1分単位調整）
- [ ] 複数イベントの一括選択・移動
- [ ] イベントのコピー&ペースト

### 中期
- [ ] 日をまたぐドラッグ（別の日への移動）
- [ ] イベントの色分け（カテゴリ別）
- [ ] ドラッグ中の時刻プレビュー表示

### 長期
- [ ] 繰り返しイベントの対応
- [ ] 他のカレンダーアプリとの連携
- [ ] ショートカットキーのカスタマイズ

---

## 使用例

### シナリオ1: 会議時間の調整
1. 10:00の会議をドラッグで10:30に移動
2. 下部ハンドルをドラッグして1時間→30分に短縮
3. 保存完了（自動的にDBへ反映）

### シナリオ2: 重複する予定の管理
1. 3つの予定が10:00-11:00に重なる
2. 自動的に3列に分割表示
3. それぞれ独立してドラッグ・リサイズ可能

### シナリオ3: 正確な時刻設定
1. イベントをクリックして編集ダイアログを開く
2. 「開始」ボタンから時刻ピッカーを表示
3. スクロールで時・分を選択
4. OK → 自動的に反映

---

## テスト推奨事項

### 手動テスト
- [ ] 重複イベントの列分割表示
- [ ] イベントのドラッグ移動（15分スナップ）
- [ ] イベントのリサイズ（最小15分）
- [ ] 時刻ピッカーでの編集
- [ ] 終了時刻が開始時刻より前にならないこと

### エッジケース
- [ ] 0:00と23:45の境界
- [ ] 10個以上のイベント重複
- [ ] 非常に短いイベント（15分）
- [ ] 非常に長いイベント（24時間）

---

## 関連ドキュメント
- `WEEKVIEW.md`: 基本的な週ビュー機能
- `EventLayoutHelper.kt`: 衝突検出アルゴリズム
- `TimePicker.kt`: 時刻ピッカー実装
