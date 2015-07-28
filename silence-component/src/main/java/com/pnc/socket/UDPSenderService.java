package com.pnc.socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.Random;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * Created with IntelliJ IDEA.
 * User: shuiqing
 * Date: 2015/7/28
 * Time: 13:50
 * Email: shuiqing301@gmail.com
 * GitHub: https://github.com/ShuiQing301
 * Blog: http://shuiqing301.github.io/
 * _
 * |_)._ _
 * | o| (_
 */
public class UDPSenderService{

    private static final Logger log = LoggerFactory.getLogger(UDPSenderService.class);

    private BlockingDeque<RequestObject> requestPool = null;

    private long intervalTime = 200L;

    private DatagramSocket udpSender = null;

    private boolean shutdown = true;

    private Object lockObj = new Object();

    public UDPSenderService(){
        requestPool = new LinkedBlockingDeque<RequestObject>();
    }

    /**
     *
     * @param poolSize 请求缓冲池上限
     * @param intervalTime 间隔时间
     */
    public UDPSenderService(int poolSize,int intervalTime){
        requestPool = new LinkedBlockingDeque<RequestObject>(poolSize);
        this.intervalTime = intervalTime;
    }

    /**
     * 过程
     */
    void process() {
        do{
            long now = System.currentTimeMillis();
            try{
                synchronized(lockObj){
                    lockObj.wait(intervalTime);

                    if(!requestPool.isEmpty()){
                        send(now);
                    }
                }
            } catch (Exception e) {
                log.error("lock obj error.",e);
            }
        }while(!shutdown);
    }

    private void send(long time) throws IOException {
        RequestObject ro = null;

        try {
            for(int i =0; i<10; i++){
                ro = requestPool.poll();
                if(null!=ro){
                    if(ro.getTime() <= time){
                        udpSender.send(ro.getDataPacket());
                    }else{
                        requestPool.put(ro);
                    }
                }else{
                    break;
                }
            }
        } catch (Exception e) {
            log.error("send datapacket.");
        }
    }


    /**
     * 重复多送发送一个udp请求
     * @param request 请求包
     * @param repeatCount 重复次数
     * @param interval 重复发送请求包间隔
     */
    public void addRepeatingRequest(DatagramPacket request, int repeatCount, long interval){
        addImmediateRequest(request);
        byte[] reqData = new byte[request.getLength()-request.getOffset()];
        System.arraycopy(request.getData(), request.getOffset(), reqData, request.getOffset(), request.getLength());
        long now = System.currentTimeMillis();
        DatagramPacket dpClone = null;

        for(long i=0,nextTime=interval; i<repeatCount; i++, nextTime+=interval){
            dpClone = new DatagramPacket(reqData, request.getOffset(), request.getLength());
            dpClone.setSocketAddress(request.getSocketAddress());
            addTimingRequest(now+nextTime, dpClone);
        }
    }

    /**
     * 添加一个定时的请求，在一个近似的时间执行发送.<br/>
     * 如果这个请求为过期的请求,则会在下一个时间被执行.
     * @param time
     * @param request
     */
    public void addTimingRequest(long time, DatagramPacket request){
        try {
            requestPool.put(new RequestObject(time, request));
        } catch (Exception e) {
            e.printStackTrace();
        }
        synchronized(lockObj){
            lockObj.notify();
        }
    }

    /**
     * 立即发送一个请求
     * @param request
     */
    public void addImmediateRequest(DatagramPacket request){
        try{
            udpSender.send(request);
        }catch(Exception ex){
            ex.printStackTrace();
        }
    }

    /**
     * 启动服务
     */
    public void start(){
        if(this.shutdown){
            try {
                if(null==udpSender){
                    udpSender = new DatagramSocket();
                }
            } catch (SocketException e1) {
                e1.printStackTrace();
                return;
            }

            try {
                Thread t = new Thread(new ProcessService(),"UDPSenderService-"+new Random().nextInt(999));
                t.start();
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage());
            }
            this.shutdown = false;
        }
    }

    /**
     * 中止服务
     */
    public void shutdown(){
        this.shutdown = true;

        try{
            Thread.sleep(1000*10);
        }catch(Exception ex){
        }

        try{
            this.udpSender.disconnect();
        }catch(Exception ex){
        }

        try{
            this.udpSender.close();
        }catch(Exception ex){
        }
    }

    public void setDatagramSocket(DatagramSocket sender){
        this.udpSender = sender;
    }

    private class ProcessService implements Runnable{
        public void run(){
            process();
        }
    }

    static class RequestObject {
        private Long time;
        private DatagramPacket dataPacket = null;

        public RequestObject(DatagramPacket dataPacket){
            this.time = System.currentTimeMillis();
        }

        public RequestObject(Long time,DatagramPacket dataPacket){
            this.time = time;
            this.dataPacket = dataPacket;
        }

        public Long getTime(){
            return time;
        }

        public DatagramPacket getDataPacket(){
            return dataPacket;
        }
    }
}
