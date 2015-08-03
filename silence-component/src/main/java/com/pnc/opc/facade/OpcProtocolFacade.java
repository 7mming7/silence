package com.pnc.opc.facade;

import java.util.Scanner;

/**
 * Created with IntelliJ IDEA.
 * User: shuiqing
 * Date: 2015/7/16
 * Time: 11:03
 * Email: shuiqing301@gmail.com
 * GitHub: https://github.com/ShuiQing301
 * Blog: http://shuiqing301.github.io/
 * _
 * |_)._ _
 * | o| (_
 */
public class OpcProtocolFacade {

    private static Scanner scanner = new Scanner(System.in);

    /**
     * 控制台交互
     */
    public static void interactiveCmd () {
        String line = scanner.nextLine();
        if (line.equals("help") || line.equals("h") || line.equals("?")) {
            displayCommandInfo();
        }
        interactiveCmd();
    }

    /**
     * 显示控制台交互信息
     */
    private static void displayCommandInfo() {
        System.out.println("* * * * * * * * * * * * * * * * * * * * * * * * *");
        System.out.println("- - - - - - - -  opc 接口功能  - - - - - - - - - -");
        System.out.println(" conn:     connect opc service.               ex: conn 3 ");
        System.out.println(" dispose:  dispose opc connect.               ex: dispose 3 ");
        System.out.println(" reload:   reLoad mesuringPoint config file.  ex: reload ");
        System.out.println(" ssend:    start send io info to udp ip&port. ex: ssend  ");
        System.out.println(" esend:    stop send io info to udp ip&port.  ex: esend  ");
    }
}
