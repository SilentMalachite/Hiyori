# AGENT.md

## プロジェクト概要
Kotlin + Compose Desktop を用いて、ローカルで高速に動作する「メモ帳＋スケジュール管理」アプリケーション。  
クラウド依存を避け、起動と操作を軽量化し、ASD/ADHD特性に配慮したUI設計を重視。

---

## 技術スタック
- **UI**: Jetpack Compose Desktop (Compose Multiplatform)  
- **言語**: Kotlin 2.0.21 + Java 21 (データ層)  
- **データ永続化**: SQLite (Xerial sqlite-jdbc)  
  - メモ検索: SQLite FTS5  
- **非同期処理**: Kotlin Coroutines  
- **配布**: Compose Gradle Plugin (jpackage統合)

---

## データモデル
### メモ
- `notes` テーブル
- `notes_fts` 仮想テーブル (全文検索)
- INSERT/UPDATE/DELETE トリガで同期

### 予定
- `events` テーブル
  - `id`, `title`, `start_epoch_sec`, `end_epoch_sec`
  - インデックス: `start_epoch_sec`

---

## UI設計
### メイン画面
- ホーム画面: アプリ情報とナビゲーション
- メモ画面:
  - 上部: 検索バー
  - 左: メモ一覧 (LazyColumn + スクロールバー)
  - 右: メモエディタ (タイトル+本文、BasicTextField)
- 予定画面: (実装予定)

### ASD/ADHDフレンドリーな工夫
- 刺激を減らす: 色数3以内、アニメーション抑制
- 一貫性: 操作結果は常に同じ挙動
- ターゲットサイズ: 最小44px
- 単一フォーカス: 編集時は他をグレーアウト
- Undo前提: すべての操作は `Ctrl/Cmd+Z` で戻れる
- 時間粒度: デフォルト15分 (設定変更可能)
- ショートカット:
  - 新規メモ: `Ctrl/Cmd+N`
  - 検索: `Ctrl/Cmd+K`
  - 保存: `Ctrl/Cmd+S`
  - 新規予定: `Ctrl/Cmd+Shift+N`
  - 予定移動: 矢印キー＋スナップ
- 定型ブロック: 「90分集中」「30分休憩」などワンタップ挿入
- フォーカスモード: 当日列を強調表示

---

## 週ビュー設計 (実装予定)
### レイアウト
- 7列 (月〜日)
- 縦方向 0:00〜24:00、15分刻み (96行)
- Compose Canvas による軽量描画
- 重なりは列分割表示

### 操作
- 空白ドラッグ: 新規予定
- 既存短冊ドラッグ: 移動
- 上下ハンドル: リサイズ
- スナップ: 5/10/15/30分刻みに吸着
- Modifierキー: スナップ解除
- ダブルクリック: インライン編集
- キーボード操作対応
- 衝突: 自動で列割り当て

---

## パフォーマンス最適化
- SQLite: WALモード + synchronous=NORMAL
- 自動保存: 600msデバウンス (Kotlin Flow)
- 検索: 200msデバウンス (Kotlin Flow)
- リスト: LazyColumn による仮想化
- 非同期処理: Dispatchers.IO でDB操作

---

## ビルドと配布
```bash
# 実行
./gradlew run

# テスト
./gradlew test

# ネイティブ配布パッケージ作成 (macOS: DMG, Windows: MSI, Linux: DEB)
./gradlew packageDistributionForCurrentOS

# または jpackage タスクで直接
./gradlew jpackage
```

Compose Gradle Plugin が自動的にプラットフォーム固有のパッケージを生成します。
