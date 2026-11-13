package com.archer.xjson.light;


public class XJsonException extends RuntimeException {
    static final long serialVersionUID = -3321467993124229948L;
    
    static final int END_OFFSET = 32;
    
    public XJsonException(Throwable e) {
    	super(e.getMessage());
    }
    
    public XJsonException(String msg) {
    	super(msg);
    }
    

    public XJsonException(String msg, Throwable e) {
    	super(msg, e);
    }
    
}
