package com.gymlog.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

internal class GymDatabase(context: Context) : SQLiteOpenHelper(context, GYMLOG_DATABASE, null, 10) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE training_plans(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL UNIQUE,sort_order INTEGER NOT NULL,notes TEXT NOT NULL DEFAULT '',notes_pinned INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("INSERT INTO training_plans(name,sort_order) VALUES('Default',0)")
        db.execSQL("CREATE TABLE machines(id INTEGER PRIMARY KEY AUTOINCREMENT,plan_id INTEGER NOT NULL DEFAULT 1,name TEXT NOT NULL,title_note TEXT NOT NULL DEFAULT '',notes TEXT NOT NULL DEFAULT '',pause_time TEXT NOT NULL DEFAULT '',chart_window TEXT NOT NULL DEFAULT '3M',trailing_average INTEGER NOT NULL DEFAULT 0,tracking_mode TEXT NOT NULL DEFAULT 'WEIGHT',current_weight REAL NOT NULL DEFAULT 0,sort_order INTEGER NOT NULL,pinned INTEGER NOT NULL DEFAULT 0,order_group TEXT NOT NULL DEFAULT 'AUTOMATIC',ranking_priority INTEGER NOT NULL DEFAULT 0,show_trend INTEGER NOT NULL DEFAULT 1,hidden INTEGER NOT NULL DEFAULT 0,FOREIGN KEY(plan_id) REFERENCES training_plans(id) ON DELETE CASCADE)")
        db.execSQL("CREATE TABLE machine_areas(machine_id INTEGER NOT NULL,area TEXT NOT NULL,strength REAL NOT NULL,PRIMARY KEY(machine_id,area),FOREIGN KEY(machine_id) REFERENCES machines(id) ON DELETE CASCADE)")
        db.execSQL("CREATE TABLE sets(id INTEGER PRIMARY KEY AUTOINCREMENT,machine_id INTEGER NOT NULL,reps INTEGER NOT NULL,weight REAL NOT NULL,performed_at INTEGER NOT NULL,FOREIGN KEY(machine_id) REFERENCES machines(id) ON DELETE CASCADE)")
        db.execSQL("CREATE TABLE weight_history(id INTEGER PRIMARY KEY AUTOINCREMENT,machine_id INTEGER NOT NULL,weight REAL NOT NULL,changed_at INTEGER NOT NULL,FOREIGN KEY(machine_id) REFERENCES machines(id) ON DELETE CASCADE)")
        db.execSQL("CREATE TABLE area_colors(area TEXT PRIMARY KEY,color INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX idx_sets_machine_time ON sets(machine_id,performed_at DESC)")
        db.execSQL("CREATE INDEX idx_machines_plan_order ON machines(plan_id,sort_order)")
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 9) {
            resetSchema(db)
            return
        }
        if (oldVersion < 10) {
            db.execSQL("ALTER TABLE machines ADD COLUMN order_group TEXT NOT NULL DEFAULT 'AUTOMATIC'")
            db.execSQL("ALTER TABLE machines ADD COLUMN ranking_priority INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE machines ADD COLUMN show_trend INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE machines ADD COLUMN hidden INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE machines SET order_group=CASE WHEN pinned=1 THEN 'MANUAL_TOP' ELSE 'AUTOMATIC' END")
        }
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        throw IllegalStateException("GymLog data cannot be opened by an older app version")
    }

    private fun resetSchema(db: SQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS sets")
        db.execSQL("DROP TABLE IF EXISTS weight_history")
        db.execSQL("DROP TABLE IF EXISTS machine_areas")
        db.execSQL("DROP TABLE IF EXISTS machines")
        db.execSQL("DROP TABLE IF EXISTS training_plans")
        db.execSQL("DROP TABLE IF EXISTS area_colors")
        onCreate(db)
    }
}
