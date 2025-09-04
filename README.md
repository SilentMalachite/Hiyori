Hiyori (JavaFX + SQLite)

概要
- Java 21 + JavaFX によるローカル専用「メモ + スケジュール」アプリ。
- SQLite 永続化 (FTS5)・週ビューは Canvas で軽量描画。

要件
- JDK 21 以上
- Gradle 8 以上（同梱の Gradle Wrapper 推奨: `./gradlew`）

実行方法
1) 依存取得と実行（Gradle Wrapper 推奨）

   ./gradlew run

   注: 初回は依存取得のためネットワーク接続が必要です（javafx, sqlite-jdbc など）。

2) 主なショートカット
- 新規メモ: Ctrl/Cmd+N
- 検索: Ctrl/Cmd+K
- 保存: Ctrl/Cmd+S (エディタは自動保存 600ms デバウンス)
- 新規予定 (クイック): Ctrl/Cmd+Shift+N (現在時刻から 90 分)

データベース
- `data/app.db` に SQLite を作成。
- テーブル: `notes`, `notes_fts`(FTS5), `events`。トリガで FTS 同期。

注意
- JavaFX は OS ごとにネイティブ依存があります。Gradle で自動解決しますが、
  M1/M2 Mac 等では JDK/Gradle のアーキテクチャに合わせてください。

配布（jlink/jpackage）
- Gradle プラグインで jlink/jpackage を統合済み：

  1. ランタイムイメージ作成

     ./gradlew jlink

     出力: `build/image/` （軽量JRE同梱）

  2. アプリイメージ（プラットフォームごとのアプリフォルダ、アプリ名: Hiyori）

     ./gradlew jpackageImage

     出力: macOSは `build/jpackage/Hiyori.app`、Windows/Linuxは `build/jpackage/Hiyori` など
     バージョンは `1.0.0` に設定

  3. インストーラ作成（macOS は PKG、Windows/Linux は各OS既定）

     ./gradlew jpackage

     - macOS: `.pkg`（デフォルトで PKG。`-Papp.installerType=pkg` で明示上書き可能）
     - Windows: MSI（ショートカット/メニュー登録あり）
     - Linux: DEB/RPM など（環境により異なります）

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
- メモ: 作成/自動保存、全文検索(FTS5)、一覧表示
- 予定: 週ビュー(Canvas)、ドラッグで作成・移動・リサイズ、衝突時の列分割表示
- 検索: メモと予定を横断して統合表示、選択でエディタ/週ビューへジャンプ
- ダイアログ: 予定詳細（タイトル/開始/終了/削除）、定型ブロック（90分集中/30分休憩）ボタン、Enter保存・Cmd/Ctrl+Del削除
- アクセシビリティ/配慮: 低刺激色/アニメーション抑制、44pxターゲット、単一フォーカス減光、キーボード操作

主要ショートカット
- 新規メモ: Ctrl/Cmd+N
- 検索: Ctrl/Cmd+K
- 保存: Ctrl/Cmd+S（エディタは自動保存 600ms デバウンス）
- 新規予定（クイック）: Ctrl/Cmd+Shift+N（現在時刻から90分）

開発者向け
- 仕様は `AGENT.md` を参照
- 貢献方法は `CONTRIBUTING.md` を参照
