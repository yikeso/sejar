package com.china.ciic.se.sejar;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;

/**
 * 对yyyyMMdd日期进行加密解密
 * 日期不得超过2250年
 * Created by hejia on 2017/7/5.
 */
public class EncodeDate {

    /**
     * 将日期加密成3字节数组
     * @param date
     * @return
     */
    public static byte[] encode(String date){
        //字符串判断
        if(date == null || date.length() < 8){
            return null;
        }
        date = date.trim();
        //日期格式校验
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        try {
            sdf.parse(date);
        } catch (ParseException e) {
            return null;
        }
        //年
        int e = Integer.parseInt(date.substring(0,4));
        String b = Integer.toBinaryString(e);
        String cArr = "0000000000000000000000000000000";
        if (b.length() < 15){
            b = cArr.substring(0,15-b.length()) + b;
        }
        //月
        e = Integer.parseInt(date.substring(4,6));
        String s = Integer.toBinaryString(e);
        if (s.length() < 4){
            s = cArr.substring(0,4-s.length()) + s;
        }
        b += s;
        //日
        e = Integer.parseInt(date.substring(6));
        s = Integer.toBinaryString(e);
        if (s.length() < 5){
            s = cArr.substring(0,5-s.length()) + s;
        }
        b += s;
        System.out.println(b);
        //加密
        byte[] arr = new byte[3];
        for(int i = 0;i < 3;i++){
            s = b.substring(0,8);
            arr[i] = (byte)(Integer.parseInt(s,2));
            b = b.substring(8);
        }
        return arr;
    }

    /**
     * 将日期加密的字节数组进行解密
     * @param data
     * @return
     */
    public static String decode(byte[] data){
        //对byte数组进行校验
        if(data == null || data.length < 3){
            return null;
        }
        String h = "";
        String cArr = "0000000000000000000000000000000";
        String s;
        for(int i = 0;i < 3;i++){
            s =  Integer.toBinaryString(Byte.toUnsignedInt(data[i]));
            if(s.length() < 8 ){
                h += cArr.substring(0,8 - s.length()) + s;
            }else if (s.length() > 8){
                h += s.substring(s.length() - 8);
            }else {
                h += s;
            }
        }
        System.out.println(h);
        h = h.substring(h.indexOf('0'));
        //日
        s = h.substring(h.length()-5);
        h = h.substring(0,h.length()-5);
        int m = Integer.parseInt(s,2);
        String dd = Integer.toString(m);
        if (dd.length() < 2){
            dd = "0" + dd;
        }
        //月
        s = h.substring(h.length()-4);
        h = h.substring(0,h.length()-4);
        m = Integer.parseInt(s,2);
        s = Integer.toString(m);
        if(s.length() < 2){
            s = "0" + s;
        }
        dd = s + dd;
        //年
        m = Integer.parseInt(h,2);
        s = Integer.toString(m);
        if(s.length() < 2){
            s = "0" + s;
        }
        dd = s + dd;
        return dd;
    }

    public static void main(String[] arg0){
        String test = "20170705";
        byte[] bs = encode(test);
        System.out.println(Arrays.toString(bs));
        System.out.println(decode(bs));
    }
}
