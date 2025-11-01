Hiyori (Compose Desktop + SQLite)

<!-- Badges -->
[![CI](https://github.com/SilentMalachite/Hiyori/actions/workflows/ci.yml/badge.svg)](https://github.com/SilentMalachite/Hiyori/actions/workflows/ci.yml)
![JDK](https://img.shields.io/badge/JDK-21-blue)
[![License: Apache-2.0](https://img.shields.io/badge/License-Apache--2.0-green.svg)](LICENSE)

概要
- Kotlin + Compose Desktop によるローカル専用「メモ + スケジュール」アプリ。
- SQLite 永続化 (FTS5)、Kotlin Coroutines で非同期処理。

要件
- JDK 21 以上
- Gradle 8 以上（同梱の Gradle Wrapper 推奨: `./gradlew`）

実行方法
1) 依存取得と実行（Gradle Wrapper 推奨）

   ./gradlew run

   注: 初回は依存取得のためネットワーク接続が必要です（Compose, sqlite-jdbc など）。

2) 主なショートカット
- 新規メモ: ボタンまたはメニューから
- 検索: リアルタイム検索 (200ms デバウンス)
- 保存: Ctrl/Cmd+S (自動保存 600ms デバウンス)
- 予定機能: 実装予定

データベース
- 既定で `data/app.db` に SQLite を作成（`src/main/resources/app.properties` の `database.path` で変更可）。
- テーブル: `notes`, `notes_fts`(FTS5), `events`。トリガで FTS 同期。

ログ
- コンソール出力に加え、`logs/hiyori.log` へ日次ローテーション（10MB分割、保持30日）。

注意
- Compose Desktop は OS ごとに最適化されたパッケージを生成します。
- M1/M2 Mac 等では JDK 21 のアーキテクチャに合わせてください。

配布（Compose Desktop）
- Compose Gradle Plugin でプラットフォーム固有のパッケージを自動生成：

  1. 現在のOS用パッケージ作成

     ./gradlew packageDistributionForCurrentOS

     - macOS: DMG
     - Windows: MSI
     - Linux: DEB

  2. アプリイメージのみ作成

     ./gradlew createDistributable

     出力: `build/compose/binaries/main/app/Hiyori/`

アイコンの組み込み
- 配置先: `packaging/icons/hiyori.icns`（macOS）、`packaging/icons/hiyori.ico`（Windows）
- これらが存在する場合のみ自動で `--icon` を付与します

CI/環境ごとの上書き（Gradleプロパティまたは環境変数）
- 例（Gradleプロパティ）:

  ./gradlew jpackage \
    -Papp.version=1.2.3 \
    -Papp.vendor=Acme \
    -Papp.macBundleId=com.acme.hiyori \
    -Papp.installerType=pkg

- 対応キー（環境変数でも可）:
  - `app.version`（APP_VERSION）: バージョン。既定 `1.0.0`
  - `app.vendor`（APP_VENDOR）: ベンダ名。既定 `Hiyori`
  - `app.macBundleId`（APP_MACBUNDLEID）: macOS Bundle ID。既定 `dev.hiyori.app`
  - `app.installerType`（APP_INSTALLERTYPE）: インストーラ種別（macOSは `pkg` を既定）

機能概要
- メモ: 作成/自動保存 (600msデバウンス)、全文検索(FTS5)、リアルタイム検索 (200msデバウンス)
- 予定: 実装予定 (週ビュー、ドラッグ操作など)
- UI: Material 3 Design、ダークテーマ、スクロールバー付き
- アクセシビリティ/配慮: 低刺激色、キーボード操作対応

主要ショートカット
- 保存: Ctrl/Cmd+S（自動保存 600ms デバウンス）
- バック: ナビゲーションボタン

開発者向け
- 仕様は `AGENT.md` を参照
- 貢献方法は `CONTRIBUTING.md` を参照
- CI: GitHub Actions でビルドとテストを自動実行

テスト
- Java層のテスト: データベース、サービス、DAO
- Kotlin/Compose層のテスト: 実装予定
- CI: GitHub Actions で自動実行

テストの実行:
```bash
./gradlew test
```

ビルド成果物の管理
- ビルドディレクトリのクリーンアップ:
  ```bash
  ./gradlew clean
  ```

- ビルドキャッシュも含めて完全クリーン:
  ```bash
  ./gradlew clean cleanBuildCache
  ```

- Gradle キャッシュの確認:
  ```bash
  du -sh ~/.gradle/caches
  ```


---

## ライセンスとポリシー
- ライセンス: Apache-2.0（詳細は `LICENSE` を参照）
- 行動規範: `CODE_OF_CONDUCT.md`（Contributor Covenant v2.1）
- セキュリティ: 脆弱性は `SECURITY.md` を参照のうえ、GitHub Security Advisories から非公開で報告してください
- サポート: 問い合わせ・バグ報告・機能要望の流れは `SUPPORT.md` を参照
- 貢献方法: `CONTRIBUTING.md` を参照
