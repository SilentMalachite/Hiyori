# 変更履歴

すべての notable な変更は本ファイルに記録します。
このプロジェクトは [Keep a Changelog](https://keepachangelog.com/ja/1.1.0/) と [Semantic Versioning](https://semver.org/lang/ja/) に従います。

## [2.0.1](https://github.com/SilentMalachite/Hiyori/compare/v2.0.0...v2.0.1) (2025-11-01)


### Bug Fixes

* add RPM support and fix Linux packaging workflow ([abb5b6d](https://github.com/SilentMalachite/Hiyori/commit/abb5b6deeca3ca91e09eabbfaad05d145786c81c))

## [Unreleased]

## [2.0.0] - 2025-11-01
### Changed - BREAKING
- **JavaFX から Compose Desktop への完全移行**
  - UI層をJetpack Compose Desktopで再実装
  - Kotlin 2.0.21 + Compose Multiplatform 1.7.0
  - Material 3 Design適用
  - データ層（Java）は維持、UI層のみ刷新

### Added
- **週ビュー機能（Compose版）**
  - 月曜〜日曜の7列表示、0:00〜24:00の24時間グリッド
  - イベントの作成・編集・削除
  - 週のナビゲーション（前週/次週/今週）
  - Material 3 ダークテーマ

- **高度なイベント操作機能**
  - イベントの衝突検出と列分割表示（EventLayoutHelper.kt）
  - ドラッグ&ドロップでイベント移動（15分スナップ）
  - リサイズハンドルで終了時刻を調整
  - 時刻ピッカーで正確な時刻設定（TimePickerDialog）

- **メモ帳機能（Compose版）**
  - リアルタイム検索（200msデバウンス）
  - 自動保存（600msデバウンス）
  - 全文検索（FTS5）
  - Material 3 UI

- **ドキュメント**
  - `WEEKVIEW.md`: 週ビュー基本機能の説明
  - `ADVANCED_FEATURES.md`: 高度な機能の詳細ドキュメント

### Fixed
- CI/CDワークフローをCompose Desktopのパッケージングに対応
  - macOS: DMG形式（packageDmg）
  - Windows: MSI形式（packageMsi）
  - Linux: DEB/RPM形式（packageDeb/packageRpm）

### Removed
- JavaFX関連のすべてのコード
  - MainApp.java、MainController.java
  - WeekView.java、EventEditorDialog.java
  - Debouncer.java、module-info.java
  - JavaFX関連のテスト4ファイル

### Technical Details
- 総コード: 1,069行のKotlin（eventsパッケージ）
- 新規ファイル: EventLayoutHelper.kt (155行), TimePicker.kt (203行)
- アーキテクチャ: Compose UI → Kotlin Coroutines → Java Service/DAO → SQLite
- 非同期処理: Dispatchers.IOでDB操作

## [1.0.0] - 2025-09-05
### Added
- 初回公開。
- メモ: CRUD/自動保存（FTS5 検索）。
- 予定: 週ビュー（Canvas）、ドラッグ作成/移動/リサイズ、重なりの列分割表示。
- グローバル検索: メモと予定の統合一覧、選択でジャンプ。
- 予定エディタ: タイトル/開始/終了、削除、プリセット（90 分集中、30 分休憩）、Enter 保存、Cmd/Ctrl+Del 削除。
- 配布: jlink/jpackage、OS 別オプション・アイコン条件付与、macOS は PKG 既定。

[Unreleased]: https://github.com/SilentMalachite/Hiyori/compare/v2.0.0...HEAD
[2.0.0]: https://github.com/SilentMalachite/Hiyori/compare/v1.0.0...v2.0.0
[1.0.0]: https://github.com/SilentMalachite/Hiyori/releases/tag/v1.0.0
