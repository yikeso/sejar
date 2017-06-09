package com.china.ciic.se.sejar;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
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
        //下载文件的url路径
        String fileUrl = "http://mirrors.sohu.com/centos/7/isos/x86_64/CentOS-7-x86_64-Minimal-1611.iso";
        //下载到本地目录
        String downloadPath = "e:/dlp";
        download(fileUrl,downloadPath);
    }

    /**
     * 下载任务生成者
     * @param fileUrl
     * @param downloadPath
     * @throws IOException
     */
    public static void download(String fileUrl,String downloadPath) throws IOException {
        //创建下载目录
        if(!new File(downloadPath).exists()){
            new File(downloadPath).mkdirs();
        }
        String fileName = fileUrl.substring(fileUrl.lastIndexOf('/')+1);
        int i = fileName.lastIndexOf('.');
        String temdir = i > 0?fileName.substring(0,i)+System.currentTimeMillis():fileName+System.currentTimeMillis();
        temdir = downloadPath + "/" +temdir;
        //创建缓存目录
        if(!new File(temdir).exists()){
            new File(temdir).mkdirs();
        }
        HttpURLConnection   con = null;
        long position = 0;
        try {
            con = (HttpURLConnection)new URL(fileUrl).openConnection();
            //获取下载文件大小
            long size = con.getContentLength();
            con.disconnect();
            con = null;
            long count = 0;
            List<String> tempFileList = new ArrayList<String>();
            //创建下载进度监听者（观察者）
            ListenDownloadLength listenLength = new ListenDownloadLength();
            listenLength.setTempFileList(tempFileList);
            listenLength.setFileName(fileName);
            listenLength.setSize(size);
            listenLength.setDownloadFilePath(downloadPath+"/"+fileName);
            //线程池队列已满，则进入等待
            while (workQueue.size() >= queLen){
                try {
                    Thread.sleep(1000 * 60);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            //启动监听器
            pool.execute(listenLength);
            //根据设置的缓存文件的大小，打包任务。
            while (position < size){
                count = position + slice < size?slice:size-position;
                //创建下载任务任务
                SliceTask task = new SliceTask();
                //传入下载任务监听器
                task.setListenDownloadLength(listenLength);
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
                //向线程池中添加下载任务
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

    /**
     * 递归删除文件夹
     * @param f
     */
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

    /**
     * 下载监听器器
     */
    static class ListenDownloadLength implements Runnable{
        long size;
        String downloadFilePath;
        String fileName;
        long preSecondLength = 0L;
        List<String> tempFileList;
        Long downloadLength = 0L;

        public void setSize(long size) {
            this.size = size;
        }

        public void setDownloadFilePath(String downloadFilePath) {
            this.downloadFilePath = downloadFilePath;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public void setTempFileList(List<String> tempFileList) {
            this.tempFileList = tempFileList;
        }

        public void add(long l){
            synchronized(downloadLength) {
                downloadLength += l;
            }
        }

        @Override
        public void run() {
            //每秒打印一次实时下载速度
            while (downloadLength < size){
                double v;
                synchronized (downloadLength) {
                    v = (downloadLength - preSecondLength) / 1024.0;
                    preSecondLength = downloadLength;
                }
                BigDecimal bg = new BigDecimal(v).setScale(2, RoundingMode.HALF_UP);
                if(v > 1024) {
                    v /= 1024.0;
                    bg = new BigDecimal(v).setScale(2, RoundingMode.HALF_UP);
                    System.out.println(fileName + " \\/ " + bg.doubleValue() + "M/s");
                }else {
                    System.out.println(fileName + " \\/ " + bg.doubleValue() + "Kb/s");
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            System.out.println(fileName + " 多线程，网络资源拉取到本地完成。合并本地缓存文件");
            //下载完成后合并本地缓存文件
            FileChannel downChannel = null;
            FileLock downLock = null;
            File downloadFile = new File(downloadFilePath);
            try {
                if (!downloadFile.exists()) {
                    downloadFile.createNewFile();
                }
                downChannel = new FileOutputStream(downloadFile).getChannel();
                downLock = downChannel.lock();
                ByteBuffer buffer = ByteBuffer.allocate(1024 * 50);
                FileChannel tempChannel;
                for (String s : tempFileList) {
                    tempChannel = new FileInputStream(s).getChannel();
                    while (tempChannel.read(buffer) > 0) {
                        buffer.flip();
                        downChannel.write(buffer);
                        buffer.clear();
                    }
                    tempChannel.close();
                }
                System.out.println(fileName + " 下载完成。");
            }catch (IOException e){
                e.printStackTrace();
            } finally {
                try {
                    if (downLock != null) {
                        downLock.release();
                    }
                    if(downChannel != null){
                        downChannel.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                //删除缓存文件夹
                deleteFile(new File(tempFileList.get(0)).getParentFile());
                //强制关闭虚拟机
                System.exit(-1);
            }
        }
    }

    /**
     * 下载任务消费者
     */
    static class SliceTask implements Runnable {

        String fileUrl;
        String tempFilePath;
        long position;
        long count;
        ListenDownloadLength listenDownloadLength;

        @Override
        public void run() {
            HttpURLConnection   con = null;
            FileOutputStream outputStream = null;
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
                ByteBuffer buffer = ByteBuffer.allocate(1024*50);
                int i;
                while ((i = read.read(buffer)) > 0) {
                    buffer.flip();
                    fileChannel.write(buffer);
                    //向监听器提交下载流量
                    listenDownloadLength.add(i);
                    buffer.clear();
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
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        public void setFileUrl(String fileUrl) {
            this.fileUrl = fileUrl;
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

        public void setListenDownloadLength(ListenDownloadLength listenDownloadLength) {
            this.listenDownloadLength = listenDownloadLength;
        }

    }
}
