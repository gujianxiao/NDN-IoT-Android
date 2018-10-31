package com.example.chenbo.helloworld;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Created by edward on 4/17/18.
 */

public class FileHelperFunctions {

    public static void save(File file, String data) {

        FileOutputStream fos = null;
        try
        {
            fos = new FileOutputStream(file);
        }
        catch (FileNotFoundException e) {e.printStackTrace();}
        try
        {
            try
            {
                fos.write(data.getBytes());
            }
            catch (IOException e) {e.printStackTrace();}
        }
        finally
        {
            try
            {
                fos.close();
            }
            catch (IOException e) {e.printStackTrace();}
        }

    }

    public static byte[] load(File file) {
        FileInputStream fis = null;
        try
        {
            fis = new FileInputStream(file);
        }
        catch (FileNotFoundException e) {e.printStackTrace();}
        int size;
        byte[] buffer = {};
        try
        {
            size = fis.available();
            buffer = new byte[size];

        }
        catch (IOException e) {e.printStackTrace();}
        try
        {
            fis.read(buffer);
        }
        catch (IOException e) {e.printStackTrace();}

        return buffer;
    }

}
