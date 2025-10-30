# 変更履歴

すべての notable な変更は本ファイルに記録します。
このプロジェクトは [Keep a Changelog](https://keepachangelog.com/ja/1.1.0/) と [Semantic Versioning](https://semver.org/lang/ja/) に従います。

## [Unreleased]
### Added
- GitHub Actions による CI（Ubuntu、JDK 21、JavaFX headless 実行、Gradle キャッシュ、テストレポート収集）。
- `.github/ISSUE_TEMPLATE` と `PULL_REQUEST_TEMPLATE.md` を追加（日本語）。
- `CODE_OF_CONDUCT.md`（Contributor Covenant v2.1 日本語）と `SECURITY.md`、`SUPPORT.md` を追加。

### Changed
- `README.md` を GitHub 向けに整理（日本語、バッジ、クイックスタート、設定項目の明記）。
- `CONTRIBUTING.md` を日本語化し、開発/テスト/PR 手順を明確化。
- `CHANGELOG.md` を Keep a Changelog 形式へ移行。

### Fixed
- SQLite の読み取り専用切替エラーを回避（接続後の `setReadOnly` 呼び出しを撤廃）。
- UI スレッド上の同期 DB アクセスを非同期化（フリーズ回避）。
- DAO 取得件数の `LIMIT` を UI から DAO 層へ伝播し、I/O とメモリを削減。
- 接続プールのクローズ整合性（`closing` フラグ、ロック整備）を改善。
- ヘッドレス/CI 環境でエラーダイアログを抑止（テストのハング防止）。
- テスト並列度とプールサイズの安全なデフォルト設定（並列=2、プール=4）。

## [1.0.0] - 2025-09-05
### Added
- 初回公開。
- メモ: CRUD/自動保存（FTS5 検索）。
- 予定: 週ビュー（Canvas）、ドラッグ作成/移動/リサイズ、重なりの列分割表示。
- グローバル検索: メモと予定の統合一覧、選択でジャンプ。
- 予定エディタ: タイトル/開始/終了、削除、プリセット（90 分集中、30 分休憩）、Enter 保存、Cmd/Ctrl+Del 削除。
- 配布: jlink/jpackage、OS 別オプション・アイコン条件付与、macOS は PKG 既定。

[Unreleased]: https://github.com/SilentMalachite/Hiyori/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/SilentMalachite/Hiyori/releases/tag/v1.0.0

