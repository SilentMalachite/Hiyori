# AGENT.md

## プロジェクト概要
Java 21 + JavaFX を用いて、ローカルで高速に動作する「メモ帳＋スケジュール管理」アプリケーション。  
クラウド依存を避け、起動と操作を軽量化し、ASD/ADHD特性に配慮したUI設計を重視。

---

## 技術スタック
- **UI**: JavaFX 21  
- **データ永続化**: SQLite (Xerial sqlite-jdbc)  
  - メモ検索: SQLite FTS5  
- **スレッド処理**: Java 21 仮想スレッド (Loom)  
- **配布**: jlink + jpackage

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
- 上部: グローバル検索 (メモ・予定横断)
- 左: メモ一覧 (ListView)
- 右: メモエディタ (タイトル+本文)
- 下: カレンダー (日/週/月ビュータブ)

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

## 週ビュー設計
### レイアウト
- 7列 (月〜日)
- 縦方向 0:00〜24:00、15分刻み (96行)
- `Canvas` による軽量描画
- 重なりは列分割表示

### 操作
- 空白ドラッグ: 新規予定
- 既存短冊ドラッグ: 移動
- 上下ハンドル: リサイズ
- スナップ: 5/10/15/30分刻みに吸着
- Altキー: スナップ解除
- ダブルクリック: インライン編集
- Tab/Enter/Esc: アクセシビリティ対応
- 衝突: 自動で列割り当て

---

## パフォーマンス最適化
- SQLite: WALモード + synchronous=NORMAL
- 自動保存: 500〜700msデバウンス
- リスト: 最大500件までページング
- 軽量フォント + WrapText
- Canvas描画でスクロール高速化

---

## ビルドと配布
```bash
# 軽量JRE作成
jlink --add-modules java.base,java.sql,javafx.controls,javafx.fxml \
      --compress=2 --no-header-files --no-man-pages \
      --output build/runtime

# ネイティブパッケージ
jpackage --name FastNoteSched \
         --app-version 0.1.0 \
         --input build/libs \
         --main-jar your-fat-jar.jar \
         --runtime-image build/runtime
