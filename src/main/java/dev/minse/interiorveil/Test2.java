import java.lang.reflect.Method;
public class Test2 {
    public static void main(String[] args) throws Exception {
        Class<?> clazz = Class.forName("net.minecraft.world.item.Item");
        for (Method m : clazz.getMethods()) {
            if (m.getName().equals("use")) {
                System.out.println(m.getReturnType().getName());
            }
        }
    }
}
