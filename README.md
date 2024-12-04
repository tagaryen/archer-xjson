# archer-xjson
json library written with pure java

## examples  
``` java  
public class TestObj {
    boolean ret;
    char c;
    byte b;
    int code;
    float money;
    String msg;
    LocalDateTime t;
    List<Queue<TestObj>> list;
    List<String> strs;
    BigInteger bigA;
    BigDecimal bigB;
    

    public TestObj() {
    }
    
    public TestObj(boolean hasList) {
        c = 'w';
        b = 10;
        code = 100;
        money = 123.4f;
        msg = "mm\"} \"mss";
        ret = true;
        t = LocalDateTime.now();
        if(hasList) {
            Queue<TestObj> queue = new ArrayBlockingQueue<>(2);
            queue.add(new TestObj(false));
            queue.add(new TestObj(false));
            list = Arrays.asList(queue);
        }
        bigA = BigInteger.valueOf(1824683462834l);
        bigB = BigDecimal.valueOf(172714.124d);
    }

    public boolean isRet() {
        return ret;
    }

    public void setRet(boolean ret) {
        this.ret = ret;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public float getMoney() {
        return money;
    }

    public void setMoney(float money) {
        this.money = money;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public LocalDateTime getT() {
        return t;
    }

    public void setT(LocalDateTime t) {
        this.t = t;
    }

    public List<Queue<TestObj>> getList() {
        return list;
    }

    public void setList(List<Queue<TestObj>> list) {
        this.list = list;
    }

    public BigInteger getBigA() {
        return bigA;
    }

    public void setBigA(BigInteger bigA) {
        this.bigA = bigA;
    }

    public BigDecimal getBigB() {
        return bigB;
    }

    public void setBigB(BigDecimal bigB) {
        this.bigB = bigB;
    }
}

    // test 
    Map<String, TestObj> map = new HashMap<>();
    map.put("the", new TestObj(true));

    String bjson = XJSONStatic.stringify(map);
    System.out.println(bjson);
     
    Map<String, TestObj> parsed = XJSONStatic.parse(bjson, new JavaTypeRef<Map<String, TestObj>>() {});
    System.out.println(parsed.get("the").msg);
```  

