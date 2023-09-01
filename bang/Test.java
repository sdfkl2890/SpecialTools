package bang;

import static bang.Utils.*;

public class Test {
    public static void main(String[] args) throws Throwable{
        long l1 = System.currentTimeMillis();
        System.out.println("----------");
        isInvokedByMain(); //true
        System.out.println("cost " + (System.currentTimeMillis() - l1) + "ms");
        l1 = System.currentTimeMillis();
        System.out.println("----------");
        Person person = new Person();
        person.age = 1;
        Person person1 = newProxy(new InvocationHandler2(true){
            public void before(Object[] args){
                System.out.println(args[0] + "1");//wasd1
                System.out.println("abcd");
            }
        },person,Person.class.getMethod("printName",String.class));
        assert person1 != null;
        person1.printName("wasd");
        System.out.println("cost " + (System.currentTimeMillis() - l1) + "ms");
        l1 = System.currentTimeMillis();
        System.out.println("----------");
        isContainsMethod();//true
        System.out.println("cost " + (System.currentTimeMillis() - l1) + "ms");
        l1 = System.currentTimeMillis();
        System.out.println("----------");
    }
    public static void isInvokedByMain(){
        System.out.println("Invoked by bang.Test.main(args) " + isInvoke("bang.Test","main"));
    }

    private static void isContainsMethod() throws Exception {
        System.out.println("Test.class Contains Method isInvokedByMain " + containsMethod(Test.class,"isInvokedByMain","()V","java/io/PrintStream","println","(Ljava/lang/String;)V"));
    }
}
