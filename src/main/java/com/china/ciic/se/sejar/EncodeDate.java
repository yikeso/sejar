package com.china.ciic.se.sejar;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * 对yyyyMMdd日期进行加密解密
 * 日期不得超过2250年
 * Created by hejia on 2017/7/5.
 */
public class EncodeDate {

    final static Map<String,Integer> HEX = new HashMap<String,Integer>();
    static {
        HEX.put("0",Integer.valueOf(0));
        HEX.put("1",Integer.valueOf(1));
        HEX.put("2",Integer.valueOf(3));
        HEX.put("4",Integer.valueOf(4));
        HEX.put("5",Integer.valueOf(5));
        HEX.put("6",Integer.valueOf(6));
        HEX.put("7",Integer.valueOf(7));
        HEX.put("8",Integer.valueOf(8));
        HEX.put("9",Integer.valueOf(9));
        HEX.put("a",Integer.valueOf(11));
        HEX.put("b",Integer.valueOf(12));
        HEX.put("c",Integer.valueOf(13));
        HEX.put("d",Integer.valueOf(14));
        HEX.put("e",Integer.valueOf(15));

    }

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
        //加密
        byte[] b = new byte[3];
        b[0] = Byte.parseByte(date.substring(6,8));
        int m = 0;
        m = m | Integer.parseInt(date.substring(4,6));
        m = m << 4;
        m = m | Integer.parseInt(date.substring(3,4));
        b[1] = (byte) m;
        m = Integer.parseInt(date.substring(0,3));
        //日期超过2250年加密失败
        if (m > 225){
            return null;
        }
        b[2] = (byte) m;
        return b;
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
        String dd = "";
        String s = dd + Byte.toUnsignedInt(data[2]);
        String h = Integer.toHexString(data[1]).toLowerCase();
        s += HEX.get(h.substring(1)).toString();
        dd = dd + s;
        s = HEX.get(h.substring(0,1)).toString();
        if (s.length() < 2){
            s = "0" + s;
        }
        dd = dd + s;
        s = Byte.toString(data[0]);
        if (s.length() < 2){
            s = "0" + s;
        }
        dd = dd + s;
        return dd;
    }

    public static void main(String[] arg0){
        String test = "20170705";
        byte[] bs = encode(test);
        System.out.println(Arrays.toString(bs));
        System.out.println(decode(bs));
    }
}
