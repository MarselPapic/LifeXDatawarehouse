package at.htlle.freq.domain;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DomainPojoTest {

    @Test
    void gettersAndSettersRoundTripValues() throws Exception {
        List<Class<?>> classes = List.of(
                Account.class,
                Address.class,
                AudioDevice.class,
                City.class,
                Clients.class,
                Country.class,
                DeploymentVariant.class,
                InstalledSoftware.class,
                PhoneIntegration.class,
                Project.class,
                Radio.class,
                Server.class,
                ServiceContract.class,
                Site.class,
                Software.class,
                UpgradePlan.class
        );

        for (Class<?> type : classes) {
            Object instance = type.getDeclaredConstructor().newInstance();
            for (Method method : type.getMethods()) {
                if (!method.getName().startsWith("set") || method.getParameterCount() != 1) {
                    continue;
                }
                Class<?> paramType = method.getParameterTypes()[0];
                Object value = sampleValue(paramType);
                if (value == null) {
                    continue; // skip unsupported types
                }
                method.invoke(instance, value);

                Method getter = resolveGetter(type, method, paramType);

                Object result = getter.invoke(instance);
                if (paramType.isPrimitive()) {
                    assertEquals(value, result, () -> type.getSimpleName() + "#" + getter.getName());
                } else {
                    assertSame(value, result, () -> type.getSimpleName() + "#" + getter.getName());
                }
            }
        }
    }

    private Method resolveGetter(Class<?> type, Method setter, Class<?> paramType) throws NoSuchMethodException {
        String getterName = paramType == boolean.class
                ? setter.getName().replaceFirst("set", "is")
                : setter.getName().replaceFirst("set", "get");

        try {
            return type.getMethod(getterName);
        } catch (NoSuchMethodException ex) {
            // fall back to getX for Boolean properties where only get-prefixed accessor exists
            if (!getterName.startsWith("get")) {
                return type.getMethod(setter.getName().replaceFirst("set", "get"));
            }
            throw ex;
        }
    }

    private Object sampleValue(Class<?> type) {
        if (type == String.class) {
            return "value";
        }
        if (type == UUID.class) {
            return UUID.fromString("00000000-0000-0000-0000-000000000001");
        }
        if (type == Integer.class) {
            return 42;
        }
        if (type == int.class) {
            return 7;
        }
        if (type == boolean.class) {
            return true;
        }
        if (type == Boolean.class) {
            return Boolean.TRUE;
        }
        return null;
    }
}
