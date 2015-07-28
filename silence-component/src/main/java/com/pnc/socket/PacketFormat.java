package com.pnc.socket;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.zip.CRC32;

/**
 * 传输包格式化，具备校验功能.
 * User: shuiqing
 * Date: 2015/7/28
 * Time: 11:41
 * Email: shuiqing301@gmail.com
 * GitHub: https://github.com/ShuiQing301
 * Blog: http://shuiqing301.github.io/
 * _
 * |_)._ _
 * | o| (_
 */
public class PacketFormat {

    /**请求头,主要用于校验*/
    public static final byte[] REQUEST_HEADER = {'S','H','A','N'};

    private Properties prop = null;

    /**
     * 构造一个用于发送请求的包结构
     */
    public PacketFormat(){
        this.prop = new Properties();
    }

    /**
     * 解析并从请求中加载数据
     * @param buff 数据包
     * @param offset 偏移量
     * @param len 长度
     * @return true:解析成功,false:解析失败
     */
    public boolean parse(byte[] buff, int offset, int len){
        boolean done = false;
        ByteArrayInputStream dataCache = null;
        ObjectInputStream objectIn = null;
        ByteArrayInputStream propCache = null;

        try {
            dataCache = new ByteArrayInputStream(buff, offset, len);
            byte[] d_header = new byte[REQUEST_HEADER.length];
            dataCache.read(d_header);
            //校验请求头
            if(!Arrays.equals(d_header, REQUEST_HEADER)){
                return false;
            }

            objectIn = new ObjectInputStream(dataCache);
            //得到数据长度
            short dataLen = objectIn.readShort();
            byte[] propBys = new byte[dataLen];
            objectIn.read(propBys);
            propCache = new ByteArrayInputStream(propBys);
            //加载数据
            this.prop.load(propCache);
            done = true;
        } catch (Exception e) {
            done = false;
        }finally{
            try{
                if(null!=propCache)propCache.close();
            }catch(Exception ex){}

            try{
                if(null!=objectIn)objectIn.close();
            }catch(Exception ex){}

            try{
                if(null!=dataCache)dataCache.close();
            }catch(Exception ex){}
        }

        return done;
    }


    /**
     * 设置数据
     * @param key
     * @param value
     */
    public void setProperty(String key, String value){
        this.prop.setProperty(key, value);
    }

    /**
     * 取数据
     * @param key
     * @return
     */
    public String getProperty(String key){
        return this.prop.getProperty(key,"");
    }

    /**
     * 返回keyset
     * @return
     */
    public Set<Object> keySet(){
        return this.prop.keySet();
    }

    /**
     * 将内容转换为byte数组
     * @return
     */
    public byte[] toBytes(){
        byte[] dataCacheBys = null;
        ByteArrayOutputStream dataCache = null;
        ObjectOutputStream dataOut = null;
        StringBuilder propContent = new StringBuilder();
        byte[] propBys = null;

        Set<Map.Entry<Object, Object>> items = this.prop.entrySet();
        for(Map.Entry<Object, Object> i: items){
            propContent.append((String)i.getKey());
            propContent.append("=");
            propContent.append((String)i.getValue());
            propContent.append("\n");
        }
        propBys = propContent.toString().getBytes();

        try{
            dataCache = new ByteArrayOutputStream();
            dataCache.write(REQUEST_HEADER);

            dataOut = new ObjectOutputStream(dataCache);
            //写入数据长度
            dataOut.writeShort(propBys.length);
            //写入数据
            dataOut.write(propBys, 0, propBys.length);
            dataOut.flush();
            dataCacheBys = dataCache.toByteArray();
        }catch(Exception ex){
            throw new RuntimeException(ex);
        }finally{
            try{
                if(null!=dataOut) dataOut.close();
            }catch(Exception ex){}

            try{
                if(null!=dataCache) dataCache.close();
            }catch(Exception ex){}
        }

        return dataCacheBys;
    }

    /**
     * 得到请求数据
     * @param buff 数据缓存,缓存大小>=128
     * @param offset 偏移量
     * @return 包长度=请求头长度+2+数据
     */
    public int getBytes(byte[] buff, int offset){
        int len = 0;
        ByteArrayOutputStream dataCache = null;
        ObjectOutputStream dataOut = null;
        StringBuilder propContent = new StringBuilder();
        byte[] propBys = null;
        Set<Map.Entry<Object, Object>>items = this.prop.entrySet();
        for(Map.Entry<Object, Object> i: items){
            propContent.append((String)i.getKey());
            propContent.append("=");
            propContent.append((String)i.getValue());
            propContent.append("\n");
        }
        propBys = propContent.toString().getBytes();

        try{
            //写入头
            dataCache = new ByteArrayOutputStream(buff.length);
            dataCache.write(REQUEST_HEADER);
            len+=REQUEST_HEADER.length;

            dataOut = new ObjectOutputStream(dataCache);
            //写入数据长度
            dataOut.writeShort(propBys.length);
            len+=2;
            //写入数据
            dataOut.write(propBys, 0, propBys.length);
            dataOut.flush();
            byte[] dataCacheBys = dataCache.toByteArray();
            System.arraycopy(dataCacheBys, 0, buff, offset, dataCacheBys.length);
            len+=dataCacheBys.length;
        }catch(Exception ex){
            throw new RuntimeException(ex);
        }finally{
            try{
                if(null!=dataOut) dataOut.close();
            }catch(Exception ex){}

            try{
                if(null!=dataCache) dataCache.close();
            }catch(Exception ex){}
        }

        return len;
    }

    /**
     * 返回crc32校验码
     * @return
     */
    long crc32(){
        CRC32 crc32 = new CRC32();
        crc32.update(toBytes());
        return crc32.getValue();
    }
}
