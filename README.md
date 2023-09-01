# SpecialTools
一个比较怪的java工具箱  

目前功能:  
  1:禁止某个类的某个方法访问当前方法  
    boolean isInvoke(String className,String methodName) throws Exception  
  2:判断某个类的某个方法里面是否调用了一个指定方法  
    boolean containsMethod(Class<?> cl,String methodName,String methodDesc,String methodName2,String methodDesc2) throws Exception  
  3:把父类对象转换成子类对象  
    <T,E extends T> E convertFatherToSon(Class<E> sonClass, T instance, List<String> fieldsToIgnore, Map<String,Object> fieldsToPut)  
  4:动态代理类对象(不是接口),可以在某个实例对象的某个方法执行前和执行后包括执行时插桩  
    <T extends InvocationHandler2,E> E newProxy(T invocation,E target,Method method) throws Throwable  
