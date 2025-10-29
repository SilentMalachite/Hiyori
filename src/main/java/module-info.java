/**
 * Hiyori - Local note-taking and scheduling application
 * 
 * This module provides a JavaFX-based desktop application for managing notes and events
 * with SQLite persistence and full-text search capabilities.
 */
module hiyori {
    // Java Platform Module System
    requires java.base;
    requires java.sql;
    
    // JavaFX modules
    requires javafx.controls;
    requires javafx.graphics;
    
    // SQLite JDBC driver (automatic module)
    requires org.xerial.sqlitejdbc;
    
    // SLF4J logging (automatic modules)
    requires org.slf4j;
    
    // Export main package for JavaFX Application launcher
    exports app;
    
    // Open packages for JavaFX reflection access
    opens app to javafx.graphics;
    opens app.controller to javafx.graphics;
    opens app.ui to javafx.graphics;
    opens app.model to javafx.base;
}
