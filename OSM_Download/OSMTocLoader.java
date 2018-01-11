package org.aph.avigenie.OSM_Download;


import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import org.aph.avigenie.activity.OSMMapDLActivity;
import org.aph.avigenie.service.HTTPFile;
import org.aph.avigenie.AVIGenieApplication;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.net.URL;
import java.util.Scanner;

import static android.content.Context.DOWNLOAD_SERVICE;
import static java.lang.Thread.sleep;
import static org.aph.avigenie.OSM_Download.OSMPathManager.KEY_OSM_REGION_GRID_INDEX;
import static org.aph.avigenie.OSM_Download.OSMPathManager.KEY_REGION_SERVER_DIR;
import static org.aph.avigenie.OSM_Download.OSMPathManager.OSM_TOC_NAME;
import static org.aph.avigenie.OSM_Download.OSMPathManager.OSM_URL_BASE;


/**
 * Created by jkarr on 1/8/2018.
 */

public class OSMTocLoader {
    private String mURLbase;
    private AVIGenieApplication app;
    private String mOSMFileName;
    private String mDirectory;
    public JSONObject toc;
    public JSONArray allRegions = new JSONArray();
    private String localGridPath;
    private int version;
    private String checkDirectory;



    public OSMTocLoader(Context c){
        checkDirectory = OSMMapDL2.createOSMDir();
        app = (AVIGenieApplication) c.getApplicationContext();
        mDirectory = "/osm/";
        mURLbase = OSM_URL_BASE;
        mOSMFileName = OSM_TOC_NAME;
        File tocFile = new File(checkDirectory,mOSMFileName);
        fileType = file_types.TOC;
        createDownloadReceiver(app);
        if(!tocFile.getAbsoluteFile().exists())
            downloadToc(remoteTocURL(), mOSMFileName);
        else {
            checkForUpdates(remoteTocURL(), mOSMFileName);
            loadLocalToc();
            checkGrid();
        }

    }


    private String remoteTocURL(){
        String url = mURLbase + OSM_TOC_NAME;

        return url;
    }
    private void localGridPath(){
        try {
            localGridPath = mDirectory + toc.getJSONObject("gridFile").getString("fileName");
        }catch(Exception e){
            e.printStackTrace();
        }
    }//end localGridPath

    private String remoteGridURL(){
        String url = null;
        try {
            Log.i("Remote Grid URL", OSM_URL_BASE + toc.getJSONObject("gridFile").getString(KEY_REGION_SERVER_DIR) + "/" + toc.getJSONObject("gridFile").getString("fileName"));
            url = OSM_URL_BASE + toc.getJSONObject("gridFile").getString(KEY_REGION_SERVER_DIR) + "/"  + toc.getJSONObject("gridFile").getString("fileName");


        }catch(Exception e){
            e.printStackTrace();
        }

        return url;
    }//end remoteGridURL

    private void downloadToc(String url, String fileName){
        long downloadReference;
        Uri osm_url = Uri.parse(url);
        DownloadManager downloadManager = (DownloadManager) app.getSystemService(DOWNLOAD_SERVICE);
        DownloadManager.Request request = new DownloadManager.Request(osm_url);
        request.setTitle(fileName + "being downloaded");
        request.setDescription("Downloading " + fileName + "for OSM use");

        request.setDestinationInExternalPublicDir(mDirectory,fileName);

        downloadReference = downloadManager.enqueue(request);
//        try {
//            downloadManager.wait();
//        }catch(Exception e){
//            e.printStackTrace();
//        }


        DownloadManager.Query query = new DownloadManager.Query();

        query.setFilterById(downloadReference);

        Cursor cursor = downloadManager.query(query);

        if(cursor.moveToFirst()){
            int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);

            int status = cursor.getInt(statusIndex);

            if(status == DownloadManager.STATUS_PENDING){
                Log.i("OSMToc", "File download is PENDING");
            }
            if(status == DownloadManager.STATUS_SUCCESSFUL){
                Log.i("OSMToc", "File download is SUCCESSFUL");
            }
            if(status == DownloadManager.STATUS_RUNNING){
                Log.i("OSMToc", "File download is RUNNING");//should pop up this one
            }

        }
        File tocFile = new File(checkDirectory,fileName);

        //Log.i("OSMToc", "\n" + tocFile.getAbsolutePath());




        if(tocFile.getAbsoluteFile().exists()){
            if(loadLocalToc())
                checkGrid();
        }

    }//end downloadToc

    private void parseJsonFromData(File tocFile){
        if(!tocFile.getAbsoluteFile().exists())
            return ;
        try{
            Scanner tocScanner = new Scanner (tocFile);//Reads Toc file
            String str = new String();

            while (tocScanner.hasNext())
                str += tocScanner.nextLine();
            tocScanner.close();

            toc = new JSONObject(str);//Converts toc file into JSONObject which is similar to a map

            version = toc.getInt("version");

            findAllRegions();
        }catch(Exception e){
            e.printStackTrace();
        }

    }//end parseJsonFromData

    private void findAllRegions() {
        try {
            JSONArray continents = toc.getJSONArray("continents");
            for (int i = 0; i < continents.length(); i++) {
                JSONArray countries = continents.getJSONObject(i).getJSONArray("countries");
                JSONArray region = countries.getJSONObject(i).getJSONArray("regions");
                Log.i("OSMToc", "Region enght is " + region.length());
                for (int j = 0; j < region.length(); j++) {
                    allRegions.put(region.getJSONObject(j));
                }
            }
        }catch(Exception e){
            e.printStackTrace();
        }

    }//end findAllRegions


    private boolean loadLocalToc(){
        File tocFile = new File(checkDirectory, mOSMFileName);
        boolean bRes = false;

        if(tocFile.getAbsoluteFile().exists()) {
            parseJsonFromData(tocFile);

            if (toc != null) {
                bRes = true;
            }
        }

        return bRes;

    }//end loadLocalToc


    private void checkForUpdates(final String url, final String filename) {
        new Thread() {
            public void run() {
                File locFile = new File(checkDirectory, filename);
                Log.i("CheckFile", "File Name: " + filename);
                HTTPFile svrFile;
                try {
                    svrFile = new HTTPFile(new URL(url));
                    Log.i("LM Local", "Last Modified  " + locFile.lastModified());
                    Log.i("LM Online", "Last Modified " + svrFile.getLastModified());
                    if (svrFile.getLastModified() <= locFile.lastModified()+7200000)
                        return; //file exists, matches size and is older
                    long downloadReference;
                    locFile.delete();//deletes old file
                    Uri osm_url = Uri.parse(url);
                    DownloadManager downloadManager = (DownloadManager) app.getSystemService(DOWNLOAD_SERVICE);
                    DownloadManager.Request request = new DownloadManager.Request(osm_url);
                    request.setTitle(filename + "being downloaded");
                    request.setDescription("Downloading " + filename + "for OSM use");

                    request.setDestinationInExternalPublicDir(mDirectory, filename);

                    downloadReference = downloadManager.enqueue(request);


                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }//end checkForUpdates


    private void createDownloadReceiver(Context context){
        final String action = "android.intent.action.DOWNLOAD_COMPLETE";
        IntentFilter intentFilter = new IntentFilter(action);
        DownloadReceiver mReceiver = new DownloadReceiver();
        context.registerReceiver(mReceiver, intentFilter);

    }


    private void checkGrid(){
        String gridFileName = null;
        try {
            gridFileName = toc.getJSONObject("gridFile").getString("fileName");
        }catch(Exception e){
            e.printStackTrace();
        }


        File grid = new File(checkDirectory,gridFileName );

       Log.i("GRID", "URL: " + remoteGridURL());

        if(!grid.getAbsoluteFile().exists()){//If grid file does not exist
            downloadToc(remoteGridURL(),gridFileName);
        }
        else{
            checkForUpdates(remoteGridURL(),gridFileName);
        }

        Log.i("GridFile", "Exists: " +grid.getAbsoluteFile().exists());
    }//end check grid

    private void downloadGrid(String gridFileName){
            long downloadReference;
            Uri osm_url = Uri.parse(remoteGridURL());
            DownloadManager downloadManager = (DownloadManager) app.getSystemService(DOWNLOAD_SERVICE);
            DownloadManager.Request request = new DownloadManager.Request(osm_url);
            request.setTitle("Grid file being downloaded");
            request.setDescription("Downloading grid file for OSM use");

            request.setDestinationInExternalPublicDir(mDirectory, gridFileName);

            downloadReference = downloadManager.enqueue(request);

    }//end downloadGrid




    public JSONArray regionFromGridIndex(int gridIndex){
        JSONArray matchRegion = null;
        int val;
        try {
            for (int i = 0; i < allRegions.length(); i++) {
                val = allRegions.getJSONObject(i).getInt(KEY_OSM_REGION_GRID_INDEX);
                if (val == gridIndex) {
                    matchRegion = allRegions.getJSONArray(i);
                    break;
                }
            }
        }catch(Exception e){
            e.printStackTrace();
        }
        return matchRegion;
    }

    public void onDownloadComplete(){
        loadLocalToc();
        checkGrid();
    }
    public void initializeToc(){


        File tocFile = new File(checkDirectory, mOSMFileName);
        tocFile.delete();//REMOVE WHEN TOCLOADER IS DONE!!!!
        Log.i("OSMToc", "File exists:" + tocFile.getAbsoluteFile().exists());

        if(!loadLocalToc()) {
            downloadToc(remoteTocURL(), mOSMFileName);
        }
        //else
           // checkForUpdates(remoteTocURL(),mOSMFileName);



        //Log.i("OSMTocLoader", "toc =" + toc.toString() );
    }

    enum file_types { NONE, TOC, GRID, AL, AK }
    static file_types fileType;
    private static boolean mTOCLoaded = false;
    public static  boolean isTocLoaded() { return mTOCLoaded;}
    class DownloadReceiver extends BroadcastReceiver{
        public void onReceive(Context ctxt, Intent intent) {
            String action = intent.getAction();

            Log.i("DOWNLOADBROADCAST", "NOT Really DONE");
            if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(action)) {
                //Show a notification


                switch (fileType) {
                    case TOC:
                        fileType = file_types.NONE;
                        mTOCLoaded = loadLocalToc();
                        Log.i("DOWNLOADBROADCAST", toc.toString());
//                        Toast tocMessage = new Toast(ctxt);
//                        tocMessage.makeText(ctxt,"Table of Contents Downloaded",Toast.LENGTH_LONG);
//                        tocMessage.show();
                        checkGrid();
                        break;
                }
            }
        }
    };
   
}//end class


