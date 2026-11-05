package dev.homecontrol.app;

import java.math.BigDecimal;
import java.util.*;

/** Rejects guessed enum values, fractional integers, out-of-range and off-step writes. */
final class MijiaValue {
    static Object parse(String format,String text){
        if("string".equals(format)){if(text.length()>256)throw new IllegalArgumentException("文本不能超过 256 字符");return text;}
        if("bool".equals(format)){if("true".equals(text))return true;if("false".equals(text))return false;throw new IllegalArgumentException("请选择开或关");}
        BigDecimal number;try{number=new BigDecimal(text.trim());}catch(Exception e){throw new IllegalArgumentException("请输入有效数值");}
        if("float".equals(format)||"double".equals(format)){double value=number.doubleValue();if(!Double.isFinite(value))throw new IllegalArgumentException("数值超出范围");return value;}
        int bits;boolean unsigned=format.startsWith("uint");try{bits=Integer.parseInt(format.substring(unsigned?4:3));}catch(Exception e){throw new IllegalArgumentException("此属性类型暂不支持");}
        if(!(format.startsWith("int")||unsigned)||!Arrays.asList(8,16,32,64).contains(bits)||unsigned&&bits==64)throw new IllegalArgumentException("此属性类型暂不支持");
        long value;try{value=number.longValueExact();}catch(Exception e){throw new IllegalArgumentException("请输入有效整数");}
        if(bits<64){long min=unsigned?0:-(1L<<(bits-1)),max=unsigned?(1L<<bits)-1:(1L<<(bits-1))-1;if(value<min||value>max)throw new IllegalArgumentException("整数超出设备支持范围");}return value;
    }
    static boolean equivalent(Object a,Object b){if(a instanceof Number&&b instanceof Number)try{return new BigDecimal(a.toString()).compareTo(new BigDecimal(b.toString()))==0;}catch(Exception e){return false;}return a!=null&&a.equals(b);}
    static boolean allowed(String format,Object value,List<Object> choices,List<Number> range){
        if(value==null)return false;Object parsed;try{if("bool".equals(format)&&!(value instanceof Boolean)||"string".equals(format)&&!(value instanceof String)||!Arrays.asList("bool","string").contains(format)&&!(value instanceof Number))return false;parsed=parse(format,String.valueOf(value));if(!equivalent(parsed,value))return false;}catch(Exception e){return false;}
        if(!choices.isEmpty()){for(Object option:choices)if(equivalent(value,option))return true;return false;}
        if(!range.isEmpty()){if(range.size()!=3||!(value instanceof Number))return false;try{BigDecimal min=new BigDecimal(range.get(0).toString()),max=new BigDecimal(range.get(1).toString()),step=new BigDecimal(range.get(2).toString()),v=new BigDecimal(value.toString());return min.compareTo(max)<=0&&step.signum()>0&&v.compareTo(min)>=0&&v.compareTo(max)<=0&&v.subtract(min).remainder(step).signum()==0;}catch(Exception e){return false;}}
        return true;
    }
    private MijiaValue(){}
}
