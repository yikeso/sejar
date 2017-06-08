package com.china.ciic.se.sejar;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by once on 2017/6/8.
 */
public class Download {
    final static int queLen = 50;
    final static long slice = 1024*1024*100L;
    final static BlockingQueue<Runnable> workQueue;
    final static ThreadPoolExecutor pool;

    static{
        workQueue = new ArrayBlockingQueue<Runnable>(queLen);
        pool = new ThreadPoolExecutor(5,10,10, TimeUnit.SECONDS,workQueue);
    }

    public static void main(String[] args) throws IOException {
/*        String fileUrl = "http://mirrors.sohu.com/centos/7/isos/x86_64/CentOS-7-x86_64-Minimal-1611.iso";
        String tempFilePath = "e:/dlp/56789.temp";
        HttpURLConnection con = (HttpURLConnection)new URL(fileUrl).openConnection();
        con.setRequestProperty("Connection", "Keep-Alive");  //保持一直连接
        con.setConnectTimeout(60 * 1000 * 5);                //连接超时5分钟
        con.setRequestMethod("GET");                         //以GET方式连接
        con.setAllowUserInteraction(true);
        con.setRequestProperty("Range", "bytes=" + 1024*1024*100 + "-" + 1024*1024*200);
        ReadableByteChannel read = Channels.newChannel(con.getInputStream());
        FileOutputStream outputStream = new FileOutputStream(tempFilePath);
        FileChannel fileChannel = outputStream.getChannel();
        FileLock lock = fileChannel.lock();
        fileChannel.transferFrom(read,0,1024*1024*100);
        lock.release();*/
        downloadMain(null);
    }

    public static void downloadMain(String[] args) throws IOException {
        String fileUrl = "http://mirrors.sohu.com/centos/7/isos/x86_64/CentOS-7-x86_64-Minimal-1611.iso";
        String downloadPath = "e:/dlp";
        if(!new File(downloadPath).exists()){
            new File(downloadPath).mkdirs();
        }
        String fileName = fileUrl.substring(fileUrl.lastIndexOf('/')+1);
        int i = fileName.lastIndexOf('.');
        String temdir = i > 0?fileName.substring(0,i)+System.currentTimeMillis():fileName+System.currentTimeMillis();
        temdir = downloadPath + "/" +temdir;
        if(!new File(temdir).exists()){
            new File(temdir).mkdirs();
        }
        HttpURLConnection   con = null;
        long position = 0;
        try {
            con = (HttpURLConnection)new URL(fileUrl).openConnection();
            long size = con.getContentLength();
            con.disconnect();
            con = null;
            ListenDownloadLength listenLength = new ListenDownloadLength();
            long count = 0;
            List<String> tempFileList = new ArrayList<String>();
            while (position < size){
                count = position + slice < size?slice:size-position;
                SliceTask task = new SliceTask();
                task.setTempFileList(tempFileList);
                task.setSize(size);
                task.setListenDownloadLength(listenLength);
                task.setDownloadFilePath(downloadPath+"/"+fileName);
                task.setPosition(position);
                task.setCount(count);
                task.setFileUrl(fileUrl);
                task.setTempFilePath(temdir + "/" + position + ".temp");
                tempFileList.add(task.getTempFilePath());
                while (workQueue.size() >= queLen){
                    try {
                        Thread.sleep(1000 * 60);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                pool.execute(task);
                position += slice;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            if(con != null){
                con.disconnect();
            }
        }
    }

    private static void deleteFile(File f){
        if(f.isFile()){
            f.delete();
            return;
        }
        File[] list = f.listFiles();
        if(list.length > 0){
            for(File sub:list){
                deleteFile(sub);
            }
        }
        f.delete();
    }

    static class DeleteFileTask implements Runnable{
        File f;
        public DeleteFileTask(File f){
            this.f = f;
        }

        @Override
        public void run() {
            try {
                Thread.sleep(1000*10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            deleteFile(f);
        }
    }

    static class ListenDownloadLength {

        long downloadLength = 0L;

        public synchronized void add(long l){
            downloadLength += l;
        }

        public long getDownloadLength() {
            return downloadLength;
        }
    }

    static class SliceTask implements Runnable {

        String fileUrl;
        String downloadFilePath;
        String tempFilePath;
        long position;
        long count;
        long size;
        ListenDownloadLength listenDownloadLength;
        List<String> tempFileList;

        @Override
        public void run() {
            HttpURLConnection   con = null;
            FileOutputStream outputStream = null;
            FileChannel downChannel = null;
            FileChannel tempChannel = null;
            FileLock downLock = null;
            try {
                con = (HttpURLConnection)new URL(fileUrl).openConnection();
                con.setRequestProperty("Connection", "Keep-Alive");  //保持一直连接
                con.setConnectTimeout(60 * 1000 * 5);                //连接超时5分钟
                con.setRequestMethod("GET");                         //以GET方式连接
                con.setAllowUserInteraction(true);
                if(this.position != 0 && this.count != 0){
                    con.setRequestProperty("Range", "bytes=" + position + "-" + (position+count));
                }
                ReadableByteChannel read = Channels.newChannel(con.getInputStream());
                outputStream = new FileOutputStream(tempFilePath);
                FileChannel fileChannel = outputStream.getChannel();
                FileLock lock = fileChannel.lock();
                fileChannel.transferFrom(read,0,count);
                lock.release();
                listenDownloadLength.add(count);
                if(listenDownloadLength.getDownloadLength() >= size){
                    File downloadFile = new File(downloadFilePath);
                    if(!downloadFile.exists()){
                        downloadFile.createNewFile();
                    }
                    downChannel = new FileOutputStream(downloadFile).getChannel();
                    downLock = downChannel.lock();
                    File f = null;
                    ByteBuffer buffer = ByteBuffer.allocate(1024*50);
                    for(String s:tempFileList){
                        f = new File(s);
                        tempChannel = new FileInputStream(f).getChannel();
                        while (tempChannel.read(buffer) > 0) {
                            buffer.flip();
                            downChannel.write(buffer);
                            buffer.clear();
                        }
                        tempChannel.close();
                    }
                    if (f != null) {
                        pool.execute(new DeleteFileTask(f.getParentFile()));
                    }
                    System.out.println(fileUrl + "下载完成。");
                    System.exit(-1);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }finally {
                if(con != null){
                    con.disconnect();
                }
                try {
                    if (outputStream != null) {
                        outputStream.close();
                    }
                    if(downLock != null){
                        downLock.release();
                    }
                    if (downChannel != null){
                        downChannel.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        public void setFileUrl(String fileUrl) {
            this.fileUrl = fileUrl;
        }

        public void setDownloadFilePath(String downloadFilePath) {
            this.downloadFilePath = downloadFilePath;
        }

        public String getTempFilePath() {
            return tempFilePath;
        }

        public void setTempFilePath(String tempFilePath) {
            this.tempFilePath = tempFilePath;
        }

        public void setPosition(long position) {
            this.position = position;
        }

        public void setCount(long count) {
            this.count = count;
        }

        public void setSize(long size) {
            this.size = size;
        }

        public void setListenDownloadLength(ListenDownloadLength listenDownloadLength) {
            this.listenDownloadLength = listenDownloadLength;
        }

        public void setTempFileList(List<String> tempFileList) {
            this.tempFileList = tempFileList;
        }
    }
}
