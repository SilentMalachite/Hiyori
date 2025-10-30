# 貢献ガイド（CONTRIBUTING）

Hiyori への貢献をご検討いただきありがとうございます。以下は開発環境のセットアップ、ブランチ/コミット方針、テスト、PR 手順のガイドです。

## 開発環境
- 必要: JDK 21 以上（Temurin 推奨）
- ビルド/実行: 同梱の Gradle Wrapper を使用してください
  - 実行: `./gradlew run`
  - ビルド: `./gradlew build`
  - テスト: `./gradlew test`
- JavaFX は Gradle プラグインにより自動解決されます。

## 実行・検証
- ヘッドレス/CI 互換:
  - `-Djava.awt.headless=true -Dhiyori.headless=true` を付与するとダイアログを抑止します。
  - UI のスモークテスト: `./gradlew run -Dapp.testExitSeconds=3`
- 並列度/DB プールサイズ（必要に応じて調整）
  - テスト並列: `-Ptest.maxParallelForks=2`（ENV: `TEST_MAX_PARALLEL_FORKS`）
  - DB プール: `-Ddb.pool.size=4`（ENV: `DB_POOL_SIZE`）

## ブランチ/コミット
- ブランチ: `feat/<短い説明>`、`fix/<短い説明>` などのトピックブランチを作成してください。
- コミットメッセージ（推奨: Conventional Commits）
  - 例: `feat: 週ビューでのドラッグ挙動を改善` / `fix: SQLite 読み取り専用切替エラーを解消`
  - 小さくレビューしやすい単位に分割してください。

## コードスタイル/設計
- 既存の命名・パッケージ構成・フォーマットに合わせてください。
- UI スレッドをブロックしない（I/O は非同期、UI 更新のみ FX スレッド）。
- DAO/Service 層への LIMIT 伝播など、I/O/メモリの節約を意識してください。

## テスト
- 追加・変更点には可能な範囲でユニット/統合テストを付与してください。
- JavaFX ヘッドレス実行は既定で有効化（`build.gradle.kts`）。
- ローカル確認:
  - `./gradlew clean test`
  - 必要に応じて `TEST_MAX_PARALLEL_FORKS=2 DB_POOL_SIZE=4` を指定

## ドキュメント
- ユーザー向け README、CHANGELOG、必要に応じて補足を更新してください。
- 重大・ユーザー影響のある変更は `CHANGELOG.md` の `Unreleased` に追記してください。

## PR（Pull Request）
- PR テンプレートに沿って、変更概要・確認手順・影響範囲を記載してください。
- スクリーンショットは不要ですが、ログやエラーメッセージがあれば貼付してください。
- CI が通ってからレビュー依頼してください（GitHub Actions で自動実行されます）。

## 行動規範 / セキュリティ
- 行動規範: `CODE_OF_CONDUCT.md`（Contributor Covenant v2.1）に従ってください。
- セキュリティ報告は公開 Issue ではなく、GitHub の Security Advisories を利用してください（`SECURITY.md` 参照）。

## リリース
- バージョンは SemVer 準拠。
- パッケージ時の上書き例: `./gradlew jpackage -Papp.version=X.Y.Z`

