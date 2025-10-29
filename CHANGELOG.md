# Changelog

## [1.0.1](https://github.com/SilentMalachite/Hiyori/compare/Hiyori-v1.0.0...Hiyori-v1.0.1) (2025-10-29)


### Bug Fixes

* データベース同期化ブロックの修正とコード重複の解消 ([5901432](https://github.com/SilentMalachite/Hiyori/commit/5901432baa98e1847a7a506da307df6bf5d13af2))
* 実行/配布の不具合修正と設定整備\n\n- settings.gradle.kts: プロジェクト名を Hiyori に統一\n- build.gradle.kts: run 非モジュール化 (mainModule 削除)、jlink --compress=2、slf4j-nop 追加\n- Gradle Wrapper: 8.10.2 に更新\n- gradle.properties: JDK 21 使用に関する注記を追加 (デフォルトは未固定)\n- AGENT.md: アプリ名などドキュメント整合性修正\n\nrun/jlink/jpackageImage の実行を確認済み ([514c334](https://github.com/SilentMalachite/Hiyori/commit/514c33461b46adc86c3a5eda0677a51232f5a43c))

Changelog

1.0.0 - 2025-09-05
- Initial public version of Hiyori.
- Notes: CRUD with autosave (FTS5 search).
- Events: Week view (Canvas), drag create/move/resize, column layout for overlaps.
- Global search: unified list across notes and events.
- Event editor dialog: title/start/end, delete, presets (90-min focus, 30-min break), Enter save, Cmd/Ctrl+Del delete.
- Packaging: jlink/jpackage with OS-specific options, icons (conditional), PKG default on macOS.
