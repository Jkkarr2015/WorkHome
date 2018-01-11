package org.aph.avigenie.OSM_Download;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import org.aph.avigenie.AVIGenieApplication;

import java.io.File;

/**
 * Created by jkarr on 1/8/2018.
 */

public class OSMMapDL2 {



    public static String createOSMDir(){
        File sdDir = Environment.getExternalStorageDirectory();
        File direct = new File(sdDir + "/osm");

        if(!direct.exists()){//Checks if directory already exists
            direct.mkdirs();
        }

        return direct.getAbsolutePath();
    }//end createOSMDir



}//end class
