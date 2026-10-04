package com.example.pocketshutdown;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.File;

/** Serves only the downloaded update APK from this app's private cache. */
public class UpdateFileProvider extends ContentProvider {
    private File updateFile() { return new File(new File(providerContext().getCacheDir(), "updates"), "PocketShutdown-update.apk"); }
    private android.content.Context providerContext() { return getContext(); }
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "application/vnd.android.package-archive"; }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws java.io.FileNotFoundException { File f=updateFile(); if(!f.isFile()) throw new java.io.FileNotFoundException(f.getAbsolutePath()); return ParcelFileDescriptor.open(f,ParcelFileDescriptor.MODE_READ_ONLY); }
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] selectionArgs,String sortOrder){ File f=updateFile(); MatrixCursor c=new MatrixCursor(new String[]{"_display_name","_size"}); c.addRow(new Object[]{f.getName(),f.length()}); return c; }
    @Override public int delete(Uri uri,String selection,String[] selectionArgs){ return 0; }
    @Override public int update(Uri uri,ContentValues values,String selection,String[] selectionArgs){ return 0; }
    @Override public Uri insert(Uri uri,ContentValues values){ return null; }
}
