package com.pnc.domain;

/**
 * Created with IntelliJ IDEA.
 * User: shuiqing
 * Date: 2015/7/16
 * Time: 10:31
 * Email: shuiqing301@gmail.com
 * GitHub: https://github.com/ShuiQing301
 * Blog: http://shuiqing301.github.io/
 * _
 * |_)._ _
 * | o| (_
 */
public enum DataType {

    /** 测点类型-开关量 */
    DATATYPE_DI(0),

    /** 测点类型-模拟量 */
    DATATYPE_AI(1);

    /**
     * 下标值。
     */
    private int index;

    /**
     * 使用下标构造枚举。
     * 构造函数
     * @param index 用于构造枚举的下标。
     */
    private DataType(int index) {
        this.index = index;
    }

    /**
     * 返回当前枚举的下标。
     * @return 返回本枚举的下标值
     */
    public int index() {
        return index;
    }
}
