// SPDX-License-Identifier: GPL-3.0-or-later
package de.giesen.camaps.companion.prototype;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.Cursor;
import java.util.*;

/** Private diagnostic observations, not yet verified measurement history. No raw notification text. */
final class ObservationStore extends SQLiteOpenHelper {
    ObservationStore(Context context) { super(context, "probe-observations.db", null, 1); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE observation (id INTEGER PRIMARY KEY, notification_key TEXT NOT NULL, "
                + "posted_ms INTEGER NOT NULL, received_ms INTEGER NOT NULL, mgdl INTEGER NOT NULL, trend TEXT NOT NULL, "
                + "UNIQUE(notification_key, posted_ms, mgdl, trend))");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new IllegalStateException("No migration defined");
    }
    boolean insert(String key, long posted, long received, NotificationParser.Result reading) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("notification_key", key); values.put("posted_ms", posted);
        values.put("received_ms", received); values.put("mgdl", reading.mgdl);
        values.put("trend", reading.trend.name());
        db.beginTransaction();
        try {
            long id = db.insertWithOnConflict("observation", null, values, SQLiteDatabase.CONFLICT_IGNORE);
            db.delete("observation","received_ms < ?",new String[]{Long.toString(Math.max(1,received-DiagnosticSeries.WINDOW_MS))});
            db.execSQL("DELETE FROM observation WHERE id NOT IN (SELECT id FROM observation ORDER BY id DESC LIMIT 5000)");
            db.setTransactionSuccessful();
            return id != -1;
        } finally { db.endTransaction(); }
    }
    List<DiagnosticSeries.Sample> receipts(long now) {
        SQLiteDatabase db=getWritableDatabase(); long from=Math.max(1,now-DiagnosticSeries.WINDOW_MS);
        db.delete("observation","received_ms < ?",new String[]{Long.toString(from)});
        List<DiagnosticSeries.Sample> result=new ArrayList<>();
        String sql="SELECT MIN(received_ms) AS t, mgdl FROM observation WHERE received_ms >= ? AND received_ms <= ? "
                +"AND posted_ms > 0 AND posted_ms <= received_ms AND received_ms-posted_ms <= ? "
                +"GROUP BY notification_key,posted_ms,mgdl ORDER BY t ASC LIMIT 5000";
        try (Cursor cursor=db.rawQuery(sql,new String[]{Long.toString(from),Long.toString(now),Long.toString(ReceiptWindow.MAX_AGE_MS)})) {
            while (cursor.moveToNext()) result.add(new DiagnosticSeries.Sample(cursor.getLong(0),cursor.getInt(1)));
        }
        return result;
    }
}
